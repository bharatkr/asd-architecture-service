package com.example.asd.mcp.tools;

import com.example.asd.document.json.OpenApiRestCatalog;
import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import com.example.asd.util.RepoTreeWalker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Spring Boot vs Node, Angular workspaces, REST vs GraphQL, samples OpenAPI operations.
 * Uses inventory {@code packageJson} paths and noise-skipping traversal for consistent monorepo coverage.
 */
@Component
public class ApiStackScanTool implements McpTool {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_PACKAGE_JSON = 400_000;
  private static final int MAX_POM_SNIPPET = 250_000;
  private static final int MAX_JAVA_FILES_GRAPHQL_SCAN = 140;
  private static final int MAX_GRAPHQL_FILES = 100;

  private static final Pattern GRAPHQL_JAVA = Pattern.compile(
      "(?is)(@Query\\s*\\(|@Mutation\\s*\\(|graphql\\.schema|GraphQlSource|DataFetcher|DgsQuery|DgsMutation|DgsEntityFetcher)");

  private static final Pattern ENV_BASE_URL = Pattern.compile(
      "(?is)(?:apiUrl|baseUrl|BASE_URL|graphqlUrl|serverUrl|backendUrl|apiEndpoint)\\s*[:=]\\s*['\"]([^'\"]{2,512})['\"]");

  private static final Set<String> NODE_GRAPHQL_DEPS = Set.of(
      "graphql", "@apollo/server", "apollo-server", "apollo-server-express", "apollo-server-fastify",
      "@nestjs/graphql", "graphql-yoga", "mercurius", "@graphql-tools", "type-graphql", "@apollo/client");

  private static final Set<String> NODE_REST_DEPS = Set.of(
      "express", "fastify", "koa", "@nestjs/core", "restify", "hapi", "@hapi/hapi", "connect", "polka",
      "@tinyhttp/app", "itty-router", "elysia");

  private static final Set<String> ANGULAR_CORE_DEPS = Set.of(
      "@angular/core", "@angular/common", "@angular/router", "@angular/cli");

  @Override
  public String name() {
    return "api_stack_scan";
  }

  @Override
  public String description() {
    return "Polyglot API signals: Spring/Node/Angular, REST vs GraphQL, OpenAPI REST table, Angular HttpClient/env hints.";
  }

