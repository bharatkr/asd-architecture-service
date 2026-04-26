package com.example.asd.service;

import com.example.asd.api.dto.GenerateAsdBundleResponse.ToolInvocation;

import java.util.List;

/** Result of a single ASD pipeline execution (before temp workspace is deleted). */
public record GenerationResult(
    byte[] documentWord,
    String commitSha,
    String fileName,
    List<ToolInvocation> toolTrace
) {}
