"""Flask application factory.

This module is the Python translation of the Spring Boot bootstrap class
``EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/SpringBootSimpleCrudWithMysqlApplication.java``,
specifically the trifecta provided by the ``@SpringBootApplication``
annotation (``@Configuration`` + ``@EnableAutoConfiguration`` +
``@ComponentScan``) plus the inline ``@OpenAPIDefinition`` block and the
``SpringApplication.run(...)`` boot sequence in ``main``.

In Spring Boot, all of the following happen implicitly the moment
``SpringApplication.run(...)`` is invoked: a Spring ``ApplicationContext`` is
created, ``application.properties`` is loaded, JPA is auto-configured, an
embedded Tomcat is started on the port declared in
``application.properties``, ``@RestController``-annotated classes are
discovered by component-scan and registered as request handlers, and the
``@OpenAPIDefinition`` metadata is copied into the springdoc-generated
OpenAPI document. The Python ecosystem provides no equivalent
auto-configuration mechanism, so this module makes every step explicit
inside the ``create_app(config_object=None)`` factory function:

1.  Instantiate a ``flask.Flask`` instance with the application name
    ``"spring-boot-simple-crud-with-mysql"`` (mirrors
    ``spring.application.name`` in ``application.properties``).
2.  Load every configuration constant from ``config.py`` into
    ``Flask.config`` (mirrors Spring's environment-property binding).
3.  Apply optional overrides from the ``config_object`` parameter (used
    by pytest fixtures to inject ``SQLALCHEMY_DATABASE_URI =
    "sqlite:///:memory:"`` and similar test-only settings).
4.  Initialize the SQLAlchemy ORM via ``db.init_app(app)`` (mirrors
    Spring Data JPA's auto-configured ``EntityManagerFactory``).
5.  Initialize the flask-smorest ``Api`` via ``api.init_app(app)``
    (mirrors springdoc-openapi's auto-configured OpenAPI document
    generation and Swagger UI endpoint).
6.  Initialize Flask-Cors **scoped exclusively to ``/product/*``**
    (mirrors the Java ``@CrossOrigin(value = "")`` annotation, which is
    placed on ``ProductController`` ONLY — ``StudentController`` carries
    no ``@CrossOrigin`` annotation, so the ``/student/*`` namespace
    remains CORS-free byte-for-byte).
7.  Lazily import and register the ``/product/*`` and ``/student/*``
    Blueprints (the Python equivalent of Spring's component-scan finding
    and registering ``@RestController`` classes).
8.  Inside an application context, import the ``Product`` ORM model
    (which registers it with SQLAlchemy's declarative metadata) and
    invoke ``db.create_all()`` (idempotent — creates tables only if
    they don't already exist; no migration tooling is introduced per
    AAP §0.3.2).
9.  Return the configured ``Flask`` app instance.

Lazy-import contract for Blueprints
-----------------------------------
The ``controller/product_controller.py`` and
``controller/student_controller.py`` modules transitively import
``entity/product.py`` (via the DAO and repository), which in turn calls
``db.Model`` from ``extensions``. If the Blueprint imports were placed
at the top of this module, the entity module would execute at import
time and try to attach to a ``db`` instance that has not yet been bound
to a Flask app via ``init_app``. The canonical Flask factory pattern
solves this by deferring Blueprint imports until inside ``create_app``,
where the configuration has been loaded and ``db.init_app(app)`` has
already been called. This module follows that pattern exactly.

CORS-scoping parity (CRITICAL)
------------------------------
The Java code applies ``@CrossOrigin(value = "")`` to
``ProductController`` only (``ProductController.java`` line 29). The
Java ``StudentController`` carries NO ``@CrossOrigin`` annotation. The
Python equivalent that preserves this asymmetry byte-for-byte is::

    cors.init_app(app, resources={r"/product/*": {"origins": "*"}})

Using the default ``cors.init_app(app)`` without the ``resources``
keyword would silently enable CORS on every Blueprint — including
``/student/*`` — and break the parity contract. The ``resources``
dictionary scopes Flask-Cors to the ``/product/*`` URL pattern ONLY;
the ``/student/*`` namespace remains CORS-free, exactly as the Java
side intends.

OpenAPI metadata parity (CRITICAL)
----------------------------------
The Java ``@OpenAPIDefinition`` block (``SpringBootSimpleCrudWithMysqlApplication.java``
lines 11–22) declares::

    title       = "Product-Crud-Operation"
    description = "we perform crud operartion with mysql db"
    version     = "1.0.0"
    contact = {
        name  = "",
        email = "",
        url   = "https://www.w3schools.com/",
    }

These strings are the externally observable OpenAPI document — they
appear on the Swagger UI page, in the ``/openapi.json`` document, and
in any client-side OpenAPI parser output. Per AAP §0.7.2 they MUST be
preserved byte-for-byte, INCLUDING the ``operartion`` typo in
``description`` (a misspelling that SHOULD read "operation" but is
preserved verbatim because correcting it would change the externally
observable API surface). The constants live in ``config.py`` (which
documents the typo preservation in detail); this module simply forwards
them through ``Flask.config`` and the ``API_SPEC_OPTIONS`` dictionary
so flask-smorest reads them at API initialization time.

Module-import contract
----------------------
Per the established codebase convention (see ``extensions.py``,
``config.py``, ``entity/product.py``, ``dao/product_dao.py``,
``repository/product_repository.py``, ``schemas/product_schema.py``,
``responses/response_structure.py``, ``controller/product_controller.py``,
``controller/student_controller.py``, ``tests/test_app.py``), imports
use SIMPLE module names (``from config import APP_NAME``) NOT a
``python_app.`` package prefix. The runtime entry point ``server.py``
runs from the ``EP-Spring-Boot--main/python_app/`` working directory
and the pytest configuration in ``pyproject.toml`` does the same, so
bare module names resolve correctly via ``sys.path``.

The Sk-21-Rule (``# SK-QA`` tail comment on every line) is scoped to
``server.py`` ONLY and does NOT apply to this module per AAP §0.7.1.
No tail-comment markers appear anywhere in this file.

Exports
-------
create_app : function
    The Flask application factory. Called by ``server.py`` at module
    load time (``app = create_app()``) to produce the WSGI application
    instance, and called by the pytest ``app`` fixture (in
    ``tests/conftest.py``) with a ``config_object`` override that swaps
    the ``SQLALCHEMY_DATABASE_URI`` to ``sqlite:///:memory:`` for
    isolated, ephemeral test databases.
"""

