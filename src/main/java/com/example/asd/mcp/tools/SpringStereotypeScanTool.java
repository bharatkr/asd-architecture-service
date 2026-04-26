package com.example.asd.mcp.tools;

import com.example.asd.config.AsdProperties;
import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Component
public class SpringStereotypeScanTool implements McpTool {

  private final AsdProperties asdProperties;

  public SpringStereotypeScanTool(AsdProperties asdProperties) {
    this.asdProperties = asdProperties;
  }

  @Override
  public String name() {
    return "spring_stereotype_scan";
  }

  @Override
  public String description() {
    return "Heuristic scan of .java sources for common Spring stereotype annotations.";
  }

  @Override
  public void execute(McpToolContext ctx) throws IOException {
    Path root = ctx.repositoryRoot();
    ctx.trace(name(), "START", root.toString());

    Map<Stereotype, AtomicInteger> counts = new EnumMap<>(Stereotype.class);
    for (Stereotype s : Stereotype.values()) {
      counts.put(s, new AtomicInteger());
    }

    int cap = asdProperties.getScanMaxFiles();
    AtomicInteger javaFilesVisited = new AtomicInteger();

    try (Stream<Path> stream = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
      Iterator<Path> it = stream.iterator();
      while (it.hasNext()) {
        Path p = it.next();
        if (!Files.isRegularFile(p) || !p.toString().endsWith(".java")) {
          continue;
        }
        if (javaFilesVisited.incrementAndGet() > cap) {
          break;
        }
        scanFile(p, counts);
      }
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("javaFilesScanned", Math.min(javaFilesVisited.get(), cap));
    summary.put("capped", javaFilesVisited.get() > cap);
    Map<String, Integer> flat = new HashMap<>();
    for (Stereotype s : Stereotype.values()) {
      flat.put(s.name(), counts.get(s).get());
    }
    summary.put("stereotypes", flat);

    ctx.setSpringSummary(summary);
    ctx.trace(name(), "OK", "javaFiles=" + Math.min(javaFilesVisited.get(), cap));
  }

  private static void scanFile(Path file, Map<Stereotype, AtomicInteger> counts) {
    try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      int lines = 0;
      while ((line = br.readLine()) != null && lines++ < 160) {
        String t = line.trim();
        if (t.startsWith("//") || t.startsWith("*")) {
          continue;
        }
        for (Stereotype s : Stereotype.values()) {
          if (t.contains(s.token())) {
            counts.get(s).incrementAndGet();
          }
        }
      }
    } catch (IOException ignored) {
      // skip unreadable file
    }
  }

  private enum Stereotype {
    REST_CONTROLLER("@RestController"),
    CONTROLLER("@Controller"),
    SERVICE("@Service"),
    REPOSITORY("@Repository"),
    FEIGN_CLIENT("@FeignClient"),
    CONFIGURATION("@Configuration");

    private final String token;

    Stereotype(String token) {
      this.token = token;
    }

    String token() {
      return token;
    }
  }
}
