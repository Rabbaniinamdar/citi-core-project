package com.citicore.user.fallback;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/customers")
    public String customerFallback() {
        return "Customer Service is down. Please try again later.";
    }

    @RequestMapping("/fallback/orders")
    public String orderFallback() {
        return "Order Service is down. Please try again later.";
    }

    @RequestMapping("/fallback/auth")
    public String authFallback() {
        return "Authentication Service is down. Please try again later.";
    }

    @RequestMapping("/fallback/users")
    public String userFallback() {
        return "User Service is down. Please try again later.";
    }
    @RequestMapping("/fallback/account")
    public String accountFallback() {
        return "Account Service is down. Please try again later.";
    }

    @RequestMapping("/fallback/transaction")
    public String transactionFallback() {
        return "Transaction Service is down. Please try again later.";
    }
}