"""Marshmallow ProductSchema — JSON serialization contract for the Product entity.

This is the Python equivalent of the Jackson-driven JSON serialization that
the Java ``entity/Product.java`` class receives implicitly via
``spring-boot-starter-web``'s auto-configured ObjectMapper, plus the
``springdoc-openapi`` ``@Schema`` annotation that documents the entity in the
OpenAPI 3.x spec.

This file is intentionally SEPARATE from the SQLAlchemy ORM model in
``entity/product.py``:

- ``entity/product.py``           — persistence contract (talks to MySQL via
                                    SQLAlchemy/Flask-SQLAlchemy)
- ``schemas/product_schema.py``   — JSON contract (talks to HTTP clients via
                                    flask-smorest)

This separation mirrors the cleaner split available in the Python ecosystem
versus the Java side, where Lombok ``@Data``, JPA ``@Entity``, and (transitive)
Jackson serialization all share the same class. Per AAP §0.4.3 this is an
intentional architectural improvement that does NOT alter externally
observable behavior — the JSON wire format produced is byte-equivalent.

Per AAP §0.7.2, the JSON field names are ``id``, ``name``, ``color``,
``price`` exactly (matching Java field names from ``Product.java`` lines
14–20). The ``price`` field carries the OpenAPI description
``"price datatype is double"`` verbatim from Java's
``@Schema(description = "price datatype is double")`` annotation
(``Product.java`` line 18).

Usage
-----

The schema is consumed by flask-smorest decorators in
``controller/product_controller.py``:

- ``@blp.arguments(ProductSchema)`` — validates incoming JSON request bodies
  on ``POST /product/saveProduct``, ``PUT /product/updateProduct/{id}``,
  ``PUT /product/{id}``.
- ``@blp.arguments(ProductSchema(many=True))`` — validates the bulk-insert
  list payload on ``POST /product/saveProducts``.
- ``@blp.response(200, ProductSchema)`` — serializes single-Product
  responses on ``GET /product/getProduct/{id}`` and
  ``PUT /product/{id}``.
- ``@blp.response(200, ProductSchema(many=True))`` — serializes
  list-of-Product responses on ``GET /product/findAllProduct``,
  ``GET /product/getProductByName/{name}``,
  ``GET /product/getProductByPrice/{price}``,
  ``POST /product/saveProducts``.
- ``@blp.response(200, ProductSchema(allow_none=True))`` — serializes the
  nullable single-Product response on ``GET /product/getProduct/{id}``,
  preserving the F-006 ``null``-on-miss behavior documented in AAP §0.2.3.

flask-smorest also reads this schema's class docstring and field metadata
to populate the OpenAPI 3.x spec served at the configured
``OPENAPI_SWAGGER_UI_PATH``:

- The class docstring becomes the ``components.schemas.Product.description``
  string in the generated OpenAPI document.
- The ``price`` field's ``metadata={"description": "..."}`` dict becomes the
  ``components.schemas.Product.properties.price.description`` string.

Out-of-scope per AAP §0.3.2 / §0.7.2
-------------------------------------

The Java ``Product`` entity carries NO Bean Validation annotations
(``@NotNull``, ``@Min``, ``@Size``, etc.). To preserve byte-identical
behavior, this schema also declares NO validators:

- No ``required=True`` on any field.
- No ``validate=Range(...)`` / ``validate=Length(...)`` constraints.
- No ``@validates`` / ``@validates_schema`` decorators.
- No ``dump_only`` / ``load_only`` flags (Java does not differentiate input
  vs output shape — the same ``Product`` class serves both directions).
- No ``@post_load`` to instantiate an ORM model — the controller code
  handles ORM instance creation explicitly.
"""

# ---------------------------------------------------------------------------
# External imports.
#
# The single dependency for this module is ``marshmallow``. Per the file
# schema's external_imports declaration (AAP companion data), only
# ``Schema`` (the parent class for ``ProductSchema``) and ``fields``
# (which exposes ``Integer``, ``String``, and ``Float`` field types) are
# pulled in. flask-smorest reads the resulting Schema to drive both
# request validation and OpenAPI 3.x spec generation.
#
# This file deliberately does NOT import:
#   * ``entity.product.Product`` — the schema is decoupled from the ORM
#     model. Coupling them would create a circular import (the controller
#     imports both) and conflate the JSON wire-format concern with the
#     persistence concern.
#   * ``sqlalchemy`` — this is a JSON contract, not a database concern.
#   * ``dataclasses`` — ``ProductSchema`` is a marshmallow Schema class,
#     not a dataclass.
# ---------------------------------------------------------------------------
from marshmallow import Schema, fields


