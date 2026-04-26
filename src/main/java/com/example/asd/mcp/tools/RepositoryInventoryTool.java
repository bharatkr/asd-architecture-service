package com.example.asd.mcp.tools;

import com.example.asd.config.AsdProperties;
import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Component
public class RepositoryInventoryTool implements McpTool {

  private final AsdProperties asdProperties;

  public RepositoryInventoryTool(AsdProperties asdProperties) {
    this.asdProperties = asdProperties;
  }

  @Override
  public String name() {
    return "repo_inventory";
  }

  @Override
  public String description() {
    return "Walk the repository tree and summarize file types and key config markers.";
  }

  @Override
  public void execute(McpToolContext ctx) throws IOException {
    Path root = ctx.repositoryRoot();
    ctx.trace(name(), "START", root.toString());

    AtomicInteger total = new AtomicInteger();
    Map<String, Integer> extCounts = new HashMap<>();
    int pom = 0;
    int gradle = 0;
    int appYml = 0;

    int cap = asdProperties.getScanMaxFiles();
    try (Stream<Path> stream = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
      Iterator<Path> it = stream.iterator();
      while (it.hasNext()) {
        Path p = it.next();
        if (total.incrementAndGet() > cap) {
          break;
        }
        if (Files.isRegularFile(p)) {
          String fn = p.getFileName().toString();
          if ("pom.xml".equalsIgnoreCase(fn)) {
            pom++;
          }
          if (fn.endsWith(".gradle") || fn.endsWith(".gradle.kts")) {
            gradle++;
          }
          if (fn.startsWith("application") && (fn.endsWith(".yml") || fn.endsWith(".yaml") || fn.endsWith(".properties"))) {
            appYml++;
          }
          int dot = fn.lastIndexOf('.');
          String ext = dot >= 0 ? fn.substring(dot).toLowerCase() : "(no-ext)";
          extCounts.merge(ext, 1, Integer::sum);
        }
      }
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("filesScanned", Math.min(total.get(), cap + 1));
    summary.put("capped", total.get() > cap);
    summary.put("extensionHistogramTop", topN(extCounts, 15));
    summary.put("pomXmlCount", pom);
    summary.put("gradleFileCount", gradle);
    summary.put("applicationConfigFileCount", appYml);

    ctx.setInventorySummary(summary);
    ctx.trace(name(), "OK", "files=" + Math.min(total.get(), cap));
  }

  private static Map<String, Integer> topN(Map<String, Integer> counts, int n) {
    return counts.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(n)
        .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
  }
}
