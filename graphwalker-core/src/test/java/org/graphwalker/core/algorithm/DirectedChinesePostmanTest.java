package org.graphwalker.core.algorithm;

import org.graphwalker.core.machine.Context;
import org.graphwalker.core.machine.TestExecutionContext;
import org.graphwalker.core.model.Edge;
import org.graphwalker.core.model.Element;
import org.graphwalker.core.model.Model;
import org.graphwalker.core.model.Vertex;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DirectedChinesePostmanTest {

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
  public void testDirectedChinesePostman() {
    Model.RuntimeModel runtimeModel = model.build();
    Context context = new TestExecutionContext().setModel(runtimeModel);
    DirectedChinesePostman directedChinesePostman = new DirectedChinesePostman(context, v0.build());

    List<String> expected = Arrays.asList("b", "2", "e", "3", "f", "0", "a", "1", "c", "2", "e", "3", "f", "0", "a", "1", "d", "3", "f");
    List<String> actual = new ArrayList<>();
    while (directedChinesePostman.hasNextElement()) {
      Element nextElement = directedChinesePostman.getNextElement();
      context.setCurrentElement(nextElement);
      actual.add(nextElement.getName());
    }

    assertEquals(expected, actual);
  }
}
