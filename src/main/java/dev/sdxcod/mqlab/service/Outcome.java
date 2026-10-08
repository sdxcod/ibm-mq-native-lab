package dev.sdxcod.mqlab.service;

import com.ibm.mq.MQException;
import dev.sdxcod.mqlab.transport.MqSession;

import java.util.Locale;

public enum Outcome {
    COMMIT, BACKOUT;

    public static Outcome parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    public void complete(MqSession session) throws MQException {
        if (this == COMMIT) session.commit();
        else session.backout();
    }
}
