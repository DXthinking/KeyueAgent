CREATE DATABASE IF NOT EXISTS ecommerce_cs CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ecommerce_cs;

CREATE TABLE IF NOT EXISTS t_order (
  id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, product_name VARCHAR(255) NOT NULL,
  product_category VARCHAR(64), amount DECIMAL(12,2) NOT NULL, status VARCHAR(32) NOT NULL,
  tracking_no VARCHAR(128), created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  KEY idx_order_user_created (user_id, created_at), KEY idx_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_refund (
  id VARCHAR(64) PRIMARY KEY, order_id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL,
  amount DECIMAL(12,2) NOT NULL, reason VARCHAR(500), status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL,
  KEY idx_refund_order (order_id), KEY idx_refund_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_return (
  id VARCHAR(64) PRIMARY KEY, order_id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL,
  reason VARCHAR(500), return_logistics_no VARCHAR(128), status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL,
  KEY idx_return_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_complaint (
  id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, order_id VARCHAR(64), content TEXT NOT NULL,
  level VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL,
  KEY idx_complaint_user (user_id), KEY idx_complaint_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_voucher (
  id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, amount DECIMAL(12,2) NOT NULL,
  reason VARCHAR(500), status VARCHAR(32) NOT NULL, valid_until DATE, created_at DATETIME NOT NULL,
  KEY idx_voucher_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_chat_session (
  id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64), status VARCHAR(32) DEFAULT 'OPEN', created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id VARCHAR(64) NOT NULL, user_id VARCHAR(64),
  role VARCHAR(32) NOT NULL, content TEXT NOT NULL, created_at DATETIME NOT NULL,
  KEY idx_chat_session_time (session_id, created_at), KEY idx_chat_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
