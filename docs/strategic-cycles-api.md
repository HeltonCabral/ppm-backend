# Strategic cycles API

Base URL: `/api/v1`. Status values are `DRAFT`, `IN_REVIEW`, `APPROVED`, `ACTIVE`, `REPLACED`, and `CLOSED`. Their Portuguese labels remain a presentation concern in the frontend.

The current application has no authentication provider. Until Spring Security is integrated, mutating endpoints accept `X-User-Id`; when absent the audit actor is `system`. Clients must never send status, approval, audit, revision, or calculated summary fields in create/update payloads.

## Examples

Create a cycle:

```http
POST /api/v1/cycles
X-User-Id: user@example.cv
Content-Type: application/json

{"name":"Ciclo Estrategico 2027-2029","startYear":2027,"endYear":2029,"description":"Revisao anual"}
```

List: `GET /api/v1/cycles?page=0&pageSize=20&status=DRAFT&sort=startYear&order=desc`.

State workflow: `POST /cycles/{id}/submit-review`; approve/reject with `{"comment":"..."}`; activate; close. Activating an approved cycle replaces the previous active cycle in the same transaction.

Start and execute an annual review:

```http
POST /api/v1/cycle-reviews
Content-Type: application/json

{"sourceCycleId":"00000000-0000-0000-0000-000000000000"}
```

Save the returned `draft` with `PATCH /cycle-reviews/{id}`, preview with `POST /validate`, then execute using a stable `Idempotency-Key` header. Reusing the key returns the original execution rather than creating another cycle.

Errors raised by the cycle domain use `{code,message,details,traceId}`. OpenAPI is available at `/api/v3/api-docs` and Swagger UI at `/api/swagger-ui.html`.

## Database

This project currently relies on Hibernate `ddl-auto=update` and has no migration runner. For controlled environments apply `src/main/resources/db/migration/V20260720_01__strategic_cycles.sql` with `psql` before deploying the new code. The file is intentionally Flyway-compatible for adoption when a migration runner is added.