# ---------------------------------------------------------------------------
# External imports.
#
# Only ONE external symbol is imported at the top of this module: the
# ``Flask`` class itself. The other Flask extensions (SQLAlchemy,
# flask-smorest, Flask-Cors) are accessed via their module-level
# singletons declared in ``extensions.py`` — that module sits at the
# bottom of the dependency graph and is the canonical location where
# these extensions are constructed (uninitialized) for later binding
# via ``init_app(...)`` inside this factory.
#
# Pinned at Flask==3.1.3 in ``requirements.txt`` (per AAP §0.6.1).
# ---------------------------------------------------------------------------
from flask import Flask

# ---------------------------------------------------------------------------
# Application-internal imports — configuration constants.
#
# Per the established codebase convention, imports use SIMPLE module
# names (``from config import ...``) NOT a ``python_app.`` package
# prefix. The runtime entry point ``server.py`` runs from the
# ``EP-Spring-Boot--main/python_app/`` working directory and pytest is
# configured (in ``pyproject.toml``) to do the same, so bare module
# names resolve correctly via ``sys.path``.
#
# The full list of constants imported here (every name documented in
# the schema's ``internal_imports`` block for ``config.py``):
#
#   * ``APP_NAME``                — applied to the ``Flask(...)``
#                                    constructor as the application
#                                    name. Mirrors ``spring.application.name``
#                                    from ``application.properties``.
#   * ``SERVER_PORT``             — imported for parity with the schema's
#                                    declared internal_imports list. The
#                                    actual binding to the Werkzeug
#                                    development server happens in
#                                    ``server.py`` (which calls
#                                    ``app.run(port=SERVER_PORT)``).
#                                    This factory does NOT bind to a
#                                    socket — it only constructs the
#                                    ``Flask`` instance.
#   * ``API_TITLE``               — copied into ``app.config["API_TITLE"]``;
#                                    flask-smorest reads it as the
#                                    OpenAPI document title.
#   * ``API_VERSION``             — copied into ``app.config["API_VERSION"]``;
#                                    flask-smorest reads it as the
#                                    OpenAPI document version.
#   * ``OPENAPI_VERSION``         — copied into
#                                    ``app.config["OPENAPI_VERSION"]``;
#                                    flask-smorest reads it as the
#                                    OpenAPI specification version
#                                    (e.g., ``"3.0.3"``).
#   * ``OPENAPI_URL_PREFIX``      — copied into
#                                    ``app.config["OPENAPI_URL_PREFIX"]``;
#                                    flask-smorest mounts the OpenAPI
#                                    document and Swagger UI under this
#                                    URL prefix.
#   * ``OPENAPI_SWAGGER_UI_PATH`` — copied into
#                                    ``app.config["OPENAPI_SWAGGER_UI_PATH"]``;
#                                    relative path (under
#                                    ``OPENAPI_URL_PREFIX``) where the
#                                    Swagger UI HTML page is served.
#   * ``OPENAPI_SWAGGER_UI_URL``  — copied into
#                                    ``app.config["OPENAPI_SWAGGER_UI_URL"]``;
#                                    CDN base URL for the Swagger UI
#                                    static assets (CSS/JS/fonts).
#   * ``SQLALCHEMY_DATABASE_URI`` — copied into
#                                    ``app.config["SQLALCHEMY_DATABASE_URI"]``;
#                                    consumed by Flask-SQLAlchemy when
#                                    ``db.init_app(app)`` runs.
#   * ``OPENAPI_DESCRIPTION``     — placed inside the nested
#                                    ``API_SPEC_OPTIONS["info"]["description"]``
#                                    field. flask-smorest renders this
#                                    in the Swagger UI page header and
#                                    the ``/openapi.json`` document. Per
#                                    AAP §0.7.2 the typo
#                                    (``operartion``) is preserved
#                                    verbatim.
#   * ``OPENAPI_CONTACT_NAME``    — placed inside
#                                    ``API_SPEC_OPTIONS["info"]["contact"]["name"]``.
#   * ``OPENAPI_CONTACT_EMAIL``   — placed inside
#                                    ``API_SPEC_OPTIONS["info"]["contact"]["email"]``.
#   * ``OPENAPI_CONTACT_URL``     — placed inside
#                                    ``API_SPEC_OPTIONS["info"]["contact"]["url"]``.
# ---------------------------------------------------------------------------
from config import (
    API_TITLE,
    API_VERSION,
    APP_NAME,
    OPENAPI_CONTACT_EMAIL,
    OPENAPI_CONTACT_NAME,
    OPENAPI_CONTACT_URL,
    OPENAPI_DESCRIPTION,
    OPENAPI_SWAGGER_UI_PATH,
    OPENAPI_SWAGGER_UI_URL,
    OPENAPI_URL_PREFIX,
    OPENAPI_VERSION,
    SERVER_PORT,  # noqa: F401  (re-exported via config; consumed by server.py)
    SQLALCHEMY_DATABASE_URI,
)

