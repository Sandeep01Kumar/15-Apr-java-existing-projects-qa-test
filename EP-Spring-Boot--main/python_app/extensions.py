"""Module-level singletons for the Flask extensions used by the application.

Pattern: instantiate the extension here without binding to an app, then
call ``extension.init_app(app)`` inside ``create_app()`` (in ``app.py``)
to bind to the actual Flask instance. This is the canonical Flask
factory pattern and the Python equivalent of Spring's ``@Autowired``
field injection - it lets every module import the SAME extension
instance via a simple top-level import, while keeping the actual
binding deferred until app creation.

This pattern is the parity-equivalent of how Spring Boot auto-configures
extensions (e.g., ``JpaAutoConfiguration`` instantiates an
``EntityManagerFactory`` at startup). In the original
``SpringBootSimpleCrudWithMysqlApplication.java``, ``@SpringBootApplication``
triggered the equivalent configuration; in this Python rewrite we make the
construction explicit so that:

1.  Multiple modules (``entity/product.py``, ``controller/*.py``,
    ``dao/product_dao.py``, ``repository/product_repository.py``,
    ``app.py``, etc.) can import the SAME extension instance through a
    single top-level name without introducing circular imports.
2.  Tests can call ``create_app()`` repeatedly with different
    configurations, and each call binds the same module-level
    singletons to a fresh Flask instance via ``init_app(...)``.
3.  No application-domain module needs to be imported here, keeping this
    file at the bottom of the dependency graph.

Exports
-------
db: flask_sqlalchemy.SQLAlchemy
    The ORM/session manager. Provides the declarative base
    (``db.Model``), column types (``db.Column``, ``db.Integer``,
    ``db.String``, ``db.Float``), the request-scoped session
    (``db.session``), and lifecycle hooks (``db.init_app``,
    ``db.create_all``, ``db.drop_all``). Replaces
    ``spring-boot-starter-data-jpa`` + Hibernate from the original
    Spring Boot application.
api: flask_smorest.Api
    The flask-smorest API manager. Owns the auto-generated OpenAPI 3.x
    specification, the bundled Swagger UI page, and Blueprint
    registration. Replaces ``springdoc-openapi-starter-webmvc-ui:2.8.6``
    from the original Spring Boot application.
cors: flask_cors.CORS
    The Flask-Cors extension. Bound in ``app.py`` with the ``resources``
    keyword set to scope CORS handling to ``/product/*`` ONLY - this
    matches the Java ``@CrossOrigin(value = "")`` annotation placement
    on ``ProductController`` (the ``StudentController`` namespace is
    intentionally CORS-free, mirroring the Java code byte-for-byte).
"""

# ---------------------------------------------------------------------------
# External imports.
#
# These three packages are the Python equivalents of:
#   - Spring Data JPA + Hibernate              -> Flask-SQLAlchemy
#   - springdoc-openapi-starter-webmvc-ui      -> flask-smorest
#   - Spring Web @CrossOrigin annotation       -> Flask-Cors
#
# Each is pinned in ``requirements.txt`` (see AAP §0.6.1):
#   Flask-SQLAlchemy==3.1.1
#   flask-smorest==0.47.0
#   Flask-Cors==5.0.0
#
# No application-domain module is imported here - this module sits at
# the bottom of the dependency graph and is imported BY the controllers,
# entities, repositories, and the application factory. Importing any
# of those upward would create a circular dependency at module import
# time.
# ---------------------------------------------------------------------------
from flask_cors import CORS
from flask_smorest import Api
from flask_sqlalchemy import SQLAlchemy


# ---------------------------------------------------------------------------
# SQLAlchemy ORM / session manager.
#
# Constructed without arguments so that no Flask app is required at
# module-import time. The actual binding to a Flask instance happens
# inside ``create_app()`` (see ``app.py``):
#
#     from extensions import db
#     ...
#     def create_app(...):
#         app = Flask(...)
#         db.init_app(app)
#         ...
#
# Once initialised, ``db`` exposes:
#   - ``db.Model``       declarative base for ORM models
#   - ``db.Column``      column constructor
#   - ``db.Integer``,
#     ``db.String``,
#     ``db.Float``       column types used by ``entity/product.py``
#   - ``db.session``     request-scoped Session for queries/transactions
#   - ``db.create_all()`` / ``db.drop_all()`` schema management hooks
#                         used by tests (``conftest.py``)
#
# Replaces ``spring-boot-starter-data-jpa`` + Hibernate from the original
# Spring Boot application. The class-level Spring annotations
# ``@Entity``, ``@Id``, and ``@Repository`` map onto SQLAlchemy's
# declarative ``db.Model`` plus repository-style helpers in
# ``repository/product_repository.py``.
# ---------------------------------------------------------------------------
db = SQLAlchemy()


# ---------------------------------------------------------------------------
# flask-smorest API manager.
#
# Owns the auto-generated OpenAPI 3.x document, the Swagger UI HTML
# page, and Blueprint registration. The OpenAPI metadata (title,
# version, description, contact) lives in ``config.py`` and is loaded
# into ``app.config`` inside ``create_app()`` before
# ``api.init_app(app)`` is called - flask-smorest then reads those
# config keys at initialisation time.
#
# Once initialised, ``api`` exposes:
#   - ``api.init_app(app)``               binds to the Flask instance
#   - ``api.register_blueprint(blp)``     registers a flask_smorest
#                                         Blueprint (the controllers in
#                                         ``controller/*.py``)
#   - ``api.spec``                        the apispec ``APISpec`` object
#                                         (after init_app); used by
#                                         flask-smorest internally to
#                                         emit the OpenAPI JSON
#
# Replaces ``springdoc-openapi-starter-webmvc-ui:2.8.6`` from the
# original Spring Boot application. The springdoc OpenAPI annotations
# (``@OpenAPIDefinition``, ``@Operation``, ``@ApiResponse``, ``@Tag``,
# ``@Schema``) map onto flask-smorest's ``@blp.response``,
# ``@blp.arguments``, ``@blp.alt_response``, and ``@blp.doc``
# decorators plus marshmallow ``Schema`` field metadata.
# ---------------------------------------------------------------------------
api = Api()


# ---------------------------------------------------------------------------
# Flask-Cors extension.
#
# Constructed without arguments so that no Flask app is required at
# module-import time. The actual binding happens inside ``create_app()``
# with a NON-default ``resources`` argument that scopes CORS handling
# to ``/product/*`` ONLY:
#
#     cors.init_app(app, resources={r"/product/*": {"origins": "*"}})
#
# This precisely mirrors the Java ``@CrossOrigin(value = "")``
# annotation, which is placed on ``ProductController`` only. The
# ``StudentController`` namespace (``/student/*``) is intentionally
# CORS-free in the Java implementation, and the Python rewrite
# preserves that asymmetry byte-for-byte (see AAP §0.7.2).
#
# Once initialised, ``cors`` exposes:
#   - ``cors.init_app(app, **kwargs)``    binds to the Flask instance
#                                         and applies the resources
#                                         scope passed in kwargs
# ---------------------------------------------------------------------------
cors = CORS()
