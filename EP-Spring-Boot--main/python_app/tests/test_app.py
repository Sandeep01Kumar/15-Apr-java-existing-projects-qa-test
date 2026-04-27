"""Pytest analog of ``SpringBootSimpleCrudWithMysqlApplicationTests.contextLoads()``.

This module is the Python translation of the single Java test class
``EP-Spring-Boot--main/src/test/java/com/jspider/spring_boot_simple_crud_with_mysql/SpringBootSimpleCrudWithMysqlApplicationTests.java``,
whose entire body is the empty method ``contextLoads()`` annotated with
``@Test`` and decorated at the class level with ``@SpringBootTest``.
The Java test passes if the Spring container boots without throwing — a
classic smoke test that catches "the entire application is broken"
regressions (missing beans, circular wiring, misconfigured datasources,
etc.) before any feature-level test runs.

Per AAP §0.5.1 the literal Python translation is::

    def test_context_loads(app):
        assert app is not None

The ``app`` fixture (defined in ``conftest.py`` and auto-discovered by
pytest at collection time) is the Python analog of Spring's
``@SpringBootTest`` mechanism: it calls the ``create_app()`` factory
inside an application context, runs ``db.create_all()`` to materialize
the SQLite-in-memory schema, yields the configured Flask instance to
the test, and tears the context down on teardown. If ``create_app()``
raises during configuration loading, extension initialization,
blueprint registration, or schema creation, the fixture itself fails
and the test errors out — exactly mirroring the Java side's failure
mode where a context-bootstrap exception causes the test to fail
before its (empty) body even runs.

Beyond the literal AAP-mandated test, this module adds two minimal
defensive checks per the assigned folder requirements:

* ``test_config_metadata`` asserts that the OpenAPI metadata constants
  defined in ``config.py`` (``API_TITLE``, ``API_VERSION``,
  ``OPENAPI_VERSION``) are propagated into ``app.config``. This catches
  silent regressions where ``create_app()`` would forget to copy one
  of the OpenAPI keys, which would render an empty/default Swagger UI
  page without any obvious startup-time error.
* ``test_blueprints_registered`` asserts that both the ``/product/*``
  and ``/student/*`` URL prefixes are registered. The Java side has two
  ``@RestController`` classes (``ProductController`` and
  ``StudentController``); the Python side MUST also have two
  blueprints registered, otherwise one of the two namespaces silently
  disappears.

These additions are NOT a deviation from parity — they are
enhancement-style smoke tests that catch a wider class of regressions
than the bare Java analog. Endpoint-level integration tests are NOT
introduced here (out of scope per AAP §0.3.2; the Java side has no
controller tests, only this single context-load smoke test).

Behavioral parity vs. enhancement
---------------------------------
+-----------------------------------------+----------------------------------------+
| Java side                               | Python side                            |
+=========================================+========================================+
| ``@SpringBootTest`` -> boots full IoC   | ``app`` fixture -> calls               |
| container at test class load time.      | ``create_app()`` (full factory) per    |
|                                         | test function (function-scoped).       |
+-----------------------------------------+----------------------------------------+
| Test method body is empty; the          | ``assert app is not None`` and         |
| implicit assertion is "context loaded   | ``isinstance(app, Flask)`` are explicit|
| without throwing".                      | non-trivial assertions.                |
+-----------------------------------------+----------------------------------------+
| Failure mode: context bootstrap throws  | Failure mode: factory throws ->        |
| -> JUnit reports the cause as the       | fixture setup fails -> pytest reports  |
| test failure.                           | the cause as a test ERROR.             |
+-----------------------------------------+----------------------------------------+
| Runs once per JUnit lifecycle (eager    | Runs once per ``function``-scoped      |
| context bootstrap on first test).       | ``app`` fixture invocation.            |
+-----------------------------------------+----------------------------------------+

Module-import contract
----------------------
Per AAP §0.7.1, the user-supplied implementation rule mandates a
specific tail-comment marker on every line of ``server.py`` ONLY.
This module is NOT subject to that rule because the rule is scoped to
a single file (the Python analog of ``server.js``), not to all Python
files in the project. Consequently no tail-comment markers appear in
this module.

Imports are package-prefix-free (``from flask import Flask``) — the
same convention used by ``app.py``, ``server.py``, ``extensions.py``,
and the controller modules. Pytest is invoked from the
``EP-Spring-Boot--main/python_app/`` working directory (per the
``[tool.pytest.ini_options] testpaths = ["tests"]`` setting in
``pyproject.toml``), so the project root is on ``sys.path`` and bare
module names resolve correctly.
"""

