# 🛒 Product API : Spring Boot CRUD with MySQL

🚀 A robust and scalable Spring Boot-based RESTful API project designed to perform *CRUD operations on Product entities, integrated with **MySQL*, using clean architecture and modular design for better maintainability and scalability,

---

## 🎯 Objectives

- ✅ Develop RESTful APIs using Spring Boot  
- ✅ Perform end-to-end CRUD operations with MySQL database  
- ✅ Implement clean separation of concerns (Controller, DAO, Entity, Repository)  
- ✅ Ensure structured project architecture following best practices  
- ✅ Scalable codebase for real-time backend development and integration  

---
 
## 🗂 Project Structure Overview

<table>
  <tr>
    <td valign="top" width="25%">

<pre>
spring-boot-simple-crud-with-mysql
├── src
│   ├── main/java
│   │   └── com.jspider.spring_boot_simple_crud_with_mysql
│   │       ├── controller
│   │       │   └── ProductController.java        # Handles HTTP requests (GET, POST, PUT, DELETE)
│   │       ├── dao
│   │       │   └── ProductDao.java               # Business logic layer for product operations
│   │       ├── entity
│   │       │   └── Product.java                  # Entity mapped to MySQL DB table
│   │       ├── repository
│   │       │   └── ProductRepository.java        # JPA Repository interface
│   │       ├── responses
│   │       │   └── ResponseStructure.java        # Custom response wrapper for consistency
│   │       └── SpringBootSimpleCrudWithMysqlApplication.java  # Main class
│
│   ├── main/resources
│   │   ├── application.properties                # DB config and server settings
│   │   ├── static                                # Static files (optional for front-end)
│   │   └── templates                             # Thymeleaf templates (if used)
│
│   └── test/java                                 # Unit and integration tests (to be added)
│
├── pom.xml                                       # Maven dependencies and plugins
├── HELP.md                                       # Spring generated project help
├── mvnw, mvnw.cmd                                # Maven wrapper
├── target                                        # Compiled build output
</pre>

</td>
<td valign="top" width="55%">
  <img src="https://github.com/user-attachments/assets/079b2b9f-b935-4aea-9d41-dc989130e3de" alt="Eclipse Project Structure" width="100%" />
 
</td>
</tr>
</table> 
---
<img src="https://github.com/user-attachments/assets/939712d2-7094-4447-a997-745471c46dbc" alt="Eclipse Project Structure" width="100%" />
 
## 🌐 Technologies Used

- *Java 17* – Backend programming language  
- *Spring Boot* – Backend framework for building APIs  
- *Spring Data JPA* – ORM for database interaction  
- *MySQL* – Relational database for persistent storage  
- *Maven* – Project management and dependency tool  
- *Eclipse IDE / IntelliJ IDEA* – Development environment  
- *Postman* – API testing  
- *Git & GitHub* – Version control system  

---

## ✨ Key Features

- ✅ *RESTful API Design* using Spring Boot  
- ✅ *Modular Architecture* with clear separation of concerns  
- ✅ *CRUD Functionality*: Create, Read, Update, Delete for products  
- ✅ *Database Integration* using Spring Data JPA and MySQL  
- ✅ *Custom Response Wrapping* using ResponseStructure.java  
- ✅ *Scalable Project Template* for enterprise-level apps  
- ✅ *Maven-Based Build* and dependency management  

---

## 📦 API Endpoints

| Method | Endpoint              | Description                |
|--------|-----------------------|----------------------------|
| POST   | /products           | Create a new product       |
| GET    | /products           | Get all products           |
| GET    | /products/{id}      | Get a product by ID        |
| PUT    | /products/{id}      | Update product by ID       |
| DELETE | /products/{id}      | Delete product by ID       |

---
 
📌 Sample API Test:  
 
