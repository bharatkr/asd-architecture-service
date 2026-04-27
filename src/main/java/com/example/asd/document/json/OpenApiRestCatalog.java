package com.example.asd.document.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Extracts REST-style HTTP operations from OpenAPI 3.x or Swagger 2.0 JSON for ASD tables.
 */
public final class OpenApiRestCatalog {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> HTTP_METHODS = Set.of(
      "get", "post", "put", "patch", "delete", "options", "head", "trace");

  private OpenApiRestCatalog() {}

  public static int countOperations(String json) {
    return collect(json, Integer.MAX_VALUE).size();
  }

  /**
   * Rows: HTTP method, path template, summary (may be empty).
   */
  public static List<String[]> operationsTable(String json, int limit) {
    List<Map<String, String>> raw = collect(json, limit);
    List<String[]> rows = new ArrayList<>(raw.size());
    for (Map<String, String> m : raw) {
      rows.add(new String[] {
          m.getOrDefault("httpMethod", ""),
          m.getOrDefault("path", ""),
          truncate(m.getOrDefault("summary", ""), 120)
      });
    }
    return rows;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }

  private static List<Map<String, String>> collect(String json, int limit) {
    List<Map<String, String>> out = new ArrayList<>();
    if (json == null || json.isBlank() || limit <= 0) {
      return out;
    }
    try {
      JsonNode root = MAPPER.readTree(json);
      JsonNode paths = root.path("paths");
      if (!paths.isObject()) {
        return out;
      }
      Iterator<String> pathNames = paths.fieldNames();
      while (pathNames.hasNext() && out.size() < limit) {
        String path = pathNames.next();
        JsonNode pathItem = paths.path(path);
        if (!pathItem.isObject()) {
          continue;
        }
        Iterator<String> fieldNames = pathItem.fieldNames();
        while (fieldNames.hasNext() && out.size() < limit) {
          String field = fieldNames.next();
          if (!HTTP_METHODS.contains(field.toLowerCase(Locale.ROOT))) {
            continue;
          }
          JsonNode op = pathItem.path(field);
          if (!op.isObject()) {
            continue;
          }
          Map<String, String> row = new LinkedHashMap<>();
          row.put("httpMethod", field.toUpperCase(Locale.ROOT));
          row.put("path", path);
          row.put("summary", text(op.path("summary")));
          if (row.get("summary").isEmpty()) {
            row.put("summary", text(op.path("operationId")));
          }
          out.add(row);
        }
      }
    } catch (Exception ignored) {
      // return partial / empty
    }
    return out;
  }

  private static String text(JsonNode n) {
    if (n == null || n.isMissingNode() || n.isNull()) {
      return "";
    }
    return n.asText("");
  }
}
