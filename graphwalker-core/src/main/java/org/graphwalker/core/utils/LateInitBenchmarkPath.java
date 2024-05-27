package org.graphwalker.core.utils;

import org.graphwalker.core.model.Model;

import java.io.File;

/**
 * <h1>LateInitBenchmarkPath</h1>
 * <p>A construct that allows one to initialize a BenchmarkPath after construction. To be used when the Machine is not readily available when gathering BenchmarkPaths (e.g. during tests)</p>
 */
public class LateInitBenchmarkPath {
  private final File pathFile;
  private final File directory;

  public LateInitBenchmarkPath(File pathFile, File directory) {
    this.pathFile = pathFile;
    this.directory = directory;
  }

  public BenchmarkPath constructBenchmarkPath(Model model) {
    return BenchmarkPathParser.parseRunFile(model, pathFile, directory);
  }

  @Override
  public String toString() {
    return "LateInitBenchmarkPath{pathFile='" + pathFile + "', directory='" + directory + "'}";
  }
}
