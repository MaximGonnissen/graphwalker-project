package org.graphwalker.cli.util;

import com.google.gson.JsonObject;
import org.graphwalker.core.generator.PathGenerator;
import org.graphwalker.core.statistics.Profiler;

import java.util.concurrent.TimeUnit;

public class BenchmarkResult {
  public String path;
  public long seed;
  public long generationTime;
  public long testSuiteSize;
  public int identifier;
  public String group;

  public BenchmarkResult(int identifier, String group, String path, Profiler profiler, PathGenerator<?> pathGenerator, long seed) {
    this.identifier = identifier;
    this.group = group;
    this.path = path;
    this.seed = seed;
    this.generationTime = profiler.getTotalExecutionTime(TimeUnit.MICROSECONDS);
    this.testSuiteSize = profiler.getTotalVisitCount();
  }

  public String toString() {
    return "BenchmarkResult{" +
      "identifier='" + identifier +
      ", group='" + group +
      ", seed=" + seed +
      ", generationTime=" + generationTime +
      ", testSuiteSize=" + testSuiteSize +
      '}';
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("path", path);
    json.addProperty("seed", seed);
    json.addProperty("generationTime", generationTime);
    json.addProperty("testSuiteSize", testSuiteSize);
    return json;
  }
}
