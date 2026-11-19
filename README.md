# VulnBank-API — Security Testing & Remediation Walkthrough

A Java 17 and Spring Boot 3 REST API security learning and verification project backed by an H2 in-memory database. This repository documents **manual API security testing with `curl`** covering HTTP response headers, authentication, broken object level authorization (IDOR/BOLA), transfer business logic, SQL injection defenses, broken function level authorization (RBAC), mass assignment protection, and response data exposure.

> **Disclaimer:** Seeded credentials, opaque in-memory bearer tokens, and the H2 console are included strictly for local security testing and demonstration purposes.

---

## Quick Start

Run the Spring Boot service from the project directory:

```bash
mvn spring-boot:run
```

- **Base URL:** `http://localhost:8080`
- **Public Health Check:** `GET /api/health`
- **H2 Console:** `http://localhost:8080/h2-console` (`jdbc:h2:mem:vulnbank`, username `sa`, blank password)

### Seeded Accounts

| Username | Password | Role | Seeded User ID |
|---|---|---|---|
| `admin` | `admin-password-123` | `ADMIN` | `1` |
| `alice` | `alice-password-123` | `USER` | `2` |
| `bob` | `bob-password-123` | `USER` | `3` |

---

## Manual Security Testing with `curl`

Each test below was performed manually via `curl` against `http://localhost:8080` to verify HTTP status codes, headers, authorization checks, input boundaries, and returned payloads.

### 1. Response Headers & Health Check

```bash
curl -i http://localhost:8080/api/health
```

**Observed Behavior:**
- Returns `HTTP/1.1 200` with body `{"status":"UP"}`.
- Response headers include `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `X-XSS-Protection: 0`, and `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`.

---

### 2. Authentication & Bearer Token Issuance

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice-password-123"}'
```

**Observed Behavior:**
- Returns `HTTP/1.1 200` with a bearer `token` and user summary (`id`, `username`, `email`, `role`, `balance`).
- Password fields and BCrypt hashes are not leaked in the response.

---

### 3. IDOR / Broken Object Level Authorization (BOLA)

Compare authorized access to Alice's own profile (`id=2`) against unauthorized access to Bob's profile (`id=3`) using Alice's bearer token:

```bash
# Own profile (authorized)
curl -i http://localhost:8080/api/users/2/profile \
  -H "Authorization: Bearer <ALICE_TOKEN>"

# Cross-user profile (unauthorized)
curl -i http://localhost:8080/api/users/3/profile \
  -H "Authorization: Bearer <ALICE_TOKEN>"
```

**Observed Behavior:**
- `GET /api/users/2/profile` returns `HTTP/1.1 200` with Alice's profile details.
- `GET /api/users/3/profile` is denied with `HTTP/1.1 403` (`Content-Length: 0`), confirming ownership enforcement.

---

### 4. Transfer Business Logic & Sender Integrity

Test negative transfer amounts alongside a valid transfer using Alice's bearer token:

```bash
# Negative transfer attempt
curl -i -X POST http://localhost:8080/api/transactions/transfer \
  -H "Authorization: Bearer <ALICE_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"toUserId":3,"amount":-500.00,"note":"Negative test"}'

# Valid transfer attempt
curl -i -X POST http://localhost:8080/api/transactions/transfer \
  -H "Authorization: Bearer <ALICE_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"toUserId":3,"amount":25.00,"note":"Test transfer"}'
```

**Observed Behavior:**
- Negative transfer (`-500.00`) is blocked with `HTTP/1.1 403` without altering account balances.
- Valid transfer (`25.00`) returns `HTTP/1.1 201` with `fromUserId: 2` derived on the server from the authenticated principal rather than trusting a client-supplied sender ID.

---

### 5. SQL Injection Testing on Transaction Search

Test a normal search query against a SQL boolean tautology payload (`' OR '1'='1`):

```bash
# Normal note search
curl -i -G "http://localhost:8080/api/transactions/search" \
  -H "Authorization: Bearer <ALICE_TOKEN>" \
  --data-urlencode "note=Test transfer"

# SQLi tautology payload
curl -i -G "http://localhost:8080/api/transactions/search" \
  -H "Authorization: Bearer <ALICE_TOKEN>" \
  --data-urlencode "note=' OR '1'='1"
```

**Observed Behavior:**
- Searching `Test transfer` returns `HTTP/1.1 200` with the matching transaction record.
- Searching `' OR '1'='1` returns `HTTP/1.1 200` with an empty JSON array `[]` and no SQL error or unauthorized data dump, consistent with parameterized query execution.

---

### 6. Broken Function Level Authorization (Privilege Escalation)

Attempt to delete a user account (`id=3`) while authenticated as standard user `alice` (`role: USER`):

```bash
curl -i -X DELETE http://localhost:8080/api/admin/users/3 \
  -H "Authorization: Bearer <ALICE_TOKEN>"
```

**Observed Behavior:**
- Rejected with `HTTP/1.1 403` (`Content-Length: 0`), confirming role-based access enforcement on admin operations.

---

### 7. Mass Assignment Testing on Registration

Register a new user (`mallory`) while attempting to inject elevated `role` and inflated `balance` fields:

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"mallory","password":"mallory-password-123","email":"mallory@example.test","role":"ADMIN","balance":999999.00}'
```

**Observed Behavior:**
- Returns `HTTP/1.1 201` with `{"id":4,"username":"mallory","email":"mallory@example.test","role":"USER","balance":0}`.
- Request DTO binding prevents client control over `role` and `balance`, applying safe server defaults (`USER` and `0`) and omitting password fields from the response.

---

### 8. Admin Access & Transaction History Inspection

Authenticate as `admin` (`role: ADMIN`) and retrieve transaction history for user `2`:

```bash
# Obtain admin token
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin-password-123"}'

# Inspect transaction history for user 2 as admin
curl -i http://localhost:8080/api/transactions/history/2 \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

**Observed Behavior:**
- Returns `HTTP/1.1 200` with the transaction records for user `2` (`id`, `fromUserId`, `toUserId`, `amount`, `note`) and no exposed password hashes or internal secrets.

---

## Audit Summary

| Area | Target / Vector | Verification Result |
|---|---|---|
| Headers & Health | `GET /api/health` | `200 OK` with security & no-cache headers |
| Authentication | `POST /api/auth/login` | `200 OK` token response; no password leak |
| IDOR / BOLA | `GET /api/users/2/profile` vs `/3/profile` | Own `200 OK`, other user `403 Forbidden` |
| Transfer Logic | `POST /api/transactions/transfer` | `-500.00` rejected (`403`), `25.00` created (`201`) |
| SQL Injection | `GET /api/transactions/search` | `' OR '1'='1` returns `200` with `[]` |
| Privilege Escalation | `DELETE /api/admin/users/3` (`USER`) | `403 Forbidden` |
| Mass Assignment | `POST /api/auth/register` | Extra `role`/`balance` ignored; defaults to `USER` & `0` |
| Admin History Access | `GET /api/transactions/history/2` (`ADMIN`) | `200 OK` with clean transaction DTOs |