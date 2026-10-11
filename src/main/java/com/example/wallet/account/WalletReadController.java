package com.example.wallet.account;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Contract aliases that identify the wallet first, while enforcing its owner. */
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletReadController {
    private final JdbcTemplate jdbc;
    public WalletReadController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/{id}/balance")
    public Map<String, Object> balance(@RequestHeader("X-User-Id") long user, @PathVariable long id) {
        return jdbc.queryForMap("SELECT id,balance_paise,currency,status FROM accounts WHERE id=? AND user_id=?", id, user);
    }
    @GetMapping("/{id}/statement")
    public List<Map<String, Object>> statement(@RequestHeader("X-User-Id") long user, @PathVariable long id,
        @RequestParam(defaultValue="50") int limit, @RequestParam(defaultValue="0") int offset) {
        return jdbc.queryForList("SELECT le.id,le.transfer_id,le.entry_type,le.amount_paise,le.created_at FROM ledger_entries le JOIN accounts a ON a.id=le.account_id WHERE a.id=? AND a.user_id=? ORDER BY le.created_at DESC,le.id DESC LIMIT ? OFFSET ?", id,user,Math.clamp(limit,1,100),Math.max(offset,0));
    }
}
