#!/bin/bash
# Bash script to start required services for the application
# Usage: ./scripts/start-services.sh

echo "🚀 Starting required services for Social Web Recommender..."

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose is not installed or not in PATH"
    exit 1
fi

# Start Redis and RabbitMQ
echo ""
echo "📦 Starting Redis and RabbitMQ..."
docker-compose up -d redis rabbitmq

if [ $? -ne 0 ]; then
    echo "❌ Failed to start services"
    exit 1
fi

# Wait for services to be healthy
echo ""
echo "⏳ Waiting for services to be ready..."
sleep 10

# Check Redis
echo ""
echo "🔍 Checking Redis..."
if docker exec cruise_recommender_redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
    echo "✅ Redis is ready"
else
    echo "⚠️  Redis may not be ready yet"
fi

# Check RabbitMQ
echo ""
echo "🔍 Checking RabbitMQ..."
if docker exec cruise_recommender_rabbitmq rabbitmq-diagnostics -q ping 2>/dev/null; then
    echo "✅ RabbitMQ is ready"
    
    # Purge messages from queues
    echo ""
    echo "🧹 Purging RabbitMQ queues..."
    docker exec cruise_recommender_rabbitmq rabbitmqctl purge_queue ais.data.queue 2>/dev/null
    docker exec cruise_recommender_rabbitmq rabbitmqctl purge_queue social.media.queue 2>/dev/null
    docker exec cruise_recommender_rabbitmq rabbitmqctl purge_queue knowledge.graph.queue 2>/dev/null
    echo "✅ RabbitMQ queues purged"
else
    echo "⚠️  RabbitMQ may not be ready yet"
fi

echo ""
echo "✨ Services started! You can now start the application."
echo "   Redis: localhost:6379"
echo "   RabbitMQ: localhost:5672 (Management UI: http://localhost:15672)"