# ---------------------------------------------------------------------------
# External imports.
#
# Only one external dependency is needed in this file: the ``Flask``
# class itself. We use it for the ``isinstance(app, Flask)`` type
# assertion in ``test_context_loads`` — this catches the unlikely but
# possible case where ``create_app()`` returns a non-``None`` object
# that is NOT actually a Flask instance (e.g., a future refactor
# accidentally returning a ``Blueprint`` or a ``Mock``). The Java
# ``@SpringBootTest`` annotation provides an analogous implicit
# guarantee that a successful context-load yields a real
# ``ApplicationContext``; the Python ``isinstance`` check makes the
# equivalent guarantee explicit.
#
# Pytest fixtures are NOT imported — they are auto-discovered by
# pytest from any ``conftest.py`` in the test path at collection
# time. The ``app`` parameter on each test function is the fixture
# from ``conftest.py``.
# ---------------------------------------------------------------------------
from flask import Flask


def test_context_loads(app):
    """Smoke test: the application factory returns a non-``None`` Flask app.

    This is the direct analog of the Java ``@SpringBootTest contextLoads()``
    test. The Java method body is empty; the implicit assertion is
    "if Spring boots without throwing, the test passes". The Python
    equivalent makes the assertion explicit:

    1. ``assert app is not None`` — the factory produced an object.
    2. ``assert isinstance(app, Flask)`` — that object is the right type.

    If ``create_app()`` raises during configuration, extension
    initialization, blueprint registration, or schema creation, the
    ``app`` fixture itself fails and pytest reports the cause as the
    test ERROR (distinct from a test FAILURE). Either way, the operator
    sees a clear stack trace pointing at the broken factory step —
    exactly the diagnostic experience JUnit provides for a failed
    ``@SpringBootTest``.

    Parameters
    ----------
    app : flask.Flask
        The fixture from ``conftest.py``. The fixture's setup invokes
        ``create_app()`` with a SQLite-in-memory database URI and runs
        ``db.create_all()`` inside an application context, then yields
        the configured Flask instance.
    """
    # Primary assertion: the fixture produced a non-None object.
    # This mirrors the Java side's implicit "context loaded" guarantee.
    assert app is not None

    # Type assertion: the object is specifically a Flask instance.
    # This catches the (unlikely) case where create_app() is refactored
    # to return a Blueprint, an Api, a tuple, or any other non-Flask
    # object that happens to be non-None. Without this check, downstream
    # tests that call methods specific to Flask (e.g., test_client(),
    # url_map.iter_rules()) would fail with cryptic AttributeError
    # messages; with this check, the failure is diagnosed at the smoke
    # test layer with a clear "Expected Flask, got <type>" message.
    assert isinstance(app, Flask)


