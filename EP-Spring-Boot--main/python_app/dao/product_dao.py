"""DAO façade for the ``Product`` entity.

Translation of
``EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/dao/ProductDao.java``
(Spring Boot ``@Repository``-annotated ``ProductDao`` class).

Role in the layered architecture
--------------------------------
The DAO sits between the controller layer
(``controller/product_controller.py``) and the repository layer
(``repository/product_repository.py``). Per AAP §0.4.3, the layered call
chain on both sides of the port is identical::

    Controller -> DAO (façade) -> Repository -> ORM/SQL

The Java side declares ``ProductDao`` as a Spring ``@Repository`` bean
that holds an ``@Autowired ProductRepository productRepository`` field.
The Python translation is an explicit class whose methods delegate to
the module-level ``product_repository`` singleton (the Python equivalent
of Spring's auto-wired repository bean).

Most methods are thin one-line delegations to ``product_repository``;
the only methods that carry meaningful business logic are:

* ``get_product_by_id_dao`` — preserves the F-006 null-on-miss parity
  quirk (returns ``None`` rather than raising).
* ``update_product_dao`` — implements the partial-update pattern (load
  existing record, copy mutable fields, save) and raises a parity-
  contract exception on miss.

Java -> Python method mapping (per AAP §0.5.1)
----------------------------------------------
=========================================  =============================================
Java method                                 Python method
=========================================  =============================================
``saveProductDao(Product)``                 ``save_product_dao(product)``
``saveMultipleProductDao(List<Product>)``   ``save_multiple_product_dao(products)``
``displayAllProductDao()``                  ``display_all_product_dao()``
``getProductByIdDao(Integer id)``           ``get_product_by_id_dao(id_)``
``getProductByNameDao(String name)``        ``get_product_by_name_dao(name)``
``getProductByPriceDao(double price)``      ``get_product_by_price_dao(price)``
``deleteProductByPriceDao(double price)``   ``delete_product_by_price_dao(price)``
``updateProductDao(Product, Integer id)``   ``update_product_dao(product, id_)``
=========================================  =============================================

Parity-critical invariants (per AAP §0.7.2)
-------------------------------------------
The following four invariants MUST be preserved exactly. Each one maps
to a documented behavioral parity requirement and any deviation would
alter externally observable behavior.

1. **``get_product_by_id_dao`` returns ``None`` on miss, never raises.**
   The F-006 endpoint (``GET /product/getProduct/{id}``) responds with
   HTTP 200 and a body of ``null`` when the record is not found —
   NOT HTTP 404. The DAO MUST propagate the repository's ``None`` return
   value through to the controller without converting it to an exception
   or a default empty ``Product``. This preserves the
   ``optional.isPresent() ? optional.get() : null`` semantic from the
   Java source (line 39).

2. **``update_product_dao`` raises ``Exception("Product not found with
   ID: <id>")`` on miss.** The exception type is plain ``Exception``
   (NOT ``RuntimeError``, NOT a custom subclass) so that the F-009
   controller — which propagates the exception unhandled — produces
   an HTTP 500 with that exact message in the response body, matching
   Java's ``RuntimeException`` behavior under Spring's default error
   handler. The F-010 controller catches the exception and returns
   HTTP 404 instead — the dual behavior is preserved. The DAO MUST NOT
   call ``flask.abort(...)`` here; that responsibility belongs to the
   controller layer.

3. **``update_product_dao`` preserves the existing record's ``id``.**
   The Java code (line 64) carries an explicit comment
   "Do NOT update the ID; it should remain unchanged" — only the
   ``name``, ``color``, and ``price`` fields are copied from the
   incoming ``product`` argument onto the loaded existing record. Even
   if the incoming argument carries an ``id`` field, the DAO MUST
   ignore it and persist the original primary key unchanged.

4. **The Spring ``@Autowired`` -> module-level singleton bridge.** A
   module-level ``product_dao = ProductDao()`` instance is exported at
   the bottom of this file. The controller imports the lowercase name::

       from dao.product_dao import product_dao

   This pattern (instantiate once at module-import time) preserves the
   singleton semantics of Spring's component container while remaining
   a trivial Python idiom. Tests that need an isolated instance can
   construct ``ProductDao()`` directly.

Out of scope (per AAP §0.3.2)
-----------------------------
The Java DAO is intentionally thin — eight delegation methods, no
caching, no logging, no soft-delete, no audit fields. The Python
translation MUST NOT add any of these enhancements:

* No ``@functools.lru_cache`` or any cache layer.
* No bulk-update helper (``update_all`` and similar).
* No soft-delete pattern (``is_deleted`` flags, etc.).
* No read/write logging or telemetry.
* No validation in the DAO — that is marshmallow's job (in
  ``schemas/product_schema.py``).
* No async variants (this is a sync Flask app).
* No pagination on ``display_all_product_dao`` — Java does not
  paginate, so the Python side does not either.

The Sk-21-Rule (``# SK-QA`` tail comment on every line) is scoped to
``server.py`` only and does NOT apply to this module per AAP §0.7.1.

Exports
-------
ProductDao : type
    The DAO façade class with eight methods (``save_product_dao``,
    ``save_multiple_product_dao``, ``display_all_product_dao``,
    ``get_product_by_id_dao``, ``get_product_by_name_dao``,
    ``get_product_by_price_dao``, ``delete_product_by_price_dao``,
    ``update_product_dao``). Tests may instantiate fresh copies if
    they want isolated state.
product_dao : ProductDao
    Module-level singleton instance — the canonical entry point used
    by ``controller/product_controller.py``. Created at import time
    via ``product_dao = ProductDao()``.
"""

