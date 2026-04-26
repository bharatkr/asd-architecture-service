package com.example.asd.document.pdf;

import com.example.asd.document.json.JsonFormatting;
import com.example.asd.mcp.McpToolContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Builds a multi-page PDF ASD with embedded flowchart PNG. */
public final class PdfAsdBuilder {

  private PdfAsdBuilder() {}

  public static byte[] build(McpToolContext ctx, byte[] flowchartPng) throws IOException {
    try (PDDocument document = new PDDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      PageWriter pw = new PageWriter(document, regular, bold);

      pw.title("Architecture Specification Document", bold, 16);
      pw.line("Repository: " + sanitize(ctx.request().githubUrl()), regular, 11);
      pw.line("Branch: " + sanitize(ctx.request().branch() == null ? "(default)" : ctx.request().branch()), regular, 11);
      pw.line("Swagger URL: " + sanitize(ctx.request().swaggerUrl() == null ? "—" : ctx.request().swaggerUrl()), regular, 11);
      pw.line("Output: " + ctx.request().documentFormat(), regular, 11);
      pw.line("Commit: " + (ctx.commitSha() == null ? "—" : sanitize(ctx.commitSha())), regular, 11);
      pw.gap(8);

      pw.section("1. Polyglot inventory & file signals", bold);
      pw.monospace(JsonFormatting.pretty(ctx.inventorySummary()), regular, 8);

      pw.section("2. Maven module graph", bold);
      pw.monospace(JsonFormatting.pretty(ctx.mavenSummary()), regular, 8);

      pw.section("3. Spring stereotype scan (Java)", bold);
      pw.monospace(JsonFormatting.pretty(ctx.springSummary()), regular, 8);

      pw.section("4. SQL script analysis", bold);
      pw.monospace(JsonFormatting.pretty(ctx.sqlScriptSummary()), regular, 8);

      pw.section("5. OpenAPI / Swagger", bold);
      if (ctx.openApiJson() != null && !ctx.openApiJson().isBlank()) {
        pw.monospace(JsonFormatting.truncate(ctx.openApiJson(), 8000), regular, 7);
      } else {
        pw.line("Not ingested — pass swaggerUrl on the request.", regular, 10);
      }

      pw.section("6. Architecture flow (PNG)", bold);
      pw.image(flowchartPng, 480, 210);

      pw.section("7. Scalability / security (summary)", bold);
      pw.line("See checklist tables in the Word export; PDF lists key themes only.", regular, 10);
      pw.line("• Scaling: validate with metrics + load tests.", regular, 10);
      pw.line("• Security: secret scan, dependency CVEs, authZ mapping.", regular, 10);

      pw.section("Appendix", bold);
      pw.line("Full MCP tool trace: POST /api/v1/asd/generate/bundle → toolTrace", regular, 10);

      pw.close();
      document.save(out);
      return out.toByteArray();
    }
  }

  private static String sanitize(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c >= 32 && c <= 126) {
        sb.append(c);
      } else {
        sb.append(' ');
      }
    }
    return sb.toString();
  }

  private static final class PageWriter {
    private final PDDocument document;
    private final PDType1Font regular;
    private final PDType1Font bold;
    private PDPage page;
    private PDPageContentStream cs;
    /** Baseline Y from bottom of page. */
    private float y;

    PageWriter(PDDocument document, PDType1Font regular, PDType1Font bold) throws IOException {
      this.document = document;
      this.regular = regular;
      this.bold = bold;
      newPage();
    }

    private void newPage() throws IOException {
      if (cs != null) {
        cs.close();
      }
      page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      cs = new PDPageContentStream(document, page);
      y = page.getMediaBox().getHeight() - 60;
    }

    private void ensure(float delta) throws IOException {
      if (y - delta < 56) {
        newPage();
      }
    }

    void title(String text, PDType1Font font, int size) throws IOException {
      ensure(28);
      cs.beginText();
      cs.setFont(font, size);
      cs.newLineAtOffset(50, y);
      cs.showText(sanitize(text));
      cs.endText();
      y -= 26;
    }

    void section(String text, PDType1Font font) throws IOException {
      y -= 6;
      ensure(22);
      cs.beginText();
      cs.setFont(font, 13);
      cs.newLineAtOffset(50, y);
      cs.showText(sanitize(text));
      cs.endText();
      y -= 18;
    }

    void line(String text, PDType1Font font, float size) throws IOException {
      for (String part : wrap(sanitize(text), 100)) {
        ensure(size + 4);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(50, y);
        cs.showText(part);
        cs.endText();
        y -= size + 3;
      }
    }

    void gap(float pts) {
      y -= pts;
    }

    void monospace(String block, PDType1Font font, float size) throws IOException {
      for (String rawLine : block.split("\n")) {
        for (String part : wrap(sanitize(rawLine), 105)) {
          ensure(size + 3);
          cs.beginText();
          cs.setFont(font, size);
          cs.newLineAtOffset(50, y);
          cs.showText(part.isEmpty() ? " " : part);
          cs.endText();
          y -= size + 2;
        }
      }
    }

    void image(byte[] png, float width, float height) throws IOException {
      ensure(height + 24);
      PDImageXObject img = PDImageXObject.createFromByteArray(document, png, "flow");
      float yBottom = y - height;
      cs.drawImage(img, 50, yBottom, width, height);
      y = yBottom - 12;
    }

    void close() throws IOException {
      cs.close();
    }
  }

  private static List<String> wrap(String s, int maxChars) {
    List<String> lines = new ArrayList<>();
    if (s == null || s.isEmpty()) {
      lines.add("");
      return lines;
    }
    int i = 0;
    while (i < s.length()) {
      int end = Math.min(i + maxChars, s.length());
      lines.add(s.substring(i, end));
      i = end;
    }
    return lines;
  }
}
