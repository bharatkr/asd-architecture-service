package com.example.asd.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.http.MediaType;

import java.util.Locale;

/**
 * Supported ASD output formats. JSON body accepts {@code "DOCX"} or {@code "PDF"} (case-insensitive).
 */
public enum DocumentFormat {
  DOCX,
  PDF;

  @JsonCreator
  public static DocumentFormat fromJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return DOCX;
    }
    try {
      return DocumentFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("documentFormat must be DOCX or PDF");
    }
  }

  @JsonValue
  public String toJson() {
    return name();
  }

  public String fileExtension() {
    return switch (this) {
      case DOCX -> ".docx";
      case PDF -> ".pdf";
    };
  }

  public MediaType mediaType() {
    return switch (this) {
      case DOCX -> MediaType.parseMediaType(
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
      case PDF -> MediaType.APPLICATION_PDF;
    };
  }
}
