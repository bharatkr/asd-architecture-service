package com.example.asd.mcp.tools;

import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class AsdDocxAssembleTool implements McpTool {

  private static final ObjectMapper PRETTY = new ObjectMapper();

  @Override
  public String name() {
    return "asd_docx_assemble";
  }

  @Override
  public String description() {
    return "Compose a formatted Word (.docx) Architecture Specification Document from prior tool outputs.";
  }

  @Override
  public void execute(McpToolContext ctx) throws Exception {
    ctx.trace(name(), "START", "render docx");
    try (XWPFDocument doc = new XWPFDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      titleBlock(doc, ctx);
      spacer(doc);
      h1(doc, "1. Repository & revision");
      repoMetaTable(doc, ctx);
      h1(doc, "2. Repository inventory");
      caption(doc, "Source tool: repo_inventory — file tree statistics.");
      jsonBlock(doc, prettyJson(ctx.inventorySummary()));
      h1(doc, "3. Maven module graph");
      caption(doc, "Source tool: maven_module_graph — root POM when present.");
      jsonBlock(doc, prettyJson(ctx.mavenSummary()));
      h1(doc, "4. Spring stereotype scan");
      caption(doc, "Heuristic scan of .java sources (substring match in first lines per file).");
      jsonBlock(doc, prettyJson(ctx.springSummary()));
      h1(doc, "5. Service boundaries (draft)");
      bulletsManual(doc, List.of(
          "Build system: inferred from pom.xml / Gradle markers in inventory.",
          "Modules: see modules list in Maven summary when present.",
          "HTTP/API layer: correlate @RestController / @Controller counts with OpenAPI (if ingested)."
      ));
      h1(doc, "6. Dependency & integration surface (draft)");
      bulletsManual(doc, List.of(
          "Direct Maven artifacts: see dependencyArtifactsSample in Maven summary.",
          "Next step: optional mvn dependency:tree in an isolated worker for production-grade graphs."
      ));
      h1(doc, "7. OpenAPI / Swagger");
      if (ctx.openApiJson() != null && !ctx.openApiJson().isBlank()) {
        caption(doc, "Source tool: openapi_ingest — excerpt below (truncated for document size).");
        monoBlock(doc, truncate(ctx.openApiJson(), 14000));
      } else {
        body(doc, "Not ingested. Pass swaggerUrl (e.g. http://localhost:8080/v3/api-docs) on the REST request to populate this section.");
      }
      h1(doc, "8. Data flow (Mermaid — text form)");
      monoBlock(doc, """
          flowchart LR
            HTTP[HTTP Client] --> CTRL[Controllers]
            CTRL --> SVC[Services]
            SVC --> REPO[Repositories]
            SVC --> EXT[External APIs]
          """);
      h1(doc, "9. Scalability & reliability (checklist)");
      checklistTable(doc);
      h1(doc, "10. Security posture (checklist)");
      securityTable(doc);
      if (ctx.request().includeLlmPlaceholderSection()) {
        h1(doc, "11. LLM-assisted narrative (placeholder)");
        body(doc, "Wire this section to Gemini (or similar) with strict JSON input = outputs of repo_inventory, maven_module_graph, spring_stereotype_scan, and optional openapi_ingest. Require citations to paths and POM coordinates.");
      }
      h1(doc, "Appendix A — MCP-style tool trace");
      caption(doc, "For a complete, ordered tool trace (including the final DOCX assembler), call POST /api/v1/asd/generate/bundle and read the toolTrace array.");
      footer(doc);

      doc.write(out);
      ctx.setDocumentWord(out.toByteArray());
      ctx.trace(name(), "OK", "docxBytes=" + out.size());
    } catch (Exception e) {
      ctx.trace(name(), "FAIL", e.getMessage());
      throw e;
    }
  }

  private static void spacer(XWPFDocument doc) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingAfter(200);
  }

  private static void titleBlock(XWPFDocument doc, McpToolContext ctx) {
    XWPFParagraph p = doc.createParagraph();
    p.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun r = p.createRun();
    r.setBold(true);
    r.setFontFamily("Calibri");
    r.setFontSize(28);
    r.setText("Architecture Specification Document");
    r.addBreak();
    XWPFRun sub = p.createRun();
    sub.setFontFamily("Calibri");
    sub.setFontSize(14);
    sub.setItalic(true);
    sub.setText("Repository: " + shortUrl(ctx.request().githubUrl()));
    sub.addBreak();
    sub.setText("Generated: " + Instant.now().toString());
  }

  private static String shortUrl(String u) {
    if (u == null) {
      return "";
    }
    return u.length() > 90 ? u.substring(0, 87) + "…" : u;
  }

  private static void h1(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingBefore(240);
    p.setSpacingAfter(120);
    XWPFRun r = p.createRun();
    r.setBold(true);
    r.setFontFamily("Calibri Light");
    r.setFontSize(18);
    r.setColor("1F4E79");
    r.setText(text);
  }

  private static void caption(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingAfter(80);
    XWPFRun r = p.createRun();
    r.setItalic(true);
    r.setFontFamily("Calibri");
    r.setFontSize(10);
    r.setColor("5A5A5A");
    r.setText(text);
  }

  private static void body(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingAfter(120);
    XWPFRun r = p.createRun();
    r.setFontFamily("Calibri");
    r.setFontSize(11);
    r.setText(text);
  }

  private static void bulletsManual(XWPFDocument doc, List<String> items) {
    for (String item : items) {
      XWPFParagraph p = doc.createParagraph();
      p.setSpacingAfter(80);
      XWPFRun r = p.createRun();
      r.setFontFamily("Calibri");
      r.setFontSize(11);
      r.setText("• " + item);
    }
  }

  private static void repoMetaTable(XWPFDocument doc, McpToolContext ctx) {
    XWPFTable table = doc.createTable(5, 2);
    table.setWidth("100%");
    table.setTableAlignment(TableRowAlign.CENTER);
    row(table, 0, "Git URL", ctx.request().githubUrl());
    row(table, 1, "Branch / ref", ctx.request().branch() == null || ctx.request().branch().isBlank() ? "(default)" : ctx.request().branch());
    row(table, 2, "Resolved commit", ctx.commitSha() == null ? "—" : ctx.commitSha());
    row(table, 3, "Swagger URL", ctx.request().swaggerUrl() == null || ctx.request().swaggerUrl().isBlank() ? "—" : ctx.request().swaggerUrl());
    row(table, 4, "Generator", "asd-architecture-service (MCP-style tools)");
    shadeLabelColumn(table);
    doc.createParagraph();
  }

  private static void row(XWPFTable table, int rowIdx, String key, String value) {
    XWPFTableRow row = table.getRow(rowIdx);
    setCell(row.getCell(0), key, true);
    setCell(row.getCell(1), value == null ? "" : value, false);
  }

  private static void setCell(XWPFTableCell cell, String text, boolean header) {
    cell.removeParagraph(0);
    XWPFParagraph p = cell.addParagraph();
    XWPFRun r = p.createRun();
    r.setFontFamily("Calibri");
    r.setFontSize(11);
    if (header) {
      r.setBold(true);
      r.setColor("1F4E79");
    }
    r.setText(text);
  }

  private static void shadeLabelColumn(XWPFTable table) {
    for (int i = 0; i < table.getNumberOfRows(); i++) {
      table.getRow(i).getCell(0).setColor("D9E2F3");
    }
  }

  private static void jsonBlock(XWPFDocument doc, String json) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingAfter(160);
    XWPFRun r = p.createRun();
    r.setFontFamily("Consolas");
    r.setFontSize(9);
    for (String line : json.split("\n")) {
      r.setText(line);
      r.addBreak();
    }
  }

  private static void monoBlock(XWPFDocument doc, String block) {
    jsonBlock(doc, block);
  }

  private static void checklistTable(XWPFDocument doc) {
    XWPFTable t = doc.createTable(4, 3);
    t.setWidth("100%");
    headerRow(t, 0, "Topic", "Status", "Notes");
    dataRow(t, 1, "Horizontal scaling", "Hypothesis", "Needs runtime metrics & deployment topology.");
    dataRow(t, 2, "DB pools / JDBC", "Hypothesis", "Inspect application*.yml and metrics.");
    dataRow(t, 3, "N+1 / ORM", "Hypothesis", "Stereotype scan hints JPA; needs profiling.");
    doc.createParagraph();
  }

  private static void securityTable(XWPFDocument doc) {
    XWPFTable t = doc.createTable(4, 3);
    t.setWidth("100%");
    headerRow(t, 0, "Topic", "Status", "Notes");
    dataRow(t, 1, "Secrets in repo", "Hypothesis", "Add gitleaks / secret scanning in CI.");
    dataRow(t, 2, "AuthZ on HTTP", "Hypothesis", "Map security filters / annotations (extend scanner).");
    dataRow(t, 3, "Dependency CVEs", "Hypothesis", "Add OWASP Dependency-Check / Dependabot.");
    doc.createParagraph();
  }

  private static void headerRow(XWPFTable t, int row, String a, String b, String c) {
    XWPFTableRow r = t.getRow(row);
    setCell(r.getCell(0), a, true);
    setCell(r.getCell(1), b, true);
    setCell(r.getCell(2), c, true);
    for (int i = 0; i < 3; i++) {
      r.getCell(i).setColor("1F4E79");
      for (XWPFParagraph p : r.getCell(i).getParagraphs()) {
        for (XWPFRun run : p.getRuns()) {
          run.setColor("FFFFFF");
        }
      }
    }
  }

  private static void dataRow(XWPFTable t, int row, String a, String b, String c) {
    XWPFTableRow r = t.getRow(row);
    setCell(r.getCell(0), a, false);
    setCell(r.getCell(1), b, false);
    setCell(r.getCell(2), c, false);
  }

  private static void footer(XWPFDocument doc) {
    XWPFParagraph p = doc.createParagraph();
    p.setAlignment(ParagraphAlignment.CENTER);
    p.setSpacingBefore(400);
    XWPFRun r = p.createRun();
    r.setItalic(true);
    r.setFontSize(9);
    r.setColor("808080");
    r.setText("End of Architecture Specification Document");
  }

  private static String prettyJson(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return "{}";
    }
    try {
      return PRETTY.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    } catch (Exception e) {
      return String.valueOf(map);
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max) + "\n… truncated …";
  }
}
