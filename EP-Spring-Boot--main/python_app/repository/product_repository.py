"""SQLAlchemy-backed repository for the ``Product`` entity.

Translation of
``EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/repository/ProductRepository.java``
(Spring Data JPA ``JpaRepository<Product, Integer>``).

Role in the layered architecture
--------------------------------
This module sits at the lowest level of the data-access stack — directly
above SQLAlchemy and beneath the DAO layer (``dao/product_dao.py``).
Per AAP §0.4.3, the layered call chain is identical on both sides::

    Controller -> DAO (façade) -> Repository (this module) -> ORM/SQL

The Java side declares ``ProductRepository`` as an *interface* extending
``JpaRepository<Product, Integer>``. Spring Data's runtime proxy
generates a concrete bean at startup that implements every method —
both the inherited CRUD operations (``save``, ``saveAll``, ``findAll``,
``findById``, etc.) and the two custom methods (``getProductByPrice``,
``deleteProductByPrice``) that carry ``@Query(nativeQuery = true)``
annotations, plus the Spring Data derived-query method ``findByName``
whose body is generated from its method name.

Python has no analog to Spring Data's runtime-proxy generation. The
Python translation is therefore an *explicit* ``ProductRepository``
class that wraps ``db.session`` calls and exposes the same set of
method names in snake_case form. Specifically:

==========================================  =====================================================
Java construct                              Python equivalent
==========================================  =====================================================
``interface ProductRepository extends``     ``class ProductRepository:`` (no parent class)
  ``JpaRepository<Product, Integer>``
``save(Product)`` (inherited)               ``save(product)`` -> add + commit + return
``saveAll(List<Product>)`` (inherited)      ``save_all(products)`` -> add_all + commit + return
``findAll()`` (inherited)                   ``find_all()`` -> session.query(Product).all()
``findById(Integer id)`` (inherited)        ``find_by_id(id_)`` -> session.get(Product, id_)
``findByName(String name)`` (derived)       ``find_by_name(name)`` -> filter_by(name=name).all()
``@Query nativeQuery=true SELECT``          ``get_product_by_price(price)`` -> text(...) + map rows
``@Query nativeQuery=true DELETE`` +        ``delete_product_by_price(price)`` -> text(...) +
  ``@Modifying`` + ``@Transactional``         explicit ``db.session.commit()``
==========================================  =====================================================

Parity-critical invariants (per AAP §0.7.2)
-------------------------------------------
The following five invariants MUST be preserved exactly. Each one
maps to a documented behavioral parity requirement and any deviation
would alter externally observable behavior or cause a silent data
integrity failure:

1. **Native SQL strings preserved verbatim.** The two Java strings
   ``"select * from product where price=?"`` and
   ``"delete from product where price=?"`` are reproduced as
   ``"select * from product where price=:price"`` and
   ``"delete from product where price=:price"`` respectively. The
   ONLY change is the parameter style (positional ``?`` -> named
   ``:price``), which is required by SQLAlchemy's ``text()`` API.
   Lowercase ``select``/``delete``, the unquoted table name
   ``product``, the column name ``price``, the ``=`` operator, the
   spaces, and the absence of a trailing semicolon are all preserved
   to keep the externally observable database operations identical.

2. **``find_by_id`` returns ``None`` on miss, never raises.** The
   F-006 endpoint (``GET /product/getProduct/{id}``) responds with
   HTTP 200 and a body of ``null`` when no row matches the id —
   NOT HTTP 404. The DAO's ``get_product_by_id_dao`` depends on this
   repository method returning ``None`` (matching Java's
   ``Optional.empty()`` semantics) so the DAO can pass that ``None``
   straight through to the controller, which JSON-serializes it as
   ``null``. ``db.session.get(Product, id_)`` is the SQLAlchemy
   2.x idiom that returns ``None`` on miss without raising.

3. **``delete_product_by_price`` MUST commit explicitly.** The Java
   method carries ``@Modifying`` + ``@Transactional`` annotations,
   which together instruct Spring to commit the DELETE on method
   exit. SQLAlchemy has no equivalent annotation; the equivalent
   semantic — "the DELETE is durably persisted by the time this
   method returns" — is achieved by calling ``db.session.commit()``
   explicitly inside the method. Without the commit, the DELETE
   statement enters an open transaction that is rolled back when
   the session is cleaned up at request boundary, producing a
   silent no-op that would be very hard to diagnose in production.

4. **``get_product_by_price`` returns ``List[Product]``, not raw
   rows.** ``db.session.execute(text(...))`` returns a SQLAlchemy
   ``Result`` whose iteration yields ``Row`` tuples, NOT ``Product``
   instances. The Java method returns ``List<Product>`` (mapped by
   Hibernate), so the Python translation rebuilds a fresh
   ``Product`` instance per row to keep the return shape identical.
   This matters because the controller's
   ``@blp.response(200, ProductSchema(many=True))`` decorator
   serializes via marshmallow, which expects an iterable of objects
   with attribute access (``row.id``, ``row.name``, ...). Raw
   ``Row`` tuples would either fail serialization or silently
   render as positional arrays, breaking the JSON contract.

5. **Save/save_all commit observably.** The Java
   ``JpaRepository.save`` returns a managed entity that is observably
   persisted by request end (Spring's open-session-in-view filter
   wraps each request in an outer transaction that auto-commits on
   success). The Python translation makes the commit explicit
   inside ``save`` and ``save_all`` so the caller can observe the
   persisted state immediately after the call returns — preserving
   the Java side's "after save, the data IS in the database"
   contract regardless of whether the caller is inside a request
   context.

Module-level singleton
----------------------
A module-level singleton ``product_repository = ProductRepository()``
is declared at the bottom of this file. The DAO module
(``dao/product_dao.py``) imports the lowercase singleton::

    from repository.product_repository import product_repository

This pattern is the Python equivalent of Spring's auto-instantiated
``@Repository`` bean: a single shared instance is created at module
import time and re-used by every caller. The instance carries no
state of its own — every method delegates to ``db.session``, which
Flask-SQLAlchemy scopes per-request — so the shared singleton is
safe for concurrent use under Flask's threaded WSGI server.

Out-of-scope (per AAP §0.3.2)
-----------------------------
The following are NOT implemented here, intentionally:

* Pagination/sorting variants (``find_all_paginated``,
  ``find_all_sorted``, page/sort parameters).
* Custom criteria or specifications API.
* Repository-level caching (``@functools.lru_cache``, second-level
  cache, etc.).
* Connection-pool tuning (handled at the SQLAlchemy engine level
  via the URI in ``config.py``).
* Bulk update operations (``update_all`` and similar).
* Asynchronous variants (``async def save`` etc.); this is a sync
  Flask app.
* Repository-level logging; SQLAlchemy's engine-level logging is
  configured globally if needed.
* Repository-level validation; that responsibility lies with
  ``schemas/product_schema.py`` (marshmallow).

The Sk-21-Rule (``# SK-QA`` tail comment on every line) is scoped to
``server.py`` only and does NOT apply to this module per AAP §0.7.1.

Exports
-------
ProductRepository : type
    The repository class with seven methods (``save``, ``save_all``,
    ``find_all``, ``find_by_id``, ``find_by_name``,
    ``get_product_by_price``, ``delete_product_by_price``). Tests
    may instantiate fresh copies if they want isolated state, but
    the production code path uses the module-level singleton below.
product_repository : ProductRepository
    Module-level singleton instance — the canonical entry point
    used by ``dao/product_dao.py``. Created at import time.
"""

