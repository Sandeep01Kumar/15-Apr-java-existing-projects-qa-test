# 🐍 Product CRUD API — Python (Flask) Implementation

A **Python 3 + Flask 3** port of the Spring Boot service in the parent
`src/` directory. This implementation preserves byte-for-byte parity with
the Java/Spring Boot baseline: every endpoint, every payload, every status
code, and every observable side effect is reproduced exactly.

> **Artifact coordinates:** `spring-boot-simple-crud-with-mysql-py`
> version `0.0.1-SNAPSHOT` (mirrors the Maven `groupId:artifactId:version`
> of the Java POM at `EP-Spring-Boot--main/pom.xml`).

---

## 🎯 Behavioral Parity Contract

The Python Flask service is a **one-for-one structural and behavioral port**
of the Spring Boot service. The two implementations are interchangeable
from a client's perspective — clients pointed at port 8090 receive the
same responses regardless of which implementation is running.

The **same OpenAPI document** is served:

- Title: `Product-Crud-Operation`
- Version: `1.0.0`
- Description: `we perform crud operartion with mysql db` *(typo `operartion` preserved verbatim per parity contract)*
- Contact URL: `https://www.w3schools.com/`

The application name and bind port mirror the Java
`application.properties` file:

- App name: `spring-boot-simple-crud-with-mysql`
- Server port: `8090`

---

## 🚀 Quick Start

### 1. Create and activate a virtual environment

```bash
cd EP-Spring-Boot--main/python_app
python -m venv .venv

# On Linux / macOS
source .venv/bin/activate

# On Windows (cmd.exe)
.venv\Scripts\activate.bat

# On Windows (PowerShell)
.venv\Scripts\Activate.ps1
```

### 2. Install dependencies

```bash
pip install -r requirements.txt
```

### 3. Configure the database

```bash
cp .env.example .env
# Edit `.env` and set SQLALCHEMY_DATABASE_URI to point at your MySQL server.
# For local dev without MySQL, use the SQLite fallback (see .env.example).
```

Or just export the environment variable directly in your shell:

```bash
export SQLALCHEMY_DATABASE_URI="mysql+pymysql://user:password@localhost:3306/product_db"
# or, for local SQLite-backed dev:
export SQLALCHEMY_DATABASE_URI="sqlite:///./products.db"
```

> The Python rewrite does **not** introduce schema-migration tooling
> (Alembic is intentionally out of scope per AAP §0.3.2). Schema
> management remains an operator responsibility, exactly as in the Java
> baseline. SQLAlchemy will create the `product` table on first launch
> when the URI points at an empty database (via `db.create_all()` inside
> `create_app()`), but production schemas should be managed externally.

### 4. Run the server

```bash
python server.py
```

You should see this banner on stdout immediately at startup:

```
All Right Sudhir...........
```

The server binds to port **8090** by default (matching `server.port=8090`
in the Java `application.properties`).

Once the server is running you can browse the auto-generated Swagger UI
at the path configured by `OPENAPI_SWAGGER_UI_PATH` in `config.py` to
explore and exercise the same OpenAPI document the Java implementation
serves via `springdoc-openapi`.

---

## 📦 API Endpoints

The Python implementation exposes the same twelve endpoints as the Java
service. Endpoints under `/product/*` have CORS enabled (matching the Java
`@CrossOrigin` annotation on `ProductController`); endpoints under
`/student/*` have NO CORS (matching the Java `StudentController`).

