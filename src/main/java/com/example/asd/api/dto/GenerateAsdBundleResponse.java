package com.example.asd.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "ASD generation bundle (JSON + Base64 Word doc) for Swagger-friendly demos")
public record GenerateAsdBundleResponse(
    @Schema(description = "Git HEAD commit SHA when resolved")
    String commitSha,

    @Schema(description = "Suggested download file name")
    String fileName,

    @Schema(description = "Word document (.docx) as Base64")
    String documentWordBase64,

    @Schema(description = "Ordered MCP-style tool invocations")
    List<ToolInvocation> toolTrace
) {
  public record ToolInvocation(String toolName, String status, String detail) {}
}
