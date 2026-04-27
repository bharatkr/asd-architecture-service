package com.example.asd.document;

import com.example.asd.mcp.McpToolContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns tool outputs into readable narrative, metric tables, and executive-summary bullets
 * for a professional ASD layout (tables in the body; raw JSON relegated to appendix).
 */
public final class AsdNarrative {

  private static final String NOT_ASSESSED = "Not assessed (automated)";

  private AsdNarrative() {}

  public static String reportTitle() {
    return "Architecture Specification Document";
  }

  public static String reportSubtitle() {
    return "Automated repository discovery & architecture baseline";
  }

  public static String documentVersion() {
    return "1.0";
  }

  public static String documentStatus() {
    return "Draft — machine-assisted";
  }

  public static List<String> executiveSummaryBullets(McpToolContext ctx) {
    List<String> bullets = new ArrayList<>();
    Map<String, Object> inv = ctx.inventorySummary();
    Map<String, Object> mvn = ctx.mavenSummary();
    Map<String, Object> spr = ctx.springSummary();
    Map<String, Object> sql = ctx.sqlScriptSummary();

    int files = intVal(inv.get("filesScanned"));
    boolean invCapped = boolVal(inv.get("capped"));
    bullets.add("Repository scan covered "
        + (files > 0 ? String.format(Locale.ROOT, "%,d", files) : "0")
        + " file system nodes"
        + (invCapped ? " (scan cap reached — counts are lower bounds)." : "."));

    String stack = inferPrimaryStack(inv);
    if (!stack.isBlank()) {
      bullets.add("Dominant technology signals: " + stack + ".");
    }

    if (boolVal(mvn.get("present"))) {
      String aid = str(mvn.get("artifactId"));
      String gid = str(mvn.get("groupId"));
      bullets.add("Build: Maven root POM present"
          + (!gid.isEmpty() || !aid.isEmpty() ? " (" + gid + (gid.isEmpty() ? "" : ":") + aid + ")" : "")
          + ".");
    } else {
      bullets.add("Build: no root pom.xml in clone — treat as non-Maven or multi-root layout.");
    }

    int javaScanned = intVal(spr.get("javaFilesScanned"));
    int rc = stereotypeCount(spr, "REST_CONTROLLER");
    int svc = stereotypeCount(spr, "SERVICE");
    if (javaScanned > 0) {
      bullets.add(String.format(Locale.ROOT,
          "Java surface: %,d source files scanned heuristically; Spring signals include ~%d @RestController and ~%d @Service hits (text search, not AST).",
          javaScanned, rc, svc));
    }

    int sqlFiles = intVal(sql.get("sqlFilesDiscovered"));
    if (sqlFiles > 0) {
      int tables = intVal(sql.get("erDiagramTableCount"));
      bullets.add(String.format(Locale.ROOT,
          "SQL: %d .sql file(s) discovered; inferred relational footprint ~%d table name(s) from DDL heuristics.",
          sqlFiles, tables));
    } else {
      bullets.add("SQL: no .sql scripts discovered in scan scope (migrations may live elsewhere or use another format).");
    }

    boolean hasApi = ctx.openApiJson() != null && !ctx.openApiJson().isBlank();
    bullets.add(hasApi
        ? "HTTP contract: OpenAPI document ingested — see §6 for metadata, REST operation table, and excerpt."
        : "HTTP contract: OpenAPI not ingested — supply swaggerUrl on the request to list REST paths.");

    Map<String, Object> api = ctx.apiSurfaceSummary();
    if (api != null && !api.isEmpty()) {
      String profile = humanizeProfile(str(api.get("primaryBackendProfile")));
      boolean rest = boolVal(api.get("restStyleLikely"));
      boolean gql = boolVal(api.get("graphqlStyleLikely"));
      int ops = intVal(api.get("openApiRestOperationTotal"));
      boolean fs = boolVal(api.get("fullStackJavaAngularLikely"));
      bullets.add(String.format(Locale.ROOT,
          "API stack (heuristic): %s%s; REST-like surface: %s; GraphQL-like surface: %s; REST operations from OpenAPI: %d.",
          profile,
          fs ? " (same repo may host Angular UI + Java API)" : "",
          rest ? "likely" : "not indicated",
          gql ? "likely" : "not indicated",
          ops));
    }

    bullets.add("Figures in §8 illustrate a reference layered flow; ER diagram appears in §7 when DDL allows inference.");
    return bullets;
  }

