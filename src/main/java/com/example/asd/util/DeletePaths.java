package com.example.asd.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class DeletePaths {

  private DeletePaths() {}

  public static void deleteRecursively(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException ignored) {
          // best-effort cleanup
        }
      });
    }
  }
}
