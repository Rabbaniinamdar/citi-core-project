package com.citicor.auth.kafka;

import com.citicore.events.otp.VerificationOtpEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OtpEventPublisher {

    private final KafkaTemplate<String, Object>
            kafkaTemplate;

    private static final String OTP_TOPIC =
            "otp-topic";

    public OtpEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishVerificationOtp(
            VerificationOtpEvent event
    ) {

        kafkaTemplate.send(
                OTP_TOPIC,
                event.getUserId().toString(),
                event
        );
    }
}