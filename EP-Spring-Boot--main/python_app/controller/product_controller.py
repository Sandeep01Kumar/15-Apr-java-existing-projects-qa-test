"""REST controllers for the ``/product/*`` namespace.

This module is the Python translation of
``EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/controller/ProductController.java``.
It defines a flask-smorest ``Blueprint`` named ``blp`` and TEN
``MethodView``-based handler classes — one per Java endpoint method —
covering the ten endpoints documented in AAP §0.2.3.

==========  =======  ============================================  =======================================
Feature     Method   Path                                          Java method translated
==========  =======  ============================================  =======================================
F-012       GET      ``/product/getTodayDate``                     ``getTodaysDate()``
F-003       POST     ``/product/saveProduct``                      ``saveProductController(Product)``
F-004       POST     ``/product/saveProducts``                     ``saveProductController(List<Product>)``
F-005       GET      ``/product/findAllProduct``                   ``findAllProductController()``
F-006       GET      ``/product/getProduct/{id}``                  ``getProductByIdController(Integer)``
F-007       GET      ``/product/getProductByName/{name}``          ``getProductByNameDao(String)``
F-008       GET      ``/product/getProductByPrice/{price}``        ``getProductByPriceController(double)``
F-011       DELETE   ``/product/deleteProductByPrice/{price}``     ``deleteProductByPriceController(double)``
F-009       PUT      ``/product/updateProduct/{id}``               ``updateProductController(Product, Integer)``
F-010       PUT      ``/product/{id}``                             ``updateProduct(Product, Integer)``
==========  =======  ============================================  =======================================

Behavioral parity quirks preserved verbatim per AAP §0.7.2
---------------------------------------------------------
1. **F-006: GET /product/getProduct/<id> returns HTTP 200 with body
   ``null`` on miss** (NOT HTTP 404). The Java DAO returns ``null`` via
   ``optional.isPresent() ? optional.get() : null``; Spring's Jackson
   serializes that to a JSON ``null`` body with status 200. The Python
   translation returns ``jsonify(None)`` for the same wire output. We
   intentionally bypass ``@blp.response(200, ProductSchema)`` because
   marshmallow's ``Schema.dump(None)`` returns ``{}`` (empty dict), not
   ``null``, which would BREAK byte-for-byte parity with the Java side.

2. **F-009: PUT /product/updateProduct/<id> propagates the
   missing-record exception to HTTP 500.** The handler does NOT wrap
   the DAO call in try/except; the ``Exception("Product not found with
   ID: <id>")`` raised by ``ProductDao.update_product_dao`` propagates
   to Flask's default error handler, producing HTTP 500 with the
   exception message in the response body — matching Java's
   ``RuntimeException`` behavior under Spring's default error handler.

3. **F-010: PUT /product/<id> wraps the DAO call in try/except and
   returns HTTP 404 on miss.** Both update endpoints (F-009 and F-010)
   coexist with divergent error semantics: the SAME DAO call produces
   HTTP 500 on one endpoint and HTTP 404 on a sibling endpoint, purely
   because of try/except placement. This is the most surprising
   parity feature in the entire codebase and it is intentional.

4. **The trailing space character in the date echo (F-012) is
   preserved literally.** Java's ``LocalDate.now() + " "`` returns the
   ISO-8601 date string followed by exactly one space character; the
   Python equivalent is ``f"{date.today()} "`` (note the space before
   the closing quote). The space is part of the externally observable
   API surface and MUST NOT be removed.

5. **The debug ``print(product_data)`` and ``print(products_data)``
   calls in the save handlers match Java's ``System.out.println(product)``
   and ``System.out.println(products)``.** These calls preserve the
   stdout debug-output footprint that operators may rely on for
   visibility into request payloads.

6. **The typos ``Secessfully`` (in success messages) and ``something
   went wrong`` (in error messages) are preserved verbatim.** These
   strings appear in the externally observable JSON responses; clients
   may already be pattern-matching on these exact strings, so any
   correction would constitute a breaking API change.

7. **Only ``saveProduct`` (F-003) and ``updateProduct/<id>`` (F-009)
   use the ResponseStructure envelope.** The other eight endpoints
   return BARE payloads (no envelope). The Python translation follows
   the same envelope-vs-bare-payload pattern endpoint-for-endpoint.

CORS scoping
------------
CORS is configured in ``app.py`` — NOT here. The Java side's
``@CrossOrigin(value = "")`` annotation on ``ProductController`` (the
class) translates to::

    cors.init_app(app, resources={r"/product/*": {"origins": "*"}})

inside the ``create_app()`` factory in ``app.py``. The ``/student/*``
namespace remains CORS-free, exactly mirroring the Java behavior
(``StudentController`` carries no ``@CrossOrigin`` annotation). Adding
``@cross_origin`` decorators or initializing ``CORS()`` here would
create double-CORS headers and break the parity-precise scope.

Layered-architecture parity
---------------------------
The handler classes delegate to ``product_dao`` (the module-level
singleton imported from ``dao.product_dao``) — the Python equivalent
of Spring's ``@Autowired ProductDao productDao`` field injection. The
DAO in turn delegates to ``product_repository`` and ultimately to
``db.session``. This preserves the layered call chain documented in
AAP §0.4.3::

    Controller (this module)
       ↓
    DAO (dao/product_dao.py)
       ↓
    Repository (repository/product_repository.py)
       ↓
    ORM/SQL (entity/product.py + db.session)

Module-import contract
----------------------
Per the established codebase convention (see ``extensions.py``,
``config.py``, ``entity/product.py``, ``dao/product_dao.py``,
``repository/product_repository.py``, ``schemas/product_schema.py``,
``responses/response_structure.py``, ``controller/student_controller.py``,
``tests/test_app.py``), imports use SIMPLE module names (``from
dao.product_dao import product_dao``) NOT a ``python_app.`` package
prefix. The runtime entry point ``server.py`` and pytest both run from
the ``EP-Spring-Boot--main/python_app/`` working directory, which puts
that directory on ``sys.path`` so bare module names resolve correctly.

The Sk-21-Rule (``# SK-QA`` tail comment on every line) is scoped to
``server.py`` only and does NOT apply to this module per AAP §0.7.1.

Exports
-------
blp : flask_smorest.Blueprint
    The ``/product`` URL-prefix namespace. Imported by ``app.py`` as
    ``from controller.product_controller import blp as product_blp``
    and registered via ``api.register_blueprint(product_blp)``. The
    variable name is exactly ``blp`` (lowercase) and MUST NOT be
    renamed — ``app.py`` relies on it.
TodayDate : flask.views.MethodView
    Class-based handler for ``GET /product/getTodayDate``.
SaveProduct : flask.views.MethodView
    Class-based handler for ``POST /product/saveProduct`` (F-003).
SaveProducts : flask.views.MethodView
    Class-based handler for ``POST /product/saveProducts`` (F-004).
FindAllProduct : flask.views.MethodView
    Class-based handler for ``GET /product/findAllProduct`` (F-005).
GetProductById : flask.views.MethodView
    Class-based handler for ``GET /product/getProduct/<int:id>`` (F-006).
GetProductByName : flask.views.MethodView
    Class-based handler for ``GET /product/getProductByName/<string:name>`` (F-007).
GetProductByPrice : flask.views.MethodView
    Class-based handler for ``GET /product/getProductByPrice/<float:price>`` (F-008).
DeleteProductByPrice : flask.views.MethodView
    Class-based handler for ``DELETE /product/deleteProductByPrice/<float:price>`` (F-011).
UpdateProductWithEnvelope : flask.views.MethodView
    Class-based handler for ``PUT /product/updateProduct/<int:id>`` (F-009).
UpdateProduct : flask.views.MethodView
    Class-based handler for ``PUT /product/<int:id>`` (F-010).
"""

