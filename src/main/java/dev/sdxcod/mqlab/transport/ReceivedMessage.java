package dev.sdxcod.mqlab.transport;

public record ReceivedMessage(String messageId,
                              String correlationId,
                              String text,
                              int backoutCount,
                              boolean persistent) {
}
