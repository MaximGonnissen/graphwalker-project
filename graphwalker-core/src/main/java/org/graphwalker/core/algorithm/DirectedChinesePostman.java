package org.graphwalker.core.algorithm;

import org.graphwalker.core.generator.NoPathFoundException;
import org.graphwalker.core.generator.SingletonRandomGenerator;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.model.Edge;
import org.graphwalker.core.model.Element;
import org.graphwalker.core.model.Model;
import org.graphwalker.core.model.Model.RuntimeModel;
import org.graphwalker.core.model.Vertex.RuntimeVertex;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim Gonnissen
 */
public class DirectedChinesePostman implements Algorithm {

  private static final Class<FloydWarshall> FWAlgorithm = FloydWarshall.class;

  private final Context context;
  private final DCPState state;
  private final List<Edge.RuntimeEdge> dCPPath;

  public DirectedChinesePostman(Context context) {
    this.context = context;
    this.state = new DCPState(context.getModel());
    dCPPath = setUp(state.runtimeModel.getVertices().indexOf((RuntimeVertex) context.getCurrentElement()));
  }

  public DirectedChinesePostman(Context context, int startVertex) {
    this.context = context;
    this.state = new DCPState(context.getModel());
    dCPPath = setUp(startVertex);
  }

  public Element getNextElement() {
    if (context.getCurrentElement() instanceof Edge.RuntimeEdge) {
      return ((Edge.RuntimeEdge) context.getCurrentElement()).getTargetVertex();
    }

    if (dCPPath.isEmpty()) {
      return randomStep();  // TODO: This is a temporary(?) fix to prevent the algorithm from getting stuck when using multiple models
    }

    return dCPPath.remove(0);
  }

  public boolean hasNextElement() {
    return !dCPPath.isEmpty();
  }

  public List<Edge.RuntimeEdge> setUp(int startVertex) {
    FloydWarshall floydWarshall = getFloydWarshall();

    if (!isStronglyConnected(floydWarshall)) throw new AlgorithmException("The graph is not strongly connected.");

    leastCostPaths();
    findUnbalancedVertices();
    findInitialSolution();
//    while (true) if (!improveSolution()) break; --> Never able to run due to cost always being 0

    return createPath(startVertex);
  }

  private void leastCostPaths() {
    for (int from = 0; from < vertexCount(state.runtimeModel); from++) {
      for (int to = 0; to < vertexCount(state.runtimeModel); to++) {
        if (state.defined[to][from]) {
          for (int intermediate = 0; intermediate < vertexCount(state.runtimeModel); intermediate++) {
            if (state.defined[from][intermediate] && !state.defined[to][intermediate]) {
              state.path[to][intermediate] = state.path[to][from];
              state.defined[to][intermediate] = true;
            }
          }
        }
      }
    }
  }

  private void findUnbalancedVertices() {
    for (int i = 0; i < vertexCount(state.runtimeModel); i++) {
      RuntimeVertex vertex = state.runtimeModel.getVertices().get(i);
      if (getInDegree(vertex) != getOutDegree(vertex)) state.balance[i] = getOutDegree(vertex) - getInDegree(vertex);
    }
  }

  private void findInitialSolution() {
    int[] tempBalance = state.balance.clone();

    state.negativeVertices = getNegativeVertices(tempBalance);
    state.positiveVertices = getPositiveVertices(tempBalance);

    for (int vertex : state.negativeVertices) {
      for (int otherVertex : state.positiveVertices) {
        int flow = Math.min(-tempBalance[vertex], tempBalance[otherVertex]);
        tempBalance[vertex] += flow;
        tempBalance[otherVertex] -= flow;
        state.flow[vertex][otherVertex] = flow;
      }
    }
  }

//  private boolean improveSolution() {
//    RuntimeModel residualGraph = createResidualGraph().build();
//    FloydWarshall floydWarshall = new FloydWarshall(residualGraph);
//
//    for (int from = 0; from < vertexCount(residualGraph); from++) {
//      int k = 0, v;
//      int u = from;
//      boolean kUnset = true;
//      while (true) {
//        // TODO? Cost is never < 0, so will never be triggered?
//        u = v;
//        if (u == from) break;
//      }
//    }
//
//    return false;
//  }

