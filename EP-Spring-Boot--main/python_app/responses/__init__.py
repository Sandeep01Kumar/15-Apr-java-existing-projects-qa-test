"""Generic response-envelope package.

Contains the ``ResponseStructure`` dataclass used by the two envelope-returning
endpoints (``POST /product/saveProduct`` and ``PUT /product/updateProduct/{id}``).
Translation of the Java ``responses/`` package which holds the
``ResponseStructure<T>`` ``@Component`` singleton. The single member exported
is ``responses.response_structure.ResponseStructure``.
"""