  private static String humanizeProfile(String p) {
    if (p == null || p.isBlank()) {
      return "unknown";
    }
    return switch (p) {
      case "SPRING_BOOT" -> "Spring Boot–style JVM backend";
      case "NODE_JS" -> "Node.js (package.json)";
      case "POLYGLOT_SPRING_AND_NODE" -> "Polyglot (Spring + Node artifacts)";
      case "FULL_STACK_JAVA_ANGULAR" -> "Full-stack (Java/Spring API + Angular workspace in repo)";
      case "JAVA_OTHER" -> "Java / JVM (non–Spring Boot signal in this scan)";
      default -> p.replace('_', ' ').toLowerCase(Locale.ROOT);
    };
  }

  public static String inferPrimaryStack(Map<String, Object> inv) {
    Map<String, Integer> poly = asStringIntMap(inv.get("polyglotCategoryCounts"));
    if (poly.isEmpty()) {
      return "";
    }
    return poly.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
        .limit(5)
        .map(e -> e.getKey().replace('_', ' ') + " (" + e.getValue() + ")")
        .collect(Collectors.joining(", "));
  }

  public static List<String[]> polyglotRows(Map<String, Object> inv, int limit) {
    return sortedCountRows(inv.get("polyglotCategoryCounts"), limit);
  }

  public static List<String[]> extensionRows(Map<String, Object> inv, int limit) {
    return sortedCountRows(inv.get("extensionHistogramTop"), limit);
  }

  public static List<String[]> mavenRows(Map<String, Object> mvn) {
    List<String[]> rows = new ArrayList<>();
    if (!boolVal(mvn.get("present"))) {
      rows.add(new String[] {"Status", str(mvn.get("reason"))});
      return rows;
    }
    rows.add(new String[] {"Group ID", str(mvn.get("groupId"))});
    rows.add(new String[] {"Artifact ID", str(mvn.get("artifactId"))});
    rows.add(new String[] {"Version", str(mvn.get("version"))});
    rows.add(new String[] {"Packaging", str(mvn.get("packaging"))});
    @SuppressWarnings("unchecked")
    List<String> modules = (List<String>) mvn.getOrDefault("modules", List.of());
    rows.add(new String[] {"Declared modules", String.valueOf(modules.size())});
    if (!modules.isEmpty()) {
      int show = Math.min(12, modules.size());
      rows.add(new String[] {"Module sample", String.join(", ", modules.subList(0, show)) + (modules.size() > show ? " …" : "")});
    }
    @SuppressWarnings("unchecked")
    List<String> deps = (List<String>) mvn.getOrDefault("dependencyArtifactsSample", List.of());
    rows.add(new String[] {"Direct dependencies (sample size)", String.valueOf(deps.size())});
    if (!deps.isEmpty()) {
      rows.add(new String[] {"Dependency artifacts (sample)", String.join(", ", deps.subList(0, Math.min(20, deps.size())))});
    }
    return rows;
  }

