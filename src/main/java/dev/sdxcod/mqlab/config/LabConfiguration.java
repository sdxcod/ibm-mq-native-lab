package dev.sdxcod.mqlab.config;

import dev.sdxcod.mqlab.service.MessageReceiver;
import dev.sdxcod.mqlab.service.MessageSender;
import dev.sdxcod.mqlab.transport.MqSessionFactory;
import dev.sdxcod.mqlab.transport.NativeMqSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LabConfiguration {
    @Bean
    MqSessionFactory sessionFactory(MqSettings settings) {
        return () -> new NativeMqSession(settings);
    }

    @Bean
    MessageSender sender(MqSessionFactory factory) {
        return new MessageSender(factory);
    }

    @Bean
    MessageReceiver receiver(MqSessionFactory factory) {
        return new MessageReceiver(factory);
    }
}
