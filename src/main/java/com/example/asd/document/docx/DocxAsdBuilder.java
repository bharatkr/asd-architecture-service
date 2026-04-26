package com.example.asd.document.docx;

import com.example.asd.document.json.JsonFormatting;
import com.example.asd.mcp.McpToolContext;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Builds the ASD as a structured Word document. */
public final class DocxAsdBuilder {

  private DocxAsdBuilder() {}

  public static byte[] build(McpToolContext ctx, byte[] flowchartPng) throws Exception {
    try (XWPFDocument doc = new XWPFDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      titleBlock(doc, ctx);
      spacer(doc);
      h1(doc, "1. Repository & revision");
      repoMetaTable(doc, ctx);
      h1(doc, "2. Polyglot inventory & file signals");
      caption(doc, "Extensions + language buckets (Java, Angular/TS, SQL, etc.).");
      jsonBlock(doc, JsonFormatting.pretty(ctx.inventorySummary()));
      h1(doc, "3. Maven module graph");
      caption(doc, "Root pom.xml when present.");
      jsonBlock(doc, JsonFormatting.pretty(ctx.mavenSummary()));
      h1(doc, "4. Spring stereotype scan (Java backend heuristic)");
      jsonBlock(doc, JsonFormatting.pretty(ctx.springSummary()));
      h1(doc, "5. SQL script analysis");
      jsonBlock(doc, JsonFormatting.pretty(ctx.sqlScriptSummary()));
      h1(doc, "6. Service boundaries (draft)");
      bullets(doc, List.of(
          "Map polyglot buckets to runtime tiers using repo layout and build metadata.",
          "Modules: see Maven summary when present.",
          "HTTP/API: correlate Spring stereotypes with OpenAPI (if ingested)."
      ));
      h1(doc, "7. Dependency & integration surface (draft)");
      bullets(doc, List.of(
          "Maven dependency sample: see maven_module_graph.",
          "SQL migrations: correlate sql_script_analysis with Flyway/Liquibase folders if present."
      ));
      h1(doc, "8. OpenAPI / Swagger");
      if (ctx.openApiJson() != null && !ctx.openApiJson().isBlank()) {
        jsonBlock(doc, JsonFormatting.truncate(ctx.openApiJson(), 12000));
      } else {
        body(doc, "Not ingested. Pass swaggerUrl (e.g. http://localhost:8080/v3/api-docs) to populate.");
      }
      h1(doc, "9. Architecture flow (rendered diagram)");
      caption(doc, "PNG diagram embedded for Word/PDF fidelity (Mermaid text is not rendered by Word).");
      flowchartImage(doc, flowchartPng);
      monoSmall(doc, "Mermaid equivalent (text):\nflowchart LR\n  HTTP --> CTRL --> SVC --> REPO --> EXT");
      h1(doc, "10. Scalability & reliability (checklist)");
      checklistTable(doc);
      h1(doc, "11. Security posture (checklist)");
      securityTable(doc);
      if (ctx.request().includeLlmPlaceholderSection()) {
        h1(doc, "12. LLM-assisted narrative (placeholder)");
        body(doc, "Ground Gemini (or similar) on JSON outputs from tools in this document; require citations.");
      }
      h1(doc, "Appendix A — Tool trace");
      caption(doc, "Full ordered trace with final export step: use POST /api/v1/asd/generate/bundle.");
      footer(doc);

      doc.write(out);
      return out.toByteArray();
    }
  }

  private static void flowchartImage(XWPFDocument doc, byte[] png) throws Exception {
    XWPFParagraph p = doc.createParagraph();
    p.setAlignment(ParagraphAlignment.CENTER);
    p.setSpacingAfter(200);
    XWPFRun r = p.createRun();
    try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
      r.addPicture(in, Document.PICTURE_TYPE_PNG, "architecture-flow.png",
          Units.toEMU(520), Units.toEMU(236));
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
    sub.setText("Generated: " + Instant.now());
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

  private static void bullets(XWPFDocument doc, List<String> items) {
    for (String item : items) {
      XWPFParagraph p = doc.createParagraph();
      p.setSpacingAfter(80);
      XWPFRun r = p.createRun();
      r.setFontFamily("Calibri");
      r.setFontSize(11);
      r.setText("• " + item);
    }
  }

  private static void monoSmall(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingAfter(120);
    XWPFRun r = p.createRun();
    r.setFontFamily("Consolas");
    r.setFontSize(8);
    r.setColor("444444");
    for (String line : text.split("\n")) {
      r.setText(line);
      r.addBreak();
    }
  }

  private static void repoMetaTable(XWPFDocument doc, McpToolContext ctx) {
    XWPFTable table = doc.createTable(6, 2);
    table.setWidth("100%");
    table.setTableAlignment(TableRowAlign.CENTER);
    row(table, 0, "Git URL", ctx.request().githubUrl());
    row(table, 1, "Branch / ref", ctx.request().branch() == null || ctx.request().branch().isBlank() ? "(default)" : ctx.request().branch());
    row(table, 2, "Resolved commit", ctx.commitSha() == null ? "—" : ctx.commitSha());
    row(table, 3, "Swagger URL", ctx.request().swaggerUrl() == null || ctx.request().swaggerUrl().isBlank() ? "—" : ctx.request().swaggerUrl());
    row(table, 4, "Output format", ctx.request().documentFormat().name());
    row(table, 5, "Generator", "asd-architecture-service");
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
}
