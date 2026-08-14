package com.citicore.transaction.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
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

    // ─────────────────────── PRODUCERS ───────────────────────────────────────────

    /**
     * JSON producer — used by DeadLetterPublishingRecoverer (DLQ).
     * Sends Object payloads with Kafka error headers attached by Spring.
     */
    @Bean("jsonProducerFactory")
    public ProducerFactory<String, Object> jsonProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRIES_CONFIG, 10);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * String producer — used exclusively by OutboxPublisher.
     *
     * CRITICAL: The outbox table stores payload as a JSON string.
     * Using JsonSerializer here would serialize the String again → double encoding:
     *   DB payload:  {"txnRef":"CITI-001",...}
     *   JsonSerializer output: "{\"txnRef\":\"CITI-001\",...}"  ← WRONG ❌
     *   StringSerializer output: {"txnRef":"CITI-001",...}      ← CORRECT ✅
     */
    @Bean("stringProducerFactory")
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRIES_CONFIG, 10);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        return new DefaultKafkaProducerFactory<>(config);
    }

    // ─────────────────────── CONSUMER ────────────────────────────────────────────

    /**
     * StringDeserializer consumer factory.
     *
     * All topics consumed by transaction-service originate from account-service's
     * AccountOutboxPublisher which sends raw JSON strings via StringSerializer.
     * Using JsonDeserializer here would cause:
     *   Cannot convert from [String] to [CreditSuccessEvent]
     *
     * Solution: receive as String → objectMapper.readValue() inside each listener.
     */
    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "transaction-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    // ─────────────────────── RETRY + DLQ ERROR HANDLER ───────────────────────────

    /**
     * Exponential backoff retry with Dead Letter Queue routing.
     *
     * Retry schedule:
     *   Attempt 1 fails → wait 2 seconds
     *   Attempt 2 fails → wait 4 seconds
     *   Attempt 3 fails → wait 8 seconds
     *   All failed → DeadLetterPublishingRecoverer → <topic>.DLT
     *
     * DLT messages are consumed by TransactionDLQConsumer,
     * persisted to dead_letter_events table, and can be replayed
     * via POST /api/v1/admin/dlq/replay/{id}
     */
    @Bean
    public DefaultErrorHandler errorHandler(
            @Qualifier("jsonKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);
        backOff.setMaxAttempts(3);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    // ─────────────────────── LISTENER FACTORY ────────────────────────────────────

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaListenerContainerFactory(DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ─────────────────────── TEMPLATES ───────────────────────────────────────────

    @Bean("jsonKafkaTemplate")
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(jsonProducerFactory());
    }

    @Bean("stringKafkaTemplate")
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }
}