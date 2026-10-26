-- Read-only target check before applying the platform catalog migration.
SELECT DATABASE() AS target_database;
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'platform_sites';
