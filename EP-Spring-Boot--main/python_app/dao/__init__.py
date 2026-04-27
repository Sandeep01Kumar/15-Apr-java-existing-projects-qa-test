"""DAO (Data Access Object) façade package.

Contains the ``ProductDao`` class and its module-level singleton
``product_dao``. Translation of the Java ``dao/`` package which holds the
Spring ``@Repository``-annotated ``ProductDao`` class (see
``dao/ProductDao.java``).

Per AAP §0.4.3, the DAO is the service-layer façade between the controller
and the repository. It encapsulates persistence/business logic that does
not belong in the controller (e.g., the partial-update pattern in
``update_product_dao``) but also does not belong in the repository (which
is a thin SQLAlchemy wrapper).

The single member exported (via direct submodule import, NOT re-export
here) is ``dao.product_dao.product_dao`` (the lowercase singleton).
"""
