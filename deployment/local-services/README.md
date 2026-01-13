# Nimbus Local Services

Docker Compose setup for running all Nimbus services locally.

## Services

### Backend Services (Java/Spring Boot)
- **world-player** - Player service (Port 9042)
- **world-control** - Control/Editor service (Port 9043)
- **world-life** - Life simulation service (Port 9044)
- **world-generator** - World generator service (Port 9045)

### Frontend Services (TypeScript/Vite)
- **viewer** - Game viewer (Port 3000)
- **editor** - Game editor (Port 3001)
- **controls** - Control panels (Port 3002)

### Infrastructure
- **mongodb** - MongoDB database (Port 27017)
- **redis** - Redis cache (Port 6379)

## Prerequisites

1. Build all Docker images first:

```bash
# Build Java services
cd ../docker-jvm
./build-all.sh

# Build TypeScript services
cd ../docker-ts
./build-all.sh
```

## Quick Start

1. Copy environment file:
```bash
cp .env.example .env
```

2. Start all services:
```bash
docker-compose up -d
```

3. Check status:
```bash
docker-compose ps
```

4. View logs:
```bash
docker-compose logs -f
```

## Access Services

### Frontend
- Viewer: http://localhost:3000
- Editor: http://localhost:3001
- Controls: http://localhost:3002

### Backend APIs
- Player API: http://localhost:9042
- Control API: http://localhost:9043
- Life API: http://localhost:9044
- Generator API: http://localhost:9045

### Backend Health Checks
- Player: http://localhost:9042/actuator/health
- Control: http://localhost:9043/actuator/health
- Life: http://localhost:9044/actuator/health
- Generator: http://localhost:9045/actuator/health

### Infrastructure
- MongoDB: localhost:27017
- Redis: localhost:6379

## Common Commands

### Start services
```bash
# Start all services
docker-compose up -d

# Start specific services
docker-compose up -d world-player viewer

# Start with logs
docker-compose up
```

### Stop services
```bash
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes data!)
docker-compose down -v
```

### View logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f world-player

# Last 100 lines
docker-compose logs --tail=100 world-player
```

### Restart services
```bash
# Restart all
docker-compose restart

# Restart specific service
docker-compose restart world-player
```

### Scale services (if needed)
```bash
# Not recommended for stateful services like world-player
docker-compose up -d --scale world-life=2
```

## Configuration

Edit `.env` file to customize:
- Database credentials
- Service URLs
- Encryption passwords
- Compression settings
- API endpoints

## Troubleshooting

### Check service health
```bash
docker-compose ps
```

### View service logs
```bash
docker-compose logs -f [service-name]
```

### Restart unhealthy service
```bash
docker-compose restart [service-name]
```

### Clean restart
```bash
docker-compose down
docker-compose up -d
```

### Reset database (WARNING: deletes all data!)
```bash
docker-compose down -v
docker-compose up -d
```

## Network

All services are connected via the `nimbus-network` bridge network, allowing them to communicate using service names (e.g., `world-player:9042`).

## Volumes

Persistent data is stored in Docker volumes:
- `mongodb-data` - MongoDB data
- `mongodb-config` - MongoDB configuration
- `redis-data` - Redis data

## Development

For development, you might want to:
1. Run some services locally (outside Docker)
2. Adjust `.env` to point to localhost services
3. Use `docker-compose up -d mongodb redis` to only run infrastructure

Example `.env` for hybrid setup:
```bash
# Run infrastructure in Docker
MONGODB_URI=mongodb://root:example@localhost:27017/world?authSource=admin
REDIS_HOST=localhost

# Services on host
PLAYER_BASE_URL=http://localhost:9042
CONTROL_BASE_URL=http://localhost:9043
```

## Production Notes

This setup is designed for local development. For production:
- Use proper secrets management
- Configure SSL/TLS
- Set up proper monitoring
- Use orchestration (Kubernetes, etc.)
- Configure backups
- Review security settings