# ---------------------------------------------------------------------------
# Standard library imports.
#
#   * ``datetime.date``  — the Python analog of Java's
#                           ``java.time.LocalDate``. Used in the
#                           ``TodayDate`` handler to produce
#                           ``f"{date.today()} "`` (matching the Java
#                           expression ``LocalDate.now() + " "``).
#                           ``date.today()`` returns the local-date
#                           analog of ``LocalDate.now()``; its
#                           ``__str__`` produces an ISO-8601
#                           representation (``YYYY-MM-DD``) that is
#                           byte-identical to Java's
#                           ``LocalDate.toString()`` output. The
#                           literal trailing space character is the
#                           F-012 parity quirk per AAP §0.7.2.
# ---------------------------------------------------------------------------
from datetime import date

# ---------------------------------------------------------------------------
# Flask web framework primitives.
#
#   * ``abort``       — raises an ``HTTPException`` with a given status
#                        code. Used in the F-010 handler to produce a
#                        clean HTTP 404 response when the DAO raises on
#                        a missing record. Imported from ``flask`` (NOT
#                        from ``flask_smorest``) — the Flask built-in
#                        produces a clean ``HTTPException(404)`` that
#                        Flask's default error handler renders as the
#                        standard 404 page; ``flask_smorest.abort``
#                        wraps that with additional flask-smorest
#                        machinery that we don't need here.
#
#   * ``jsonify``     — serializes a Python object to a JSON response.
#                        Used in the F-006 handler to produce the
#                        literal JSON ``null`` body required by the
#                        null-on-miss parity quirk
#                        (``jsonify(None)`` -> body ``"null"`` with
#                        ``Content-Type: application/json``), AND to
#                        serialize the hit-path Product dict for the
#                        same endpoint
#                        (``jsonify(ProductSchema().dump(product))``).
#
#   * ``Response``    — Flask's WSGI response class (a thin subclass of
#                        ``werkzeug.wrappers.Response``). Used in the
#                        F-011 handler (``DELETE
#                        /product/deleteProductByPrice/<price>``) to
#                        produce a TRULY EMPTY response body that
#                        matches Java's ``void`` return semantics
#                        byte-for-byte. Returning a bare ``""`` string
#                        from a view decorated with
#                        ``@blp.response(200)`` causes flask-smorest to
#                        run the empty string through ``jsonify``,
#                        which serializes it as ``""\n`` (3 bytes) with
#                        ``Content-Type: application/json`` — that
#                        diverges from Java's ``void`` which produces a
#                        zero-byte body. Returning a pre-built
#                        ``Response("", status=200)`` instance instead
#                        is recognized by flask-smorest's
#                        ``isinstance(result_raw, Response)`` short
#                        circuit (see flask_smorest 0.47.0
#                        ``response.py``) and is forwarded as-is,
#                        producing ``Content-Length: 0`` and an empty
#                        body — the byte-for-byte parity that AAP
#                        §0.5.2 requires for F-011.
#
#   * ``MethodView``  — the base class for all ten class-based
#                        handlers in this module. flask-smorest's
#                        ``Blueprint.route`` decorator inspects the
#                        ``MethodView`` subclass and registers each of
#                        its ``get``/``post``/``put``/``delete``
#                        methods as a separate HTTP-verb route on the
#                        blueprint. This replaces Spring's
#                        ``@RestController`` plus method-level
#                        ``@GetMapping``/``@PostMapping``/``@PutMapping``/
#                        ``@DeleteMapping`` annotations from
#                        ``ProductController.java``. Imported from
#                        ``flask.views`` (the canonical location since
#                        Flask 2.0; older code may import from
#                        ``flask`` directly, but ``flask.views`` is the
#                        documented modern path).
# ---------------------------------------------------------------------------
from flask import Response, abort, jsonify
from flask.views import MethodView

# ---------------------------------------------------------------------------
# flask-smorest Blueprint.
#
#   * ``Blueprint``   — replaces Spring's
#                        ``@RequestMapping(value = "/product")`` at the
#                        class level. The Blueprint instance provides
#                        the ``@blp.route``, ``@blp.arguments``,
#                        ``@blp.response``, ``@blp.alt_response``, and
#                        ``@blp.doc`` decorators that collectively
#                        replace the Spring annotations
#                        ``@RequestMapping``, ``@RequestBody``,
#                        ``@ApiResponse``, and ``@Operation`` from
#                        ``ProductController.java``. flask-smorest
#                        auto-generates an OpenAPI 3.x specification
#                        from the resulting Blueprint + Schema metadata,
#                        which is then served by the bundled Swagger UI.
# ---------------------------------------------------------------------------
from flask_smorest import Blueprint

