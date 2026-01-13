# Nimbus Local One

Docker Compose setup with **Nginx Reverse Proxy** routing all services through a single port (8080).

## Architecture

```
                    ┌─────────────────┐
                    │  Nginx Proxy    │
                    │   Port 8080     │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
   ┌────▼────┐         ┌────▼────┐         ┌────▼────┐
   │ Backend │         │Frontend │         │  Infra  │
   │Services │         │Services │         │Services │
   └─────────┘         └─────────┘         └─────────┘
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

### Special Routes
- **/health** → Nginx health check

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

3. Access the application:
```bash
open http://localhost:8080
```

## Access URLs

### All through Port 8080

| Service | URL | Description |
|---------|-----|-------------|
| Root | http://localhost:8080/ | Redirects to viewer |
| Viewer | http://localhost:8080/viewer/ | Game viewer |
| Editor | http://localhost:8080/editor/ | Game editor |
| Controls | http://localhost:8080/controls/ | Control panels |
| Player API | http://localhost:8080/player/ | Player service API |
| Control API | http://localhost:8080/control/ | Control service API |
| Life API | http://localhost:8080/life/ | Life service API |
| Generator API | http://localhost:8080/generator/ | Generator service API |
| Health | http://localhost:8080/health | Nginx health check |

### Health Checks (Backend)
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

# View logs
docker-compose logs -f

# View nginx logs specifically
docker-compose logs -f nginx
```

### Stop services
```bash
# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### Restart services
```bash
# Restart all
docker-compose restart

# Restart nginx (after config changes)
docker-compose restart nginx

# Restart specific backend service
docker-compose restart world-player
```

### Check status
```bash
docker-compose ps
```

## Configuration

### Nginx Configuration

The `nginx.conf` file defines all routing rules. To modify:

1. Edit `nginx.conf`
2. Restart nginx: `docker-compose restart nginx`

### Service Configuration

Edit `.env` file to customize:
- Database credentials
- Service URLs
- Encryption passwords
- Compression settings

## Advantages of This Setup

✅ **Single Port** - Only expose 8080, simplify firewall rules
✅ **Unified Access** - All services through one domain
✅ **Easy SSL** - Add SSL certificate to nginx only
✅ **Load Balancing** - Nginx can load balance if you scale services
✅ **Monitoring** - Central point for logging and metrics
✅ **CORS** - No cross-origin issues

## Disadvantages

❌ **Single Point of Failure** - If nginx fails, everything is down
❌ **Additional Hop** - Slight latency from extra proxy layer
❌ **More Complex** - Requires nginx configuration knowledge

## Troubleshooting

### Check nginx configuration
```bash
docker-compose exec nginx nginx -t
```

### View nginx logs
```bash
docker-compose logs nginx
```

### Test routing
```bash
# Test viewer
curl http://localhost:8080/viewer/

# Test API
curl http://localhost:8080/player/actuator/health
```

### Restart nginx after config change
```bash
docker-compose restart nginx
```

### Check if backend services are accessible
```bash
# From inside nginx container
docker-compose exec nginx wget -O- http://world-player:9042/actuator/health
```

## Development

For development, you might want to:

1. **Access services directly** (bypass nginx):
   ```bash
   # Temporarily expose ports
   docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
   ```

2. **Live reload nginx config**:
   ```bash
   docker-compose exec nginx nginx -s reload
   ```

3. **View real-time access logs**:
   ```bash
   docker-compose exec nginx tail -f /var/log/nginx/access.log
   ```

## Production Considerations

For production deployment:

- ✅ Enable SSL/TLS in nginx
- ✅ Set proper client_max_body_size
- ✅ Configure rate limiting
- ✅ Add request/response logging
- ✅ Implement health check monitoring
- ✅ Set up log rotation
- ✅ Configure nginx caching
- ✅ Add security headers
- ✅ Use proper secrets management

## Network

All services communicate through the `nimbus-network` bridge network. Services are not exposed to the host except through the nginx proxy on port 8080.

## Volumes

Persistent data is stored in Docker volumes:
- `mongodb-data` - MongoDB data
- `mongodb-config` - MongoDB configuration
- `redis-data` - Redis data

## Comparison with local-services

| Feature | local-services | local-one |
|---------|----------------|-----------|
| Ports exposed | 9 ports (3000-3002, 9042-9045, 27017, 6379) | 1 port (8080) |
| URL structure | http://localhost:PORT | http://localhost:8080/PATH |
| CORS issues | Possible | None |
| SSL setup | Per service | Single point |
| Complexity | Simpler | More complex |
| Use case | Development | Production-like |
