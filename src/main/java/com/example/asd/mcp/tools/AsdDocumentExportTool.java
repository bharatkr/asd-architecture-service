package com.example.asd.mcp.tools;

import com.example.asd.document.diagram.ArchitectureFlowchartRenderer;
import com.example.asd.document.docx.DocxAsdBuilder;
import com.example.asd.document.pdf.PdfAsdBuilder;
import com.example.asd.domain.DocumentFormat;
import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import org.springframework.stereotype.Component;

/**
 * Final pipeline step: renders flowchart PNG and emits {@link DocumentFormat#DOCX} or {@link DocumentFormat#PDF}.
 */
@Component
public class AsdDocumentExportTool implements McpTool {

  @Override
  public String name() {
    return "asd_document_export";
  }

  @Override
  public String description() {
    return "Export ASD as Word (DOCX) or PDF with embedded architecture diagram.";
  }

  @Override
  public void execute(McpToolContext ctx) throws Exception {
    ctx.trace(name(), "START", ctx.request().documentFormat().name());
    byte[] diagramPng = ArchitectureFlowchartRenderer.renderPng();
    byte[] document = switch (ctx.request().documentFormat()) {
      case DOCX -> DocxAsdBuilder.build(ctx, diagramPng); // may throw checked POI exceptions
      case PDF -> PdfAsdBuilder.build(ctx, diagramPng);
    };
    ctx.setDocumentBytes(document);
    ctx.trace(name(), "OK", ctx.request().documentFormat() + " bytes=" + document.length);
  }
}
