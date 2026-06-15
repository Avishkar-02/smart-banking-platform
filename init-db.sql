-- ================================================================
-- init-db.sql
-- Place this file at: C:\Personal_Project\Microservice_project\init-db.sql
-- (same folder as docker-compose.yml)
--
-- Mounted into /docker-entrypoint-initdb.d/ of the MySQL container.
-- Runs automatically ONLY on the very first container startup
-- (i.e. when the mysql-data volume is empty/new).
--
-- Your services already have createDatabaseIfNotExist=true in their
-- JDBC URLs, so this is technically a safety net — but it ensures all
-- 5 databases exist immediately, before any service even tries to connect.
-- ================================================================

CREATE DATABASE IF NOT EXISTS user_db;
CREATE DATABASE IF NOT EXISTS account_db;
CREATE DATABASE IF NOT EXISTS transaction_db;
CREATE DATABASE IF NOT EXISTS fraud_db;
CREATE DATABASE IF NOT EXISTS notification_db;
