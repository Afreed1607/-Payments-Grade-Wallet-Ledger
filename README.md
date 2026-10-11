# Payments-Grade Wallet Ledger

Spring Boot/PostgreSQL reference wallet backend. Start it with `mvn package` followed by `docker compose up --build`. It uses PostgreSQL 16, Flyway migrations, integer paise, JDBC for the money path, and a health endpoint at `/actuator/health`.

## Guarantees

- Every completed movement creates one immutable debit and one immutable credit in the same transaction.
- User-wallet cached balances are non-negative; system accounts may be negative. Cached balances and entries update atomically.
- Peer transfers lock both accounts in ascending identifier order under Read Committed, preventing overspend and common account-pair deadlocks.
- Every transfer POST requires a UUID `Idempotency-Key`. `(user_id,key)` is unique, same-payload retries replay the persisted response, different payloads return 422, and in-progress work returns 409.
- Risk rules are data in `risk_rules`; hold rules post no entries or balance changes until an admin releases the transfer.
- Reconciliation runs in a repeatable-read transaction, stores findings, and never repairs financial data.

The demo gateway identity is supplied as `X-User-Id` (and `X-Admin-Id` for an admin decision). In a production deployment these headers must be set only by an authenticated API gateway/JWT resource server; they are deliberately not a substitute for that boundary.

## Main API

`POST /api/v1/transfers`, `/transfers/topup`, and `/transfers/withdraw` accept integer `amountPaise`; transfer responses are 201 or 202 when held. `POST /api/v1/admin/risk/{id}/decision` releases or rejects a hold. `POST /api/v1/admin/reconciliation/run` is the manual read-only check. Users and wallets also support the existing `/api/v1/users/{userId}/wallets` balance and statement endpoints.

See [schema documentation](docs/schema.md) and [isolation lab](docs/isolation.md). The database is intentionally the source of truth: ledger entries are immutable and a reversal must be represented as a new transfer. Current scope does not yet include a JWT issuer, scheduled reconciliation trigger, LLM statement Q&A, seed data, or the required Testcontainers acceptance suite; those are the remaining delivery milestones.
