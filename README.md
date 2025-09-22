# University Room Booking System - Backend

Spring Boot backend for a university classroom booking system. This backend provides REST APIs for room management, user authentication, and booking operations.

## Features

- ✅ **Complete Database Integration**: Full MySQL database integration with JPA/Hibernate
- ✅ **RESTful APIs**: Comprehensive REST endpoints for rooms, bookings, and user management
- ✅ **JWT Authentication**: Secure user authentication and authorization system
- ✅ **Role-Based Access Control**: Different access levels for admins and regular users
- ✅ **CORS Configuration**: Properly configured for frontend integration
- ✅ **Advanced Logging**: Comprehensive logging system with configurable levels
- ✅ **MySQL Database**: Production-ready database configuration
- ✅ **Spring Security**: Robust security implementation

## Project Structure

```
src/
├── main/
│   ├── java/com/prenotazioni/
│   │   ├── PrenotazioniAuleBackendApplication.java  # Main application class
│   │   ├── config/                                  # Configuration classes
│   │   │   ├── CorsConfig.java                     # CORS configuration
│   │   │   ├── JwtAuthFilter.java                  # JWT authentication filter
│   │   │   └── SecurityConfig.java                 # Spring Security configuration
│   │   ├── controller/                             # REST Controllers
│   │   │   ├── admin/                              # Admin-specific endpoints
│   │   │   ├── auth/                               # Authentication endpoints
│   │   │   ├── MeController.java                   # User profile endpoints
│   │   │   ├── PrenotazioneController.java         # Booking management
│   │   │   └── RoomController.java                 # Room management
│   │   ├── dto/                                    # Data Transfer Objects
│   │   ├── model/                                  # JPA Entity classes
│   │   ├── repository/                             # Data access layer
│   │   └── service/                                # Business logic layer
│   └── resources/
│       ├── application.properties                   # Application configuration
│       └── logback-spring.xml                      # Logging configuration
└── target/                                         # Compiled classes and build artifacts
```

## Installation and Setup

### Prerequisites
- Java 17 or higher
- Maven 3.6 or higher
- MySQL 8.0 or higher

### Database Setup
1. Install and start MySQL server
2. Create a database named `prenotazioni_aule`:
   ```sql
   CREATE DATABASE prenotazioni_aule;
   ```
3. Update database credentials in `application.properties` if needed

### Running the Application

```bash
# Clone the repository
git clone <repository-url>

# Navigate to backend directory
cd backend

# Install dependencies and run
mvn spring-boot:run
```

The backend will be available at `http://localhost:8080`

### Alternative: Run with Maven Wrapper
```bash
./mvnw spring-boot:run  # Linux/macOS
mvnw.cmd spring-boot:run  # Windows
```

## API Endpoints

The backend exposes the following main API groups:

### 🏢 Room Management
- `GET /api/rooms` - Get all rooms (basic info)
- `GET /api/rooms/detailed` - **Main endpoint**: Get all rooms with complete details and bookings
- `GET /api/rooms/{id}` - Get specific room details
- `GET /api/rooms/{id}/details` - Get detailed room information with bookings

### 📅 Booking Management  
- `GET /api/prenotazioni` - Get all bookings
- `POST /api/prenotazioni` - Create new booking
- `PUT /api/prenotazioni/{id}` - Update booking
- `DELETE /api/prenotazioni/{id}` - Delete booking

### 👤 User Management
- `GET /api/me` - Get current user profile
- `POST /api/auth/login` - User authentication
- `POST /api/auth/register` - User registration

### 🔧 Admin Endpoints
- `GET /api/admin/users` - Manage users (admin only)
- `POST /api/admin/rooms` - Create/modify rooms (admin only)
- Admin-specific booking management

## Technologies

- **Framework**: Spring Boot 3.2.5
- **Language**: Java 17
- **Database**: MySQL 8.0
- **ORM**: Spring Data JPA / Hibernate
- **Security**: Spring Security + JWT
- **Build Tool**: Maven
- **Logging**: Logback with custom configuration

## Dependencies

### Core Dependencies
- `spring-boot-starter-web` - Web layer and REST APIs
- `spring-boot-starter-data-jpa` - Database access and JPA
- `spring-boot-starter-security` - Security framework
- `mysql-connector-java` - MySQL database driver

### Security & Authentication
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` - JWT token handling

### Development & Utilities
- `spring-boot-devtools` - Development tools
- `lombok` - Code generation and boilerplate reduction
- `spring-boot-starter-logging` - Logging framework

## Configuration

### Database Configuration
The application connects to MySQL with the following default settings:
- **URL**: `jdbc:mysql://localhost:3306/prenotazioni_aule`
- **Username**: `root`
- **Password**: `root`
- **Auto-create tables**: Enabled (`hibernate.ddl-auto=update`)

### CORS Configuration
CORS is configured to allow requests from the frontend:
- **Allowed Origins**: `http://localhost:5173` (Vite dev server)

### Logging Configuration
Advanced logging setup with multiple levels:
- **SQL Queries**: Visible in DEBUG mode
- **Security Events**: Configurable logging
- **Transaction Logging**: Enabled for debugging
- **Custom Log Patterns**: Available

## Development Features

### 🔍 Advanced Logging
- SQL query logging with parameter binding
- Spring Security event logging (configurable)
- Transaction-level logging
- Custom log patterns and file outputs

### 🚀 Hot Reload
- Spring Boot DevTools enabled for automatic application restart
- Automatic detection of classpath changes

### 🛡️ Security Features
- JWT-based authentication
- Role-based authorization (USER, ADMIN)
- CORS configuration for frontend integration
- Password encryption and validation

## Frontend Integration

This backend is designed to work seamlessly with the React frontend:

- **Main Integration Endpoint**: `/api/rooms/detailed` provides complete room data with bookings
- **CORS Enabled**: Configured for `http://localhost:5173` (Vite dev server)
- **JSON APIs**: All endpoints return JSON data suitable for React consumption
- **Error Handling**: Proper HTTP status codes and error messages

## Project Status

The backend is **production-ready** with the following implemented features:

✅ **Database Integration:**
- Complete MySQL integration
- JPA entity relationships
- Automatic table creation and updates

✅ **Security Implementation:**
- JWT authentication system
- Role-based access control
- Secure password handling

✅ **API Completeness:**
- Full CRUD operations for all entities
- Admin-specific endpoints
- User profile management

✅ **Production Features:**
- Comprehensive logging
- Error handling and validation
- CORS configuration
- Database connection pooling

## Getting Started

1. **Set up MySQL database** and create the `prenotazioni_aule` database
2. **Update credentials** in `application.properties` if needed
3. **Run the backend** with `mvn spring-boot:run`
4. **Verify connection** by accessing `http://localhost:8080/api/rooms`
5. **Start the frontend** to begin using the full application

The backend will automatically create the necessary database tables on first run and will be ready to serve the frontend application.
