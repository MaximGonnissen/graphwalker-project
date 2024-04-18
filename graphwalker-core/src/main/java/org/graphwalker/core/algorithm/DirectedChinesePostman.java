package org.graphwalker.core.algorithm;

import org.graphwalker.core.machine.Context;
import org.graphwalker.core.model.Element;
import org.graphwalker.core.model.Model.RuntimeModel;
import org.graphwalker.core.model.Vertex.RuntimeVertex;

public class DirectedChinesePostman implements Algorithm {

  private static final Class<FloydWarshall> FWAlgorithm = FloydWarshall.class;

  private final Context context;
  private final RuntimeModel model;
  private final int[] balance;

  public DirectedChinesePostman(Context context) {
    this.context = context;
    this.model = context.getModel();
    this.balance = new int[vertexCount()];
    setUp();
  }

  public Element getNextElement(Element origin) {
    // TODO
    return null;
  }

  private void setUp() {
    FloydWarshall floydWarshall = getFloydWarshall();

    if (!isStronglyConnected(floydWarshall)) throw new AlgorithmException("The graph is not strongly connected.");

    findUnbalancedVertices();
    findInitialSolution();
    while (true) if (!improveSolution()) break;
  }

  private void findUnbalancedVertices() {
    for (int i = 0; i < vertexCount(); i++) {
      RuntimeVertex vertex = model.getVertices().get(i);
      if (getInDegree(vertex) != getOutDegree(vertex)) balance[i] = getOutDegree(vertex) - getInDegree(vertex);
    }
  }

  private void findInitialSolution() {
    int[] tempBalance = balance.clone();

    // TODO
  }

  private boolean improveSolution() {
    // TODO
    return false;
  }

  private int[] getNegativeVertices(int[] _balance) {
    int negVertexCount = 0;
    for (int i = 0; i < vertexCount(); i++)
      if (_balance[i] < 0) negVertexCount++;

    int[] negativeVertices = new int[negVertexCount];
    for (int i = 0, j = 0; i < vertexCount(); i++)
      if (_balance[i] < 0) negativeVertices[j++] = i;

    return negativeVertices;
  }

  private int[] getPositiveVertices(int[] _balance) {
    // TODO: Reduce code duplication with getNegativeVertices
    int posVertexCount = 0;
    for (int i = 0; i < vertexCount(); i++)
      if (_balance[i] > 0) posVertexCount++;

    int[] positiveVertices = new int[posVertexCount];
    for (int i = 0, j = 0; i < vertexCount(); i++)
      if (_balance[i] > 0) positiveVertices[j++] = i;

    return positiveVertices;
  }

  private int vertexCount() {
    return model.getVertices().size();
  }

  private int edgeCount() {
    return model.getEdges().size();
  }

  private FloydWarshall getFloydWarshall() {
    return context.getAlgorithm(FWAlgorithm);
  }

  private boolean isStronglyConnected(FloydWarshall floydWarshall) {
    for (int i = 0; i < vertexCount(); i++)
      for (int j = 0; j < vertexCount(); j++)
        if (floydWarshall.getShortestDistance(model.getVertices().get(i), model.getVertices().get(j)) == Integer.MAX_VALUE)
          return false;

    return true;
  }

  private int getInDegree(Element element) {
    return model.getInEdges((RuntimeVertex) element).size();
  }

  private int getOutDegree(Element element) {
    return model.getOutEdges((RuntimeVertex) element).size();
  }
}
