"""REST controllers for the ``/student/*`` namespace.

This module is the Python translation of
``EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/controller/StudentController.java``.
It defines a flask-smorest ``Blueprint`` named ``blp`` and two
``MethodView``-based handler classes — one per Java endpoint method —
covering the two endpoints documented in AAP §0.2.3:

==================  ======  ============================  ==========================
Feature             Method  Path                          Java method translated
==================  ======  ============================  ==========================
F-012 (student)     GET     ``/student/getTodayDate``     ``getTodaysDate()``
F-013               POST    ``/student/addition/{a1}/{b1}``  ``getAdditionOfTwoNumber(int, int)``
==================  ======  ============================  ==========================

Behavioral parity quirks preserved verbatim per AAP §0.7.2
---------------------------------------------------------
1. **Trailing space in the date echo.** ``LocalDate.now() + " "`` on the
   Java side returns the ISO-8601 date string followed by exactly one
   space character. The Python equivalent ``f"{date.today()} "`` carries
   the same trailing space, byte-for-byte. The space is part of the
   externally observable API surface and MUST NOT be removed.
2. **No CORS for the /student/* namespace.** The Java
   ``StudentController`` carries NO ``@CrossOrigin`` annotation (only
   ``ProductController`` does, with ``@CrossOrigin(value = "")``).
   Consequently this blueprint does NOT register any CORS handling, and
   ``app.py`` scopes Flask-Cors specifically to ``/product/*``. Browser
   preflight requests against ``/student/*`` therefore return without
   any ``Access-Control-Allow-Origin`` headers, exactly mirroring the
   Java behavior.
3. **HTTP POST for the addition endpoint.** The Java side uses the
   verbose form ``@RequestMapping(method = RequestMethod.POST)`` rather
   than the short-hand ``@PostMapping``; the dispatch produced by
   Spring is identical regardless of which decorator style is used.
   The Python equivalent is ``MethodView.post`` — using ``get`` instead
   would change the HTTP method and break parity.
4. **Bare-integer return.** The Java method signature returns ``int``,
   which Spring's Jackson auto-serializes to a JSON integer literal in
   the response body (``Content-Type: application/json``). Flask cannot
   return a bare Python ``int`` (the WSGI layer raises
   ``TypeError: The view function did not return a valid response``);
   the equivalent that produces the same wire output is
   ``jsonify(a1 + b1)``.

Layered-architecture parity
---------------------------
``StudentController`` is intentionally minimal in the original Java
implementation: it is stateless, it has no DAO/repository/entity
coupling, and it does not carry any ``@Tag``, ``@Operation``, or
``@ApiResponse`` annotations. The Python translation preserves that
minimalism — there are no flask-smorest documentation decorators
(``@blp.doc``, ``@blp.response``, ``@blp.alt_response``,
``@blp.arguments``), no marshmallow Schemas, and no imports from any
``dao``, ``schemas``, ``responses``, or ``entity`` module. Adding any
of those would deviate from the Java side and violate the parity
contract.

Exports
-------
blp: flask_smorest.Blueprint
    The ``/student`` URL-prefix namespace. Imported by ``app.py`` as
    ``from controller.student_controller import blp as student_blp`` and
    registered via ``api.register_blueprint(student_blp)``. The variable
    name is exactly ``blp`` (lowercase) and MUST NOT be renamed —
    ``app.py`` relies on it.
StudentTodayDate: flask.views.MethodView
    Class-based handler for ``GET /student/getTodayDate``.
StudentAddition: flask.views.MethodView
    Class-based handler for ``POST /student/addition/<int:a1>/<int:b1>``.
"""

# ---------------------------------------------------------------------------
# Imports.
#
# Only four imports are required — this module is stateless and has no
# DAO/repository/entity coupling on the Java side, so the Python side
# preserves that minimalism:
#
#   - ``datetime.date``         replaces Java's ``java.time.LocalDate``
#                                for the date-echo endpoint.
#   - ``flask.jsonify``         serializes the integer addition result as
#                                a JSON body. Flask cannot return a bare
#                                Python int from a view function;
#                                jsonify is the canonical helper.
#   - ``flask.views.MethodView`` base class for class-based handlers,
#                                the Python analog of Spring's
#                                ``@RestController`` dispatch model.
#   - ``flask_smorest.Blueprint`` replaces Spring's
#                                ``@RequestMapping(value = "/student")``
#                                at the class level.
#
# Per the file's agent prompt, imports use SIMPLE module names
# (``from flask import ...``) NOT a ``python_app.`` package prefix —
# the same convention used in ``extensions.py`` and ``config.py``.
# ---------------------------------------------------------------------------
from datetime import date

from flask import jsonify
from flask.views import MethodView
from flask_smorest import Blueprint


# ---------------------------------------------------------------------------
# Blueprint definition — the ``/student`` URL-prefix namespace.
#
# Mirrors the Java class-level annotations:
#   @RestController                          (-> MethodView dispatch)
#   @RequestMapping(value = "/student")      (-> url_prefix="/student")
#
# The ``description`` keyword is intentionally OMITTED here because the
# Java ``StudentController`` has NO ``@Tag`` annotation. The
# ``ProductController`` carries ``@Tag(name = "productcontroller",
# description = "this is controller class")``, so the Python
# ``product_controller.py`` translates that as a Blueprint description;
# the absence of a description here mirrors the Java side's absence
# byte-for-byte.
#
# The blueprint variable name MUST be exactly ``blp`` (lowercase) so that
# ``app.py``'s import statement
#     from controller.student_controller import blp as student_blp
# resolves correctly. Renaming this variable would break the application
# factory.
# ---------------------------------------------------------------------------
blp = Blueprint(
    "student",
    __name__,
    url_prefix="/student",
)


