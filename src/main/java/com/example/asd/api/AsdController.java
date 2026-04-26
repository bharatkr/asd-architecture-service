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
@Tag(name = "ASD", description = "Generate Architecture Specification Documents from a Git repository (DOCX or PDF)")
public class AsdController {

  private final AsdOrchestrationService orchestrationService;

  public AsdController(AsdOrchestrationService orchestrationService) {
    this.orchestrationService = orchestrationService;
  }

  @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Generate ASD (binary download)",
      description = "Runs the pipeline. Response is **DOCX** or **PDF** based on `documentFormat` in the JSON body."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Generated document",
      content = {
          @Content(mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
          @Content(mediaType = "application/pdf")
      }
  )
  public ResponseEntity<byte[]> generate(@Valid @RequestBody GenerateAsdRequest request) {
    GenerationResult result = orchestrationService.runPipeline(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
        .header("X-ASD-Commit-Sha", result.commitSha() == null ? "" : result.commitSha())
        .header("X-ASD-Document-Format", result.documentFormat().name())
        .contentType(result.mediaType())
        .body(result.documentBytes());
  }

  @PostMapping(value = "/generate/bundle", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Generate ASD (JSON + Base64)",
      description = "Same pipeline as /generate; returns JSON including **documentBase64** and **contentType** for Swagger demos."
  )
  public GenerateAsdBundleResponse generateBundle(@Valid @RequestBody GenerateAsdRequest request) {
    return orchestrationService.generateBundle(request);
  }
}