# ---------------------------------------------------------------------------
# Application-internal imports.
#
# Per the established codebase convention, imports use SIMPLE module
# names (NOT a ``python_app.`` prefix). The runtime entry point
# ``server.py`` runs from inside the ``EP-Spring-Boot--main/python_app/``
# directory and the pytest configuration in ``pyproject.toml`` does the
# same, so bare module names resolve correctly via ``sys.path``.
#
#   * ``product_dao``           — the module-level ``ProductDao``
#                                  singleton imported from
#                                  ``dao/product_dao.py``. Provides
#                                  the eight DAO methods
#                                  (``save_product_dao``,
#                                  ``save_multiple_product_dao``,
#                                  ``display_all_product_dao``,
#                                  ``get_product_by_id_dao``,
#                                  ``get_product_by_name_dao``,
#                                  ``get_product_by_price_dao``,
#                                  ``delete_product_by_price_dao``,
#                                  ``update_product_dao``) that the ten
#                                  controller handlers delegate to.
#                                  This is the Python equivalent of
#                                  Spring's ``@Autowired ProductDao
#                                  productDao`` field injection in
#                                  ``ProductController.java``.
#
#   * ``Product``               — the SQLAlchemy ORM model class for
#                                  the ``product`` table, imported from
#                                  ``entity/product.py``. Used in the
#                                  controller to convert
#                                  marshmallow-deserialized request
#                                  body dicts into ``Product`` ORM
#                                  instances (``Product(**product_data)``)
#                                  before passing to DAO methods that
#                                  expect ``Product`` instances.
#
#   * ``ResponseStructure``     — the response envelope dataclass
#                                  imported from
#                                  ``responses/response_structure.py``.
#                                  Instantiated per-request inside the
#                                  F-003 (``POST /product/saveProduct``)
#                                  and F-009
#                                  (``PUT /product/updateProduct/<id>``)
#                                  handlers to wrap the success/failure
#                                  responses. The marshmallow
#                                  ``ResponseStructureSchema`` then
#                                  serializes these instances to JSON
#                                  with camelCase keys (``statusCode``,
#                                  ``apiDescription``, ``data``)
#                                  preserving byte-for-byte parity
#                                  with the Java
#                                  ``ResponseStructure<Product>``
#                                  response shape.
#
#   * ``ProductSchema``         — the marshmallow schema for Product
#                                  JSON serialization/deserialization,
#                                  imported from
#                                  ``schemas/product_schema.py``. Used
#                                  as the
#                                  ``@blp.arguments(ProductSchema)``
#                                  request validator on POST/PUT
#                                  endpoints, as the
#                                  ``@blp.response(200, ProductSchema(many=True))``
#                                  response serializer on list-returning
#                                  endpoints, and called manually as
#                                  ``ProductSchema().dump(product)``
#                                  inside the F-006 ``GetProductById``
#                                  handler to render the hit-path JSON
#                                  body while preserving the
#                                  ``jsonify(None)`` fall-through for
#                                  the null-on-miss parity quirk.
#
#   * ``ResponseStructureSchema`` — the marshmallow schema for the
#                                  envelope, imported from
#                                  ``schemas/response_structure_schema.py``.
#                                  Used as
#                                  ``@blp.response(200, ResponseStructureSchema, ...)``
#                                  on the F-003 and F-009 handlers, and
#                                  as
#                                  ``@blp.alt_response(406, schema=ResponseStructureSchema, ...)``
#                                  for the documented validation-failure
#                                  envelope shape on those same two
#                                  endpoints.
# ---------------------------------------------------------------------------
from dao.product_dao import product_dao
from entity.product import Product
from responses.response_structure import ResponseStructure
from schemas.product_schema import ProductSchema
from schemas.response_structure_schema import ResponseStructureSchema


# ---------------------------------------------------------------------------
# Blueprint definition — the ``/product`` URL-prefix namespace.
#
# Mirrors the Java class-level annotations:
#
#   @RestController                                       (-> MethodView dispatch)
#   @RequestMapping(value = "/product")                   (-> url_prefix="/product")
#   @CrossOrigin(value = "")                              (-> configured in app.py, NOT here)
#   @Tag(name = "productcontroller",                      (-> description="this is controller class")
#        description = "this is controller class")
#
# The ``description`` keyword carries the Java ``@Tag(description =
# "this is controller class")`` description into the OpenAPI document.
# The Java ``@Tag(name = "productcontroller", ...)`` ``name`` attribute
# does NOT need to match exactly — flask-smorest derives the tag name
# from the Blueprint's ``name`` argument, which is ``"product"``. This
# is a minor cosmetic difference in the OpenAPI tag but does not
# affect any client behavior.
#
# The blueprint variable name MUST be exactly ``blp`` (lowercase) so
# that ``app.py``'s import statement
#     from controller.product_controller import blp as product_blp
# resolves correctly. Renaming this variable would break the
# application factory.
# ---------------------------------------------------------------------------
blp = Blueprint(
    "product",
    __name__,
    url_prefix="/product",
    description="this is controller class",
)


# ===========================================================================
# Handler 1: GET /product/getTodayDate (Feature F-012, product variant)
# ===========================================================================
# Translates Java ``ProductController.java`` lines 39-43:
#
#     @GetMapping(value = "/getTodayDate")
#     public String getTodaysDate() {
#         return LocalDate.now() + " ";
#     }
#
# Behavioral notes:
#   - Returns the ISO-8601 date string (e.g., "2026-04-27") followed by
#     exactly ONE space character.
#   - The trailing space character is mandatory parity per AAP §0.7.2;
#     do NOT remove it.
#   - Returns a plain ``str``; Flask serializes it as the text body
#     with ``Content-Type: text/html; charset=utf-8`` by default,
#     matching Spring's behavior for a ``String``-returning method.
#   - This endpoint duplicates ``StudentController.getTodaysDate()``
#     (handler ``StudentTodayDate`` in ``student_controller.py``) — two
#     parallel routes producing identical output. Both are preserved
#     as-is for parity with the Java side.
#   - No flask-smorest documentation decorators are applied — the Java
#     method has no ``@Operation`` or ``@ApiResponse`` annotations.
# ===========================================================================
@blp.route("/getTodayDate")
class TodayDate(MethodView):
    """``GET /product/getTodayDate`` — echo today's date with a trailing space.

    Translation of ``ProductController.getTodaysDate()`` (Java lines
    39-43). Per AAP §0.7.2 the trailing space character in
    ``LocalDate.now() + " "`` MUST be preserved literally — the Python
    equivalent is ``f"{date.today()} "`` (note the space character
    before the closing quote).

    Returns
    -------
    str
        The current date in ISO-8601 form (``YYYY-MM-DD``) followed by
        exactly one space character. Flask renders this as the response
        body with HTTP status 200.
    """

    def get(self):
        # ``date.today()`` returns the local-date analog of Java's
        # ``LocalDate.now()``. Its ``__str__`` produces the ISO-8601
        # representation (e.g., "2026-04-27"), which is the same
        # representation Java's ``LocalDate.toString()`` produces — so
        # the body is byte-identical to the Java response.
        #
        # The literal " " between the closing brace and the closing
        # quote of the f-string is the trailing-space parity quirk
        # (AAP §0.7.2). Do NOT strip or rstrip the result.
        return f"{date.today()} "


