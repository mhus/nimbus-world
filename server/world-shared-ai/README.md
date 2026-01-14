# world-shared-ai

AI-specific shared functionality for nimbus-world.

## Overview

This module provides AI-related shared components that can be used across different world-* modules. It extends `world-shared` with AI-specific features like agent management, tool execution, and model integration.

## Package Structure

- **agent**: AI agent base classes and interfaces
  - Base agent interfaces and abstract classes
  - Agent lifecycle management
  - Agent registration and discovery
  - Agent communication protocols

- **tool**: Tool definitions and execution interfaces for AI agents
  - Tool interface definitions
  - Tool parameter schemas
  - Tool execution context
  - Tool result types
  - Tool discovery and registration

- **context**: Context management for AI operations
  - AI operation context builders
  - Session context management
  - Context persistence and retrieval
  - Context validation

- **model**: AI model integration and abstractions
  - AI model provider interfaces
  - Model configuration
  - Model selection and routing
  - Model response processing
  - Prompt templates and builders

## Dependencies

- **shared**: Core nimbus shared functionality
- **world-shared**: World-specific shared functionality
- **generated**: Generated types from TypeScript
- **Spring Boot**: Core framework
- **Spring Web**: REST client support

## Usage

This module is designed to be used by world-* modules that need AI functionality:

```java
// Example: Using AI tools in world-generator
@Service
public class MyAIService {
    private final AIToolRegistry toolRegistry;

    public void executeAIOperation() {
        // Use AI tools and agents
    }
}
```

## Integration

Add this dependency to your world-* module's pom.xml:

```xml
<dependency>
    <groupId>de.mhus.nimbus</groupId>
    <artifactId>world-shared-ai</artifactId>
    <version>${project.version}</version>
</dependency>
```

## Development

This module follows the same conventions as other nimbus-world modules:
- Java 25
- Spring Boot 3
- Lombok for boilerplate reduction
- JPA with MongoDB
- Apache Commons utilities
