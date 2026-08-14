package com.citicore.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${kyc.upload-dir}")
    private String uploadDir;

    public String storeFile(MultipartFile file, String userId) throws Exception {

        String basePath = new File(uploadDir).getAbsolutePath();

        String dirPath = basePath + File.separator + userId;

        File dir = new File(dirPath);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        File target = new File(dir, fileName);

        file.transferTo(target);
        System.out.println("UPLOAD DIR = " + uploadDir);
        return userId + "/" + fileName;
    }
}