# ===========================================================================
# Handler 2: POST /product/saveProduct (Feature F-003)
# ===========================================================================
# Translates Java ``ProductController.java`` lines 45-73:
#
#     @PostMapping(value = "/saveProduct")
#     @Operation(description = "it will save one object at a time",
#         responses = {
#             @ApiResponse(responseCode = "200", description = "Product saved successfully"),
#             @ApiResponse(responseCode = "400", description = "Invalid input, object not saved"),
#             @ApiResponse(responseCode = "406", description = "Not acceptable, validation failed"),
#             @ApiResponse(responseCode = "500", description = "Internal server error") }
#     )
#     public ResponseStructure<Product> saveProductController(@RequestBody Product product) {
#         System.out.println(product);
#         Product product2 = productDao.saveProductDao(product);
#         if (product2 != null) {
#             responseStructure.setStatusCode(HttpStatus.OK.value());
#             responseStructure.setApiDescription("save product Secessfully...");
#             responseStructure.setData(product2);
#             return responseStructure;
#         } else {
#             responseStructure.setStatusCode(HttpStatus.NOT_ACCEPTABLE.value());
#             responseStructure.setApiDescription("data not saved something went wrong");
#             responseStructure.setData(product2);
#             return responseStructure;
#         }
#     }
#
# CRITICAL parity checks:
#   - ``print(product_data)`` is REQUIRED — preserves stdout debug
#     output parity with Java's ``System.out.println(product)``.
#   - The literal strings ``"save product Secessfully..."`` (with the
#     typo ``Secessfully``) and ``"data not saved something went wrong"``
#     MUST be exact byte-for-byte copies of the Java strings (no
#     spelling corrections, no rephrasing, no capitalization changes).
#   - The ``@blp.alt_response(406, schema=ResponseStructureSchema, ...)``
#     includes the schema because the 406 response also uses the
#     envelope shape (the Java side returns a ResponseStructure with
#     ``statusCode = 406`` rather than letting Spring render a 406
#     error page).
#   - The ``description=`` strings on ``@blp.response`` and
#     ``@blp.alt_response`` come from the Java
#     ``@ApiResponse(description = "...")`` annotations (lines 48-51).
#   - The ``@blp.doc(description="it will save one object at a time")``
#     mirrors the Java ``@Operation(description = "it will save one
#     object at a time")``.
# ===========================================================================
@blp.route("/saveProduct")
class SaveProduct(MethodView):
    """``POST /product/saveProduct`` — save a single product, return ResponseStructure envelope.

    Translation of
    ``ProductController.saveProductController(Product)`` (Java lines
    45-73). Wraps the saved Product in a ``ResponseStructure`` envelope
    matching the Java side's response shape byte-for-byte.

    Behavior:
      - Calls ``print(product_data)`` for debug parity with Java's
        ``System.out.println(product)``.
      - Constructs a ``Product`` ORM instance from the validated
        request data (``Product(**product_data)``).
      - Delegates to ``product_dao.save_product_dao(product)``.
      - On success: returns
        ``ResponseStructure(status_code=200, api_description="save product Secessfully...", data=saved)``.
      - On ``None`` (defensive parity branch): returns
        ``ResponseStructure(status_code=406, api_description="data not saved something went wrong", data=None)``.
        Note: in Python this branch is theoretical/defensive — the DAO
        raises an exception on save failure rather than returning
        ``None``. The branch is preserved for byte-for-byte parity
        with the Java if/else structure.

    The typos ``Secessfully`` and ``something went wrong`` are
    preserved verbatim per AAP §0.7.2 — these strings appear in the
    externally observable JSON responses and clients may already be
    pattern-matching on them.
    """

    @blp.arguments(ProductSchema)
    @blp.response(200, ResponseStructureSchema, description="Product saved successfully")
    @blp.alt_response(400, description="Invalid input, object not saved")
    @blp.alt_response(406, schema=ResponseStructureSchema, description="Not acceptable, validation failed")
    @blp.alt_response(500, description="Internal server error")
    @blp.doc(description="it will save one object at a time")
    def post(self, product_data):
        # Debug parity: ``System.out.println(product)`` on the Java
        # side (Java line 56). The marshmallow-deserialized dict is
        # what the controller "sees" before any further conversion.
        # Operators relying on stdout for visibility see equivalent
        # output in both implementations.
        print(product_data)

        # Convert dict → Product ORM instance before calling the DAO.
        # The DAO's ``save_product_dao`` expects a ``Product`` instance
        # (it calls ``db.session.add(product)`` internally, which
        # requires an ORM-mapped instance, not a plain dict). The
        # ``Product(**product_data)`` constructor unpacks the dict's
        # keys (``id``, ``name``, ``color``, ``price``) directly to
        # the SQLAlchemy column attributes.
        product = Product(**product_data)
        saved = product_dao.save_product_dao(product)

        if saved is not None:
            # SUCCESS path (parity with Java lines 60-64).
            # Construct a fresh ResponseStructure per request — this is
            # an intentional architectural improvement over Java's
            # singleton-mutation pattern (documented as a thread-safety
            # caveat in the Tech Spec). The serialized JSON output is
            # byte-equivalent; the in-memory instantiation difference
            # is invisible to clients per AAP §0.4.3.
            #
            # The literal string "save product Secessfully..." carries
            # the typo from the Java side (Java line 62). DO NOT
            # correct to "Successfully" — clients may pattern-match
            # on the exact string.
            return ResponseStructure(
                status_code=200,
                api_description="save product Secessfully...",
                data=saved,
            )
        else:
            # FAILURE path (parity with Java lines 66-70).
            #
            # In Python this branch is theoretical/defensive — the DAO
            # raises an exception on save failure rather than returning
            # None. The branch is preserved for byte-for-byte parity
            # with the Java if/else structure: should the DAO's
            # behavior ever change to return ``None`` on a soft-fail
            # case, this branch produces the same wire-level response
            # the Java side does today.
            #
            # The literal string "data not saved something went wrong"
            # is preserved verbatim from the Java side (Java line 68);
            # the lowercase "something" and the lack of trailing
            # punctuation match the Java original.
            return ResponseStructure(
                status_code=406,
                api_description="data not saved something went wrong",
                data=None,
            )


