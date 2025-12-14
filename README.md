# Database User Access Management

A Spring Boot application for managing user permissions with time-based access control. This application allows you to grant and revoke database access permissions to users for specified time intervals.

## Features

- User authentication and authorization with JWT
- Time-based permission management
- Automatic permission expiration and cleanup
- RESTful API for permission management
- Scheduled tasks for monitoring expired permissions
- Support for multiple database backends (H2, PostgreSQL, MySQL)
- API documentation with Swagger/OpenAPI
- Configurable grace periods for permission revocation

## Tech Stack

- **Java 17**
- **Spring Boot 3.2.1**
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Database access
- **Quartz Scheduler** - Scheduled tasks
- **JWT** - Token-based authentication
- **H2/PostgreSQL/MySQL** - Database support
- **Lombok** - Reduce boilerplate code
- **SpringDoc OpenAPI** - API documentation
- **Gradle 8.5** - Build tool

## Prerequisites

- Java 17 or higher
- Gradle 8.5 or higher (or use the included Gradle wrapper)

## Getting Started

### Clone the repository

```bash
git clone <repository-url>
cd Db-user-access-management-
```

### Build the project

```bash
./gradlew build
```

### Run the application

#### Development mode (with H2 in-memory database)

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

#### Default mode

```bash
./gradlew bootRun
```

#### Production mode

```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### Access the application

- **Application API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/api/swagger-ui.html
- **H2 Console** (dev mode): http://localhost:8080/api/h2-console

## Configuration

### Application Profiles

The application supports multiple profiles:

- `dev` - Development mode with H2 in-memory database
- `prod` - Production mode with PostgreSQL/MySQL

### Main Configuration (`application.yml`)

Key configuration properties:

```yaml
# Server Configuration
server:
  port: 8080
  servlet:
    context-path: /api

# JWT Configuration
jwt:
  secret: your-secret-key
  expiration: 86400000  # 24 hours

# Permission Management
app:
  permission:
    check-interval: 300000      # Check every 5 minutes
    cleanup-expired: true
    grace-period: 60000         # 1 minute grace period
```

### Production Configuration

For production deployment, set the following environment variables:

```bash
export DB_USERNAME=your_db_username
export DB_PASSWORD=your_db_password
export JWT_SECRET=your_secure_jwt_secret
export SERVER_PORT=8080
```

## Database Configuration

### H2 (Development)

Default configuration uses H2 in-memory database. No additional setup required.

### PostgreSQL (Production)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/userdb
    driver-class-name: org.postgresql.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

### MySQL (Production)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/userdb
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

## Project Structure

```
db-user-access-management/
├── src/
│   ├── main/
│   │   ├── java/com/userpermission/management/
│   │   │   ├── UserAccessManagementApplication.java
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java
│   │   │   │   └── JwtConfig.java
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── model/
│   │   │   └── security/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   └── test/
├── build.gradle
├── settings.gradle
└── README.md
```

## Building for Production

### Create executable JAR

```bash
./gradlew clean build
```

The executable JAR will be created in `build/libs/db-user-access-management-1.0.0.jar`

### Run the JAR

```bash
java -jar build/libs/db-user-access-management-1.0.0.jar --spring.profiles.active=prod
```

## Development

### Running tests

```bash
./gradlew test
```

### Code formatting

The project uses Lombok to reduce boilerplate code. Ensure your IDE has Lombok plugin installed.

## API Documentation

Once the application is running, access the interactive API documentation at:

- Swagger UI: http://localhost:8080/api/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api/v3/api-docs

## License

This project is licensed under the terms specified in the LICENSE file.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request
