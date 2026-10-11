# Isolation and locking policy

The posting path uses PostgreSQL `READ COMMITTED` plus `SELECT ... FOR UPDATE` on both accounts in ascending id order. Locks eliminate stale-balance lost updates and the stable order avoids the usual two-account deadlock. Serializable would also prevent anomalies but introduces serialization retries and lower throughput; it is not necessary for this bounded row-locking path.

Run these in two `psql` sessions against a disposable database; values shown are the expected PostgreSQL behavior, not claimed captured output.

```sql
-- A: READ COMMITTED (default)
BEGIN; SELECT balance_paise FROM accounts WHERE id=1;
-- B: UPDATE accounts SET balance_paise=balance_paise+1 WHERE id=1; COMMIT;
-- A reads again and sees B's committed value (non-repeatable read).

-- A: BEGIN ISOLATION LEVEL REPEATABLE READ; SELECT balance_paise FROM accounts WHERE id=1;
-- B commits the update above.  A's second select sees its original snapshot.

-- A and B: BEGIN ISOLATION LEVEL SERIALIZABLE; SELECT balance_paise FROM accounts WHERE id=1;
-- both update based on the read; one COMMIT fails with serialization_failure and must retry.

-- Repeatable Read also keeps joins snapshot-consistent:
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT a.id, count(le.id) FROM accounts a LEFT JOIN ledger_entries le ON le.account_id=a.id GROUP BY a.id;
```
