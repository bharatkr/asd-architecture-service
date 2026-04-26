package com.example.asd.document.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class JsonFormatting {

  private static final ObjectMapper PRETTY = new ObjectMapper();

  private JsonFormatting() {}

  public static String pretty(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return "{}";
    }
    try {
      return PRETTY.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    } catch (Exception e) {
      return String.valueOf(map);
    }
  }

  public static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max) + "\n… truncated …";
  }
}
