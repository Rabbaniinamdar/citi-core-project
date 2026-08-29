    package com.citicor.auth.config;
    
    import org.apache.kafka.clients.consumer.ConsumerConfig;
    import org.apache.kafka.clients.producer.ProducerConfig;
    import org.apache.kafka.common.serialization.StringDeserializer;
    import org.apache.kafka.common.serialization.StringSerializer;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
    import org.springframework.kafka.core.*;
    import org.springframework.kafka.listener.ContainerProperties;
    import org.springframework.kafka.support.serializer.JsonSerializer;
    
    import java.util.HashMap;
    import java.util.Map;
    
    @Configuration
    public class KafkaConfig {

        @Value("${spring.kafka.bootstrap-servers}")
        private String bootstrapServers;
        // ─────────────────────── PRODUCERS ───────────────────────

        // ✅ For direct sends (POJOs) — used by DebitConsumer/CreditConsumer internally
        @Bean("jsonProducerFactory")
        public ProducerFactory<String, Object> jsonProducerFactory() {
            Map<String, Object> config = new HashMap<>();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            config.put(ProducerConfig.RETRIES_CONFIG, 10);
            config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
            return new DefaultKafkaProducerFactory<>(config);
        }
    
        // ✅ For outbox publisher — payload is already a JSON string, use StringSerializer
        //    to avoid JsonSerializer wrapping it in extra quotes → "\"{\\"txnRef\\"...}\""
        @Bean("stringProducerFactory")
        public ProducerFactory<String, String> stringProducerFactory() {
            Map<String, Object> config = new HashMap<>();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class); // ✅
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            config.put(ProducerConfig.RETRIES_CONFIG, 10);
            config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
            return new DefaultKafkaProducerFactory<>(config);
        }
    
        // ─────────────────────── TEMPLATES ───────────────────────

        // ✅ Used by DebitConsumer/CreditConsumer/ReversalConsumer for POJO sends (if any direct sends remain)
        @Bean("jsonKafkaTemplate")
        public KafkaTemplate<String, Object> kafkaTemplate() {
            return new KafkaTemplate<>(jsonProducerFactory());
        }
    
        // ✅ Used exclusively by AccountOutboxPublisher — sends raw JSON string as-is
        @Bean("stringKafkaTemplate")
        public KafkaTemplate<String, String> stringKafkaTemplate() {
            return new KafkaTemplate<>(stringProducerFactory());
        }
    }