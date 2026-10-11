package com.example.wallet.reconciliation;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only diagnostic job: it records discrepancies and never repairs money. */
@Service
public class ReconciliationService {
  private final JdbcTemplate jdbc;
  public ReconciliationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  @Transactional(isolation=Isolation.REPEATABLE_READ)
  public Map<String,Object> run() {
    try { jdbc.queryForObject("SELECT id FROM job_locks WHERE job_name='reconciliation' FOR UPDATE NOWAIT",Long.class); }
    catch (Exception busy) { return Map.of("status","SKIPPED","reason","another reconciliation is running"); }
    long run=jdbc.queryForObject("INSERT INTO reconciliation_runs(started_at,status) VALUES(now(),'RUNNING') RETURNING id",Long.class);
    int issues=0;
    issues+=record(run,"UNBALANCED_TRANSFER","transfer", "SELECT transfer_id::text, COALESCE(sum(amount_paise) FILTER (WHERE entry_type='DEBIT'),0)::text, COALESCE(sum(amount_paise) FILTER (WHERE entry_type='CREDIT'),0)::text FROM ledger_entries GROUP BY transfer_id HAVING COALESCE(sum(amount_paise) FILTER (WHERE entry_type='DEBIT'),0)<>COALESCE(sum(amount_paise) FILTER (WHERE entry_type='CREDIT'),0)");
    issues+=record(run,"BALANCE_MISMATCH","account", "SELECT a.id::text, a.balance_paise::text, COALESCE(sum(CASE WHEN le.entry_type='CREDIT' THEN le.amount_paise ELSE -le.amount_paise END),0)::text FROM accounts a LEFT JOIN ledger_entries le ON le.account_id=a.id GROUP BY a.id HAVING a.balance_paise<>COALESCE(sum(CASE WHEN le.entry_type='CREDIT' THEN le.amount_paise ELSE -le.amount_paise END),0)");
    issues+=record(run,"ORPHAN_TRANSFER","transfer", "SELECT t.id::text, 'entries'::text, '0'::text FROM transfers t LEFT JOIN ledger_entries le ON le.transfer_id=t.id GROUP BY t.id HAVING count(le.id)=0");
    jdbc.update("UPDATE reconciliation_runs SET finished_at=now(),status='COMPLETED',checks_executed=3,problems_found=?,summary=? WHERE id=?",issues,"Read-only ledger reconciliation",run);
    return Map.of("id",run,"status","COMPLETED","problems_found",issues);
  }
  private int record(long run,String code,String type,String sql) { List<Map<String,Object>> rows=jdbc.queryForList(sql); for(Map<String,Object> r:rows) jdbc.update("INSERT INTO reconciliation_issues(run_id,check_code,entity_type,entity_id,expected_value,actual_value,description) VALUES (?,?,?,CAST(? AS bigint),?,?,?)",run,code,type,r.values().toArray()[0],r.values().toArray()[1],r.values().toArray()[2],code+" detected"); return rows.size(); }
}