  private List<Edge.RuntimeEdge> createPath(int startVertex) {
    List<Edge.RuntimeEdge> edgePath = new ArrayList<>();

    int[][] flow = state.flow.clone();
    int[][] arcs = state.arcs.clone();

    int to = startVertex;

    while (true) {
      int from = to;
      to = findPath(from, flow);
      if (to != -1) {
        flow[from][to]--;
        for (int p; from != to; from = p) {
          p = state.path[from][to];
          addFromToEdgePriorityNew(edgePath, from, p);
//          System.out.println("From \"" + state.runtimeModel.getVertices().get(from).getName() + "\" to \"" + state.runtimeModel.getVertices().get(p).getName() + "\" using edge: " + edgePath.get(edgePath.size() - 1).getName());
        }
      } else {
        int bridge = state.path[from][startVertex];
        if (arcs[from][bridge] == 0) {
          break;
        }
        to = bridge;
        for (int bridgeTo = 0; bridgeTo < vertexCount(state.runtimeModel); bridgeTo++) {
          if (bridgeTo != bridge && arcs[from][bridgeTo] > 0) {
            to = bridgeTo;
            break;
          }
        }
        arcs[from][to]--;
        addFromToEdgePriorityNew(edgePath, from, to);
//        System.out.println("From \"" + state.runtimeModel.getVertices().get(from).getName() + "\" to \"" + state.runtimeModel.getVertices().get(to).getName() + "\" using edge: " + edgePath.get(edgePath.size() - 1).getName());
      }
    }

    return edgePath;
  }

  private List<Edge.RuntimeEdge> getEdgesFromTo(int from, int to) {
    List<Edge.RuntimeEdge> edges = new ArrayList<>();

    RuntimeVertex fromVertex = state.runtimeModel.getVertices().get(from);
    RuntimeVertex toVertex = state.runtimeModel.getVertices().get(to);

    for (Edge.RuntimeEdge edge : state.runtimeModel.getOutEdges(fromVertex)) {
      if (edge.getTargetVertex().equals(toVertex)) {
        edges.add(edge);
      }
    }
    return edges;
  }

  /**
   * Add an edge from the list of possible edges to the list of edges. Priority is given to edges not already in the list.
   *
   * @param edges The list of edges
   * @param from  The index of the source vertex
   * @param to    The index of the target vertex
   */
  private void addFromToEdgePriorityNew(List<Edge.RuntimeEdge> edges, int from, int to) {
    List<Edge.RuntimeEdge> possibleEdges = getEdgesFromTo(from, to);
    for (Edge.RuntimeEdge edge : possibleEdges) {
      if (!edges.contains(edge)) {
        edges.add(edge);
        return;
      }
    }
    edges.add(possibleEdges.get(0));
  }

  private Model createResidualGraph() {
    Model residualGraph = new Model(state.runtimeModel);

    for (int source : state.negativeVertices) {
      for (int target : state.positiveVertices) {
        residualGraph.addEdge(new Edge().setName("e" + source + target).setSourceVertex(state.model.getVertices().get(source)).setTargetVertex(state.model.getVertices().get(target)));
        if (state.flow[source][target] != 0) {
          residualGraph.addEdge(new Edge().setName("e" + target + source).setSourceVertex(state.model.getVertices().get(target)).setTargetVertex(state.model.getVertices().get(source)));
        }
      }
    }

    return residualGraph;
  }

