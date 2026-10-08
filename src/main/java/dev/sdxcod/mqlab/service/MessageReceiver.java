package dev.sdxcod.mqlab.service;

import com.ibm.mq.MQException;
import dev.sdxcod.mqlab.transport.MqSessionFactory;
import dev.sdxcod.mqlab.transport.ReceivedMessage;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public final class MessageReceiver {
    private final MqSessionFactory factory;

    public MessageReceiver(MqSessionFactory factory) {
        this.factory = factory;
    }

    public Optional<ReceivedMessage> receive(String queue, Duration wait, String id, Outcome outcome)
            throws MQException, IOException {

        try (var session = factory.create()) {
            session.connect();
            var message = session.getMessage(queue, wait, id);
            if (message.isPresent())
                outcome.complete(session);
            return message;
        }
    }
}
