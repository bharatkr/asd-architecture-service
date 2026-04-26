package com.example.asd.mcp.tools;

import com.example.asd.config.AsdProperties;
import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans {@code .sql} scripts (Flyway/Liquibase/hand-rolled) and estimates statement mix.
 * Language-agnostic alongside Java/Angular inventory.
 */
@Component
public class SqlScriptAnalysisTool implements McpTool {

  private static final Pattern DDL = Pattern.compile(
      "(?is)^\\s*(create|alter|drop)\\s+(table|view|index|sequence|database|schema|procedure|function|trigger)\\b");
  private static final Pattern DML = Pattern.compile(
      "(?is)^\\s*(insert|update|delete|merge|truncate)\\s+");
  private static final Pattern QUERY = Pattern.compile("(?is)^\\s*(select|with)\\s+");

  private final AsdProperties asdProperties;

  public SqlScriptAnalysisTool(AsdProperties asdProperties) {
    this.asdProperties = asdProperties;
  }

  @Override
  public String name() {
    return "sql_script_analysis";
  }

  @Override
  public String description() {
    return "Locate .sql files and estimate DDL/DML/query statement counts from line-based heuristics.";
  }

  @Override
  public void execute(McpToolContext ctx) throws IOException {
    Path root = ctx.repositoryRoot();
    ctx.trace(name(), "START", root.toString());

    List<Path> sqlFiles = new ArrayList<>();
    int cap = Math.min(500, asdProperties.getScanMaxFiles());
    try (Stream<Path> stream = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
      stream.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
          .limit(cap)
          .forEach(sqlFiles::add);
    }

    int ddl = 0;
    int dml = 0;
    int qry = 0;
    int other = 0;
    List<String> samples = new ArrayList<>();

    int fileCap = 120;
    int bytesCap = 512_000;
    int n = 0;
    for (Path sql : sqlFiles) {
      if (n++ >= fileCap) {
        break;
      }
      samples.add(root.relativize(sql).toString().replace('\\', '/'));
      String content = readLimited(sql, bytesCap);
      for (String line : content.split("\r?\n")) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("--") || t.startsWith("/*")) {
          continue;
        }
        if (DDL.matcher(t).find()) {
          ddl++;
        } else if (DML.matcher(t).find()) {
          dml++;
        } else if (QUERY.matcher(t).find()) {
          qry++;
        } else if (t.endsWith(";") && t.length() > 5) {
          other++;
        }
      }
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("sqlFilesDiscovered", sqlFiles.size());
    summary.put("sqlFilesAnalyzed", Math.min(sqlFiles.size(), fileCap));
    summary.put("estimatedDdlStatements", ddl);
    summary.put("estimatedDmlStatements", dml);
    summary.put("estimatedQueryStatements", qry);
    summary.put("otherSqlishLines", other);
    summary.put("sampleRelativePaths", samples.stream().limit(40).toList());
    summary.put("note", "Heuristic line scan — not a full SQL parser. Use for inventory and review triage.");

    ctx.setSqlScriptSummary(summary);
    ctx.trace(name(), "OK", "sqlFiles=" + sqlFiles.size());
  }

  private static String readLimited(Path file, int maxBytes) throws IOException {
    byte[] raw = Files.readAllBytes(file);
    if (raw.length <= maxBytes) {
      return new String(raw, StandardCharsets.UTF_8);
    }
    return new String(raw, 0, maxBytes, StandardCharsets.UTF_8);
  }
}
