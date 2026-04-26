package com.example.asd.api.dto;

import com.example.asd.domain.DocumentFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "ASD generation bundle (JSON + Base64 document) for Swagger-friendly demos")
public record GenerateAsdBundleResponse(
    @Schema(description = "Git HEAD commit SHA when resolved")
    String commitSha,

    @Schema(description = "Suggested download file name")
    String fileName,

    @Schema(description = "MIME type of the generated artifact")
    String contentType,

    @Schema(description = "DOCX or PDF")
    DocumentFormat documentFormat,

    @Schema(description = "Generated document bytes as Base64")
    String documentBase64,

    @Schema(description = "Ordered MCP-style tool invocations")
    List<ToolInvocation> toolTrace
) {
  public record ToolInvocation(String toolName, String status, String detail) {}
}
