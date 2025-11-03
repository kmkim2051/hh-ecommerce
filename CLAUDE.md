# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot e-commerce application built with:
- **Java 17** (required)
- **Spring Boot 3.5.7** (Spring Web MVC)
- **Gradle** as the build tool
- **Lombok** for reducing boilerplate code

Project package: `com.hh.ecom`

## Essential Commands

### Building and Running
```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Clean build artifacts
./gradlew clean
```

### Testing
```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests com.hh.ecom.EcomApplicationTests

# Run tests with pattern matching
./gradlew test --tests '*ControllerTest'

# Run tests in continuous mode
./gradlew test --continuous
```

### Development
```bash
# Check for dependency updates
./gradlew dependencyUpdates

# Generate IDE files (if needed)
./gradlew idea
```

## Architecture

### Current Structure
The project follows standard Spring Boot conventions:
- `src/main/java/com/hh/ecom/` - Main application code
- `src/main/resources/` - Configuration files and static resources
- `src/test/java/com/hh/ecom/` - Test code

### Expected Architecture Pattern
As an e-commerce application, expect to develop these layers:
- **Controller layer** - REST endpoints (use `@RestController` or `@Controller`)
- **Service layer** - Business logic (use `@Service`)
- **Repository layer** - Data access (use `@Repository`)
- **Domain/Entity layer** - Data models
- **DTO layer** - Data transfer objects for API contracts

### Lombok Usage
The project uses Lombok to reduce boilerplate. Common annotations:
- `@Data` - Generates getters, setters, toString, equals, hashCode
- `@Builder` - Implements builder pattern
- `@NoArgsConstructor` / `@AllArgsConstructor` - Constructor generation
- `@Slf4j` - Logger field generation

## Configuration

Application configuration is in `src/main/resources/application.properties`.

Common properties to add as the project grows:
- Server port: `server.port=8080`
- Database configuration (when added)
- Logging levels: `logging.level.com.hh.ecom=DEBUG`
