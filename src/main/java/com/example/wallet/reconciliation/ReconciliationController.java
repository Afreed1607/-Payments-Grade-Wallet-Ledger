package com.example.wallet.reconciliation;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/reconciliation") public class ReconciliationController {
 private final ReconciliationService service; private final JdbcTemplate jdbc;
 public ReconciliationController(ReconciliationService service, JdbcTemplate jdbc){this.service=service;this.jdbc=jdbc;}
 @PostMapping("/run") public Map<String,Object> run(@RequestHeader("Idempotency-Key") String ignored){return service.run();}
 @GetMapping("/runs/{id}") public Map<String,Object> get(@PathVariable long id){return jdbc.queryForMap("SELECT * FROM reconciliation_runs WHERE id=?",id);}
}