# ---------------------------------------------------------------------------
# Application-internal imports — extension singletons.
#
#   * ``db``    — Flask-SQLAlchemy instance. Provides ``db.Model`` (the
#                 declarative base used by ``entity/product.py``),
#                 ``db.session`` (the request-scoped session used by
#                 the DAO and repository), ``db.init_app(app)`` (binds
#                 the extension to the Flask app), and
#                 ``db.create_all()`` (creates all registered tables).
#   * ``api``   — flask-smorest ``Api`` instance. Provides
#                 ``api.init_app(app)`` (binds the extension to the
#                 Flask app) and ``api.register_blueprint(blp)``
#                 (registers a flask-smorest Blueprint with the OpenAPI
#                 spec).
#   * ``cors``  — Flask-Cors ``CORS`` instance. Provides
#                 ``cors.init_app(app, resources={...})`` which scopes
#                 CORS handling to the URL patterns supplied in the
#                 resources dictionary. We pass
#                 ``resources={r"/product/*": {"origins": "*"}}`` to
#                 mirror the Java ``@CrossOrigin(value = "")`` placement
#                 on ``ProductController`` only.
#
# These three singletons are constructed (uninitialized) at module
# import time in ``extensions.py``. Binding them to the actual Flask
# app happens here in ``create_app(...)`` via the ``init_app(...)``
# methods. This is the canonical Flask factory pattern and the Python
# equivalent of Spring's auto-wired extension beans.
# ---------------------------------------------------------------------------
from extensions import api, cors, db


