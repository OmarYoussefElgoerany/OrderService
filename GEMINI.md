# GEMINI.md — Orders Service

## Overview & Architecture

**Orders** (`com.screen:orders`) is a Spring Boot REST API providing JWT-based authentication, role/permission-based authorization, and audited order management backed by PostgreSQL.

- **Stateless Security & Token Rotation**:
  - Short-lived JWT **access tokens** (`token_type=access`, 15m default) are returned in the JSON response body and passed via `Authorization: Bearer <token>`.
  - Long-lived JWT **refresh tokens** (`token_type=refresh`, 7d default) are delivered via an `HttpOnly`, configurable `Secure`/`SameSite` cookie (`refreshToken`, path `/api/auth`).
  - Only SHA-256 hashes of refresh tokens are persisted in the `token` table (`TokenHash`), scoped to one `Token` row per user with a signed session family (`sid` / `familyId`) claim and a short `previousTokenHash` + `rotatedAt` multi-tab concurrency grace window. Presenting an already-rotated refresh token from the active session family outside the grace window triggers reuse detection and immediately revokes the session.
- **Authorization (RBAC + Authorities)**:
  - Roles (`USER`, `MANAGER`, `ADMIN`) map to granular permissions (`admin:*`, `management:*`) and `ROLE_*` authorities.
  - Enforced via both URL matchers in `SecurityConfiguration` and `@PreAuthorize` method security.
- **JPA Auditing**:
  - Enabled on `OrdersApplication` (`@EnableJpaAuditing(auditorAwareRef = "auditorAware")`).
  - `ApplicationAuditAware` resolves the authenticated `User.id` to populate `@CreatedBy` and `@LastModifiedBy` on audited entities (`Order`).

---

## Tech Stack

- **Language & Runtime**: Java 21
- **Framework**: Spring Boot `4.1.2` (`spring-boot-starter-webmvc`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`)
- **Database**: PostgreSQL (`jdbc:postgresql://localhost:5432/orders_db`)
- **JWT**: `io.jsonwebtoken:jjwt-*` (`0.12.6`)
- **API Documentation**: SpringDoc OpenAPI Starter WebMVC UI (`3.2.0`) — Swagger UI enabled with `with-credentials=true`
- **Boilerplate Reduction**: Project Lombok
- **Build Tool**: Maven (with Maven Wrapper `mvnw` / `mvnw.cmd`)

---

## Directory Structure & Key Modules

Base package: `src/main/java/com/screen/orders`

| Package / File | Responsibility |
| :--- | :--- |
| `OrdersApplication.java` | Main `@SpringBootApplication` entry point and JPA Auditing bootstrap. |
| `config/` | Security filter chain (`SecurityConfiguration`), bearer filter (`JwtAuthenticationFilter`), JWT signing/validation (`JwtService`), logout revocation (`LogoutService`), core beans (`ApplicationConfig`), and OpenAPI specification (`OpenApiConfig`). |
| `auth/` | Public auth endpoints (`AuthenticationController` at `/api/auth/{register,login,refresh}`), session rotation and reuse detection (`AuthenticationService`), and `RefreshTokenCookie` helper. |
| `token/` | `Token` (active refresh session per user with `familyId`, `previousTokenHash`, `rotatedAt`, and pessimistic locking), `TokenHash` (SHA-256 utility), and `TokenRepository`. |
| `user/` | `User` entity (`_user` table, implements `UserDetails`), `Role` & `Permission` enums, and password update flow (`UserController` at `PATCH /api/v1/users`, `UserService`). |
| `order/` | Audited `Order` entity, `OrderRequest` DTO, `OrderService`, and `OrderController` (`GET`/`POST /api/v1/orders`). |
| `auditing/` | `ApplicationAuditAware` bridging Spring Security's `SecurityContext` to Spring Data JPA auditing. |
| `demo/` | Role/authority demonstration controllers (`AdminController` at `/api/v1/admin`, `ManagementController` at `/api/v1/management`, `DemoController` at `/api/v1/demo-controller`). |

Configuration: `src/main/resources/application.properties`  
Tests: `src/test/java/com/screen/orders/OrdersApplicationTests.java`

---

## Required Environment & Configuration

Before running the application or context-loading tests, ensure:

1. **PostgreSQL** is running on `localhost:5432` with database `orders_db` (default credentials: `postgres` / `postgres`).
2. **Environment Variables**:
   - `JWT_SECRET` (**required**): Base64-encoded HMAC-SHA secret key (at least 256 bits / 32 bytes before encoding).
   - `CORS_ALLOWED_ORIGINS` (*optional*): Comma-separated allowed origins (defaults to `http://localhost:3000`).

---

## Build, Run & Test Commands

Use the included Maven Wrapper (`.\mvnw.cmd` on Windows PowerShell/CMD, `./mvnw` on Unix/Git Bash):

```powershell
# Compile the project
.\mvnw.cmd clean compile

# Run the application (requires PostgreSQL and JWT_SECRET)
$env:JWT_SECRET="<base64-encoded-256-bit-secret>"
.\mvnw.cmd spring-boot:run

# Run all tests
.\mvnw.cmd test

# Package executable JAR (skipping tests)
.\mvnw.cmd clean package -DskipTests
```

### API Docs (when running locally on port 8080)
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

---

## Git Workflow & Approval Rules

- **Branch Isolation**: Never work or commit directly on `main` or `master`. Before modifying any files for a new feature, test suite, or bug fix, create and checkout a new branch (`feat/<short-name>`, `test/<short-name>`, or `fix/<short-name>`).
- **Pre-Commit Gate**: After completing the changes and verifying that `.\mvnw.cmd test` passes, stop and present a summary of changes (`git status` and `git diff --stat`) and **ask for explicit user approval before running `git commit`**.
- **Commit Format**: Once approved, stage the modified files and commit locally using Conventional Commits (`feat: ...`, `test: ...`, `fix: ...`).
- **Pre-Merge & Push Gate**: **Never merge any branch into `main` or `master` (or run `git push`) unless explicitly approved by the user.**