# ---------------------------------------------------------------------------
# Standard library type-hint imports.
#
# Python's ``typing`` module supplies the generic type aliases used in
# the method signatures below:
#
#   * ``List[Product]``     return type for ``save_multiple_product_dao``,
#                            ``display_all_product_dao``,
#                            ``get_product_by_name_dao``, and
#                            ``get_product_by_price_dao``. Mirrors Java's
#                            ``List<Product>`` byte-for-byte.
#
#   * ``Optional[Product]`` return type for ``get_product_by_id_dao``.
#                            Mirrors Java's ``Optional<Product>`` semantic
#                            ("a value or no value", expressed in Python
#                            as ``Product | None``). The ``None`` return
#                            on miss is the F-006 parity quirk.
#
# The PEP 585 ``list[...]`` and PEP 604 ``X | None`` syntaxes would also
# work on Python 3.9+/3.10+ respectively, but the project's stated
# minimum (``requires-python = ">=3.9"`` in ``pyproject.toml``) does not
# universally support PEP 604 unioning. Using the ``typing`` module is
# the lowest-common-denominator choice that works under every supported
# Python version (3.9, 3.10, 3.11, 3.12). It also matches the
# convention already in use in ``repository/product_repository.py``.
# ---------------------------------------------------------------------------
from typing import List, Optional

# ---------------------------------------------------------------------------
# Application-internal imports.
#
# Per the established codebase convention (verified against
# ``extensions.py``, ``config.py``, ``entity/product.py``,
# ``repository/product_repository.py``, ``responses/response_structure.py``,
# ``schemas/product_schema.py``, ``controller/student_controller.py``,
# and ``tests/test_app.py``), imports use SIMPLE module names — NOT a
# ``python_app.`` prefix.
#
# The runtime entry point ``server.py`` runs from inside the
# ``EP-Spring-Boot--main/python_app/`` directory, which puts that
# directory on ``sys.path`` so bare module names resolve correctly. The
# pytest configuration in ``pyproject.toml`` also runs from that working
# directory. Using ``python_app.`` as a package prefix would break both
# the runtime and the test discovery.
#
#   * ``Product``            the SQLAlchemy ORM model from
#                             ``entity/product.py``. Used here as a
#                             type hint on the DAO method signatures
#                             (e.g., ``def save_product_dao(self,
#                             product: Product) -> Product``) and as
#                             the runtime type that flows through the
#                             update path (returned from
#                             ``product_repository.find_by_id`` and
#                             passed back into ``product_repository.save``).
#
#   * ``product_repository`` the module-level ``ProductRepository``
#                             singleton from
#                             ``repository/product_repository.py``. All
#                             eight DAO methods delegate to it. This is
#                             the Python equivalent of the Spring
#                             ``@Autowired ProductRepository productRepository``
#                             field-injection pattern in the Java source
#                             (lines 20-21).
# ---------------------------------------------------------------------------
from entity.product import Product
from repository.product_repository import product_repository


