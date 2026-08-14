package com.citicore.transaction.client;

import com.citicore.transaction.dto.BalanceUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "account-service")
public interface AccountFeignClient {

    @PostMapping("/api/v1/accounts/internal/balance-update")
    void updateBalance(@RequestBody BalanceUpdateRequest request);

    @GetMapping("/api/v1/accounts/validate-transfer")
    void validateTransfer(@RequestParam("account") String account,
                          @RequestParam("amount") BigDecimal amount);
}