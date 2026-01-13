# Network Protocol - Nimbus Voxel Engine 2.0

WebSocket-based network protocol with JSON-serialized messages.

## Message Structure

All messages follow the base structure:

```typescript
{
  i?: string;  // Message ID (for request/response)
  r?: string;  // Response ID (references request)
  t: string;   // Message type
  d?: any;     // Data payload
}
```

## Message Types

### Authentication (Client ↔ Server)
- **login** - Login request with credentials
- **loginResponse** - Login response with world info
- **logout** - Logout request

### Connection (Client ↔ Server)
- **p** (ping/pong) - Connection keepalive and latency measurement

### World (Server → Client)
- **w.su** - World status update (triggers chunk re-render)

### Chunks (Client ↔ Server)
- **c.r** - Chunk registration (client specifies desired chunks)
- **c.q** - Chunk query (client requests specific chunks)
- **c.u** - Chunk update (server sends chunk data)

### Blocks (Server → Client)
- **b.u** - Block update (server → client)
- **b.s.u** - Block status update with optional animations

### Entities (Server → Client)
- **e.u** - Entity update

### Animation (Server → Client)
- **a.s** - Animation start instruction

### User/Player (Client ↔ Server)
- **u.m** - User movement update (client → server)
- **p.t** - Player teleport (server → client)

### Interaction (Client ↔ Server)
- **int.r** - Interaction request (client → server)
- **int.rs** - Interaction response (server → client)

## Naming Conventions

### Abbreviations (Objects)
- `c` - chunk
- `b` - block
- `a` - animation
- `e` - effect / entity
- `s` - status
- `i` - id
- `t` - type
- `ts` - timestamp
- `rq` / `r` - request
- `rs` - response

### Abbreviations (Verbs)
- `c` - close
- `o` - open
- `u` - update
- `q` - query
- `r` - register / request
- `se` - select

## Message Flow Examples

### Login Flow
```
Client → Server: {"i";"1","t";"login","d":{...}}
Server → Client: {"r";"1","t";"loginResponse","d":{...}}
```

### Ping/Pong
```
Client → Server: {"i";"ping123","t";"p"}
Server → Client: {"r";"ping123","t";"p"}
```

### Chunk Loading
```
Client → Server: {"t";"c.r","d":{"c":[{"x":0,"z":0}]}}
Server → Client: {"t";"c.u","d":[{...chunkData...}]}
```

## Connection Management

### Ping Interval
- Client sends ping every `pingInterval` seconds (from world settings)
- Server responds with pong (same message ID)
- **Timeout**: `lastPingAt + pingInterval*1000 + 10000ms`
- Connection terminated if timeout exceeded

### Session Management
- Login creates/resumes session
- Session ID returned in login response
- Logout terminates session
- Connection loss keeps session alive temporarily

## Data Structures

### Chunk Columns
- Chunks are always columns (X, Z coordinates)
- Y-direction is complete (full height)
- ChunkSize from world settings (typically 16 or 32)

### Height Data
Array of 4 integers per chunk position:
```typescript
[maxHeight, minHeight, groundLevel, waterHeight]
```

### Coordinates
- Position: `{x, y, z}` (world coordinates)
- Rotation: `{y, p}` (yaw, pitch)
- Chunk: `{x, z}` (chunk coordinates)

## Message Files

**Base:**
- `BaseMessage.ts` - Base message structure  
- `MessageTypes.ts` - Message type enums + test enums for ts-to-java generator

**Test Enums in MessageTypes.ts:**
- `Priority` (numeric values) - Tests int tsIndex generation
- `MixedEnum` (mixed types) - Tests String fallback generation

**Messages:**
- `LoginMessage.ts` - Authentication messages
- `LogoutMessage.ts` - Logout message
- `PingMessage.ts` - Ping/pong keepalive
- `WorldMessage.ts` - World status updates
- `ChunkMessage.ts` - Chunk registration and updates
- `BlockMessage.ts` - Block updates and status changes
- `EntityMessage.ts` - Entity updates
- `AnimationMessage.ts` - Animation execution
- `UserMessage.ts` - User movement and teleport
- `InteractionMessage.ts` - Block interaction
- `CommandMessage.ts` - Client commands
- `ServerCommandMessage.ts` - Server commands

## Usage

```typescript
import {
  MessageType,
  LoginMessage,
  ChunkUpdateMessage,
  BlockUpdateMessage
} from '@nimbus/shared';

// Create login message
const loginMsg: LoginMessage = {
  i: '1',
  t: MessageType.LOGIN,
  d: {
    username: 'player',
    password: 'secret',
    worldId: 'world123',
    clientType: ClientType.WEB
  }
};

// Send via WebSocket
ws.send(JSON.stringify(loginMsg));
```

## Network Optimization

- **Short field names** reduce JSON size (e.g., `t` instead of `type`)
- **Optional fields** minimize data transmission
- **Chunk registration** prevents unnecessary data sending
- **Status updates** separate from full block updates
- **Abbreviated message types** (e.g., `p` instead of `ping`)

## See Also

- `network-model-2.0.md` - Complete protocol specification
- `object-model-2.0.md` - Data model specification