# ---------------------------------------------------------------------------
# Standard library type-hint imports.
#
# Python's ``typing`` module supplies the generic type aliases used in
# the method signatures below:
#
#   * ``List[Product]``  — return type for ``find_all``, ``save_all``,
#                           ``find_by_name``, ``get_product_by_price``.
#                           Mirrors the Java ``List<Product>`` return
#                           type byte-for-byte.
#   * ``Optional[Product]`` — return type for ``find_by_id``. Mirrors
#                           the Java ``Optional<Product>`` semantic
#                           (a value or "no value", expressed in
#                           Python as ``Product | None``).
#
# The PEP 585 ``list[...]`` and PEP 604 ``X | None`` syntaxes would
# also work on Python 3.9+/3.10+ respectively, but the project's
# stated minimum (``requires-python = ">=3.9"`` in ``pyproject.toml``)
# would NOT support PEP 604 unioning across all targets. Using the
# ``typing`` module is the lowest-common-denominator choice that
# works under every supported Python version listed in
# ``pyproject.toml`` (3.9, 3.10, 3.11, 3.12).
# ---------------------------------------------------------------------------
from typing import List, Optional

# ---------------------------------------------------------------------------
# SQLAlchemy ``text()`` for native-SQL parameter binding.
#
# ``sqlalchemy.text`` wraps a SQL string in a textual construct that
# the SQLAlchemy ``Connection.execute`` / ``Session.execute`` API can
# consume as a "non-managed" statement. It is the Python equivalent
# of the Java ``@Query(value = "...", nativeQuery = true)`` annotation
# pair: both bypass the ORM query builder and run a literal SQL string
# against the database.
#
# We import ``text`` from the top-level ``sqlalchemy`` namespace
# (NOT from ``flask_sqlalchemy``). This is the canonical SQLAlchemy
# 2.x import path documented at
# https://docs.sqlalchemy.org/en/20/core/connections.html#sqlalchemy.text.
# Flask-SQLAlchemy does NOT re-export ``text`` because the construct
# is part of SQLAlchemy Core, not the Flask integration layer.
# ---------------------------------------------------------------------------
from sqlalchemy import text

