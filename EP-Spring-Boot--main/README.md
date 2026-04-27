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

## 🐍 Python (Flask) Implementation

The same Product CRUD API is also available as a parallel **Flask 3** application under [`python_app/`](python_app/), providing one-for-one behavioral parity with the Spring Boot service. The Java/Spring Boot implementation continues to live alongside in [`src/`](src/) and remains the reference baseline — both implementations are kept in this repository so a reader can navigate either codebase identically.

### 🔁 Endpoint and Port Parity

The Python implementation exposes the **same twelve endpoints** over the **same TCP port `8090`** with the same OpenAPI document. The OpenAPI metadata is preserved verbatim, including the original typo:

| Field | Value |
|---|---|
| Title | `Product-Crud-Operation` |
| Version | `1.0.0` |
| Description | `we perform crud operartion with mysql db` *(typo `operartion` preserved verbatim)* |
| Contact URL | `https://www.w3schools.com/` |

### 🚀 Quick Start

```bash
cd python_app
python -m venv .venv
# Activate the virtualenv:
#   On Linux/macOS:
source .venv/bin/activate
#   On Windows (cmd):
#   .venv\Scripts\activate.bat
#   On Windows (PowerShell):
#   .venv\Scripts\Activate.ps1
pip install -r requirements.txt
cp .env.example .env
# Edit .env to set SQLALCHEMY_DATABASE_URI to point at your MySQL server
python server.py
```

For the full Python quick-start documentation, configuration details, and the complete endpoint reference, see [`python_app/README.md`](python_app/README.md).

### 📦 Endpoint Reference (Python — same as Java)

| Method | Endpoint | Description |
|---|---|---|
| GET    | `/product/getTodayDate`                  | Returns today's date with a trailing space character (parity preserved) |
| POST   | `/product/saveProduct`                   | Save a single product; returns `ResponseStructure` envelope (200 success / 406 if null) |
| POST   | `/product/saveProducts`                  | Bulk-save a list of products; returns the saved list |
| GET    | `/product/findAllProduct`                | Return all products (unbounded, no pagination) |
| GET    | `/product/getProduct/{id}`               | Returns the product by id; **HTTP 200 with body `null` on miss** (parity quirk) |
| GET    | `/product/getProductByName/{name}`       | Returns matching products by exact name |
| GET    | `/product/getProductByPrice/{price}`     | Returns matching products by exact price (uses native SQL) |
| DELETE | `/product/deleteProductByPrice/{price}`  | Deletes products by price (uses native SQL + transaction) |
| PUT    | `/product/updateProduct/{id}`            | Update with `ResponseStructure` envelope; **HTTP 500 on missing id** (parity quirk F-009) |
| PUT    | `/product/{id}`                          | Update without envelope; **HTTP 404 on missing id** (parity quirk F-010) |
| GET    | `/student/getTodayDate`                  | Returns today's date with trailing space character (no CORS) |
| POST   | `/student/addition/{a1}/{b1}`            | Returns integer sum `a1 + b1` (no CORS) |

### 🧭 Behavioral Parity Notes

The Python implementation reproduces the Java service's observable behaviors byte-for-byte. The following parity quirks are **intentional** and are preserved, not fixed:

- **Trailing-space date echoes** — Both `GET /product/getTodayDate` and `GET /student/getTodayDate` return today's date followed by a single trailing space character.
- **`null`-on-miss read** — `GET /product/getProduct/{id}` returns HTTP 200 with body `null` when the id is not found (it does *not* return HTTP 404).
- **Divergent update endpoints** — `PUT /product/updateProduct/{id}` (F-009) lets the missing-record exception propagate and returns HTTP 500, while `PUT /product/{id}` (F-010) catches the same condition and returns HTTP 404. Both endpoints coexist by design.
- **`ResponseStructure` envelope scope** — The `{ statusCode, apiDescription, data }` response envelope is used by exactly two endpoints, `POST /product/saveProduct` and `PUT /product/updateProduct/{id}`. All other endpoints return bare payloads.
- **CORS scope** — Cross-origin support is enabled only for `/product/*`. The `/student/*` namespace remains CORS-free, exactly mirroring the `@CrossOrigin` annotation placement in the Java codebase.

### 🔄 Technology Mapping

| Java/Spring Boot | Python/Flask Equivalent |
|---|---|
| Spring Boot 3.4.4 + Spring MVC | Flask 3.1.3 |
| Spring Data JPA + Hibernate | Flask-SQLAlchemy 3.1.1 + SQLAlchemy 2.0.x |
| `mysql-connector-j` | PyMySQL 1.1.1 |
| springdoc-openapi 2.8.6 | flask-smorest 0.47.0 + marshmallow |
| Spring `@CrossOrigin` | Flask-Cors |
| Lombok `@Data` + Jackson | `@dataclass` + marshmallow Schemas |
| JUnit 5 + `@SpringBootTest` | pytest |
| Maven (`pom.xml`, `mvnw`) | `requirements.txt` + `pyproject.toml` |

