package dev.sdxcod.mqlab.service;

import com.ibm.mq.MQException;
import dev.sdxcod.mqlab.transport.MqSessionFactory;

import java.io.IOException;

public final class MessageSender {
    private final MqSessionFactory factory;

    public MessageSender(MqSessionFactory factory) {
        this.factory = factory;
    }

    public String send(String queue, String text, Outcome outcome) throws MQException, IOException {
        try (var session = factory.create()) {
            session.connect();
            String id = session.putMessage(queue, text);
            outcome.complete(session);
            return id;
        }
    }
}