![postman](https://github.com/user-attachments/assets/9f0e39f1-1588-4c0e-b8da-18f0c1ab899b)
 
---
<h1 align="center">🛒Product API : API Automation Framework</h1>
 
🚀 A scalable and efficient API automation framework designed to validate and test Product API services, ensuring seamless integration, data integrity, and performance.


### 🎯 Objectives
✔ Automate end-to-end API testing with CRUD operations <br>
✔ Validate API responses and data accuracy <br>
✔ Implement data-driven testing for broader coverage <br>
✔ Generate detailed execution reports with Extent Reports <br>
✔ Ensure modular, maintainable, and scalable framework design <br>

![WhatsApp Image 2025-05-23 at 20 37 18_592d0402](https://github.com/user-attachments/assets/d7d24e0e-a1da-449b-b79a-24312a7a60e2)

 

![WhatsApp Image 2025-05-23 at 20 35 39_0a1c84f6](https://github.com/user-attachments/assets/583fea34-e73b-4939-842b-25bab2de5298)

 





---

## 🔐 Authentication & Authorization

🚀 The API now ships with a complete **JWT-based authentication and authorization layer** powered by Spring Security 6. All product and student endpoints are protected; clients must register, login, and present a Bearer token on every subsequent request.

---

### 🛡 Security Model Overview

- ✅ **Stateless** authentication via signed **JSON Web Tokens (JWT)** — no server-side session storage, no `JSESSIONID` cookie
- ✅ **Bearer token** delivery via the standard `Authorization` HTTP header (`Authorization: Bearer <token>`)
- ✅ **Role-Based Access Control (RBAC)** with two roles: `ROLE_USER` and `ROLE_ADMIN`
- ✅ The `ROLE_` prefix is **mandatory** per Spring Security convention — Spring auto-prepends `ROLE_` when evaluating `hasRole()` SpEL expressions, so use `hasRole('USER')` (NOT `hasRole('ROLE_USER')`) inside `@PreAuthorize`
- ✅ All passwords are stored as **BCrypt hashes** (default strength 10) — plain-text passwords are NEVER persisted
- ✅ Clients re-authenticate on every request via the token; expired or tampered tokens are rejected with HTTP 401
- ✅ Token signing uses **HMAC-SHA-256 (HS256)** with a Base64-encoded secret of at least 256 bits (32 bytes) per RFC 7518 §3.2

---

### 🌍 Public Endpoints (No Authentication Required)

| Method | Endpoint                | Description                                |
|--------|-------------------------|--------------------------------------------|
| POST   | `/api/auth/register`    | Register a new user                        |
| POST   | `/api/auth/login`       | Authenticate and receive a JWT             |
| GET    | `/v3/api-docs/**`       | OpenAPI 3.0 JSON documentation             |
| GET    | `/swagger-ui/**`        | Swagger UI (interactive API explorer)      |
| GET    | `/swagger-ui.html`      | Swagger UI entry page                      |

---

### 🔒 Protected Endpoints (JWT Bearer Token Required)

All previously-anonymous endpoints now require a valid JWT in the `Authorization` header:

- 🛒 All `/product/*` endpoints (10 endpoints under `ProductController`)
- 🎓 All `/student/*` endpoints (2 endpoints under `StudentController`)

#### 🧑‍🤝‍🧑 Role-Based Authorization Rules

| Operation Type        | Endpoints                                                                                                                                                                                                                                | Required Role               |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| **Read**              | `GET /product/getTodayDate`, `GET /product/findAllProduct`, `GET /product/getProduct/{id}`, `GET /product/getProductByName/{name}`, `GET /product/getProductByPrice/{price}`, `GET /student/getTodayDate`, `POST /student/addition/{a1}/{b1}`        | `ROLE_USER` or `ROLE_ADMIN` |
| **Write / Delete**    | `POST /product/saveProduct`, `POST /product/saveProducts`, `PUT /product/updateProduct/{id}`, `PUT /product/{id}`, `DELETE /product/deleteProductByPrice/{price}`                                                                | `ROLE_ADMIN` only           |

- ❌ Unauthorized requests (no token, expired token, invalid signature) receive **HTTP 401 Unauthorized**
- ⛔ Authenticated requests with insufficient role permissions receive **HTTP 403 Forbidden**

📌 **Notes on the two `updateProduct` overloads** — both operate on the same `Product` entity payload but differ in response shape:
- `PUT /product/updateProduct/{id}` returns the wrapped `ResponseStructure<Product>` envelope (matches the existing pre-security CRUD contract).
- `PUT /product/{id}` returns a `ResponseEntity<Product>` with bare-product body and HTTP-status semantics (HTTP 200 on success, HTTP 404 if the id is unknown).
Both endpoints require `ROLE_ADMIN` and accept a full `Product` JSON body.

---

### 📝 Example: Register a New User

```bash
curl -X POST http://localhost:8090/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123","role":["user"]}'
```

📌 **Notes**:
- The `role` field is **optional**. If omitted, the user is automatically assigned `ROLE_USER`.
- To create an admin, send `"role":["admin"]` (the lowercase strings `"user"` and `"admin"` are mapped to `ROLE_USER` / `ROLE_ADMIN` by `AuthService.register`).
- Multiple roles can be combined: `"role":["user","admin"]`.

**Successful response** (HTTP 200):

```json
{ "message": "User registered successfully!" }
```

---

### 🔑 Example: Login and Receive a JWT

```bash
curl -X POST http://localhost:8090/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123"}'
```

**Successful response** (HTTP 200):

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSIsImlhdCI6MTcxNDQwMDAwMCwiZXhwIjoxNzE0NDg2NDAwfQ...",
  "type": "Bearer",
  "id": 1,
  "username": "alice",
  "email": "alice@example.com",
  "roles": ["ROLE_USER"]
}
```

The response contains a JWT in the `token` field along with the authenticated user's `id`, `username`, `email`, and `roles`.

---

### 🚀 Example: Authenticated Request to a Protected Endpoint

Replace `<token>` with the JWT value received from the login endpoint:

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8090/product/findAllProduct
```

For an admin-only operation:

```bash
curl -X POST http://localhost:8090/product/saveProduct \
  -H "Authorization: Bearer <admin-token>" \
  -H 'Content-Type: application/json' \
  -d '{"id":1,"name":"Pen","price":10.5}'
```

For the `/student/addition/{a1}/{b1}` endpoint (note: it is `POST`, not `GET`, and accepts the two operands as path variables; the response body is the integer sum, e.g. `8`):

```bash
curl -X POST -H "Authorization: Bearer <token>" \
  http://localhost:8090/student/addition/5/3
```

---

### ⚙ Configuration Properties

The following `jwt.*` properties are added to `src/main/resources/application.properties`:

```properties
# JWT
jwt.secret=ZGV2LWp3dC1zZWNyZXQta2V5LXJlcGxhY2UtaW4tcHJvZHVjdGlvbi13aXRoLTI1NmJpdC1obWFjLXNoYS0yNTYta2V5
jwt.expiration=86400000
jwt.header=Authorization
jwt.prefix=Bearer 
```

| Property         | Description                                                                                                                                            | Default              |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| `jwt.secret`     | Base64-encoded HMAC-SHA-256 key (minimum 256 bits / 32 bytes per RFC 7518 §3.2). ⚠️ **Development placeholder — override in production.**              | (placeholder)        |
| `jwt.expiration` | Token lifetime in milliseconds.                                                                                                                        | `86400000` (24 hrs)  |
| `jwt.header`     | HTTP header name carrying the bearer token.                                                                                                            | `Authorization`      |
| `jwt.prefix`     | Bearer-token prefix (note the trailing space).                                                                                                         | `Bearer `            |

---

### ⚠️ Production Hardening (CRITICAL Security Warning)

> **⚠️ WARNING**: The default configuration is intended for **local development only**. Production deployments **MUST** apply the following hardening measures.

#### 🔐 JWT Secret

- Production deployments **MUST** override `jwt.secret` via the environment variable `JWT_SECRET`. Spring Boot maps the `JWT_SECRET` env-var to the `jwt.secret` property automatically (Relaxed Binding).
- Alternative: store the secret in an external secret manager (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager).
- Generate a fresh 256-bit key and start the application:

  ```bash
  export JWT_SECRET=$(openssl rand -base64 32)
  java -jar target/spring-boot-simple-crud-with-mysql-0.0.1-SNAPSHOT.jar
  ```

- 🚫 **Never commit production secrets to source control.** Never deploy with the default development placeholder.

#### 🔒 TLS / HTTPS

- The application listens on **plain HTTP (port 8090)** by default.
- Production deployments **MUST terminate TLS** at an upstream reverse proxy (nginx, AWS ALB, Traefik, HAProxy, Caddy, etc.) because plain-HTTP transport of `Authorization: Bearer <token>` exposes tokens to **network sniffing, man-in-the-middle attacks, and replay attacks**.

#### 🌐 CORS

- The default CORS posture is permissive (`*` origins) to simplify development and Swagger UI usage.
- Production deployments **should restrict** `allowedOrigins` to known frontend hostnames (e.g., `https://app.example.com`) and avoid combining `*` origins with `allowCredentials=true` (Spring Security 6 rejects this combination at startup).

#### ⏱ Token Expiration

- The default 24-hour token lifetime (`jwt.expiration=86400000`) is suitable for development.
- Production deployments may consider **shorter expiration** (e.g., 1 hour = `3600000`) combined with a refresh-token strategy. Note: refresh-token issuance is **out of scope** for this implementation; clients must re-authenticate via `/api/auth/login` when the access token expires.

#### 🛑 Additional Recommendations

- Add **rate limiting** at the API gateway (e.g., `bucket4j-spring-boot-starter`, AWS API Gateway throttling) to mitigate brute-force login attacks.
- Enable **request logging** and **audit trails** in production environments.
- Rotate the JWT secret periodically; existing tokens become invalid on rotation, forcing clients to re-authenticate.
- Consider deploying behind an API Gateway (Spring Cloud Gateway, Kong, AWS API Gateway) for centralized rate limiting, IP allow-listing, and observability.

---

### 🧪 Quick Smoke Test

After starting the application, run the following sequence to verify the security layer end-to-end:

```bash
# 1. Verify protected endpoints reject unauthenticated requests (expect HTTP 401)
curl -i http://localhost:8090/product/findAllProduct

# 2. Register a new admin user
curl -X POST http://localhost:8090/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123","role":["admin"]}'

# 3. Login and capture the token
TOKEN=$(curl -s -X POST http://localhost:8090/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# 4. Call a protected endpoint with the token (expect HTTP 200)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8090/product/findAllProduct
```

---

### 📚 Component Reference

| Component                          | Package                                                                | Responsibility                                          |
|------------------------------------|------------------------------------------------------------------------|---------------------------------------------------------|
| `SecurityConfig`                   | `com.jspider.spring_boot_simple_crud_with_mysql.security`              | Spring Security filter chain configuration              |
| `JwtUtils`                         | `com.jspider.spring_boot_simple_crud_with_mysql.security.jwt`          | JWT generation, parsing, validation                     |
| `AuthTokenFilter`                  | `com.jspider.spring_boot_simple_crud_with_mysql.security.jwt`          | Per-request JWT extraction and SecurityContext setup    |
| `AuthEntryPointJwt`                | `com.jspider.spring_boot_simple_crud_with_mysql.security.jwt`          | HTTP 401 JSON response writer                           |
| `UserDetailsServiceImpl`           | `com.jspider.spring_boot_simple_crud_with_mysql.security.services`     | Loads users by username for Spring Security             |
| `UserDetailsImpl`                  | `com.jspider.spring_boot_simple_crud_with_mysql.security.services`     | Spring Security `UserDetails` adapter                   |
| `User`, `Role`, `ERole`            | `com.jspider.spring_boot_simple_crud_with_mysql.entity`                | JPA entities for the identity domain                    |
| `UserRepository`, `RoleRepository` | `com.jspider.spring_boot_simple_crud_with_mysql.repository`            | Spring Data JPA repositories                            |
| `AuthService`                      | `com.jspider.spring_boot_simple_crud_with_mysql.service`               | Registration and login orchestration                    |
| `AuthController`                   | `com.jspider.spring_boot_simple_crud_with_mysql.controller`            | REST endpoints for `/api/auth/*`                        |
| `GlobalExceptionHandler`           | `com.jspider.spring_boot_simple_crud_with_mysql.exception`             | `@RestControllerAdvice`-based error translation         |
| `LoginRequest`, `SignupRequest`    | `com.jspider.spring_boot_simple_crud_with_mysql.payload.request`       | Request DTOs                                            |
| `JwtResponse`, `MessageResponse`   | `com.jspider.spring_boot_simple_crud_with_mysql.payload.response`      | Response DTOs                                           |

---

### 🔄 Authentication Flow

1. **Register** → `POST /api/auth/register` with `{username, email, password, role}` → BCrypt-hashed password persisted to `users` table → `MessageResponse` returned.
2. **Login** → `POST /api/auth/login` with `{username, password}` → Spring Security validates credentials via `DaoAuthenticationProvider` → `JwtUtils` issues a signed JWT → `JwtResponse` returned with `{token, type, id, username, email, roles}`.
3. **Authenticated Request** → Client sends `Authorization: Bearer <token>` → `AuthTokenFilter` validates token → `SecurityContextHolder` populated with `UsernamePasswordAuthenticationToken` → `@PreAuthorize` checks role → controller method executes.
4. **Token Expiration** → Server rejects expired tokens with HTTP 401 → Client must re-authenticate via `/api/auth/login` to obtain a fresh token.

---

### 📖 Interactive API Documentation

Once the application is running, the OpenAPI / Swagger UI page is publicly accessible at:

- **Swagger UI**: [http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8090/v3/api-docs](http://localhost:8090/v3/api-docs)

The Swagger UI exposes an **🔒 Authorize** button (configured via `@SecurityScheme(name = "bearerAuth", ...)` on `SpringBootSimpleCrudWithMysqlApplication`). Click it, paste your JWT, and all subsequent "Try it out" requests against `/product/*` and `/student/*` endpoints automatically include the `Authorization: Bearer <token>` header.

---