def create_app(config_object=None):
    """Construct and configure a fully-wired ``Flask`` application instance.

    This factory is the Python translation of the Spring Boot bootstrap
    sequence performed by ``SpringApplication.run(SpringBootSimpleCrudWithMysqlApplication.class, args)``
    in ``SpringBootSimpleCrudWithMysqlApplication.java`` line 26. Spring's
    invocation triggers context creation, property loading, JPA
    auto-configuration, embedded Tomcat startup, component-scan
    discovery of ``@RestController`` classes, and OpenAPI metadata
    binding — all implicitly. The Python ecosystem requires each step
    to be made explicit; this function lays them out in the order:

    1.  Construct the ``Flask`` instance with ``APP_NAME``.
    2.  Load configuration from ``config.py`` into ``Flask.config``.
    3.  Apply ``config_object`` overrides (test fixtures use this to
        inject a SQLite-in-memory database URI).
    4.  Initialize the SQLAlchemy ORM (``db.init_app(app)``).
    5.  Initialize flask-smorest's ``Api`` (``api.init_app(app)``).
    6.  Initialize Flask-Cors scoped to ``/product/*`` ONLY
        (``cors.init_app(app, resources={...})``).
    7.  Lazily import the Product/Student Blueprints and register them
        with the ``Api``. Lazy imports break the circular dependency
        between this factory and the controller modules (which import
        the entity, which imports ``db`` from ``extensions``).
    8.  Inside an application context, import the ``Product`` ORM model
        (registers it with SQLAlchemy's declarative metadata) and
        invoke ``db.create_all()`` (idempotent — creates tables only
        if they don't already exist).
    9.  Return the configured ``Flask`` instance.

    Parameters
    ----------
    config_object : dict, optional
        A dictionary of configuration overrides applied AFTER the
        ``config.py`` constants are loaded. Used primarily by pytest
        fixtures to inject test-only settings, e.g.
        ``create_app({"SQLALCHEMY_DATABASE_URI": "sqlite:///:memory:",
        "TESTING": True})``. If ``None`` (the default), no overrides
        are applied and the constants from ``config.py`` are used
        as-is. Acceptable types are anything that ``Flask.config.update``
        accepts (a ``dict``, a ``Config`` object, or any mapping).

    Returns
    -------
    flask.Flask
        The fully configured Flask application instance with all
        extensions initialized, both Blueprints registered, and the
        SQLAlchemy schema materialized via ``db.create_all()``. The
        return value is NEVER ``None`` — the function either returns a
        valid Flask instance or raises an exception during one of the
        configuration / initialization steps.

    Raises
    ------
    Any exception raised by Flask, Flask-SQLAlchemy, flask-smorest, or
    Flask-Cors during their respective ``init_app(...)`` calls. The
    factory does NOT swallow exceptions — failure to bootstrap is
    surfaced directly to the caller (mirroring Spring Boot's behavior
    where a context-bootstrap failure aborts the JVM with a stack
    trace).

    Examples
    --------
    Production-style construction (used by ``server.py``)::

        from app import create_app
        app = create_app()
        app.run(host="0.0.0.0", port=SERVER_PORT)

    Test-style construction (used by the pytest ``app`` fixture in
    ``tests/conftest.py``)::

        from app import create_app
        app = create_app({
            "SQLALCHEMY_DATABASE_URI": "sqlite:///:memory:",
            "TESTING": True,
        })
        with app.app_context():
            # SQLAlchemy schema is already materialized by create_all()
            ...
    """
    # -----------------------------------------------------------------
    # Step 1: Instantiate the Flask application.
    #
    # The ``Flask(import_name)`` constructor takes a module / package
    # name used by Flask to locate static files, templates, and the
    # logger. We pass ``APP_NAME`` (the literal string
    # ``"spring-boot-simple-crud-with-mysql"``) — this matches the
    # Java ``spring.application.name`` property byte-for-byte and
    # surfaces in the Flask logger output and any error pages
    # generated by the framework.
    # -----------------------------------------------------------------
    app = Flask(APP_NAME)

    # -----------------------------------------------------------------
    # Step 2: Load core configuration into Flask.config.
    #
    # ``Flask.config`` is a ``flask.Config`` instance (a subclass of
    # ``dict`` with helpers for loading from environment, files, etc.).
    # We populate it with the constants from ``config.py`` so that:
    #
    #   * Flask-SQLAlchemy reads ``SQLALCHEMY_DATABASE_URI`` and
    #     ``SQLALCHEMY_TRACK_MODIFICATIONS`` to construct the engine.
    #   * flask-smorest reads ``API_TITLE``, ``API_VERSION``,
    #     ``OPENAPI_VERSION``, ``OPENAPI_URL_PREFIX``,
    #     ``OPENAPI_SWAGGER_UI_PATH``, and ``OPENAPI_SWAGGER_UI_URL``
    #     to configure the OpenAPI document generator and the
    #     Swagger UI HTML page.
    #   * The nested ``API_SPEC_OPTIONS`` dict provides values for
    #     OpenAPI fields that have no flat config-key equivalent
    #     (specifically ``info.description`` and ``info.contact``).
    #
    # The ``SQLALCHEMY_TRACK_MODIFICATIONS`` flag is set to ``False``
    # to suppress the deprecation warning that Flask-SQLAlchemy emits
    # when this flag is not explicitly configured. We do not need the
    # tracking machinery (it is used for the Flask-SQLAlchemy
    # signal-emitting feature, which we don't use here); disabling it
    # also has a small performance benefit.
    # -----------------------------------------------------------------
    app.config["SQLALCHEMY_DATABASE_URI"] = SQLALCHEMY_DATABASE_URI
    app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
    app.config["API_TITLE"] = API_TITLE
    app.config["API_VERSION"] = API_VERSION
    app.config["OPENAPI_VERSION"] = OPENAPI_VERSION
    app.config["OPENAPI_URL_PREFIX"] = OPENAPI_URL_PREFIX
    app.config["OPENAPI_SWAGGER_UI_PATH"] = OPENAPI_SWAGGER_UI_PATH
    app.config["OPENAPI_SWAGGER_UI_URL"] = OPENAPI_SWAGGER_UI_URL

    # -----------------------------------------------------------------
    # OpenAPI ``info`` block — preserves the Java @OpenAPIDefinition
    # metadata verbatim, INCLUDING the ``operartion`` typo per AAP
    # §0.1.1 / §0.7.2. flask-smorest exposes a special config key
    # ``API_SPEC_OPTIONS`` whose contents are merged into the OpenAPI
    # spec's top-level dictionary. We populate the ``info`` sub-object
    # with ``description`` and ``contact`` (the two fields that have
    # no flat ``API_*`` config-key equivalent).
    #
    # The ``info.title`` and ``info.version`` fields come from the flat
    # config keys ``API_TITLE`` and ``API_VERSION`` set above; we do
    # NOT duplicate them here because flask-smorest would issue a
    # warning about conflicting metadata sources.
    #
    # The contact name and email are intentionally empty strings (NOT
    # ``None``) because the Java ``@Contact`` annotation declares
    # ``name = ""`` and ``email = ""`` (literal empty strings); the
    # rendered OpenAPI JSON consequently contains
    # ``"contact": {"name": "", "email": "", "url": "https://www.w3schools.com/"}``.
    # Substituting ``None`` for the empty strings would change the JSON
    # output (the keys would be omitted entirely or set to
    # ``null``), breaking byte-for-byte parity with the Java side.
    # -----------------------------------------------------------------
    app.config["API_SPEC_OPTIONS"] = {
        "info": {
            "description": OPENAPI_DESCRIPTION,
            "contact": {
                "name": OPENAPI_CONTACT_NAME,
                "email": OPENAPI_CONTACT_EMAIL,
                "url": OPENAPI_CONTACT_URL,
            },
        }
    }

    # -----------------------------------------------------------------
    # Step 3: Apply optional configuration overrides.
    #
    # This is where the pytest ``app`` fixture in
    # ``tests/conftest.py`` injects test-only settings such as
    # ``SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"`` and
    # ``TESTING = True``. Production code (``server.py``) calls
    # ``create_app()`` with no argument, so this branch is skipped.
    #
    # ``Flask.config.update(mapping)`` accepts any mapping (dict,
    # Config, etc.) and overlays its keys/values onto the existing
    # config. Keys NOT present in ``config_object`` retain their
    # values from ``config.py`` (above), so a partial override is
    # safe — tests typically override only ``SQLALCHEMY_DATABASE_URI``
    # and leave the OpenAPI metadata untouched.
    # -----------------------------------------------------------------
    if config_object is not None:
        app.config.update(config_object)

    # -----------------------------------------------------------------
    # Step 4: Initialize SQLAlchemy.
    #
    # ``db.init_app(app)`` registers the Flask-SQLAlchemy extension on
    # the app, reads ``SQLALCHEMY_DATABASE_URI`` from ``app.config``,
    # and constructs the SQLAlchemy engine + session factory. After
    # this call, the ``db.session`` proxy resolves to a request-scoped
    # session whenever the app is in an active request or app context.
    #
    # The Java equivalent is Spring Boot auto-configuring an
    # ``EntityManagerFactory`` when ``spring-boot-starter-data-jpa`` is
    # on the classpath; the property binding from
    # ``application.properties`` flows through Spring's
    # ``Environment`` abstraction.
    # -----------------------------------------------------------------
    db.init_app(app)

    # -----------------------------------------------------------------
    # Step 5: Initialize flask-smorest.
    #
    # ``api.init_app(app)`` registers the flask-smorest extension on
    # the app, reads ``API_TITLE``, ``API_VERSION``, ``OPENAPI_VERSION``,
    # ``OPENAPI_URL_PREFIX``, ``OPENAPI_SWAGGER_UI_PATH``,
    # ``OPENAPI_SWAGGER_UI_URL``, and ``API_SPEC_OPTIONS`` from
    # ``app.config``, and constructs the OpenAPI document generator
    # plus the Swagger UI route. Subsequent ``api.register_blueprint(...)``
    # calls add Blueprints to the OpenAPI document.
    #
    # The Java equivalent is springdoc-openapi-starter-webmvc-ui's
    # auto-configuration kicking in when the starter is on the
    # classpath; springdoc reads the ``@OpenAPIDefinition`` annotation
    # at startup and produces the same OpenAPI 3.x document.
    # -----------------------------------------------------------------
    api.init_app(app)

    # -----------------------------------------------------------------
    # Step 6: Initialize Flask-Cors with the parity-precise resource
    # scope.
    #
    # CRITICAL parity requirement (per AAP §0.1.1, §0.7.2): the Java
    # ``@CrossOrigin(value = "")`` annotation is placed on
    # ``ProductController`` ONLY. The Java ``StudentController``
    # carries NO ``@CrossOrigin`` annotation. The Python equivalent
    # MUST scope CORS to ``/product/*`` ONLY; the ``/student/*``
    # namespace MUST remain CORS-free.
    #
    # The ``resources`` keyword argument tells Flask-Cors to apply
    # the CORS headers ONLY to requests whose path matches the
    # supplied URL pattern(s). Here we pass a single-pattern
    # dictionary::
    #
    #     resources={r"/product/*": {"origins": "*"}}
    #
    # which matches any URL under the ``/product/`` prefix and applies
    # the wildcard origin (``*``) — equivalent to the Java
    # ``@CrossOrigin(value = "")`` annotation, where the empty
    # ``value`` attribute defaults to ``"*"`` (allow all origins).
    #
    # WARNING: Calling ``cors.init_app(app)`` WITHOUT the ``resources``
    # keyword would silently enable CORS on every Blueprint, including
    # ``/student/*`` — breaking the parity contract and adding
    # ``Access-Control-Allow-Origin: *`` headers to responses where
    # the Java side does not. Do NOT remove or simplify the
    # ``resources`` argument.
    # -----------------------------------------------------------------
    cors.init_app(app, resources={r"/product/*": {"origins": "*"}})

    # -----------------------------------------------------------------
    # Step 7: Lazily import and register the Blueprints.
    #
    # The Blueprint imports MUST be done HERE (inside ``create_app``)
    # rather than at the top of this module. Reason: the controller
    # modules transitively import ``entity/product.py`` (via
    # ``dao/product_dao.py`` and ``repository/product_repository.py``),
    # and ``entity/product.py`` declares a SQLAlchemy ORM model by
    # subclassing ``db.Model``. The ``db.Model`` attribute resolves
    # correctly only after ``db`` has been instantiated in
    # ``extensions.py`` — but if we imported the Blueprints at the top
    # of THIS module (before ``db.init_app(app)`` runs), there would be
    # no Flask app available for the model to attach to, causing a
    # ``RuntimeError: working outside of application context`` or a
    # silent misconfiguration.
    #
    # The canonical Flask factory pattern solves this by deferring
    # Blueprint imports until inside ``create_app``, where:
    #
    #   1. ``db`` has been bound to the Flask app via ``init_app``.
    #   2. ``api`` has been bound to the Flask app via ``init_app``.
    #   3. ``cors`` has been bound (with the parity-precise scope).
    #
    # Now we can safely import the Blueprints and register them with
    # the ``api``. Registration order is alphabetical (product before
    # student) to match the Java side's component-scan order; this is
    # cosmetic (it affects the Swagger UI tag order) but worth
    # preserving for parity.
    #
    # ``api.register_blueprint(blp)`` adds the Blueprint's routes to
    # the Flask URL map AND registers them with flask-smorest's
    # OpenAPI spec generator. The Blueprint's ``url_prefix`` argument
    # (``/product`` or ``/student``) determines where the routes are
    # mounted on the Flask app.
    # -----------------------------------------------------------------
    from controller.product_controller import blp as product_blp
    from controller.student_controller import blp as student_blp

    api.register_blueprint(product_blp)
    api.register_blueprint(student_blp)

    # -----------------------------------------------------------------
    # Step 8: Materialize the SQLAlchemy schema.
    #
    # Inside an application context (required by Flask-SQLAlchemy 3.x
    # for ``db.create_all()`` to find the bound app), import the
    # ``Product`` ORM model and invoke ``db.create_all()``.
    #
    # The ``from entity.product import Product`` line has the SIDE
    # EFFECT of registering the ``Product`` class with SQLAlchemy's
    # declarative metadata. Without this import, ``db.create_all()``
    # would not know about the ``product`` table and would create no
    # tables — a silent failure that would manifest later as
    # ``OperationalError: no such table: product`` when the first DAO
    # method runs.
    #
    # The ``# noqa: F401`` comment silences linters that flag the
    # import as "unused". The import IS used — its side effect is the
    # whole point — but linters cannot detect side-effect imports
    # without the explicit suppression marker.
    #
    # ``db.create_all()`` is idempotent: it creates tables only if
    # they do not already exist. It does NOT drop, alter, or migrate
    # existing tables. Per AAP §0.3.2, schema migration tooling is
    # explicitly out of scope; ``db.create_all()`` is the simplest
    # mechanism that lets:
    #
    #   * The pytest ``sqlite:///:memory:`` fixture work without any
    #     operator intervention (the in-memory SQLite database is
    #     fresh on every test, so all tables need to be created on
    #     each fixture setup).
    #   * The first-time-launch developer experience match Spring
    #     Boot's "just works" feel for a development deployment.
    #
    # For production MySQL deployments, operators are expected to
    # provision the schema externally (DDL scripts, Flyway,
    # Liquibase, etc.) — see ``.env.example`` and the README — but
    # ``db.create_all()`` won't interfere with an already-existing
    # schema because it skips tables that already exist.
    # -----------------------------------------------------------------
    with app.app_context():
        from entity.product import Product  # noqa: F401  (registers ORM model)

        db.create_all()

    # -----------------------------------------------------------------
    # Step 9: Return the fully configured Flask app instance.
    #
    # The returned ``Flask`` object is ready to:
    #
    #   * Serve HTTP requests via ``app.run(...)`` (development
    #     server) or via a production WSGI server (gunicorn,
    #     uWSGI, etc.).
    #   * Be used as a test client via ``app.test_client()`` (the
    #     pytest fixture pattern).
    #   * Have its URL map inspected via ``app.url_map.iter_rules()``
    #     for diagnostic purposes.
    #   * Have its config inspected via ``app.config[...]`` for
    #     diagnostic purposes (the smoke tests in
    #     ``tests/test_app.py`` rely on this).
    #
    # The function NEVER returns ``None`` — any failure in the steps
    # above raises an exception, which propagates to the caller and
    # aborts the application bootstrap with a clear stack trace.
    # -----------------------------------------------------------------
    return app
