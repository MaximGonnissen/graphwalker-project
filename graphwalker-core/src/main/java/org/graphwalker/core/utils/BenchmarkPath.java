package org.graphwalker.core.utils;

import org.graphwalker.core.model.Edge;

import java.util.List;

/**
 * <h1>BenchmarkPath</h1>
 * <p>A simple record-like class to store a Benchmark path, along with relevant information on the benchmark and run</p>
 */
public class BenchmarkPath {
  public final String generatorString;
  public final Integer runId;
  public final List<Edge> path;

  public BenchmarkPath(String generatorString, Integer runId, List<Edge> path) {
    this.generatorString = generatorString;
    this.runId = runId;
    this.path = path;
  }

  @Override
  public String toString() {
    return "BenchmarkPath{generatorString='" + generatorString + "', runId='" + runId + "'}";
  }
}
