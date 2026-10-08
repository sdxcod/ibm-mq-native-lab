package dev.sdxcod.mqlab.transport;

import com.ibm.mq.*;
import dev.sdxcod.mqlab.config.MqSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static com.ibm.mq.constants.MQConstants.*;

/**
 * Direct MQ classes for Java. No global MQEnvironment, JMS sessions, pooling, or automatic retry.
 */
public final class NativeMqSession implements MqSession {
    private final MqSettings settings;
    private final QueueManagerConnector connector;
    private final Thread owner = Thread.currentThread();
    private final Map<QueueHandle, MQQueue> queues = new LinkedHashMap<>();
    private MQQueueManager manager;
    private TransactionState state = TransactionState.CLEAN;

    private record QueueHandle(String name, int mode) {
    }

    public NativeMqSession(MqSettings settings) {
        this(settings, MQQueueManager::new);
    }

    public NativeMqSession(MqSettings settings, QueueManagerConnector connector) {
        this.settings = settings;
        this.connector = connector;
    }

    private void checkThread() {
        if (owner != Thread.currentThread())
            throw new IllegalStateException("MQ session must be used by its owning thread");
    }

    @Override
    public void connect() throws MQException, IOException {
        checkThread();
        if (manager != null)
            throw new IllegalStateException("Disconnect before reconnecting this session");

        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(HOST_NAME_PROPERTY, settings.host());
        properties.put(PORT_PROPERTY, settings.port());
        properties.put(CHANNEL_PROPERTY, settings.channel());
        properties.put(TRANSPORT_PROPERTY, TRANSPORT_MQSERIES_CLIENT);
        properties.put(USER_ID_PROPERTY, settings.user());
        properties.put(PASSWORD_PROPERTY, settings.resolvePassword());
        properties.put(USE_MQCSP_AUTHENTICATION_PROPERTY, true);
        manager = connector.connect(settings.queueManager(), properties);
        state = TransactionState.CLEAN;
    }

    @Override
    public boolean isConnected() {
        checkThread();
        return manager != null && manager.isConnected(); // Local state, NOT a network probe.
    }

    private void requireReady() {
        checkThread();

        if (state == TransactionState.UNKNOWN)
            throw new IllegalStateException("Transaction outcome is unknown. Disconnect and investigate; do not blindly retry.");
        if (!isConnected())
            throw new IllegalStateException("MQ session is not connected");
    }

    private MQQueue queue(String name, int mode) throws MQException {
        requireReady();
        QueueHandle key = new QueueHandle(name, mode);
        MQQueue handle = queues.get(key);
        if (handle == null) {
            handle = manager.accessQueue(name, mode | MQOO_FAIL_IF_QUIESCING);
            queues.put(key, handle);
        }
        return handle;
    }

    @Override
    public String putMessage(String queue, String text) throws MQException, IOException {
        requireReady();
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > settings.maxPayloadBytes())
            throw new IllegalArgumentException("Text exceeds configured payload limit, or is null");
        MQMessage message = new MQMessage();
        message.format = MQFMT_STRING;
        message.characterSet = 1208;
        message.persistence = MQPER_PERSISTENT;
        message.writeString(text);
        MQPutMessageOptions options = new MQPutMessageOptions();
        options.options = MQPMO_SYNCPOINT | MQPMO_NEW_MSG_ID | MQPMO_FAIL_IF_QUIESCING;
        try {
            queue(queue, MQOO_OUTPUT).put(message, options);
            state = TransactionState.PENDING;
            return HexFormat.of().formatHex(message.messageId);
        } catch (MQException e) {
            observe(e);
            throw e;
        }
    }

    @Override
    public Optional<ReceivedMessage> getMessage(String queue, Duration wait, String messageId)
            throws MQException, IOException {
        requireReady();

        long millis = wait.toMillis();
        if (wait.isNegative() || millis > 60000)
            throw new IllegalArgumentException("Wait must be 0..60 seconds");

        MQMessage message = new MQMessage();
        message.characterSet = 1208;
        MQGetMessageOptions options = new MQGetMessageOptions();
        options.options = MQGMO_SYNCPOINT | MQGMO_FAIL_IF_QUIESCING | MQGMO_CONVERT
                | (millis > 0 ? MQGMO_WAIT : MQGMO_NO_WAIT);
        options.waitInterval = (int) millis;
        options.matchOptions = MQMO_NONE;

        if (messageId != null && !messageId.isBlank()) {
            if (messageId.length() != 48)
                throw new IllegalArgumentException("MQ message ID must contain 48 hex characters");
            message.messageId = HexFormat.of().parseHex(messageId);
            options.matchOptions = MQMO_MATCH_MSG_ID;
        }
        try {
            queue(queue, MQOO_INPUT_SHARED).get(message, options, settings.maxPayloadBytes());
            state = TransactionState.PENDING; // Set BEFORE decoding: close must back out malformed messages too.

            if (!MQFMT_STRING.equals(message.format))
                throw new IOException("This lab accepts MQFMT_STRING text only");
            String text = message.readStringOfByteLength(message.getDataLength());

            return Optional.of(new ReceivedMessage(HexFormat.of().formatHex(message.messageId),
                    HexFormat.of().formatHex(message.correlationId), text, message.backoutCount,
                    message.persistence == MQPER_PERSISTENT));
        } catch (MQException e) {
            if (e.reasonCode == MQRC_NO_MSG_AVAILABLE)
                return Optional.empty();
            observe(e);
            throw e;
        }
    }

    @Override
    public int getInQueueMsgCount(String name) throws MQException {
        try {
            return queue(name, MQOO_INQUIRE).getCurrentDepth();
        } catch (MQException e) {
            observe(e);
            throw e;
        }
    }

    @Override
    public void commit() throws MQException {
        requireReady();
        try {
            manager.commit();
            state = TransactionState.CLEAN;
        } catch (MQException e) {
            state = TransactionState.UNKNOWN;
            throw e;
        }
    }

    @Override
    public void backout() throws MQException {
        requireReady();
        try {
            manager.backout();
            state = TransactionState.CLEAN;
        } catch (MQException e) {
            state = TransactionState.UNKNOWN;
            throw e;
        }
    }

    private void observe(MQException e) {
        if (e.reasonCode == MQRC_CONNECTION_BROKEN || e.reasonCode == MQRC_HCONN_ERROR)
            state = TransactionState.UNKNOWN;
    }

    @Override
    public TransactionState transactionState() {
        checkThread();
        return state;
    }

    @Override
    public void disconnect() throws MQException {
        checkThread();
        if (manager == null)
            return;

        MQException failure = null;
        if (state == TransactionState.PENDING) {
            try {
                manager.backout();
                state = TransactionState.CLEAN;
            } catch (MQException e) {
                state = TransactionState.UNKNOWN;
                failure = e;
            }
        }
        for (MQQueue queue : queues.values()) {
            try {
                queue.close();
            } catch (MQException e) {
                failure = merge(failure, e);
            }
        }
        queues.clear();

        try {
            manager.disconnect();
        } catch (MQException e) {
            failure = merge(failure, e);
        } finally {
            manager = null;
        }
        // Preserve UNKNOWN for diagnostics. Disconnect cannot prove a lost commit's outcome.
        if (failure != null)
            throw failure;
    }

    private static MQException merge(MQException first, MQException next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }
}