# ===========================================================================
# Handler 3: POST /product/saveProducts (Feature F-004)
# ===========================================================================
# Translates Java ``ProductController.java`` lines 75-80:
#
#     @PostMapping(value = "/saveProducts")
#     public List<Product> saveProductController(@RequestBody List<Product> products) {
#         System.out.println(products);
#         return productDao.saveMultipleProductDao(products);
#     }
#
# CRITICAL parity checks:
#   - ``print(products_data)`` is REQUIRED — preserves stdout debug
#     output parity with Java's ``System.out.println(products)``.
#   - **NO ``@blp.doc(...)`` decorator** — the Java method has no
#     ``@Operation`` annotation, so the Python equivalent must NOT
#     document it either (per AAP §0.5.2 and the folder requirements).
#     Adding ``@blp.doc`` here would generate OpenAPI metadata that
#     the Java side does not have, producing a divergent Swagger UI
#     between the two implementations.
#   - The schema is ``ProductSchema(many=True)`` for both the
#     ``@blp.arguments`` and the ``@blp.response`` — for list-of-Product
#     handling.
# ===========================================================================
@blp.route("/saveProducts")
class SaveProducts(MethodView):
    """``POST /product/saveProducts`` — bulk save a list of products, return the list.

    Translation of
    ``ProductController.saveProductController(List<Product>)`` (Java
    lines 75-80). Returns a bare list of saved Products (no
    ResponseStructure envelope) matching the Java return type
    ``List<Product>``.

    Per AAP §0.5.2 and the folder requirements, this handler has NO
    ``@blp.doc(...)`` decorator because the Java method has NO
    ``@Operation`` annotation. The OpenAPI surface for this endpoint
    is therefore minimal — just the auto-derived schema and HTTP
    method/path information.

    Behavior:
      - Calls ``print(products_data)`` for debug parity with Java's
        ``System.out.println(products)``.
      - Converts each dict in the request body to a ``Product``
        instance via list comprehension.
      - Delegates to ``product_dao.save_multiple_product_dao(products)``.
      - Returns the saved list (no envelope).
    """

    @blp.arguments(ProductSchema(many=True))
    @blp.response(200, ProductSchema(many=True))
    def post(self, products_data):
        # Debug parity with Java's ``System.out.println(products)``
        # (Java line 78). Operators see the deserialized list as it
        # arrives at the controller.
        print(products_data)

        # Convert list-of-dicts → list-of-Product-instances before
        # calling the DAO. The DAO's ``save_multiple_product_dao``
        # expects a list of ORM-mapped Product instances (it calls
        # ``db.session.add_all(products)`` internally). The list
        # comprehension unpacks each dict's keys into a fresh Product
        # constructor, mirroring the Java side where Jackson
        # auto-binds each JSON object in the array to a typed
        # ``Product`` instance.
        products = [Product(**p) for p in products_data]
        return product_dao.save_multiple_product_dao(products)


# ===========================================================================
# Handler 4: GET /product/findAllProduct (Feature F-005)
# ===========================================================================
# Translates Java ``ProductController.java`` lines 82-86:
#
#     @GetMapping(value = "/findAllProduct")
#     public List<Product> findAllProductController() {
#         return productDao.displayAllProductDao();
#     }
#
# Behavioral notes:
#   - Returns an UNBOUNDED list — Java does not paginate (the
#     ``findAllProductController`` method simply delegates to
#     ``productDao.displayAllProductDao()`` with no ``Pageable``
#     argument), and the Python translation does not either per AAP
#     §0.3.2 ("Out of scope: pagination/sorting").
#   - No flask-smorest documentation decorators beyond
#     ``@blp.response`` — the Java method has no ``@Operation`` or
#     ``@ApiResponse`` annotations.
# ===========================================================================
@blp.route("/findAllProduct")
class FindAllProduct(MethodView):
    """``GET /product/findAllProduct`` — return all products (unbounded).

    Translation of ``ProductController.findAllProductController()``
    (Java lines 82-86). Returns every row in the ``product`` table as
    a JSON array. Per AAP §0.3.2, no pagination/sorting/filtering is
    added — the Java side does not expose them.

    Returns
    -------
    list[Product]
        Every row in the ``product`` table. The list MAY be empty.
        Insertion order is NOT guaranteed by either side; the
        underlying SQL is ``SELECT * FROM product`` with no
        ``ORDER BY`` clause.
    """

    @blp.response(200, ProductSchema(many=True))
    def get(self):
        # Direct delegation to the DAO. The DAO's
        # ``display_all_product_dao`` calls
        # ``product_repository.find_all()`` which executes
        # ``db.session.query(Product).all()`` — the SQLAlchemy
        # equivalent of Java's ``JpaRepository.findAll()``.
        return product_dao.display_all_product_dao()


# ===========================================================================
# Handler 5: GET /product/getProduct/<int:id> (Feature F-006)
#                                              CRITICAL F-006 PARITY
# ===========================================================================
# Translates Java ``ProductController.java`` lines 88-92:
#
#     @GetMapping(value = "/getProduct/{id}")
#     public Product getProductByIdController(@PathVariable(name = "id") Integer id) {
#         return productDao.getProductByIdDao(id);
#     }
#
# The Java DAO returns ``null`` on miss via
# ``optional.isPresent() ? optional.get() : null``. Spring's Jackson
# serializes that null to a JSON ``null`` body with status 200. The
# controller does NOT translate the miss to HTTP 404 — that's the
# F-006 quirk per AAP §0.7.2.
#
# CRITICAL parity checks:
#   - The MISS path MUST return HTTP 200 with body ``null`` (literally
#     the four-character string "null"). Use ``jsonify(None)`` which
#     produces this exact output.
#   - **DO NOT** decorate this handler with
#     ``@blp.response(200, ProductSchema)`` — marshmallow's
#     ``Schema.dump(None)`` returns ``{}`` (empty dict), NOT ``null``.
#     Using ``@blp.response`` would BREAK byte-for-byte parity by
#     producing ``{}`` instead of ``null`` on miss.
#   - **DO NOT** raise ``abort(404)`` on miss — the F-006 quirk is
#     intentional behavioral parity, not a bug to fix.
#   - The ``<int:id>`` URL converter ensures the id parameter is
#     parsed as an integer (matches Java's ``Integer id`` parameter
#     type with Spring's auto-coercion).
# ===========================================================================
@blp.route("/getProduct/<int:id>")
class GetProductById(MethodView):
    """``GET /product/getProduct/<id>`` — F-006 PARITY: returns 200 with body ``null`` on miss.

    Translation of
    ``ProductController.getProductByIdController(Integer)`` (Java
    lines 88-92).

    **CRITICAL F-006 PARITY (AAP §0.7.2):**
        Per the Java DAO behavior
        (``optional.isPresent() ? optional.get() : null``), a missing
        record yields HTTP 200 with a JSON ``null`` body — NOT HTTP
        404. Do NOT raise ``abort(404)``. Do NOT raise an exception.

    Implementation: this handler does NOT use
    ``@blp.response(200, ProductSchema)`` because marshmallow's
    ``Schema.dump(None)`` returns ``{}`` (empty dict), not ``null``.
    Instead, the handler manually returns ``jsonify(None)`` on miss
    (which produces a literal JSON ``null`` body) and
    ``jsonify(ProductSchema().dump(product))`` on hit.

    Parameters
    ----------
    id : int
        The primary key value to look up. Parsed by Werkzeug's
        ``<int:id>`` URL converter from the URL path segment.

    Returns
    -------
    flask.Response
        On hit: HTTP 200 with the Product as a JSON object body
        (``Content-Type: application/json``). On miss: HTTP 200 with
        the literal JSON ``null`` body (``Content-Type:
        application/json``). NEVER returns HTTP 404 — that's the
        F-006 parity quirk.
    """

    @blp.doc(description="Get product by id; returns body `null` on miss (HTTP 200)")
    def get(self, id):
        # Delegate to the DAO. The DAO returns ``None`` on miss
        # (preserving Java's ``optional.empty()`` semantic via
        # ``db.session.get(Product, id)``) — NOT raising an
        # exception. This is the parity-critical chain that makes
        # F-006 work end-to-end.
        product = product_dao.get_product_by_id_dao(id)

        if product is None:
            # F-006 parity: return HTTP 200 with body literally
            # ``null``. ``jsonify(None)`` produces:
            #
            #   HTTP/1.1 200 OK
            #   Content-Type: application/json
            #   Content-Length: 4
            #
            #   null
            #
            # which is byte-equivalent to what Spring/Jackson
            # produces on the Java side when the controller method
            # returns ``null`` from the DAO. DO NOT change this to
            # ``abort(404)`` — clients of the original Java
            # application rely on the 200+null behavior.
            return jsonify(None)

        # Hit path: serialize the Product through the schema and
        # return as JSON. We use ``jsonify(ProductSchema().dump(...))``
        # rather than ``@blp.response(200, ProductSchema)`` because
        # the response decorator approach cannot accommodate the
        # null-on-miss case above (marshmallow returns ``{}`` on
        # ``dump(None)``, not ``null``). Keeping the manual
        # serialization here makes both paths produce identical
        # response shapes to the Java side.
        return jsonify(ProductSchema().dump(product))


