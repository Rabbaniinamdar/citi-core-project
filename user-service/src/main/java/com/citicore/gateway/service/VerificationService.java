//package com.citicore.user.service;
//
//import com.citicore.events.kyc.KycEvent;
//import com.citicore.events.kyc.KycEventType;
//import com.citicore.user.entity.KycDocument;
//import com.citicore.user.kafka.KycEventProducer;
//import com.citicore.user.repository.KycDocumentRepository;
//import jakarta.transaction.Transactional;
//import org.springframework.stereotype.Service;
//
//@Service
//public class VerificationService {
//
//    private final KycDocumentRepository repo;
//    private final KycEventProducer producer;
//
//    public VerificationService(KycDocumentRepository repo,
//                               KycEventProducer producer) {
//        this.repo = repo;
//        this.producer = producer;
//    }
//
//    @Transactional
//    public void verify(KycEvent event) {
//
//        KycDocument doc = repo.findById(event.getDocumentId())
//                .orElseThrow(() -> new RuntimeException("Document not found"));
//
//        if (doc.getStatus() == KycDocument.Status.VERIFIED) return;
//
//        boolean isValid = event.getFilePath() != null
//                && !event.getFilePath().contains("fake");
//
//        doc.setStatus(isValid ?
//                KycDocument.Status.VERIFIED :
//                KycDocument.Status.REJECTED);
//
//        repo.save(doc);
//
//        KycEventType type = isValid ?
//                KycEventType.DOC_VERIFIED :
//                KycEventType.DOC_REJECTED;
//
//        producer.send("kyc-result", new KycEvent(
//                type,
//                doc.getId(),
//                event.getUserId(),
//                event.getEmail(),
//                event.getFilePath()
//        ));
//    }
//}