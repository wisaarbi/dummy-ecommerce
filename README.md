# Dummy Ecommerce - Microservices E-Commerce Platform

## Overview
A microservices-based marketplace platform built with Spring Boot 3.5.8 and Java 21. Implements JWT-based authentication, product catalog with Redis caching, shopping cart with MongoDB, and Redis-based rate limiting.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Client                                     │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         API Gateway (:8080)                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │ JWT Filter  │  │Rate Limiter │  │Token Blackl.│  │   Routing   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
└──────────┬──────────────┬───────────────────┬───────────────────────────┘
           │              │                   │
           ▼              ▼                   ▼
┌──────────────┐  ┌──────────────┐    ┌──────────────┐
│Member Service│  │Product Serv. │    │ Cart Service │
│   (:8081)    │  │   (:8082)    │    │   (:8083)    │
└──────┬───────┘  └──────┬───────┘    └──────┬───────┘
       │                 │                   │
       ▼                 ▼                   ▼
┌──────────────┐  ┌──────────────┐    ┌──────────────┐
│  PostgreSQL  │  │  PostgreSQL  │    │   MongoDB    │
│   (member)   │  │  (product)   │    │  (cart_db)   │
└──────────────┘  └──────────────┘    └──────────────┘
                         │
                         ▼
                  ┌──────────────┐
                  │    Redis     │
                  │ (Cache/Rate) │
                  └──────────────┘
```

### Services

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| **API Gateway** | 8080 | Redis | Entry point, JWT validation, rate limiting, request routing |
| **Member Service** | 8081 | PostgreSQL | User registration & credential validation |
| **Product Service** | 8082 | PostgreSQL + Redis | Product catalog with search & caching |
| **Cart Service** | 8083 | MongoDB | Shopping cart management |

## Tech Stack

- **Java 21** with Virtual Threads enabled
- **Spring Boot 3.5.8**
- **Spring Cloud Gateway (WebMVC)** - Request routing
- **Spring Security** - JWT authentication
- **Databases**:
  - PostgreSQL (Member & Product services)
  - MongoDB (Cart service)
  - Redis (Caching, Rate Limiting, Token Blacklist)
- **Build Tool**: Maven
- **Container**: Docker with Eclipse Temurin 21 Alpine

## Prerequisites

### Local Development
- Java 21+
- Maven 3.8+
- PostgreSQL 12+
- MongoDB 4.4+
- Redis 6+

### Docker
- Docker Engine 20.10+

## Quick Start

### Option 1: Local Development

#### 1. Setup Databases

**PostgreSQL:**
```bash
# Create databases
psql -U postgres -c "CREATE DATABASE member;"
psql -U postgres -c "CREATE DATABASE product;"

# Create users
psql -U postgres -c "CREATE USER member_user WITH PASSWORD 'member_pass';"
psql -U postgres -c "CREATE USER product_user WITH PASSWORD 'product_pass';"

# Grant privileges
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE member TO member_user;"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE product TO product_user;"
```

**MongoDB:**
MongoDB will automatically create the `cart_db` database on first connection.

**Redis:**
```bash
# Start Redis (default port 6379)
redis-server
```

#### 2. Seed Data (Optional)

```bash
# Seed 5,000 members (password: Password!123)
psql -U member_user -d member -f member/setup-db.sql

# Seed 50,000 products (requires pgcrypto extension)
psql -U product_user -d product -f product/setup-db.sql
```

#### 3. Set Environment Variable

```bash
# Linux/Mac
export SECURITY_JWT_SECRET_KEY=your-256-bit-secret-key-here-minimum-32-chars

# Windows PowerShell
$env:SECURITY_JWT_SECRET_KEY="your-256-bit-secret-key-here-minimum-32-chars"
```

#### 4. Start Services

```bash
# Terminal 1 - Member Service
cd member && mvn spring-boot:run

# Terminal 2 - Product Service
cd product && mvn spring-boot:run

# Terminal 3 - Cart Service
cd cart && mvn spring-boot:run

