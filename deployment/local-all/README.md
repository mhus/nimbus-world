# Nimbus Local All

Complete Docker Compose setup with **Nginx Reverse Proxy** + **Admin Tools** routing everything through a single port (8080).

## What's Included

This setup combines:
- All services from `local-one` (Nimbus app + infrastructure)
- Admin tools from `local` (mongo-express, redis-commander)

All accessible through **Port 8080** with Nginx routing.

## Architecture

```
                    ┌─────────────────┐
                    │  Nginx Proxy    │
                    │   Port 8080     │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┬──────────────┐
        │                    │                    │              │
   ┌────▼────┐         ┌────▼────┐         ┌────▼────┐   ┌────▼────┐
   │ Backend │         │Frontend │         │  Infra  │   │  Admin  │
   │Services │         │Services │         │Services │   │  Tools  │
   └─────────┘         └─────────┘         └─────────┘   └─────────┘
```

## Routes

All services accessible through **http://localhost:8080**:

### Frontend Routes
- **/** → Redirects to `/viewer/`
- **/viewer/** → Game viewer
- **/editor/** → Game editor
- **/controls/** → Control panels

### Backend API Routes
- **/player/** → World Player API (9042)
- **/control/** → World Control API (9043)
- **/life/** → World Life API (9044)
- **/generator/** → World Generator API (9045)

### Admin Tool Routes
- **/mongo/** → Mongo Express (MongoDB admin UI)
- **/redis/** → Redis Commander (Redis admin UI)

### Special Routes
- **/health** → Nginx health check

## Prerequisites

1. Build all Docker images:

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

3. Access the application:
```bash
open http://localhost:8080
```

## Access URLs

### All through Port 8080

| Service | URL | Description |
|---------|-----|-------------|
| Root | http://localhost:8080/ | Redirects to viewer |
| **Frontend** | | |
| Viewer | http://localhost:8080/viewer/ | Game viewer |
| Editor | http://localhost:8080/editor/ | Game editor |
| Controls | http://localhost:8080/controls/ | Control panels |
| **Backend APIs** | | |
| Player API | http://localhost:8080/player/ | Player service API |
| Control API | http://localhost:8080/control/ | Control service API |
| Life API | http://localhost:8080/life/ | Life service API |
| Generator API | http://localhost:8080/generator/ | Generator service API |
| **Admin Tools** | | |
| Mongo Express | http://localhost:8080/mongo/ | MongoDB admin UI |
| Redis Commander | http://localhost:8080/redis/ | Redis admin UI |
| **System** | | |
| Health | http://localhost:8080/health | Nginx health check |

### Backend Health Checks
- Player: http://localhost:8080/player/actuator/health
- Control: http://localhost:8080/control/actuator/health
- Life: http://localhost:8080/life/actuator/health
- Generator: http://localhost:8080/generator/actuator/health

## Common Commands

### Start services
```bash
# Start all services
docker-compose up -d

# Start with logs
docker-compose up

# Start specific services
docker-compose up -d nginx world-player viewer mongodb redis
```

### Stop services
```bash
# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### View logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f world-player

# Nginx logs
docker-compose logs -f nginx
```

### Restart services
```bash
# Restart all
docker-compose restart

# Restart nginx
docker-compose restart nginx
```

### Check status
```bash
docker-compose ps
```

## Services Overview

### Backend Services (4)
- **world-player** (internal:9042) - Player service
- **world-control** (internal:9043) - Control/Editor service
- **world-life** (internal:9044) - Life simulation
- **world-generator** (internal:9045) - World generator

### Frontend Services (3)
- **viewer** (internal:3000) - Game viewer
- **editor** (internal:3001) - Game editor
- **controls** (internal:3002) - Control panels

### Infrastructure (2)
- **mongodb** (internal:27017) - MongoDB database
- **redis** (internal:6379) - Redis cache

### Admin Tools (2)
- **mongo-express** (internal:8081) - MongoDB admin UI
- **redis-commander** (internal:8081) - Redis admin UI

### Proxy (1)
- **nginx** (external:8080) - Reverse proxy

**Total: 12 containers**

## Advantages

✅ **Single Port** - Only expose 8080
✅ **Admin Tools Included** - Database management through browser
✅ **No CORS Issues** - Everything under one domain
✅ **Production-like** - Realistic deployment setup
✅ **Easy Monitoring** - All logs through nginx
✅ **SSL-Ready** - Single certificate point

## Configuration

Edit `.env` file to customize:
- Database credentials
- Service URLs
- Encryption passwords
- Compression settings

## Troubleshooting

### Check nginx configuration
```bash
docker-compose exec nginx nginx -t
```

### View nginx access logs
```bash
docker-compose exec nginx tail -f /var/log/nginx/access.log
```

### Test routing
```bash
# Test frontend
curl http://localhost:8080/viewer/

# Test backend API
curl http://localhost:8080/player/actuator/health

# Test admin tools
curl http://localhost:8080/mongo/
curl http://localhost:8080/redis/
```

### Restart nginx after config change
```bash
docker-compose restart nginx
```

### Check if services are reachable from nginx
```bash
docker-compose exec nginx wget -O- http://world-player:9042/actuator/health
docker-compose exec nginx wget -O- http://mongo-express:8081/
```

## Comparison with Other Setups

| Feature | local-services | local-one | local-all |
|---------|----------------|-----------|-----------|
| Ports exposed | 9 ports | 1 port (8080) | 1 port (8080) |
| Admin tools | ❌ No | ❌ No | ✅ Yes |
| URL structure | localhost:PORT | localhost:8080/PATH | localhost:8080/PATH |
| CORS issues | Possible | None | None |
| Complexity | Simple | Medium | Complex |
| Use case | Dev (individual) | Dev (unified) | Dev (full stack) |

## Network

All services communicate through the `nimbus-network` bridge network. Only nginx is exposed to the host on port 8080.

## Volumes

Persistent data in Docker volumes:
- `mongodb-data` - MongoDB data
- `mongodb-config` - MongoDB configuration
- `redis-data` - Redis data

## Development Tips

### Selective startup
```bash
# Start only infrastructure + one backend service
docker-compose up -d mongodb redis world-player viewer nginx

# Add more services as needed
docker-compose up -d world-control controls
```

### Live reload nginx config
```bash
# After editing nginx.conf
docker-compose exec nginx nginx -s reload
```

### Access databases directly
Even though ports aren't exposed, you can:
```bash
# MongoDB
docker-compose exec mongodb mongosh -u root -p example

# Redis
docker-compose exec redis redis-cli
```

Or use the admin tools through the browser!