class ProductDao:
    """DAO façade for ``Product`` persistence operations.

    Mirrors ``dao/ProductDao.java`` method-for-method (Java camelCase ->
    Python snake_case). All eight methods delegate to the
    ``product_repository`` singleton, with the exception of two methods
    that carry parity-critical business logic:

    * ``get_product_by_id_dao`` propagates the repository's ``None``
      return on miss instead of raising — preserving the F-006
      null-on-miss parity quirk.

    * ``update_product_dao`` implements the Java side's partial-update
      pattern: load the existing record, copy ONLY ``name``, ``color``,
      and ``price`` (preserving ``id``), and save. On miss, raises
      ``Exception("Product not found with ID: <id>")`` to match the
      Java ``RuntimeException`` semantics.

    There is no inheritance and no decorator on this class — instances
    are plain Python objects whose methods route through
    ``product_repository``. The class is instantiated exactly once at
    the bottom of this module (``product_dao = ProductDao()``); the
    controller layer imports that lowercase singleton.

    Methods
    -------
    save_product_dao(product)
        Persist a single ``Product``. Translation of
        ``saveProductDao(Product)``.
    save_multiple_product_dao(products)
        Persist a list of ``Product`` instances in one batch.
        Translation of ``saveMultipleProductDao(List<Product>)``.
    display_all_product_dao()
        Return every ``Product`` row. Translation of
        ``displayAllProductDao()``.
    get_product_by_id_dao(id_)
        Look up a ``Product`` by primary key. Returns ``None`` on miss
        (NOT raises). Translation of ``getProductByIdDao(Integer)``.
    get_product_by_name_dao(name)
        Find every ``Product`` with the given ``name``. Translation of
        ``getProductByNameDao(String)``.
    get_product_by_price_dao(price)
        Find every ``Product`` at the given ``price`` (native SQL).
        Translation of ``getProductByPriceDao(double)``.
    delete_product_by_price_dao(price)
        Delete every ``Product`` at the given ``price`` (native SQL).
        Translation of ``deleteProductByPriceDao(double)``.
    update_product_dao(product, id_)
        Update mutable fields of an existing ``Product``, preserving
        its ``id``. Raises on miss. Translation of
        ``updateProductDao(Product, Integer)``.
    """

    # ------------------------------------------------------------------
    # save_product_dao
    #
    # Translation of Java lines 23-25::
    #
    #     public Product saveProductDao(Product product) {
    #         return productRepository.save(product);
    #     }
    #
    # A direct one-line delegation to ``product_repository.save``. The
    # repository implementation calls ``db.session.add(product)`` then
    # ``db.session.commit()`` so the row is durably persisted by the
    # time this method returns. The returned ``Product`` is the same
    # instance the caller passed in (now managed by the SQLAlchemy
    # session).
    # ------------------------------------------------------------------
    def save_product_dao(self, product: Product) -> Product:
        """Persist a single ``Product`` and return it.

        Translation of Java ``saveProductDao(Product)``::

            return productRepository.save(product);

        Parameters
        ----------
        product : Product
            The fully-constructed ``Product`` instance to insert. The
            caller MUST set ``product.id`` because the entity has
            ``autoincrement=False`` per AAP §0.7.2.

        Returns
        -------
        Product
            The same ``product`` instance, now persisted and managed
            by the SQLAlchemy session.
        """
        return product_repository.save(product)

    # ------------------------------------------------------------------
    # save_multiple_product_dao
    #
    # Translation of Java lines 27-29::
    #
    #     public List<Product> saveMultipleProductDao(List<Product> product) {
    #         return productRepository.saveAll(product);
    #     }
    #
    # Note that the Java parameter is named ``product`` (singular)
    # despite holding a list — likely an oversight in the original
    # source. The Python parameter uses ``products`` (plural) because
    # the AAP explicitly specifies that name in §0.5.2 and Section 6 of
    # the agent prompt's "Java -> Python Mapping" table. The plural form
    # is also more idiomatic and avoids misleading future readers.
    # ------------------------------------------------------------------
    def save_multiple_product_dao(
        self, products: List[Product]
    ) -> List[Product]:
        """Persist a list of ``Product`` instances and return them.

        Translation of Java ``saveMultipleProductDao(List<Product>)``::

            return productRepository.saveAll(product);

        Parameters
        ----------
        products : List[Product]
            A list of fully-constructed ``Product`` instances. Each
            element MUST have ``id`` set (no autoincrement on the
            entity per AAP §0.7.2). The list MAY be empty.

        Returns
        -------
        List[Product]
            The same list the caller passed in, with every element
            now persisted and managed.
        """
        return product_repository.save_all(products)

    # ------------------------------------------------------------------
    # display_all_product_dao
    #
    # Translation of Java lines 31-33::
    #
    #     public List<Product> displayAllProductDao() {
    #         return productRepository.findAll();
    #     }
    #
    # Returns an UNBOUNDED list — Java does not paginate (the
    # controller method ``findAllProductController`` simply delegates
    # to this DAO method with no ``Pageable`` argument), and the Python
    # translation does not either per AAP §0.3.2 ("Out of scope:
    # pagination/sorting").
    # ------------------------------------------------------------------
    def display_all_product_dao(self) -> List[Product]:
        """Return every ``Product`` row.

        Translation of Java ``displayAllProductDao()``::

            return productRepository.findAll();

        Returns
        -------
        List[Product]
            Every row in the ``product`` table. The list MAY be empty.
            Insertion order is NOT guaranteed by either side; the
            underlying SQL is ``SELECT * FROM product`` with no
            ``ORDER BY`` clause.
        """
        return product_repository.find_all()

    # ------------------------------------------------------------------
    # get_product_by_id_dao  -- F-006 null-on-miss parity quirk
    #
    # Translation of Java lines 35-41::
    #
    #     public Product getProductByIdDao(Integer id) {
    #         Optional<Product> optional = productRepository.findById(id);
    #         return optional.isPresent() ? optional.get() : null;
    #     }
    #
    # The Python translation is shorter because Python's ``None`` is
    # the natural equivalent of Java's ``Optional.empty()`` and
    # ``db.session.get(Product, id_)`` returns ``None`` directly on
    # miss — there is no need for an explicit ``isPresent()`` check.
    # Returning the repository result directly preserves the exact
    # F-006 semantics: a missing record yields ``None``, which the
    # controller serializes as JSON ``null`` with HTTP 200.
    #
    # CRITICAL (per AAP §0.7.2): this method MUST NOT raise on miss.
    # Doing so would convert the externally observable F-006 response
    # from "200 OK with body null" to "404 Not Found" (or "500
    # Internal Server Error" if the exception propagates), violating
    # the parity contract.
    # ------------------------------------------------------------------
    def get_product_by_id_dao(self, id_: int) -> Optional[Product]:
        """Look up a ``Product`` by primary key.

        Translation of Java ``getProductByIdDao(Integer id)``::

            Optional<Product> optional = productRepository.findById(id);
            return optional.isPresent() ? optional.get() : null;

        **CRITICAL — F-006 null-on-miss parity:** Returns ``None`` on
        miss (does NOT raise). The controller serializes ``None`` as
        JSON ``null`` with HTTP 200, preserving the documented
        behavioral quirk per AAP §0.7.2 — a missing record produces a
        ``null`` body rather than HTTP 404.

        Parameters
        ----------
        id_ : int
            The primary key value to look up. The trailing underscore
            avoids shadowing Python's built-in ``id()``.

        Returns
        -------
        Optional[Product]
            The matching ``Product`` instance, or ``None`` if no row
            has that primary key. NEVER raises on miss.
        """
        return product_repository.find_by_id(id_)

    # ------------------------------------------------------------------
    # get_product_by_name_dao
    #
    # Translation of Java lines 43-47::
    #
    #     public List<Product> getProductByNameDao(String name) {
    #         return productRepository.findByName(name);
    #     }
    #
    # The Java method delegates to the Spring Data derived query
    # ``findByName(String)``, which is auto-generated from the method
    # name. The Python translation delegates to the repository's
    # explicit ``find_by_name`` method (which uses
    # ``filter_by(name=name)``).
    # ------------------------------------------------------------------
    def get_product_by_name_dao(self, name: str) -> List[Product]:
        """Find every ``Product`` whose ``name`` column matches exactly.

        Translation of Java ``getProductByNameDao(String name)``::

            return productRepository.findByName(name);

        Parameters
        ----------
        name : str
            The exact name to match. Case-sensitivity depends on the
            collation of the underlying ``name`` column.

        Returns
        -------
        List[Product]
            Every ``Product`` row with matching ``name``. The list
            MAY be empty.
        """
        return product_repository.find_by_name(name)

    # ------------------------------------------------------------------
    # get_product_by_price_dao
    #
    # Translation of Java lines 49-51::
    #
    #     public List<Product> getProductByPriceDao(double price){
    #         return productRepository.getProductByPrice(price);
    #     }
    #
    # Backed by native SQL on the repository side:
    #   "select * from product where price=:price"
    # The native SQL string is preserved byte-for-byte from the Java
    # ``@Query(value = "select * from product where price=?",
    # nativeQuery = true)`` annotation; only the parameter style
    # changes (positional ``?`` -> named ``:price``).
    # ------------------------------------------------------------------
    def get_product_by_price_dao(self, price: float) -> List[Product]:
        """Find every ``Product`` at the given ``price`` using NATIVE SQL.

        Translation of Java ``getProductByPriceDao(double price)``::

            return productRepository.getProductByPrice(price);

        Parameters
        ----------
        price : float
            The exact price to match. SQLAlchemy parameterizes this
            value, so SQL injection is not possible regardless of
            input.

        Returns
        -------
        List[Product]
            Every ``Product`` row with matching ``price``. The list
            MAY be empty.
        """
        return product_repository.get_product_by_price(price)

    # ------------------------------------------------------------------
    # delete_product_by_price_dao
    #
    # Translation of Java lines 53-56::
    #
    #     public void deleteProductByPriceDao(double price) {
    #         productRepository.deleteProductByPrice(price);
    #     }
    #
    # The Java repository method carries ``@Modifying`` +
    # ``@Transactional`` annotations, which together instruct Spring to
    # commit the DELETE on method exit. The Python repository
    # implementation calls ``db.session.commit()`` explicitly inside
    # ``delete_product_by_price`` to preserve that semantic.
    #
    # The DAO method itself returns ``None`` (Java's ``void``); the row
    # count is not exposed to the controller.
    # ------------------------------------------------------------------
    def delete_product_by_price_dao(self, price: float) -> None:
        """Delete every ``Product`` at the given ``price`` using NATIVE SQL.

        Translation of Java ``deleteProductByPriceDao(double price)``::

            productRepository.deleteProductByPrice(price);

        Parameters
        ----------
        price : float
            The exact price to match. Every row in the ``product``
            table whose ``price`` column equals this value is deleted
            in one DELETE statement.

        Returns
        -------
        None
            Mirrors Java's ``void`` return type. The row count is
            NOT returned.
        """
        product_repository.delete_product_by_price(price)

    # ------------------------------------------------------------------
    # update_product_dao  -- partial-update pattern + F-009/F-010 parity
    #
    # Translation of Java lines 58-74::
    #
    #     public Product updateProductDao(Product product, Integer id) {
    #         Optional<Product> optional = productRepository.findById(id);
    #         if (optional.isPresent()) {
    #             Product existingProduct = optional.get();
    #             // Do NOT update the ID; it should remain unchanged
    #             existingProduct.setName(product.getName());
    #             existingProduct.setColor(product.getColor());
    #             existingProduct.setPrice(product.getPrice());
    #             return productRepository.save(existingProduct);
    #         } else {
    #             // You can handle not found case as you wish
    #             throw new RuntimeException("Product not found with ID: " + id);
    #         }
    #     }
    #
    # CRITICAL parity points (per AAP §0.7.2):
    #
    # 1. **Exception type:** plain ``Exception`` (NOT ``RuntimeError``,
    #    NOT a custom subclass). The F-009 controller propagates the
    #    exception unhandled, producing HTTP 500 with the message in
    #    the body — matching the Java ``RuntimeException`` behavior
    #    under Spring's default error handler. The F-010 controller
    #    catches the exception and returns HTTP 404 — both behaviors
    #    coexist via a single exception type that the controller layer
    #    chooses to propagate or catch.
    #
    # 2. **Exception message:** EXACTLY ``"Product not found with ID:
    #    <id>"``, formatted via an f-string. The message is part of
    #    the externally observable HTTP 500 response body for F-009;
    #    changing it would alter the wire-level API.
    #
    # 3. **Preserve the existing record's ``id``:** copy ONLY ``name``,
    #    ``color``, and ``price`` from the incoming argument onto the
    #    loaded ``existing`` instance. Even if the incoming argument
    #    carries an ``id`` field, the DAO MUST ignore it and persist
    #    the original primary key unchanged.
    #
    # 4. **Do NOT call ``flask.abort()``:** that responsibility belongs
    #    to the F-010 controller. The DAO MUST only RAISE; the
    #    controller decides whether to propagate (F-009) or catch
    #    (F-010).
    #
    # The dual-input handling (dict OR Product instance) is a defensive
    # measure that accommodates both:
    #   * The marshmallow-deserialized dict produced by
    #     ``@blp.arguments(ProductSchema)`` (the default flask-smorest
    #     behavior).
    #   * A pre-converted ``Product`` instance (if the controller chose
    #     to convert dict -> Product before calling the DAO).
    # The Java side never sees a dict because Jackson auto-binds the
    # request body directly to a ``Product``; the Python translation
    # supports both forms so the DAO is robust regardless of the
    # controller's pre-processing decision.
    # ------------------------------------------------------------------
    def update_product_dao(self, product, id_: int) -> Product:
        """Update mutable fields of an existing ``Product``, preserving its id.

        Translation of Java ``updateProductDao(Product, Integer id)``::

            Optional<Product> optional = productRepository.findById(id);
            if (optional.isPresent()) {
                Product existingProduct = optional.get();
                // Do NOT update the ID; it should remain unchanged
                existingProduct.setName(product.getName());
                existingProduct.setColor(product.getColor());
                existingProduct.setPrice(product.getPrice());
                return productRepository.save(existingProduct);
            } else {
                throw new RuntimeException("Product not found with ID: " + id);
            }

        **CRITICAL — F-009/F-010 dual-behavior parity (AAP §0.7.2):**
        On miss, raises ``Exception("Product not found with ID: <id>")``
        with the EXACT message string. The exception type is plain
        ``Exception`` (NOT ``RuntimeError``, NOT a custom subclass) so
        that the F-009 controller can let it propagate unhandled
        (producing HTTP 500) while the F-010 controller catches it and
        returns HTTP 404. The DAO MUST NOT call ``flask.abort()`` —
        that's the controller's responsibility.

        **CRITICAL — id preservation (AAP §0.7.2):** Copies ONLY
        ``name``, ``color``, and ``price`` from the incoming argument
        onto the loaded existing record. Even if the incoming argument
        carries an ``id`` field, the DAO MUST ignore it and persist
        the original primary key unchanged. This matches the Java
        comment "Do NOT update the ID; it should remain unchanged"
        (Java line 64).

        Parameters
        ----------
        product : Product or dict
            The new field values to apply. Accepts either:

            * A ``Product`` ORM instance (controller pre-converted
              the input).
            * A ``dict`` (raw output from marshmallow's
              ``@blp.arguments(ProductSchema)`` deserialization —
              the default flask-smorest behavior).

            Only the ``name``, ``color``, and ``price`` keys/attributes
            are read; any ``id`` key/attribute is ignored.
        id_ : int
            The primary key of the record to update. The trailing
            underscore avoids shadowing Python's built-in ``id()``.

        Returns
        -------
        Product
            The updated ``Product`` instance, persisted and managed.
            Its ``id`` is preserved from the original record (NOT
            changed to any ``id`` carried by the incoming argument).

        Raises
        ------
        Exception
            With message ``"Product not found with ID: <id>"`` when
            no row has the given primary key. Plain ``Exception`` (NOT
            a subclass) so F-009 produces HTTP 500 and F-010 produces
            HTTP 404 via the controller's choice of propagate-vs-catch.
        """
        # Step 1: load the existing record by primary key. The
        # repository's ``find_by_id`` returns ``None`` on miss (it
        # uses ``db.session.get(Product, id_)`` internally) — the
        # Python equivalent of Java's ``Optional.empty()``.
        existing = product_repository.find_by_id(id_)

        # Step 2: handle the miss case with a parity-contract exception.
        # The exception type is plain ``Exception`` (NOT
        # ``RuntimeError``, NOT a custom subclass); the message is
        # EXACTLY ``"Product not found with ID: <id>"`` to match the
        # Java ``RuntimeException`` message string byte-for-byte. The
        # F-009 controller propagates this exception (producing HTTP
        # 500) and the F-010 controller catches it (producing HTTP
        # 404) — both behaviors coexist via the controller's choice.
        if existing is None:
            raise Exception(f"Product not found with ID: {id_}")

        # Step 3: copy ONLY the mutable fields onto the existing
        # record. The ``existing.id`` is intentionally NOT touched —
        # this preserves the Java side's "Do NOT update the ID; it
        # should remain unchanged" semantic (Java line 64).
        #
        # The incoming ``product`` argument may be a dict (from
        # marshmallow's ``@blp.arguments(ProductSchema)``
        # deserialization, which by default produces a dict) OR a
        # ``Product`` instance (if the controller pre-converted the
        # dict). The Java side only ever saw a ``Product`` because
        # Jackson auto-binds the request body to the typed parameter;
        # the Python translation supports both forms via an
        # ``isinstance`` check.
        if isinstance(product, dict):
            # dict path: use ``.get(...)`` to safely read each key.
            # Missing keys yield ``None`` (matching the behavior when
            # a Product instance has an unset attribute).
            existing.name = product.get("name")
            existing.color = product.get("color")
            existing.price = product.get("price")
        else:
            # Product instance path: use direct attribute access.
            # This mirrors the Java ``existingProduct.setName(
            # product.getName())`` etc. one-for-one.
            existing.name = product.name
            existing.color = product.color
            existing.price = product.price

        # Step 4: persist the modified record and return it. The
        # repository's ``save`` calls ``db.session.add`` (a no-op for
        # an already-managed instance) and ``db.session.commit()``,
        # so the row is durably persisted by the time this method
        # returns. The returned instance is the same ``existing``
        # object, now reflecting the applied updates.
        return product_repository.save(existing)