# ===========================================================================
# Handler 6: GET /product/getProductByName/<string:name> (Feature F-007)
# ===========================================================================
# Translates Java ``ProductController.java`` lines 94-97:
#
#     @GetMapping(value = "/getProductByName/{name}")
#     public List<Product> getProductByNameDao(@PathVariable(name = "name") String name) {
#         return productDao.getProductByNameDao(name);
#     }
#
# Note: the Java method name ``getProductByNameDao`` is misleading
# (it's in the controller, not the DAO) — preserved here for
# traceability; only the URL path matters for the public contract.
# ===========================================================================
@blp.route("/getProductByName/<string:name>")
class GetProductByName(MethodView):
    """``GET /product/getProductByName/<name>`` — find products by exact name match.

    Translation of ``ProductController.getProductByNameDao(String)``
    (Java lines 94-97). Note: the Java method name
    ``getProductByNameDao`` is misleading (it's in the controller,
    not the DAO) — preserved here for traceability; only the URL path
    matters for the public contract.

    Parameters
    ----------
    name : str
        The exact name to match. Parsed by Werkzeug's
        ``<string:name>`` URL converter from the URL path segment.
        Case-sensitivity depends on the collation of the underlying
        ``name`` column.

    Returns
    -------
    list[Product]
        Every ``Product`` row whose ``name`` column matches exactly.
        The list MAY be empty (status 200 with body ``[]``).
    """

    @blp.response(200, ProductSchema(many=True))
    def get(self, name):
        # Delegate to the DAO. The DAO calls
        # ``product_repository.find_by_name(name)`` which executes
        # ``db.session.query(Product).filter_by(name=name).all()`` —
        # the SQLAlchemy translation of Spring Data JPA's derived
        # query ``findByName(String)``.
        return product_dao.get_product_by_name_dao(name)


# ===========================================================================
# Handler 7: GET /product/getProductByPrice/<float:price> (Feature F-008)
# ===========================================================================
# Translates Java ``ProductController.java`` lines 99-102:
#
#     @GetMapping(value = "/getProductByPrice/{price}")
#     public List<Product> getProductByPriceController(@PathVariable(name = "price") double price) {
#         return productDao.getProductByPriceDao(price);
#     }
#
# Backed by the repository's NATIVE SQL query
# ``select * from product where price=:price`` — preserved verbatim
# from the Java ``@Query(value = "select * from product where
# price=?", nativeQuery = true)`` annotation. Only the parameter
# style changes (positional ``?`` -> named ``:price``), which is
# required by SQLAlchemy's ``text()`` API.
# ===========================================================================
@blp.route("/getProductByPrice/<float:price>")
class GetProductByPrice(MethodView):
    """``GET /product/getProductByPrice/<price>`` — find products by exact price (native SQL).

    Translation of
    ``ProductController.getProductByPriceController(double)`` (Java
    lines 99-102). Backed by the repository's native SQL query
    ``select * from product where price=:price`` (preserved verbatim
    from the Java ``@Query(nativeQuery = true)`` annotation; only the
    parameter style changes from positional ``?`` to named
    ``:price``).

    Parameters
    ----------
    price : float
        The exact price to match. Parsed by Werkzeug's
        ``<float:price>`` URL converter from the URL path segment.
        SQLAlchemy parameterizes this value, so SQL injection is not
        possible regardless of input.

    Returns
    -------
    list[Product]
        Every ``Product`` row whose ``price`` column matches exactly.
        The list MAY be empty (status 200 with body ``[]``).
    """

    @blp.response(200, ProductSchema(many=True))
    def get(self, price):
        # Delegate to the DAO. The DAO calls
        # ``product_repository.get_product_by_price(price)`` which
        # executes the native SQL via
        # ``db.session.execute(text("select * from product where
        # price=:price"), {"price": price})`` and rebuilds Product
        # instances from the result rows.
        return product_dao.get_product_by_price_dao(price)


