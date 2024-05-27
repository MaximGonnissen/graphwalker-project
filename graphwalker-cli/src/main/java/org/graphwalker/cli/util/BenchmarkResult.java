package org.graphwalker.cli.util;

import com.google.gson.JsonObject;
import org.graphwalker.core.generator.PathGenerator;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.model.Element;
import org.graphwalker.core.statistics.Profiler;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BenchmarkResult {
  public String path;
  public long seed;
  public long generationTime;
  public long testSuiteSize;
  public int identifier;
  public String group;
  public Map<String, Long> VertexVisits = new java.util.HashMap<>();
  public Map<String, Long> EdgeVisits = new java.util.HashMap<>();

  public BenchmarkResult(int identifier, String group, String path, Profiler profiler, PathGenerator<?> pathGenerator, long seed) {
    this.identifier = identifier;
    this.group = group;
    this.path = path;
    this.seed = seed;
    this.generationTime = profiler.getTotalExecutionTime(TimeUnit.MICROSECONDS);
    this.testSuiteSize = profiler.getTotalVisitCount();
    calculateVisits(profiler, pathGenerator.getContext());
  }

  private long getElementVisitCount(Profiler profiler, Context context, Element element) {
    return profiler.getVisitCount(context, element);
  }

  private void calculateVisits(Profiler profiler, Context context) {
    calculateVertexVisits(profiler, context);
    calculateEdgeVisits(profiler, context);
  }

  private void calculateVertexVisits(Profiler profiler, Context context) {
    for (Element element : context.getModel().getVertices()) {
      VertexVisits.put(element.getName() + "[" + element.getId() + "]", getElementVisitCount(profiler, context, element));
    }
  }

  private void calculateEdgeVisits(Profiler profiler, Context context) {
    for (Element element : context.getModel().getEdges()) {
      EdgeVisits.put(element.getName() + "[" + element.getId() + "]", getElementVisitCount(profiler, context, element));
    }
  }

  public String toString() {
    return "BenchmarkResult{" + "identifier='" + identifier + ", group='" + group + ", seed=" + seed + ", generationTime=" + generationTime + ", testSuiteSize=" + testSuiteSize + '}';
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
