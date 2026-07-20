INSERT INTO app_settings (setting_key, setting_value, `sensitive`) VALUES
    ('auth.altcha_enabled', 'false', 0),
    ('auth.altcha_cost', '5000', 0),
    ('auth.altcha_expires_seconds', '300', 0)
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
