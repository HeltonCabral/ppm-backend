# PPM Backend

API REST de gestão estratégica de portfólio, construída com Java 17, Spring Boot, JPA e PostgreSQL.

## Executar

Crie o banco e o utilizador configurados abaixo (ou defina as variáveis de ambiente equivalentes):

```text
DB_URL=jdbc:postgresql://localhost:5432/ppm_db
DB_USERNAME=ppm_user
DB_PASSWORD=ppm_password
```

Depois execute:

```powershell
mvn spring-boot:run
```

A API fica em `http://localhost:8080/api`. O Swagger UI fica em
`http://localhost:8080/api/swagger-ui.html`.

## Recursos

- `/strategic-pillars`
- `/strategic-objectives`
- `/programs`
- `/projects`
- `/dependencies`
- `/macro-resources`
- `/suppliers`
- `/benefits`
- `/project-scorings`
- `/capacity-allocations`

Todos possuem `GET`, `GET /{id}`, `POST`, `PUT /{id}` e `DELETE /{id}`. Os filtros e operações especiais descritos no contrato também estão documentados no Swagger.

## Testes

```powershell
mvn test
```

Os testes usam H2 em memória no modo compatível com PostgreSQL.