| Method | Endpoint                                  | Description |
|--------|-------------------------------------------|-------------|
| GET    | `/product/getTodayDate`                   | Returns today's date with a **trailing space character** (parity quirk) |
| POST   | `/product/saveProduct`                    | Save a single product. Returns the `ResponseStructure` envelope (HTTP 200 success / 406 if null) |
| POST   | `/product/saveProducts`                   | Bulk-save a list of products. Returns the saved list |
| GET    | `/product/findAllProduct`                 | Return all products (unbounded — no pagination) |
| GET    | `/product/getProduct/{id}`                | Get product by id. **HTTP 200 with body `null` on miss** (parity quirk) |
| GET    | `/product/getProductByName/{name}`        | Get products matching exact name |
| GET    | `/product/getProductByPrice/{price}`      | Get products matching exact price (uses native SQL) |
| DELETE | `/product/deleteProductByPrice/{price}`   | Delete products by price (uses native SQL + transaction) |
| PUT    | `/product/updateProduct/{id}`             | Update with `ResponseStructure` envelope. **HTTP 500 on missing id** (parity quirk F-009) |
| PUT    | `/product/{id}`                           | Update without envelope. **HTTP 404 on missing id** (parity quirk F-010) |
| GET    | `/student/getTodayDate`                   | Today's date with trailing space (no CORS) |
| POST   | `/student/addition/{a1}/{b1}`             | Returns the integer sum `a1 + b1` (no CORS) |

> **Note on the two coexisting update endpoints:** `PUT /product/updateProduct/{id}`
> and `PUT /product/{id}` deliberately diverge in their error-handling
> semantics. The first lets the missing-record exception propagate
> (HTTP 500); the second catches it and returns HTTP 404. Both
> behaviors are preserved verbatim from the Java service per the parity
> contract — see "Preserved Behavioral Quirks" below.

---

## 📋 ResponseStructure Envelope

Two endpoints (`POST /product/saveProduct` and `PUT /product/updateProduct/{id}`)
return responses wrapped in the `ResponseStructure` envelope. The envelope's
JSON shape is identical to the Java side:

```json
{
  "statusCode": 200,
  "apiDescription": "save product Secessfully...",
  "data": {
    "id": 1,
    "name": "Widget",
    "color": "blue",
    "price": 9.99
  }
}
```

The keys `statusCode` and `apiDescription` use camelCase exactly as the
Java side (preserved via marshmallow `data_key=` mappings on
`ResponseStructureSchema`). The `data` field carries the saved/updated
`Product` payload.

The text `save product Secessfully...` (with the typo "Secessfully")
is reproduced verbatim from the Java code per the parity contract. The
matching failure envelope sent on a `null` save result is
`{"statusCode": 406, "apiDescription": "data not saved something went wrong", "data": null}` —
again, preserved verbatim from the Java controller.

The other ten endpoints (including bulk save, find-all, find-by-id,
find-by-name, find-by-price, delete-by-price, the second update endpoint
`PUT /product/{id}`, both `getTodayDate` endpoints, and the student
addition endpoint) return **bare** payloads without the envelope —
matching the Java implementation method-for-method.

---

## 🏗️ Architecture

The Python implementation mirrors the Spring Boot layered architecture
one-for-one. A reader familiar with the Java tree can navigate the
Python tree by the same names:

```
python_app/
├── server.py              # Entry script (the equivalent of `mvn spring-boot:run`)
├── app.py                 # Flask application factory (create_app())
├── config.py              # APP_NAME, SERVER_PORT, OpenAPI metadata, DB URI
├── extensions.py          # SQLAlchemy + flask-smorest + Flask-Cors singletons
├── controller/
│   ├── product_controller.py   # /product/*    (the equivalent of ProductController.java)
│   └── student_controller.py   # /student/*    (the equivalent of StudentController.java)
├── dao/
│   └── product_dao.py          # the equivalent of ProductDao.java
├── repository/
│   └── product_repository.py   # the equivalent of ProductRepository.java
├── entity/
│   └── product.py              # the equivalent of Product.java (SQLAlchemy model)
├── responses/
│   └── response_structure.py   # the equivalent of ResponseStructure.java
├── schemas/
│   ├── product_schema.py       # marshmallow ProductSchema (replaces Lombok @Data + Jackson)
│   └── response_structure_schema.py
└── tests/
    ├── conftest.py             # pytest fixtures (in-memory SQLite test client)
    └── test_app.py             # context-load smoke test (analog of @SpringBootTest)
```

**Layer responsibilities (Java → Python parity map):**

- **Controller layer** — Translates HTTP requests to DAO method calls
  and back. Implemented as `flask_smorest.Blueprint` + `MethodView`
  classes (the structural equivalent of Spring `@RestController`).
