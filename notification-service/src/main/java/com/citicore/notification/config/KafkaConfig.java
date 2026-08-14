package com.citicore.notification.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    // ─────────────────────── PRODUCER ───────────────────────────────────────────
    // Used by DeadLetterPublishingRecoverer — sends Object payloads with error headers
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRIES_CONFIG, 10);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ─────────────────────── CONSUMER ───────────────────────────────────────────
    // ✅ StringDeserializer — all topics come from outbox publishers which send
    //    raw JSON strings. Using JsonDeserializer here would cause double-encoding
    //    deserialization failures. We parse manually inside each listener.
    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    // ─────────────────────── RETRY + DLQ ERROR HANDLER ──────────────────────────
    // Exponential backoff: attempt 1 → 2s wait, attempt 2 → 4s wait, attempt 3 → 8s wait
    // After 3 failures → DeadLetterPublishingRecoverer routes to <topic>.DLT
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);
        backOff.setMaxAttempts(3);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    // ─────────────────────── LISTENER FACTORY ───────────────────────────────────
    // Single shared factory for ALL @KafkaListener methods.
    // MANUAL ack mode — offset committed only after ack.acknowledge() is called
    // inside the listener (i.e. after email is successfully sent).
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaListenerContainerFactory(DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(errorHandler); // ✅ retry + DLQ attached
        return factory;
    }
}