SET NAMES utf8mb4;

CREATE TEMPORARY TABLE tmp_wallet_bonus_user_ids AS
SELECT u.`id` AS user_id
FROM `users` u
LEFT JOIN `wallet_transactions` wt
  ON wt.`user_id` = u.`id`
  AND wt.`type` = 'REGISTRATION_BONUS'
  AND wt.`amount` = 20000.00
  AND wt.`remark` = '现有用户补发注册赠送余额'
WHERE wt.`id` IS NULL;

INSERT IGNORE INTO `user_wallets`(`user_id`, `balance`)
SELECT user_id, 0.00
FROM tmp_wallet_bonus_user_ids;

UPDATE `user_wallets` uw
JOIN tmp_wallet_bonus_user_ids b ON b.user_id = uw.`user_id`
SET uw.`balance` = uw.`balance` + 20000.00,
    uw.`update_time` = NOW();

INSERT INTO `wallet_transactions`(`user_id`, `type`, `amount`, `balance_after`, `remark`)
SELECT uw.`user_id`, 'REGISTRATION_BONUS', 20000.00, uw.`balance`, '现有用户补发注册赠送余额'
FROM `user_wallets` uw
JOIN tmp_wallet_bonus_user_ids b ON b.user_id = uw.`user_id`;

DROP TEMPORARY TABLE tmp_wallet_bonus_user_ids;
