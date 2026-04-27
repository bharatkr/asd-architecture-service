package com.example.asd.document.diagram;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic extraction of table names and FK-style REFERENCES edges from SQL DDL text.
 * Not a full parser; suitable for Flyway/Liquibase-style scripts.
 */
public final class SqlErModelExtractor {

  /** Table identifier: word chars, dots, brackets, quotes, backticks. */
  private static final String IDENT = "[`\"\\[\\]\\w.]+";

  private static final Pattern CREATE_TABLE = Pattern.compile(
      "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(" + IDENT + ")\\s*\\(");

  private static final Pattern REFERENCES = Pattern.compile(
      "(?is)REFERENCES\\s+(?:" + IDENT + "\\s*\\.\\s*)?(" + IDENT + ")\\s*\\(");

  private static final Pattern ALTER_FK = Pattern.compile(
      "(?is)ALTER\\s+TABLE\\s+(?:" + IDENT + "\\s*\\.\\s*)?(" + IDENT + ")\\s+"
          + "ADD\\s+(?:CONSTRAINT\\s+" + IDENT + "\\s+)?(?:FOREIGN\\s+KEY\\s*\\([^)]+\\)\\s*)?"
          + "REFERENCES\\s+(?:" + IDENT + "\\s*\\.\\s*)?(" + IDENT + ")\\b");

  private SqlErModelExtractor() {}

  public static SqlErModel extract(String sql) {
    if (sql == null || sql.isBlank()) {
      return SqlErModel.empty();
    }
    String normalized = stripLineComments(sql);
    normalized = stripBlockComments(normalized);

    List<Block> blocks = new ArrayList<>();
    Matcher cm = CREATE_TABLE.matcher(normalized);
    while (cm.find()) {
      String table = SqlErModel.normalizeIdent(cm.group(1));
      if (!table.isEmpty()) {
        blocks.add(new Block(cm.start(), cm.end(), table));
      }
    }

    Set<String> tableSet = new LinkedHashSet<>();
    List<SqlErModel.Relationship> rels = new ArrayList<>();

    for (int i = 0; i < blocks.size(); i++) {
      Block b = blocks.get(i);
      tableSet.add(b.tableName);
      int bodyEnd = (i + 1 < blocks.size()) ? blocks.get(i + 1).start : normalized.length();
      String body = normalized.substring(b.ddlHeaderEnd, bodyEnd);
      Matcher rm = REFERENCES.matcher(body);
      while (rm.find()) {
        String to = SqlErModel.normalizeIdent(rm.group(1));
        if (!to.isEmpty() && !to.equalsIgnoreCase(b.tableName)) {
          rels.add(new SqlErModel.Relationship(b.tableName, to));
          tableSet.add(to);
        }
      }
    }

    Matcher am = ALTER_FK.matcher(normalized);
    while (am.find()) {
      String from = SqlErModel.normalizeIdent(am.group(1));
      String to = SqlErModel.normalizeIdent(am.group(2));
      if (!from.isEmpty() && !to.isEmpty() && !from.equalsIgnoreCase(to)) {
        tableSet.add(from);
        tableSet.add(to);
        SqlErModel.Relationship r = new SqlErModel.Relationship(from, to);
        if (!rels.contains(r)) {
          rels.add(r);
        }
      }
    }

    return new SqlErModel(new ArrayList<>(tableSet), dedupeRels(rels));
  }

  private static List<SqlErModel.Relationship> dedupeRels(List<SqlErModel.Relationship> rels) {
    Set<String> seen = new LinkedHashSet<>();
    List<SqlErModel.Relationship> out = new ArrayList<>();
    for (SqlErModel.Relationship r : rels) {
      String key = SqlErModel.canonical(r.fromTable()) + "->" + SqlErModel.canonical(r.toTable());
      if (seen.add(key)) {
        out.add(r);
      }
    }
    return out;
  }

  private static String stripLineComments(String sql) {
    StringBuilder sb = new StringBuilder(sql.length());
    for (String line : sql.split("\r?\n")) {
      String t = line;
      int dash = indexOfUnquoted(line, "--");
      if (dash >= 0) {
        t = line.substring(0, dash);
      }
      sb.append(t).append('\n');
    }
    return sb.toString();
  }

  private static int indexOfUnquoted(String line, String needle) {
    boolean inS = false;
    boolean inD = false;
    for (int i = 0; i <= line.length() - needle.length(); i++) {
      char c = line.charAt(i);
      if (!inD && c == '\'' && (i == 0 || line.charAt(i - 1) != '\\')) {
        inS = !inS;
      } else if (!inS && c == '"') {
        inD = !inD;
      }
      if (!inS && !inD && line.regionMatches(i, needle, 0, needle.length())) {
        return i;
      }
    }
    return -1;
  }

  private static String stripBlockComments(String sql) {
    String lower = sql.toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(sql.length());
    int i = 0;
    while (i < sql.length()) {
      int start = lower.indexOf("/*", i);
      if (start < 0) {
        out.append(sql, i, sql.length());
        break;
      }
      out.append(sql, i, start);
      int end = lower.indexOf("*/", start + 2);
      if (end < 0) {
        break;
      }
      i = end + 2;
    }
    return out.toString();
  }

  private record Block(int start, int ddlHeaderEnd, String tableName) {}
}
