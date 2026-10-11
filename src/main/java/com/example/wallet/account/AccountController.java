package com.example.wallet.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/wallets")
public class AccountController {
    private final JdbcTemplate jdbc;

    public AccountController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    public Map<String, Object> open(@PathVariable long userId, @RequestHeader("Idempotency-Key") UUID idempotencyKey, @Valid @RequestBody OpenWallet request) {
        Long id = jdbc.queryForObject("INSERT INTO accounts(user_id, account_type, name) VALUES (?, ?, ?) RETURNING id", Long.class, userId, request.accountType(), request.name());
        return Map.of("id", id, "user_id", userId, "account_type", request.accountType(), "name", request.name(), "currency", "INR", "balance_paise", 0L);
    }

    @GetMapping("/{walletId}/balance")
    public Map<String, Object> balance(@PathVariable long userId, @PathVariable long walletId) {
        return jdbc.queryForMap("SELECT id, balance_paise, currency, status FROM accounts WHERE id = ? AND user_id = ?", walletId, userId);
    }

    @GetMapping("/{walletId}/statement")
    public java.util.List<Map<String, Object>> statement(@PathVariable long userId, @PathVariable long walletId, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int offset) {
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        int boundedOffset = Math.max(offset, 0);
        return jdbc.queryForList("SELECT le.id, le.transfer_id, le.entry_type, le.amount_paise, le.created_at FROM ledger_entries le JOIN accounts a ON a.id = le.account_id WHERE a.id = ? AND a.user_id = ? ORDER BY le.created_at DESC, le.id DESC LIMIT ? OFFSET ?", walletId, userId, boundedLimit, boundedOffset);
    }

    public record OpenWallet(@NotBlank String name, @NotBlank String accountType) {}
}
