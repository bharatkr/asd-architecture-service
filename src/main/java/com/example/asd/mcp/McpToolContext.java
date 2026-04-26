package com.example.asd.mcp;

import com.example.asd.api.dto.GenerateAsdRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mutable pipeline context passed between MCP tools. */
public final class McpToolContext {

  private final GenerateAsdRequest request;
  private final List<Map<String, String>> trace = new ArrayList<>();

  private Path workspaceDir;
  private Path repositoryRoot;
  private String commitSha;
  private Map<String, Object> inventorySummary = Map.of();
  private Map<String, Object> mavenSummary = Map.of();
  private Map<String, Object> springSummary = Map.of();
  private String openApiJson;
  private byte[] documentWord;

  public McpToolContext(GenerateAsdRequest request) {
    this.request = request;
  }

  public GenerateAsdRequest request() {
    return request;
  }

  public void trace(String tool, String status, String detail) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("tool", tool);
    row.put("status", status);
    row.put("detail", detail);
    trace.add(row);
  }

  public List<Map<String, String>> traceSnapshot() {
    return List.copyOf(trace);
  }

  public Path workspaceDir() {
    return workspaceDir;
  }

  public void setWorkspaceDir(Path workspaceDir) {
    this.workspaceDir = workspaceDir;
  }

  public Path repositoryRoot() {
    return repositoryRoot;
  }

  public void setRepositoryRoot(Path repositoryRoot) {
    this.repositoryRoot = repositoryRoot;
  }

  public String commitSha() {
    return commitSha;
  }

  public void setCommitSha(String commitSha) {
    this.commitSha = commitSha;
  }

  public Map<String, Object> inventorySummary() {
    return inventorySummary;
  }

  public void setInventorySummary(Map<String, Object> inventorySummary) {
    this.inventorySummary = inventorySummary;
  }

  public Map<String, Object> mavenSummary() {
    return mavenSummary;
  }

  public void setMavenSummary(Map<String, Object> mavenSummary) {
    this.mavenSummary = mavenSummary;
  }

  public Map<String, Object> springSummary() {
    return springSummary;
  }

  public void setSpringSummary(Map<String, Object> springSummary) {
    this.springSummary = springSummary;
  }

  public String openApiJson() {
    return openApiJson;
  }

  public void setOpenApiJson(String openApiJson) {
    this.openApiJson = openApiJson;
  }

  public byte[] documentWord() {
    return documentWord;
  }

  public void setDocumentWord(byte[] documentWord) {
    this.documentWord = documentWord;
  }
}