def test_config_metadata(app):
    """The OpenAPI metadata propagates from ``config.py`` into ``app.config``.

    These assertions catch silent regressions where ``create_app()``
    would forget to copy one of the OpenAPI constants from ``config.py``
    into ``Flask.config``. Without those config keys, flask-smorest
    initializes with default placeholder values and the rendered
    Swagger UI page shows a generic "API" title and a missing version
    field — a regression that would NOT be caught by the basic
    context-load test (because the factory still returns a working
    Flask instance).

    The three keys checked here are the externally visible portion of
    the OpenAPI document that the parity contract (AAP §0.7.2) commits
    to byte-for-byte preservation:

    * ``API_TITLE`` — must equal ``"Product-Crud-Operation"`` per the
      ``@OpenAPIDefinition(info = @Info(title = ...))`` annotation in
      ``SpringBootSimpleCrudWithMysqlApplication.java``.
    * ``API_VERSION`` — must equal ``"1.0.0"`` per the same annotation's
      ``version`` attribute.
    * ``OPENAPI_VERSION`` — must equal ``"3.0.3"``; this is a
      flask-smorest-internal config key that pins the OpenAPI
      specification version emitted in the document header.

    The two additional metadata fields (description and contact URL)
    are not asserted here because they are not used by clients to
    identify the API — only the title/version pair is.

    Parameters
    ----------
    app : flask.Flask
        The fixture from ``conftest.py``; provides ``app.config`` as a
        ``flask.Config`` (a dict subclass) populated from the
        ``config.py`` constants.
    """
    # Title: must match the @OpenAPIDefinition info.title in
    # SpringBootSimpleCrudWithMysqlApplication.java (Java line 13).
    # This string is part of the externally observable API surface
    # (it appears on the Swagger UI page header) so any deviation
    # would constitute a behavioral drift.
    assert app.config["API_TITLE"] == "Product-Crud-Operation"

    # Version: must match the @OpenAPIDefinition info.version
    # (Java line 15). Stored as a string per the OpenAPI spec
    # (numeric literals are NOT valid for the version field).
    assert app.config["API_VERSION"] == "1.0.0"

    # OpenAPI specification version emitted in the document header.
    # flask-smorest uses this internally to determine which OpenAPI
    # schema dialect to emit; pinning 3.0.3 ensures the rendered
    # Swagger UI matches the springdoc-openapi 2.8.6 output produced
    # by the Java side.
    assert app.config["OPENAPI_VERSION"] == "3.0.3"


def test_blueprints_registered(app):
    """Both ``/product/*`` and ``/student/*`` URL prefixes must be registered.

    This guards against the regression where someone refactors
    ``create_app()`` and accidentally drops one of the two
    ``api.register_blueprint(...)`` calls. The Java side has TWO
    ``@RestController`` classes (``ProductController`` and
    ``StudentController``) so the Python side MUST also have two
    blueprints registered. If only one is registered, half of the
    twelve documented endpoints (AAP §0.2.3) silently disappear
    without any obvious startup-time error — the kind of regression
    that is hard to detect without a smoke test.

    The check is done by enumerating ``app.url_map.iter_rules()`` and
    asserting that at least one rule starts with ``/product/`` and at
    least one with ``/student/``. This is intentionally lenient — it
    does NOT enforce a specific endpoint count or a specific URL
    pattern — because the endpoint list is the responsibility of the
    individual controller modules, not the application factory.

    The ``str(rule.rule)`` cast is defensive against future Flask
    versions where ``rule.rule`` could be a non-string subclass; the
    cast is a no-op in current Flask but future-proofs the assertion.

    Parameters
    ----------
    app : flask.Flask
        The fixture from ``conftest.py``; provides
        ``app.url_map.iter_rules()`` which yields all registered
        ``werkzeug.routing.Rule`` objects.
    """
    # Collect every URL rule registered with the Flask app's URL map.
    # The ``rule.rule`` attribute is the URL pattern string (e.g.,
    # ``/product/getTodayDate`` or ``/student/addition/<int:a1>/<int:b1>``).
    # The ``str(...)`` cast is defensive — in current Flask
    # ``rule.rule`` is already a ``str``, but explicit conversion
    # future-proofs the assertion against any subclass changes.
    rules = [str(rule.rule) for rule in app.url_map.iter_rules()]

    # At least one rule must start with /product/ — guards against
    # the regression where ProductController's blueprint is dropped
    # from create_app(). Includes a helpful debug message listing
    # ALL registered URL patterns so the operator can see exactly
    # what is registered when the assertion fails.
    assert any(rule.startswith("/product/") for rule in rules), (
        "Expected at least one route starting with /product/, got: "
        f"{sorted(rules)}"
    )

    # At least one rule must start with /student/ — guards against
    # the regression where StudentController's blueprint is dropped
    # from create_app(). Same diagnostic-message pattern as above.
    assert any(rule.startswith("/student/") for rule in rules), (
        "Expected at least one route starting with /student/, got: "
        f"{sorted(rules)}"
    )
