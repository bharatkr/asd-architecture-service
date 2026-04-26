package com.example.asd.mcp.tools;

import com.example.asd.config.AsdProperties;
import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.McpToolContext;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class GitCloneTool implements McpTool {

  private final AsdProperties asdProperties;

  public GitCloneTool(AsdProperties asdProperties) {
    this.asdProperties = asdProperties;
  }

  @Override
  public String name() {
    return "git_clone";
  }

  @Override
  public String description() {
    return "Shallow-clone the remote Git repository into an isolated workspace directory.";
  }

  @Override
  public void execute(McpToolContext ctx) throws Exception {
    String raw = ctx.request().githubUrl().trim();
    URI uri = URI.create(raw);
    if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("Only http(s) Git URLs are allowed.");
    }

    Path workspace = Files.createTempDirectory("asd-ws-");
    Path repoDir = workspace.resolve("repository");
    Files.createDirectories(repoDir);

    ctx.setWorkspaceDir(workspace);
    ctx.trace(name(), "START", "Cloning into " + repoDir);

    var clone = Git.cloneRepository()
        .setURI(raw)
        .setDirectory(repoDir.toFile())
        .setCloneSubmodules(false);

    int depth = asdProperties.getCloneDepth();
    if (depth > 0) {
      clone.setDepth(depth);
    }
    String branch = ctx.request().branch();
    if (branch != null && !branch.isBlank()) {
      clone.setBranch(branch.trim());
    }

    try (Git git = clone.call()) {
      Repository repository = git.getRepository();
      ObjectId head = repository.resolve(Constants.HEAD);
      if (head != null) {
        ctx.setCommitSha(head.getName());
      }
      ctx.setRepositoryRoot(repoDir.toAbsolutePath().normalize());
      ctx.trace(name(), "OK", "HEAD=" + (ctx.commitSha() == null ? "unknown" : ctx.commitSha()));
    } catch (Exception e) {
      ctx.trace(name(), "FAIL", e.getMessage());
      throw e;
    }
  }
}
