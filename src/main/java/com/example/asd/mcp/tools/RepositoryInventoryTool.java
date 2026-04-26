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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Walks the repository and builds extension + polyglot language/category histograms.
 * Agnostic to stack: Java, Kotlin, TypeScript/Angular, SQL, Python, etc.
 */
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
    return "Walk the repository tree: extensions, polyglot language buckets, build/config markers.";
  }

  @Override
  public void execute(McpToolContext ctx) throws IOException {
    Path root = ctx.repositoryRoot();
    ctx.trace(name(), "START", root.toString());

    AtomicInteger total = new AtomicInteger();
    Map<String, Integer> extCounts = new HashMap<>();
    Map<String, Integer> polyglot = new HashMap<>();
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
        if (!Files.isRegularFile(p)) {
          continue;
        }
        String rel = root.relativize(p).toString().replace('\\', '/');
        String fn = p.getFileName().toString();
        String fnLower = fn.toLowerCase(Locale.ROOT);
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
        String ext = dot >= 0 ? fn.substring(dot).toLowerCase(Locale.ROOT) : "(no-ext)";
        extCounts.merge(ext, 1, Integer::sum);
        classifyPolyglot(rel, fnLower, polyglot);
      }
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("filesScanned", Math.min(total.get(), cap + 1));
    summary.put("capped", total.get() > cap);
    summary.put("extensionHistogramTop", topN(extCounts, 18));
    summary.put("polyglotCategoryCounts", topN(polyglot, 40));
    summary.put("pomXmlCount", pom);
    summary.put("gradleFileCount", gradle);
    summary.put("applicationConfigFileCount", appYml);

    ctx.setInventorySummary(summary);
    ctx.trace(name(), "OK", "files=" + Math.min(total.get(), cap));
  }

  private static void classifyPolyglot(String relativePath, String fnLower, Map<String, Integer> polyglot) {
    if (fnLower.endsWith(".java")) {
      bump(polyglot, "JAVA");
    } else if (fnLower.endsWith(".kt") || fnLower.endsWith(".kts")) {
      bump(polyglot, "KOTLIN");
    } else if (fnLower.endsWith(".sql")) {
      bump(polyglot, "SQL");
    } else if (fnLower.endsWith(".ts") || fnLower.endsWith(".tsx")) {
      bump(polyglot, "TYPESCRIPT");
    } else if (fnLower.endsWith(".js") || fnLower.endsWith(".mjs") || fnLower.endsWith(".cjs")) {
      bump(polyglot, "JAVASCRIPT");
    } else if (fnLower.endsWith(".py")) {
      bump(polyglot, "PYTHON");
    } else if (fnLower.endsWith(".go")) {
      bump(polyglot, "GO");
    } else if (fnLower.endsWith(".cs")) {
      bump(polyglot, "DOTNET");
    } else if (fnLower.endsWith(".rb")) {
      bump(polyglot, "RUBY");
    } else if (fnLower.endsWith(".html") || fnLower.endsWith(".htm")) {
      if (relativePath.contains("src/app") || relativePath.contains("angular")
          || fnLower.contains(".component.") || relativePath.endsWith("index.html")) {
        bump(polyglot, "ANGULAR_OR_UI_HTML");
      } else {
        bump(polyglot, "HTML");
      }
    } else if (fnLower.endsWith(".css") || fnLower.endsWith(".scss") || fnLower.endsWith(".sass")) {
      bump(polyglot, "STYLES");
    } else if (fnLower.endsWith(".json")) {
      bump(polyglot, "JSON");
    } else if (fnLower.endsWith(".yaml") || fnLower.endsWith(".yml")) {
      bump(polyglot, "YAML");
    } else if (fnLower.endsWith(".xml")) {
      bump(polyglot, "XML");
    } else if (fnLower.endsWith(".properties")) {
      bump(polyglot, "PROPERTIES");
    } else if (fnLower.endsWith(".md")) {
      bump(polyglot, "MARKDOWN");
    }
  }

  private static void bump(Map<String, Integer> polyglot, String key) {
    polyglot.merge(key, 1, Integer::sum);
  }

  private static Map<String, Integer> topN(Map<String, Integer> counts, int n) {
    return counts.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(n)
        .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
  }
}
