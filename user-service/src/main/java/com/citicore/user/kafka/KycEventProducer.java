package com.citicore.user.kafka;

import com.citicore.events.kyc.KycEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KycEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KycEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String topic, KycEvent event) {
        kafkaTemplate.send(topic, event);
    }
}