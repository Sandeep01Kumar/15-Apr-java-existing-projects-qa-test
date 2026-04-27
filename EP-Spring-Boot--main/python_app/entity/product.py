"""SQLAlchemy ORM model for the ``product`` table.

Translation of
``EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/entity/Product.java``
(Spring Boot ``@Entity`` + Lombok ``@Data`` + JPA ``@Id`` + Swagger
``@Schema``).

This module is the foundation of the persistence stack — every other
module that touches Product data (``controller/product_controller.py``,
``dao/product_dao.py``, ``repository/product_repository.py``,
``schemas/product_schema.py``) imports the ``Product`` class declared
here. It maps a Python class onto the physical ``product`` table that
the original Java application persists to via Hibernate.

Parity-critical invariants (per AAP §0.7.2)
-------------------------------------------
The following four invariants MUST be preserved byte-for-byte against
the Java implementation; any deviation would cause externally
observable behavioral drift and violate the parity contract:

1. ``__tablename__`` MUST be ``"product"`` (lowercase, singular).
   Hibernate's default JPA naming strategy maps ``@Entity`` class
   ``Product`` to physical table ``product``. The native SQL strings
   in ``repository/product_repository.py`` are preserved verbatim from
   the Java side::

       SELECT * FROM product WHERE price = :price
       DELETE FROM product WHERE price = :price

   These statements reference the literal table name ``product``. If
   ``__tablename__`` is anything else (``"products"``, ``"Product"``,
   ``"product_table"``, etc.), the native SQL fails at runtime with
   ``OperationalError: no such table``.

2. The four column names MUST be exactly ``id``, ``name``, ``color``,
   ``price`` — matching the Java field names in ``Product.java`` lines
   15–20. The native SQL ``... WHERE price = :price`` references column
   ``price`` directly; renaming any column breaks the native SQL.
   Likewise, the Spring Data derived query ``findByName(name)``
   (translated to ``filter_by(name=name)`` in the repository) requires
   the attribute to be named ``name``.

3. The ``id`` column MUST have ``autoincrement=False`` because the Java
   ``@Id`` annotation appears WITHOUT ``@GeneratedValue``. Clients are
   expected to supply ``id`` explicitly on every write — this is a
   documented behavioral parity requirement (AAP §0.7.2: "The
   ``Product`` entity has no auto-generated primary key — clients must
   continue to supply ``id`` on writes."). SQLAlchemy's default for an
   ``Integer`` primary key is ``autoincrement="auto"``, which would
   silently change the observable behavior versus Java; making this
   explicit prevents accidental drift.

4. The ``__repr__`` method MUST produce a Lombok-``@Data``-style
   string ``Product(id=..., name=..., color=..., price=...)`` so that
   the ``print(product)`` / ``print(products)`` debug calls in
   ``controller/product_controller.py`` produce stdout output equivalent
   to Java's ``System.out.println(product)`` / ``System.out.println(products)``
   (which renders Lombok's auto-generated ``toString()`` via
   ``ProductController.java`` lines 56 and 76 of the original source).

Java → Python construct mapping (per AAP §0.4.1, §0.5.1)
--------------------------------------------------------
========================================  =====================================================
Java construct                            Python equivalent
========================================  =====================================================
``package com.jspider...entity;``         (Python uses directory-based packages; no statement)
``@Entity``                               ``class Product(db.Model):`` (declarative auto-register)
``@Data`` (Lombok)                        SQLAlchemy attribute access + manual ``__repr__``
``@Schema(name=..., description=...)``    Documented in ``schemas/product_schema.py`` (NOT here)
``@Id`` (no ``@GeneratedValue``)          ``primary_key=True, autoincrement=False``
``private int id;``                       ``id = db.Column(db.Integer, primary_key=True, ...)``
``private String name;``                  ``name = db.Column(db.String(255))``
``private String color;``                 ``color = db.Column(db.String(255))``
``@Schema(description=...)`` on price     (Field-level OpenAPI doc lives in product_schema.py)
``private double price;``                 ``price = db.Column(db.Float)``
========================================  =====================================================

Out-of-scope (per AAP §0.3.2)
-----------------------------
The Java ``Product`` entity is intentionally minimal — no Bean
Validation annotations, no foreign keys, no audit timestamps, no
indexes, no soft-delete columns. The Python translation MUST NOT add
any of these:

* No ``nullable=False``, ``CheckConstraint``, ``unique=True``, or
  similar column constraints.
* No ``ForeignKey``, ``relationship``, ``backref``, or other
  cross-entity wiring (there is only one entity in this application).
* No ``created_at`` / ``updated_at`` / ``deleted_at`` audit columns.
* No ``Index(...)`` declarations (Java has no ``@Index`` annotation).
* No ``@Schema`` metadata on the entity itself — that documentation
  lives in ``schemas/product_schema.py`` (the marshmallow schema)
  because the Python ecosystem cleanly separates the persistence
  concern from the JSON wire-format concern, whereas the Java side
  conflates them via Lombok + JPA + Jackson on the same class.

Exports
-------
Product : sqlalchemy.orm.DeclarativeBase subclass
    The ORM model for the ``product`` table. Imported by:

    * ``dao/product_dao.py`` — instantiates ``Product(...)`` and adds
      to ``db.session`` for save/update operations.
    * ``repository/product_repository.py`` — queries ``Product`` via
      ``db.session.query(Product)`` for derived-query operations.
    * ``controller/product_controller.py`` — converts validated
      marshmallow input into ``Product`` instances before passing to
      the DAO, and prints ``Product`` instances for debug parity with
      Java's ``System.out.println(product)``.
    * ``schemas/product_schema.py`` — DOES NOT import ``Product``
      directly (the schema is intentionally decoupled from the ORM
      model); it instead declares matching field names and types.
"""