- **DAO layer** — `ProductDao` plain Python class containing the
  business-logic adapter methods (`save_product_dao`,
  `get_product_by_id_dao`, `update_product_dao`, etc.). Module-level
  singleton `product_dao = ProductDao()` plays the role of Spring's
  `@Autowired` field injection.
- **Repository layer** — `ProductRepository` wraps SQLAlchemy session
  calls. The three methods derived/native in the Java
  `JpaRepository` (`findByName`, `getProductByPrice`,
  `deleteProductByPrice`) are translated to `db.session.query(...)`
  and `db.session.execute(text("..."), {...})` invocations that emit
  the same SQL.
- **Entity layer** — SQLAlchemy declarative model
  `Product(db.Model)` with `__tablename__ = "product"` and columns
  `id`, `name`, `color`, `price` — names chosen to keep the existing
  native SQL strings executable verbatim against the same physical
  schema.
- **Responses / Schemas** — `ResponseStructure` (`@dataclass`) carries
  the envelope payload; `ProductSchema` and
  `ResponseStructureSchema` (marshmallow) handle JSON serialization
  and OpenAPI documentation generation.

---

## 🌐 Technology Mapping (Java → Python)

| Java/Spring Boot                          | Python/Flask Equivalent                                  |
|-------------------------------------------|----------------------------------------------------------|
| Java 17                                   | Python 3.12 (Flask 3.1 floor is `>=3.9`)                 |
| Spring Boot 3.4.4 + Spring MVC            | Flask 3.1.3                                              |
| Spring Data JPA + Hibernate               | Flask-SQLAlchemy 3.1.1 + SQLAlchemy 2.0.46               |
| `mysql-connector-j`                       | PyMySQL 1.1.1                                            |
| H2 (runtime fallback)                     | SQLite (built into Python; SQLAlchemy `sqlite:///...`)   |
| `springdoc-openapi-starter-webmvc-ui:2.8.6` | flask-smorest 0.47.0 + marshmallow 3.x                 |
| Spring `@CrossOrigin`                     | Flask-Cors 5.0.0                                         |
| Lombok `@Data` + Jackson                  | `@dataclass` + marshmallow Schemas                       |
| JUnit 5 + `@SpringBootTest`               | pytest 8.3.3                                             |
| Maven (`pom.xml`, `mvnw`)                 | `requirements.txt` + `pyproject.toml`                    |
| Embedded Tomcat                           | Werkzeug dev server (development) / gunicorn (prod)      |
| `@Repository` / `@Component` stereotypes  | Plain Python module-level singletons                     |
| `@Autowired` field injection              | Module-level instance imports                            |

---

## ✅ Running Tests

```bash
cd EP-Spring-Boot--main/python_app
SQLALCHEMY_DATABASE_URI="sqlite:///:memory:" pytest tests/ -v
```

The included `test_context_loads` test is the Python analog of the Java
`@SpringBootTest contextLoads()` smoke test (defined in
`SpringBootSimpleCrudWithMysqlApplicationTests.java`). It verifies that
`create_app()` returns a non-`None` Flask instance, exercising the entire
factory pipeline (config load, extension init, blueprint registration,
schema creation) — exactly the way Spring's `@SpringBootTest` annotation
verifies the Spring application context bootstraps without errors.

The `tests/conftest.py` file provides reusable pytest fixtures (`app`,
`client`, in-memory SQLite session) that mirror the way `@SpringBootTest`
auto-wires a fresh test context on the Java side.

---

## 🚢 Production Deployment

By default, `python server.py` uses Werkzeug's development WSGI server,
which is the Python parity-equivalent of Spring Boot's embedded Tomcat in
development mode. **For production deployments**, run behind a real WSGI
server such as **gunicorn**:

```bash
pip install gunicorn
gunicorn --bind 0.0.0.0:8090 --workers 4 'app:create_app()'
```

(Production deployment configuration is documented as an operator
responsibility per AAP §0.7.3 — gunicorn itself is not pinned in
`requirements.txt` to avoid expanding the runtime baseline.)

