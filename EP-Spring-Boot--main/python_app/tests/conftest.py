"""Pytest fixtures shared across the test suite.

These fixtures replace the role of Spring Boot's `@SpringBootTest`
context-loading mechanism: each fixture invocation builds a fully-wired
Flask application instance using the same `create_app()` factory the
production entry script (`server.py`) uses. The only difference is the
`SQLALCHEMY_DATABASE_URI` override to `sqlite:///:memory:` so tests run
without requiring a live MySQL server.

All fixtures are `function`-scoped (the pytest default). This guarantees:
    - Each test gets a fresh Flask app instance.
    - The in-memory SQLite database is recreated per test (full isolation).
    - State leaks between tests are impossible.

Using `session` scope would speed up the suite slightly but would break
the in-memory SQLite isolation guarantee, so it is intentionally NOT used.
"""

import pytest

from app import create_app
from extensions import db


@pytest.fixture
def app():
    """Yield a fully-configured Flask application bound to in-memory SQLite.

    The configuration override dict matches the keys consumed by
    `create_app()`:
        - `SQLALCHEMY_DATABASE_URI` overrides the env-var-driven default
          to use SQLite in-memory storage. Each connection in the test
          process gets a fresh database (perfect for test isolation).
        - `TESTING = True` enables Flask's testing mode, which surfaces
          exceptions instead of converting them to 500 responses (so test
          failures produce useful tracebacks).

    `create_app()` itself imports the entity module and runs
    `db.create_all()` inside an app context, so tables exist before the
    fixture returns the app. No additional setup is needed here.

    Yields:
        Flask: the configured application instance.
    """
    test_config = {
        "SQLALCHEMY_DATABASE_URI": "sqlite:///:memory:",
        "TESTING": True,
    }
    app = create_app(test_config)

    # `create_app()` already calls `db.create_all()` inside an app context,
    # so the SQLAlchemy schema is ready by the time we get here. We push
    # an additional app context so test code that calls into the SQLAlchemy
    # session (e.g., `db.session.add(...)`) does not need to manually open
    # a context.
    with app.app_context():
        yield app
        # Tear-down: drop all tables to be doubly-sure no state leaks.
        # Strictly speaking, this is redundant for `sqlite:///:memory:`
        # because the database is destroyed when the connection closes,
        # but it's defensive and costs nothing.
        db.session.remove()
        db.drop_all()


@pytest.fixture
def client(app):
    """Flask test client for HTTP-style integration tests.

    Although AAP §0.3.2 explicitly excludes endpoint-level integration
    tests from the current scope (the Java side has no controller tests),
    providing this fixture costs nothing and aligns with conventional
    Flask testing patterns. Future tests that need to assert on
    request/response shapes can `def test_xxx(client): client.get(...)`.

    Args:
        app: the Flask app fixture (auto-injected by pytest).

    Yields:
        FlaskClient: a test client for issuing HTTP-style requests.
    """
    return app.test_client()


@pytest.fixture
def runner(app):
    """Flask CLI runner for command-line interface tests.

    Optional but conventional. Currently the Python implementation has no
    custom CLI commands, but if any are added in the future they can be
    tested via `def test_xxx(runner): runner.invoke(...)`.

    Args:
        app: the Flask app fixture (auto-injected by pytest).

    Returns:
        FlaskCliRunner: a test runner for invoking CLI commands.
    """
    return app.test_cli_runner()