# ---------------------------------------------------------------------------
# Imports.
#
# A SINGLE import is required: the ``db`` SQLAlchemy singleton from the
# top-level ``extensions`` module. ``db`` provides:
#
#   - ``db.Model``    declarative base class (subclassing this auto-
#                     registers the model with SQLAlchemy's metadata)
#   - ``db.Column``   column constructor (the SQLAlchemy 1.x/2.x style
#                     for declaring mapped columns)
#   - ``db.Integer``  integer column type (maps to MySQL INT)
#   - ``db.String``   string column type (maps to MySQL VARCHAR; requires
#                     a length argument for VARCHAR-flavor dialects)
#   - ``db.Float``    floating-point column type (maps to MySQL DOUBLE
#                     for the SQLAlchemy default precision; equivalent to
#                     Java ``double`` semantics)
#
# Per the established codebase convention (see ``extensions.py``,
# ``config.py``, ``responses/response_structure.py``,
# ``controller/student_controller.py``, ``schemas/product_schema.py``,
# ``tests/test_app.py``), imports use SIMPLE module names
# (``from extensions import db``) NOT a ``python_app.`` package prefix.
# The pytest configuration in ``pyproject.toml`` and the ``server.py``
# entry script both run from the ``EP-Spring-Boot--main/python_app/``
# working directory, which puts that directory on ``sys.path`` so bare
# module names resolve correctly.
#
# This file deliberately does NOT import:
#   * ``sqlalchemy`` directly  — all column types and constructors come
#                                through ``db`` for consistency with
#                                Flask-SQLAlchemy's recommended pattern.
#   * ``dataclasses``           — this is a SQLAlchemy ORM model, not a
#                                dataclass. Mixing the two requires
#                                special configuration and is unnecessary
#                                for the parity translation.
#   * ``marshmallow``           — JSON serialization concerns belong in
#                                ``schemas/product_schema.py``.
#   * ``flask`` / ``flask_smorest`` — this is a persistence concern;
#                                HTTP and OpenAPI live in the controllers.
# ---------------------------------------------------------------------------
from extensions import db