  private int[] getNegativeVertices(int[] _balance) {
    int negVertexCount = 0;
    for (int i = 0; i < vertexCount(state.runtimeModel); i++)
      if (_balance[i] < 0) negVertexCount++;

    int[] negativeVertices = new int[negVertexCount];
    for (int i = 0, j = 0; i < vertexCount(state.runtimeModel); i++)
      if (_balance[i] < 0) negativeVertices[j++] = i;

    return negativeVertices;
  }

  private int[] getPositiveVertices(int[] _balance) {
    // TODO: Reduce code duplication with getNegativeVertices
    int posVertexCount = 0;
    for (int i = 0; i < vertexCount(state.runtimeModel); i++)
      if (_balance[i] > 0) posVertexCount++;

    int[] positiveVertices = new int[posVertexCount];
    for (int i = 0, j = 0; i < vertexCount(state.runtimeModel); i++)
      if (_balance[i] > 0) positiveVertices[j++] = i;

    return positiveVertices;
  }

  private int vertexCount(RuntimeModel model) {
    return model.getVertices().size();
  }

  private int edgeCount(RuntimeModel model) {
    return model.getEdges().size();
  }

  private FloydWarshall getFloydWarshall() {
    return context.getAlgorithm(FWAlgorithm);
  }

  private Element randomStep() {
    Element currentElement = context.getCurrentElement();
    List<Element> elements = context.filter(context.getModel().getElements(currentElement));
    if (elements.isEmpty()) {
      throw new NoPathFoundException(context.getCurrentElement());
    }
    return elements.get(SingletonRandomGenerator.nextInt(elements.size()));
  }

  private boolean isStronglyConnected(FloydWarshall floydWarshall) {
    for (int i = 0; i < vertexCount(state.runtimeModel); i++)
      for (int j = 0; j < vertexCount(state.runtimeModel); j++)
        if (floydWarshall.getShortestDistance(state.runtimeModel.getVertices().get(i), state.runtimeModel.getVertices().get(j)) == Integer.MAX_VALUE)
          return false;

    return true;
  }

  private int getInDegree(Element element) {
    return state.runtimeModel.getInEdges((RuntimeVertex) element).size();
  }

  private int getOutDegree(Element element) {
    return state.runtimeModel.getOutEdges((RuntimeVertex) element).size();
  }

  private int findPath(int from, int[][] flow) {
    for (int to = 0; to < vertexCount(state.runtimeModel); to++)
      if (flow[from][to] > 0) {
        return to;
      }
    return -1;
  }

  public void printFlow() {
    for (int i = 0; i < vertexCount(state.runtimeModel); i++) {
      for (int j = 0; j < vertexCount(state.runtimeModel); j++) {
        if (state.flow[i][j] > 0)
          System.out.println("Flow from " + state.runtimeModel.getVertices().get(i).getName() + " to " + state.runtimeModel.getVertices().get(j).getName() + " is " + state.flow[i][j]);
      }
    }
  }

  public class DCPState {
    public final RuntimeModel runtimeModel;
    public final Model model;
    public final int[] balance;
    public final int[][] flow;
    public final boolean[][] defined;
    public final int[][] path;
    public final int[][] arcs;
    public int[] negativeVertices;
    public int[] positiveVertices;

    public DCPState(RuntimeModel runtimeModel) {
      this.runtimeModel = runtimeModel;
      this.model = new Model(runtimeModel);

      int vertexCount = vertexCount(runtimeModel);

      this.balance = new int[vertexCount];
      this.flow = new int[vertexCount][vertexCount];
      this.arcs = new int[vertexCount][vertexCount];
      this.defined = new boolean[vertexCount][vertexCount];
      this.path = new int[vertexCount][vertexCount];

      this.init();
    }

    private void init() {
      for (Edge.RuntimeEdge edge : runtimeModel.getEdges()) {
        int from = runtimeModel.getVertices().indexOf(edge.getSourceVertex());
        int to = runtimeModel.getVertices().indexOf(edge.getTargetVertex());
        defined[from][to] = true;
        path[from][to] = to;
        arcs[from][to] += 1;
      }
    }
  }
}
