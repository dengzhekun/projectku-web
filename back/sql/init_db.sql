-- --------------------------------------------------
-- ProjectKu 数据库完整初始化脚本（修复版）
-- 包含所有表结构、基础数据、商品、订单等
-- --------------------------------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 创建类目表（新增）
-- ----------------------------
CREATE TABLE `categories` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL COMMENT '类目名称',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '父类目ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品类目表';

-- ----------------------------
-- 2. 创建其他表结构
-- ----------------------------

-- 用户表
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account` varchar(64) NOT NULL COMMENT '账号(邮箱/手机号)',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户钱包表
CREATE TABLE `user_wallets` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `balance` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '可用余额',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_wallet` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户钱包表';

-- 钱包流水表
CREATE TABLE `wallet_transactions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `order_id` bigint(20) DEFAULT NULL COMMENT '关联订单ID',
  `trade_id` varchar(64) DEFAULT NULL COMMENT '支付流水号',
  `type` varchar(32) NOT NULL COMMENT '流水类型: INIT, PAYMENT, REFUND, ADJUST, REGISTRATION_BONUS',
  `amount` decimal(10,2) NOT NULL COMMENT '变动金额，收入为正，支出为负',
  `balance_after` decimal(10,2) NOT NULL COMMENT '变动后余额',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_wallet_user_time` (`user_id`, `create_time`),
  KEY `idx_wallet_trade_id` (`trade_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水表';

-- 商品表 (SPU)
CREATE TABLE `products` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category_id` bigint(20) DEFAULT NULL COMMENT '类目ID',
  `name` varchar(255) NOT NULL COMMENT '商品名称',
  `description` text COMMENT '商品描述',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签 (逗号分隔或JSON)',
  `rating` decimal(3,1) DEFAULT 4.5 COMMENT '评分',
  `sold` int(11) DEFAULT 0 COMMENT '已售数量',
  `activity_label` varchar(255) DEFAULT NULL COMMENT '活动标签',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `price` decimal(10,2) NOT NULL COMMENT '基础展示价格',
  `stock` int(11) NOT NULL DEFAULT '0' COMMENT '基础库存',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态: 0-下架, 1-上架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_create_time` (`status`, `create_time`),
  KEY `idx_category_status_create_time` (`category_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 商品媒体表 (轮播图)
CREATE TABLE `product_media` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL,
  `url` varchar(512) NOT NULL COMMENT '图片URL',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_product_sort_order` (`product_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品媒体表';

-- 商品 SKU 表 (规格)
CREATE TABLE `product_skus` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL,
  `attrs` varchar(512) DEFAULT '{}' COMMENT '规格属性 (JSON字符串)',
  `price` decimal(10,2) NOT NULL COMMENT '该规格价格',
  `stock` int(11) NOT NULL DEFAULT '0' COMMENT '该规格库存',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格表';

-- 用户收货地址表
CREATE TABLE `user_addresses` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `receiver` varchar(64) NOT NULL COMMENT '收件人',
  `phone` varchar(32) NOT NULL COMMENT '联系电话',
  `region` varchar(128) NOT NULL COMMENT '所在地区(省市区)',
  `detail` varchar(255) NOT NULL COMMENT '详细地址',
  `is_default` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否默认地址: 0-否, 1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_default_create_time` (`user_id`, `is_default`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

-- 订单表
CREATE TABLE `orders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `total_amount` decimal(10,2) NOT NULL COMMENT '总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态: 0-待支付, 1-已支付, 2-已发货, 3-已完成, 4-已取消',
  `address_id` bigint(20) DEFAULT NULL COMMENT '收货地址ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status_create_time` (`user_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单明细表
CREATE TABLE `order_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `sku_id` bigint(20) DEFAULT NULL COMMENT '规格ID',
  `product_name` varchar(255) NOT NULL COMMENT '商品名称',
  `product_image` varchar(512) DEFAULT NULL COMMENT '商品图片',
  `price` decimal(10,2) NOT NULL COMMENT '购买时价格',
  `quantity` int(11) NOT NULL COMMENT '购买数量',
  `total_amount` decimal(10,2) NOT NULL COMMENT '总金额',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 购物车明细表
CREATE TABLE `cart_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `sku_id` bigint(20) DEFAULT NULL COMMENT '规格ID',
  `quantity` int(11) NOT NULL DEFAULT '1' COMMENT '数量',
  `checked` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否选中: 0-否, 1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_create_time` (`user_id`, `create_time`),
  KEY `idx_user_product_sku` (`user_id`, `product_id`, `sku_id`),
  KEY `idx_user_checked` (`user_id`, `checked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车明细表';

-- 支付记录表
CREATE TABLE `payments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL COMMENT '关联的订单ID',
  `trade_id` varchar(64) NOT NULL COMMENT '支付流水号',
  `channel` varchar(32) NOT NULL COMMENT '支付渠道: alipay, wechat, unionpay, balance',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '支付状态: PENDING, SUCCESS, FAILED',
  `paid_at` datetime DEFAULT NULL COMMENT '实际支付完成时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_id` (`trade_id`),
  KEY `idx_order_create_time` (`order_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- 用户优惠券表
CREATE TABLE `coupons` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `code` varchar(64) NOT NULL COMMENT '优惠券码',
  `name` varchar(128) NOT NULL COMMENT '优惠券名称',
  `type` varchar(32) NOT NULL COMMENT '类型: full_reduction(满减), discount(折扣)',
  `min_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '最低消费金额',
  `discount_amount` decimal(10,2) NOT NULL COMMENT '优惠金额',
  `status` varchar(32) NOT NULL DEFAULT 'VALID' COMMENT '状态: VALID, USED, EXPIRED',
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_user_status_time` (`user_id`, `status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- 消息通知表
CREATE TABLE `notifications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `type` varchar(64) NOT NULL COMMENT '消息类型',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `related_id` varchar(128) DEFAULT NULL COMMENT '关联业务ID',
  `is_read` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否已读: 0-否, 1-是',
  `read_time` datetime DEFAULT NULL COMMENT '读取时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_create_time` (`user_id`, `create_time`),
  KEY `idx_user_is_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- 售后申请表
CREATE TABLE `aftersales` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `order_item_id` varchar(128) DEFAULT NULL COMMENT '订单项ID',
  `qty` int(11) DEFAULT '1' COMMENT '申请数量',
  `evidence` text COMMENT '凭证图片 (JSON数组)',
  `type` varchar(32) NOT NULL COMMENT '售后类型',
  `reason` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_create_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后申请表';

-- 商品评价表
CREATE TABLE `reviews` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `order_id` bigint(20) DEFAULT NULL COMMENT '关联订单ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `rating` int(11) NOT NULL DEFAULT '5' COMMENT '评分 (1-5)',
  `content` text COMMENT '评价内容',
  `images` varchar(1024) DEFAULT NULL COMMENT '图片列表 (JSON数组字符串)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_create_time` (`product_id`, `create_time`),
  KEY `idx_user_create_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

-- 商品收藏表
CREATE TABLE `favorites` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user_create_time` (`user_id`, `create_time`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏表';

-- ----------------------------
-- 3. 初始数据填充
-- ----------------------------

-- 3.1 类目数据
INSERT INTO `categories` (`id`, `name`) VALUES
(1, '手机'),
(2, '电脑/办公'),
(3, '家电'),
(4, '数码配件'),
(5, '家居收纳'),
(6, '美妆个护'),
(7, '食品生鲜'),
(8, '运动户外');

-- 3.2 用户数据
INSERT INTO `users`(`account`, `password`, `nickname`) VALUES 
('user@example.com','e10adc3949ba59abbe56e057f20f883e','测试用户'),
('alice@example.com','e10adc3949ba59abbe56e057f20f883e','Alice'),
('admin','e10adc3949ba59abbe56e057f20f883e','后台管理员');

INSERT INTO `user_wallets`(`user_id`, `balance`) VALUES
(1, 20000.00),
(2, 20000.00);

INSERT INTO `wallet_transactions`(`user_id`, `type`, `amount`, `balance_after`, `remark`) VALUES
(1, 'INIT', 20000.00, 20000.00, '注册赠送余额'),
(2, 'INIT', 20000.00, 20000.00, '注册赠送余额');

-- 3.3 商品数据（手机类，1-20）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(1,'iPhone 15 Pro 128G','A17 Pro 芯片，旗舰性能','["旗舰"]',7999.00,100,1),
(1,'iPhone 15 128G','A16 芯片，性能均衡','["性价比"]',5999.00,120,1),
(1,'Xiaomi 14 Pro 12+256G','徕卡光学，旗舰影像','["旗舰"]',4999.00,200,1),
(1,'Redmi K70 12+256G','性能强悍，价格亲民','["性价比"]',2299.00,300,1),
(1,'HUAWEI Mate X5','折叠旗舰，大屏体验','["折叠屏"]',12999.00,50,1),
(1,'OPPO Find N3','折叠轻薄，旗舰体验','["折叠屏"]',9999.00,60,1),
(1,'MagSafe 原装充电器','配件','["配件"]',329.00,500,1),
(1,'Type-C 充电线 1m','配件','["配件"]',39.00,1000,1),
(1,'荣耀 Magic6 Pro','骁龙8 Gen3 旗舰','["旗舰"]',6499.00,120,1),
(1,'realme GT Neo','高性价比游戏手机','["性价比"]',1999.00,220,1),
(1,'vivo X100','自研影像旗舰','["旗舰"]',5299.00,140,1),
(1,'一加 Ace','高性能高性价比','["性价比"]',2399.00,240,1),
(1,'小米 折叠屏 Mix Fold','折叠大屏','["折叠屏"]',7999.00,70,1),
(1,'三星 Galaxy Z Fold','折叠标杆','["折叠屏"]',12999.00,40,1),
(1,'苹果 原装耳机 Type-C','配件','["配件"]',149.00,600,1),
(1,'Anker 65W 氮化镓充电器','配件','["配件"]',199.00,300,1),
(1,'iQOO 12','电竞旗舰性能','["旗舰"]',4199.00,180,1),
(1,'Redmi Note 13','超高性价比','["性价比"]',1299.00,800,1),
(1,'荣耀 X50','均衡之选','["性价比"]',1599.00,500,1),
(1,'OPPO Reno 旗舰版','影像旗舰','["旗舰"]',4599.00,160,1);

-- 商品数据（电脑/外设类，21-40）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(2,'MacBook Air 13 M3 16G','轻薄便携，续航出众','["轻薄本"]',9999.00,50,1),
(2,'ThinkPad X1 Carbon','高可靠商务轻薄','["轻薄本"]',12999.00,30,1),
(2,'ROG 枪神 笔记本','高刷电竞游戏本','["游戏本"]',14999.00,20,1),
(2,'联想 拯救者 Y7000','高性价比游戏本','["游戏本"]',6999.00,80,1),
(2,'Dell U2723QE 27\" 4K','Type-C 一线通','["显示器"]',3999.00,25,1),
(2,'LG 34WP65C 34\" 曲面','沉浸式超宽','["显示器"]',3299.00,20,1),
(2,'罗技 MX Master 3s','人体工学鼠标','["外设"]',599.00,200,1),
(2,'Keychron K2 键盘','机械键盘','["外设"]',499.00,150,1),
(2,'华为 MateBook X','金属轻薄','["轻薄本"]',7999.00,40,1),
(2,'小米 Pro 16','性能轻薄','["轻薄本"]',6499.00,60,1),
(2,'雷神 911','实惠游戏本','["游戏本"]',5699.00,90,1),
(2,'外星人 m15','旗舰电竞','["游戏本"]',17999.00,15,1),
(2,'明基 PD2705U 4K','设计师显示器','["显示器"]',2999.00,25,1),
(2,'群晖 NAS DS220+','数据存储','["外设"]',2999.00,35,1),
(2,'海盗船 K70 键盘','外设','["外设"]',899.00,60,1),
(2,'罗技 G Pro X Superlight','电竞鼠标','["外设"]',899.00,70,1),
(2,'惠普 星14','轻薄本','["轻薄本"]',4799.00,120,1),
(2,'微星 GF66','高性价比游戏本','["游戏本"]',6299.00,70,1),
(2,'AOC 27G2 144Hz','电竞显示器','["显示器"]',1299.00,100,1),
(2,'雷蛇 黑寡妇','外设','["外设"]',799.00,60,1);

-- 商品数据（家电类，41-60）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(3,'海尔 纤诺 洗衣机','大容量变频','["冰洗"]',3299.00,40,1),
(3,'美的 风冷 冰箱','多门风冷','["冰洗"]',4599.00,35,1),
(3,'戴森 V12 吸尘器','强劲吸力','["清洁"]',3999.00,30,1),
(3,'石头 扫拖机器人','激光导航','["清洁"]',2599.00,50,1),
(3,'苏泊尔 炒锅','不粘锅具','["厨房"]',399.00,200,1),
(3,'九阳 破壁机','多功能','["厨房"]',899.00,120,1),
(3,'飞科 电动牙刷','清洁口腔','["个护"]',199.00,300,1),
(3,'飞利浦 电吹风','恒温护发','["个护"]',299.00,220,1),
(3,'格力 空调 1.5P','一级能效','["冰洗"]',3899.00,25,1),
(3,'小米 空气净化器','滤除PM2.5','["清洁"]',799.00,110,1),
(3,'美的 洗碗机','嵌入式','["厨房"]',3699.00,28,1),
(3,'松下 电动剃须刀','三刀头','["个护"]',599.00,180,1),
(3,'添可 吸拖一体机','高效清洁','["清洁"]',2499.00,40,1),
(3,'苏泊尔 电饭煲','智能预约','["厨房"]',499.00,140,1),
(3,'美的 干衣机','快速烘干','["冰洗"]',2999.00,22,1),
(3,'莱克 吸尘器','无线便携','["清洁"]',1299.00,60,1),
(3,'小熊 榨汁机','厨房小电','["厨房"]',199.00,250,1),
(3,'飞科 理发器','个护','["个护"]',129.00,260,1),
(3,'博朗 牙刷','个护','["个护"]',399.00,90,1),
(3,'云鲸 扫拖机器人','清洁','["清洁"]',4599.00,18,1);

-- 商品数据（数码配件类，61-80）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(4,'索尼 WH-1000XM5','旗舰降噪耳机','["耳机"]',2599.00,60,1),
(4,'AirPods Pro 2','主动降噪','["耳机"]',1999.00,90,1),
(4,'JBL Live Pro','真无线耳机','["耳机"]',799.00,120,1),
(4,'Anker 65W 充电器','氮化镓快充','["充电"]',199.00,300,1),
(4,'倍思 充电宝 20000mAh','大容量','["充电"]',169.00,400,1),
(4,'闪迪 至尊高速 128G','U3 存储卡','["存储"]',129.00,500,1),
(4,'三星 EVO 256G','高速存储卡','["存储"]',299.00,260,1),
(4,'Apple Watch SE','智能手表','["智能穿戴"]',2199.00,80,1),
(4,'华为 Watch 4','长续航手表','["智能穿戴"]',2499.00,70,1),
(4,'小米 手环 8 Pro','智能手环','["智能穿戴"]',399.00,600,1),
(4,'索尼 LinkBuds','开放式耳机','["耳机"]',1199.00,90,1),
(4,'绿联 氮化镓 100W','快充','["充电"]',299.00,150,1),
(4,'西数 SSD 1TB','高速固态','["存储"]',599.00,80,1),
(4,'东芝 U盘 128G','存储','["存储"]',79.00,700,1),
(4,'佳明 Forerunner','运动手表','["智能穿戴"]',2599.00,50,1),
(4,'Beats Studio Buds','无线耳机','["耳机"]',1099.00,100,1),
(4,'南孚 充电套装','充电','["充电"]',129.00,300,1),
(4,'海康 TF 256G','存储卡','["存储"]',169.00,400,1),
(4,'OPPO Watch','智能穿戴','["智能穿戴"]',1299.00,80,1),
(4,'索尼 WF-1000XM5','旗舰真无线','["耳机"]',1699.00,70,1);

-- 商品数据（家居收纳类，81-100）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(5,'抽屉式收纳盒','桌面收纳','["收纳"]',69.00,500,1),
(5,'衣物整理箱','大容量','["收纳"]',89.00,400,1),
(5,'北欧风抱枕','舒适家居','["家居"]',49.00,600,1),
(5,'落地台灯','氛围灯','["家居"]',199.00,200,1),
(5,'晨光 中性笔 12支','顺滑书写','["文具"]',19.90,1000,1),
(5,'得力 活页本','学习办公','["文具"]',29.90,800,1),
(5,'厨房纸巾 6卷','强吸水','["清洁耗材"]',19.90,900,1),
(5,'一次性手套 100只','清洁防护','["清洁耗材"]',14.90,1200,1),
(5,'多功能置物架','家居','["家居"]',129.00,300,1),
(5,'密封保鲜盒','厨房收纳','["收纳"]',39.90,700,1),
(5,'梳齿书签套装','文具','["文具"]',9.90,900,1),
(5,'抽绳垃圾袋 3卷','清洁耗材','["清洁耗材"]',12.90,1500,1),
(5,'懒人抹布','清洁耗材','["清洁耗材"]',9.90,1600,1),
(5,'墙面置物袋','收纳','["收纳"]',29.90,600,1),
(5,'防滑衣架 20只','家居','["家居"]',19.90,1200,1),
(5,'极简闹钟','家居','["家居"]',49.90,500,1),
(5,'高光修正带','文具','["文具"]',6.90,1000,1),
(5,'擦窗器','清洁耗材','["清洁耗材"]',39.90,400,1),
(5,'桌面理线器','收纳','["收纳"]',9.90,900,1),
(5,'便签便利贴','文具','["文具"]',4.90,1000,1);

-- 商品数据（美妆个护类，101-120）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(6,'温和保湿洁面乳','氨基酸配方，清洁同时不紧绷','["护肤"]',79.00,300,1),
(6,'深层补水爽肤水','二次清洁，舒缓干燥','["护肤"]',99.00,260,1),
(6,'修护精华液','添加神经酰胺，修护屏障','["护肤"]',219.00,180,1),
(6,'水润保湿面霜','长效锁水，适合秋冬使用','["护肤"]',169.00,200,1),
(6,'清爽防晒乳 SPF50+','日常通勤防晒','["护肤"]',129.00,220,1),
(6,'丝绒雾面口红','显白不拔干，多色可选','["彩妆"]',139.00,400,1),
(6,'水润气垫粉底','轻薄服帖，自然遮瑕','["彩妆"]',189.00,250,1),
(6,'防水眉笔','细芯顺滑，持久不晕染','["彩妆"]',59.00,500,1),
(6,'纤长睫毛膏','根根分明，不易结块','["彩妆"]',99.00,320,1),
(6,'高光修容盘','修饰轮廓，提亮气色','["彩妆"]',159.00,180,1),
(6,'柔顺修护洗发水','针对干枯毛躁发质','["洗护"]',69.00,420,1),
(6,'顺滑护发素','减少打结，提升光泽','["洗护"]',59.00,380,1),
(6,'氨基酸沐浴露','温和清洁，全肤质适用','["洗护"]',49.00,450,1),
(6,'深度滋养发膜','一周一次集中护理','["洗护"]',89.00,260,1),
(6,'旅行装洗护套装','适合短途出行携带','["洗护"]',39.00,600,1),
(6,'花果香淡香水','清新甜美日常香','["香氛"]',269.00,160,1),
(6,'木质麝香香水','沉稳木质调，适合通勤','["香氛"]',329.00,140,1),
(6,'室内藤条香薰','净化异味，营造氛围','["香氛"]',129.00,220,1),
(6,'车载夹式香氛','长效留香，驾驶更愉悦','["香氛"]',79.00,300,1),
(6,'香氛蜡烛礼盒','多种香型可选','["香氛"]',199.00,180,1);

-- 商品数据（食品生鲜类，121-140）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(7,'每日坚果 750g','混合坚果，独立小包装','["零食"]',89.00,400,1),
(7,'海盐薯片 8连包','轻薄脆爽，追剧必备','["零食"]',29.90,800,1),
(7,'和风鱿鱼丝','高蛋白小零食','["零食"]',19.90,600,1),
(7,'冻干草莓脆','水果冻干，不添加色素','["零食"]',35.90,300,1),
(7,'黄油曲奇礼盒','下午茶点心','["零食"]',49.90,260,1),
(7,'挂耳咖啡 20袋','阿拉比卡咖啡豆，中度烘焙','["咖啡茶饮"]',69.00,300,1),
(7,'精品咖啡豆 500g','浅烘焙，适合手冲','["咖啡茶饮"]',89.00,220,1),
(7,'锡兰红茶 礼盒装','产地直采，口感清爽','["咖啡茶饮"]',59.00,260,1),
(7,'茉莉花茶 250g','茉莉花窨制绿茶','["咖啡茶饮"]',49.00,280,1),
(7,'浓缩奶茶液 6瓶装','冷水冲泡即可饮用','["咖啡茶饮"]',39.90,320,1),
(7,'东北大米 10kg','颗粒饱满，新米香甜','["粮油"]',89.00,500,1),
(7,'五常稻花香 5kg','认证产区大米','["粮油"]',79.00,380,1),
(7,'非转菜籽油 5L','冷榨工艺，少油烟','["粮油"]',69.90,260,1),
(7,'橄榄调和油 2L','适合凉拌与煎炒','["粮油"]',59.90,280,1),
(7,'高筋小麦粉 5kg','适合烘焙与面食','["粮油"]',49.90,320,1),
(7,'速冻手工水饺 1.5kg','猪肉白菜口味','["生鲜冷冻"]',49.90,260,1),
(7,'冷冻鸡翅中 1kg','气调保鲜，家庭囤货','["生鲜冷冻"]',39.90,280,1),
(7,'雪花肥牛片 500g','适合火锅与炒菜','["生鲜冷冻"]',59.90,220,1),
(7,'去壳虾仁 400g','速冻锁鲜，简单烹饪','["生鲜冷冻"]',49.90,240,1),
(7,'冷冻披萨 半成品','烤箱加热即可食用','["生鲜冷冻"]',29.90,260,1);

-- 商品数据（运动户外类，141-160）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(8,'减震跑步鞋 男款','适合日常慢跑训练','["跑步"]',399.00,260,1),
(8,'轻量跑步鞋 女款','透气网面，舒适脚感','["跑步"]',369.00,240,1),
(8,'专业跑步短袖','速干面料，排汗透气','["跑步"]',129.00,320,1),
(8,'运动压缩裤','支撑肌群，减少疲劳','["跑步"]',199.00,200,1),
(8,'夜跑反光臂包','可放手机与钥匙','["跑步"]',59.00,360,1),
(8,'双人帐篷 防雨款','三季适用，搭建方便','["露营"]',599.00,180,1),
(8,'便携折叠椅','户外露营休闲必备','["露营"]',129.00,260,1),
(8,'户外野营灯','USB 充电，多档亮度','["露营"]',99.00,280,1),
(8,'保温野餐壶 1.5L','长效保温保冷','["露营"]',89.00,220,1),
(8,'钛合金野餐餐具套装','轻便耐用','["露营"]',149.00,200,1),
(8,'家用哑铃套装 20kg','可调节重量','["健身"]',299.00,220,1),
(8,'瑜伽垫 加厚款','高回弹材质，防滑耐磨','["健身"]',129.00,320,1),
(8,'跳绳 轴承款','适合燃脂训练','["健身"]',59.00,360,1),
(8,'阻力带 5件套','力量训练辅助','["健身"]',79.00,280,1),
(8,'家用引体向上器','免打孔安装','["健身"]',199.00,180,1),
(8,'公路自行车 入门款','铝合金车架，适合通勤','["骑行"]',1999.00,80,1),
(8,'山地车 21速','适合户外越野','["骑行"]',1699.00,90,1),
(8,'骑行头盔 一体成型','轻量防护','["骑行"]',199.00,260,1),
(8,'骑行手套 透气款','掌心减震垫','["骑行"]',79.00,300,1),
(8,'自行车码表','记录速度与里程','["骑行"]',129.00,220,1);

-- 补充商品（避免与前面重复，修改名称，161-170）
INSERT INTO `products`(`category_id`, `name`, `description`, `tags`, `price`, `stock`, `status`) VALUES
(1,'iPhone 14 128G','苹果智能手机，A15 仿生，支持 5G','["手机"]',5999.00,50,1),
(1,'Xiaomi 14 256G','小米旗舰手机，徕卡影像','["手机"]',4299.00,120,1),
(2,'MacBook Air 13 M3 16G (新款)','轻薄本，日常学习办公优选','["轻薄本"]',9999.00,30,1),
(2,'ThinkPad X1 Carbon (Gen 11)','专业商务本，高可靠性','["轻薄本"]',12999.00,18,1),
(3,'戴森 V12 吸尘器 (Absolute)','强劲吸力，全屋清洁','["清洁"]',3999.00,40,1),
(3,'米家扫拖机器人','激光导航，自动回充','["清洁"]',1799.00,80,1),
(4,'索尼 WH-1000XM5 (银)','旗舰降噪耳机，舒适佩戴','["耳机"]',2599.00,60,1),
(4,'AirPods Pro 2 (USB-C)','主动降噪，通透模式','["耳机"]',1999.00,90,1),
(5,'Dell U2723QE 27" 4K (升级版)','Type-C 一线通，IPS 面板','["显示器"]',3999.00,25,1),
(5,'LG 34WP65C 34" 曲面 (新款)','超宽曲面，沉浸体验','["显示器"]',3299.00,20,1);

-- 3.4 地址数据
INSERT INTO `user_addresses`(`user_id`, `receiver`, `phone`, `region`, `detail`, `is_default`)
VALUES (1,'张三','13800000000','北京','朝阳区建国路 88 号',1);

-- 3.5 优惠券数据
INSERT INTO `coupons`(`user_id`, `code`, `name`, `type`, `min_amount`, `discount_amount`, `status`, `start_time`, `end_time`)
VALUES (1,'NEW300','新客满5000-300','full_reduction',5000.00,300.00,'VALID', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY);

-- 3.6 模拟订单（使用唯一商品 iPhone 15 Pro，避免名称重复）
-- 先获取地址ID（用户1的默认地址）
SET @addr_id = (SELECT id FROM user_addresses WHERE user_id=1 LIMIT 1);
-- 插入订单
INSERT INTO `orders`(`user_id`, `order_no`, `total_amount`, `pay_amount`, `status`, `address_id`)
VALUES (1,'A20260405001',7999.00,7999.00,1, @addr_id);
-- 获取刚插入的订单ID
SET @order_id = LAST_INSERT_ID();
-- 获取商品ID（iPhone 15 Pro）
SET @product_id = (SELECT id FROM products WHERE name='iPhone 15 Pro 128G' LIMIT 1);
-- 插入订单明细
INSERT INTO `order_items`(`order_id`, `product_id`, `product_name`, `product_image`, `price`, `quantity`, `total_amount`)
VALUES (@order_id, @product_id, 'iPhone 15 Pro 128G', '/product_1.jpg', 7999.00, 1, 7999.00);
-- 插入支付记录
INSERT INTO `payments`(`order_id`, `trade_id`, `channel`, `amount`, `status`, `paid_at`)
VALUES (@order_id, 'T20260405001', 'alipay', 7999.00, 'SUCCESS', NOW());

-- 为所有初始化商品补齐本地商品主图，避免商品详情页显示缺图或实物不符
INSERT INTO `product_media`(`product_id`, `url`, `sort_order`) VALUES
(1, '/product_1.jpg', 1),
(2, '/product_2.jpg', 1),
(3, '/product_3.jpg', 1),
(4, '/product_4.jpg', 1),
(5, '/product_5.jpg', 1),
(6, '/product_6.jpg', 1),
(7, '/product_7.jpg', 1),
(8, '/product_8.jpg', 1),
(9, '/product_9.jpg', 1),
(10, '/product_10.jpg', 1),
(11, '/product_11.jpg', 1),
(12, '/product_12.jpg', 1),
(13, '/product_13.jpg', 1),
(14, '/product_14.jpg', 1),
(15, '/product_15.jpg', 1),
(16, '/product_16.jpg', 1),
(17, '/product_17.jpg', 1),
(18, '/product_18.jpg', 1),
(19, '/product_19.jpg', 1),
(20, '/product_20.jpg', 1),
(21, '/product_21.jpg', 1),
(22, '/product_22.jpg', 1),
(23, '/product_23.jpg', 1),
(24, '/product_24.jpg', 1),
(25, '/product_25.jpg', 1),
(26, '/product_26.jpg', 1),
(27, '/product_27.jpg', 1),
(28, '/product_28.jpg', 1),
(29, '/product_29.jpg', 1),
(30, '/product_30.jpg', 1),
(31, '/product_31.jpg', 1),
(32, '/product_32.jpg', 1),
(33, '/product_33.jpg', 1),
(34, '/product_34.jpg', 1),
(35, '/product_35.jpg', 1),
(36, '/product_36.jpg', 1),
(37, '/product_37.jpg', 1),
(38, '/product_38.jpg', 1),
(39, '/product_39.jpg', 1),
(40, '/product_40.jpg', 1),
(41, '/product_41.jpg', 1),
(42, '/product_42.jpg', 1),
(43, '/product_43.jpg', 1),
(44, '/product_44.jpg', 1),
(45, '/product_45.jpg', 1),
(46, '/product_46.jpg', 1),
(47, '/product_47.jpg', 1),
(48, '/product_48.jpg', 1),
(49, '/product_49.jpg', 1),
(50, '/product_50.jpg', 1),
(51, '/product_51.jpg', 1),
(52, '/product_52.jpg', 1),
(53, '/product_53.jpg', 1),
(54, '/product_54.jpg', 1),
(55, '/product_55.jpg', 1),
(56, '/product_56.jpg', 1),
(57, '/product_57.jpg', 1),
(58, '/product_58.jpg', 1),
(59, '/product_59.jpg', 1),
(60, '/product_60.jpg', 1),
(61, '/product_61.jpg', 1),
(62, '/product_62.jpg', 1),
(63, '/product_63.jpg', 1),
(64, '/product_64.jpg', 1),
(65, '/product_65.jpg', 1),
(66, '/product_66.jpg', 1),
(67, '/product_67.jpg', 1),
(68, '/product_68.jpg', 1),
(69, '/product_69.jpg', 1),
(70, '/product_70.jpg', 1),
(71, '/product_71.jpg', 1),
(72, '/product_72.jpg', 1),
(73, '/product_73.jpg', 1),
(74, '/product_74.jpg', 1),
(75, '/product_75.jpg', 1),
(76, '/product_76.jpg', 1),
(77, '/product_77.jpg', 1),
(78, '/product_78.jpg', 1),
(79, '/product_79.jpg', 1),
(80, '/product_80.jpg', 1),
(81, '/product_81.jpg', 1),
(82, '/product_82.jpg', 1),
(83, '/product_83.jpg', 1),
(84, '/product_84.jpg', 1),
(85, '/product_85.jpg', 1),
(86, '/product_86.jpg', 1),
(87, '/product_87.jpg', 1),
(88, '/product_88.jpg', 1),
(89, '/product_89.jpg', 1),
(90, '/product_90.jpg', 1),
(91, '/product_91.jpg', 1),
(92, '/product_92.jpg', 1),
(93, '/product_93.jpg', 1),
(94, '/product_94.jpg', 1),
(95, '/product_95.jpg', 1),
(96, '/product_96.jpg', 1),
(97, '/product_97.jpg', 1),
(98, '/product_98.jpg', 1),
(99, '/product_99.jpg', 1),
(100, '/product_100.jpg', 1),
(101, '/product_101.jpg', 1),
(102, '/product_102.jpg', 1),
(103, '/product_103.jpg', 1),
(104, '/product_104.jpg', 1),
(105, '/product_105.jpg', 1),
(106, '/product_106.jpg', 1),
(107, '/product_107.jpg', 1),
(108, '/product_108.jpg', 1),
(109, '/product_109.jpg', 1),
(110, '/product_110.jpg', 1),
(111, '/product_111.jpg', 1),
(112, '/product_112.jpg', 1),
(113, '/product_113.jpg', 1),
(114, '/product_114.jpg', 1),
(115, '/product_115.jpg', 1),
(116, '/product_116.jpg', 1),
(117, '/product_117.jpg', 1),
(118, '/product_118.jpg', 1),
(119, '/product_119.jpg', 1),
(120, '/product_120.jpg', 1),
(121, '/product_121.jpg', 1),
(122, '/product_122.jpg', 1),
(123, '/product_123.jpg', 1),
(124, '/product_124.jpg', 1),
(125, '/product_125.jpg', 1),
(126, '/product_126.jpg', 1),
(127, '/product_127.jpg', 1),
(128, '/product_128.jpg', 1),
(129, '/product_129.jpg', 1),
(130, '/product_130.jpg', 1),
(131, '/product_131.jpg', 1),
(132, '/product_132.jpg', 1),
(133, '/product_133.jpg', 1),
(134, '/product_134.jpg', 1),
(135, '/product_135.jpg', 1),
(136, '/product_136.jpg', 1),
(137, '/product_137.jpg', 1),
(138, '/product_138.jpg', 1),
(139, '/product_139.jpg', 1),
(140, '/product_140.jpg', 1),
(141, '/product_141.jpg', 1),
(142, '/product_142.jpg', 1),
(143, '/product_143.jpg', 1),
(144, '/product_144.jpg', 1),
(145, '/product_145.jpg', 1),
(146, '/product_146.jpg', 1),
(147, '/product_147.jpg', 1),
(148, '/product_148.jpg', 1),
(149, '/product_149.jpg', 1),
(150, '/product_150.jpg', 1),
(151, '/product_151.jpg', 1),
(152, '/product_152.jpg', 1),
(153, '/product_153.jpg', 1),
(154, '/product_154.jpg', 1),
(155, '/product_155.jpg', 1),
(156, '/product_156.jpg', 1),
(157, '/product_157.jpg', 1),
(158, '/product_158.jpg', 1),
(159, '/product_159.jpg', 1),
(160, '/product_160.jpg', 1),
(161, '/product_161.jpg', 1),
(162, '/product_162.jpg', 1),
(163, '/product_163.jpg', 1),
(164, '/product_164.jpg', 1),
(165, '/product_165.jpg', 1),
(166, '/product_166.jpg', 1),
(167, '/product_167.jpg', 1),
(168, '/product_168.jpg', 1),
(169, '/product_169.jpg', 1),
(170, '/product_170.jpg', 1);
INSERT INTO `product_skus`(`product_id`, `attrs`, `price`, `stock`) VALUES
(1, '{"颜色":"黑色","容量":"128G"}', 7999.00, 50),
(1, '{"颜色":"白色","容量":"128G"}', 7999.00, 50);

-- 3.7 评价数据

INSERT INTO `reviews` (`user_id`, `order_id`, `product_id`, `rating`, `content`, `images`, `create_time`, `update_time`) VALUES
-- product_id = 1 的两条评价
(1001, 20241001, 1, 5, '质量很棒，细节处理到位，物流也很快。', NULL, NOW(), NOW()),
(1002, 20241002, 1, 4, '整体不错，就是包装稍微有点挤压。', NULL, NOW(), NOW()),
-- product_id = 2 的两条评价
(1003, 20241003, 2, 5, '颜色很正，大小合适，非常满意！', NULL, NOW(), NOW()),
(1004, 20241004, 2, 3, '中规中矩吧，没有宣传图那么惊艳。', NULL, NOW(), NOW()),
-- product_id = 3 的两条评价
(1005, 20241005, 3, 4, '性价比高，这个价位能买到这样的商品很值。', NULL, NOW(), NOW()),
(1006, 20241006, 3, 2, '收到时外包装破了，里面有些划痕。', NULL, NOW(), NOW()),
-- product_id = 4 的两条评价
(1007, 20241007, 4, 5, '买给家人的，他们很喜欢，做工精细。', NULL, NOW(), NOW()),
(1008, 20241008, 4, 5, '第二次回购了，品质一直稳定。', NULL, NOW(), NOW()),
-- product_id = 5 的两条评价
(1009, 20241009, 5, 3, '还行，快递有点慢，其他都还好。', NULL, NOW(), NOW()),
(1010, 20241010, 5, 4, '商品没问题，客服态度也不错。', NULL, NOW(), NOW()),
-- product_id = 6 的两条评价
(1011, 20241011, 6, 5, '超出预期，设计很人性化。', NULL, NOW(), NOW()),
(1012, 20241012, 6, 1, '用了两天就坏了，质量太差了！', NULL, NOW(), NOW()),
-- product_id = 7 的两条评价
(1013, 20241013, 7, 4, '外观好看，功能实用。', NULL, NOW(), NOW()),
(1014, 20241014, 7, 5, '朋友推荐买的，确实不错。', NULL, NOW(), NOW()),
-- product_id = 8 的两条评价
(1015, 20241015, 8, 3, '一分钱一分货，能接受。', NULL, NOW(), NOW()),
(1016, 20241016, 8, 5, '非常棒，下次还会来买。', NULL, NOW(), NOW()),
-- product_id = 9 的两条评价
(1017, 20241017, 9, 4, '物流很快，包装严实。', NULL, NOW(), NOW()),
(1018, 20241018, 9, 5, '实物比图片好看，手感很好。', NULL, NOW(), NOW()),
-- product_id = 10 的两条评价
(1019, 20241019, 10, 2, '有点失望，功能没有描述的全。', NULL, NOW(), NOW()),
(1020, 20241020, 10, 3, '一般般，对得起这个价格。', NULL, NOW(), NOW()),
-- product_id = 11 的两条评价
(1021, 20241021, 11, 5, '完美，挑不出毛病。', NULL, NOW(), NOW()),
(1022, 20241022, 11, 4, '还不错，如果能再优惠点就更好了。', NULL, NOW(), NOW()),
-- product_id = 12 的两条评价
(1023, 20241023, 12, 5, '发货速度很快，东西也好。', NULL, NOW(), NOW()),
(1024, 20241024, 12, 3, '还行吧，能用。', NULL, NOW(), NOW()),
-- product_id = 13 的两条评价
(1025, 20241025, 13, 4, '款式新颖，喜欢。', NULL, NOW(), NOW()),
(1026, 20241026, 13, 5, '和描述一致，满意。', NULL, NOW(), NOW()),
-- product_id = 14 的两条评价
(1027, 20241027, 14, 2, '有异味，放了好几天才散掉。', NULL, NOW(), NOW()),
(1028, 20241028, 14, 3, '外观可以，细节有待提升。', NULL, NOW(), NOW()),
-- product_id = 15 的两条评价
(1029, 20241029, 15, 5, '已经用了几天，性能稳定。', NULL, NOW(), NOW()),
(1030, 20241030, 15, 4, '挺好的，给个好评。', NULL, NOW(), NOW()),
-- product_id = 16 的两条评价
(1031, 20241031, 16, 3, '包装太简陋了，还好东西没坏。', NULL, NOW(), NOW()),
(1032, 20241032, 16, 5, '东西不错，物流给力。', NULL, NOW(), NOW()),
-- product_id = 17 的两条评价
(1033, 20241033, 17, 4, '大小正好，材质也不错。', NULL, NOW(), NOW()),
(1034, 20241034, 17, 5, '非常喜欢，会回购。', NULL, NOW(), NOW()),
-- product_id = 18 的两条评价
(1035, 20241035, 18, 2, '不推荐，感觉不值这个钱。', NULL, NOW(), NOW()),
(1036, 20241036, 18, 4, '还行，凑合用。', NULL, NOW(), NOW()),
-- product_id = 19 的两条评价
(1037, 20241037, 19, 5, '质量过硬，值得信赖。', NULL, NOW(), NOW()),
(1038, 20241038, 19, 5, '很好，已经是老顾客了。', NULL, NOW(), NOW()),
-- product_id = 20 的两条评价
(1039, 20241039, 20, 3, '颜色有点色差，其他还好。', NULL, NOW(), NOW()),
(1040, 20241040, 20, 4, '性价比可以，日常使用足够。', NULL, NOW(), NOW()),
-- product_id = 21 的两条评价
(1041, 20241041, 21, 5, '精致小巧，携带方便。', NULL, NOW(), NOW()),
(1042, 20241042, 21, 4, '功能齐全，操作简单。', NULL, NOW(), NOW()),
-- product_id = 22 的两条评价
(1043, 20241043, 22, 2, '开箱就发现少了配件，差评。', NULL, NOW(), NOW()),
(1044, 20241044, 22, 3, '东西没问题，但客服响应太慢。', NULL, NOW(), NOW()),
-- product_id = 23 的两条评价
(1045, 20241045, 23, 5, '包装精美，送人很合适。', NULL, NOW(), NOW()),
(1046, 20241046, 23, 5, '物流超快，隔天就到了。', NULL, NOW(), NOW()),
-- product_id = 24 的两条评价
(1047, 20241047, 24, 4, '还不错，就是有点小贵。', NULL, NOW(), NOW()),
(1048, 20241048, 24, 5, '一分钱一分货，质量确实好。', NULL, NOW(), NOW()),
-- product_id = 25 的两条评价
(1049, 20241049, 25, 3, '普普通通，没什么亮点。', NULL, NOW(), NOW()),
(1050, 20241050, 25, 4, '日常用用还行。', NULL, NOW(), NOW()),
-- product_id = 26 的两条评价
(1051, 20241051, 26, 5, '设计感强，很喜欢。', NULL, NOW(), NOW()),
(1052, 20241052, 26, 4, '不错，符合预期。', NULL, NOW(), NOW()),
-- product_id = 27 的两条评价
(1053, 20241053, 27, 2, '质量一般，用了没多久就出问题。', NULL, NOW(), NOW()),
(1054, 20241054, 27, 3, '一般般吧，不推荐。', NULL, NOW(), NOW()),
-- product_id = 28 的两条评价
(1055, 20241055, 28, 5, '做工精细，手感好。', NULL, NOW(), NOW()),
(1056, 20241056, 28, 5, '完美，挑不出缺点。', NULL, NOW(), NOW()),
-- product_id = 29 的两条评价
(1057, 20241057, 29, 4, '大小合适，颜色也正。', NULL, NOW(), NOW()),
(1058, 20241058, 29, 3, '还行，就是发货慢了点。', NULL, NOW(), NOW()),
-- product_id = 30 的两条评价
(1059, 20241059, 30, 5, '非常满意，下次还来。', NULL, NOW(), NOW()),
(1060, 20241060, 30, 4, '不错，给个五星。', NULL, NOW(), NOW()),
-- product_id = 31 的两条评价
(1061, 20241061, 31, 3, '能用，但细节处理不到位。', NULL, NOW(), NOW()),
(1062, 20241062, 31, 4, '整体还可以，价格再低点更好。', NULL, NOW(), NOW()),
-- product_id = 32 的两条评价
(1063, 20241063, 32, 5, '质量没得说，会回购。', NULL, NOW(), NOW()),
(1064, 20241064, 32, 2, '物流太差了，包装都破了。', NULL, NOW(), NOW()),
-- product_id = 33 的两条评价
(1065, 20241065, 33, 4, '颜值高，实用性强。', NULL, NOW(), NOW()),
(1066, 20241066, 33, 5, '很满意，推荐购买。', NULL, NOW(), NOW()),
-- product_id = 34 的两条评价
(1067, 20241067, 34, 3, '一分价钱一分货。', NULL, NOW(), NOW()),
(1068, 20241068, 34, 4, '还行，对得起这个价。', NULL, NOW(), NOW()),
-- product_id = 35 的两条评价
(1069, 20241069, 35, 5, '很棒，已经用了一段时间了。', NULL, NOW(), NOW()),
(1070, 20241070, 35, 5, '完美，没什么可挑剔的。', NULL, NOW(), NOW()),
-- product_id = 36 的两条评价
(1071, 20241071, 36, 2, '有瑕疵，联系客服态度不好。', NULL, NOW(), NOW()),
(1072, 20241072, 36, 1, '非常差的一次购物体验。', NULL, NOW(), NOW()),
-- product_id = 37 的两条评价
(1073, 20241073, 37, 4, '质量不错，就是有点小瑕疵。', NULL, NOW(), NOW()),
(1074, 20241074, 37, 5, '挺好的，包装也用心。', NULL, NOW(), NOW()),
-- product_id = 38 的两条评价
(1075, 20241075, 38, 3, '能用，但感觉不值这个价。', NULL, NOW(), NOW()),
(1076, 20241076, 38, 4, '总体来说还是不错的。', NULL, NOW(), NOW()),
-- product_id = 39 的两条评价
(1077, 20241077, 39, 5, '质量很好，物流快。', NULL, NOW(), NOW()),
(1078, 20241078, 39, 5, '非常满意，以后会常来。', NULL, NOW(), NOW()),
-- product_id = 40 的两条评价
(1079, 20241079, 40, 4, '颜色好看，大小合适。', NULL, NOW(), NOW()),
(1080, 20241080, 40, 3, '一般般，没什么特别。', NULL, NOW(), NOW()),
-- product_id = 41 的两条评价
(1081, 20241081, 41, 5, '精致，送朋友很合适。', NULL, NOW(), NOW()),
(1082, 20241082, 41, 4, '包装很好，商品也没问题。', NULL, NOW(), NOW()),
-- product_id = 42 的两条评价
(1083, 20241083, 42, 2, '发货慢，东西也不新。', NULL, NOW(), NOW()),
(1084, 20241084, 42, 3, '还行，勉强接受。', NULL, NOW(), NOW()),
-- product_id = 43 的两条评价
(1085, 20241085, 43, 5, '品质很棒，值得推荐。', NULL, NOW(), NOW()),
(1086, 20241086, 43, 4, '物流很快，东西不错。', NULL, NOW(), NOW()),
-- product_id = 44 的两条评价
(1087, 20241087, 44, 3, '中规中矩，没有惊喜。', NULL, NOW(), NOW()),
(1088, 20241088, 44, 5, '很满意，超出预期。', NULL, NOW(), NOW()),
-- product_id = 45 的两条评价
(1089, 20241089, 45, 4, '东西挺好，就是价格小贵。', NULL, NOW(), NOW()),
(1090, 20241090, 45, 5, '质量过硬，下次还来。', NULL, NOW(), NOW()),
-- product_id = 46 的两条评价
(1091, 20241091, 46, 2, '有异味，影响使用心情。', NULL, NOW(), NOW()),
(1092, 20241092, 46, 3, '一般，没有图片好看。', NULL, NOW(), NOW()),
-- product_id = 47 的两条评价
(1093, 20241093, 47, 5, '非常喜欢，大小刚好。', NULL, NOW(), NOW()),
(1094, 20241094, 47, 4, '还不错，如果多送点赠品更好。', NULL, NOW(), NOW()),
-- product_id = 48 的两条评价
(1095, 20241095, 48, 3, '快递包装差，东西还行。', NULL, NOW(), NOW()),
(1096, 20241096, 48, 4, '性价比高，值得购买。', NULL, NOW(), NOW()),
-- product_id = 49 的两条评价
(1097, 20241097, 49, 5, '细节处理得很好，大爱。', NULL, NOW(), NOW()),
(1098, 20241098, 49, 5, '很好，已经推荐给同事了。', NULL, NOW(), NOW()),
-- product_id = 50 的两条评价
(1099, 20241099, 50, 4, '物流快，包装严实。', NULL, NOW(), NOW()),
(1100, 20241100, 50, 3, '功能简单，够用。', NULL, NOW(), NOW()),
-- product_id = 51 的两条评价
(1101, 20241101, 51, 5, '做工精细，物有所值。', NULL, NOW(), NOW()),
(1102, 20241102, 51, 4, '还不错，如果能便宜点更好。', NULL, NOW(), NOW()),
-- product_id = 52 的两条评价
(1103, 20241103, 52, 2, '有点失望，没有描述的那么好。', NULL, NOW(), NOW()),
(1104, 20241104, 52, 3, '能用，但做工一般。', NULL, NOW(), NOW()),
-- product_id = 53 的两条评价
(1105, 20241105, 53, 5, '非常满意，下次还会光顾。', NULL, NOW(), NOW()),
(1106, 20241106, 53, 5, '质量好，服务也好。', NULL, NOW(), NOW()),
-- product_id = 54 的两条评价
(1107, 20241107, 54, 4, '挺好的，符合预期。', NULL, NOW(), NOW()),
(1108, 20241108, 54, 3, '一般般，没什么特别的。', NULL, NOW(), NOW()),
-- product_id = 55 的两条评价
(1109, 20241109, 55, 5, '颜值高，手感好。', NULL, NOW(), NOW()),
(1110, 20241110, 55, 4, '不错，可以入手。', NULL, NOW(), NOW()),
-- product_id = 56 的两条评价
(1111, 20241111, 56, 2, '物流太慢，而且包装破损。', NULL, NOW(), NOW()),
(1112, 20241112, 56, 3, '东西还行，但服务体验差。', NULL, NOW(), NOW()),
-- product_id = 57 的两条评价
(1113, 20241113, 57, 5, '买给父母的，他们很喜欢。', NULL, NOW(), NOW()),
(1114, 20241114, 57, 5, '质量很好，会回购。', NULL, NOW(), NOW()),
-- product_id = 58 的两条评价
(1115, 20241115, 58, 4, '颜色比图片暗一点，其他都好。', NULL, NOW(), NOW()),
(1116, 20241116, 58, 3, '还行，这个价位可以了。', NULL, NOW(), NOW()),
-- product_id = 59 的两条评价
(1117, 20241117, 59, 5, '精致小巧，非常满意。', NULL, NOW(), NOW()),
(1118, 20241118, 59, 4, '物流快，东西也不错。', NULL, NOW(), NOW()),
-- product_id = 60 的两条评价
(1119, 20241119, 60, 3, '中规中矩，没有特别惊喜。', NULL, NOW(), NOW()),
(1120, 20241120, 60, 5, '物超所值，推荐购买！', NULL, NOW(), NOW()),
-- product_id = 61 的两条评价
(1121, 20241121, 61, 5, '质量一如既往的好，物流也快。', NULL, NOW(), NOW()),
(1122, 20241122, 61, 4, '不错，包装严实。', NULL, NOW(), NOW()),
-- product_id = 62 的两条评价
(1123, 20241123, 62, 3, '一般般吧，没有预期的好。', NULL, NOW(), NOW()),
(1124, 20241124, 62, 5, '性价比很高，值得购买。', NULL, NOW(), NOW()),
-- product_id = 63 的两条评价
(1125, 20241125, 63, 4, '款式好看，颜色很正。', NULL, NOW(), NOW()),
(1126, 20241126, 63, 2, '收到有小瑕疵，不太满意。', NULL, NOW(), NOW()),
-- product_id = 64 的两条评价
(1127, 20241127, 64, 5, '非常精致，推荐购买。', NULL, NOW(), NOW()),
(1128, 20241128, 64, 4, '物流超快，满意。', NULL, NOW(), NOW()),
-- product_id = 65 的两条评价
(1129, 20241129, 65, 3, '能用，但细节有待提高。', NULL, NOW(), NOW()),
(1130, 20241130, 65, 5, '第二次回购了，品质稳定。', NULL, NOW(), NOW()),
-- product_id = 66 的两条评价
(1131, 20241201, 66, 4, '挺好的，大小合适。', NULL, NOW(), NOW()),
(1132, 20241202, 66, 2, '发货太慢，体验不好。', NULL, NOW(), NOW()),
-- product_id = 67 的两条评价
(1133, 20241203, 67, 5, '完美，挑不出毛病。', NULL, NOW(), NOW()),
(1134, 20241204, 67, 5, '质量很好，会再来的。', NULL, NOW(), NOW()),
-- product_id = 68 的两条评价
(1135, 20241205, 68, 3, '一分价钱一分货，还行。', NULL, NOW(), NOW()),
(1136, 20241206, 68, 4, '对得起这个价格。', NULL, NOW(), NOW()),
-- product_id = 69 的两条评价
(1137, 20241207, 69, 5, '包装精美，送礼很合适。', NULL, NOW(), NOW()),
(1138, 20241208, 69, 4, '实物和图片一致。', NULL, NOW(), NOW()),
-- product_id = 70 的两条评价
(1139, 20241209, 70, 2, '有异味，需要通风几天。', NULL, NOW(), NOW()),
(1140, 20241210, 70, 3, '一般般，能用。', NULL, NOW(), NOW()),
-- product_id = 71 的两条评价
(1141, 20241211, 71, 5, '手感超好，颜色也高级。', NULL, NOW(), NOW()),
(1142, 20241212, 71, 4, '朋友看了也想买。', NULL, NOW(), NOW()),
-- product_id = 72 的两条评价
(1143, 20241213, 72, 3, '中规中矩，无亮点。', NULL, NOW(), NOW()),
(1144, 20241214, 72, 5, '很喜欢，用起来很方便。', NULL, NOW(), NOW()),
-- product_id = 73 的两条评价
(1145, 20241215, 73, 4, '质量可以，物流给力。', NULL, NOW(), NOW()),
(1146, 20241216, 73, 2, '有划痕，有点失望。', NULL, NOW(), NOW()),
-- product_id = 74 的两条评价
(1147, 20241217, 74, 5, '做工精细，性价比高。', NULL, NOW(), NOW()),
(1148, 20241218, 74, 4, '还不错，会再买。', NULL, NOW(), NOW()),
-- product_id = 75 的两条评价
(1149, 20241219, 75, 3, '能用，但价格略贵。', NULL, NOW(), NOW()),
(1150, 20241220, 75, 5, '品质一如既往的好。', NULL, NOW(), NOW()),
-- product_id = 76 的两条评价
(1151, 20241221, 76, 4, '大小合适，颜色好看。', NULL, NOW(), NOW()),
(1152, 20241222, 76, 2, '物流暴力，盒子都扁了。', NULL, NOW(), NOW()),
-- product_id = 77 的两条评价
(1153, 20241223, 77, 5, '非常满意，很棒的设计。', NULL, NOW(), NOW()),
(1154, 20241224, 77, 5, '已经推荐给朋友了。', NULL, NOW(), NOW()),
-- product_id = 78 的两条评价
(1155, 20241225, 78, 3, '一般，没什么特别的感觉。', NULL, NOW(), NOW()),
(1156, 20241226, 78, 4, '还行，凑合用。', NULL, NOW(), NOW()),
-- product_id = 79 的两条评价
(1157, 20241227, 79, 5, '细节处理得很好，赞。', NULL, NOW(), NOW()),
(1158, 20241228, 79, 4, '挺好的，就是等得久了点。', NULL, NOW(), NOW()),
-- product_id = 80 的两条评价
(1159, 20241229, 80, 3, '实物颜色稍微深一点。', NULL, NOW(), NOW()),
(1160, 20241230, 80, 5, '很不错，家人都很喜欢。', NULL, NOW(), NOW()),
-- product_id = 81 的两条评价
(1161, 20250101, 81, 4, '质量不错，物流快。', NULL, NOW(), NOW()),
(1162, 20250102, 81, 2, '客服态度差，体验不佳。', NULL, NOW(), NOW()),
-- product_id = 82 的两条评价
(1163, 20250103, 82, 5, '包装用心，商品精美。', NULL, NOW(), NOW()),
(1164, 20250104, 82, 4, '性价比挺高的。', NULL, NOW(), NOW()),
-- product_id = 83 的两条评价
(1165, 20250105, 83, 3, '一般水平，价格合适。', NULL, NOW(), NOW()),
(1166, 20250106, 83, 5, '非常棒，下次还来。', NULL, NOW(), NOW()),
-- product_id = 84 的两条评价
(1167, 20250107, 84, 4, '款式新颖，很喜欢。', NULL, NOW(), NOW()),
(1168, 20250108, 84, 3, '做工稍显粗糙。', NULL, NOW(), NOW()),
-- product_id = 85 的两条评价
(1169, 20250109, 85, 5, '完美，和描述一致。', NULL, NOW(), NOW()),
(1170, 20250110, 85, 5, '会回购，品质放心。', NULL, NOW(), NOW()),
-- product_id = 86 的两条评价
(1171, 20250111, 86, 2, '用了几天就出问题。', NULL, NOW(), NOW()),
(1172, 20250112, 86, 3, '勉强能用，不推荐。', NULL, NOW(), NOW()),
-- product_id = 87 的两条评价
(1173, 20250113, 87, 5, '发货快，东西也很棒。', NULL, NOW(), NOW()),
(1174, 20250114, 87, 4, '不错，价格再低点就更好了。', NULL, NOW(), NOW()),
-- product_id = 88 的两条评价
(1175, 20250115, 88, 4, '质量可以，没让我失望。', NULL, NOW(), NOW()),
(1176, 20250116, 88, 5, '非常满意，已经用起来了。', NULL, NOW(), NOW()),
-- product_id = 89 的两条评价
(1177, 20250117, 89, 3, '普通商品，无功无过。', NULL, NOW(), NOW()),
(1178, 20250118, 89, 4, '实用性还可以。', NULL, NOW(), NOW()),
-- product_id = 90 的两条评价
(1179, 20250119, 90, 5, '精巧，送人自用都好。', NULL, NOW(), NOW()),
(1180, 20250120, 90, 4, '还不错，包装漂亮。', NULL, NOW(), NOW()),
-- product_id = 91 的两条评价
(1181, 20250121, 91, 5, '质量过硬，推荐。', NULL, NOW(), NOW()),
(1182, 20250122, 91, 5, '已经是老顾客了，一如既往好。', NULL, NOW(), NOW()),
-- product_id = 92 的两条评价
(1183, 20250123, 92, 3, '有点色差，其他还好。', NULL, NOW(), NOW()),
(1184, 20250124, 92, 4, '物流很快，满意。', NULL, NOW(), NOW()),
-- product_id = 93 的两条评价
(1185, 20250125, 93, 2, '包装破损，联系客服处理中。', NULL, NOW(), NOW()),
(1186, 20250126, 93, 3, '东西能用，但服务不行。', NULL, NOW(), NOW()),
-- product_id = 94 的两条评价
(1187, 20250127, 94, 5, '颜值高，功能强大。', NULL, NOW(), NOW()),
(1188, 20250128, 94, 4, '挺好的，值得入手。', NULL, NOW(), NOW()),
-- product_id = 95 的两条评价
(1189, 20250129, 95, 4, '性价比高，日常用很好。', NULL, NOW(), NOW()),
(1190, 20250130, 95, 3, '一般吧，没有特别惊艳。', NULL, NOW(), NOW()),
-- product_id = 96 的两条评价
(1191, 20250201, 96, 5, '非常满意，物流快。', NULL, NOW(), NOW()),
(1192, 20250202, 96, 5, '会推荐给身边人。', NULL, NOW(), NOW()),
-- product_id = 97 的两条评价
(1193, 20250203, 97, 3, '能用，但质感一般。', NULL, NOW(), NOW()),
(1194, 20250204, 97, 4, '价格实惠，够用。', NULL, NOW(), NOW()),
-- product_id = 98 的两条评价
(1195, 20250205, 98, 5, '做工精细，快递给力。', NULL, NOW(), NOW()),
(1196, 20250206, 98, 2, '有瑕疵，懒得换了。', NULL, NOW(), NOW()),
-- product_id = 99 的两条评价
(1197, 20250207, 99, 4, '还不错，就是包装简陋。', NULL, NOW(), NOW()),
(1198, 20250208, 99, 5, '物超所值，会回购。', NULL, NOW(), NOW()),
-- product_id = 100 的两条评价
(1199, 20250209, 100, 3, '普普通通，能用。', NULL, NOW(), NOW()),
(1200, 20250210, 100, 5, '超出预期，很好。', NULL, NOW(), NOW()),
-- product_id = 101 的两条评价
(1201, 20250211, 101, 4, '颜色和图片一样，喜欢。', NULL, NOW(), NOW()),
(1202, 20250212, 101, 3, '一般，没什么亮点。', NULL, NOW(), NOW()),
-- product_id = 102 的两条评价
(1203, 20250213, 102, 5, '质量很好，已经是回头客了。', NULL, NOW(), NOW()),
(1204, 20250214, 102, 4, '挺不错的，物流也快。', NULL, NOW(), NOW()),
-- product_id = 103 的两条评价
(1205, 20250215, 103, 2, '有异味，影响使用。', NULL, NOW(), NOW()),
(1206, 20250216, 103, 3, '一般般，不推荐。', NULL, NOW(), NOW()),
-- product_id = 104 的两条评价
(1207, 20250217, 104, 5, '设计很人性化，用着舒服。', NULL, NOW(), NOW()),
(1208, 20250218, 104, 4, '不错，如果能多几种颜色就好了。', NULL, NOW(), NOW()),
-- product_id = 105 的两条评价
(1209, 20250219, 105, 4, '整体不错，细节再优化就更好了。', NULL, NOW(), NOW()),
(1210, 20250220, 105, 5, '满意，包装也用心。', NULL, NOW(), NOW()),
-- product_id = 106 的两条评价
(1211, 20250221, 106, 3, '还行，对得起价格。', NULL, NOW(), NOW()),
(1212, 20250222, 106, 5, '质量很好，会继续支持。', NULL, NOW(), NOW()),
-- product_id = 107 的两条评价
(1213, 20250223, 107, 4, '发货快，商品没问题。', NULL, NOW(), NOW()),
(1214, 20250224, 107, 2, '快递太慢，耽误事。', NULL, NOW(), NOW()),
-- product_id = 108 的两条评价
(1215, 20250225, 108, 5, '做工扎实，非常棒。', NULL, NOW(), NOW()),
(1216, 20250226, 108, 5, '和专卖店看的一样。', NULL, NOW(), NOW()),
-- product_id = 109 的两条评价
(1217, 20250227, 109, 3, '一般，没有惊喜。', NULL, NOW(), NOW()),
(1218, 20250228, 109, 4, '还行，可接受。', NULL, NOW(), NOW()),
-- product_id = 110 的两条评价
(1219, 20250301, 110, 5, '非常满意，推荐。', NULL, NOW(), NOW()),
(1220, 20250302, 110, 4, '东西不错，下次还来。', NULL, NOW(), NOW()),
-- product_id = 111 的两条评价
(1221, 20250303, 111, 4, '颜值在线，功能实用。', NULL, NOW(), NOW()),
(1222, 20250304, 111, 3, '中规中矩，价格合适。', NULL, NOW(), NOW()),
-- product_id = 112 的两条评价
(1223, 20250305, 112, 5, '买给孩子的，他们很喜欢。', NULL, NOW(), NOW()),
(1224, 20250306, 112, 2, '有缺陷，客服不处理。', NULL, NOW(), NOW()),
-- product_id = 113 的两条评价
(1225, 20250307, 113, 4, '还不错，物流给力。', NULL, NOW(), NOW()),
(1226, 20250308, 113, 5, '品质好，价格公道。', NULL, NOW(), NOW()),
-- product_id = 114 的两条评价
(1227, 20250309, 114, 3, '能用，但不够精细。', NULL, NOW(), NOW()),
(1228, 20250310, 114, 5, '很满意，功能齐全。', NULL, NOW(), NOW()),
-- product_id = 115 的两条评价
(1229, 20250311, 115, 5, '颜色漂亮，手感好。', NULL, NOW(), NOW()),
(1230, 20250312, 115, 4, '整体不错，略有瑕疵。', NULL, NOW(), NOW()),
-- product_id = 116 的两条评价
(1231, 20250313, 116, 2, '不太满意，退货了。', NULL, NOW(), NOW()),
(1232, 20250314, 116, 3, '一般，不推荐。', NULL, NOW(), NOW()),
-- product_id = 117 的两条评价
(1233, 20250315, 117, 5, '包装很好，送礼体面。', NULL, NOW(), NOW()),
(1234, 20250316, 117, 4, '东西不错，价格稍贵。', NULL, NOW(), NOW()),
-- product_id = 118 的两条评价
(1235, 20250317, 118, 4, '性价比可以，日常够用。', NULL, NOW(), NOW()),
(1236, 20250318, 118, 5, '非常满意的一次购物。', NULL, NOW(), NOW()),
-- product_id = 119 的两条评价
(1237, 20250319, 119, 3, '普通商品，没什么特别。', NULL, NOW(), NOW()),
(1238, 20250320, 119, 5, '很好，会回购。', NULL, NOW(), NOW()),
-- product_id = 120 的两条评价
(1239, 20250321, 120, 4, '款式好看，大小合适。', NULL, NOW(), NOW()),
(1240, 20250322, 120, 2, '有色差，不太满意。', NULL, NOW(), NOW()),
-- product_id = 121 的两条评价
(1241, 20250323, 121, 5, '质量好，发货快。', NULL, NOW(), NOW()),
(1242, 20250324, 121, 5, '会一直回购的店铺。', NULL, NOW(), NOW()),
-- product_id = 122 的两条评价
(1243, 20250325, 122, 3, '一般般，能用就行。', NULL, NOW(), NOW()),
(1244, 20250326, 122, 4, '对得起这个价位。', NULL, NOW(), NOW()),
-- product_id = 123 的两条评价
(1245, 20250327, 123, 5, '很精致，细节到位。', NULL, NOW(), NOW()),
(1246, 20250328, 123, 4, '不错，比实体店便宜。', NULL, NOW(), NOW()),
-- product_id = 124 的两条评价
(1247, 20250329, 124, 2, '有瑕疵，心情不美丽。', NULL, NOW(), NOW()),
(1248, 20250330, 124, 3, '一般，不做推荐。', NULL, NOW(), NOW()),
-- product_id = 125 的两条评价
(1249, 20250331, 125, 4, '物流很快，东西完好。', NULL, NOW(), NOW()),
(1250, 20250401, 125, 5, '非常好，已经是老顾客了。', NULL, NOW(), NOW()),
-- product_id = 126 的两条评价
(1251, 20250402, 126, 3, '使用体验一般。', NULL, NOW(), NOW()),
(1252, 20250403, 126, 5, '超喜欢，颜值高。', NULL, NOW(), NOW()),
-- product_id = 127 的两条评价
(1253, 20250404, 127, 4, '品质不错，值得购买。', NULL, NOW(), NOW()),
(1254, 20250405, 127, 5, '很满意，给五星好评。', NULL, NOW(), NOW()),
-- product_id = 128 的两条评价
(1255, 20250406, 128, 2, '发货慢，包装差。', NULL, NOW(), NOW()),
(1256, 20250407, 128, 3, '凑合能用，不推荐。', NULL, NOW(), NOW()),
-- product_id = 129 的两条评价
(1257, 20250408, 129, 5, '非常棒，超出期望。', NULL, NOW(), NOW()),
(1258, 20250409, 129, 5, '已经推荐给同事了。', NULL, NOW(), NOW()),
-- product_id = 130 的两条评价
(1259, 20250410, 130, 4, '质量可以，价格适中。', NULL, NOW(), NOW()),
(1260, 20250411, 130, 3, '一般，没有想象中的好。', NULL, NOW(), NOW()),
-- product_id = 131 的两条评价
(1261, 20250412, 131, 5, '做工很好，喜欢。', NULL, NOW(), NOW()),
(1262, 20250413, 131, 4, '不错，会再来。', NULL, NOW(), NOW()),
-- product_id = 132 的两条评价
(1263, 20250414, 132, 3, '普通，无功无过。', NULL, NOW(), NOW()),
(1264, 20250415, 132, 5, '给家人买的，都说好。', NULL, NOW(), NOW()),
-- product_id = 133 的两条评价
(1265, 20250416, 133, 4, '款式可以，质量也行。', NULL, NOW(), NOW()),
(1266, 20250417, 133, 5, '很好用，设计贴心。', NULL, NOW(), NOW()),
-- product_id = 134 的两条评价
(1267, 20250418, 134, 2, '有点失望，不值。', NULL, NOW(), NOW()),
(1268, 20250419, 134, 3, '凑合吧，便宜货。', NULL, NOW(), NOW()),
-- product_id = 135 的两条评价
(1269, 20250420, 135, 5, '很高档，朋友都问链接。', NULL, NOW(), NOW()),
(1270, 20250421, 135, 4, '满意，还会继续关注。', NULL, NOW(), NOW()),
-- product_id = 136 的两条评价
(1271, 20250422, 136, 3, '一般般，物流快。', NULL, NOW(), NOW()),
(1272, 20250423, 136, 5, '质量过硬，值得信赖。', NULL, NOW(), NOW()),
-- product_id = 137 的两条评价
(1273, 20250424, 137, 4, '性价比高，日常用。', NULL, NOW(), NOW()),
(1274, 20250425, 137, 5, '非常好，会回购。', NULL, NOW(), NOW()),
-- product_id = 138 的两条评价
(1275, 20250426, 138, 2, '收到时发现划痕。', NULL, NOW(), NOW()),
(1276, 20250427, 138, 3, '能用，但品控需加强。', NULL, NOW(), NOW()),
-- product_id = 139 的两条评价
(1277, 20250428, 139, 5, '和图片一模一样，赞。', NULL, NOW(), NOW()),
(1278, 20250429, 139, 4, '包装严实，送人好看。', NULL, NOW(), NOW()),
-- product_id = 140 的两条评价
(1279, 20250430, 140, 3, '还行，没什么特别。', NULL, NOW(), NOW()),
(1280, 20250501, 140, 5, '很满意，颜色高级。', NULL, NOW(), NOW()),
-- product_id = 141 的两条评价
(1281, 20250502, 141, 4, '不错，大小正好。', NULL, NOW(), NOW()),
(1282, 20250503, 141, 5, '会推荐给朋友。', NULL, NOW(), NOW()),
-- product_id = 142 的两条评价
(1283, 20250504, 142, 2, '有瑕疵，退换麻烦。', NULL, NOW(), NOW()),
(1284, 20250505, 142, 3, '一般，不推荐。', NULL, NOW(), NOW()),
-- product_id = 143 的两条评价
(1285, 20250506, 143, 5, '买了很多次了，质量稳定。', NULL, NOW(), NOW()),
(1286, 20250507, 143, 5, '发货快，没破损。', NULL, NOW(), NOW()),
-- product_id = 144 的两条评价
(1287, 20250508, 144, 4, '性价比高，功能简单实用。', NULL, NOW(), NOW()),
(1288, 20250509, 144, 3, '勉强能接受。', NULL, NOW(), NOW()),
-- product_id = 145 的两条评价
(1289, 20250510, 145, 5, '非常漂亮，家人都喜欢。', NULL, NOW(), NOW()),
(1290, 20250511, 145, 4, '物美价廉。', NULL, NOW(), NOW()),
-- product_id = 146 的两条评价
(1291, 20250512, 146, 2, '不好用，差评。', NULL, NOW(), NOW()),
(1292, 20250513, 146, 3, '一般，不推荐购买。', NULL, NOW(), NOW()),
-- product_id = 147 的两条评价
(1293, 20250514, 147, 5, '做工讲究，细节完美。', NULL, NOW(), NOW()),
(1294, 20250515, 147, 5, '很棒，已经回购。', NULL, NOW(), NOW()),
-- product_id = 148 的两条评价
(1295, 20250516, 148, 4, '还不错，物流很快。', NULL, NOW(), NOW()),
(1296, 20250517, 148, 3, '一般水平，没什么惊艳。', NULL, NOW(), NOW()),
-- product_id = 149 的两条评价
(1297, 20250518, 149, 5, '很满意的一次购物。', NULL, NOW(), NOW()),
(1298, 20250519, 149, 4, '东西好用，客服态度好。', NULL, NOW(), NOW()),
-- product_id = 150 的两条评价
(1299, 20250520, 150, 3, '中规中矩，价格合适。', NULL, NOW(), NOW()),
(1300, 20250521, 150, 5, '非常好，下次还来。', NULL, NOW(), NOW()),
-- product_id = 151 的两条评价
(1301, 20250522, 151, 4, '颜色好看，尺寸合适。', NULL, NOW(), NOW()),
(1302, 20250523, 151, 5, '买给父母的，都说好。', NULL, NOW(), NOW()),
-- product_id = 152 的两条评价
(1303, 20250524, 152, 2, '质量差，用了几天就坏了。', NULL, NOW(), NOW()),
(1304, 20250525, 152, 3, '能用，但质量堪忧。', NULL, NOW(), NOW()),
-- product_id = 153 的两条评价
(1305, 20250526, 153, 5, '完美，挑不出任何毛病。', NULL, NOW(), NOW()),
(1306, 20250527, 153, 5, '非常喜欢，会回购。', NULL, NOW(), NOW()),
-- product_id = 154 的两条评价
(1307, 20250528, 154, 3, '一般般，对得起价格。', NULL, NOW(), NOW()),
(1308, 20250529, 154, 4, '还算满意，物流不错。', NULL, NOW(), NOW()),
-- product_id = 155 的两条评价
(1309, 20250530, 155, 5, '手感很好，颜值爆表。', NULL, NOW(), NOW()),
(1310, 20250531, 155, 4, '包装用心，商品不错。', NULL, NOW(), NOW()),
-- product_id = 156 的两条评价
(1311, 20250601, 156, 2, '有瑕疵，差评。', NULL, NOW(), NOW()),
(1312, 20250602, 156, 3, '一般，不推荐。', NULL, NOW(), NOW()),
-- product_id = 157 的两条评价
(1313, 20250603, 157, 5, '细节精致，爱不释手。', NULL, NOW(), NOW()),
(1314, 20250604, 157, 4, '同事看了想买同款。', NULL, NOW(), NOW()),
-- product_id = 158 的两条评价
(1315, 20250605, 158, 3, '没有预期的好。', NULL, NOW(), NOW()),
(1316, 20250606, 158, 5, '性价比高，会再买。', NULL, NOW(), NOW()),
-- product_id = 159 的两条评价
(1317, 20250607, 159, 4, '挺好的，满意。', NULL, NOW(), NOW()),
(1318, 20250608, 159, 5, '质量不错，发货快。', NULL, NOW(), NOW()),
-- product_id = 160 的两条评价
(1319, 20250609, 160, 3, '普通，没有特别之处。', NULL, NOW(), NOW()),
(1320, 20250610, 160, 5, '很不错，会回购。', NULL, NOW(), NOW()),
-- product_id = 161 的两条评价
(1321, 20250611, 161, 4, '做工可以，价格实在。', NULL, NOW(), NOW()),
(1322, 20250612, 161, 5, '满意，下次还来。', NULL, NOW(), NOW()),
-- product_id = 162 的两条评价
(1323, 20250613, 162, 2, '有瑕疵，勉强接受。', NULL, NOW(), NOW()),
(1324, 20250614, 162, 3, '一般水平。', NULL, NOW(), NOW()),
-- product_id = 163 的两条评价
(1325, 20250615, 163, 5, '非常精致，推荐。', NULL, NOW(), NOW()),
(1326, 20250616, 163, 4, '物流快，包装好。', NULL, NOW(), NOW()),
-- product_id = 164 的两条评价
(1327, 20250617, 164, 3, '能用，没亮点。', NULL, NOW(), NOW()),
(1328, 20250618, 164, 5, '品质很好，回购。', NULL, NOW(), NOW()),
-- product_id = 165 的两条评价
(1329, 20250619, 165, 4, '物美价廉，不错。', NULL, NOW(), NOW()),
(1330, 20250620, 165, 5, '很棒，朋友都说好看。', NULL, NOW(), NOW()),
-- product_id = 166 的两条评价
(1331, 20250621, 166, 2, '不太满意，做工粗糙。', NULL, NOW(), NOW()),
(1332, 20250622, 166, 3, '一般般，不推荐。', NULL, NOW(), NOW()),
-- product_id = 167 的两条评价
(1333, 20250623, 167, 5, '质感很好，上档次。', NULL, NOW(), NOW()),
(1334, 20250624, 167, 5, '会再来的，好店。', NULL, NOW(), NOW()),
-- product_id = 168 的两条评价
(1335, 20250625, 168, 4, '挺不错的，物流快。', NULL, NOW(), NOW()),
(1336, 20250626, 168, 3, '价格合适，品质一般。', NULL, NOW(), NOW()),
-- product_id = 169 的两条评价
(1337, 20250627, 169, 5, '颜值和实用性并存。', NULL, NOW(), NOW()),
(1338, 20250628, 169, 4, '很满意，就是等得久了点。', NULL, NOW(), NOW()),
-- product_id = 170 的两条评价
(1339, 20250629, 170, 3, '普通，没有惊喜。', NULL, NOW(), NOW()),
(1340, 20250630, 170, 5, '买对了，家人都说好。', NULL, NOW(), NOW());

CREATE TABLE `kb_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(255) NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `version` INT NOT NULL DEFAULT 1,
  `storage_path` VARCHAR(512) DEFAULT NULL,
  `content_text` LONGTEXT,
  `created_by` VARCHAR(64) DEFAULT 'admin',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `kb_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `chunk_index` INT NOT NULL,
  `content` LONGTEXT NOT NULL,
  `char_count` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'active',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_chunk_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `kb_index_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `version` INT NOT NULL,
  `embedding_provider` VARCHAR(64) NOT NULL,
  `vector_collection` VARCHAR(128) NOT NULL,
  `indexed_chunk_count` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_index_record_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `kb_hit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `chunk_id` BIGINT NOT NULL,
  `query_text` VARCHAR(1000) NOT NULL,
  `conversation_id` VARCHAR(128) DEFAULT NULL,
  `hit_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_hit_log_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `kb_miss_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `query_text` VARCHAR(1000) NOT NULL,
  `conversation_id` VARCHAR(128) DEFAULT NULL,
  `confidence` DECIMAL(5,4) DEFAULT NULL,
  `fallback_reason` VARCHAR(1000) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'open',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_miss_log_status` (`status`),
  KEY `idx_kb_miss_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `customer_service_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `query_text` VARCHAR(1000) NOT NULL,
  `conversation_id` VARCHAR(128) DEFAULT NULL,
  `route` VARCHAR(64) NOT NULL,
  `source_type` VARCHAR(64) NOT NULL,
  `source_id` VARCHAR(128) DEFAULT NULL,
  `confidence` DECIMAL(5,4) DEFAULT NULL,
  `fallback_reason` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_customer_service_log_route` (`route`),
  KEY `idx_customer_service_log_source_type` (`source_type`),
  KEY `idx_customer_service_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