  @Override
  public void execute(McpToolContext ctx) throws IOException {
    Path root = ctx.repositoryRoot();
    ctx.trace(name(), "START", root.toString());

    Map<String, Object> inv = ctx.inventorySummary();
    List<String> packageJsonPaths = packageJsonPaths(ctx, root);
    Set<String> npmDeps = new HashSet<>();
    for (String rel : packageJsonPaths) {
      mergeNpmDependencies(root.resolve(rel), npmDeps);
    }

    boolean pomPresent = bool(ctx.mavenSummary().get("present"));
    String pomRel = str(ctx.mavenSummary().get("primaryPomRelativePath"));
    if (pomRel.isBlank()) {
      pomRel = "pom.xml";
    }
    String pomSnippet = "";
    if (pomPresent) {
      Path pom = root.resolve(pomRel);
      if (Files.isRegularFile(pom)) {
        byte[] raw = Files.readAllBytes(pom);
        pomSnippet = new String(raw, 0, Math.min(raw.length, MAX_POM_SNIPPET), StandardCharsets.UTF_8);
      }
    }

    boolean pomMentionsSpringBoot = pomSnippet.toLowerCase(Locale.ROOT).contains("spring-boot");
    @SuppressWarnings("unchecked")
    List<String> depSample = (List<String>) ctx.mavenSummary().getOrDefault("dependencyArtifactsSample", List.of());
    boolean mavenSpringBootDep = depSample.stream()
        .anyMatch(d -> d != null && d.toLowerCase(Locale.ROOT).contains("spring-boot"));

    int javaFiles = intVal(ctx.springSummary().get("javaFilesScanned"));
    int restControllers = stereotype(ctx.springSummary(), "REST_CONTROLLER");

    int graphqlFiles = countGraphqlSchemaFiles(root);
    boolean javaGraphqlHints = scanJavaForGraphqlHints(root, javaFiles);

    AngularHints angularHints = scanAngularTypeScript(root);

    boolean springBootLikely = pomPresent && (pomMentionsSpringBoot || mavenSpringBootDep || restControllers > 0);
    boolean nodeLikely = !packageJsonPaths.isEmpty();

    int angularJsonFiles = intVal(inv.get("angularJsonFiles"));
    boolean angularFromNpm = ANGULAR_CORE_DEPS.stream().anyMatch(npmDeps::contains);
    int angularTsFiles = angularTsPolyglotCount(inv);
    boolean angularWorkspaceLikely = angularJsonFiles > 0 || angularFromNpm
        || angularHints.httpClientLikeHits() > 0 || angularTsFiles > 0;
    boolean fullStackJavaAngular = springBootLikely && angularWorkspaceLikely;

    boolean graphqlFromNpm = npmDeps.stream().anyMatch(NODE_GRAPHQL_DEPS::contains);
    boolean graphqlFromMaven = depSample.stream()
        .anyMatch(d -> {
          if (d == null) {
            return false;
          }
          String x = d.toLowerCase(Locale.ROOT);
          return x.contains("graphql") || x.contains("spring-graphql") || x.contains("dgs");
        });
    boolean graphqlLikely = graphqlFiles > 0 || graphqlFromNpm || graphqlFromMaven || javaGraphqlHints;

    boolean restFromNpm = npmDeps.stream().anyMatch(NODE_REST_DEPS::contains);
    boolean openApiIngested = ctx.openApiJson() != null && !ctx.openApiJson().isBlank();
    int oaOps = openApiIngested ? OpenApiRestCatalog.countOperations(ctx.openApiJson()) : 0;
    boolean restFromAngular = angularHints.httpClientLikeHits() > 0;
    boolean restLikely = openApiIngested && oaOps > 0 || restControllers > 0 || restFromNpm || restFromAngular;

    List<String> restEvidence = new ArrayList<>();
    if (oaOps > 0) {
      restEvidence.add("OpenAPI lists " + oaOps + " HTTP operation(s) under paths.");
    }
    if (restControllers > 0) {
      restEvidence.add("Spring: ~" + restControllers + " @RestController heuristic hit(s).");
    }
    if (restFromNpm) {
      restEvidence.add("Node: REST-style framework dependency detected (" + firstMatchingDep(npmDeps, NODE_REST_DEPS) + ").");
    }
    if (restFromAngular) {
      restEvidence.add("Angular/TypeScript: HttpClient-style usage in ~" + angularHints.httpClientLikeHits() + " file(s) (heuristic).");
    }
    if (!angularHints.environmentUrlHints().isEmpty()) {
      restEvidence.add("Angular environment(s) expose " + angularHints.environmentUrlHints().size() + " candidate base URL hint(s) — see apiSurfaceSummary.angularEnvironmentUrlHints.");
    }
    if (restEvidence.isEmpty() && springBootLikely) {
      restEvidence.add("Spring-style build without explicit REST hits in this scan — confirm with OpenAPI or code review.");
    }

    List<String> gqlEvidence = new ArrayList<>();
    if (graphqlFiles > 0) {
      gqlEvidence.add(graphqlFiles + " GraphQL schema / .graphql file(s) found.");
    }
    if (graphqlFromNpm) {
      gqlEvidence.add("Node dependency: " + firstMatchingDep(npmDeps, NODE_GRAPHQL_DEPS) + ".");
    }
    if (graphqlFromMaven) {
      gqlEvidence.add("Maven sample lists GraphQL-related artifact(s).");
    }
    if (javaGraphqlHints) {
      gqlEvidence.add("Java sources contain GraphQL / DGS / DataFetcher style patterns (heuristic).");
    }

    String primary;
    if (fullStackJavaAngular) {
      primary = "FULL_STACK_JAVA_ANGULAR";
    } else if (springBootLikely && nodeLikely) {
      primary = "POLYGLOT_SPRING_AND_NODE";
    } else if (springBootLikely) {
      primary = "SPRING_BOOT";
    } else if (nodeLikely) {
      primary = "NODE_JS";
    } else if (javaFiles > 0) {
      primary = "JAVA_OTHER";
    } else {
      primary = "UNKNOWN";
    }

    List<Map<String, String>> opSample = new ArrayList<>();
    int opTotal = 0;
    if (openApiIngested) {
      opTotal = OpenApiRestCatalog.countOperations(ctx.openApiJson());
      for (String[] row : OpenApiRestCatalog.operationsTable(ctx.openApiJson(), 80)) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("method", row[0]);
        m.put("path", row[1]);
        m.put("summary", row.length > 2 ? row[2] : "");
        opSample.add(m);
      }
    }

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("primaryBackendProfile", primary);
    summary.put("fullStackJavaAngularLikely", fullStackJavaAngular);
    summary.put("angularWorkspaceLikely", angularWorkspaceLikely);
    summary.put("angularJsonFilesFromInventory", angularJsonFiles);
    summary.put("angularHttpClientLikeFileHits", angularHints.httpClientLikeHits());
    summary.put("angularEnvironmentUrlHints", angularHints.environmentUrlHints().stream().limit(22).toList());
    summary.put("springBootDetected", springBootLikely);
    summary.put("nodeJsDetected", nodeLikely);
    summary.put("pomReferencesSpringBoot", pomMentionsSpringBoot);
    summary.put("mavenSpringBootDependencyHit", mavenSpringBootDep);
    summary.put("packageJsonRelativePaths", packageJsonPaths.stream().limit(20).toList());
    summary.put("packageJsonFilesMerged", packageJsonPaths.size());
    summary.put("npmDependencyCountSampled", npmDeps.size());
    summary.put("restStyleLikely", restLikely);
    summary.put("graphqlStyleLikely", graphqlLikely);
    summary.put("restEvidence", restEvidence.stream().limit(12).toList());
    summary.put("graphqlEvidence", gqlEvidence.stream().limit(12).toList());
    summary.put("graphqlSchemaLikeFiles", graphqlFiles);
    summary.put("openApiIngested", openApiIngested);
    summary.put("openApiRestOperationTotal", opTotal);
    summary.put("openApiRestOperationsSample", opSample);
    summary.put("traversalStrategy", RepoTreeWalker.describeSkipsShort());
    summary.put("note", "Heuristics for polyglot repos — confirm with owners; Angular may call a separate Java API service.");