# ---------------------------------------------------------------------------
# Handler 1: GET /student/getTodayDate (Feature F-012, student variant)
#
# Translates Java lines 15-19:
#
#     @GetMapping(value = "/getTodayDate")
#     public String getTodaysDate() {
#         return LocalDate.now()+" ";
#     }
#
# Behavioral notes:
#   - Returns the ISO-8601 date string (e.g., "2026-04-27") followed by
#     exactly ONE space character.
#   - The trailing space character is mandatory parity per AAP §0.7.2;
#     do NOT remove it.
#   - Returns a plain ``str``; Flask serializes it as the text body with
#     ``Content-Type: text/html; charset=utf-8`` by default, matching
#     Spring's behavior for a ``String``-returning method.
#   - This endpoint duplicates ``ProductController.getTodaysDate()``
#     (handler ``TodayDate`` in ``product_controller.py``) — two parallel
#     routes producing identical output. Both are preserved as-is for
#     parity with the Java side.
#   - No flask-smorest documentation decorators are applied — the Java
#     method has no ``@Operation`` or ``@ApiResponse`` annotations.
# ---------------------------------------------------------------------------
@blp.route("/getTodayDate")
class StudentTodayDate(MethodView):
    """``GET /student/getTodayDate`` — echo today's date with a trailing space.

    Translation of ``StudentController.getTodaysDate()`` (Java lines
    15-19). Per AAP §0.7.2 the trailing space character in
    ``LocalDate.now() + " "`` MUST be preserved literally — the Python
    equivalent is ``f"{date.today()} "`` (note the space character before
    the closing quote).

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


# ---------------------------------------------------------------------------
# Handler 2: POST /student/addition/<a1>/<b1> (Feature F-013)
#
# Translates Java lines 21-25:
#
#     @RequestMapping(value = "/addition/{a1}/{b1}",
#                     method = RequestMethod.POST)
#     public int getAdditionOfTwoNumber(@PathVariable(name = "a1") int a,
#             @PathVariable(name = "b1") int b) {
#         return a+b;
#     }
#
# Behavioral notes:
#   - HTTP method is POST (not GET). The Java side uses
#     ``@RequestMapping(method = RequestMethod.POST)`` rather than
#     ``@PostMapping``, but the dispatch is identical. The Python
#     equivalent is ``MethodView.post``. Using ``get`` would change the
#     HTTP verb and break parity.
#   - Both path variables are integers (Werkzeug ``<int:...>`` converter
#     -> Java ``int`` parameter type). Non-integer path values produce a
#     404 from Werkzeug's URL routing, mirroring Spring's
#     ``MethodArgumentTypeMismatchException`` -> 400/404 behavior closely
#     enough that no client of the Java side would observe a difference.
#   - The Java method's parameter names are ``a`` and ``b`` (not ``a1``
#     and ``b1`` — the path-variable annotation supplies the binding via
#     the ``name`` attribute). The Python signature uses ``a1`` and ``b1``
#     because flask routing matches by URL converter group name, which is
#     ``a1``/``b1`` in the URL pattern. The arithmetic outcome
#     (``a+b`` vs ``a1+b1``) is identical regardless of parameter naming.
#   - ``jsonify(a1 + b1)`` is REQUIRED — Flask cannot serialize a bare
#     ``int`` and would raise ``TypeError: The view function did not
#     return a valid response``. The output of jsonify on an integer is a
#     JSON integer literal (e.g., ``7``) with
#     ``Content-Type: application/json``. Spring's Jackson auto-
#     serialization of ``int`` produces the same JSON integer literal,
#     so the wire output is byte-identical.
# ---------------------------------------------------------------------------
@blp.route("/addition/<int:a1>/<int:b1>")
class StudentAddition(MethodView):
    """``POST /student/addition/<a1>/<b1>`` — return the integer sum of two ints.

    Translation of ``StudentController.getAdditionOfTwoNumber(int, int)``
    (Java lines 21-25). The Java method declares HTTP POST via the
    verbose ``@RequestMapping(method = RequestMethod.POST)`` form;
    flask-smorest dispatches by the lowercase method name on the
    ``MethodView`` subclass, so ``def post(self, ...)`` is the parity
    equivalent. ``def get(self, ...)`` would change the verb and break
    parity.

    Parameters
    ----------
    a1 : int
        First addend, parsed from the URL ``<int:a1>`` segment.
    b1 : int
        Second addend, parsed from the URL ``<int:b1>`` segment.

    Returns
    -------
    flask.Response
        A Flask response carrying the JSON integer literal ``a1 + b1``
        (e.g., ``7`` for ``a1=3, b1=4``) with status 200 and
        ``Content-Type: application/json``. Matches Spring's Jackson
        auto-serialization of an ``int`` return value.
    """

    def post(self, a1, b1):
        # Compute the sum and serialize via ``jsonify`` to produce a
        # JSON integer literal as the response body. The body and
        # content type are byte-identical to what Spring produces for
        # the equivalent Java method.
        return jsonify(a1 + b1)
