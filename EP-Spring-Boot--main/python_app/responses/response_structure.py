"""Generic response envelope used by the two envelope-returning endpoints.

This is the Python equivalent of `responses/ResponseStructure.java`. It is
a small dataclass carrying three fields. The marshmallow schema in
`schemas/response_structure_schema.py` maps these snake_case Python
attribute names to the camelCase JSON keys (`statusCode`, `apiDescription`,
`data`) when flask-smorest serializes responses.

Used by exactly TWO endpoints (per AAP §0.2.3):
- POST /product/saveProduct (F-003)
- PUT  /product/updateProduct/{id} (F-009)

The other ten endpoints in the application return bare payloads.

Per AAP §0.4.3, the Python implementation instantiates a fresh
ResponseStructure per request — it does NOT replicate the Java side's
@Component singleton-mutation pattern (which is documented as a known
thread-safety caveat). The serialized JSON output is BYTE-IDENTICAL; the
in-memory implementation difference is invisible to clients.

The Java side's @Data + @Component + @Schema(hidden=true) annotations
translate as follows:
- @Data (Lombok)              -> @dataclass
- @Component (Spring)         -> module-level singleton in the controllers
                                 (NOT here — we explicitly instantiate per
                                 request to avoid the thread-safety bug)
- @Schema(hidden=true) (OAS)  -> the schema-level `Meta` class in
                                 ResponseStructureSchema can mark this
                                 hidden if needed; this dataclass file
                                 does not directly participate in OpenAPI.
"""

from dataclasses import dataclass
from typing import Any, Optional


@dataclass
class ResponseStructure:
    """Generic response envelope carrying statusCode, apiDescription, and data.

    Attributes:
        status_code: HTTP-style status code (e.g., 200, 406). Serialized as
            JSON key `statusCode` via the marshmallow schema.
        api_description: Human-readable description of the operation result.
            Serialized as JSON key `apiDescription` via the marshmallow schema.
        data: The payload (a Product, a list of Products, or None on failure).
            Serialized as JSON key `data`.

    Defaults allow no-arg instantiation (matching Lombok @Data's no-arg
    constructor on the Java side). Controllers always supply all three
    fields, so the defaults are rarely used.
    """

    status_code: int = 0
    api_description: str = ""
    data: Optional[Any] = None
