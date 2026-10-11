package com.example.wallet.transfer;

import com.example.wallet.common.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TransferQueryController {
    private final JdbcTemplate jdbc;
    public TransferQueryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/transfers/{id}")
    public Map<String, Object> get(@RequestHeader("X-User-Id") long userId, @PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT t.* FROM transfers t JOIN accounts a ON a.id=t.source_account_id
            WHERE t.id=? AND a.user_id=?
            """, id, userId);
        if (rows.isEmpty()) throw new ApiException("RESOURCE_NOT_FOUND", "Transfer not found.", HttpStatus.NOT_FOUND);
        Map<String, Object> transfer = rows.getFirst();
        transfer.put("entries", jdbc.queryForList("SELECT id,account_id,entry_type,amount_paise,created_at FROM ledger_entries WHERE transfer_id=? ORDER BY id", id));
        transfer.put("status_history", jdbc.queryForList("SELECT previous_status,new_status,changed_by,reason,created_at FROM transfer_status_audits WHERE transfer_id=? ORDER BY id", id));
        return transfer;
    }

    @GetMapping("/admin/risk/queue")
    public List<Map<String, Object>> queue(@RequestHeader("X-Admin-Id") long ignored) {
        return jdbc.queryForList("SELECT t.*, rf.risk_label, rf.explanation FROM transfers t JOIN risk_flags rf ON rf.transfer_id=t.id WHERE t.status='HELD' ORDER BY t.created_at");
    }
}