# Terminal 4 - API Gateway
cd api-gateway && mvn spring-boot:run
```

### Option 2: Docker

Each service has a `Dockerfile` using Eclipse Temurin 21 Alpine.

#### Build Images

```bash
# Build all services
cd api-gateway && mvn clean package -DskipTests && docker build -t api-gateway .
cd ../member && mvn clean package -DskipTests && docker build -t member-service .
cd ../product && mvn clean package -DskipTests && docker build -t product-service .
cd ../cart && mvn clean package -DskipTests && docker build -t cart-service .
```

#### Run with Docker Network

```bash
# Create network
docker network create ecommerce-net

# Run Redis
docker run -d --name redis --network ecommerce-net redis:alpine

# Run PostgreSQL
docker run -d --name postgres --network ecommerce-net \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15-alpine

# Run MongoDB
docker run -d --name mongo --network ecommerce-net mongo:6

# Run services (adjust environment variables as needed)
docker run -d --name member --network ecommerce-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/member \
  member-service

docker run -d --name product --network ecommerce-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/product \
  -e SPRING_DATA_REDIS_HOST=redis \
  product-service

docker run -d --name cart --network ecommerce-net \
  -e SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/cart_db \
  cart-service

docker run -d --name api-gateway --network ecommerce-net -p 8080:8080 \
  -e SECURITY_JWT_SECRET_KEY=your-secret-key \
  -e SPRING_DATA_REDIS_HOST=redis \
  api-gateway
```

## API Endpoints

### Authentication (via Gateway :8080)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/auth/register` | Register new user | No |
| POST | `/auth/login` | Login (returns JWT cookie + token body) | No |
| POST | `/auth/logout` | Logout (blacklists token in Redis) | No |

**Register Request:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "Password123!"
}
```

**Login Request:**
```json
{
  "email": "john@example.com",
  "password": "Password123!"
}
```

### Products (via Gateway :8080)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/products` | List/search products with pagination | No |
| GET | `/products/{id}` | Get product detail (cached in Redis) | No |

**Query Parameters for Search:**
- `query` - Search keyword (supports wildcard `*` and `?`)
- `page` - Page number (0-based)
- `size` - Page size
- `sort` - Sort field (e.g., `name,asc`)

**Examples:**
```bash
# Search products
curl "http://localhost:8080/products?query=Product*&page=0&size=10"

# Get product by ID
curl "http://localhost:8080/products/550e8400-e29b-41d4-a716-446655440000"
```