# ---------------------------------------------------------------------------
# Application-internal imports.
#
# Per the established codebase convention (verified by inspecting
# ``extensions.py``, ``entity/product.py``, ``schemas/product_schema.py``,
# ``responses/response_structure.py``, and ``tests/test_app.py``),
# imports use SIMPLE module names — NOT a ``python_app.`` prefix.
#
# The runtime entry point ``server.py`` runs from inside the
# ``EP-Spring-Boot--main/python_app/`` directory, which puts that
# directory on ``sys.path`` so bare module names resolve. The pytest
# configuration (``[tool.pytest.ini_options]`` in ``pyproject.toml``)
# also runs from that working directory. Using ``python_app.`` as a
# package prefix would break both the runtime and the test discovery.
#
#   * ``db``       — the ``flask_sqlalchemy.SQLAlchemy`` singleton
#                    from ``extensions.py``. Provides ``db.session``
#                    (the request-scoped Session for query/add/commit
#                    operations) plus ``db.Model`` (used by the
#                    ``Product`` ORM model).
#   * ``Product``  — the ORM model from ``entity/product.py``. Used as
#                    both the queryable entity (``db.session.query(
#                    Product)``, ``db.session.get(Product, id_)``) and
#                    as the construction target when mapping native-SQL
#                    ``Row`` results to typed ``Product`` instances in
#                    ``get_product_by_price``.
# ---------------------------------------------------------------------------
from extensions import db
from entity.product import Product


