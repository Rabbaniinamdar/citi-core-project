package com.citicore.account.client;

import com.citicore.account.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service",configuration = FeignClientConfig.class)
public interface UserClient {

    @GetMapping("/api/v1/users/kyc-status/{userId}")
    boolean getKycStatus(@PathVariable Long userId);
}