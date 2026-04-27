"""Marshmallow ResponseStructureSchema — JSON serialization contract for the
generic response envelope.

This is the Python equivalent of the Jackson-driven JSON serialization that
the Java ``responses/ResponseStructure.java`` class receives implicitly via
``spring-boot-starter-web``'s auto-configured ObjectMapper. It is the SCHEMA
side (JSON contract) of the Lombok ``@Data`` + Jackson combo replacement;
the in-memory data carrier is ``responses/response_structure.py``.

This file is intentionally SEPARATE from the in-memory dataclass in
``responses/response_structure.py``:

- ``responses/response_structure.py``        — in-memory data carrier (a
                                                Python ``@dataclass`` carrying
                                                ``status_code``,
                                                ``api_description``, ``data``)
- ``schemas/response_structure_schema.py``   — JSON wire-format contract
                                                (this file; the marshmallow
                                                ``Schema`` that flask-smorest
                                                uses to serialize the
                                                dataclass to JSON)

This separation mirrors the cleaner split available in the Python ecosystem
versus the Java side, where Lombok ``@Data`` and (transitive) Jackson
serialization share the same class. Per AAP §0.4.3 this is an intentional
architectural improvement that does NOT alter externally observable
behavior — the JSON wire format produced is byte-equivalent to the Java
service's output.

Used by exactly TWO endpoints (per AAP §0.2.3):

- ``POST /product/saveProduct``        (F-003) — wraps the saved Product on
                                                success, or wraps a ``null``
                                                payload on the 406 error path.
- ``PUT  /product/updateProduct/{id}`` (F-009) — wraps the updated Product on
                                                success; lets the
                                                ``ProductDao`` ``Exception``
                                                propagate to a Flask 500 on
                                                missing-record (preserving
                                                divergent F-009/F-010 update
                                                semantics per AAP §0.7.2).

The other ten endpoints in the application return BARE payloads (no
envelope) and do NOT use this schema.

CRITICAL PARITY (AAP §0.7.2)
============================

The JSON output keys MUST be camelCase exactly:

- ``statusCode``      (NOT ``status_code``)
- ``apiDescription``  (NOT ``api_description``)
- ``data``            (already lowercase — no remap needed)

The ``data_key=`` argument on each marshmallow field provides the
bidirectional mapping between the Python snake_case attribute name and
the JSON camelCase key. WITHOUT ``data_key=``, marshmallow would default
to emitting ``status_code`` and ``api_description``, which would BREAK
client compatibility with the Java service. This is the SINGLE most
important parity feature in the entire ``schemas/`` folder; it is the
mechanism that keeps the Python service's response payloads
byte-equivalent to the Java service's response payloads.

The ``data`` field is ``fields.Nested(ProductSchema, allow_none=True)``:

- ``Nested(ProductSchema)`` renders a Product payload according to the
  Product schema's field rules (id, name, color, price).
- ``allow_none=True`` is REQUIRED because the Java side sends
  ``data: null`` on the 406 error path (when ``ProductDao.saveProductDao``
  returns ``null``). Without this flag, marshmallow raises a
  ``ValidationError`` on serialization when ``data is None``, which
  would break the F-003 error path and diverge from Java behavior.

Usage
-----

The schema is consumed by flask-smorest decorators in
``controller/product_controller.py``:

- ``@blp.response(200, ResponseStructureSchema)`` — serializes the
  controller's return value (a ``ResponseStructure`` dataclass instance)
  to JSON for both F-003 and F-009. The controller code does NOT call
  ``schema.dump()`` manually — flask-smorest does it automatically based
  on the decorator's schema argument.
- ``@blp.alt_response(406, ...)`` — declares the alternate-status response
  shape on F-003. The same schema is reused; only ``status_code`` and
  ``api_description`` differ at runtime.

Out-of-scope per AAP §0.3.2 / §0.7.2
-------------------------------------

The Java ``ResponseStructure`` class carries NO Bean Validation
annotations and NO field-level Swagger ``@Schema`` description
annotations (the class-level annotation is ``@Schema(hidden = true)``
which excludes the envelope itself from the OpenAPI spec). To preserve
byte-identical behavior, this schema also declares NO validators and NO
field metadata:

- No ``required=True`` on any field.
- No ``validate=Range(...)`` / ``validate=Length(...)`` constraints.
- No ``@validates`` / ``@validates_schema`` decorators.
- No ``metadata={"description": "..."}`` on any field.
- No ``@pre_load`` / ``@post_dump`` hooks.
- No ``@dataclass`` decorator (that's for the in-memory carrier in
  ``responses/response_structure.py``, NOT here).
- No ``Generic[T]`` typing (marshmallow Schemas are not generic in the
  Java sense; the ``data`` field is statically typed as
  ``Nested(ProductSchema)`` because in this codebase the only ``T``
  ever used is ``Product``).
"""

