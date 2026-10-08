package dev.sdxcod.mqlab.transport;

import com.ibm.mq.MQException;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * A thread-confined unit of work. Commit/backout cover ALL puts/gets on this connection.
 */
public interface MqSession extends AutoCloseable {
    void connect() throws MQException, IOException;

    void disconnect() throws MQException;

    boolean isConnected();

    String putMessage(String queue, String text) throws MQException, IOException;

    Optional<ReceivedMessage> getMessage(String queue, Duration wait, String messageId) throws MQException, IOException;

    int getInQueueMsgCount(String queue) throws MQException;

    void commit() throws MQException;

    void backout() throws MQException;

    TransactionState transactionState();

    @Override
    default void close() throws MQException {
        disconnect();
    }
}