  public static List<String[]> springStereotypeRows(Map<String, Object> spr) {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {
        "Java files scanned",
        String.valueOf(intVal(spr.get("javaFilesScanned"))) + (boolVal(spr.get("capped")) ? " (scan cap reached)" : ""),
        "Enumeration of .java paths visited by the scanner."
    });
    @SuppressWarnings("unchecked")
    Map<String, Object> st = (Map<String, Object>) spr.getOrDefault("stereotypes", Map.of());
    Map<String, String> labels = Map.of(
        "REST_CONTROLLER", "REST controllers (@RestController)",
        "CONTROLLER", "MVC controllers (@Controller)",
        "SERVICE", "Services (@Service)",
        "REPOSITORY", "Repositories (@Repository)",
        "FEIGN_CLIENT", "Feign HTTP clients (@FeignClient)",
        "CONFIGURATION", "Configuration (@Configuration)"
    );
    List<String> order = List.of(
        "REST_CONTROLLER", "CONTROLLER", "SERVICE", "REPOSITORY", "FEIGN_CLIENT", "CONFIGURATION");
    for (String key : order) {
      if (!st.containsKey(key)) {
        continue;
      }
      int c = intVal(st.get(key));
      String label = labels.getOrDefault(key, key);
      rows.add(new String[] {label, String.valueOf(c), interpretation(key, c)});
    }
    return rows;
  }

  private static String interpretation(String key, int c) {
    if (c <= 0) {
      return "—";
    }
    return switch (key) {
      case "REST_CONTROLLER" -> "Public HTTP entrypoints likely; map to OpenAPI paths.";
      case "SERVICE" -> "Domain / orchestration layer.";
      case "REPOSITORY" -> "Persistence boundary; pair with SQL / ER analysis.";
      case "FEIGN_CLIENT" -> "Outbound synchronous HTTP dependencies.";
      case "CONFIGURATION" -> "Beans & integration wiring.";
      default -> "Heuristic hit count (line scan).";
    };
  }

  public static List<String[]> sqlMetricsRows(Map<String, Object> sql) {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {".sql files discovered", String.valueOf(intVal(sql.get("sqlFilesDiscovered")))});
    rows.add(new String[] {".sql files analyzed", String.valueOf(intVal(sql.get("sqlFilesAnalyzed")))});
    rows.add(new String[] {"Estimated DDL statements", String.valueOf(intVal(sql.get("estimatedDdlStatements")))});
    rows.add(new String[] {"Estimated DML statements", String.valueOf(intVal(sql.get("estimatedDmlStatements")))});
    rows.add(new String[] {"Estimated query statements", String.valueOf(intVal(sql.get("estimatedQueryStatements")))});
    rows.add(new String[] {"Other SQL-like lines", String.valueOf(intVal(sql.get("otherSqlishLines")))});
    rows.add(new String[] {"ER diagram inferred", boolVal(sql.get("erDiagramInferred")) ? "Yes" : "No"});
    rows.add(new String[] {"Inferred table count", String.valueOf(intVal(sql.get("erDiagramTableCount")))});
    rows.add(new String[] {"Method", "Heuristic line / DDL scan — not a certified schema parser."});
    return rows;
  }

  public static List<String[]> apiSurfaceProfileRows(Map<String, Object> api) {
    List<String[]> rows = new ArrayList<>();
    if (api == null || api.isEmpty()) {
      rows.add(new String[] {"Status", "api_stack_scan produced no summary"});
      return rows;
    }
    rows.add(new String[] {"Primary backend profile (heuristic)", humanizeProfile(str(api.get("primaryBackendProfile")))});
    rows.add(new String[] {"Spring Boot–style signals", boolVal(api.get("springBootDetected")) ? "Yes" : "No"});
    rows.add(new String[] {"Node.js (package.json) detected", boolVal(api.get("nodeJsDetected")) ? "Yes" : "No"});
    rows.add(new String[] {"REST / HTTP API (documented or inferred)", boolVal(api.get("restStyleLikely")) ? "Likely" : "Not indicated"});
    rows.add(new String[] {"GraphQL API (dependencies or schema files)", boolVal(api.get("graphqlStyleLikely")) ? "Likely" : "Not indicated"});
    rows.add(new String[] {"GraphQL / schema-like files found", String.valueOf(intVal(api.get("graphqlSchemaLikeFiles")))});
    rows.add(new String[] {"OpenAPI ingested this run", boolVal(api.get("openApiIngested")) ? "Yes" : "No"});
    rows.add(new String[] {"REST operations under paths (OpenAPI)", String.valueOf(intVal(api.get("openApiRestOperationTotal")))});
    rows.add(new String[] {"Full-stack Java + Angular (same repo)", boolVal(api.get("fullStackJavaAngularLikely")) ? "Likely" : "No"});
    rows.add(new String[] {"Angular workspace signals", boolVal(api.get("angularWorkspaceLikely")) ? "Likely" : "No"});
    rows.add(new String[] {"Angular HttpClient-style file hits", String.valueOf(intVal(api.get("angularHttpClientLikeFileHits")))});
    rows.add(new String[] {"package.json files merged for npm scan", String.valueOf(intVal(api.get("packageJsonFilesMerged")))});
    return rows;
  }

  @SuppressWarnings("unchecked")
  public static List<String> stringList(Object o) {
    if (!(o instanceof List<?> list)) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (Object x : list) {
      if (x != null) {
        out.add(String.valueOf(x));
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  public static List<String[]> apiRestOperationRows(McpToolContext ctx) {
    Object sample = ctx.apiSurfaceSummary().get("openApiRestOperationsSample");
    List<String[]> rows = new ArrayList<>();
    if (!(sample instanceof List<?> list)) {
      return rows;
    }
    for (Object o : list) {
      if (o instanceof Map<?, ?> m) {
        rows.add(new String[] {
            str(m.get("method")),
            str(m.get("path")),
            str(m.get("summary"))
        });
      }
    }
    return rows;
  }

  public static List<String[]> openApiBriefRows(Map<String, Object> brief) {
    List<String[]> rows = new ArrayList<>();
    if (brief == null || brief.isEmpty()) {
      return rows;
    }
    if (brief.containsKey("parseError")) {
      rows.add(new String[] {"Parse", str(brief.get("parseError"))});
      return rows;
    }
    rows.add(new String[] {"API title", str(brief.get("apiTitle"))});
    rows.add(new String[] {"API version", str(brief.get("apiVersion"))});
    rows.add(new String[] {"OpenAPI version field (root)", str(brief.get("openapiSpecVersion"))});
    rows.add(new String[] {"Paths declared", String.valueOf(intVal(brief.get("pathsCount")))});
    rows.add(new String[] {"Tags (info)", String.valueOf(intVal(brief.get("tagsCount")))});
    rows.add(new String[] {"Component schemas", String.valueOf(intVal(brief.get("schemasCount")))});
    return rows;
  }

  public static List<String[]> traceRows(McpToolContext ctx, int detailMax) {
    List<String[]> rows = new ArrayList<>();
    for (Map<String, String> row : ctx.traceSnapshot()) {
      String d = row.getOrDefault("detail", "");
      if (d.length() > detailMax) {
        d = d.substring(0, detailMax - 1) + "…";
      }
      rows.add(new String[] {
          row.getOrDefault("tool", ""),
          row.getOrDefault("status", ""),
          d
      });
    }
    return rows;
  }

  public static Map<String, Object> inventoryKeyFacts(Map<String, Object> inv) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("Files scanned (regular files)", inv.get("filesScanned"));
    m.put("Scan capped", inv.get("capped"));
    m.put("Traversal", inv.get("traversalStrategy"));
    m.put("pom.xml files", inv.get("pomXmlCount"));
    m.put("package.json files (sample list length)", inv.get("packageJsonCount"));
    m.put("angular.json files", inv.get("angularJsonFiles"));
    m.put("nx.json files", inv.get("nxJsonFiles"));
    m.put("Gradle files", inv.get("gradleFileCount"));
    m.put("Spring-style config files (application*)", inv.get("applicationConfigFileCount"));
    return m;
  }

  /** Monorepo / polyglot markers for ASD §3.4. */
  public static List<String[]> workspaceMarkerRows(Map<String, Object> inv) {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"package.json locations (sample)", joinSample(inv.get("packageJsonSamplePaths"), 12)});
    rows.add(new String[] {"pom.xml locations (sample)", joinSample(inv.get("pomXmlSamplePaths"), 12)});
    rows.add(new String[] {"angular.json count", String.valueOf(intVal(inv.get("angularJsonFiles")))});
    rows.add(new String[] {"nx.json count", String.valueOf(intVal(inv.get("nxJsonFiles")))});
    int angTs = angularTsPolyglot(inv);
    rows.add(new String[] {"Angular-style .ts files (heuristic bucket)", String.valueOf(angTs)});
    return rows;
  }

  @SuppressWarnings("unchecked")
  private static int angularTsPolyglot(Map<String, Object> inv) {
    Object poly = inv.get("polyglotCategoryCounts");
    if (!(poly instanceof Map<?, ?> m)) {
      return 0;
    }
    Object v = m.get("ANGULAR_TYPESCRIPT");
    return intVal(v);
  }

  @SuppressWarnings("unchecked")
  private static String joinSample(Object listObj, int max) {
    if (!(listObj instanceof List<?> list) || list.isEmpty()) {
      return "—";
    }
    return list.stream().limit(max).map(String::valueOf).collect(Collectors.joining("; "));
  }

  /** RAG-style assessments suitable for an enterprise architecture baseline. */
  public static List<String[]> qualityAttributeRows(McpToolContext ctx) {
    Map<String, Object> inv = ctx.inventorySummary();
    int appYml = intVal(inv.get("applicationConfigFileCount"));
    int repos = stereotypeCount(ctx.springSummary(), "REPOSITORY");
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {
        "Scalability & elasticity",
        NOT_ASSESSED,
        "Confirm horizontal scaling, statelessness, and cache topology with platform owners."
    });
    rows.add(new String[] {
        "Database connectivity & pooling",
        appYml > 0 ? "Partial signal" : NOT_ASSESSED,
        appYml > 0
            ? appYml + " Spring-style configuration file(s) detected — review datasource / pool settings in deployment."
            : "No application*.yml/.properties count in scan scope."
    });
    rows.add(new String[] {
        "Persistence performance (N+1, ORM)",
        repos > 0 ? "Indicative (heuristic)" : NOT_ASSESSED,
        repos > 0
            ? "@Repository hits suggest JPA/MyBatis-style access — validate with traces and load tests."
            : "No repository stereotype hits in heuristic scan."
    });
    rows.add(new String[] {
        "Observability & operability",
        NOT_ASSESSED,
        "Map to logging, metrics, tracing, and SLOs outside this document."
    });
    return rows;
  }

  public static List<String[]> securityPostureRows(McpToolContext ctx) {
    int java = intVal(ctx.springSummary().get("javaFilesScanned"));
    int ctrl = stereotypeCount(ctx.springSummary(), "REST_CONTROLLER");
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {
        "Secret & credential hygiene",
        NOT_ASSESSED,
        "Run secret scanning (e.g. gitleaks), verify CI policies, and rotate any leaked material."
    });
    rows.add(new String[] {
        "HTTP authorization model",
        ctrl > 0 ? "Indicative (heuristic)" : NOT_ASSESSED,
        ctrl > 0
            ? "~" + ctrl + " controller stereotype hit(s) — map security filters, method security, and OpenAPI securitySchemes."
            : "Few or no REST controller hits; still validate non-Spring HTTP surfaces if any."
    });
    rows.add(new String[] {
        "Dependency & supply-chain risk",
        boolVal(ctx.mavenSummary().get("present")) ? "Partial signal" : NOT_ASSESSED,
        "Cross-check declared dependencies with CVE feeds (OWASP Dependency-Check, OSV, Dependabot)."
    });
    rows.add(new String[] {
        "Attack surface (HTTP)",
        ctx.openApiJson() != null && !ctx.openApiJson().isBlank() ? "Partial signal" : NOT_ASSESSED,
        java > 0
            ? (ctx.openApiJson() != null && !ctx.openApiJson().isBlank()
                ? "OpenAPI available — reconcile §6 REST operation list with controllers and ingress."
                : "Ingest OpenAPI (swaggerUrl) to align exposure with implementation.")
            : "Limited Java footprint in scan."
    });
    return rows;
  }

  private static List<String[]> sortedCountRows(Object histogram, int limit) {
    Map<String, Integer> map = asStringIntMap(histogram);
    return map.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
        .limit(limit)
        .map(e -> new String[] {e.getKey(), String.valueOf(e.getValue())})
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Integer> asStringIntMap(Object o) {
    if (!(o instanceof Map<?, ?> raw)) {
      return Map.of();
    }
    Map<String, Integer> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> e : raw.entrySet()) {
      if (e.getKey() == null) {
        continue;
      }
      out.put(String.valueOf(e.getKey()), intVal(e.getValue()));
    }
    return out;
  }

  private static int intVal(Object o) {
    if (o instanceof Number n) {
      return n.intValue();
    }
    if (o instanceof String s) {
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }

  private static boolean boolVal(Object o) {
    return Boolean.TRUE.equals(o) || "true".equalsIgnoreCase(str(o));
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }

  private static int stereotypeCount(Map<String, Object> spr, String key) {
    @SuppressWarnings("unchecked")
    Map<String, Object> st = (Map<String, Object>) spr.getOrDefault("stereotypes", Map.of());
    return intVal(st.get(key));
  }
}
