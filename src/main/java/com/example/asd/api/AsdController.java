package com.example.asd.api;

import com.example.asd.api.dto.GenerateAsdBundleResponse;
import com.example.asd.api.dto.GenerateAsdRequest;
import com.example.asd.service.AsdOrchestrationService;
import com.example.asd.service.GenerationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asd")
@Tag(name = "ASD", description = "Generate Architecture Specification Documents from a Git repository")
public class AsdController {

  public static final MediaType DOCX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

  private final AsdOrchestrationService orchestrationService;

  public AsdController(AsdOrchestrationService orchestrationService) {
    this.orchestrationService = orchestrationService;
  }

  @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  @Operation(
      summary = "Generate ASD (Word download)",
      description = "Runs the pipeline and returns a **.docx** file. Use Postman/curl or browser download; Swagger UI may not preview binary bodies."
  )
  @ApiResponse(responseCode = "200", description = "Word document", content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
  public ResponseEntity<byte[]> generateDocx(@Valid @RequestBody GenerateAsdRequest request) {
    GenerationResult result = orchestrationService.runPipeline(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
        .header("X-ASD-Commit-Sha", result.commitSha() == null ? "" : result.commitSha())
        .contentType(DOCX)
        .body(result.documentWord());
  }

  @PostMapping(value = "/generate/bundle", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Generate ASD (JSON + Base64)",
      description = "Same pipeline as /generate, but returns JSON including **documentWordBase64** — convenient for Swagger \"Try it out\" demos."
  )
  public GenerateAsdBundleResponse generateBundle(@Valid @RequestBody GenerateAsdRequest request) {
    return orchestrationService.generateBundle(request);
  }
}