class ProductSchema(Schema):
    """this is product entity class"""

    # NOTE: The class docstring above is preserved VERBATIM from the Java
    # side's class-level annotation
    # ``@Schema(name = "product class", description = "this is product entity class")``.
    # Note the lowercase "this" with no leading capital, the lowercase
    # "p" in "product entity class", and the absence of a trailing period.
    # flask-smorest reads this docstring as the OpenAPI schema-level
    # description (``components.schemas.Product.description`` in the
    # generated spec). DO NOT capitalize, rephrase, or punctuate this
    # string — the OpenAPI document is part of the externally observable
    # API surface and must remain byte-equivalent to the Java original.

    class Meta:
        """Marshmallow Schema configuration controlling JSON output.

        Attributes:
            ordered: When True, marshmallow preserves the field
                declaration order (id, name, color, price) in the
                serialized JSON output. Without this flag, marshmallow
                may use a regular dict whose iteration order, while
                stable in CPython 3.7+, is not contractually guaranteed
                across the marshmallow internals. Setting ``ordered =
                True`` makes the order explicit and deterministic, which
                aligns with the field declaration order in
                ``Product.java`` (lines 14–20: ``id``, ``name``,
                ``color``, ``price``).
        """

        # Preserve declaration order in JSON output: id, name, color, price.
        # Matches Java field declaration order in Product.java (lines 14–20).
        ordered = True

    # -----------------------------------------------------------------
    # ``id`` — primary key.
    #
    # Maps to Java ``@Id private int id;`` (Product.java line 15).
    # No ``autoincrement`` on the database side (see entity/product.py),
    # so clients MUST supply ``id`` on writes — this preserves the
    # quirky no-@GeneratedValue behavior of the Java code per AAP §0.7.2
    # and Section 5.2.6 of the Technical Specification.
    #
    # ``fields.Integer()`` serializes Python ``int`` to a JSON integer
    # and deserializes JSON integers (or numeric strings) back to
    # Python ``int``, matching Java ``int`` semantics.
    #
    # No ``required=True`` — the Java entity carries no Bean Validation
    # annotation declaring ``id`` mandatory. While the application
    # would reject an INSERT lacking a primary key at the database
    # layer, that's a database-level error, not a schema-level
    # validation error, and the Python rewrite preserves that exact
    # error path rather than rejecting the request earlier.
    # -----------------------------------------------------------------
    id = fields.Integer()

    # -----------------------------------------------------------------
    # ``name`` — product name.
    #
    # Maps to Java ``private String name;`` (Product.java line 16).
    # ``fields.String()`` serializes Python ``str`` to a JSON string and
    # deserializes JSON strings back to Python ``str``. There is no
    # ``@Size``, ``@NotBlank``, or other constraint on the Java side, so
    # this schema does not declare any either.
    #
    # This same field also backs the Spring Data derived query
    # ``findByName(name)`` (translated to ``filter_by(name=name)`` in
    # ``repository/product_repository.py``), so the type is identical
    # both directions: clients send a JSON string, repository looks up
    # by Python ``str``, MySQL stores VARCHAR.
    # -----------------------------------------------------------------
    name = fields.String()

    # -----------------------------------------------------------------
    # ``color`` — product color.
    #
    # Maps to Java ``private String color;`` (Product.java line 17).
    # Same type and serialization semantics as ``name`` above. No
    # validators (no enum constraint, no length constraint) to match
    # the Java side's lack of validators.
    # -----------------------------------------------------------------
    color = fields.String()

    # -----------------------------------------------------------------
    # ``price`` — product price.
    #
    # Maps to Java ``private double price;`` (Product.java line 20),
    # which is preceded by the field-level annotation
    # ``@Schema(description = "price datatype is double")``
    # (Product.java line 18).
    #
    # ``fields.Float()`` matches Java ``double`` semantics: Python
    # ``float`` is a 64-bit IEEE 754 floating-point number,
    # byte-equivalent to Java ``double``. marshmallow's ``Float`` field
    # serializes Python ``float`` to a JSON number and deserializes
    # JSON numbers (integer or floating-point) to Python ``float``.
    #
    # The ``metadata={"description": ...}`` argument is the
    # marshmallow-standard mechanism for attaching arbitrary key/value
    # pairs to a field. flask-smorest reads ``metadata["description"]``
    # and emits it as the
    # ``components.schemas.Product.properties.price.description``
    # string in the generated OpenAPI 3.x spec, exactly mirroring the
    # ``@Schema(description = "price datatype is double")`` annotation
    # on the Java side. The string ``"price datatype is double"`` is
    # preserved VERBATIM — the slightly unusual phrasing is part of
    # the externally observable OpenAPI surface and must NOT be
    # corrected, capitalized, or rephrased.
    #
    # This same field also backs the native-SQL queries
    # ``select * from product where price=?`` and
    # ``delete from product where price=?`` (translated to SQLAlchemy
    # ``text(...)`` calls in ``repository/product_repository.py``).
    # -----------------------------------------------------------------
    price = fields.Float(metadata={"description": "price datatype is double"})