# ---------------------------------------------------------------------------
# External imports.
#
# The single external dependency for this module is ``marshmallow``. Per the
# file schema's external_imports declaration (AAP companion data), only
# ``Schema`` (the parent class for ``ResponseStructureSchema``) and
# ``fields`` (which exposes ``Integer``, ``String``, and ``Nested`` field
# types) are pulled in. flask-smorest reads the resulting Schema to drive
# response serialization for the two envelope-using endpoints (F-003 and
# F-009).
# ---------------------------------------------------------------------------
from marshmallow import Schema, fields

# ---------------------------------------------------------------------------
# Internal imports.
#
# ``ProductSchema`` is the marshmallow schema for a single Product, used as
# the nested schema for the ``data`` field below. The simple module path
# ``schemas.product_schema`` is used (NOT ``python_app.schemas.product_schema``)
# because the import convention throughout the ``python_app/`` codebase is
# to treat ``python_app/`` as the working directory at runtime, with its
# subdirectories on ``sys.path``. See ``entity/product.py`` using
# ``from extensions import db`` and ``responses/response_structure.py``'s
# import patterns for the precedent.
# ---------------------------------------------------------------------------
from schemas.product_schema import ProductSchema


class ResponseStructureSchema(Schema):
    """Generic envelope wrapping a Product payload with status code and description.

    Mirrors the Java ``ResponseStructure<T>`` class (where ``T`` is always
    ``Product`` in this codebase, since both endpoints that use the envelope
    — F-003 ``POST /product/saveProduct`` and F-009
    ``PUT /product/updateProduct/{id}`` — wrap a single ``Product`` payload).
    Serializes a Python ``ResponseStructure`` dataclass instance to JSON of
    the shape:

        {
            "statusCode": 200,
            "apiDescription": "save product Secessfully...",
            "data": { "id": 1, "name": "Widget", "color": "blue", "price": 9.99 }
        }

    On the 406 error path (F-003 only), ``data`` becomes ``null`` and the
    ``status_code`` / ``api_description`` carry the documented error
    metadata:

        {
            "statusCode": 406,
            "apiDescription": "data not saved something went wrong",
            "data": null
        }

    The success/failure ``api_description`` strings preserve the Java
    side's typos (``Secessfully`` instead of ``Successfully``,
    ``something went wrong`` lowercase) verbatim per AAP §0.7.2 — the
    response payload is part of the externally observable API surface
    and clients may already pattern-match on these exact strings.
    """

    class Meta:
        """Marshmallow Schema configuration controlling JSON output.

        Attributes:
            ordered: When ``True``, marshmallow preserves the field
                declaration order (``status_code``, ``api_description``,
                ``data``) in the serialized JSON output. Without this
                flag, marshmallow may use a regular dict whose iteration
                order, while stable in CPython 3.7+, is not contractually
                guaranteed across the marshmallow internals. Setting
                ``ordered = True`` makes the order explicit and
                deterministic, which aligns with the Java field
                declaration order in ``ResponseStructure.java`` (lines
                13–15: ``statusCode``, ``apiDescription``, ``data``)
                that Jackson naturally respects on the Java side.
        """

        # Preserve declaration order in JSON output: statusCode,
        # apiDescription, data. Matches Java field declaration order in
        # ResponseStructure.java (lines 13–15).
        ordered = True

    # -----------------------------------------------------------------
    # ``status_code`` — HTTP-style status code carried in the envelope.
    #
    # Maps to Java ``private int statusCode;`` (ResponseStructure.java
    # line 13). ``fields.Integer()`` serializes Python ``int`` to a JSON
    # integer and deserializes JSON integers back to Python ``int``,
    # matching Java ``int`` semantics.
    #
    # CRITICAL: ``data_key="statusCode"`` preserves the camelCase JSON
    # key while the Python attribute name remains snake_case
    # (``status_code``). Without this argument, marshmallow would emit
    # ``"status_code"`` as the JSON key — that would BREAK byte-equivalent
    # parity with the Java service's output and BREAK any client code
    # that relies on the documented JSON contract.
    #
    # Documented values produced by the controllers:
    # - 200 on F-003 success / F-009 success
    # - 406 on F-003 failure (when ProductDao.saveProductDao returns None)
    # -----------------------------------------------------------------
    status_code = fields.Integer(data_key="statusCode")

    # -----------------------------------------------------------------
    # ``api_description`` — human-readable description of the operation
    # result.
    #
    # Maps to Java ``private String apiDescription;``
    # (ResponseStructure.java line 14). ``fields.String()`` serializes
    # Python ``str`` to a JSON string and deserializes JSON strings back
    # to Python ``str``.
    #
    # CRITICAL: ``data_key="apiDescription"`` preserves the camelCase
    # JSON key. Same parity rationale as ``status_code`` above.
    #
    # Documented values produced by the controllers (per AAP §0.5.2):
    # - "save product Secessfully..." on F-003 success
    # - "data not saved something went wrong" on F-003 failure
    # The typo ``Secessfully`` and the lack of capitalization on
    # ``something went wrong`` are PRESERVED VERBATIM per AAP §0.7.2 —
    # they are part of the externally observable API surface.
    # -----------------------------------------------------------------
    api_description = fields.String(data_key="apiDescription")

    # -----------------------------------------------------------------
    # ``data`` — the Product payload wrapped by the envelope.
    #
    # Maps to Java ``private T data;`` (ResponseStructure.java line 15).
    # In this codebase the type parameter ``T`` is always ``Product`` —
    # both endpoints that use the envelope (F-003 saveProduct and F-009
    # updateProduct) wrap a single Product. We therefore statically type
    # this field as ``Nested(ProductSchema)`` rather than attempting to
    # replicate Java generics (which marshmallow does not support and
    # which the codebase does not need).
    #
    # No ``data_key=`` argument is supplied because the Java field name
    # (``data``) is already lowercase and matches the desired JSON key
    # exactly. marshmallow's default behavior — using the Python
    # attribute name as the JSON key — produces ``"data"`` here, which
    # is what we want.
    #
    # CRITICAL: ``allow_none=True`` is REQUIRED. The F-003 error path
    # constructs a ``ResponseStructure`` with ``data=None`` (matching the
    # Java side's ``responseStructure.setData(null)`` when
    # ``ProductDao.saveProductDao`` returns ``null``). Without this
    # flag, marshmallow's ``Nested`` field rejects ``None`` on
    # serialization with a ``ValidationError``, which would break the
    # F-003 406 response path and diverge from documented Java behavior.
    # Java/Jackson naturally serializes ``null`` for any reference-typed
    # field; marshmallow requires the explicit opt-in.
    # -----------------------------------------------------------------
    data = fields.Nested(ProductSchema, allow_none=True)
