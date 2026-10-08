package dev.sdxcod.mqlab.transport;

@FunctionalInterface
public interface MqSessionFactory {
    MqSession create();
}
