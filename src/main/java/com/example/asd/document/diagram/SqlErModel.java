package com.example.asd.document.diagram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Inferred relational model from SQL DDL heuristics (CREATE TABLE / REFERENCES). */
public final class SqlErModel {

  private final List<String> tables;
  private final List<Relationship> relationships;

  public SqlErModel(List<String> tables, List<Relationship> relationships) {
    this.tables = List.copyOf(tables);
    this.relationships = List.copyOf(relationships);
  }

  public static SqlErModel empty() {
    return new SqlErModel(List.of(), List.of());
  }

  public List<String> tables() {
    return tables;
  }

  public List<Relationship> relationships() {
    return relationships;
  }

  public boolean hasTables() {
    return !tables.isEmpty();
  }

  /** Merge another model into this one (union tables, union edges, stable order). */
  public SqlErModel merge(SqlErModel other) {
    Map<String, String> order = new LinkedHashMap<>();
    for (String t : tables) {
      order.putIfAbsent(canonical(t), t);
    }
    for (String t : other.tables) {
      order.putIfAbsent(canonical(t), t);
    }
    List<Relationship> edges = new ArrayList<>(relationships);
    for (Relationship r : other.relationships) {
      if (!edges.contains(r)) {
        edges.add(r);
      }
    }
    return new SqlErModel(new ArrayList<>(order.values()), edges);
  }

  public static String canonical(String name) {
    return normalizeIdent(name).toLowerCase();
  }

  public static String normalizeIdent(String raw) {
    if (raw == null) {
      return "";
    }
    String s = raw.trim();
    if ((s.startsWith("`") && s.endsWith("`")) || (s.startsWith("\"") && s.endsWith("\""))) {
      s = s.substring(1, s.length() - 1);
    }
    if (s.startsWith("[") && s.endsWith("]")) {
      s = s.substring(1, s.length() - 1);
    }
    int dot = s.lastIndexOf('.');
    if (dot >= 0 && dot < s.length() - 1) {
      s = s.substring(dot + 1);
    }
    return s.trim();
  }

  public record Relationship(String fromTable, String toTable) {
    public Relationship {
      Objects.requireNonNull(fromTable);
      Objects.requireNonNull(toTable);
    }
  }
}
