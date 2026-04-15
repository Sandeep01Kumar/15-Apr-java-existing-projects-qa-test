# 🛒 Product API : Spring Boot CRUD with MySQL

🚀 A robust and scalable Spring Boot-based RESTful API project designed to perform *CRUD operations on Product entities, integrated with **MySQL*, using clean architecture and modular design for better maintainability and scalability,

---

## 🎯 Objectives

- ✅ Develop RESTful APIs using Spring Boot  
- ✅ Perform end-to-end CRUD operations with MySQL database  
- ✅ Implement clean separation of concerns (Controller, DAO, Entity, Repository)  
- ✅ Ensure structured project architecture following best practices  
- ✅ Scalable codebase for real-time backend development and integration  

---
 
## 🗂 Project Structure Overview

<table>
  <tr>
    <td valign="top" width="25%">

<pre>
spring-boot-simple-crud-with-mysql
├── src
│   ├── main/java
│   │   └── com.jspider.spring_boot_simple_crud_with_mysql
│   │       ├── controller
│   │       │   └── ProductController.java        # Handles HTTP requests (GET, POST, PUT, DELETE)
│   │       ├── dao
│   │       │   └── ProductDao.java               # Business logic layer for product operations
│   │       ├── entity
│   │       │   └── Product.java                  # Entity mapped to MySQL DB table
│   │       ├── repository
│   │       │   └── ProductRepository.java        # JPA Repository interface
│   │       ├── responses
│   │       │   └── ResponseStructure.java        # Custom response wrapper for consistency
│   │       └── SpringBootSimpleCrudWithMysqlApplication.java  # Main class
│
│   ├── main/resources
│   │   ├── application.properties                # DB config and server settings
│   │   ├── static                                # Static files (optional for front-end)
│   │   └── templates                             # Thymeleaf templates (if used)
│
│   └── test/java                                 # Unit and integration tests (to be added)
│
├── pom.xml                                       # Maven dependencies and plugins
├── HELP.md                                       # Spring generated project help
├── mvnw, mvnw.cmd                                # Maven wrapper
├── target                                        # Compiled build output
</pre>

</td>
<td valign="top" width="55%">
  <img src="https://github.com/user-attachments/assets/079b2b9f-b935-4aea-9d41-dc989130e3de" alt="Eclipse Project Structure" width="100%" />
 
</td>
</tr>
</table> 
---
<img src="https://github.com/user-attachments/assets/939712d2-7094-4447-a997-745471c46dbc" alt="Eclipse Project Structure" width="100%" />
 
## 🌐 Technologies Used

- *Java 17* – Backend programming language  
- *Spring Boot* – Backend framework for building APIs  
- *Spring Data JPA* – ORM for database interaction  
- *MySQL* – Relational database for persistent storage  
- *Maven* – Project management and dependency tool  
- *Eclipse IDE / IntelliJ IDEA* – Development environment  
- *Postman* – API testing  
- *Git & GitHub* – Version control system  

---

## ✨ Key Features

- ✅ *RESTful API Design* using Spring Boot  
- ✅ *Modular Architecture* with clear separation of concerns  
- ✅ *CRUD Functionality*: Create, Read, Update, Delete for products  
- ✅ *Database Integration* using Spring Data JPA and MySQL  
- ✅ *Custom Response Wrapping* using ResponseStructure.java  
- ✅ *Scalable Project Template* for enterprise-level apps  
- ✅ *Maven-Based Build* and dependency management  

---

## 📦 API Endpoints

| Method | Endpoint                       | Description                                         |
|--------|--------------------------------|-----------------------------------------------------|
| POST   | /products                      | Create a new product                                |
| GET    | /products                      | Get all products                                    |
| GET    | /products/{id}                 | Get a product by ID                                 |
| PUT    | /products/{id}                 | Update product by ID                                |
| DELETE | /products/{id}                 | Delete product by ID                                |
| GET    | /product/search?name={name}    | Search products by name (partial, case-insensitive) |

---
 
📌 Sample API Test:  
 
![postman](https://github.com/user-attachments/assets/9f0e39f1-1588-4c0e-b8da-18f0c1ab899b)
 
---
<h1 align="center">🛒Product API : API Automation Framework</h1>
 
🚀 A scalable and efficient API automation framework designed to validate and test Product API services, ensuring seamless integration, data integrity, and performance.


### 🎯 Objectives
✔ Automate end-to-end API testing with CRUD operations <br>
✔ Validate API responses and data accuracy <br>
✔ Implement data-driven testing for broader coverage <br>
✔ Generate detailed execution reports with Extent Reports <br>
✔ Ensure modular, maintainable, and scalable framework design <br>

![WhatsApp Image 2025-05-23 at 20 37 18_592d0402](https://github.com/user-attachments/assets/d7d24e0e-a1da-449b-b79a-24312a7a60e2)

 

![WhatsApp Image 2025-05-23 at 20 35 39_0a1c84f6](https://github.com/user-attachments/assets/583fea34-e73b-4939-842b-25bab2de5298)

 





