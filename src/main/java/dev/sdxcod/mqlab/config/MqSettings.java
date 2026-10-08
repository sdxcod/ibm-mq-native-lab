package dev.sdxcod.mqlab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@ConfigurationProperties("lab.mq")
public record MqSettings(String host,
                         int port,
                         String queueManager,
                         String channel,
                         String user,
                         String password,
                         Path passwordFile,
                         String inQueue,
                         String outQueue,
                         String testQueue,
                         int maxPayloadBytes) {

    public MqSettings {
        for (String value : new String[]{host, queueManager, channel, user, inQueue, outQueue, testQueue}) {
            if (value == null || value.isBlank())
                throw new IllegalArgumentException("MQ settings must not be blank");
        }

        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("Invalid MQ port");
        if (maxPayloadBytes < 1 || maxPayloadBytes > 1048576)
            throw new IllegalArgumentException("max-payload-bytes must be 1..1048576 for these lab queues");
    }

    public String resolvePassword() throws IOException {
        if (password != null && !password.isEmpty())
            return password;
        if (passwordFile == null || !Files.isRegularFile(passwordFile))
            throw new IOException("MQ password missing. Run ./scripts/init-lab.sh or set LAB_MQ_PASSWORD_FILE.");

        String value = Files.readString(passwordFile, StandardCharsets.UTF_8);

        if (value.isEmpty())
            throw new IOException("MQ password file is empty");
        return value; // Exact secret bytes: the setup script writes no trailing newline.
    }

    public String queue(String alias) {
        return switch (alias) {
            case "in" -> inQueue;
            case "out" -> outQueue;
            case "test" -> testQueue;
            default -> throw new IllegalArgumentException("Queue must be in, out, or test");
        };
    }

    @Override
    public String toString() {
        return "MqSettings[host=" + host + ", port=" + port + ", queueManager=" + queueManager
                + ", channel=" + channel + ", user=" + user + ", password=<redacted>]";
    }
}
