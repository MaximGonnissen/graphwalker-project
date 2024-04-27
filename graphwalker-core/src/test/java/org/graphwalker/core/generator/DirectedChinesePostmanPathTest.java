package org.graphwalker.core.generator;

import org.graphwalker.core.condition.EdgeCoverage;
import org.graphwalker.core.condition.Never;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.machine.TestExecutionContext;
import org.graphwalker.core.model.*;
import org.graphwalker.core.statistics.SimpleProfiler;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Maxim Gonnissen
 */
public class DirectedChinesePostmanPathTest {

  private static final Vertex v0 = new Vertex().setName("0").setId("start");
  private static final Vertex v1 = new Vertex().setName("1");
  private static final Vertex v2 = new Vertex().setName("2");
  private static final Vertex v3 = new Vertex().setName("3");
  private static final Edge a = new Edge().setName("a").setSourceVertex(v0).setTargetVertex(v1);
  private static final Edge b = new Edge().setName("b").setSourceVertex(v0).setTargetVertex(v2);
  private static final Edge c = new Edge().setName("c").setSourceVertex(v1).setTargetVertex(v2);
  private static final Edge d = new Edge().setName("d").setSourceVertex(v1).setTargetVertex(v3);
  private static final Edge e = new Edge().setName("e").setSourceVertex(v2).setTargetVertex(v3);
  private static final Edge f = new Edge().setName("f").setSourceVertex(v3).setTargetVertex(v0);

  private static final Model model = new Model().addEdge(a).addEdge(b).addEdge(c).addEdge(d).addEdge(e).addEdge(f);

  @Test
  public void paperExample() {
    Context context = new TestExecutionContext(model, new DirectedChinesePostmanPath(new Never()));
    context.setProfiler(new SimpleProfiler());
    Deque<Builder<? extends Element>> expectedElements = new ArrayDeque<>(Arrays.asList(b, v2, e, v3, f, v0, a, v1, c, v2, e, v3, f, v0, a, v1, d, v3, f));
    context.setCurrentElement(context.getModel().getElementById("start"));
    execute(context, expectedElements);
    assertTrue(expectedElements.isEmpty());
  }

  private void execute(Context context, Deque<Builder<? extends Element>> expectedElements) {
    while (context.getPathGenerator().hasNextStep()) {
      context.getPathGenerator().getNextStep();
      context.getProfiler().start(context);
      context.getProfiler().stop(context);
      assertEquals(expectedElements.removeFirst().build(), context.getCurrentElement());
    }
  }

}
