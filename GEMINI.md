# GEMINI.md — Orders Service

## Overview & Architecture

**Orders** (`com.screen:orders`) is a Spring Boot REST API providing JWT-based authentication, strict 3-role RBAC authorization, and audited order management backed by PostgreSQL.

- **Global `/api/v1` Base Path & Controller Routing Convention**:
  - `server.servlet.context-path=/api/v1` is configured globally in `application.properties`.
  - Controller `@RequestMapping` annotations MUST specify only the resource path (e.g., `@RequestMapping("/auth")`, `@RequestMapping("/orders")`, `@RequestMapping("/users")`, `@RequestMapping("/admin/users")`) without hardcoding `/api` or `/api/v1`.
  - `SecurityConfiguration` matchers and `JwtAuthenticationFilter` skip paths are context-relative (`/auth/**`, `/admin/**`, `/management/**`), while external browser URLs and cookie paths use the full `/api/v1/...` prefix.
- **Stateless Security & Token Rotation**:
  - Short-lived JWT **access tokens** (`token_type=access`, 15m default) embed the user's primary `ROLE_*` authority claim (`role`) and are passed via `Authorization: Bearer <token>`.
  - Long-lived JWT **refresh tokens** (`token_type=refresh`, 7d default) are delivered via an `HttpOnly`, configurable `Secure`/`SameSite` cookie (`refreshToken`, path `/api/v1/auth`).
  - Only SHA-256 hashes of refresh tokens are persisted in the `token` table (`TokenHash`), scoped to one `Token` row per user with a signed session family (`sid` / `familyId`) claim and a short `previousTokenHash` + `rotatedAt` multi-tab concurrency grace window. Presenting an already-rotated refresh token from the active session family outside the grace window triggers reuse detection and immediately revokes the session.
- **3-Role RBAC (`USER`, `MANAGEMENT`, `ADMIN`)**:
  - The `Role` enum strictly defines three roles (`USER`, `MANAGEMENT`, `ADMIN`) persisted on `User` (`_user` table) via `@Enumerated(EnumType.STRING)` and mapped to `ROLE_*` and granular permission authorities (`admin:*`, `management:*`).
  - **Self-Registration (`POST /api/v1/auth/register`)**: `RegisterRequest` does not accept a `role` field (and ignores unknown JSON properties); newly registered users are unconditionally assigned `Role.USER`.
  - **Initial Admin Seeding**: `AdminBootstrapSeeder` (`ApplicationRunner`) checks on startup whether any `ADMIN` exists in `orders_db`; if none exists, it seeds the initial `ADMIN` from `APP_INITIAL_ADMIN_EMAIL` and `APP_INITIAL_ADMIN_PASSWORD`.
  - **Admin Role Management (`/api/v1/admin/users`)**: `AdminUserController` (`GET /admin/users`, `PATCH /admin/users/{id}/role`) is restricted to `ROLE_ADMIN` via both `SecurityConfiguration` (`.requestMatchers("/admin/**").hasRole("ADMIN")`) and `@PreAuthorize("hasRole('ADMIN')")`. `UserService.updateUserRole` prevents an admin from demoting themselves, prevents demoting the last remaining `ADMIN`, and immediately revokes the target user's refresh token session when their role changes.
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
- **Object Mapping**: MapStruct (`1.6.3`, Spring component model) with `lombok-mapstruct-binding` (`0.2.0`)
- **Boilerplate Reduction**: Project Lombok
- **Build Tool**: Maven (with Maven Wrapper `mvnw` / `mvnw.cmd`)

---

## Directory Structure & Key Modules

Base package: `src/main/java/com/screen/orders`