# ---------------------------------------------------------------------------
# Module-level singleton.
#
# This is the Python equivalent of Spring's auto-instantiated
# ``@Repository`` bean (see ``ProductDao.java`` line 17 ``@Repository``
# annotation). The class is constructed exactly once at module-import
# time, and the instance is shared across the entire process —
# importers obtain a reference to the same instance regardless of where
# or when they call::
#
#     from dao.product_dao import product_dao
#
# The controller module (``controller/product_controller.py``) imports
# this lowercase name and calls its methods directly:
#
#     product_dao.save_product_dao(product)
#     product_dao.get_product_by_id_dao(id_)
#     product_dao.update_product_dao(product, id_)
#     ...
#
# Sharing a single instance is safe because ``ProductDao`` carries no
# instance state — every method delegates to ``product_repository``,
# which itself is stateless and routes through ``db.session`` (which
# Flask-SQLAlchemy scopes per-request). So even under Flask's threaded
# WSGI server, each request thread sees its own ``db.session`` despite
# all threads sharing the same ``product_dao`` reference.
#
# Tests that want to override the DAO for mocking can either:
#   * Monkeypatch ``dao.product_dao.product_dao`` to a test double in
#     a pytest fixture, OR
#   * Instantiate a fresh ``ProductDao()`` directly (the class is
#     exported alongside the singleton for this purpose).
# ---------------------------------------------------------------------------
product_dao = ProductDao()
