"""Marshmallow Schemas package.

Contains the JSON-shape contracts that flask-smorest uses to validate incoming
request bodies (``@blp.arguments(...)``), serialize outgoing Python objects to
JSON (``@blp.response(200, ...)``), and generate the OpenAPI 3.x specification
rendered by the bundled Swagger UI. Python equivalent of Lombok ``@Data`` +
Jackson auto-binding on the Java side: it carries the JSON-serialization
concern that is intentionally separate from the SQLAlchemy ORM model (which
lives in ``entity/``). The members exported are
``schemas.product_schema.ProductSchema`` and
``schemas.response_structure_schema.ResponseStructureSchema``.
"""