| Package / File | Responsibility |
| :--- | :--- |
| `OrdersApplication.java` | Main `@SpringBootApplication` entry point and JPA Auditing bootstrap. |
| `common/audit/` | `AuthenticatedPrincipal` interface and `ApplicationAuditAware` bridging `SecurityContext` to Spring Data JPA auditing. |
| `common/jpa/` | `@MappedSuperclass` base entity hierarchy (`BaseEntity` with `id` and Hibernate-safe `equals`/`hashCode`, and `BaseAuditedEntity` with JPA auditing fields). |
| `common/config/` | Core Spring beans (`ApplicationConfig`) and OpenAPI specification (`OpenApiConfig`). |
| `common/exception/` | Global `@RestControllerAdvice` (`GlobalExceptionHandler`) mapping exceptions to RFC 9457 `ProblemDetail`. |
| `common/security/` | Security filter chain (`SecurityConfiguration`), bearer filter (`JwtAuthenticationFilter`), JWT signing/validation (`JwtService`), and `SessionRevocationHandler` port. |
| `auth/` | Public auth endpoints (`AuthenticationController` at `@RequestMapping("/auth")` -> `/api/v1/auth/{register,login,refresh,logout}`), `AuthenticationService` (session rotation, reuse detection, `SessionRevocationHandler`), `LogoutService`, `RefreshTokenCookie` (`Path=/api/v1/auth`), package-private `TokenRepository`/`TokenHash`/`AuthMapper`, `Token` entity, and `auth/dto/` (`RegisterRequest`, `AuthenticationRequest`, `AuthenticationResponse`, `IssuedTokens`). |
| `user/` | `User` entity (`_user` table, implements `UserDetails` & `AuthenticatedPrincipal`), 3-value `Role` (`USER`, `MANAGEMENT`, `ADMIN`) & `Permission` enums, initial admin seeder (`AdminBootstrapSeeder`), admin user role management (`AdminUserController` at `@RequestMapping("/admin/users")` -> `/api/v1/admin/users`), password update flow (`UserController` at `@RequestMapping("/users")`, `UserService`), package-private `UserMapper`, and `user/dto/` (`ChangePasswordRequest`, `UpdateUserRoleRequest`, `UserSummaryResponse`). |
| `order/` | Audited `Order` entity, package-private `OrderRepository` & `OrderMapper`, `OrderService`, `OrderController` (`@RequestMapping("/orders")` -> `GET`/`POST /api/v1/orders`), and `order/dto/` (`OrderRequest`, `OrderResponse`). |
| `demo/` | Role/authority demonstration controllers (`AdminController` at `/admin`, `ManagementController` at `/management`, `DemoController` at `/demo-controller`). |

Configuration: `src/main/resources/application.properties`  
Tests: `src/test/java/com/screen/orders/`

---

## Required Environment & Configuration

Before running the application or context-loading tests, ensure:

1. **PostgreSQL** is running on `localhost:5432` with database `orders_db` (default credentials: `postgres` / `postgres`).
2. **Environment Variables**:
   - `JWT_SECRET` (**required**): Base64-encoded HMAC-SHA secret key (at least 256 bits / 32 bytes before encoding).
   - `APP_INITIAL_ADMIN_EMAIL` & `APP_INITIAL_ADMIN_PASSWORD` (*optional / recommended for first boot*): Credentials used by `AdminBootstrapSeeder` to create the initial `ADMIN` account when no `ADMIN` exists in `orders_db`.
   - `CORS_ALLOWED_ORIGINS` (*optional*): Comma-separated allowed origins (defaults to `http://localhost:3000`).

---

## Build, Run & Test Commands

Use the included Maven Wrapper (`.\mvnw.cmd` on Windows PowerShell/CMD, `./mvnw` on Unix/Git Bash):

```powershell
# Compile the project
.\mvnw.cmd clean compile

# Run the application (requires PostgreSQL and JWT_SECRET)
$env:JWT_SECRET="<base64-encoded-256-bit-secret>"
$env:APP_INITIAL_ADMIN_EMAIL="admin@example.com"
$env:APP_INITIAL_ADMIN_PASSWORD="<strong-password>"
.\mvnw.cmd spring-boot:run

# Run all tests
.\mvnw.cmd test

# Package executable JAR (skipping tests)
.\mvnw.cmd clean package -DskipTests
```

### API Docs (when running locally on port 8080 with `/api/v1` context path)
- **Swagger UI**: `http://localhost:8080/api/v1/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/api/v1/v3/api-docs`

---

## Git Workflow & Approval Rules

- **Branch Isolation**: Never work or commit directly on `main` or `master`. Before modifying any files for a new feature, test suite, or bug fix, create and checkout a new branch (`feat/<short-name>`, `test/<short-name>`, or `fix/<short-name>`).
- **Pre-Commit Gate**: After completing the changes and verifying that `.\mvnw.cmd test` passes, stop and present a summary of changes (`git status` and `git diff --stat`) and **ask for explicit user approval before running `git commit`**.
- **Commit Format**: Once approved, stage the modified files and commit locally using Conventional Commits (`feat: ...`, `test: ...`, `fix: ...`).
- **Pre-Merge & Push Gate**: **Never merge any branch into `main` or `master` (or run `git push`) unless explicitly approved by the user.**
