# Runtime Configuration - Nimbus Engine

Die Nimbus Engine unterstützt Runtime-Konfiguration, sodass Server-URLs nach dem Build geändert werden können, ohne neu kompilieren zu müssen.

## Wichtige Änderung: worldId

**worldId wird jetzt immer als URL-Parameter übergeben**: `?worldId=xxx`

Die worldId kommt nicht mehr aus der `.env` Datei!

## Konfigurierbare Parameter

### Runtime (config.json):
- `websocketUrl` - WebSocket Server URL (z.B. `ws://localhost:9042/ws`)
- `apiUrl` - REST API Server URL (z.B. `http://localhost:9042`)
- `exitUrl` - Logout/Exit URL (z.B. `/login`)

### URL Parameter (erforderlich):
- `worldId` - World ID (z.B. `?worldId=earth616:westview`)
- `session` - Session ID (optional, z.B. `?session=xxx`)

### .env (optional, zur Entwicklung):
- `VITE_LOG_LEVEL` - Log Level (DEBUG, INFO, WARN, ERROR)
- `VITE_LOG_TO_CONSOLE` - Console Logging (true/false)
- `VITE_SHOW_SPLASH_SCREEN` - Splash Screen anzeigen (true/false)

## Funktionsweise

### Entwicklung (lokal)

In der Entwicklung wird die Config aus zwei Quellen geladen:
1. `public/config.json` - Server URLs (zur Laufzeit)
2. `.env` - Optional settings (zur Build-Zeit)

```json
// public/config.json
{
  "websocketUrl": "ws://localhost:9042/ws",
  "apiUrl": "http://localhost:9042",
  "exitUrl": "http://localhost:3002/dev-login.html"
}
```

### Production (Docker)

In Production generiert das Docker Entrypoint Script zur Laufzeit eine `config.json` aus Umgebungsvariablen.

## URL Aufruf

Die Engine MUSS mit worldId aufgerufen werden:

```
http://localhost:3001?worldId=earth616:westview&session=abc123
```

Ohne worldId Parameter wird die Engine einen Fehler werfen!

## Docker Usage

### Dockerfile

```dockerfile
FROM nginx:alpine

COPY dist/ /usr/share/nginx/html/
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

ENV VITE_SERVER_WEBSOCKET_URL=ws://localhost:9042/ws
ENV VITE_SERVER_API_URL=http://localhost:9042
ENV VITE_EXIT_URL=/login

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
```

### Docker Run

```bash
# Mit Standard-URLs (aus ENV im Dockerfile)
docker run -p 3001:80 nimbus-engine

# Mit custom Server URLs
docker run -p 3001:80 \
  -e VITE_SERVER_WEBSOCKET_URL=ws://production-server:9042/ws \
  -e VITE_SERVER_API_URL=http://production-server:9042 \
  -e VITE_EXIT_URL=http://control-panel.example.com/dev-login.html \
  nimbus-engine
```

Dann aufrufen mit: `http://localhost:3001?worldId=earth616:westview`

### Docker Compose

```yaml
version: '3.8'
services:
  nimbus-engine:
    image: nimbus-engine
    ports:
      - "3001:80"
    environment:
      - VITE_SERVER_WEBSOCKET_URL=ws://game-server:9042/ws
      - VITE_SERVER_API_URL=http://game-server:9042
      - VITE_EXIT_URL=http://control-panel/dev-login.html
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nimbus-engine
spec:
  template:
    spec:
      containers:
      - name: nimbus-engine
        image: nimbus-engine:latest
        env:
        - name: VITE_SERVER_WEBSOCKET_URL
          value: "ws://game-server-service:9042/ws"
        - name: VITE_SERVER_API_URL
          value: "http://game-server-service:9042"
        - name: VITE_EXIT_URL
          value: "http://control-panel-service/dev-login.html"
        ports:
        - containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: nimbus-engine-service
spec:
  selector:
    app: nimbus-engine
  ports:
    - protocol: TCP
      port: 80
      targetPort: 80
```

## Entrypoint Script

Das `docker-entrypoint.sh` Script:

```bash
#!/bin/sh
set -e

CONFIG_FILE="/usr/share/nginx/html/config.json"

WEBSOCKET_URL="${VITE_SERVER_WEBSOCKET_URL:-ws://localhost:9042/ws}"
API_URL="${VITE_SERVER_API_URL:-http://localhost:9042}"
EXIT_URL="${VITE_EXIT_URL:-/login}"

cat > "$CONFIG_FILE" <<EOF
{
  "websocketUrl": "$WEBSOCKET_URL",
  "apiUrl": "$API_URL",
  "exitUrl": "$EXIT_URL"
}
EOF

exec "$@"
```

## Migration von bestehenden Setups

### Vorher (.env):
```env
VITE_SERVER_WEBSOCKET_URL=ws://localhost:9042/ws
VITE_SERVER_API_URL=http://localhost:9042
VITE_WORLD_ID=earth616:westview  # ❌ ENTFERNT!
VITE_EXIT_URL=/login
```

### Nachher:

**public/config.json** (für Entwicklung):
```json
{
  "websocketUrl": "ws://localhost:9042/ws",
  "apiUrl": "http://localhost:9042",
  "exitUrl": "/login"
}
```

**URL Aufruf**:
```
http://localhost:3001?worldId=earth616:westview
```

Die worldId wird IMMER über den URL-Parameter übergeben!

## Dateien

- `public/config.json` - Default Config für Entwicklung
- `src/config/RuntimeConfig.ts` - Lädt config.json zur Laufzeit
- `src/config/ClientConfig.ts` - Kombiniert Runtime Config + URL Parameter
- `docker-entrypoint.sh` - Docker Entrypoint für Runtime Config
- `.env` - Nur noch für optionale Entwicklungs-Settings

## Vorteile

✅ **Keine Rebuilds nötig** - Ändere Server-URLs ohne neu zu kompilieren
✅ **Docker-freundlich** - Einfache Konfiguration per Environment Variables
✅ **Kubernetes-ready** - ConfigMaps und Secrets werden unterstützt
✅ **Flexibel** - worldId kann pro Aufruf unterschiedlich sein
✅ **Fallback** - Falls config.json fehlt, werden .env Werte verwendet
