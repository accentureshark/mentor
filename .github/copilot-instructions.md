# Copilot Custom Instructions

## Project Context

This project implements an MCP client that is "MCP Client compliant". This means it adheres to the MCP protocol specifications and is generic, allowing connection and operation with any MCP server without requiring prior knowledge of its implementation.

The backend is built with Java 17 and uses the following main technologies and frameworks:
- **Spring Boot** (Web, WebSocket, WebFlux, Messaging)
- **Langchain4j** and **Langchain4j-Ollama** for LLM and AI integration
- **Jackson** for JSON processing
- **Jakarta Validation** for bean validation
- **Lombok** for boilerplate code reduction
- **Swagger/OpenAPI** (springdoc-openapi) for API documentation
- **Rest-Assured** for API testing
- **Maven** for build and dependency management (with plugins: spring-boot-maven-plugin, maven-compiler-plugin, maven-resources-plugin)

The backend follows a clean architecture, with code organized by domain and feature, using subpackages under `org.shark.mentor.mcp.application.service` (e.g., `service/tool`, `service/server`, `service/chat`, etc.). Lombok, JUnit, Mockito, and Maven are used. The frontend uses React, Vite, and modern JavaScript.

## Coding Agent Instructions

- **Java Backend Organization:**
  - All new service classes must go in a thematic subpackage under `org.shark.mentor.mcp.application.service` (e.g., `service/tool`, `service/server`, `service/chat`, etc.).
  - Do not leave classes directly in `service`; only subpackages are allowed.
  - Use Lombok annotations (`@Data`, `@Builder`, etc.) for models and DTOs.
  - Place tests in `backend/src/test/java/org/shark/mentor/mcp/` or appropriate subpackages.
  - Use JUnit 5 and Mockito for testing.

- **Configuration and Dependencies:**
  - Use Maven for dependency management.
  - Add new dependencies to `backend/pom.xml` or `ui/package.json` as appropriate.
  - Ensure Lombok is present in `pom.xml` and supported by the IDE.

- **Spring Boot Best Practices:**
  - Use `@Service`, `@Component`, `@Repository`, and `@RestController` as appropriate.
  - Inject dependencies via constructor or `@Autowired`.
  - Configure properties in `application.yml` and access them with `@Value` or configuration classes.

- **Frontend Guidelines:**
  - Place React components in `ui/src/components/` or subfolders.
  - Use functional components and hooks.
  - Keep service logic in `ui/src/services/`.
  - Use CSS modules or files in `ui/src/styles/`.

- **Testing and Validation:**
  - After any change, run tests with Maven (`mvn test`) or npm scripts for the frontend.
  - Fix all compilation and test errors before committing.

- **Naming and Style:**
  - Follow Java and JavaScript naming conventions.
  - Use English for class, method, and variable names.
  - Write clear, descriptive commit messages.

- **Documentation:**
  - Update documentation for new or changed endpoints.
  - Document scripts/utilities in the README or dedicated files.

---

These instructions help Copilot generate code and suggestions aligned with the project's architecture, tools, and conventions. Place this file at `.github/copilot-instructions.md` in the repository root.
