"""SQLAlchemy-backed repository package.

Contains the ``ProductRepository`` class and its module-level singleton
``product_repository``. Translation of the Java ``repository/`` package
which holds the Spring Data JPA ``ProductRepository`` interface (a
``JpaRepository<Product, Integer>`` with the derived query ``findByName``,
the native-SQL ``getProductByPrice``, and the ``@Modifying`` +
``@Transactional`` ``deleteProductByPrice`` operation).

The single member exported (via direct submodule import, NOT re-export
here) is ``repository.product_repository.product_repository``.
"""
