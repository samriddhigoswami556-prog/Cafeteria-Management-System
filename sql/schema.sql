
-- Cafeteria Management System - PostgreSQL schema

-- Clean up existing objects safely
DROP VIEW IF EXISTS v_order_summary;
DROP TABLE IF EXISTS order_item CASCADE;
DROP TABLE IF EXISTS cafe_order CASCADE;
DROP TABLE IF EXISTS menu_item CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 1. USERS
CREATE TABLE users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(30) NOT NULL UNIQUE,
    password_hash VARCHAR(64) NOT NULL,
    full_name VARCHAR(60) NOT NULL,
    email VARCHAR(60),
    role VARCHAR(10) DEFAULT 'CUSTOMER' NOT NULL
        CHECK (role IN ('ADMIN', 'CUSTOMER')),
    cust_type VARCHAR(10) DEFAULT 'STUDENT' NOT NULL
        CHECK (cust_type IN ('STUDENT', 'STAFF', 'GUEST')),
    wallet_balance NUMERIC(8,2) DEFAULT 0 NOT NULL
        CHECK (wallet_balance >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. MENU ITEMS
CREATE TABLE menu_item (
    item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_name VARCHAR(50) NOT NULL UNIQUE,
    category VARCHAR(12) NOT NULL
        CHECK (category IN ('BREAKFAST', 'MEALS', 'SNACKS', 'BEVERAGES', 'DESSERTS')),
    price NUMERIC(6,2) NOT NULL CHECK (price > 0),
    stock INTEGER DEFAULT 0 NOT NULL CHECK (stock >= 0),
    available CHAR(1) DEFAULT 'Y' NOT NULL
        CHECK (available IN ('Y', 'N'))
);

-- 3. ORDERS
CREATE TABLE cafe_order (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    subtotal NUMERIC(9,2) NOT NULL,
    discount NUMERIC(9,2) DEFAULT 0 NOT NULL,
    tax NUMERIC(9,2) DEFAULT 0 NOT NULL,
    total NUMERIC(9,2) NOT NULL,
    pay_mode VARCHAR(10) NOT NULL
        CHECK (pay_mode IN ('WALLET', 'CASH')),
    status VARCHAR(12) DEFAULT 'PLACED' NOT NULL
        CHECK (status IN ('PLACED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fk_order_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 4. ORDER ITEMS
CREATE TABLE order_item (
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    qty INTEGER NOT NULL CHECK (qty > 0),
    unit_price NUMERIC(6,2) NOT NULL,
    line_total NUMERIC(9,2) NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (order_id, item_id),
    CONSTRAINT fk_oi_order
        FOREIGN KEY (order_id) REFERENCES cafe_order(order_id),
    CONSTRAINT fk_oi_item
        FOREIGN KEY (item_id) REFERENCES menu_item(item_id)
);

-- 5. INDEXES
CREATE INDEX idx_order_user ON cafe_order(user_id);
CREATE INDEX idx_menu_category ON menu_item(category);

-- 6. ORDER SUMMARY VIEW
CREATE VIEW v_order_summary AS
SELECT
    o.order_id,
    u.username,
    u.full_name,
    o.order_date,
    o.subtotal,
    o.discount,
    o.tax,
    o.total,
    o.pay_mode,
    o.status
FROM cafe_order o
JOIN users u ON u.user_id = o.user_id;

-- 7. SAMPLE USERS
INSERT INTO users
    (username, password_hash, full_name, email, role, cust_type, wallet_balance)
VALUES
    ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
     'Cafeteria Manager', 'admin@cafe.edu', 'ADMIN', 'STAFF', 0),
    ('student1', '703b0a3d6ad75b649a28adde7d83c6251da457549263bc7ff45ec709b0a8448b',
     'Anitha Kumar', 'anitha@mail.com', 'CUSTOMER', 'STUDENT', 500),
    ('staff1', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6',
     'Bharath Raj', 'bharath@mail.com', 'CUSTOMER', 'STAFF', 800);

-- 8. SAMPLE MENU
INSERT INTO menu_item (item_name, category, price, stock) VALUES
    ('Idli (2 pcs)', 'BREAKFAST', 30, 60),
    ('Masala Dosa', 'BREAKFAST', 50, 40),
    ('Veg Meals', 'MEALS', 90, 50),
    ('Chicken Biryani', 'MEALS', 140, 30),
    ('Paneer Fried Rice', 'MEALS', 110, 25),
    ('Samosa', 'SNACKS', 15, 100),
    ('French Fries', 'SNACKS', 60, 35),
    ('Veg Sandwich', 'SNACKS', 45, 40),
    ('Filter Coffee', 'BEVERAGES', 20, 80),
    ('Masala Tea', 'BEVERAGES', 15, 80),
    ('Cold Coffee', 'BEVERAGES', 50, 8),
    ('Gulab Jamun (2 pcs)', 'DESSERTS', 35, 30),
    ('Ice Cream Cup', 'DESSERTS', 40, 25);

COMMIT;