### Cart (via Gateway :8080) - Requires Authentication

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/cart` | Get current user's cart | Yes |
| POST | `/cart` | Add item to cart | Yes |
| DELETE | `/cart/{productId}` | Remove item from cart | Yes |

**Add to Cart Request:**
```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "quantity": 2
}
```

## Redis Usage

Redis is used for three main purposes:

### 1. Token Blacklist (API Gateway)
- Key pattern: `jwt:blacklist:{jwtId}`
- TTL: Remaining token expiration time
- Used during logout to invalidate JWT before expiration

### 2. Rate Limiting (API Gateway)
- Key pattern: `ratelimit:{user|anon}:{identifier}`
- Window: 1 minute (sliding window)
- Default limit: 120 requests/minute
- Anonymous users are identified via `ANON_CLIENT_ID` cookie

### 3. Product Cache (Product Service)
- Cache name: `productById`
- TTL: 2 minutes (per product detail)
- Serialization: JSON via Jackson

## Rate Limiting

Redis-based rate limiter with configuration in `application.properties`:

```properties
rate-limiter.enabled=true
rate-limiter.requests-per-minute=120
rate-limiter.ignored-paths[0]=/actuator/**
```

### Response Headers
- `X-RateLimit-Limit` - Maximum requests per window
- `X-RateLimit-Remaining` - Remaining requests
- `Retry-After` - Seconds until reset (when blocked)

### Rate Limit Exceeded Response
```json
{
  "message": "Rate limit exceeded"
}
```
HTTP Status: `429 Too Many Requests`

## Features Implemented

✅ User registration with password hashing (BCrypt)  
✅ JWT-based authentication with HttpOnly cookies  
✅ Logout with Redis-backed token blacklist  
✅ Product search with wildcard support (`*`, `?`)  
✅ Pagination for product listing  
✅ Shopping cart (add, view, remove)  
✅ Authorization via JWT validation in API Gateway  
✅ User ID propagation via `X-User-Id` header  
✅ Redis cache for product detail lookups (TTL: 2 minutes)  
✅ Redis-based rate limiting (120 req/min per user)  
✅ Virtual Threads (Java 21) enabled in all services  
✅ Docker support with Eclipse Temurin 21 Alpine  
✅ Spring Cloud Gateway (WebMVC) for request routing  
✅ Standardized API response (`BaseResponse<T>`)  

## Project Structure

```
dummy-ecommerce/
├── api-gateway/                    # API Gateway with JWT filter & rate limiter
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── client/                 # HTTP clients (MemberClient)
│       ├── config/                 # Security, Rate limiter configs
│       ├── controller/             # AuthController (login/register/logout)
│       ├── dto/                    # Request/Response DTOs
│       ├── exception/              # Global exception handler
│       └── security/               # JWT, Token blacklist, Rate limiter
│
├── member/                         # Member Service (Authentication)
│   ├── Dockerfile
│   ├── pom.xml
│   ├── setup-db.sql               # Seed 5,000 members
│   └── src/main/java/.../
│       ├── controller/             # Internal auth endpoints
│       ├── entity/                 # Member entity
│       ├── repository/             # JPA repository
│       └── service/                # Business logic
│
├── product/                        # Product Service (Catalog)
│   ├── Dockerfile
│   ├── pom.xml
│   ├── setup-db.sql               # Seed 50,000 products
│   └── src/main/java/.../
│       ├── config/                 # Redis cache config
│       ├── controller/             # Product endpoints
│       ├── entity/                 # Product entity
│       ├── repository/             # JPA repository
│       └── service/                # Business logic with caching
│
├── cart/                           # Cart Service (Shopping Cart)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── client/                 # ProductClient for validation
│       ├── controller/             # Cart endpoints
│       ├── entity/                 # Cart, CartItem entities
│       ├── repository/             # MongoDB repository
│       └── service/                # Cart business logic
│
├── common-model/                   # Shared DTOs
│   └── src/main/java/.../
│       └── model/
│           ├── BaseResponse.java   # Standard API response wrapper
│           └── ErrorResponse.java  # Error response format
│
└── README.md
```

## Configuration

### Environment Variables

| Variable | Service | Description | Required |
|----------|---------|-------------|----------|
| `SECURITY_JWT_SECRET_KEY` | api-gateway | JWT signing key (min 32 chars) | Yes |
| `SPRING_DATA_REDIS_HOST` | api-gateway, product | Redis hostname | Docker only |
| `SPRING_DATASOURCE_URL` | member, product | PostgreSQL JDBC URL | Docker only |
| `SPRING_DATA_MONGODB_URI` | cart | MongoDB connection URI | Docker only |

### Default Ports

| Service | Local | Docker |
|---------|-------|--------|
| API Gateway | 8080 | 8080 |
| Member | 8081 | 8081 |
| Product | 8082 | 8082 |
| Cart | 8083 | 8083 |
| PostgreSQL | 5432 | 5432 |
| MongoDB | 27017 | 27017 |
| Redis | 6379 | 6379 |

## Development

### Build All Services
```bash
# From root directory
cd common-model && mvn clean install
cd ../member && mvn clean package
cd ../product && mvn clean package
cd ../cart && mvn clean package
cd ../api-gateway && mvn clean package
```

### Run Tests
```bash
mvn test
```

### API Documentation
Each service (except api-gateway) has Swagger UI available:
- Member: http://localhost:8081/members/swagger-ui.html
- Product: http://localhost:8082/products/swagger-ui.html
- Cart: http://localhost:8083/cart/swagger-ui.html

## License
MIT
