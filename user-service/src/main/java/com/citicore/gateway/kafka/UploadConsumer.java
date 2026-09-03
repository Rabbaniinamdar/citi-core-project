//package com.citicore.account.kafka;
//
//import com.citicore.events.kyc.KycEvent;
//import com.citicore.account.service.VerificationService;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Service;
//
//@Service
//public class UploadConsumer {
//
//    private final VerificationService verificationService;
//
//    public UploadConsumer(VerificationService verificationService) {
//        this.verificationService = verificationService;
//    }
//
//    @KafkaListener(topics = "kyc-uploaded", groupId = "kyc-upload-group")
//    public void consume(KycEvent event) {
//        verificationService.verify(event);
//    }
//}