# Runtime Configuration

Die Nimbus Controls unterstützen Runtime-Konfiguration, sodass Umgebungsvariablen (wie API-URLs) nach dem Build geändert werden können, ohne neu kompilieren zu müssen.

## Funktionsweise

### Entwicklung (lokal)

In der Entwicklung wird die API-URL aus `.env` geladen:

```env
VITE_CONTROL_API_URL=http://localhost:9043
```

Zusätzlich lädt die App beim Start `public/config.json`, das die `.env` Werte überschreiben kann.

### Production (Docker)

In Production generiert das Docker Entrypoint Script zur Laufzeit eine `config.json` aus Umgebungsvariablen.

## App-Integration

### Schritt 1: Initialisierung hinzufügen

In jeder `main.ts` Datei muss `initializeApp()` vor dem Mount aufgerufen werden:

```typescript
// Vorher:
import { createApp } from 'vue';
import LayerApp from './LayerApp.vue';
import '../style.css';

const app = createApp(LayerApp);
app.mount('#app');

// Nachher:
import { createApp } from 'vue';
import LayerApp from './LayerApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(LayerApp);
  app.mount('#app');
});
```

### Schritt 2: API Service verwenden

Der `apiService` lädt automatisch die Runtime Config. Keine Änderungen nötig in bestehenden Services!

```typescript
import { apiService } from '@/services/ApiService';

// API calls verwenden automatisch die geladene Config
const data = await apiService.get('/some/endpoint');
```

## Docker Usage

### Dockerfile

```dockerfile
FROM nginx:alpine

COPY dist/ /usr/share/nginx/html/
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

ENV VITE_CONTROL_API_URL=http://localhost:9043

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
```

### Docker Run

```bash
# Mit Standard-URL (aus ENV im Dockerfile)
docker run -p 8080:80 nimbus-controls

# Mit custom API URL
docker run -p 8080:80 \
  -e VITE_CONTROL_API_URL=http://production-api.example.com:9043 \
  nimbus-controls
```

### Docker Compose

```yaml
version: '3.8'
services:
  nimbus-controls:
    image: nimbus-controls
    ports:
      - "8080:80"
    environment:
      - VITE_CONTROL_API_URL=http://api-server:9043
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nimbus-controls
spec:
  template:
    spec:
      containers:
      - name: nimbus-controls
        image: nimbus-controls:latest
        env:
        - name: VITE_CONTROL_API_URL
          value: "http://api-service:9043"
        ports:
        - containerPort: 80
```

## Entrypoint Script

Das `docker-entrypoint.sh` Script:

1. Liest die `VITE_CONTROL_API_URL` Umgebungsvariable
2. Generiert `/usr/share/nginx/html/config.json` mit dem Wert
3. Startet nginx

```bash
#!/bin/sh
set -e

CONFIG_FILE="/usr/share/nginx/html/config.json"
API_URL="${VITE_CONTROL_API_URL:-http://localhost:9043}"

cat > "$CONFIG_FILE" <<EOF
{
  "apiUrl": "$API_URL"
}
EOF

exec "$@"
```

## Dateien

- `public/config.json` - Default Config für Entwicklung
- `src/services/ConfigService.ts` - Lädt config.json zur Laufzeit
- `src/utils/initApp.ts` - App-Initialisierung Helper
- `docker-entrypoint.sh` - Docker Entrypoint für Runtime Config
- `Dockerfile.example` - Beispiel Dockerfile

## Vorteile

✅ **Keine Rebuilds nötig** - Ändere die API-URL ohne neu zu kompilieren
✅ **Docker-freundlich** - Einfache Konfiguration per Environment Variables
✅ **Kubernetes-ready** - ConfigMaps und Secrets werden unterstützt
✅ **Fallback** - Falls config.json fehlt, wird .env Wert verwendet
✅ **Abwärtskompatibel** - Apps ohne initializeApp() funktionieren weiter (mit .env Werten)
