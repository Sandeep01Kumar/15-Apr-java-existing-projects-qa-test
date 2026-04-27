"""REST API controller layer for the Flask Product/Student CRUD service.

This package contains the Python translations of the two Java REST
controllers under
EP-Spring-Boot--main/src/main/java/com/jspider/spring_boot_simple_crud_with_mysql/controller/.

Modules:
    product_controller: Translation of ProductController.java — exposes
        the ten endpoints under /product/* via a flask_smorest.Blueprint
        named `blp`. The Blueprint is imported by app.py and registered
        with the flask-smorest Api.
    student_controller: Translation of StudentController.java — exposes
        the two endpoints under /student/* via a flask_smorest.Blueprint
        named `blp`. CORS is intentionally NOT configured for /student/*
        (matching the Java side's lack of @CrossOrigin on
        StudentController).
"""