# ===========================================================================
# Handler 8: DELETE /product/deleteProductByPrice/<float:price>
#                                                       (Feature F-011)
# ===========================================================================
# Translates Java ``ProductController.java`` lines 104-108:
#
#     @DeleteMapping(value = "/deleteProductByPrice/{price}")
#     public void deleteProductByPriceController(@PathVariable(name = "price") double price) {
#         productDao.deleteProductByPriceDao(price);
#     }
#
# Backed by the repository's NATIVE SQL DELETE
# ``delete from product where price=:price`` wrapped in an explicit
# transaction commit (preserves Java's ``@Modifying`` +
# ``@Transactional`` semantics). Returns HTTP 200 with an empty body
# (matches Java's ``void`` return type — Spring renders ``void`` as
# HTTP 200 with no body, NOT HTTP 204 No Content).
#
# CRITICAL parity checks:
#   - The endpoint MUST return HTTP 200 (NOT 204 No Content). Java's
#     ``void`` return on a Spring controller method maps to HTTP 200
#     by default.
#   - The body MUST be TRULY EMPTY (Content-Length: 0) to match Java
#     ``void``. Returning a bare ``""`` string from a view decorated
#     with ``@blp.response(200)`` causes flask-smorest to JSON-
#     serialize the empty string as ``""\n`` (3 bytes) with
#     ``Content-Type: application/json`` — diverging from Java's
#     zero-byte ``void`` body. Returning a pre-built
#     ``Response("", status=200)`` instance instead is recognized by
#     flask-smorest's ``isinstance(result_raw, Response)`` short
#     circuit (see flask_smorest 0.47.0 ``response.py``) and is
#     forwarded as-is, producing ``Content-Length: 0`` and an empty
#     body — the byte-for-byte parity AAP §0.5.2 requires for F-011.
#   - ``@blp.response(200)`` (without a schema) is preserved so the
#     OpenAPI document still records "DELETE returns 200" for Swagger
#     UI. flask-smorest passes Response instances through without
#     re-serializing, so the decorator is purely documentational here.
# ===========================================================================
@blp.route("/deleteProductByPrice/<float:price>")
class DeleteProductByPrice(MethodView):
    """``DELETE /product/deleteProductByPrice/<price>`` — delete products by price (native SQL).

    Translation of
    ``ProductController.deleteProductByPriceController(double)`` (Java
    lines 104-108). Backed by the repository's native SQL DELETE
    ``delete from product where price=:price`` wrapped in an explicit
    transaction commit (preserves Java's ``@Modifying`` +
    ``@Transactional`` semantics).

    Returns HTTP 200 with TRULY EMPTY body (``Content-Length: 0``) to
    match Java's ``void`` return type byte-for-byte. Spring renders
    ``void`` as HTTP 200 with no body, NOT HTTP 204 No Content; the
    Python equivalent must produce a zero-byte body with status 200.

    Parameters
    ----------
    price : float
        The exact price to match. Every row in the ``product`` table
        whose ``price`` column equals this value is deleted in one
        DELETE statement.

    Returns
    -------
    flask.Response
        A pre-built Flask ``Response`` instance with an empty body and
        status 200. flask-smorest's ``@blp.response(200)`` decorator
        recognizes the ``Response`` instance and passes it through
        without re-serializing, producing ``Content-Length: 0`` —
        byte-identical to the Java ``void`` return.
    """

    @blp.response(200)
    def delete(self, price):
        # Delegate to the DAO. The DAO calls
        # ``product_repository.delete_product_by_price(price)`` which
        # executes ``db.session.execute(text("delete from product
        # where price=:price"), {"price": price})`` followed by
        # ``db.session.commit()`` to satisfy the Java side's
        # ``@Modifying`` + ``@Transactional`` durability semantic.
        product_dao.delete_product_by_price_dao(price)

        # Java returns ``void`` -> HTTP 200 with TRULY EMPTY body
        # (``Content-Length: 0``). Returning a bare ``""`` here would
        # be re-serialized by ``@blp.response(200)`` through
        # ``jsonify`` into ``""\n`` (3 bytes) with
        # ``Content-Type: application/json`` — diverging from Java's
        # zero-byte ``void`` body. Returning a pre-built
        # ``Response("", status=200)`` is recognized by flask-smorest's
        # ``isinstance(result_raw, Response)`` short circuit (see
        # flask_smorest 0.47.0 ``response.py``: "If return value is a
        # werkzeug Response, return it") and is forwarded as-is. The
        # resulting wire-level response has an empty body and
        # ``Content-Length: 0`` — byte-for-byte parity with Java's
        # ``void`` return type as required by AAP §0.5.2.
        return Response("", status=200)


# ===========================================================================
# Handler 9: PUT /product/updateProduct/<int:id> (Feature F-009)
#                                              CRITICAL F-009 PARITY
# ===========================================================================
# Translates Java ``ProductController.java`` lines 114-142:
#
#     @PutMapping(value = "/updateProduct/{id}")
#     @Operation(description = "it will update one object at a time",
#         responses = {
#             @ApiResponse(responseCode = "200", description = "Product Update successfully"),
#             @ApiResponse(responseCode = "400", description = "Invalid input, object not saved"),
#             @ApiResponse(responseCode = "406", description = "Not acceptable, validation failed"),
#             @ApiResponse(responseCode = "500", description = "Internal server error") }
#     )
#     public ResponseStructure<Product> updateProductController(
#             @RequestBody Product userproduct, @PathVariable(name = "id") Integer id) {
#         Product product2 = productDao.updateProductDao(userproduct, id);
#         if (product2 != null) {
#             responseStructure.setStatusCode(HttpStatus.OK.value());
#             responseStructure.setApiDescription("update product Secessfully...");
#             responseStructure.setData(product2);
#             return responseStructure;
#         } else {
#             responseStructure.setStatusCode(HttpStatus.NOT_ACCEPTABLE.value());
#             responseStructure.setApiDescription("data not saved something went wrong");
#             responseStructure.setData(product2);
#             return responseStructure;
#         }
#     }
#
# The Java DAO ``updateProductDao`` raises ``RuntimeException`` on
# missing record. The Java controller does NOT wrap the call in
# try/catch, so the exception propagates to Spring's default error
# handler -> HTTP 500.
#
# CRITICAL F-009 parity checks:
#   - **NO ``try/except``** around the DAO call. Let
#     ``Exception("Product not found with ID: <id>")`` propagate to
#     Flask's default error handler so the response is HTTP 500.
#   - The literal strings ``"update product Secessfully..."`` (with
#     the typo ``Secessfully``) and ``"data not saved something went
#     wrong"`` MUST be exact byte-for-byte copies of the Java
#     strings.
#   - The success message uses the word ``update`` (lowercase)
#     followed by the typo ``Secessfully`` — verify against Java line
#     131: ``responseStructure.setApiDescription("update product
#     Secessfully...");``.
#   - The ``@blp.doc(description="it will update one object at a
#     time")`` mirrors Java line 115.
#   - Convert ``product_data`` (dict) -> ``Product(**product_data)``
#     before passing to DAO.
#
# The SIBLING endpoint at PUT /product/<int:id> (handler
# UpdateProduct below) intentionally returns HTTP 404 on the same
# condition. Both endpoints coexist with divergent error semantics —
# this is the most surprising parity feature in the entire codebase.
# ===========================================================================
@blp.route("/updateProduct/<int:id>")
class UpdateProductWithEnvelope(MethodView):
    """``PUT /product/updateProduct/<id>`` — F-009 PARITY: missing-record → HTTP 500 (no try/except).

    Translation of
    ``ProductController.updateProductController(Product, Integer)``
    (Java lines 114-142). Wraps the updated Product in a
    ``ResponseStructure`` envelope on success.

    **CRITICAL F-009 PARITY (AAP §0.7.2):**
        DO NOT WRAP the DAO call in try/except. The DAO raises
        ``Exception("Product not found with ID: <id>")`` on miss; this
        exception MUST propagate to Flask's default error handler so
        the response is HTTP 500 (matching Java's ``RuntimeException``
        → Spring-default 500 behavior).

        This is the F-009 quirk — the SIBLING endpoint at
        ``PUT /product/<id>`` (handler ``UpdateProduct`` below)
        intentionally returns HTTP 404 on the same condition. Both
        endpoints coexist with divergent error semantics: the SAME
        DAO call produces HTTP 500 on one endpoint and HTTP 404 on a
        sibling endpoint, purely because of try/except placement.
        This is the most surprising parity feature in the entire
        codebase.

    The typos ``Secessfully`` and ``something went wrong`` are
    preserved verbatim per AAP §0.7.2.
    """

    @blp.arguments(ProductSchema)
    @blp.response(200, ResponseStructureSchema, description="Product Update successfully")
    @blp.alt_response(400, description="Invalid input, object not saved")
    @blp.alt_response(406, schema=ResponseStructureSchema, description="Not acceptable, validation failed")
    @blp.alt_response(500, description="Internal server error")
    @blp.doc(description="it will update one object at a time")
    def put(self, product_data, id):
        # F-009 parity: do NOT wrap in try/except. Exception
        # propagates to Flask's default error handler -> HTTP 500.
        # Convert dict -> Product instance for the DAO call.
        product = Product(**product_data)
        updated = product_dao.update_product_dao(product, id)

        # If we reach here, the DAO did NOT raise — ``updated`` is a
        # Product instance. The if/else preserves byte-for-byte parity
        # with the Java if/else structure even though, in Python, the
        # DAO raises rather than returning None on miss (so the else
        # branch is unreachable in normal operation). Keeping the
        # structure makes the side-by-side comparison with the Java
        # source trivial.
        if updated is not None:
            # SUCCESS path (parity with Java lines 129-133).
            #
            # The literal string "update product Secessfully..." is
            # preserved VERBATIM from Java line 131 — note the typo
            # ``Secessfully`` (instead of "Successfully") and the
            # lowercase first letter ``update``. DO NOT correct
            # either; clients may pattern-match on the exact string.
            return ResponseStructure(
                status_code=200,
                api_description="update product Secessfully...",
                data=updated,
            )
        else:
            # FAILURE path (parity with Java lines 134-139).
            #
            # In Python this branch is unreachable in normal
            # operation — the DAO raises rather than returning None
            # on miss. The branch is preserved for byte-for-byte
            # parity with the Java if/else structure.
            #
            # The literal string "data not saved something went
            # wrong" is preserved VERBATIM from Java line 137; the
            # lowercase "something" and the lack of trailing
            # punctuation match the Java original.
            return ResponseStructure(
                status_code=406,
                api_description="data not saved something went wrong",
                data=None,
            )


