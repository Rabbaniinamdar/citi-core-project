package com.citicore.transaction.client;

import com.citicore.transaction.config.FeignClientConfig;
import com.citicore.transaction.dto.BalanceUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@FeignClient(
        name = "account-service",
        configuration = FeignClientConfig.class
)
public interface AccountFeignClient {

    @PostMapping("/api/v1/accounts/internal/balance-update")
    void updateBalance(
            @RequestBody BalanceUpdateRequest request
    );

    @GetMapping("/api/v1/accounts/validate-transfer")
    void validateTransfer(
            @RequestParam("accNo") String accNo,
            @RequestParam("amount") BigDecimal amount
    );
}