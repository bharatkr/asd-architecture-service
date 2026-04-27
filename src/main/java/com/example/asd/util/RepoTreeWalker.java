package com.example.asd.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded repository walks that skip heavy / generated trees ({@code node_modules}, {@code target},
 * etc.) so monorepos with Java + Angular still surface meaningful files under scan caps.
 */
public final class RepoTreeWalker {

  /** Directory names (single segment) whose subtrees are skipped entirely. */
  public static final Set<String> SKIP_SUBTREE_DIR_NAMES = Set.of(
      ".git", "node_modules", "dist", "build", "target", ".angular", "coverage",
      ".gradle", "out", "__pycache__", ".venv", "vendor", ".idea", ".vscode",
      "bower_components", "tmp", "temp", ".next", ".nuxt", "storybook-static",
      "e2e", "playwright-report", "cypress", ".turbo");

  @FunctionalInterface
  public interface RegularFileVisitor {
    /**
     * @param file repository-relative path using {@code /}
     * @return {@code false} to terminate the walk immediately
     */
    boolean visit(Path file, String relativeUnix) throws IOException;
  }

  private RepoTreeWalker() {}

  /**
   * Visits regular files depth-first, skipping noisy subtrees. Stops when {@code visitor} returns
   * {@code false} or {@code maxRegularFiles} regular files have been offered (including skipped
   * extensions — caller should return quickly for unwanted files).
   *
   * @return how many regular files were visited (offered to the visitor)
   */
  public static int walkRegularFiles(Path root, int maxRegularFiles, RegularFileVisitor visitor) throws IOException {
    int[] count = {0};
    Files.walkFileTree(root, EnumSet.noneOf(java.nio.file.FileVisitOption.class), Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (root.equals(dir)) {
              return FileVisitResult.CONTINUE;
            }
            String name = dir.getFileName().toString();
            if (SKIP_SUBTREE_DIR_NAMES.contains(name)) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!attrs.isRegularFile()) {
              return FileVisitResult.CONTINUE;
            }
            if (count[0] >= maxRegularFiles) {
              return FileVisitResult.TERMINATE;
            }
            count[0]++;
            String rel = root.relativize(file).toString().replace('\\', '/');
            if (!visitor.visit(file, rel)) {
              return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
          }
        });
    return count[0];
  }

  public static String describeSkipsShort() {
    return "Skips subtrees named: node_modules, target, dist, build, .git, .angular, coverage, …";
  }

  /** True if path segment suggests Angular workspace or app source. */
  public static boolean looksAngularSourcePath(String relativeUnix) {
    String r = relativeUnix.toLowerCase(Locale.ROOT);
    return r.contains("/src/app/")
        || r.contains("/projects/")
        || r.contains("angular.json")
        || r.endsWith("app.config.ts")
        || r.contains("/apps/") && r.endsWith(".ts");
  }
}