    ctx.setApiSurfaceSummary(summary);
    ctx.trace(name(), "OK", "profile=" + primary + " angularWs=" + angularWorkspaceLikely + " ops=" + opTotal);
  }

  @SuppressWarnings("unchecked")
  private static List<String> packageJsonPaths(McpToolContext ctx, Path root) throws IOException {
    List<String> fromInv = (List<String>) ctx.inventorySummary().getOrDefault("packageJsonSamplePaths", List.of());
    if (!fromInv.isEmpty()) {
      return new ArrayList<>(fromInv);
    }
    List<String> out = new ArrayList<>();
    RepoTreeWalker.walkRegularFiles(root, 85_000, (file, rel) -> {
      if ("package.json".equalsIgnoreCase(file.getFileName().toString())) {
        out.add(rel);
      }
      return out.size() < 65;
    });
    return out;
  }

  private static String firstMatchingDep(Set<String> npmDeps, Set<String> candidates) {
    for (String c : candidates) {
      if (npmDeps.contains(c)) {
        return c;
      }
    }
    return "matched";
  }

  private static void mergeNpmDependencies(Path file, Set<String> out) {
    if (!Files.isRegularFile(file)) {
      return;
    }
    try {
      byte[] raw = Files.readAllBytes(file);
      if (raw.length > MAX_PACKAGE_JSON) {
        raw = java.util.Arrays.copyOf(raw, MAX_PACKAGE_JSON);
      }
      JsonNode root = JSON.readTree(new String(raw, StandardCharsets.UTF_8));
      mergeDepBlock(root.path("dependencies"), out);
      mergeDepBlock(root.path("devDependencies"), out);
      mergeDepBlock(root.path("peerDependencies"), out);
    } catch (Exception ignored) {
      // skip unreadable
    }
  }

  private static void mergeDepBlock(JsonNode block, Set<String> out) {
    if (!block.isObject()) {
      return;
    }
    block.fieldNames().forEachRemaining(out::add);
  }

  private static int countGraphqlSchemaFiles(Path root) throws IOException {
    int[] n = {0};
    RepoTreeWalker.walkRegularFiles(root, 75_000, (file, rel) -> {
      String fn = file.getFileName().toString().toLowerCase(Locale.ROOT);
      if (fn.endsWith(".graphql") || fn.endsWith(".graphqls")
          || "schema.graphql".equals(fn) || "schema.graphqls".equals(fn)) {
        n[0]++;
      }
      return n[0] < MAX_GRAPHQL_FILES + 40;
    });
    return n[0];
  }

  private static boolean scanJavaForGraphqlHints(Path root, int javaCapHint) throws IOException {
    int cap = Math.min(MAX_JAVA_FILES_GRAPHQL_SCAN, Math.max(50, javaCapHint / 2));
    int[] javaSeen = {0};
    boolean[] hit = {false};
    RepoTreeWalker.walkRegularFiles(root, 70_000, (file, rel) -> {
      if (!rel.endsWith(".java")) {
        return !hit[0];
      }
      if (javaSeen[0]++ >= cap) {
        return false;
      }
      try {
        String chunk = Files.readString(file, StandardCharsets.UTF_8);
        if (chunk.length() > 48_000) {
          chunk = chunk.substring(0, 48_000);
        }
        if (GRAPHQL_JAVA.matcher(chunk).find()) {
          hit[0] = true;
          return false;
        }
      } catch (IOException ignored) {
        // skip
      }
      return true;
    });
    return hit[0];
  }

  private static AngularHints scanAngularTypeScript(Path root) throws IOException {
    int[] httpHits = {0};
    List<String> urls = new ArrayList<>();
    int[] tsSeen = {0};
    RepoTreeWalker.walkRegularFiles(root, 65_000, (file, rel) -> {
      String rl = rel.toLowerCase(Locale.ROOT);
      if (!rl.endsWith(".ts") && !rl.endsWith(".tsx")) {
        return true;
      }
      boolean envLike = rl.contains("environment") || rl.endsWith("app.config.ts");
      boolean appLike = RepoTreeWalker.looksAngularSourcePath(rel) || rl.contains("/src/app/");
      if (!envLike && !appLike) {
        return true;
      }
      if (tsSeen[0]++ > 420) {
        return false;
      }
      try {
        String chunk = Files.readString(file, StandardCharsets.UTF_8);
        if (chunk.length() > 36_000) {
          chunk = chunk.substring(0, 36_000);
        }
        if (chunk.contains("HttpClient") || chunk.contains("provideHttpClient")
            || chunk.contains("inject(HttpClient)") || chunk.contains("HttpClientModule")) {
          httpHits[0]++;
        }
        Matcher m = ENV_BASE_URL.matcher(chunk);
        while (m.find() && urls.size() < 24) {
          String u = m.group(1).trim();
          if (u.length() > 512) {
            u = u.substring(0, 511) + "…";
          }
          urls.add(rel + " → " + u);
        }
      } catch (IOException ignored) {
        // skip
      }
      return true;
    });
    return new AngularHints(httpHits[0], urls);
  }

  private record AngularHints(int httpClientLikeHits, List<String> environmentUrlHints) {}

  private static int stereotype(Map<String, Object> spr, String key) {
    @SuppressWarnings("unchecked")
    Map<String, Object> st = (Map<String, Object>) spr.getOrDefault("stereotypes", Map.of());
    Object v = st.get(key);
    return v instanceof Number n ? n.intValue() : 0;
  }

  private static int intVal(Object o) {
    if (o instanceof Number n) {
      return n.intValue();
    }
    return 0;
  }

  private static boolean bool(Object o) {
    return Boolean.TRUE.equals(o);
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }

  @SuppressWarnings("unchecked")
  private static int angularTsPolyglotCount(Map<String, Object> inv) {
    Object poly = inv.get("polyglotCategoryCounts");
    if (!(poly instanceof Map<?, ?> m)) {
      return 0;
    }
    Object v = m.get("ANGULAR_TYPESCRIPT");
    return v instanceof Number n ? n.intValue() : 0;
  }
}
