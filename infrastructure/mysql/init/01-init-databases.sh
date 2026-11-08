#!/bin/bash
set -e

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL

CREATE DATABASE IF NOT EXISTS stockpulse_order;
CREATE DATABASE IF NOT EXISTS stockpulse_payment;

CREATE USER IF NOT EXISTS 'order_service'@'%'
IDENTIFIED BY '${ORDER_DB_PASSWORD}';

CREATE USER IF NOT EXISTS 'payment_service'@'%'
IDENTIFIED BY '${PAYMENT_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON stockpulse_order.*
TO 'order_service'@'%';

GRANT ALL PRIVILEGES ON stockpulse_payment.*
TO 'payment_service'@'%';

FLUSH PRIVILEGES;

SQL