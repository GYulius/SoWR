-- Create MySQL user for the application
-- Run this script as root user in MySQL Workbench

-- Create the user (if it doesn't exist)
CREATE USER IF NOT EXISTS 'cruise_app'@'localhost' IDENTIFIED BY 'cruise_password';

-- Grant all privileges on the cruise_recommender database
GRANT ALL PRIVILEGES ON cruise_recommender.* TO 'cruise_app'@'localhost';

-- Flush privileges to apply changes
FLUSH PRIVILEGES;

-- Verify the user was created
SELECT User, Host FROM mysql.user WHERE User = 'cruise_app';