class ProductRepository:
    """Data-access layer for the ``Product`` entity.

    Wraps SQLAlchemy ``db.session`` calls and exposes the same method
    names that ``JpaRepository<Product, Integer>`` provides on the Java
    side, rendered in Python snake_case. There is no inheritance and no
    ``@Repository`` decorator — instances are plain Python objects whose
    methods delegate to ``db.session``.

    The class is instantiated exactly once at the bottom of this module
    (``product_repository = ProductRepository()``); the DAO layer imports
    that lowercase singleton. Tests may instantiate fresh copies if
    they want isolated state, but the production code path always
    routes through the singleton.

    Methods
    -------
    save(product)
        Persist a single ``Product``. Translation of the inherited
        ``JpaRepository.save(S)``.
    save_all(products)
        Persist a list of ``Product`` instances in one batch.
        Translation of the inherited ``JpaRepository.saveAll(Iterable)``.
    find_all()
        Return every row in the ``product`` table. Translation of the
        inherited ``JpaRepository.findAll()``.
    find_by_id(id_)
        Look up a ``Product`` by primary key, returning ``None`` on
        miss (NOT raising — preserves F-006 null-on-miss parity).
        Translation of the inherited ``JpaRepository.findById(ID)``.
    find_by_name(name)
        Find every ``Product`` with the given ``name``. Translation
        of the Java derived-query method ``findByName(String)``.
    get_product_by_price(price)
        Find every ``Product`` at the given ``price`` using NATIVE
        SQL. Translation of the Java
        ``@Query(value = "select * from product where price=?",
        nativeQuery = true) List<Product> getProductByPrice(double)``.
    delete_product_by_price(price)
        Delete every ``Product`` at the given ``price`` using NATIVE
        SQL. Translation of the Java
        ``@Query(value = "delete from product where price=?",
        nativeQuery = true) @Modifying @Transactional void
        deleteProductByPrice(double)``.
    """

    # ------------------------------------------------------------------
    # Inherited-from-JpaRepository surface (the methods the DAO uses).
    #
    # Spring Data's ``JpaRepository`` interface declares dozens of
    # methods (``save``, ``saveAll``, ``findAll``, ``findById``,
    # ``existsById``, ``count``, ``deleteById``, ``flush``, etc.). The
    # Java codebase only USES four of them via the ``ProductDao``
    # façade — ``save``, ``saveAll``, ``findAll``, ``findById`` — so
    # this section translates exactly those four. The others are NOT
    # implemented here per AAP §0.3.2 ("Out of scope: pagination/
    # sorting"): adding methods that the Java side does not expose
    # would constitute API expansion, not parity translation.
    # ------------------------------------------------------------------

    def save(self, product: Product) -> Product:
        """Persist a single ``Product`` and return it.

        Equivalent to Spring Data's inherited ``save(S entity)`` method.

        The Java side's ``JpaRepository.save`` returns a managed entity
        that is observably persisted by request end because Spring
        Boot's open-session-in-view filter wraps each request in an
        outer transaction that auto-commits on success. SQLAlchemy
        has no such auto-commit on session-cleanup behavior — calling
        ``db.session.add(product)`` alone would leave the INSERT in
        the session's pending-changes buffer until something else
        (a query that flushes, an explicit commit, or session close)
        triggered the actual SQL execution.

        To preserve the Java semantic — "after ``save`` returns, the
        data IS in the database" — this method calls
        ``db.session.commit()`` explicitly. The commit:

        1. Flushes pending changes (executes the INSERT statement).
        2. Commits the current transaction (durably persists the row).
        3. Begins a new transaction implicitly (SQLAlchemy 2.x
           autobegin) so subsequent ``db.session`` operations within
           the same request remain transactional.

        After the commit, the ``product`` argument is a managed
        instance attached to ``db.session`` with its primary key
        column populated (which, in this codebase, was supplied by
        the caller because the entity has no ``@GeneratedValue``).

        Parameters
        ----------
        product : Product
            The fully-constructed ``Product`` instance to insert. The
            caller is responsible for setting ``product.id`` because
            the ``Product`` entity has ``autoincrement=False`` per
            AAP §0.7.2 — the database does NOT auto-generate a key.

        Returns
        -------
        Product
            The same ``product`` instance, now persisted and managed.
            Returning it (rather than ``None``) matches the Java
            ``JpaRepository.save`` signature ``S save(S entity)``.
        """
        # Stage the INSERT in the session's pending-changes buffer.
        # This does NOT execute SQL yet; it just registers the
        # instance with SQLAlchemy's identity map.
        db.session.add(product)

        # Commit: flush the pending INSERT to the database and end
        # the transaction. After this call returns, a separate
        # connection (or a re-issued query) will see the new row.
        # This is the parity-preservation step that makes the
        # Python ``save`` observably equivalent to the Java
        # ``JpaRepository.save`` running under Spring's
        # open-session-in-view filter.
        db.session.commit()

        # Return the managed instance. Java returns the same object
        # the caller passed in (after Hibernate has populated any
        # generated keys); we do the same. The ``id`` field was
        # already set by the caller (no ``@GeneratedValue``), so no
        # post-commit field-update is needed.
        return product

    def save_all(self, products: List[Product]) -> List[Product]:
        """Persist a list of ``Product`` instances and return them.

        Equivalent to Spring Data's inherited ``saveAll(Iterable<S>)``
        method.

        Uses ``db.session.add_all(products)`` to register every
        instance with the session in one call (SQLAlchemy's batched
        equivalent of looping over ``add(p)`` for each ``p``). The
        commit at the end flushes all pending INSERTs in one
        transaction — matching Java's ``saveAll``, which executes
        the inserts inside a single transaction boundary.

        The Java side does NOT batch the SQL itself (Hibernate emits
        one INSERT per element by default unless
        ``hibernate.jdbc.batch_size`` is configured, which it is not
        in this codebase); SQLAlchemy 2.x likewise emits one INSERT
        per element by default. Both sides therefore produce the
        same wire-level SQL traffic per element, just inside one
        transaction.

        Parameters
        ----------
        products : List[Product]
            A list of fully-constructed ``Product`` instances. Each
            element MUST have ``id`` set (no autoincrement on the
            entity per AAP §0.7.2). The list MAY be empty, in which
            case the method is effectively a no-op (the commit at
            the end commits an empty transaction, which is a no-op
            on every supported database).

        Returns
        -------
        List[Product]
            The same list the caller passed in, with every element
            now persisted and managed. Returning the list (rather
            than ``None``) matches the Java
            ``JpaRepository.saveAll`` signature
            ``Iterable<S> saveAll(Iterable<S> entities)``.
        """
        # Stage every element's INSERT in one call. ``add_all`` is
        # SQLAlchemy's idiomatic batched ``add`` — internally it
        # loops over the iterable and calls ``session.add(entity)``
        # for each, but it's clearer at the call site.
        db.session.add_all(products)

        # Commit the batch: all pending INSERTs are flushed and the
        # transaction is committed. As with ``save``, this is the
        # parity-preservation step that makes the Python
        # ``save_all`` observably equivalent to the Java
        # ``JpaRepository.saveAll`` running under Spring's
        # open-session-in-view filter.
        db.session.commit()

        # Return the list the caller passed in, mirroring the Java
        # signature ``Iterable<S> saveAll(Iterable<S>)``. The list
        # reference is the same; the elements inside it are now
        # managed instances with persisted state.
        return products

    def find_all(self) -> List[Product]:
        """Return every row in the ``product`` table as a list.

        Equivalent to Spring Data's inherited ``findAll()`` method.

        Returns an UNBOUNDED list — the Java side does not paginate
        (the controller method ``findAllProductController`` simply
        delegates to ``productDao.displayAllProductDao()`` with no
        ``Pageable`` argument), and the Python translation does not
        either per AAP §0.3.2 ("Out of scope: pagination/sorting").
        For tables with millions of rows this could be a performance
        problem, but introducing pagination would be a behavioral
        enhancement that violates the parity contract.

        Returns
        -------
        List[Product]
            Every row in the ``product`` table, as a Python list of
            ``Product`` instances. The list MAY be empty. Insertion
            order is NOT guaranteed by either side; the underlying
            SQL is ``SELECT * FROM product`` with no ``ORDER BY``,
            so the actual order depends on the database engine
            (MySQL typically returns rows in insertion order on a
            simple table, but this is not guaranteed).
        """
        # ``db.session.query(Product).all()`` is the SQLAlchemy 1.x /
        # 2.x-legacy idiom for "execute SELECT * FROM product and
        # return every row as a Product instance". SQLAlchemy 2.x
        # also supports the new ``select(Product)`` + ``scalars``
        # syntax, but ``session.query`` continues to work and is
        # idiomatic Flask-SQLAlchemy code (the Flask-SQLAlchemy
        # documentation uses ``session.query`` throughout). Either
        # idiom produces identical SQL.
        return db.session.query(Product).all()

    def find_by_id(self, id_: int) -> Optional[Product]:
        """Look up a ``Product`` by primary key.

        Equivalent to Spring Data's inherited ``findById(ID id)``
        method.

        **CRITICAL — F-006 null-on-miss parity:** This method returns
        ``None`` (NOT raises an exception) when the id does not match
        any row. The DAO's ``get_product_by_id_dao`` propagates that
        ``None`` straight through to the controller, which serializes
        it as JSON ``null``. The endpoint
        ``GET /product/getProduct/{id}`` therefore returns HTTP 200
        with body ``null`` on a miss — NOT HTTP 404. This is a
        documented parity quirk per AAP §0.7.2 and Section 5.2.4.1
        of the Technical Specification; clients of the original Java
        application rely on this behavior, so it MUST be preserved.

        ``db.session.get(Product, id_)`` is the SQLAlchemy 2.x idiom
        for "look up by primary key". Per the SQLAlchemy 2.x
        documentation, ``Session.get`` returns the instance or
        ``None`` if no row matches — exactly the semantic we need.
        The older ``query(Product).get(id_)`` works too but is
        deprecated in 2.x.

        Parameters
        ----------
        id_ : int
            The primary key value to look up. The trailing
            underscore avoids shadowing Python's built-in ``id()``.

        Returns
        -------
        Optional[Product]
            The matching ``Product`` instance, or ``None`` if no row
            has that primary key. NEVER raises on miss.
        """
        # ``Session.get`` is the SQLAlchemy 2.x primary-key lookup
        # idiom. It uses the session's identity map first (so a
        # repeated lookup of the same id within a transaction
        # returns the cached instance without hitting the database)
        # and falls through to a SELECT-by-PK query on miss. On a
        # genuine "row does not exist" miss, it returns ``None`` —
        # which is exactly the behavior we need for F-006.
        return db.session.get(Product, id_)

    # ------------------------------------------------------------------
    # Custom methods declared in the Java interface.
    #
    # Spring Data parses these from the interface body and either
    # generates a query from the method name (``findByName``) or runs
    # the explicit ``@Query`` annotation's SQL string
    # (``getProductByPrice``, ``deleteProductByPrice``). The Python
    # translation provides hand-written equivalents.
    # ------------------------------------------------------------------

    def find_by_name(self, name: str) -> List[Product]:
        """Find every ``Product`` whose ``name`` column matches exactly.

        Translation of the Java derived-query method::

            List<Product> findByName(String name);

        Spring Data parses the method name ``findByName`` and
        auto-generates the query ``SELECT p FROM Product p WHERE
        p.name = ?1``. The SQLAlchemy equivalent is
        ``filter_by(name=name)`` on a ``Product`` query — also
        producing ``WHERE name = :name`` SQL. Both sides use exact
        equality (case-sensitive on most SQL collations), no
        wildcards, no LIKE.

        Parameters
        ----------
        name : str
            The exact name to match. Case-sensitivity depends on
            the collation of the underlying ``name`` column; on
            MySQL with the default ``utf8mb4_general_ci`` collation
            the match is case-insensitive, on SQLite the default
            ``BINARY`` collation gives case-sensitive matching.
            This sensitivity is determined at the schema level and
            is NOT controlled by this method — it matches Java's
            behavior exactly.

        Returns
        -------
        List[Product]
            Every ``Product`` row with matching ``name``. The list
            MAY be empty (no exception is raised on no-match — the
            Java derived query also returns an empty list rather
            than raising).
        """
        # ``filter_by(name=name)`` is the SQLAlchemy idiom for "WHERE
        # name = :name". It is equivalent to ``filter(Product.name
        # == name)`` but more concise. ``.all()`` materializes the
        # query result as a Python list of ``Product`` instances.
        return db.session.query(Product).filter_by(name=name).all()

    def get_product_by_price(self, price: float) -> List[Product]:
        """Find every ``Product`` at the given ``price`` using NATIVE SQL.

        Translation of the Java method::

            @Query(value = "select * from product where price=?",
                   nativeQuery = true)
            List<Product> getProductByPrice(double price);

        **Native SQL preservation (CRITICAL):** The SQL string is
        preserved BYTE-FOR-BYTE from the Java side. The ONLY change
        is the parameter style: positional ``?`` (Java JPA) becomes
        named ``:price`` (SQLAlchemy ``text()``). Specifically:

        ============= ============================================
        Java          ``select * from product where price=?``
        Python        ``select * from product where price=:price``
        ============= ============================================

        Lowercase ``select``, the unquoted lowercase table name
        ``product``, the column name ``price``, the ``=`` operator,
        the absence of a ``;``, and the absence of any ``ORDER BY``
        clause are all preserved. Per AAP §0.7.2, this byte-for-byte
        preservation is required because the SQL string forms part
        of the externally observable database operation; any change
        (uppercase, semicolon, schema-qualified table name, etc.)
        would alter the wire-level SQL traffic.

        **Row -> Product mapping (CRITICAL):** SQLAlchemy's
        ``db.session.execute(text(...))`` returns a ``Result`` whose
        rows are tuple-like ``Row`` objects, NOT ``Product``
        instances. The Java method returns ``List<Product>`` (mapped
        by Hibernate's native-query result-set mapping). To preserve
        that return shape, this method rebuilds a fresh ``Product``
        instance per row by reading each column off the row's
        attribute interface (``row.id``, ``row.name``, ``row.color``,
        ``row.price``).

        This matters downstream because the controller's
        ``@blp.response(200, ProductSchema(many=True))`` decorator
        uses marshmallow to serialize the list, and marshmallow
        accesses fields via attribute access. A raw ``Row`` does
        expose attribute access, but it would render with the
        column order rather than the marshmallow declared field
        order, and it would not pass the ``isinstance(item,
        Product)`` checks that other parts of the codebase make.
        Returning real ``Product`` instances keeps the type
        contract identical to ``find_all`` and ``find_by_name``.

        Parameters
        ----------
        price : float
            The exact price to match. SQLAlchemy binds this as a
            named parameter (``:price``) so SQL injection is not
            possible regardless of the value.

        Returns
        -------
        List[Product]
            Every ``Product`` row with matching ``price``. The list
            MAY be empty (no exception is raised on no-match).
        """
        # Execute the native SQL with named-parameter binding. The
        # second argument is the parameter dict; SQLAlchemy
        # substitutes ``:price`` with a parameterized placeholder
        # at the database driver level (so this is NOT string
        # interpolation — it is safe against SQL injection).
        result = db.session.execute(
            text("select * from product where price=:price"),
            {"price": price},
        )

        # Map each Row to a fresh Product instance.
        #
        # ``row.id``, ``row.name``, ``row.color``, ``row.price`` work
        # because SQLAlchemy ``Row`` objects support attribute access
        # by column name when the underlying SELECT exposes those
        # column names (which ``select *`` does — the column names
        # come from the underlying table schema).
        #
        # Constructing ``Product(id=row.id, ...)`` produces a
        # detached instance — i.e., one not attached to the session.
        # This is fine because:
        #   1. The caller (the DAO) only reads from the returned
        #      list; it does not modify or re-save the instances.
        #   2. The marshmallow schema serializes via attribute
        #      access, which works identically on detached and
        #      attached instances.
        #   3. Detached instances do NOT participate in the session's
        #      identity map, so they cannot accidentally interfere
        #      with concurrent transactions.
        return [
            Product(id=row.id, name=row.name, color=row.color, price=row.price)
            for row in result
        ]

    def delete_product_by_price(self, price: float) -> None:
        """Delete every ``Product`` at the given ``price`` using NATIVE SQL.

        Translation of the Java method::

            @Query(value = "delete from product where price=?",
                   nativeQuery = true)
            @Modifying
            @Transactional
            void deleteProductByPrice(double price);

        **Native SQL preservation (CRITICAL):** As with
        ``get_product_by_price``, the SQL string is preserved
        BYTE-FOR-BYTE from the Java side. The ONLY change is the
        parameter style: positional ``?`` becomes named ``:price``.

        ============= ===============================================
        Java          ``delete from product where price=?``
        Python        ``delete from product where price=:price``
        ============= ===============================================

        **Explicit commit (CRITICAL):** The Java method's
        ``@Modifying`` + ``@Transactional`` annotation pair instructs
        Spring to commit the DELETE on method exit. SQLAlchemy has
        no equivalent annotation; the equivalent semantic — "the
        DELETE is durably persisted by the time this method
        returns" — MUST be achieved by calling
        ``db.session.commit()`` explicitly. Without this commit:

        1. The DELETE statement is executed (the database driver
           sees it and the row is logically removed within the
           current transaction).
        2. But the transaction is never committed.
        3. When the request context is torn down, SQLAlchemy's
           cleanup either rolls back the transaction outright (in
           a clean shutdown path) or, in some failure paths,
           leaves the transaction dangling until the connection
           is recycled.

        The net effect of forgetting the commit would be a SILENT
        no-op: the method appears to succeed (no exception
        raised), the controller returns HTTP 200, but the row is
        still in the database. This is exactly the kind of bug
        that is hard to detect during development (single-request
        manual testing might pass if the connection isn't
        recycled between calls) and catastrophic in production.
        The explicit ``db.session.commit()`` below is the
        non-negotiable parity-preservation step for the F-011
        endpoint.

        Parameters
        ----------
        price : float
            The exact price to match. Every row in the ``product``
            table whose ``price`` column equals this value is
            deleted in one DELETE statement.

        Returns
        -------
        None
            The Java method returns ``void``; the Python method
            returns ``None``. The number of rows deleted is NOT
            returned (Java's ``@Modifying void`` discards the
            row count, and the Python translation matches that).
        """
        # Execute the native DELETE with named-parameter binding.
        # The parameter ``:price`` is substituted at the database
        # driver level (not string-interpolated), so SQL injection
        # is not possible.
        #
        # ``db.session.execute(text(...))`` returns a Result, but
        # we ignore it for DELETE statements: the row count is
        # available via ``result.rowcount`` if needed, but the
        # Java method's ``void`` return type means clients don't
        # see this count, and we mirror that.
        db.session.execute(
            text("delete from product where price=:price"),
            {"price": price},
        )

        # CRITICAL: explicit commit. See the docstring above for the
        # full rationale. In one line: without this commit, the
        # DELETE is rolled back at session cleanup and the
        # operation silently fails. This is the Python equivalent
        # of Java's ``@Transactional`` annotation firing on method
        # exit.
        db.session.commit()


