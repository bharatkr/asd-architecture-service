# ASD Architecture Service — Design Document

This document explains **what the service does**, **how the pieces fit together**, and **how to demo** it. It matches the implementation in `asd-architecture-service`.

---

## 1. Purpose

Generate an **Architecture Specification Document (ASD)** from a remote **Git** repository (typically GitHub), using a **fixed pipeline of analysis steps** exposed as **MCP-style tools** (Java interfaces), and deliver the result as a **Microsoft Word `.docx`** with structured headings, tables, and monospace technical blocks.

Two consumption modes:

| Mode | Endpoint | Response | Best for |
|------|-----------|----------|----------|
| **File download** | `POST /api/v1/asd/generate` | Raw `.docx` bytes | Postman, curl, browser download |
| **Swagger demo** | `POST /api/v1/asd/generate/bundle` | JSON + Base64 document + tool trace | Swagger UI “Try it out” |

---

## 2. Your mental model (REST vs MCP)

- **Swagger / OpenAPI** documents the **REST API** (`/v3/api-docs`, `/swagger-ui.html`).
- **MCP (Model Context Protocol)** here is implemented as a **design pattern**: discrete **tools** (`McpTool`) invoked in order by an **orchestrator** inside the **same JVM**. There is **no separate MCP daemon** in this baseline; you can add a wire-protocol MCP server later that delegates to the same Spring beans.
- **Flow:** HTTP request → `AsdController` → `AsdOrchestrationService.runPipeline` → tools mutate shared `McpToolContext` → final tool writes **DOCX bytes** → temp clone directory deleted in `finally`.

```mermaid
sequenceDiagram
  participant Client
  participant AsdController
  participant Orchestrator
  participant Tools
  participant Disk

  Client->>AsdController: POST JSON (githubUrl, branch?, swaggerUrl?, flags)
  AsdController->>Orchestrator: runPipeline(request)
  Orchestrator->>Disk: temp workspace
  loop Each McpTool in order
    Orchestrator->>Tools: execute(context)
    Tools->>Context: append trace + summaries
  end
  Orchestrator->>Disk: delete workspace (finally)
  Orchestrator-->>AsdController: GenerationResult (bytes, sha, filename, trace)
  AsdController-->>Client: .docx OR JSON bundle
```

---

## 3. Component diagram

```mermaid
flowchart TB
  subgraph API["HTTP API"]
    C[AsdController]
  end
  subgraph Core["Orchestration"]
    O[AsdOrchestrationService]
    P[asdMcpPipeline List of McpTool]
  end
  subgraph Tools["MCP-style tools"]
    T1[GitCloneTool]
    T2[RepositoryInventoryTool]
    T3[MavenModuleGraphTool]
    T4[SpringStereotypeScanTool]
    T5[OpenApiIngestTool]
    T6[AsdDocxAssembleTool]
  end
  subgraph Infra["Infrastructure"]
    Props[AsdProperties]
    Del[DeletePaths]
  end

  C --> O
  O --> P
  P --> T1 --> T2 --> T3 --> T4 --> T5 --> T6
  T1 --> Props
  T2 --> Props
  T4 --> Props
  T5 --> RestClient
  O --> Del
```

---

## 4. Tool contracts

### 4.1 `McpTool`

- `name()` — stable identifier (e.g. `git_clone`) used in traces.
- `description()` — human-readable summary for docs / future MCP exposure.
- `execute(McpToolContext ctx)` — reads prior state from `ctx`, writes new state, calls `ctx.trace(...)`.

### 4.2 `McpToolContext`

Mutable **pipeline context**:

| Field | Produced by | Used by |
|--------|-------------|----------|
| `workspaceDir` | `GitCloneTool` | Cleanup |
| `repositoryRoot` | `GitCloneTool` | All scanners |
| `commitSha` | `GitCloneTool` | ASD title table |
| `inventorySummary` | `RepositoryInventoryTool` | DOCX §2 |
| `mavenSummary` | `MavenModuleGraphTool` | DOCX §3 |
| `springSummary` | `SpringStereotypeScanTool` | DOCX §4 |
| `openApiJson` | `OpenApiIngestTool` | DOCX §7 |
| `documentWord` | `AsdDocxAssembleTool` | HTTP response |

**Trace:** ordered list of `{tool, status, detail}` returned on **`/generate/bundle`**. The Word document’s Appendix A points readers there so the trace always includes the final assembler step.

---

## 5. Tool-by-tool behavior

| Tool | Technology | Output |
|------|-------------|--------|
| **git_clone** | JGit shallow clone | Files on disk; `commitSha` |
| **repo_inventory** | `Files.walk` (capped) | Extension histogram, counts of `pom.xml`, Gradle files, `application*` configs |
| **maven_module_graph** | `MavenXpp3Reader` | `groupId` / `artifactId` / `modules` / sample dependencies from **root** `pom.xml` only |
| **spring_stereotype_scan** | Line scan of `.java` | Counts of `@RestController`, `@Service`, etc. (heuristic, not a full AST) |
| **openapi_ingest** | `RestClient` GET | Optional OpenAPI JSON from a **running** `swaggerUrl` |
| **asd_docx_assemble** | Apache POI `XWPFDocument` | Final `.docx` bytes |

**Limitations (honest for interviews):**

- Root POM only for Maven graph; multi-module child POMs are not all merged.
- Spring scan is **textual**, not JavaParser-level semantics.
- OpenAPI section is **truncated** to keep document size reasonable.
- Mermaid in Word is **plain text** (Word does not render Mermaid natively).

---

## 6. Configuration

`application.properties`:

| Key | Meaning |
|-----|---------|
| `asd.clone-depth` | Shallow clone depth (default `1`) |
| `asd.scan-max-files` | Max nodes / Java files walked per scan |
| `server.port` | Default `8015` |

---

## 7. Security & operations notes

- Only **http(s)** Git and Swagger URLs are accepted in tools (see `GitCloneTool` / `OpenApiIngestTool`).
- Clones go under **system temp**; deleted in `finally` after pipeline completes.
- For production: add **auth** on endpoints, **rate limits**, **disk quotas**, **allowlist** of Git hosts, and run clones in **sandboxed workers**.

---

## 8. Extension roadmap

1. **JavaParser** (or similar) for accurate type / import graphs.
2. **`mvn dependency:tree`** in an isolated container for full dependency graphs.
3. **LLM enrichment** (e.g. Gemini) with **JSON-only** inputs = tool outputs; require citations.
4. **Wire-protocol MCP server** (Spring AI MCP or `io.modelcontextprotocol`) exposing the same tools to Cursor/Claude without REST.

---

## 9. Demo checklist

1. `mvn spring-boot:run` from `asd-architecture-service`.
2. Open Swagger: `http://localhost:8015/swagger-ui.html`.
3. Call **`POST /api/v1/asd/generate/bundle`** with a small public repo JSON body; copy `documentWordBase64`, decode to `.docx`, open in Word.
4. Call **`POST /api/v1/asd/generate`** with Postman → **Send and Download** to get the file directly.

Example body:

```json
{
  "githubUrl": "https://github.com/spring-projects/spring-petclinic.git",
  "branch": "main",
  "swaggerUrl": null,
  "includeLlmPlaceholderSection": true
}
```

---

## 10. Glossary

| Term | Meaning |
|------|---------|
| **ASD** | Architecture Specification Document produced by this service. |
| **MCP-style tool** | A composable pipeline step with a clear name + trace; same spirit as MCP tools, in-process. |
| **Orchestrator** | `AsdOrchestrationService` — runs tools in order, owns lifecycle of temp workspace. |
