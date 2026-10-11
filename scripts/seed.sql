-- Development-scale seed: 1,000 users and 2,000 wallets.
-- Generate transfers through the API so ledger and cache invariants hold.
INSERT INTO users(name,email,phone,kyc_status)
SELECT 'Seed User ' || n, 'seed-user-' || n || '@example.test', '+910000' || lpad(n::text, 6, '0'), 'VERIFIED'
FROM generate_series(1,1000) n ON CONFLICT (email) DO NOTHING;
INSERT INTO accounts(user_id,account_type,name)
SELECT u.id, 'MAIN_WALLET', 'Main wallet' FROM users u WHERE u.email LIKE 'seed-user-%@example.test'
ON CONFLICT DO NOTHING;
INSERT INTO accounts(user_id,account_type,name)
SELECT u.id, 'SAVINGS_WALLET', 'Savings wallet' FROM users u WHERE u.email LIKE 'seed-user-%@example.test'
ON CONFLICT DO NOTHING;
