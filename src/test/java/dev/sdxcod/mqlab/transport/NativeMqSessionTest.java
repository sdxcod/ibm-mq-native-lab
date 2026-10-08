package dev.sdxcod.mqlab.transport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ibm.mq.constants.MQConstants.*;

import com.ibm.mq.*;
import dev.sdxcod.mqlab.config.MqSettings;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NativeMqSessionTest {
    private MQQueueManager manager;
    private MQQueue queue;
    private NativeMqSession session;
    private Hashtable<String, Object> connectionProperties;
    private String managerName;

    static MqSettings settings() {
        return new MqSettings("localhost",
                1414,
                "QM1",
                "DEV.APP.SVRCONN",
                "app",
                "unit-secret",
                Path.of("unused"),
                "DEV.LAB.IN",
                "DEV.LAB.OUT",
                "DEV.LAB.TEST",
                1048576);
    }

    @BeforeEach
    void setup() throws Exception {
        manager = mock(MQQueueManager.class);
        queue = mock(MQQueue.class);
        when(manager.isConnected()).thenReturn(true);
        when(manager.accessQueue(anyString(), anyInt())).thenReturn(queue);
        session = new NativeMqSession(settings(), (name, properties) -> {
            managerName = name;
            connectionProperties = properties;
            return manager;
        });
        session.connect();
    }

    @Test
    void connectionUsesPerSessionPropertiesAndMqcsp() {
        assertEquals("QM1", managerName);
        assertEquals(TRANSPORT_MQSERIES_CLIENT, connectionProperties.get(TRANSPORT_PROPERTY));
        assertEquals("unit-secret", connectionProperties.get(PASSWORD_PROPERTY));
        assertEquals(true, connectionProperties.get(USE_MQCSP_AUTHENTICATION_PROPERTY));
        assertFalse(settings().toString().contains("unit-secret"));
    }

    @Test
    void putIsPersistentUtf8WithSyncpointAndNativeMessageId() throws Exception {
        byte[] id = new byte[24];
        Arrays.fill(id, (byte) 0x5a);
        doAnswer(inv -> {
            inv.getArgument(0, MQMessage.class).messageId = id;
            return null;
        })
                .when(queue).put(any(MQMessage.class), any(MQPutMessageOptions.class));
        assertEquals(HexFormat.of().formatHex(id), session.putMessage("DEV.LAB.IN", "سلام MQ"));
        var message = ArgumentCaptor.forClass(MQMessage.class);
        var options = ArgumentCaptor.forClass(MQPutMessageOptions.class);
        verify(queue).put(message.capture(), options.capture());
        assertEquals(1208, message.getValue().characterSet);
        assertEquals(MQPER_PERSISTENT, message.getValue().persistence);
        message.getValue().seek(0);
        assertEquals("سلام MQ", message.getValue().readStringOfByteLength(message.getValue().getDataLength()));
        assertTrue((options.getValue().options & MQPMO_SYNCPOINT) != 0);
        assertEquals(TransactionState.PENDING, session.transactionState());
    }

    @Test
    void commitClearsPendingAndCloseDoesNotBackout() throws Exception {
        session.putMessage("DEV.LAB.IN", "commit");
        session.commit();
        session.close();
        verify(manager).commit();
        verify(manager, never()).backout();
        verify(manager).disconnect();
        assertEquals(TransactionState.CLEAN, session.transactionState());
        assertFalse(session.isConnected());
    }

    @Test
    void closeRollsBackUnfinishedWorkAndReleasesHandles() throws Exception {
        session.putMessage("DEV.LAB.IN", "pending");
        session.close();
        var order = inOrder(manager, queue);
        order.verify(manager).backout();
        order.verify(queue).close();
        order.verify(manager).disconnect();
    }

    @Test
    void explicitBackoutClearsPending() throws Exception {
        session.putMessage("DEV.LAB.IN", "discard");
        session.backout();
        session.close();
        verify(manager, times(1)).backout();
        assertEquals(TransactionState.CLEAN, session.transactionState());
    }

    @Test
    void commitFailureIsUnknownAndNeverRetriedOrAutomaticallyBackedOut() throws Exception {
        session.putMessage("DEV.LAB.IN", "uncertain");
        doThrow(new MQException(MQCC_FAILED, MQRC_CONNECTION_BROKEN, "test")).when(manager).commit();
        assertThrows(MQException.class, session::commit);
        assertEquals(TransactionState.UNKNOWN, session.transactionState());
        assertThrows(IllegalStateException.class, () -> session.putMessage("DEV.LAB.IN", "retry"));
        session.close();
        verify(manager, times(1)).commit();
        verify(manager, never()).backout();
        assertEquals(TransactionState.UNKNOWN, session.transactionState());
    }

    @Test
    void brokenConnectionDuringPutAlsoBlocksFurtherWork() throws Exception {
        doThrow(new MQException(MQCC_FAILED, MQRC_CONNECTION_BROKEN, "test"))
                .when(queue).put(any(MQMessage.class), any(MQPutMessageOptions.class));
        assertThrows(MQException.class, () -> session.putMessage("DEV.LAB.IN", "x"));
        assertEquals(TransactionState.UNKNOWN, session.transactionState());
        assertThrows(IllegalStateException.class, session::commit);
    }

    @Test
    void noMessage2033ReturnsEmptyWithoutPendingWork() throws Exception {
        doThrow(new MQException(MQCC_FAILED, MQRC_NO_MSG_AVAILABLE, "test"))
                .when(queue).get(any(MQMessage.class), any(MQGetMessageOptions.class), anyInt());
        assertTrue(session.getMessage("DEV.LAB.IN", Duration.ZERO, null).isEmpty());
        assertEquals(TransactionState.CLEAN, session.transactionState());
        assertTrue(session.isConnected());
    }

    @Test
    void getMatchesExactMqIdAndUsesTransactionalWait() throws Exception {
        String id = "ab".repeat(24);

        doAnswer(inv -> {
            MQMessage m = inv.getArgument(0);
            m.format = MQFMT_STRING;
            m.characterSet = 1208;
            m.writeString("received");
            m.seek(0);
            m.backoutCount = 2;
            return null;
        }).when(queue).get(any(MQMessage.class), any(MQGetMessageOptions.class), anyInt());

        var result = session.getMessage("DEV.LAB.IN", Duration.ofSeconds(2), id).orElseThrow();

        assertEquals(id, result.messageId());
        assertEquals("received", result.text());
        assertEquals(2, result.backoutCount());

        var options = ArgumentCaptor.forClass(MQGetMessageOptions.class);
        verify(queue).get(any(MQMessage.class), options.capture(), eq(1048576));
        assertEquals(MQMO_MATCH_MSG_ID, options.getValue().matchOptions);
        assertEquals(2000, options.getValue().waitInterval);
        assertTrue((options.getValue().options & MQGMO_SYNCPOINT) != 0);
    }

    @Test
    void decodingFailureStillRollsBackRetrievedMessageOnClose() throws Exception {
        doAnswer(inv -> {
            inv.getArgument(0, MQMessage.class).format = MQFMT_NONE;
            return null;
        })
                .when(queue).get(any(MQMessage.class), any(MQGetMessageOptions.class), anyInt());
        assertThrows(IOException.class, () -> session.getMessage("DEV.LAB.IN", Duration.ZERO, null));
        assertEquals(TransactionState.PENDING, session.transactionState());
        session.close();
        verify(manager).backout();
    }

    @Test
    void cleanupAttemptsDisconnectEvenWhenBackoutAndCloseFail() throws Exception {
        session.putMessage("DEV.LAB.IN", "x");
        doThrow(new MQException(2, MQRC_CONNECTION_BROKEN, "backout")).when(manager).backout();
        doThrow(new MQException(2, MQRC_HOBJ_ERROR, "close")).when(queue).close();
        MQException error = assertThrows(MQException.class, session::close);
        assertEquals(1, error.getSuppressed().length);
        verify(manager).disconnect();
        assertFalse(session.isConnected());
    }

    @Test
    void queueDepthUsesInquiryHandleAndHandlesAreCachedByMode() throws Exception {
        when(queue.getCurrentDepth()).thenReturn(7);
        assertEquals(7, session.getInQueueMsgCount("DEV.LAB.IN"));
        session.getInQueueMsgCount("DEV.LAB.IN");
        session.putMessage("DEV.LAB.IN", "x");
        verify(manager, times(1)).accessQueue("DEV.LAB.IN",
                MQOO_INQUIRE | MQOO_FAIL_IF_QUIESCING);
        verify(manager, times(1)).accessQueue("DEV.LAB.IN",
                MQOO_OUTPUT | MQOO_FAIL_IF_QUIESCING);
    }

    @Test
    void rejectsCrossThreadSessionUse() throws Exception {
        var result = CompletableFuture.supplyAsync(() -> assertThrows(IllegalStateException.class,
                session::isConnected));
        assertTrue(result.get().getMessage().contains("owning thread"));
    }

    @Test
    void rejectsInvalidMessageIdWaitAndOversizedUtf8PayloadBeforeQueueGet() {
        assertThrows(IllegalArgumentException.class, () -> session.getMessage("DEV.LAB.IN",
                Duration.ZERO, "bad"));
        assertThrows(IllegalArgumentException.class, () -> session.getMessage("DEV.LAB.IN",
                Duration.ofSeconds(61), null));
        assertThrows(IllegalArgumentException.class, () -> session.putMessage("DEV.LAB.IN", "سلام"
                .repeat(524289)));
        verifyNoInteractions(queue);
    }
}
