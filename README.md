# Smart Voucher API

Hệ thống quản lý voucher thông minh được xây dựng bằng **Spring Boot 3.4.3** với **Java 21**, hỗ trợ các tính năng xác thực, phân quyền, quản lý voucher, và tracking API.

## 📋 Mục lục

- [Tính năng chính](#tính-năng-chính)
- [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
- [Cài đặt và chạy dự án](#cài-đặt-và-chạy-dự-án)
- [Cấu hình biến môi trường](#cấu-hình-biến-môi-trường)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [API Documentation](#api-documentation)
- [Database](#database)
- [Docker Compose](#docker-compose)
- [Troubleshooting](#troubleshooting)

## ✨ Tính năng chính

- ✅ **Xác thực & Phân quyền** - JWT-based authentication với Spring Security, phân quyền RBAC (Admin, Staff, User)
- ✅ **Quản lý Voucher** - Tạo, cập nhật, xóa, và theo dõi voucher
- ✅ **Đăng ký Tài khoản** - Tính năng đăng ký người dùng mới
- ✅ **Xác thực Email** - Xác thực email sau khi đăng ký
- ✅ **Quên/Đặt lại Mật khẩu** - Reset password và change password
- ✅ **Quản lý Campaign** - Tạo và quản lý các chiến dịch voucher
- ✅ **Gửi Email** - Gửi voucher qua email
- ✅ **API Tracking** - Tracking request/response và rate limiting
- ✅ **Cache Redis** - Caching các dữ liệu thường xuyên truy cập

## 🔧 Yêu cầu hệ thống

### Phát triển Local

- **Java**: 21 trở lên
- **Maven**: 3.9.0 trở lên
- **PostgreSQL**: 14+ 
- **Redis**: 7.0+
- **Git**: Để clone/push code
- **Docker & Docker Compose**: (Optional, nếu muốn chạy database bằng container)

### Runtime

- **PostgreSQL**: 14+
- **Redis**: 7.0+
- **Java Runtime**: 21+

## 🚀 Cài đặt và chạy dự án

### Cách 1: Chạy Local với Database từ Docker

**Bước 1: Clone dự án**
```bash
git clone <repository-url>
cd spring_be_smart_voucher
```

**Bước 2: Khởi động database và Redis bằng Docker**
```bash
docker-compose up -d postgres redis
```

Chờ cho đến khi cả PostgreSQL và Redis khởi động thành công (kiểm tra health check):
```bash
docker-compose ps
```

**Bước 3: Build dự án**
```bash
mvn clean package -DskipTests
```

**Bước 4: Chạy ứng dụng**

Chạy với Spring Boot Maven plugin:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

Hoặc chạy trực tiếp JAR file:
```bash
java -jar target/smart-voucher-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

**Bước 5: Kiểm tra ứng dụng**
```bash
# Ứng dụng sẽ chạy tại: http://localhost:8080

# Kiểm tra API:
curl http://localhost:8080/api/health

# Xem Swagger UI:
# http://localhost:8080/swagger-ui.html
```

---

### Cách 2: Chạy Toàn bộ với Docker Compose

**Bước 1: Clone dự án**
```bash
git clone <repository-url>
cd spring_be_smart_voucher
```

**Bước 2: Cấu hình biến môi trường (Optional)**

Tạo file `.env`:
```bash
# .env
JWT_SECRET=smartvoucher-secret-key-must-be-at-least-256-bits-long-for-hs256
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
APP_BASE_URL=http://localhost:8080
```

**Bước 3: Khởi động tất cả services**
```bash
docker-compose up -d
```

**Bước 4: Kiểm tra logs**
```bash
docker-compose logs -f app
```

**Bước 5: Kiểm tra ứng dụng**
```bash
# Ứng dụng sẽ chạy tại: http://localhost:8080

# Kiểm tra health:
curl http://localhost:8080/api/health
```

**Bước 6: Dừng services**
```bash
docker-compose down
```

---

### Cách 3: Chạy Test

```bash
# Chạy tất cả tests
mvn test

# Chạy một test class cụ thể
mvn test -Dtest=AuthControllerTest

# Chạy tests với coverage report
mvn clean test jacoco:report
# Xem report tại: target/site/jacoco/index.html
```

## 🔐 Cấu hình biến môi trường

### Development (dev)

Sửa file `src/main/resources/application-dev.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smart_voucher
    username: postgres
    password: postgres
  redis:
    host: localhost
    port: 6379
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

app:
  jwt:
    secret: smartvoucher-dev-secret-key-256-bits-minimum-for-hs256
    expiration: 86400000  # 24 hours
  mail:
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
  baseUrl: http://localhost:8080
```

### Production (prod)

Sửa file `src/main/resources/application-prod.yml`:
```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400000
  mail:
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
  baseUrl: ${APP_BASE_URL}
```

### Biến môi trường quan trọng

| Biến | Mô tả | Ví dụ |
|------|-------|-------|
| `JWT_SECRET` | Secret key cho JWT (tối thiểu 256 bits) | `smartvoucher-secret-key-must-be-at-least-256-bits-long` |
| `DB_URL` | Connection string PostgreSQL | `jdbc:postgresql://localhost:5432/smart_voucher` |
| `DB_USERNAME` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `REDIS_HOST` | Redis hostname | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `MAIL_USERNAME` | Email address để gửi mail | `your-email@gmail.com` |
| `MAIL_PASSWORD` | Email app password | `xxxx xxxx xxxx xxxx` |
| `APP_BASE_URL` | URL của ứng dụng | `http://localhost:8080` |
| `SPRING_PROFILES_ACTIVE` | Active profile | `dev` hoặc `prod` |

## 📁 Cấu trúc dự án

```
spring_be_smart_voucher/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/smartvoucher/
│   │   │       ├── controller/       # REST Controllers
│   │   │       ├── service/          # Business Logic
│   │   │       ├── repository/       # Data Access Layer
│   │   │       ├── entity/           # JPA Entities
│   │   │       ├── dto/              # Data Transfer Objects
│   │   │       ├── security/         # Security Configuration
│   │   │       ├── config/           # App Configuration
│   │   │       ├── exception/        # Exception Handling
│   │   │       ├── util/             # Utility Classes
│   │   │       └── SmartVoucherApplication.java  # Main Class
│   │   └── resources/
│   │       ├── application.yml       # Default config
│   │       ├── application-dev.yml   # Development config
│   │       ├── application-prod.yml  # Production config
│   │       └── db/migration/         # Flyway migrations
│   └── test/
│       ├── java/                     # Unit & Integration Tests
│       └── resources/
│           └── application-test.yml  # Test config
├── database/
│   ├── smart_voucher_api.sql         # Database schema
│   └── erd_api.puml                  # Entity Relationship Diagram
├── docker-compose.yml                # Docker Compose configuration
├── Dockerfile                        # Docker image build config
├── pom.xml                          # Maven dependencies
├── requirements.md                  # Project requirements
└── README.md                        # This file
```

## 📚 API Documentation

### Swagger UI

Sau khi chạy ứng dụng, truy cập Swagger UI tại:
```
http://localhost:8080/swagger-ui.html
```

### API Endpoints chính

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/auth/register` | Đăng ký tài khoản mới |
| POST | `/api/auth/login` | Đăng nhập |
| POST | `/api/auth/refresh` | Refresh JWT token |
| POST | `/api/auth/logout` | Đăng xuất |
| POST | `/api/auth/forgot-password` | Quên mật khẩu |
| POST | `/api/auth/reset-password` | Đặt lại mật khẩu |
| PUT | `/api/auth/change-password` | Đổi mật khẩu |
| GET | `/api/vouchers` | Lấy danh sách voucher |
| POST | `/api/vouchers` | Tạo voucher mới |
| GET | `/api/vouchers/{id}` | Lấy chi tiết voucher |
| PATCH | `/api/vouchers/{id}` | Cập nhật voucher |
| DELETE | `/api/vouchers/{id}` | Xóa voucher |
| POST | `/api/vouchers/{id}/redeem` | Sử dụng voucher |

### Authentication

Tất cả endpoints (ngoại trừ đăng nhập, đăng ký) đều yêu cầu JWT token:

```bash
# Đăng nhập
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password"}'

# Response:
# {
#   "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "refreshToken": "...",
#   "user": {...}
# }

# Sử dụng token trong request
curl -X GET http://localhost:8080/api/vouchers \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

## 🗄️ Database

### Khởi tạo Database

Dự án sử dụng **Flyway** để quản lý migrations. Khi ứng dụng khởi động, Flyway sẽ tự động chạy tất cả migration files trong `src/main/resources/db/migration/`.

**Kiểm tra database:**
```bash
# Kết nối vào PostgreSQL
psql -h localhost -U postgres -d smart_voucher

# List tables
\dt

# Xem schema của một table
\d user_account
```

### Schema chính

- **user_account** - Thông tin người dùng
- **campaign** - Chiến dịch voucher
- **voucher** - Các voucher
- **voucher_redemption** - Lịch sử sử dụng voucher
- **api_key** - API keys cho third parties
- **audit_log** - Tracking request/response

### Xem ERD

Entity Relationship Diagram nằm tại: `database/erd_api.puml`

## 🐳 Docker Compose

### Architecture

```
┌─────────────────────────────────────────┐
│         Smart Voucher App               │
│    (Java 21 + Spring Boot 3.4.3)       │
├─────────────────────────────────────────┤
│         PostgreSQL Database             │
│      (smart_voucher_db container)       │
├─────────────────────────────────────────┤
│         Redis Cache                     │
│      (smart_voucher_redis container)    │
└─────────────────────────────────────────┘
```

### Docker Compose Commands

```bash
# Khởi động tất cả services
docker-compose up -d

# Khởi động services cụ thể
docker-compose up -d postgres redis
docker-compose up -d app

# Xem logs
docker-compose logs -f                    # Tất cả services
docker-compose logs -f app                # Chỉ app
docker-compose logs -f postgres           # Chỉ postgres

# Kiểm tra status
docker-compose ps

# Dừng services
docker-compose stop                       # Dừng nhưng không xóa
docker-compose down                       # Dừng và xóa containers
docker-compose down -v                    # Dừng, xóa containers và volumes

# Rebuild image
docker-compose build
docker-compose up -d --build

# Xóa tất cả (containers, images, volumes)
docker-compose down -v --rmi all
```

### Volumes

Docker Compose tạo hai volumes để persist data:

- **pgdata** - PostgreSQL data
- **redisdata** - Redis data

Xem volumes:
```bash
docker volume ls
docker volume inspect smart_voucher_db_pgdata
```

## 🔍 Troubleshooting

### ❌ Lỗi: Port 5432 đã được sử dụng

**Vấn đề**: PostgreSQL đã chạy trên port 5432

**Giải pháp**:
```bash
# Option 1: Kill process
lsof -i :5432
kill -9 <PID>

# Option 2: Sử dụng port khác
# Chỉnh sửa docker-compose.yml:
# postgress:
#   ports:
#     - "5433:5432"  # Sử dụng port 5433
# Sau đó cập nhật connection string trong application-dev.yml
```

### ❌ Lỗi: Connection refused to database

**Vấn đề**: Ứng dụng không thể kết nối đến PostgreSQL

**Giải pháp**:
```bash
# 1. Kiểm tra PostgreSQL đã khởi động
docker-compose ps

# 2. Kiểm tra logs
docker-compose logs postgres

# 3. Test connection
docker-compose exec postgres psql -U postgres -d smart_voucher -c "SELECT 1"

# 4. Chờ PostgreSQL ready (health check)
docker-compose up postgres -d
sleep 10  # Chờ health check pass
```

### ❌ Lỗi: Flyway migration failed

**Vấn đề**: Flyway migration lỗi khi khởi động

**Giải pháp**:
```bash
# 1. Kiểm tra logs
docker-compose logs app | grep -i flyway

# 2. Clean database (cẩn thận - xóa tất cả data!)
docker-compose down -v
docker-compose up -d postgres
sleep 10

# 3. Chạy lại ứng dụng
docker-compose up -d app
```

### ❌ Lỗi: Redis connection timeout

**Vấn đề**: Ứng dụng không thể kết nối Redis

**Giải pháp**:
```bash
# 1. Kiểm tra Redis đã khởi động
docker-compose ps redis

# 2. Test Redis connection
docker-compose exec redis redis-cli ping

# 3. Xem logs
docker-compose logs redis

# 4. Restart Redis
docker-compose restart redis
```

### ❌ Lỗi: Java version mismatch

**Vấn đề**: `Error: Unsupported Java version`

**Giải pháp**:
```bash
# Kiểm tra Java version
java -version

# Cần Java 21+. Cài đặt hoặc switch version
# macOS (với Homebrew):
brew install openjdk@21
# Hoặc dùng SDKMAN: sdk install java 21

# Ubuntu/Debian:
sudo apt-get install openjdk-21-jdk

# Set JAVA_HOME:
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### ❌ Lỗi: Maven build failed

**Vấn đề**: `mvn clean package` bị lỗi

**Giải pháp**:
```bash
# 1. Clear Maven cache
rm -rf ~/.m2/repository
mvn clean

# 2. Cài đặt dependencies lại
mvn install

# 3. Build again
mvn clean package -DskipTests
```

### ⚠️ Check Health của services

```bash
# Check app health
curl http://localhost:8080/actuator/health

# Check database
docker-compose exec postgres psql -U postgres -d smart_voucher -c "SELECT 1"

# Check Redis
docker-compose exec redis redis-cli ping

# Check PostgreSQL status
docker-compose logs postgres | tail -20
```

## 📖 Tài liệu thêm

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Flyway Documentation](https://flywaydb.org/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Redis Documentation](https://redis.io/documentation)
- [Docker Documentation](https://docs.docker.com/)

## 👥 Contributing

1. Fork dự án
2. Tạo feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📝 License

Dự án này là một phần của khóa học DACN tại SGU.

---

**Được tạo**: March 2026  
**Version**: 1.0.0  
**Status**: Active Development
