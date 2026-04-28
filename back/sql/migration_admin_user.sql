INSERT INTO `users`(`account`, `password`, `nickname`)
SELECT 'admin', 'e10adc3949ba59abbe56e057f20f883e', '后台管理员'
WHERE NOT EXISTS (
  SELECT 1 FROM `users` WHERE `account` = 'admin'
);
