    # EduApp REST API

A Spring Boot REST API for managing a teacher registry for Coding Factory @ AUEB. Provides endpoints for managing teachers, users, and organizational data, secured with JWT-based authentication and capability-based authorization.

## Tech stack

- Java 21 (Amazon Corretto toolchain)
- Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation)
- MySQL + Flyway migrations
- JWT authentication (jjwt)
- springdoc-openapi (Swagger UI)
- Apache Tika (file content-type detection)
- Lombok
- Gradle

## Prerequisites

- JDK 21
- A running MySQL instance
- Gradle Wrapper (bundled — no local Gradle install needed)

## Configuration

The app loads configuration from a `.env` file at the repo root (via `spring.config.import=optional:file:.env`), which is not committed to git. Copy `.env.example` to `.env` and fill in the values:

```
MYSQL_HOST=
MYSQL_PORT=
MYSQL_DB=
MYSQL_USER=
MYSQL_PASSWORD=
JWT_SECRET_KEY=
```

`JWT_SECRET_KEY` must be a base64-encoded secret — it's used as the HS256 signing key for JWTs.

The active Spring profile is `dev` (`application-dev.properties`), which also sets:

- `file.upload.dir=uploads/` — where uploaded attachment files (e.g. AMKA documents) are stored
- `logging.file.name=logs/eduapp.log` — application log file
- `allowed.origins=http://localhost:5174` — CORS allowed origin(s) for the frontend
- `spring.servlet.multipart.max-file-size=5MB` / `max-request-size=10MB` — upload size limits

`spring.jpa.hibernate.ddl-auto=validate` — the schema is managed entirely through Flyway migrations (`src/main/resources/db/migration/`), not Hibernate auto-DDL.

## Running the app

```bash
./gradlew bootRun
```

On Windows, use `gradlew.bat bootRun`.

On startup, Flyway applies the migrations in `src/main/resources/db/migration/`, which create the schema and seed reference data (regions, roles, and capabilities).

## Building

```bash
./gradlew build
```

## Testing

```bash
./gradlew test
```

Run a single test class:

```bash
./gradlew test --tests "gr.aueb.cf.eduapp.EduAppApplicationTests"
```

## API documentation

Once running, interactive API docs are available via Swagger UI at:

```
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI spec: `http://localhost:8080/v3/api-docs`.

Authenticate via `POST /api/v1/auth/authenticate` to obtain a JWT, then authorize in Swagger UI using the "Authorize" button (Bearer token) to call secured endpoints.

Postman collections for manual API testing are available under `postman/`.

## Authentication & authorization

- Authentication is stateless, JWT-based (`Authorization: Bearer <token>` header).
- Authorization is capability-based: each user's role grants a set of capabilities (e.g. `VIEW_TEACHER`, `EDIT_TEACHER`, `DELETE_TEACHER`, `VIEW_ONLY_TEACHER`), which map to Spring Security authorities enforced via `@PreAuthorize` on service methods.
- Teachers can only view their own profile unless granted broader `VIEW_TEACHER`/`VIEW_TEACHERS` capabilities.

## Main endpoints

| Method | Path                              | Description                                  |
|--------|-----------------------------------|-----------------------------------------------|
| POST   | `/api/v1/auth/authenticate`       | Authenticate and obtain a JWT                 |
| POST   | `/api/v1/teachers`                | Register a new teacher                        |
| POST   | `/api/v1/teachers/{uuid}/amka-file` | Upload a teacher's AMKA attachment file     |
| GET    | `/api/v1/teachers`                | List teachers, paginated and filtered         |
| GET    | `/api/v1/teachers/{uuid}`         | Get a teacher by UUID                         |
| PUT    | `/api/v1/teachers/{uuid}`         | Update a teacher                              |
| DELETE | `/api/v1/teachers/{uuid}`         | Soft-delete a teacher                         |

See Swagger UI for the full, up-to-date list of endpoints, request/response schemas, and error responses.
