# PowerShell script to start required services for the application
# Usage: .\scripts\start-services.ps1

Write-Host "🚀 Starting required services for Social Web Recommender..." -ForegroundColor Cyan

# Check if docker-compose is available
if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Host "❌ docker-compose is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

# Start Redis and RabbitMQ
Write-Host "`n📦 Starting Redis and RabbitMQ..." -ForegroundColor Yellow
docker-compose up -d redis rabbitmq

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to start services" -ForegroundColor Red
    exit 1
}

# Wait for services to be healthy
Write-Host "`n⏳ Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Check Redis
Write-Host "`n🔍 Checking Redis..." -ForegroundColor Yellow
$redisCheck = docker exec cruise_recommender_redis redis-cli ping 2>$null
if ($redisCheck -eq "PONG") {
    Write-Host "✅ Redis is ready" -ForegroundColor Green
} else {
    Write-Host "⚠️  Redis may not be ready yet" -ForegroundColor Yellow
}

# Check RabbitMQ
Write-Host "`n🔍 Checking RabbitMQ..." -ForegroundColor Yellow
$rabbitmqCheck = docker exec cruise_recommender_rabbitmq rabbitmq-diagnostics -q ping 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ RabbitMQ is ready" -ForegroundColor Green
    
    # Purge messages from queues
    Write-Host "`n🧹 Purging RabbitMQ queues..." -ForegroundColor Yellow
    docker exec cruise_recommender_rabbitmq rabbitmqctl purge_queue ais.data.queue 2>$null
    docker exec cruise_recommender_rabbitmq rabbitmqctl purge_queue social.media.queue 2>$null
    docker exec cruise_recommender_rabbitmq rabbitmqctl purge_queue knowledge.graph.queue 2>$null
    Write-Host "✅ RabbitMQ queues purged" -ForegroundColor Green
} else {
    Write-Host "⚠️  RabbitMQ may not be ready yet" -ForegroundColor Yellow
}

Write-Host "`n✨ Services started! You can now start the application." -ForegroundColor Green
Write-Host "   Redis: localhost:6379" -ForegroundColor Gray
Write-Host "   RabbitMQ: localhost:5672 (Management UI: http://localhost:15672)" -ForegroundColor Gray

