package com.example.asd.mcp.tools;

import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MavenModuleGraphTool implements McpTool {

  @Override
  public String name() {
    return "maven_module_graph";
  }

  @Override
  public String description() {
    return "Parse root pom.xml for coordinates, packaging, modules, and direct dependencies.";
  }

  @Override
  public void execute(McpToolContext ctx) throws IOException, XmlPullParserException {
    Path root = ctx.repositoryRoot();
    Path pom = root.resolve("pom.xml");
    if (!Files.isRegularFile(pom)) {
      ctx.setMavenSummary(Map.of("present", false, "reason", "No root pom.xml"));
      ctx.trace(name(), "SKIP", "No root pom.xml");
      return;
    }

    ctx.trace(name(), "START", pom.toString());
    MavenXpp3Reader reader = new MavenXpp3Reader();
    Model model;
    try (Reader r = Files.newBufferedReader(pom)) {
      model = reader.read(r);
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("present", true);
    summary.put("groupId", nullToEmpty(model.getGroupId()));
    summary.put("artifactId", nullToEmpty(model.getArtifactId()));
    summary.put("version", nullToEmpty(model.getVersion()));
    summary.put("packaging", nullToEmpty(model.getPackaging()));
    summary.put("modules", model.getModules() == null ? List.of() : model.getModules());

    List<String> deps = model.getDependencies() == null
        ? List.of()
        : model.getDependencies().stream()
            .map(Dependency::getArtifactId)
            .filter(a -> a != null && !a.isBlank())
            .limit(60)
            .collect(Collectors.toList());
    summary.put("dependencyArtifactsSample", deps);

    ctx.setMavenSummary(summary);
    ctx.trace(name(), "OK", "artifact=" + model.getArtifactId());
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
