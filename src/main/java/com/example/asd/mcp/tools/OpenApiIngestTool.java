package com.example.asd.mcp.tools;

import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

@Component
public class OpenApiIngestTool implements McpTool {

  private final RestClient restClient;

  public OpenApiIngestTool(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public String name() {
    return "openapi_ingest";
  }

  @Override
  public String description() {
    return "Optional fetch of OpenAPI JSON from a running service (e.g. /v3/api-docs).";
  }

  @Override
  public void execute(McpToolContext ctx) {
    String url = ctx.request().swaggerUrl();
    if (url == null || url.isBlank()) {
      ctx.trace(name(), "SKIP", "swaggerUrl not provided");
      return;
    }

    URI uri = URI.create(url.trim());
    if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
      ctx.trace(name(), "FAIL", "Only http(s) swagger URLs are allowed");
      return;
    }

    ctx.trace(name(), "START", uri.toString());
    try {
      String body = restClient.get()
          .uri(uri)
          .header("Accept", "application/json, */*")
          .retrieve()
          .body(String.class);
      ctx.setOpenApiJson(body);
      ctx.trace(name(), "OK", "chars=" + (body == null ? 0 : body.length()));
    } catch (RestClientException ex) {
      ctx.trace(name(), "FAIL", ex.getMessage());
    }
  }
}
