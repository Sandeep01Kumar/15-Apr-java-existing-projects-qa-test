"""Application configuration constants.

This module is the Python equivalent of `application.properties` and
the inline `@OpenAPIDefinition` metadata in
`SpringBootSimpleCrudWithMysqlApplication.java`. It exposes ONLY constants;
no functions, no classes, no side effects beyond the `os.environ.get`
read for `SQLALCHEMY_DATABASE_URI`.

The constants are imported by `app.py` (which copies them into
`Flask.config`) and `server.py` (which reads `SERVER_PORT`).

Constants exposed (in order of declaration):
    APP_NAME                  - Application identity (mirrors
                                spring.application.name).
    SERVER_PORT               - HTTP listener port (mirrors server.port).
    API_TITLE                 - OpenAPI document title.
    API_VERSION               - API version string surfaced in OpenAPI.
    OPENAPI_VERSION           - OpenAPI specification version emitted.
    OPENAPI_URL_PREFIX        - flask-smorest mount point.
    OPENAPI_SWAGGER_UI_PATH   - Path under URL prefix that serves the
                                Swagger UI HTML page.
    OPENAPI_SWAGGER_UI_URL    - CDN base URL for Swagger UI static assets.
    OPENAPI_DESCRIPTION       - OpenAPI description (typo PRESERVED for
                                byte-for-byte parity with the Java side).
    OPENAPI_CONTACT_NAME      - Contact "name" field (empty string).
    OPENAPI_CONTACT_EMAIL     - Contact "email" field (empty string).
    OPENAPI_CONTACT_URL       - Contact "url" field
                                (https://www.w3schools.com/).
    SQLALCHEMY_DATABASE_URI   - Database connection URI; read from the
                                environment variable of the same name with
                                an in-memory SQLite fallback for ergonomic
                                local development.
"""

import os


# ----------------------------------------------------------------------------
# Application identity
# ----------------------------------------------------------------------------
# Mirrors `spring.application.name` from
# EP-Spring-Boot--main/src/main/resources/application.properties (line 1).
APP_NAME = "spring-boot-simple-crud-with-mysql"

# Mirrors `server.port` from
# EP-Spring-Boot--main/src/main/resources/application.properties (line 3).
# Kept as an int (not a string) so Flask's `app.run(port=...)` and Werkzeug
# can consume it directly.
SERVER_PORT = 8090


# ----------------------------------------------------------------------------
# OpenAPI / Swagger UI metadata.
#
# These constants mirror the inline `@OpenAPIDefinition` annotation block
# in
# EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/SpringBootSimpleCrudWithMysqlApplication.java
# (lines 11-22). They are consumed by `app.py` when initialising
# flask-smorest's `Api` instance.
#
# !!! IMPORTANT — TYPO PRESERVATION !!!
# `OPENAPI_DESCRIPTION` contains the misspelling `operartion` (it should
# read "operation"). This is INTENTIONAL and MUST NOT be "corrected".
# Per the parity contract (AAP §0.1.1, §0.7.2), the OpenAPI document is
# part of the externally observable API surface, and any deviation from
# the Java side — even a typo fix — would constitute a behavioral drift.
# ----------------------------------------------------------------------------

# Mirrors `title = "Product-Crud-Operation"` (Java line 13).
API_TITLE = "Product-Crud-Operation"

# Mirrors `version = "1.0.0"` (Java line 15). Stored as a string, not a
# numeric literal — OpenAPI version fields are strings per the spec.
API_VERSION = "1.0.0"

# OpenAPI specification version emitted in the document header. flask-smorest
# defaults to OpenAPI 3.x; we pin 3.0.3 explicitly so the Swagger UI render
# is deterministic and matches the springdoc-openapi 2.8.6 output produced
# by the Java side.
OPENAPI_VERSION = "3.0.3"

# flask-smorest mount point for the auto-generated OpenAPI document. Setting
# this to "/" places the JSON spec at "/openapi.json" and the Swagger UI at
# the path defined by `OPENAPI_SWAGGER_UI_PATH` below.
OPENAPI_URL_PREFIX = "/"

# Path (relative to OPENAPI_URL_PREFIX) at which the Swagger UI HTML page
# is served. Operators visit http://<host>:8090/swagger-ui to browse the
# documented endpoints — the functional equivalent of springdoc-openapi's
# default `/swagger-ui.html`.
OPENAPI_SWAGGER_UI_PATH = "/swagger-ui"

# CDN base URL from which flask-smorest loads the Swagger UI static assets
# (CSS, JS, fonts, icons). Using the jsDelivr CDN avoids vendoring multi-
# megabyte UI assets into the repository.
OPENAPI_SWAGGER_UI_URL = "https://cdn.jsdelivr.net/npm/swagger-ui-dist/"

# Mirrors `description = "we perform crud operartion with mysql db"` from
# Java line 14. The misspelling `operartion` (instead of "operation") is
# PRESERVED VERBATIM per the parity contract — DO NOT CORRECT IT.
OPENAPI_DESCRIPTION = "we perform crud operartion with mysql db"

# Mirrors `name = ""` from Java line 17. Java declares an empty contact
# name; we mirror that exactly (empty string, not None) so the rendered
# OpenAPI JSON contains an empty-string contact name field identical to
# the Java output.
OPENAPI_CONTACT_NAME = ""

# Mirrors `email = ""` from Java line 18. Same rationale as above —
# empty string preserves byte-for-byte JSON output parity.
OPENAPI_CONTACT_EMAIL = ""

# Mirrors `url = "https://www.w3schools.com/"` from Java line 19. The
# trailing slash is intentional (matches the Java string literal exactly).
OPENAPI_CONTACT_URL = "https://www.w3schools.com/"


# ----------------------------------------------------------------------------
# Database — read from the environment, fall back to in-memory SQLite for
# local dev (the Python analog of H2 runtime scope per AAP §0.5.1).
#
# Operators set this to a real MySQL URI in production:
#   SQLALCHEMY_DATABASE_URI=mysql+pymysql://user:password@host:3306/db
# See `.env.example` for the documented options.
#
# The `sqlite:///:memory:` fallback (one colon, no slashes after
# `:memory:`) is the SQLAlchemy URL for an in-memory SQLite database.
# Each connection gets a FRESH database — fine for tests but not suitable
# for any persistent workload. Operators MUST set the environment variable
# for any real use; the fallback is purely so the smoke test does not
# require external setup.
#
# Mirrors the Java side's posture in `application.properties`, which
# deliberately omits `spring.datasource.*` so credentials never live in
# the repository — operators supply them externally for both the Java
# and Python implementations.
# ----------------------------------------------------------------------------
SQLALCHEMY_DATABASE_URI = os.environ.get(
    "SQLALCHEMY_DATABASE_URI",
    "sqlite:///:memory:",
)
