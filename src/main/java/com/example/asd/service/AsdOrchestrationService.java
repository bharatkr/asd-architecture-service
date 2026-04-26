package com.example.asd.service;

import com.example.asd.api.dto.GenerateAsdBundleResponse;
import com.example.asd.api.dto.GenerateAsdBundleResponse.ToolInvocation;
import com.example.asd.api.dto.GenerateAsdRequest;
import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import com.example.asd.util.DeletePaths;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AsdOrchestrationService {

  private final List<McpTool> pipeline;

  public AsdOrchestrationService(List<McpTool> asdMcpPipeline) {
    this.pipeline = asdMcpPipeline;
  }

  public GenerationResult runPipeline(GenerateAsdRequest request) {
    McpToolContext ctx = new McpToolContext(request);
    try {
      for (McpTool tool : pipeline) {
        tool.execute(ctx);
      }
      byte[] bytes = ctx.documentBytes();
      if (bytes == null || bytes.length == 0) {
        throw new IllegalStateException("Document export produced no output.");
      }
      String sha = ctx.commitSha();
      String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      String fileName = "ASD-" + ts + request.documentFormat().fileExtension();
      List<ToolInvocation> trace = toTrace(ctx.traceSnapshot());
      return new GenerationResult(bytes, sha, fileName, request.documentFormat(), trace);
    } catch (Exception e) {
      throw new IllegalStateException("ASD pipeline failed: " + e.getMessage(), e);
    } finally {
      try {
        if (ctx.workspaceDir() != null) {
          DeletePaths.deleteRecursively(ctx.workspaceDir());
        }
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    }
  }

  public GenerateAsdBundleResponse generateBundle(GenerateAsdRequest request) {
    GenerationResult gen = runPipeline(request);
    String b64 = Base64.getEncoder().encodeToString(gen.documentBytes());
    return new GenerateAsdBundleResponse(
        gen.commitSha(),
        gen.fileName(),
        gen.contentTypeString(),
        gen.documentFormat(),
        b64,
        gen.toolTrace()
    );
  }

  private static List<ToolInvocation> toTrace(List<Map<String, String>> rows) {
    List<ToolInvocation> list = new ArrayList<>();
    for (Map<String, String> row : rows) {
      list.add(new ToolInvocation(
          row.getOrDefault("tool", ""),
          row.getOrDefault("status", ""),
          row.getOrDefault("detail", "")
      ));
    }
    return list;
  }
}