class Product(db.Model):
    """The ``product`` table ORM model.

    Mirrors
    ``EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/entity/Product.java``
    one-for-one. Lombok's ``@Data`` annotation (which auto-generates
    getters, setters, ``equals``, ``hashCode``, and ``toString`` on the
    Java side) is replaced by SQLAlchemy's automatic attribute access
    machinery (e.g. ``instance.name = "Widget"`` works without an
    explicit setter) plus the explicit ``__repr__`` defined below for
    stdout parity with the Java ``System.out.println(product)`` debug
    calls in ``ProductController.saveProductController`` (Java lines 56
    and 76 of the original ``ProductController.java``).

    Java ``@Schema(name = "product class", description = "this is product
    entity class")`` is intentionally NOT reproduced on this class. That
    OpenAPI documentation lives in ``schemas/product_schema.py`` — the
    Python ecosystem cleanly separates persistence (this module) from
    JSON wire format (the schemas module), unlike the Java side where
    ``@Entity``, ``@Data``, and ``@Schema`` all share the same class.

    Attributes
    ----------
    __tablename__ : str
        The physical table name. MUST be ``"product"`` (lowercase,
        singular) so that the native SQL strings in
        ``repository/product_repository.py`` execute against the same
        physical table the Java side targets.
    id : int
        Primary key. Maps to Java ``@Id private int id;`` (line 15 of
        the source). NOT auto-generated — the Java side omits
        ``@GeneratedValue``, so clients MUST supply ``id`` on every
        write. SQLAlchemy enforces this via ``autoincrement=False``.
    name : str
        Product name. Maps to Java ``private String name;`` (line 16).
        Backs the Spring Data derived query ``findByName(name)``
        (translated to ``filter_by(name=name)`` in the repository).
    color : str
        Product color. Maps to Java ``private String color;`` (line 17).
        No constraint on the value (no enum validation on the Java
        side, so the Python side mirrors that).
    price : float
        Product price. Maps to Java ``private double price;`` (line 20).
        Backs the native SQL queries ``select * from product where
        price=?`` and ``delete from product where price=?`` (translated
        to SQLAlchemy ``text(...)`` calls with the named parameter
        ``:price`` in the repository). Python ``float`` is 64-bit IEEE
        754, byte-equivalent to Java ``double``.

    Examples
    --------
    Construct a Product instance (clients MUST supply ``id``)::

        product = Product(id=1, name="Widget", color="blue", price=9.99)

    Persist via the request-scoped session (typically inside a DAO
    method, after the application has set up an app context)::

        db.session.add(product)
        db.session.commit()

    Print for debug (matches Java's ``System.out.println(product)``)::

        print(product)
        # -> Product(id=1, name=Widget, color=blue, price=9.99)
    """

    # -----------------------------------------------------------------
    # Table name binding.
    #
    # Hibernate's JPA default naming strategy on the Java side maps
    # ``@Entity`` class ``Product`` to physical table ``product``
    # (lowercase singular). SQLAlchemy's default would produce
    # ``product`` as well from a class ``Product``, but we set
    # ``__tablename__`` explicitly to:
    #
    #   1. Make the binding unambiguous and self-documenting (no need to
    #      remember which dialect/version of which framework would
    #      produce which name from which class name).
    #
    #   2. Pin the value to ``"product"`` regardless of any future
    #      change to SQLAlchemy's default naming convention.
    #
    #   3. Guarantee that the native SQL strings in
    #      ``repository/product_repository.py`` (``SELECT * FROM product
    #      WHERE price = :price`` and ``DELETE FROM product WHERE price
    #      = :price``) execute against the same physical table the Java
    #      side targets.
    #
    # CRITICAL: This value MUST remain exactly ``"product"``. Any
    # deviation (``"products"``, ``"Product"``, ``"product_table"``,
    # ``"PRODUCT"``, etc.) breaks the native SQL at runtime.
    # -----------------------------------------------------------------
    __tablename__ = "product"

    # -----------------------------------------------------------------
    # ``id`` — primary key column.
    #
    # Maps to Java ``@Id private int id;`` (line 15 of the source).
    # The Java code uses the bare ``@Id`` annotation WITHOUT
    # ``@GeneratedValue`` — meaning the application expects clients to
    # supply the primary key value on every write. This is documented
    # behavior per AAP §0.7.2 and Section 5.2.6 of the Technical
    # Specification.
    #
    # ``autoincrement=False`` is the parity-critical flag: SQLAlchemy's
    # default for an ``Integer`` primary key is ``autoincrement="auto"``,
    # which would cause SQLAlchemy to instruct the database to
    # auto-assign primary keys (or, on SQLite, set ROWID magic). Setting
    # this to ``False`` suppresses that auto-assignment so the Python
    # behavior matches the Java behavior byte-for-byte: clients supply
    # ``id`` or the INSERT fails at the database layer.
    #
    # ``primary_key=True`` is the equivalent of ``@Id`` — it tells
    # SQLAlchemy this column participates in the table's primary key.
    #
    # ``db.Integer`` maps to MySQL ``INT`` (which the Java side gets
    # implicitly from the field type ``int``).
    # -----------------------------------------------------------------
    id = db.Column(db.Integer, primary_key=True, autoincrement=False)

    # -----------------------------------------------------------------
    # ``name`` — product name column.
    #
    # Maps to Java ``private String name;`` (line 16 of the source).
    #
    # ``db.String(255)`` maps to MySQL ``VARCHAR(255)``. The length
    # argument is REQUIRED for VARCHAR-flavor dialects: SQLAlchemy
    # raises ``CompileError: VARCHAR requires a length on dialect
    # mysql`` if the length is omitted on a MySQL connection. 255 is
    # the conventional default — historically a single-byte length
    # prefix in MySQL row formats — and is what Hibernate's default
    # column generator produces for a Java ``String`` field with no
    # explicit ``@Column(length = ...)`` annotation. This means the
    # underlying MySQL DDL produced by Hibernate on the Java side and
    # by SQLAlchemy on the Python side is byte-equivalent.
    #
    # No ``nullable=False`` — the Java field has no validation
    # annotation declaring it mandatory (no ``@NotNull``, no
    # ``@NotBlank``, no ``@Column(nullable = false)``). To preserve
    # parity, the Python side MUST also allow nulls. Bean validation
    # would be a behavioral enhancement, which is out of scope per
    # AAP §0.3.2.
    #
    # This column also backs the Spring Data derived query
    # ``findByName(name)`` (Java line 17 of ``ProductRepository.java``,
    # translated to ``filter_by(name=name)`` in
    # ``repository/product_repository.py``), so the column name MUST
    # remain exactly ``name``.
    # -----------------------------------------------------------------
    name = db.Column(db.String(255))

    # -----------------------------------------------------------------
    # ``color`` — product color column.
    #
    # Maps to Java ``private String color;`` (line 17 of the source).
    # Same type and rationale as ``name`` above:
    #
    #   * ``db.String(255)`` maps to MySQL ``VARCHAR(255)``, matching
    #     Hibernate's default DDL for a Java ``String`` field.
    #   * No ``nullable=False`` — Java has no validation annotation
    #     here, so the Python side MUST also allow nulls.
    #   * No enum constraint — Java's ``String`` is unrestricted
    #     (any string value is accepted), so the Python side MUST
    #     also accept any string value.
    #
    # The column name MUST remain exactly ``color`` for consistency
    # with the JSON field name in ``schemas/product_schema.py`` and
    # with any client code that reads/writes the ``color`` field of
    # the response payload.
    # -----------------------------------------------------------------
    color = db.Column(db.String(255))

    # -----------------------------------------------------------------
    # ``price`` — product price column.
    #
    # Maps to Java ``private double price;`` (line 20 of the source),
    # which is preceded by the field-level annotation
    # ``@Schema(description = "price datatype is double")`` (line 18).
    # That OpenAPI documentation lives in
    # ``schemas/product_schema.py``, NOT here — the entity is the
    # persistence contract, the schema is the JSON contract.
    #
    # ``db.Float`` maps to MySQL ``DOUBLE`` (SQLAlchemy emits
    # ``FLOAT`` by default but the underlying MySQL representation for
    # a SQLAlchemy ``Float`` without precision is ``DOUBLE`` — the
    # same physical type that Hibernate generates for a Java
    # ``double`` field). Python ``float`` is 64-bit IEEE 754,
    # byte-equivalent to Java ``double``, so round-trip arithmetic
    # produces identical results on both sides.
    #
    # CRITICAL: The column name MUST remain exactly ``price``. The
    # native SQL strings in ``repository/product_repository.py``
    # reference the column directly:
    #
    #     SELECT * FROM product WHERE price = :price
    #     DELETE FROM product WHERE price = :price
    #
    # If this column is renamed, the native SQL fails with
    # ``OperationalError: no such column: price``.
    #
    # No ``nullable=False`` — same parity rationale as ``name`` and
    # ``color`` above.
    # -----------------------------------------------------------------
    price = db.Column(db.Float)

    def __repr__(self) -> str:
        """Return a Lombok-``@Data``-style debug string for stdout parity.

        This method exists SOLELY to make the Python ``print(product)``
        output byte-equivalent to Java's ``System.out.println(product)``.
        The Java side relies on Lombok's ``@Data`` annotation, which
        auto-generates a ``toString()`` method of the form::

            Product(id=1, name=Widget, color=blue, price=9.99)

        — that is, the simple class name followed by parenthesized
        ``field=value`` pairs separated by ``", "``. The Python
        implementation produces exactly this format.

        The method is invoked implicitly in two places (per AAP §0.7.2,
        which lists "debug ``System.out.println`` parity" as a mandatory
        behavioral preservation):

        1. ``ProductController.saveProductController(Product)`` (Java
           line 56) calls ``System.out.println(product)`` after
           receiving a single ``Product`` from the request body. The
           Python translation in
           ``controller/product_controller.py`` will call
           ``print(product)`` on the equivalent ``Product`` instance,
           which CPython routes through ``__repr__`` (since ``str()``
           on a class without ``__str__`` falls through to
           ``__repr__``).

        2. ``ProductController.saveProductController(List<Product>)``
           (Java line 76) calls ``System.out.println(products)`` on a
           ``List<Product>``. Java's ``ArrayList.toString()`` produces
           ``[Product(id=1, ...), Product(id=2, ...)]`` — bracket-
           wrapped, comma-separated element ``toString()`` outputs.
           Python's ``list.__repr__()`` produces the same bracket-
           wrapped, comma-separated format, calling ``__repr__`` on
           each element. With the Lombok-style ``__repr__`` defined
           here, the Python output of ``print(products)`` is
           byte-equivalent to the Java output of
           ``System.out.println(products)``.

        Returns
        -------
        str
            A string of the form
            ``"Product(id=<id>, name=<name>, color=<color>, price=<price>)"``
            with each placeholder replaced by ``str()`` of the
            corresponding attribute value. Specifically:

            * ``id`` → bare integer (e.g., ``1``) or ``None`` if unset.
            * ``name`` → bare string with no surrounding quotes (e.g.,
              ``Widget``) or ``None`` if unset.
            * ``color`` → bare string with no surrounding quotes (e.g.,
              ``blue``) or ``None`` if unset.
            * ``price`` → bare float (e.g., ``9.99``) or ``None`` if
              unset.

            The lack of surrounding quotes around string values matches
            Lombok's ``toString()`` behavior, which calls
            ``String.valueOf(...)`` on each field and does NOT wrap
            string values in quotes.

        Examples
        --------
        Single instance::

            >>> p = Product(id=1, name="Widget", color="blue", price=9.99)
            >>> repr(p)
            'Product(id=1, name=Widget, color=blue, price=9.99)'
            >>> print(p)
            Product(id=1, name=Widget, color=blue, price=9.99)

        Empty / partially-initialized instance (defaults to ``None``)::

            >>> p = Product()
            >>> repr(p)
            'Product(id=None, name=None, color=None, price=None)'
        """
        # The format string below uses two adjacent f-strings (Python's
        # implicit string concatenation) for readability; the runtime
        # behavior is identical to a single long f-string. The order
        # of fields (``id``, ``name``, ``color``, ``price``) MUST match
        # the Java field declaration order in ``Product.java`` lines
        # 15-20, since Lombok's auto-generated ``toString()`` uses
        # field declaration order.
        return (
            f"Product(id={self.id}, name={self.name}, "
            f"color={self.color}, price={self.price})"
        )
