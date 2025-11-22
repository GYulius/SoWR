-- Social Web Recommender for Cruising Ports - Database Schema
-- MySQL 8.0 Compatible

-- Create database
CREATE DATABASE IF NOT EXISTS cruise_recommender 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE cruise_recommender;

-- Users table for cruise passengers
CREATE TABLE users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    nationality VARCHAR(100),
    preferences JSON,
    interests JSON,
    dietary_restrictions JSON,
    accessibility_needs JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    is_active BOOLEAN DEFAULT TRUE,
    INDEX idx_email (email),
    INDEX idx_created_at (created_at)
);

-- Ports table (imported from ports_A.json)
-- Using BIGINT for id to match JPA entity (Long type)
CREATE TABLE ports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    port_code VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(100) NOT NULL,
    geo VARCHAR(100),
    region VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    coordinates POINT SRID 4326 NOT NULL,
    capacity INT NOT NULL DEFAULT 0,
    facilities JSON,
    amenities JSON,
    docking_fees DECIMAL(10, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    timezone VARCHAR(50),
    language VARCHAR(200),
    tourism1 VARCHAR(500),
    foodie_main JSON,
    foodie_dessert JSON,
    meal_venues JSON COMMENT 'Meal Venues (MEAL_VENUE category) - e.g., Coffee shops, Cafes, Food courts',
    activities JSON COMMENT 'Activities keywords (ACTIVITY category)',
    restaurants JSON COMMENT 'Restaurant types/cuisines (RESTAURANT category)',
    excursions JSON COMMENT 'Shore Excursion keywords (EXCURSION category)',
    general_interests JSON COMMENT 'General Interests keywords (GENERAL category)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_country (country),
    INDEX idx_geo (geo),
    INDEX idx_port_code (port_code),
    SPATIAL INDEX idx_spatial_coords (coordinates)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Categories for attractions, restaurants, etc.
CREATE TABLE categories (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id INT NULL,
    icon VARCHAR(100),
    color VARCHAR(7),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL,
    INDEX idx_parent_id (parent_id)
);

-- Attractions table
CREATE TABLE attractions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    port_id INT NOT NULL,
    category_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    address TEXT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    coordinates POINT AS (POINT(longitude, latitude)) STORED,
    rating DECIMAL(3, 2) DEFAULT 0.00,
    price_range ENUM('free', 'low', 'medium', 'high', 'luxury'),
    opening_hours JSON,
    contact_info JSON,
    images JSON,
    accessibility_features JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (port_id) REFERENCES ports(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_port_id (port_id),
    INDEX idx_category_id (category_id),
    INDEX idx_rating (rating),
    SPATIAL INDEX idx_spatial_coords (coordinates)
);

-- Restaurants table (from foodiedb integration)
CREATE TABLE restaurants (
    id INT PRIMARY KEY AUTO_INCREMENT,
    port_id INT NOT NULL,
    category_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    cuisine_type VARCHAR(100),
    address TEXT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    coordinates POINT AS (POINT(longitude, latitude)) STORED,
    rating DECIMAL(3, 2) DEFAULT 0.00,
    price_range ENUM('budget', 'moderate', 'expensive', 'luxury'),
    opening_hours JSON,
    contact_info JSON,
    menu_items JSON,
    dietary_options JSON,
    images JSON,
    reservation_required BOOLEAN DEFAULT FALSE,
    max_capacity INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (port_id) REFERENCES ports(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_port_id (port_id),
    INDEX idx_cuisine_type (cuisine_type),
    INDEX idx_price_range (price_range),
    SPATIAL INDEX idx_spatial_coords (coordinates)
);

-- Activities table
CREATE TABLE activities (
    id INT PRIMARY KEY AUTO_INCREMENT,
    port_id INT NOT NULL,
    category_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    activity_type VARCHAR(100),
    duration_minutes INT,
    difficulty_level ENUM('easy', 'moderate', 'challenging', 'expert'),
    min_age INT,
    max_age INT,
    group_size_min INT,
    group_size_max INT,
    price_per_person DECIMAL(10, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    equipment_provided JSON,
    requirements JSON,
    images JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (port_id) REFERENCES ports(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_port_id (port_id),
    INDEX idx_activity_type (activity_type),
    INDEX idx_difficulty_level (difficulty_level)
);

-- Cruise ships table
CREATE TABLE cruise_ships (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    cruise_line VARCHAR(100) NOT NULL,
    capacity INT NOT NULL,
    length_meters DECIMAL(8, 2),
    width_meters DECIMAL(8, 2),
    year_built YEAR,
    amenities JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cruise_line (cruise_line)
);

-- Cruise schedules table
CREATE TABLE cruise_schedules (
    id INT PRIMARY KEY AUTO_INCREMENT,
    ship_id INT NOT NULL,
    port_id INT NOT NULL,
    arrival_datetime DATETIME NOT NULL,
    departure_datetime DATETIME NOT NULL,
    estimated_passengers INT NOT NULL,
    actual_passengers INT NULL,
    dock_number VARCHAR(20),
    tender_required BOOLEAN DEFAULT FALSE,
    status ENUM('scheduled', 'arrived', 'departed', 'cancelled') DEFAULT 'scheduled',
    weather_conditions JSON,
    special_requirements JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (ship_id) REFERENCES cruise_ships(id) ON DELETE CASCADE,
    FOREIGN KEY (port_id) REFERENCES ports(id) ON DELETE CASCADE,
    INDEX idx_ship_id (ship_id),
    INDEX idx_port_id (port_id),
    INDEX idx_arrival_datetime (arrival_datetime),
    INDEX idx_status (status)
);

-- Publishers table (local businesses)
CREATE TABLE publishers (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    business_type ENUM('restaurant', 'attraction', 'activity', 'shop', 'service'),
    description TEXT,
    contact_info JSON,
    verification_status ENUM('pending', 'verified', 'rejected') DEFAULT 'pending',
    subscription_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_business_type (business_type),
    INDEX idx_verification_status (verification_status)
);

-- Subscriptions table (publisher-subscriber system)
CREATE TABLE subscriptions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    publisher_id INT NOT NULL,
    subscription_type ENUM('all', 'specific_categories', 'custom'),
    preferences JSON,
    notification_settings JSON,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (publisher_id) REFERENCES publishers(id) ON DELETE CASCADE,
    UNIQUE KEY unique_subscription (user_id, publisher_id),
    INDEX idx_user_id (user_id),
    INDEX idx_publisher_id (publisher_id),
    INDEX idx_is_active (is_active)
);

-- Recommendations table
CREATE TABLE recommendations (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    port_id INT NOT NULL,
    item_type ENUM('attraction', 'restaurant', 'activity', 'shop'),
    item_id INT NOT NULL,
    score DECIMAL(3, 2) NOT NULL,
    reasoning TEXT,
    algorithm_version VARCHAR(20),
    context JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (port_id) REFERENCES ports(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_port_id (port_id),
    INDEX idx_item_type (item_type),
    INDEX idx_score (score),
    INDEX idx_created_at (created_at)
);

-- User interactions table (for ML training)
CREATE TABLE user_interactions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    item_type ENUM('attraction', 'restaurant', 'activity', 'shop'),
    item_id INT NOT NULL,
    interaction_type ENUM('view', 'like', 'bookmark', 'book', 'review', 'share'),
    rating DECIMAL(3, 2) NULL,
    review_text TEXT NULL,
    context JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_item_type (item_type),
    INDEX idx_interaction_type (interaction_type),
    INDEX idx_created_at (created_at)
);

-- Bookings table
CREATE TABLE bookings (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    item_type ENUM('attraction', 'restaurant', 'activity', 'shop'),
    item_id INT NOT NULL,
    cruise_schedule_id INT NOT NULL,
    booking_date DATE NOT NULL,
    booking_time TIME NOT NULL,
    party_size INT NOT NULL DEFAULT 1,
    total_price DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status ENUM('pending', 'confirmed', 'cancelled', 'completed') DEFAULT 'pending',
    special_requests TEXT,
    contact_info JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (cruise_schedule_id) REFERENCES cruise_schedules(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_item_type (item_type),
    INDEX idx_booking_date (booking_date),
    INDEX idx_status (status)
);

-- Notifications table
CREATE TABLE notifications (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    publisher_id INT NULL,
    notification_type ENUM('recommendation', 'booking_confirmation', 'cruise_arrival', 'promotion', 'system'),
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    data JSON,
    is_read BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (publisher_id) REFERENCES publishers(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_notification_type (notification_type),
    INDEX idx_is_read (is_read),
    INDEX idx_sent_at (sent_at)
);

-- RDF mappings table (for SPARQL integration)
CREATE TABLE rdf_mappings (
    id INT PRIMARY KEY AUTO_INCREMENT,
    local_table VARCHAR(100) NOT NULL,
    local_id INT NOT NULL,
    rdf_uri VARCHAR(500) NOT NULL,
    rdf_type VARCHAR(200) NOT NULL,
    last_synced TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sync_status ENUM('pending', 'synced', 'error') DEFAULT 'pending',
    INDEX idx_local_table (local_table),
    INDEX idx_rdf_uri (rdf_uri),
    INDEX idx_sync_status (sync_status)
);

-- Analytics events table
CREATE TABLE analytics_events (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSON,
    session_id VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);

-- Create views for common queries
CREATE VIEW user_recommendations_summary AS
SELECT 
    u.id as user_id,
    u.email,
    COUNT(r.id) as total_recommendations,
    AVG(r.score) as avg_score,
    MAX(r.created_at) as last_recommendation
FROM users u
LEFT JOIN recommendations r ON u.id = r.user_id
GROUP BY u.id, u.email;

CREATE VIEW port_capacity_summary AS
SELECT 
    p.id as port_id,
    p.name as port_name,
    p.capacity,
    COUNT(cs.id) as scheduled_arrivals,
    SUM(cs.estimated_passengers) as total_estimated_passengers,
    AVG(cs.estimated_passengers) as avg_passengers_per_ship
FROM ports p
LEFT JOIN cruise_schedules cs ON p.id = cs.port_id 
    AND cs.status IN ('scheduled', 'arrived')
    AND cs.arrival_datetime >= CURDATE()
GROUP BY p.id, p.name, p.capacity;

-- Insert sample categories
INSERT INTO categories (name, description, icon, color) VALUES
('Historical Sites', 'Museums, monuments, and historical landmarks', 'museum', '#8B4513'),
('Nature & Parks', 'National parks, gardens, and natural attractions', 'tree', '#228B22'),
('Entertainment', 'Theaters, shows, and entertainment venues', 'theater', '#FF6347'),
('Shopping', 'Markets, malls, and shopping districts', 'shopping', '#FFD700'),
('Adventure', 'Outdoor activities and adventure sports', 'mountain', '#FF4500'),
('Cultural', 'Cultural centers, art galleries, and local traditions', 'palette', '#9370DB'),
('Fine Dining', 'Upscale restaurants and gourmet experiences', 'restaurant', '#DC143C'),
('Casual Dining', 'Family-friendly restaurants and cafes', 'coffee', '#8B4513'),
('Street Food', 'Local street food and food markets', 'food', '#FF8C00'),
('Water Activities', 'Beach activities, water sports, and marine tours', 'water', '#00BFFF'),
('City Tours', 'Walking tours, bus tours, and city exploration', 'city', '#696969'),
('Nightlife', 'Bars, clubs, and evening entertainment', 'night', '#2F4F4F');

-- Create indexes for better performance
CREATE INDEX idx_recommendations_user_port ON recommendations(user_id, port_id);
CREATE INDEX idx_interactions_user_item ON user_interactions(user_id, item_type, item_id);
CREATE INDEX idx_bookings_user_cruise ON bookings(user_id, cruise_schedule_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);

-- Create stored procedures for common operations
DELIMITER //

CREATE PROCEDURE GetUserRecommendations(
    IN p_user_id INT,
    IN p_port_id INT,
    IN p_limit INT DEFAULT 10
)
BEGIN
    SELECT 
        r.id,
        r.item_type,
        r.item_id,
        r.score,
        r.reasoning,
        CASE 
            WHEN r.item_type = 'attraction' THEN a.name
            WHEN r.item_type = 'restaurant' THEN res.name
            WHEN r.item_type = 'activity' THEN act.name
        END as item_name,
        CASE 
            WHEN r.item_type = 'attraction' THEN a.description
            WHEN r.item_type = 'restaurant' THEN res.description
            WHEN r.item_type = 'activity' THEN act.description
        END as item_description
    FROM recommendations r
    LEFT JOIN attractions a ON r.item_type = 'attraction' AND r.item_id = a.id
    LEFT JOIN restaurants res ON r.item_type = 'restaurant' AND r.item_id = res.id
    LEFT JOIN activities act ON r.item_type = 'activity' AND r.item_id = act.id
    WHERE r.user_id = p_user_id 
        AND r.port_id = p_port_id
    ORDER BY r.score DESC
    LIMIT p_limit;
END //

CREATE PROCEDURE UpdatePublisherSubscriptionCount(IN p_publisher_id INT)
BEGIN
    UPDATE publishers 
    SET subscription_count = (
        SELECT COUNT(*) 
        FROM subscriptions 
        WHERE publisher_id = p_publisher_id AND is_active = TRUE
    )
    WHERE id = p_publisher_id;
END //

DELIMITER ;

-- Grant permissions (adjust as needed for your environment)
-- GRANT ALL PRIVILEGES ON cruise_recommender.* TO 'cruise_app'@'localhost' IDENTIFIED BY 'secure_password';
-- FLUSH PRIVILEGES;
