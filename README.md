# Booking App

A REST API for managing resort activity bookings. Customers can register, browse availability, book activities, and reschedule. Administrators can manage resources and configure settings.

## Tech Stack

- **Java 25** with **Spring Boot 4.1.1**
- **PostgreSQL 18** for production, **H2** for tests
- **Spring Security** with **JWT** (HS256) bearer-token authentication
- **Spring Data JPA** with **Hibernate**
- **Lombok** for boilerplate reduction
- **Maven** for build management
- **Docker Compose** for local database

## Prerequisites

- Java 25 JDK
- Maven 3.8+ (or use the included Maven wrapper)
- Docker and Docker Compose (for the local PostgreSQL database)

## Quick Start

### 1. Start the Database

```bash
docker compose up -d
```

This starts a PostgreSQL 18 container on `localhost:5432` with database `booking_db`, user `booking_user`, and password `dev_password`.

### 2. Run the Application

```bash
.\mvnw.cmd spring-boot:run
```

The application starts on `http://localhost:8080`. Hibernate will automatically create or update the database schema on first run.

### 3. Create an Admin User

Register a new account. If the email matches `app.admin.email` in `application.yaml` (default: `admin@example.com`), the user is automatically promoted to administrator on registration and on each subsequent login.

## API Overview

All endpoints return JSON. Authentication endpoints are public; all other endpoints require a valid JWT bearer token.

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new account |
| `POST` | `/api/auth/login` | Log in and receive a JWT token |

After login, include the token in subsequent requests:

```
Authorization: Bearer <token>
```

### Bookings (Customer)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/bookings` | List my bookings (newest first) |
| `GET` | `/api/bookings?status=PENDING` | Filter by status: `PENDING`, `CONFIRMED`, `REJECTED`, `DROPPED`, `COMPLETED` |
| `GET` | `/api/bookings/{id}` | Get booking details |
| `POST` | `/api/bookings/availability` | Browse availability for resources and dates |
| `POST` | `/api/bookings` | Create a new booking |
| `POST` | `/api/bookings/{id}/reschedule` | Reschedule a booking to a new date |

### User Profile

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/users/me` | Get my profile |
| `PUT` | `/api/users/me` | Update my profile (name, email, phone) |
| `DELETE` | `/api/users/me` | Delete my account (soft delete) |
| `PUT` | `/api/users/me/password` | Change my password |

### Admin Resources

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/admin/resources` | List all resources |
| `GET` | `/api/admin/resources/{id}` | Get resource details |
| `POST` | `/api/admin/resources` | Create a new resource |
| `PUT` | `/api/admin/resources/{id}` | Update a resource |
| `POST` | `/api/admin/resources/{id}/activate` | Activate a resource |
| `POST` | `/api/admin/resources/{id}/deactivate` | Deactivate a resource |

### Admin Settings

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/admin/settings` | List all settings |
| `GET` | `/api/admin/settings/{key}` | Get a specific setting |
| `PUT` | `/api/admin/settings/reschedule-deadline-hours` | Update the rescheduling deadline |

## Booking Domain

### Resort Hours

The resort operates Saturday through Sunday in the `Europe/Vilnius` time zone. Three session types are available:

- **Morning**: 09:00–13:00
- **Evening**: 13:00–17:00
- **Full Day**: 09:00–17:00

These times are defined in `Session.java` and can be adjusted for the resort's actual hours.

### Resources

Resources come in two types:

- **Tickets**: Entry passes for activities. The sum of ticket quantities must equal the group size.
- **Gear**: Equipment rentals. Quantities are independent of group size.

Full-day bookings require available capacity in both the morning and evening halves.

### Booking Lifecycle

A booking moves through these states:

1. **PENDING** — Created, awaiting admin review. Holds capacity.
2. **CONFIRMED** — Approved by an admin. Holds capacity.
3. **REJECTED** — Declined by an admin. Releases capacity.
4. **DROPPED** — Time-derived: a pending booking whose session has already started. Releases capacity.
5. **COMPLETED** — Time-derived: a confirmed booking whose session has ended. Releases capacity.

States `DROPPED` and `COMPLETED` are computed from the current time and require no database updates or scheduler. Only pending and confirmed bookings hold capacity.

### Rescheduling

- Changes only the date; items, session type, and prices remain the same.
- Available for pending and confirmed bookings.
- Must occur before the rescheduling deadline: the booking's session start time minus `reschedule_deadline_hours`.
- The destination must be a future open session with sufficient capacity.
- A failed reschedule leaves the original booking unchanged.
- Bookings retain their original prices even if resources are later repriced or deactivated.

## Security

### Authentication

The API uses **JWT bearer tokens** signed with **HS256**. After login, the client receives a token containing:

- `sub` — the user's numeric ID
- `iss` — the issuer (`booking-app`)
- `aud` — the audience (`booking-app-api`)
- `iat` — issued-at timestamp
- `exp` — expiration (default: 30 minutes)
- `admin` — present when the user is an administrator
- `tokenVersion` — the user's current token version

The decoder validates the signature, requires an expiration claim, and verifies the issuer and audience.

### Authorization

- **Public**: `/api/auth/**` and `/errors` require no authentication.
- **Authenticated**: All other endpoints require a valid JWT.
- **Admin only**: `/api/admin/**` endpoints require the `ROLE_ADMIN` authority. Regular customers receive `403 Forbidden`.

Users whose email matches `app.admin.email` are automatically promoted to administrators.

### Token Revocation

Tokens are immediately invalidated when sensitive account changes occur:

- Password change
- Account deletion
- Admin status change

The app uses a `tokenVersion` counter stored in the database. Each request validates the JWT's `tokenVersion` claim against the current value. A mismatch rejects the token with `401 Unauthorized`.

### Development Profile

The `dev` profile allows impersonation via the `X-Dev-User-Id` header, bypassing the JWT subject for user ID resolution. This is intended for local development only and **must never be enabled in any deployed environment**.

```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

## Configuration

Key properties in `application.yaml`:

| Property | Default | Description |
|---|---|---|
| `app.jwt.secret` | *(dev only)* | HS256 signing key (minimum 32 bytes) |
| `app.jwt.expiration-minutes` | `30` | Token lifetime in minutes |
| `app.jwt.issuer` | `booking-app` | Expected issuer claim |
| `app.jwt.audience` | `booking-app-api` | Expected audience claim |
| `app.admin.email` | `admin@example.com` | Email auto-promoted to admin |

Override these values via environment variables or a separate profile for production.

## Testing

Run the full test suite:

```bash
.\mvnw.cmd test
```

Tests use an H2 in-memory database with transactional rollback. Service tests use a fixed clock for deterministic time-based behavior.

## Deployment Notes

- **JWT Secret**: Replace the default secret with a strong, randomly generated value. Never commit the production secret.
- **Database**: Use a separate PostgreSQL instance with production credentials. The `docker-compose.yaml` file is for local development only.
- **HTTPS**: Terminate TLS at a reverse proxy or load balancer. Passwords and JWT tokens should not be sent over plain HTTP.
- **CSRF**: CSRF protection is disabled. The API uses stateless bearer-token authentication. If a future client stores tokens in automatically-sent cookies, revisit CSRF protection.
- **CORS**: No application CORS policy is configured. Configure allowed origins at the reverse proxy or in Spring Security if a web frontend needs cross-origin access.