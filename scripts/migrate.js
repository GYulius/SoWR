const mysql = require('mysql2/promise');
const fs = require('fs').promises;
const path = require('path');

// Database configuration
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 3306,
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'cruise_recommender',
  multipleStatements: true
};

async function migrate() {
  let connection;
  
  try {
    console.log('🔄 Starting database migration...');
    
    // Create connection
    connection = await mysql.createConnection(dbConfig);
    
    // Read schema file
    const schemaPath = path.join(__dirname, '../database/schema.sql');
    const schema = await fs.readFile(schemaPath, 'utf8');
    
    // Execute schema
    await connection.execute(schema);
    
    console.log('✅ Database migration completed successfully');
    
  } catch (error) {
    console.error('❌ Database migration failed:', error);
    process.exit(1);
  } finally {
    if (connection) {
      await connection.end();
    }
  }
}

async function seed() {
  let connection;
  
  try {
    console.log('🌱 Starting database seeding...');
    
    // Create connection
    connection = await mysql.createConnection(dbConfig);
    
    // Read and import ports data
    const portsPath = path.join(__dirname, '../data/ports_A.json');
    const portsData = JSON.parse(await fs.readFile(portsPath, 'utf8'));
    
    console.log(`📊 Importing ${portsData.length} ports...`);
    
    for (const port of portsData) {
      await connection.execute(`
        INSERT INTO ports (port_code, name, country, region, city, latitude, longitude, capacity, facilities, amenities, docking_fees, currency, timezone, language)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
        name = VALUES(name),
        country = VALUES(country),
        region = VALUES(region),
        city = VALUES(city),
        latitude = VALUES(latitude),
        longitude = VALUES(longitude),
        capacity = VALUES(capacity),
        facilities = VALUES(facilities),
        amenities = VALUES(amenities),
        docking_fees = VALUES(docking_fees),
        currency = VALUES(currency),
        timezone = VALUES(timezone),
        language = VALUES(language)
      `, [
        port.port_code,
        port.name,
        port.country,
        port.region,
        port.city,
        port.latitude,
        port.longitude,
        port.capacity,
        JSON.stringify(port.facilities),
        JSON.stringify(port.amenities),
        port.docking_fees,
        port.currency,
        port.timezone,
        port.language
      ]);
    }
    
    // Read and import attractions data
    const attractionsPath = path.join(__dirname, '../data/attractions.json');
    const attractionsData = JSON.parse(await fs.readFile(attractionsPath, 'utf8'));
    
    console.log(`🏛️ Importing ${attractionsData.length} attractions...`);
    
    for (const attraction of attractionsData) {
      // Get category ID
      const [categoryRows] = await connection.execute(
        'SELECT id FROM categories WHERE name = ?',
        [attraction.category]
      );
      
      const categoryId = categoryRows.length > 0 ? categoryRows[0].id : 1;
      
      await connection.execute(`
        INSERT INTO attractions (port_id, category_id, name, description, latitude, longitude, rating, price_range, opening_hours, contact_info, accessibility_features, images)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
        name = VALUES(name),
        description = VALUES(description),
        latitude = VALUES(latitude),
        longitude = VALUES(longitude),
        rating = VALUES(rating),
        price_range = VALUES(price_range),
        opening_hours = VALUES(opening_hours),
        contact_info = VALUES(contact_info),
        accessibility_features = VALUES(accessibility_features),
        images = VALUES(images)
      `, [
        attraction.port_id,
        categoryId,
        attraction.name,
        attraction.description,
        attraction.latitude,
        attraction.longitude,
        attraction.rating,
        attraction.price_range,
        JSON.stringify(attraction.opening_hours),
        JSON.stringify(attraction.contact_info),
        JSON.stringify(attraction.accessibility_features),
        JSON.stringify(attraction.images)
      ]);
    }
    
    // Read and import restaurants data
    const restaurantsPath = path.join(__dirname, '../data/restaurants.json');
    const restaurantsData = JSON.parse(await fs.readFile(restaurantsPath, 'utf8'));
    
    console.log(`🍽️ Importing ${restaurantsData.length} restaurants...`);
    
    for (const restaurant of restaurantsData) {
      // Get category ID
      const [categoryRows] = await connection.execute(
        'SELECT id FROM categories WHERE name = ?',
        [restaurant.category]
      );
      
      const categoryId = categoryRows.length > 0 ? categoryRows[0].id : 1;
      
      await connection.execute(`
        INSERT INTO restaurants (port_id, category_id, name, description, cuisine_type, latitude, longitude, rating, price_range, opening_hours, contact_info, menu_items, dietary_options, reservation_required, max_capacity)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
        name = VALUES(name),
        description = VALUES(description),
        cuisine_type = VALUES(cuisine_type),
        latitude = VALUES(latitude),
        longitude = VALUES(longitude),
        rating = VALUES(rating),
        price_range = VALUES(price_range),
        opening_hours = VALUES(opening_hours),
        contact_info = VALUES(contact_info),
        menu_items = VALUES(menu_items),
        dietary_options = VALUES(dietary_options),
        reservation_required = VALUES(reservation_required),
        max_capacity = VALUES(max_capacity)
      `, [
        restaurant.port_id,
        categoryId,
        restaurant.name,
        restaurant.description,
        restaurant.cuisine_type,
        restaurant.latitude,
        restaurant.longitude,
        restaurant.rating,
        restaurant.price_range,
        JSON.stringify(restaurant.opening_hours),
        JSON.stringify(restaurant.contact_info),
        JSON.stringify(restaurant.menu_items),
        JSON.stringify(restaurant.dietary_options),
        restaurant.reservation_required,
        restaurant.max_capacity
      ]);
    }
    
    // Create sample cruise ships
    console.log('🚢 Creating sample cruise ships...');
    
    const cruiseShips = [
      {
        name: 'Harmony of the Seas',
        cruise_line: 'Royal Caribbean',
        capacity: 5479,
        length_meters: 362.1,
        width_meters: 47.4,
        year_built: 2016,
        amenities: JSON.stringify(['water_park', 'casino', 'theater', 'spa', 'multiple_restaurants'])
      },
      {
        name: 'Norwegian Epic',
        cruise_line: 'Norwegian Cruise Line',
        capacity: 4100,
        length_meters: 329.5,
        width_meters: 40.5,
        year_built: 2010,
        amenities: JSON.stringify(['water_slides', 'casino', 'broadway_shows', 'spa', 'freestyle_dining'])
      },
      {
        name: 'MSC Grandiosa',
        cruise_line: 'MSC Cruises',
        capacity: 6334,
        length_meters: 331.0,
        width_meters: 43.0,
        year_built: 2019,
        amenities: JSON.stringify(['water_park', 'casino', 'theater', 'spa', 'yacht_club'])
      }
    ];
    
    for (const ship of cruiseShips) {
      await connection.execute(`
        INSERT INTO cruise_ships (name, cruise_line, capacity, length_meters, width_meters, year_built, amenities)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
        name = VALUES(name),
        cruise_line = VALUES(cruise_line),
        capacity = VALUES(capacity),
        length_meters = VALUES(length_meters),
        width_meters = VALUES(width_meters),
        year_built = VALUES(year_built),
        amenities = VALUES(amenities)
      `, [
        ship.name,
        ship.cruise_line,
        ship.capacity,
        ship.length_meters,
        ship.width_meters,
        ship.year_built,
        ship.amenities
      ]);
    }
    
    // Create sample cruise schedules
    console.log('📅 Creating sample cruise schedules...');
    
    const schedules = [
      {
        ship_id: 1,
        port_id: 1,
        arrival_datetime: '2024-02-15 08:00:00',
        departure_datetime: '2024-02-15 18:00:00',
        estimated_passengers: 3000,
        dock_number: 'A1',
        tender_required: false,
        status: 'scheduled'
      },
      {
        ship_id: 2,
        port_id: 2,
        arrival_datetime: '2024-02-16 07:00:00',
        departure_datetime: '2024-02-16 19:00:00',
        estimated_passengers: 2500,
        dock_number: 'B2',
        tender_required: false,
        status: 'scheduled'
      },
      {
        ship_id: 3,
        port_id: 3,
        arrival_datetime: '2024-02-17 09:00:00',
        departure_datetime: '2024-02-17 17:00:00',
        estimated_passengers: 4000,
        dock_number: 'C1',
        tender_required: true,
        status: 'scheduled'
      }
    ];
    
    for (const schedule of schedules) {
      await connection.execute(`
        INSERT INTO cruise_schedules (ship_id, port_id, arrival_datetime, departure_datetime, estimated_passengers, dock_number, tender_required, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `, [
        schedule.ship_id,
        schedule.port_id,
        schedule.arrival_datetime,
        schedule.departure_datetime,
        schedule.estimated_passengers,
        schedule.dock_number,
        schedule.tender_required,
        schedule.status
      ]);
    }
    
    console.log('✅ Database seeding completed successfully');
    
  } catch (error) {
    console.error('❌ Database seeding failed:', error);
    process.exit(1);
  } finally {
    if (connection) {
      await connection.end();
    }
  }
}

async function reset() {
  let connection;
  
  try {
    console.log('🔄 Resetting database...');
    
    // Create connection without database
    const configWithoutDb = { ...dbConfig };
    delete configWithoutDb.database;
    connection = await mysql.createConnection(configWithoutDb);
    
    // Drop and recreate database
    await connection.execute(`DROP DATABASE IF EXISTS ${dbConfig.database}`);
    await connection.execute(`CREATE DATABASE ${dbConfig.database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
    
    await connection.end();
    
    // Run migration and seeding
    await migrate();
    await seed();
    
    console.log('✅ Database reset completed successfully');
    
  } catch (error) {
    console.error('❌ Database reset failed:', error);
    process.exit(1);
  }
}

// Command line interface
const command = process.argv[2];

switch (command) {
  case 'migrate':
    migrate();
    break;
  case 'seed':
    seed();
    break;
  case 'reset':
    reset();
    break;
  default:
    console.log('Usage: node scripts/migrate.js [migrate|seed|reset]');
    process.exit(1);
}
