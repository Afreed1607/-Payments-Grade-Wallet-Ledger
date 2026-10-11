-- One named main and savings wallet per user; system accounts remain singleton by type.
CREATE UNIQUE INDEX accounts_user_wallet_type_unique
  ON accounts(user_id, account_type)
  WHERE account_type IN ('MAIN_WALLET', 'SAVINGS_WALLET');
CREATE UNIQUE INDEX accounts_system_type_unique
  ON accounts(account_type)
  WHERE user_id IS NULL;