# ---------------------------------------------------------------------------
# Module-level singleton.
#
# This is the Python equivalent of Spring's auto-instantiated
# ``@Repository`` bean. The class is constructed exactly once at module
# import time, and the instance is shared across the entire process —
# importers obtain a reference to the same instance regardless of where
# or when they call ``from repository.product_repository import
# product_repository``.
#
# The DAO module (``dao/product_dao.py``) imports this lowercase name::
#
#     from repository.product_repository import product_repository
#
# and calls its methods directly:
#
#     product_repository.save(product)
#     product_repository.find_by_id(id_)
#     ...
#
# Sharing a single instance is safe because ``ProductRepository``
# carries no instance state — every method delegates to ``db.session``,
# which Flask-SQLAlchemy scopes per-request via Flask's application
# context. So even under Flask's threaded WSGI server (multiple
# request threads sharing the same Python process), each thread sees
# its own ``db.session`` despite all threads sharing the same
# ``product_repository`` reference.
#
# Tests that want to override the repository for mocking can either:
#   * Monkeypatch ``repository.product_repository.product_repository``
#     to a test double in a pytest fixture, OR
#   * Instantiate a fresh ``ProductRepository()`` directly (the class
#     is exported alongside the singleton for this purpose).
# ---------------------------------------------------------------------------
product_repository = ProductRepository()
