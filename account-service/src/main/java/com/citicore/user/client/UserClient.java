package com.citicore.user.client;

import com.citicore.user.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service",configuration = FeignClientConfig.class)
public interface UserClient {

    @GetMapping("/api/v1/users/{userId}/kyc-status")
    boolean getKycStatus(@PathVariable Long userId);
}