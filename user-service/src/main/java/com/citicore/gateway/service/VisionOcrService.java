//package com.citicore.user.service;
//
//import com.google.cloud.vision.v1.*;
//import com.google.protobuf.ByteString;
//import org.springframework.stereotype.Service;
//
//import java.io.FileInputStream;
//import java.util.List;
//
//@Service
//public class VisionOcrService {
//
//    public String extractText(String filePath) {
//
//        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
//
//            ByteString imgBytes = ByteString.readFrom(new FileInputStream(filePath));
//
//            Image img = Image.newBuilder().setContent(imgBytes).build();
//
//            Feature feat = Feature.newBuilder()
//                    .setType(Feature.Type.TEXT_DETECTION)
//                    .build();
//
//            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
//                    .addFeatures(feat)
//                    .setImage(img)
//                    .build();
//
//            BatchAnnotateImagesResponse response =
//                    client.batchAnnotateImages(List.of(request));
//
//            List<AnnotateImageResponse> responses = response.getResponsesList();
//
//            for (AnnotateImageResponse res : responses) {
//
//                if (res.hasError()) {
//                    throw new RuntimeException("Vision API Error: " + res.getError().getMessage());
//                }
//
//                return res.getFullTextAnnotation().getText();
//            }
//
//        } catch (Exception e) {
//            throw new RuntimeException("OCR failed", e);
//        }
//
//        return null;
//    }
//}