# ===========================================================================
# Handler 10: PUT /product/<int:id> (Feature F-010)
#                                              CRITICAL F-010 PARITY
# ===========================================================================
# Translates Java ``ProductController.java`` lines 145-153:
#
#     @PutMapping("/{id}")
#     public ResponseEntity<Product> updateProduct(@RequestBody Product product, @PathVariable Integer id) {
#         try {
#             Product updatedProduct = productDao.updateProductDao(product, id);
#             return new ResponseEntity<Product>(updatedProduct, HttpStatus.OK);
#         } catch (RuntimeException e) {
#             return new ResponseEntity<Product>(HttpStatus.NOT_FOUND);
#         }
#     }
#
# The Java controller wraps the DAO call in try/catch and returns 404
# on a missing record. The Python translation preserves this exact
# semantic.
#
# CRITICAL F-010 parity checks:
#   - **DO** wrap the DAO call in ``try/except``.
#   - Catch the broad ``Exception`` (matches Java's
#     ``catch (RuntimeException e)``).
#   - On exception, call ``abort(404)`` from Flask (NOT
#     ``flask_smorest.abort`` — use the Flask built-in to raise a
#     clean ``HTTPException(404)``).
#   - The URL pattern is ``<int:id>`` (matches Java's
#     ``@PathVariable Integer id`` parameter type).
#   - This handler intentionally has NO ``@blp.doc(...)`` (matches
#     Java's lack of ``@Operation``).
# ===========================================================================
@blp.route("/<int:id>")
class UpdateProduct(MethodView):
    """``PUT /product/<id>`` — F-010 PARITY: missing-record → HTTP 404 (with try/except + abort).

    Translation of ``ProductController.updateProduct(Product, Integer)``
    (Java lines 145-153).

    **CRITICAL F-010 PARITY (AAP §0.7.2):**
        DO WRAP the DAO call in try/except. Catch the missing-record
        ``Exception`` and call ``abort(404)``. Both update endpoints
        (this one and ``UpdateProductWithEnvelope`` above) coexist
        with divergent error semantics — the SAME DAO call produces
        HTTP 500 on the F-009 endpoint and HTTP 404 on this
        endpoint, purely because of try/except placement.

    Per AAP §0.5.2 and the folder requirements, this handler has NO
    ``@blp.doc(...)`` decorator because the Java method has NO
    ``@Operation`` annotation. It only has ``@blp.alt_response(404,
    ...)`` for the documented NOT_FOUND case (the same documented
    case the Java ``ResponseEntity<Product>(HttpStatus.NOT_FOUND)``
    return represents).
    """

    @blp.arguments(ProductSchema)
    @blp.response(200, ProductSchema)
    @blp.alt_response(404, description="Product not found")
    def put(self, product_data, id):
        # F-010 parity: WRAP the DAO call in try/except.
        # ``abort(404)`` on Exception.
        try:
            # Convert dict -> Product instance, then delegate to DAO.
            product = Product(**product_data)
            updated = product_dao.update_product_dao(product, id)

            # Hit path: return the updated Product. flask-smorest's
            # ``@blp.response(200, ProductSchema)`` decorator
            # serializes the Product instance via marshmallow into
            # the JSON response body. This matches Java's
            # ``new ResponseEntity<Product>(updatedProduct,
            # HttpStatus.OK)`` byte-for-byte at the wire level.
            return updated
        except Exception:
            # Catch the broad ``Exception`` to match Java's
            # ``catch (RuntimeException e)``. The DAO raises
            # ``Exception("Product not found with ID: <id>")`` on
            # miss; we catch it and translate to HTTP 404.
            #
            # The Java side returns
            # ``new ResponseEntity<Product>(HttpStatus.NOT_FOUND)``
            # which is HTTP 404 with no body. Flask's ``abort(404)``
            # raises an ``HTTPException(404)`` which Flask handles
            # with a default 404 response. The status code parity
            # is what matters; the body content is
            # implementation-defined and was effectively empty in
            # Java.
            #
            # We use ``flask.abort`` (NOT ``flask_smorest.abort``)
            # because the Flask built-in produces a clean
            # ``HTTPException(404)`` that Flask's default error
            # handler renders as the standard 404 page;
            # ``flask_smorest.abort`` would wrap this with
            # additional flask-smorest machinery that is unnecessary
            # for the parity contract.
            abort(404)
