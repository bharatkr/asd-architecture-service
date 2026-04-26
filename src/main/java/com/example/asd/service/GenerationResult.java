package com.example.asd.service;

import com.example.asd.api.dto.GenerateAsdBundleResponse.ToolInvocation;
import com.example.asd.domain.DocumentFormat;
import org.springframework.http.MediaType;

import java.util.List;

/** Result of a single ASD pipeline execution (before temp workspace is deleted). */
public record GenerationResult(
    byte[] documentBytes,
    String commitSha,
    String fileName,
    DocumentFormat documentFormat,
    List<ToolInvocation> toolTrace
) {

  public MediaType mediaType() {
    return documentFormat.mediaType();
  }

  public String contentTypeString() {
    return mediaType().toString();
  }
}
