package com.example.asd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "asd")
public final class AsdProperties {

  /**
   * Shallow clone depth (1 = fastest).
   */
  private int cloneDepth = 1;

  /** Safety cap when walking the working tree. */
  private int scanMaxFiles = 8000;

  public int getCloneDepth() {
    return cloneDepth;
  }

  public void setCloneDepth(int cloneDepth) {
    this.cloneDepth = Math.max(1, cloneDepth);
  }

  public int getScanMaxFiles() {
    return scanMaxFiles;
  }

  public void setScanMaxFiles(int scanMaxFiles) {
    this.scanMaxFiles = Math.max(100, scanMaxFiles);
  }
}
