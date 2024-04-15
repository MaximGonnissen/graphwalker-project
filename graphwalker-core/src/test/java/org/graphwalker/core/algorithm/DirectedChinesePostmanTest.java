package org.graphwalker.core.algorithm;

import org.graphwalker.core.machine.TestExecutionContext;
import org.graphwalker.core.model.Edge;
import org.graphwalker.core.model.Model;
import org.graphwalker.core.model.Vertex;
import org.junit.Test;

public class DirectedChinesePostmanTest {


  private static final Vertex v0 = new Vertex().setName("v0");
  private static final Vertex v1 = new Vertex().setName("v1");
  private static final Vertex v2 = new Vertex().setName("v2");
  private static final Vertex v3 = new Vertex().setName("v3");

  private static final Edge e1 = new Edge().setName("e1").setSourceVertex(v0).setTargetVertex(v1);
  private static final Edge e2 = new Edge().setName("e2").setSourceVertex(v1).setTargetVertex(v2);
  private static final Edge e3 = new Edge().setName("e3").setSourceVertex(v2).setTargetVertex(v3);
  private static final Edge e4 = new Edge().setName("e4").setSourceVertex(v3).setTargetVertex(v0);
  private static final Edge e5 = new Edge().setName("e5").setSourceVertex(v0).setTargetVertex(v2);
  private static final Edge e6 = new Edge().setName("e6").setSourceVertex(v1).setTargetVertex(v3);

  private static final Model model = new Model().addEdge(e1).addEdge(e2).addEdge(e3).addEdge(e4).addEdge(e5).addEdge(e6);

  @Test
  public void testDirectedChinesePostman() {
    DirectedChinesePostman directedChinesePostman = new DirectedChinesePostman(new TestExecutionContext().setModel(model.build()));
    // TODO
  }
}
