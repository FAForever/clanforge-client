# Clan Management Platform - Project


The initial target environment is the FAForever ecosystem, but the solution should be designed as a generic and reusable platform. Game-specific rules should be isolated where possible so that the application can be adapted for other communities.
The platform shall provide:
- a user-facing web interface
- an administration interface
- a REST API
- PostgreSQL-based persistence
- authentication through an external OpenID Connect provider
- an audit history mechanism for external synchronization

The project should demonstrate modern software engineering practices, including:
- secure authentication
- database design
- web application development
- API design
- automated testing
- containerization
- continuous integration and delivery
- open-source software development practices

## Local development

Start PostgreSQL with Docker:

```bash
docker compose up -d
```

Set the datasource environment variables before starting Spring Boot. The
values below match the development-only defaults in `docker-compose.yml`:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/clan_management
export SPRING_DATASOURCE_USERNAME=clanadmin
export SPRING_DATASOURCE_PASSWORD=devpassword
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Open `http://localhost:8080/`. The development login is `admin` / `admin`.
Real credentials must be supplied through environment variables or the
deployment platform's secret store and must not be committed to Git.
