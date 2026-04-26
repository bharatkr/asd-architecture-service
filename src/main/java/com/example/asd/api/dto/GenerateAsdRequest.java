package com.example.asd.api.dto;

import com.example.asd.domain.DocumentFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Input for ASD generation")
public record GenerateAsdRequest(
    @NotBlank
    @Schema(description = "HTTPS Git URL (e.g. https://github.com/org/repo.git)", example = "https://github.com/spring-projects/spring-petclinic.git")
    String githubUrl,

    @Schema(description = "Optional branch or tag to clone (default: remote HEAD)", example = "main")
    String branch,

    @Schema(description = "Optional OpenAPI/Swagger URL of a running service (e.g. http://localhost:8080/v3/api-docs)", example = "http://localhost:8080/v3/api-docs")
    String swaggerUrl,

    @Schema(description = "If true, include a template section for LLM-assisted risk narrative (no external LLM call in this build)")
    boolean includeLlmPlaceholderSection,

    @Schema(description = "Output document type: DOCX or PDF (default DOCX)", example = "DOCX", allowableValues = {"DOCX", "PDF"})
    DocumentFormat documentFormat
) {
  public GenerateAsdRequest {
    if (branch != null && branch.isBlank()) {
      branch = null;
    }
    if (swaggerUrl != null && swaggerUrl.isBlank()) {
      swaggerUrl = null;
    }
    if (documentFormat == null) {
      documentFormat = DocumentFormat.DOCX;
    }
  }
}