Other production-grade WSGI servers such as **uWSGI** or **waitress**
(Windows-friendly) are equally valid. None are required by, or assumed
by, the application code itself; the Flask app object exposed by
`create_app()` is a standards-compliant WSGI callable.

> **Operator note:** authentication, authorization, rate limiting,
> structured logging, distributed tracing, metrics endpoints, container
> images, CI/CD pipelines, Helm charts, and Kubernetes manifests are all
> intentionally **out of scope** for this rewrite (AAP §0.3.2). The
> Java baseline does not include them, and the Python rewrite preserves
> the same minimal posture.

---

## 🔍 Preserved Behavioral Quirks (per the Parity Contract)

These observable behaviors look like bugs at first glance but are
**intentional parity preservation** with the Java service. Do NOT "fix"
them — doing so would break the byte-for-byte parity contract that
clients depend on:

1. **`GET /product/getProduct/<unknown_id>` returns HTTP 200 with body `null`** — not 404. This matches the Java `getProductByIdDao` which returns `optional.isPresent() ? optional.get() : null`.
2. **`PUT /product/updateProduct/<unknown_id>` returns HTTP 500** — the controller does NOT wrap the DAO call in `try/except`, so the missing-record exception propagates to a Flask default 500. (Java `RuntimeException` → Spring default 500.)
3. **`PUT /product/<unknown_id>` returns HTTP 404** — the controller DOES wrap the DAO call in `try/except` and returns 404. (Both update endpoints coexist with divergent error semantics.)
4. **`GET /product/getTodayDate` and `GET /student/getTodayDate` return today's date with a TRAILING SPACE character.** The Java code is `LocalDate.now() + " "`.
5. **`/product/*` has CORS enabled; `/student/*` does NOT.** Java `@CrossOrigin` is on `ProductController` only.
6. **The OpenAPI description has the typo `operartion`** (should be "operation"). It's preserved verbatim because changing it alters the externally observable OpenAPI document.
7. **`Product.id` is NOT auto-generated** — clients must supply `id` on writes. The Java entity has `@Id` without `@GeneratedValue`.
8. **`POST /product/saveProduct` and `POST /product/saveProducts` print the request body to stdout** — this matches the Java `System.out.println(product)` and `System.out.println(products)` debug calls in the controller.
9. **The save success message has the typo `save product Secessfully...`** — preserved verbatim. The matching failure message `data not saved something went wrong` is also kept exactly as the Java code emits it.
10. **The native SQL strings `select * from product where price=...` and `delete from product where price=...`** are executed verbatim against the `product` table (parameter style is translated from positional `?` to SQLAlchemy named `:price`, but the SQL semantics are unchanged).
11. **The console banner `All Right Sudhir...........`** (eleven dots — count them) is printed to stdout at startup, mirroring the Java `System.out.println` line in `SpringBootSimpleCrudWithMysqlApplication.main`.
12. **The `findAllProduct` endpoint is unbounded** — it returns every row without pagination, sorting, or filtering. This matches the Java implementation; do NOT add pagination here.

If you find yourself thinking "this can't possibly be intentional" while
reading the code, return to this list. Each item is documented in the
Agent Action Plan that produced this rewrite (sections §0.7.2 and
§0.5.2) and is a deliberate preservation of the Java behavior.

---

## 📚 Further Reading

- **Java source** — `EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/`
- **Java README** — `EP-Spring-Boot--main/README.md`
- **Java configuration** — `EP-Spring-Boot--main/src/main/resources/application.properties`
- **Maven POM** — `EP-Spring-Boot--main/pom.xml`
- **Python entry script** — `EP-Spring-Boot--main/python_app/server.py`
- **Python application factory** — `EP-Spring-Boot--main/python_app/app.py`
- **Python config** — `EP-Spring-Boot--main/python_app/config.py`
- **Python dependencies** — `EP-Spring-Boot--main/python_app/requirements.txt`
- **Sample environment** — `EP-Spring-Boot--main/python_app/.env.example`

---

*This is a parity port. It is intentionally not "Pythonic" where being
Pythonic would diverge from the Java baseline. Read the AAP for the full
list of preserved behaviors and the rationale for each.*

