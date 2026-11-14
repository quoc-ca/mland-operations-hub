-- Initialize database and privileges for PPSWBS backend
CREATE DATABASE IF NOT EXISTS ppswbs_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant full privileges to 'sa' user
GRANT ALL PRIVILEGES ON *.* TO 'sa'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;

