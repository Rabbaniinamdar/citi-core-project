package com.citicore.account.controller;

import com.citicore.account.dto.AccountResponse;
import com.citicore.account.dto.ApiResponse;
import com.citicore.account.dto.CreateAccountRequest;
import com.citicore.account.dto.DepositWithdrawRequest;
import com.citicore.account.entity.Account;
import com.citicore.account.entity.AccountStatement;
import com.citicore.account.entity.AuthUser;
import com.citicore.account.service.AccountService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * POST /api/v1/account/create
     * Creates a new Savings or Current account for the authenticated user.
     * Requires KYC to be completed.
     *
     * Body: { "accountType": "SAVINGS", "initialDeposit": 5000 }
     */
        @PostMapping("/create")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @RequestBody CreateAccountRequest request) {

        AuthUser authUser = getAuthUser();

        Account account = accountService.createAccount(
                request.getAccountType(),
                request.getInitialDeposit(),
                authUser.getId(),
                authUser.getEmail()
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Account created successfully",
                AccountResponse.from(account)
        ));
    }

    /**
     * GET /api/v1/account/my-accounts
     * Returns all accounts owned by the authenticated user.
     */
    @GetMapping("/my-accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts() {
        AuthUser authUser = getAuthUser();
        List<Account> accounts = accountService.getMyAccounts(authUser.getId());
        List<AccountResponse> response = accounts.stream()
                .map(AccountResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Accounts fetched", response));
    }

    /**
     * GET /api/v1/account/balance/{accNo}
     * Returns the current balance for the given account number.
     * Served from Redis cache (Cache-Aside pattern) — fast ⚡
     */
    @GetMapping("/balance/{accNo}")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(
            @PathVariable String accNo) {
        BigDecimal balance = accountService.getBalance(accNo);
        return ResponseEntity.ok(ApiResponse.success("Balance fetched", balance));
    }

    /**
     * GET /api/v1/account/mini-statement/{accNo}
     * Returns the last 10 transactions for the given account.
     */
    @GetMapping("/mini-statement/{accNo}")
    public ResponseEntity<ApiResponse<List<AccountStatement>>> getMiniStatement(
            @PathVariable String accNo) {
        List<AccountStatement> statements = accountService.getMiniStatement(accNo);
        return ResponseEntity.ok(ApiResponse.success("Mini statement fetched", statements));
    }

    /**
     * GET /api/v1/account/statement/{accNo}?page=0&size=10
     * Returns paginated full statement for the given account.
     */
    @GetMapping("/statement/{accNo}")
    public ResponseEntity<ApiResponse<Page<AccountStatement>>> getStatement(
            @PathVariable String accNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<AccountStatement> statement = accountService.getStatement(accNo, page, size);
        return ResponseEntity.ok(ApiResponse.success("Statement fetched", statement));
    }

    /**
     * POST /api/v1/account/deposit
     * Deposits money into an account.
     *
     * Body: { "accNo": "CITI000000000001", "amount": 10000, "txnRef": "DEP_xxx" }
     */
    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<String>> deposit(
            @RequestBody DepositWithdrawRequest request) {
        AuthUser authUser = getAuthUser();
        accountService.deposit(
                request.getAccNo(),
                request.getAmount(),
                request.getTxnRef(),
                authUser.getId(),
                authUser.getEmail()
        );
        return ResponseEntity.ok(ApiResponse.success("Deposit successful", request.getAccNo()));
    }

    /**
     * POST /api/v1/account/withdraw
     * Withdraws money from an account.
     *
     * Body: { "accNo": "CITI000000000001", "amount": 5000, "txnRef": "WIT_xxx" }
     */
    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<String>> withdraw(
            @RequestBody DepositWithdrawRequest request) {
        AuthUser authUser = getAuthUser();
        accountService.withdraw(
                request.getAccNo(),
                request.getAmount(),
                request.getTxnRef(),
                authUser.getId(),
                authUser.getEmail()
        );
        return ResponseEntity.ok(ApiResponse.success("Withdrawal successful", request.getAccNo()));
    }

    /**
     * GET /api/v1/account/validate-transfer
     * Called by transaction-service via Feign for pre-validation (fail-fast).
     * Non-locking balance check before the saga starts.
     */
    @GetMapping("/validate-transfer")
    public ResponseEntity<Void> validateTransfer(
            @RequestParam String accNo,
            @RequestParam BigDecimal amount) {
        accountService.validateTransfer(accNo, amount);
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private AuthUser getAuthUser() {
        return (AuthUser) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}