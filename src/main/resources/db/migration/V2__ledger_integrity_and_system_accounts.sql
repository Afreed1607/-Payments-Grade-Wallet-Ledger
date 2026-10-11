-- System accounts are the counterparties for cash movement.  They deliberately
-- have no user owner and may carry a negative balance.
INSERT INTO accounts (account_type, name, currency)
SELECT v.account_type, v.name, 'INR'
FROM (VALUES ('CASH_IN', 'Cash-in clearing'), ('CASH_OUT', 'Cash-out clearing'), ('FEE_REVENUE', 'Fee revenue')) AS v(account_type, name)
WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.account_type = v.account_type AND a.user_id IS NULL);

CREATE INDEX IF NOT EXISTS transfers_completed_source_created_idx
    ON transfers (source_account_id, created_at DESC)
    WHERE status = 'COMPLETED';
CREATE INDEX IF NOT EXISTS transfers_completed_destination_created_idx
    ON transfers (destination_account_id, created_at DESC)
    WHERE status = 'COMPLETED';

-- A transfer must still be balanced if an application attempts to mark it
-- completed after its entries were inserted (the V1 trigger covers inserts).
CREATE OR REPLACE FUNCTION enforce_completed_transfer_balance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE debit_total BIGINT; credit_total BIGINT; entry_count INTEGER;
BEGIN
  IF NEW.status = 'COMPLETED' THEN
    SELECT count(*), COALESCE(sum(amount_paise) FILTER (WHERE entry_type = 'DEBIT'), 0),
           COALESCE(sum(amount_paise) FILTER (WHERE entry_type = 'CREDIT'), 0)
      INTO entry_count, debit_total, credit_total
      FROM ledger_entries WHERE transfer_id = NEW.id;
    IF entry_count < 2 OR debit_total <> credit_total THEN
      RAISE EXCEPTION 'completed transfer % must have at least two balanced entries', NEW.id;
    END IF;
  END IF;
  RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER transfers_completed_balanced
AFTER INSERT OR UPDATE OF status ON transfers DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_completed_transfer_balance();
