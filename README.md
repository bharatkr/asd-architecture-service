# ASD Architecture Service

Small Spring Boot app that clones a Git repo, runs a **pipeline of MCP-style tools** in-process (`McpTool` + shared `McpToolContext`: clone, inventory, Maven graph, Spring scan, SQL scripts, optional OpenAPI, then export), and writes an **Architecture Specification Document** as **DOCX** or **PDF**. That’s the same *shape* as Model Context Protocol tooling (discrete tools, shared context, trace), but it’s **not** a standalone MCP wire server—everything runs in one JVM unless you add a protocol adapter later.

The doc includes a simple **PNG** flowchart so Word/PDF don’t choke on Mermaid.

More detail: [`docs/DESIGN.md`](docs/DESIGN.md).

## What’s in the code (rough map)

| Package | What it does |
|---------|----------------|
| `api` | REST controllers, DTOs, errors |
| `config` | Spring wiring for the pipeline |
| `domain` | Small types like `DocumentFormat` |
| `document.diagram` | Flowchart → PNG (Java2D) |
| `document.docx` / `document.pdf` | DOCX (POI) and PDF (PDFBox) |
| `document.json` | Shared JSON formatting for traces |
| `mcp` | `McpTool` + `McpToolContext` |
| `mcp.tools` | Clone, inventory, Maven graph, Spring scan, SQL, OpenAPI, export |
| `service` | Runs the pipeline, returns `GenerationResult` |
| `util` | File/path helpers |

## Run it

```bash
cd asd-architecture-service
mvn spring-boot:run
```

- Swagger UI: `http://localhost:8015/swagger-ui.html`  
- OpenAPI JSON: `http://localhost:8015/v3/api-docs`

## HTTP API

| Method | Path | What you get |
|--------|------|----------------|
| `POST` | `/api/v1/asd/generate` | Raw **DOCX** or **PDF** (download) |
| `POST` | `/api/v1/asd/generate/bundle` | JSON with Base64 body, `contentType`, `documentFormat`, and `toolTrace` |

### `GenerateAsdRequest` fields

| Field | Required | Notes |
|-------|----------|--------|
| `githubUrl` | yes | HTTPS Git URL |
| `branch` | no | Uses default branch if you skip it |
| `swaggerUrl` | no | e.g. `http://localhost:8080/v3/api-docs` |
| `includeLlmPlaceholderSection` | no | boolean |
| `documentFormat` | no | `DOCX` (default) or `PDF` |

### Example body (PDF)

```json
{
  "githubUrl": "https://github.com/spring-projects/spring-petclinic.git",
  "branch": "main",
  "swaggerUrl": null,
  "includeLlmPlaceholderSection": true,
  "documentFormat": "PDF"
}
```

### Download with curl

```bash
curl -sS -X POST "http://localhost:8015/api/v1/asd/generate" \
  -H "Content-Type: application/json" \
  -d '{"githubUrl":"https://github.com/spring-projects/spring-petclinic.git","branch":"main","swaggerUrl":null,"includeLlmPlaceholderSection":false,"documentFormat":"DOCX"}' \
  -o ASD.docx
```

## Moving this folder

If you prefer it under a `backend/` tree with other services, copy the directory and wire it into your parent POM like any other module—nothing here depends on living inside the frontend repo.
