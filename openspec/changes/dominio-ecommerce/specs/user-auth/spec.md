# User Authentication Specification

## Purpose

Replace the hardcoded `admin/admin123` credential check with database-backed users (`Usuario`) and roles (`Rol`: ADMIN, CLIENTE), BCrypt password hashing, public CLIENTE self-registration and JWT login validated against the database. The `roles` claim keeps `ROLE_`-prefixed entries so `hasRole("ADMIN")` and existing test tooling keep working. Secret and expiration come from a single source (`app.jwt.secret`, `app.jwt.expiration`); the hardcoded 3600 s TTL and the duplicated secret default in the login path are removed, and dead `JwtConfig` code is wired or deleted.

## Requirements

### Requirement: ADMIN seed on startup

The system MUST seed one `Usuario` with rol ADMIN and username `admin` when no ADMIN exists, storing the password as a BCrypt hash read from configuration (dev default keeps the historic `admin123`, never stored in plaintext). Seeding MUST be idempotent and MUST NOT overwrite an existing `admin`.

#### Scenario: First boot seeds the admin user

- GIVEN an empty usuarios table and the configured admin password
- WHEN the application starts
- THEN a usuario `admin` with rol ADMIN exists and its stored password is a BCrypt hash

#### Scenario: Seeder does not reset an existing admin

- GIVEN an `admin` usuario created by a previous boot
- WHEN the application starts again
- THEN the existing password hash is left untouched

### Requirement: Login validates against the database

POST /auth/login MUST remain public and authenticate by username lookup plus BCrypt match. Unknown username and wrong password MUST both return 401 `CredencialesInvalidasException` with the same Spanish message (no user enumeration). On success the token MUST carry subject=username, a `roles` claim containing `ROLE_ADMIN` or `ROLE_CLIENTE` per the stored rol, expiry derived from `app.jwt.expiration`, and the response MUST keep the shape `LoginResponse(token, username, expiresIn)`.

#### Scenario: Valid admin login

- GIVEN the seeded `admin` and its correct password
- WHEN POST /auth/login
- THEN 200 with a token whose roles claim contains "ROLE_ADMIN" and whose expiry reflects app.jwt.expiration

#### Scenario: Wrong password and unknown user are indistinguishable

- GIVEN a wrong password for `admin`, or an unknown username
- WHEN POST /auth/login
- THEN 401 "Credenciales inválidas" in both cases

#### Scenario: CLIENTE login carries CLIENTE authorities only

- GIVEN a registered CLIENTE usuario with its correct password
- WHEN POST /auth/login
- THEN 200 with a roles claim containing only "ROLE_CLIENTE"

### Requirement: Public CLIENTE registration

The system MUST expose public registration (POST /auth/register) creating a `Usuario` with rol CLIENTE only, regardless of any client-supplied role hint. `username` MUST be unique and validated with Spanish messages; `password` MUST satisfy a minimum policy (at least 8 characters, Spanish message). Duplicate username MUST return 409. The response MUST NOT expose the password hash.

#### Scenario: Register a cliente

- GIVEN a valid `{username, password}` body
- WHEN POST /auth/register
- THEN 201 and the usuario can log in with those credentials as CLIENTE

#### Scenario: Duplicate username

- GIVEN an existing username
- WHEN POST /auth/register reuses it
- THEN 409 with a Spanish message

#### Scenario: Registration cannot mint an admin

- GIVEN a registration body that hints at rol ADMIN
- WHEN POST /auth/register
- THEN the created usuario has rol CLIENTE

### Requirement: Role enforcement on catalog mutations

A valid CLIENTE token MUST be forbidden from POST/PUT/PATCH/DELETE under `/v1/productos/**` (403); an ADMIN token MUST remain allowed; public catalog GETs stay open.

#### Scenario: CLIENTE blocked from catalog mutation

- GIVEN a valid CLIENTE JWT
- WHEN POST /v1/productos
- THEN 403
