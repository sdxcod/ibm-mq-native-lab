package dev.sdxcod.mqlab.cli;

import com.ibm.mq.MQException;
import dev.sdxcod.mqlab.config.MqSettings;
import dev.sdxcod.mqlab.service.MessageReceiver;
import dev.sdxcod.mqlab.service.MessageSender;
import dev.sdxcod.mqlab.service.Outcome;
import dev.sdxcod.mqlab.transport.MqSessionFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
public final class LabRunner implements ApplicationRunner {
    private final MqSettings settings;
    private final MqSessionFactory factory;
    private final MessageSender sender;
    private final MessageReceiver receiver;
    private final Environment env;

    public LabRunner(MqSettings settings, MqSessionFactory factory, MessageSender sender,
                     MessageReceiver receiver, Environment env) {
        this.settings = settings;
        this.factory = factory;
        this.sender = sender;
        this.receiver = receiver;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String command = env.getProperty("lab.command", "help");
        if (command.equals("help")) {
            help();
            return;
        }
        String queue = settings.queue(env.getProperty("lab.queue", "in"));
        Outcome outcome = Outcome.parse(env.getProperty("lab.outcome", "commit"));
        Duration wait = env.getProperty("lab.wait", Duration.class, Duration.ofSeconds(3));
        try {
            switch (command) {
                case "send" -> System.out.println(outcome + " messageId=" +
                        sender.send(queue,
                                env.getProperty("lab.message", "Hello from dev.sdxcod"),
                                outcome));
                case "receive" -> receiver.receive(queue, wait, env.getProperty("lab.message-id"), outcome)
                        .ifPresentOrElse(m ->
                                        System.out.println(outcome + " " + m),
                                () -> System.out.println("No message available"));
                case "depth", "status" -> {
                    try (var s = factory.create()) {
                        s.connect();
                        System.out.println("connected(local)=" + s.isConnected() + ", queue=" + queue
                                + ", depth(server inquiry)=" + s.getInQueueMsgCount(queue));
                    }
                }
                case "demo" -> demo();
                case "shell" -> shell();
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            }
        } catch (MQException e) {
            System.err.println("IBM MQ failure: completion=" + e.completionCode + ", reason=" + e.reasonCode);
            throw e;
        }
    }

    private void demo() throws Exception {
        String queue = settings.testQueue();
        String text = "native-demo-" + UUID.randomUUID() + " | سلام IBM MQ";
        String committedId = null;
        try {

            String rolledBackId = sender.send(queue, "discard-" + text, Outcome.BACKOUT);

            var absent = receiver.receive(queue, Duration.ZERO, rolledBackId, Outcome.COMMIT);

            if (absent.isPresent())
                throw new IllegalStateException("Rolled-back put became visible");

            System.out.println("PASS: backout of put leaves no matching message");

            committedId = sender.send(queue, text, Outcome.COMMIT);

            var first = receiver.receive(queue, Duration.ofSeconds(3), committedId, Outcome.BACKOUT).orElseThrow();
            if (!first.text().equals(text))
                throw new IllegalStateException("UTF-8 text changed");

            var second = receiver.receive(queue, Duration.ofSeconds(3), committedId, Outcome.COMMIT).orElseThrow();
            if (second.backoutCount() < 1)
                throw new IllegalStateException("Redelivery did not record backout");

            System.out.println("PASS: commit of put preserves UTF-8 text; backout of get allows redelivery");

            if (receiver.receive(queue, Duration.ZERO, committedId, Outcome.COMMIT).isPresent())
                throw new IllegalStateException("Committed get did not remove the message");
            System.out.println("PASS: commit of get removes the matching message");
        } finally {
            if (committedId != null) receiver.receive(queue, Duration.ZERO, committedId, Outcome.COMMIT);
        }
    }

    private void shell() throws Exception {

        System.out.println("Shell: help, connect, disconnect, status, depth, put, get, commit, backout, quit.");
        System.out.println("Every put/get is pending until commit/backout. EOF/quit rolls back pending work.");

        try (var session = factory.create()) {

            BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            while (true) {
                System.out.print("mq> ");
                System.out.flush();
                String line = input.readLine();

                if (line == null)
                    return;

                String[] parts = line.trim().split("\s+", 3);

                if (parts[0].isBlank())
                    continue;
                try {
                    switch (parts[0]) {
                        case "quit", "exit" -> {
                            return;
                        }
                        case "help" ->
                                System.out.println("connect | disconnect | status | depth in/out/test | put in/out/test TEXT | get in/out/test [48-hex-ID] | commit | backout | quit");
                        case "connect" -> {
                            session.connect();
                            System.out.println("Connected");
                        }
                        case "disconnect" -> {
                            session.disconnect();
                            System.out.println("Disconnected");
                        }
                        case "status" -> System.out.println("connected(local)=" + session.isConnected() +
                                ", transaction=" + session.transactionState());
                        case "depth" ->
                                System.out.println(session.getInQueueMsgCount(settings.queue(argument(parts, 1))));
                        case "put" -> System.out.println("PENDING messageId=" +
                                session.putMessage(settings.queue(argument(parts, 1)),
                                        argument(parts, 2)));
                        case "get" -> System.out.println(session.getMessage(settings.queue(argument(parts, 1)),
                                Duration.ofSeconds(3), parts.length > 2 ? parts[2] : null));
                        case "commit" -> {
                            session.commit();
                            System.out.println("Committed");
                        }
                        case "backout" -> {
                            session.backout();
                            System.out.println("Backed out");
                        }
                        default -> System.out.println("Unknown command. Type help.");
                    }
                } catch (MQException e) {
                    System.err.println("MQ completion=" + e.completionCode + ", reason=" + e.reasonCode
                            + ", transaction=" + session.transactionState());
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        }
    }

    private static String argument(String[] parts, int index) {
        if (parts.length <= index) throw new IllegalArgumentException("Missing argument. Type help.");
        return parts[index];
    }

    private void help() {
        System.out.println("IBM MQ native Java lab — Spring Boot configuration, no JMS transport");
        System.out.println("Commands: --lab.command=help|demo|shell|send|receive|depth|status");
        System.out.println("Options: --lab.queue=in|out|test --lab.message=TEXT --lab.outcome=commit|backout --lab.wait=3s --lab.message-id=48_HEX");
        System.out.println("Quick start: ./scripts/init-lab.sh && docker compose up -d --wait mq && ./scripts/lab.sh demo");
    }
}
