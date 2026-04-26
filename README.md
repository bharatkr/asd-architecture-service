# ASD Architecture Service

Spring Boot service with **Swagger UI**. It clones a Git repo, runs **MCP-style tools** in-process, and produces a **formatted Word `.docx`** Architecture Specification Document (ASD).

**Design (architecture, sequence, extension roadmap):** see [`docs/DESIGN.md`](docs/DESIGN.md).

## Run

```bash
cd asd-architecture-service
mvn spring-boot:run
```

- **Swagger UI:** `http://localhost:8015/swagger-ui.html`  
- **OpenAPI JSON:** `http://localhost:8015/v3/api-docs`

## Endpoints (demo)

| Method | Path | Response |
|--------|------|----------|
| `POST` | `/api/v1/asd/generate` | **Binary `.docx`** download (`Content-Disposition: attachment`) |
| `POST` | `/api/v1/asd/generate/bundle` | **JSON**: `documentWordBase64`, `fileName`, `commitSha`, `toolTrace` |

Use **`/generate/bundle` in Swagger “Try it out”** (copy Base64 → decode to `.docx`). Use **`/generate` in Postman** (“Send and Download”) for an immediate file.

### Example request body

```json
{
  "githubUrl": "https://github.com/spring-projects/spring-petclinic.git",
  "branch": "main",
  "swaggerUrl": null,
  "includeLlmPlaceholderSection": true
}
```

Optional `swaggerUrl` (e.g. `http://localhost:8080/v3/api-docs`) pulls live OpenAPI JSON into section 7 of the Word doc.

### curl (file download)

```bash
curl -sS -X POST "http://localhost:8015/api/v1/asd/generate" \
  -H "Content-Type: application/json" \
  -d '{"githubUrl":"https://github.com/spring-projects/spring-petclinic.git","branch":"main","swaggerUrl":null,"includeLlmPlaceholderSection":true}' \
  -o ASD.docx
```

