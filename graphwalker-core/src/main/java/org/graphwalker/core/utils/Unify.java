package org.graphwalker.core.utils;

import org.graphwalker.core.condition.PredefinedPathStopCondition;
import org.graphwalker.core.generator.PredefinedPath;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.model.Edge;
import org.graphwalker.core.model.Model;
import org.graphwalker.core.model.Vertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class Unify {

  public static Model CreateUnifiedModel(Model.RuntimeModel... modelsToUnify) {
    return CreateUnifiedModel(1.0f, modelsToUnify);
  }

  // Multiple models as input
  public static Model CreateUnifiedModel(float rounding, Model.RuntimeModel... modelsToUnify) {
    // Prep model objects
    Model unifiedModel = new Model();

    // First stage model unification, copy all non-shared vertices & edges
    {
      for (Model.RuntimeModel modelToUnify : modelsToUnify) {
        String prefix = modelToUnify.getName() + "_";
        Model model = new Model(modelToUnify);

        // Find centre of local graph so we can centre the local graph in the unified model for later spreading
        double local_offset_x = 0;
        double local_offset_y = 0;
        for (Vertex vertex : model.getVertices()) {
          if (!isEmpty(vertex.getSharedState()))
            continue;  // We don't take into account shared states for the local offset, since these aren't copied
          if (vertex.getProperties().containsKey("x") && vertex.getProperties().containsKey("y")) {
            local_offset_x += (double) vertex.getProperty("x");
            local_offset_y += (double) vertex.getProperty("y");
          }
        }

        local_offset_x /= model.getVertices().size();
        local_offset_y /= model.getVertices().size();

        // Add non-shared vertices
        for (Vertex vertex : model.getVertices()) {
          if (isEmpty(vertex.getSharedState()))  // We handle shared states separately
          {
            Vertex newVertex = copyVertex(vertex, prefix, rounding);
            newVertex.setProperty("x", (double) newVertex.getProperty("x") - local_offset_x);
            newVertex.setProperty("y", (double) newVertex.getProperty("y") - local_offset_y);
            unifiedModel.addVertex(newVertex);
          }
        }

        // Add non-shared edges
        for (Edge edge : model.getEdges()) {
          if (isEmpty(edge.getSourceVertex().getSharedState()) && isEmpty(edge.getTargetVertex().getSharedState()))  // We handle shared states separately
            unifiedModel.addEdge(copyEdge(edge, prefix, unifiedModel));
        }
      }
    }

    // Second stage model unification, spread out subgraphs
    {
      double max_distance = 0;
      for (Vertex vertex : unifiedModel.getVertices())
        if (vertex.getProperties().containsKey("x") && vertex.getProperties().containsKey("y")) {
          double distance = Math.sqrt(Math.pow((double) vertex.getProperty("x"), 2) + Math.pow((double) vertex.getProperty("y"), 2));
          if (distance > max_distance) max_distance = distance;
        }

      double coordinate_offset = max_distance * 1.1;  // Give a bit of padding

      boolean coordinate_offset_vertical = false;

      boolean first_context = true;

      for (Model.RuntimeModel modelToUnify : modelsToUnify) {
        if (first_context) {  // Skip so the first context is central
          first_context = false;
          continue;
        }

        Model model = new Model(modelToUnify);

        // Update coordinates with the global offset
        for (Vertex vertex : model.getVertices()) {
          if (!isEmpty(vertex.getSharedState())) continue;
          String unifiedVertexName = getPrefixedString(vertex.getName(), modelToUnify.getName() + "_");
          Vertex unifiedVertex = unifiedModel.getVertices().stream().filter(v -> v.getName().equals(unifiedVertexName)).findFirst().orElse(null);
          if (unifiedVertex == null) {
            continue;
          }

          if (unifiedVertex.getProperties().containsKey("x") && unifiedVertex.getProperties().containsKey("y")) {
            if (coordinate_offset_vertical) {
              unifiedVertex.setProperty("y", (double) unifiedVertex.getProperty("y") + coordinate_offset);
            } else {
              unifiedVertex.setProperty("x", (double) unifiedVertex.getProperty("x") + coordinate_offset);
            }
          }
        }

        // Alternate coordinate offset
        if (coordinate_offset_vertical) {
          if (coordinate_offset > 0) {
            coordinate_offset = -coordinate_offset;
          } else {
            coordinate_offset *= -2;
          }
          coordinate_offset_vertical = false;
        } else {
          coordinate_offset_vertical = true;
        }
      }
    }

    // Third stage model unification, create and connect (previously-)shared states & edges
    {
      Map<String, Vertex> sharedVertices = new HashMap<>();

      for (Model.RuntimeModel modelToUnify : modelsToUnify) {
        Model model = new Model(modelToUnify);

        // We create a single vertex for each shared state
        for (Vertex vertex : model.getVertices()) {
          if (isEmpty(vertex.getSharedState())) continue;
          if (sharedVertices.containsKey(vertex.getSharedState())) continue;

          Vertex newVertex = new Vertex();
          newVertex.setName(vertex.getSharedState());
          newVertex.setId(vertex.getId());
          sharedVertices.put(vertex.getSharedState(), newVertex);
          unifiedModel.addVertex(newVertex);
        }

        for (Edge edge : model.getEdges()) {
          // If the edge doesn't have either a shared source or target, we skip it, since we've already copied it
          if (isEmpty(edge.getSourceVertex().getSharedState()) && isEmpty(edge.getTargetVertex().getSharedState()))
            continue;
          Vertex sourceVertex = edge.getSourceVertex();
          Vertex targetVertex = edge.getTargetVertex();
          if (!isEmpty(sourceVertex.getSharedState())) {
            sourceVertex = sharedVertices.get(sourceVertex.getSharedState());
          } else {
            String unifiedVertexName = getPrefixedString(sourceVertex.getName(), modelToUnify.getName() + "_");
            sourceVertex = unifiedModel.getVertices().stream().filter(v -> v.getName().equals(unifiedVertexName)).findFirst().orElse(null);
            if (sourceVertex == null) {
              throw new RuntimeException("Could not find source vertex for edge: " + edge.getId());
            }
          }
          if (!isEmpty(targetVertex.getSharedState())) {
            targetVertex = sharedVertices.get(targetVertex.getSharedState());
          } else {
            String unifiedVertexName = getPrefixedString(targetVertex.getName(), modelToUnify.getName() + "_");
            targetVertex = unifiedModel.getVertices().stream().filter(v -> v.getName().equals(unifiedVertexName)).findFirst().orElse(null);
            if (targetVertex == null) {
              throw new RuntimeException("Could not find target vertex for edge: " + edge.getId());
            }
          }

          Edge newEdge = copyEdge(edge, modelToUnify.getName() + "_");
          newEdge.setSourceVertex(sourceVertex);
          newEdge.setTargetVertex(targetVertex);
          unifiedModel.addEdge(newEdge);
        }
      }
    }

    return unifiedModel;
  }

  public static Context CreateUnifiedContext(String unifiedModelName, Context unifiedContext, Context... contextsToUnify) {
    return CreateUnifiedContext(1.0f, unifiedModelName, unifiedContext, null, contextsToUnify);
  }

  public static Context CreateUnifiedContext(float rounding, Context unifiedContext, Context... contextsToUnify) {
    return CreateUnifiedContext(rounding, "UnifiedModel", unifiedContext, null, contextsToUnify);
  }

  public static Context CreateUnifiedContext(Context unifiedContext, Context... contextsToUnify) {
    return CreateUnifiedContext(1.0f, "UnifiedModel", unifiedContext, null, contextsToUnify);
  }

  public static Context CreateUnifiedContext(List<Edge> predefinedPath, Context unifiedContext, Context... contextsToUnify) {
    return CreateUnifiedContext(1.0f, "UnifiedModel", unifiedContext, predefinedPath, contextsToUnify);
  }

  public static Context CreateUnifiedContext(float rounding, String unifiedModelName, Context unifiedContext, Context... contextsToUnify) {
    return CreateUnifiedContext(rounding, unifiedModelName, unifiedContext, null, contextsToUnify);
  }

  /**
   * Reconstructs the predefined path for the unified model to ensure they're the same edge references. Assumes the predefined path was created for the unified model.
   */
  private static List<Edge> reconstructPredefinedPath(List<Edge> originalPredefinedPath, Model unifiedModel) {
    List<Edge> reconstructedPredefinedPath = new ArrayList<>();

    for (Edge edge : originalPredefinedPath) {
      for (Edge unifiedEdge : unifiedModel.getEdges()) {
        if (unifiedEdge.getId().equals(edge.getId())) {
          reconstructedPredefinedPath.add(unifiedEdge);
          break;
        }
      }
    }

    return reconstructedPredefinedPath;
  }

  public static Context CreateUnifiedContext(float rounding, String unifiedModelName, Context unifiedContext, List<Edge> predefinedPath, Context... contextsToUnify) {
    Model.RuntimeModel[] modelsToUnify = new Model.RuntimeModel[contextsToUnify.length];
    for (int i = 0; i < contextsToUnify.length; i++) {
      modelsToUnify[i] = contextsToUnify[i].getModel();
    }

    Model unifiedModel = CreateUnifiedModel(rounding, modelsToUnify);

    unifiedModel.setName(unifiedModelName);

    if (predefinedPath != null) {
      unifiedModel.setPredefinedPath(reconstructPredefinedPath(predefinedPath, unifiedModel));
      unifiedContext.setPathGenerator(new PredefinedPath(new PredefinedPathStopCondition()));
    }

    unifiedContext.setModel(unifiedModel.build());

    // Set the start element & copy pathGenerator from its context
    String startElement = null;
    for (Context context : contextsToUnify) {
      if (context.getNextElement() != null) {
        startElement = getPrefixedString(context.getNextElement().getName(), context.getModel().getName() + "_");
        if (predefinedPath == null) unifiedContext.setPathGenerator(context.getPathGenerator());
        break;
      }
    }
    if (startElement != null) {
      for (Vertex.RuntimeVertex vertex : unifiedContext.getModel().getVertices()) {
        if (vertex.getName().equals(startElement)) {
          unifiedContext.setNextElement(vertex);
          break;
        }
      }
    }

    return unifiedContext;
  }

  public static Vertex copyVertex(Vertex vertex, String prefix) {
    return copyVertex(vertex, prefix, 1.0f);
  }

  public static Vertex copyVertex(Vertex vertex, String prefix, float rounding) {
    Vertex newVertex = new Vertex();
    if (vertex.getName() != null) {
      newVertex.setName(getPrefixedString(vertex.getName(), prefix));
    }
    if (vertex.getId() != null) {
      newVertex.setId(getPrefixedString(vertex.getId(), prefix));
    }
    if (!isEmpty(vertex.getSharedState())) {
      newVertex.setSharedState(vertex.getSharedState());  // Shouldn't ever reach this for the Unify command, but left for completeness
    }
    if (vertex.getRequirements() != null) {
      newVertex.setRequirements(vertex.getRequirements());
    }
    if (vertex.getActions() != null) {
      newVertex.setActions(vertex.getActions());
    }
    if (vertex.getProperties() != null) {
      newVertex.setProperties(vertex.getProperties());
      if (newVertex.getProperties().containsKey("x") && newVertex.getProperties().containsKey("y")) {
        newVertex.setProperty("x", (double) Math.round((double) newVertex.getProperty("x") / rounding) * rounding);
        newVertex.setProperty("y", (double) Math.round((double) newVertex.getProperty("y") / rounding) * rounding);
      }
    }
    return newVertex;
  }

  public static Edge copyEdge(Edge edge, String prefix) {
    Edge newEdge = new Edge();
    if (edge.getName() != null) {
      newEdge.setName(getPrefixedString(edge.getName(), prefix));
    }
    if (edge.getId() != null) {
      newEdge.setId(getPrefixedString(edge.getId(), prefix));
    }
    if (edge.getGuard() != null) {
      newEdge.setGuard(edge.getGuard());
    }
    if (edge.getActions() != null) {
      newEdge.setActions(edge.getActions());
    }
    if (edge.getRequirements() != null) {
      newEdge.setRequirements(edge.getRequirements());
    }

    return newEdge;
  }

  private static Edge copyEdge(Edge edge, String prefix, Model model) {
    Edge newEdge = copyEdge(edge, prefix);

    Vertex sourceVertex = null;
    Vertex targetVertex = null;
    for (Vertex vertex : model.getVertices()) {
      if (vertex.getId().equals(getPrefixedString(edge.getSourceVertex().getId(), prefix))) {
        sourceVertex = vertex;
      }
      if (vertex.getId().equals(getPrefixedString(edge.getTargetVertex().getId(), prefix))) {
        targetVertex = vertex;
      }
    }
    if (sourceVertex == null || targetVertex == null) {
      throw new RuntimeException("Could not find source or target vertex for edge: " + edge.getId());
    }
    newEdge.setSourceVertex(sourceVertex);
    newEdge.setTargetVertex(targetVertex);

    return newEdge;
  }

  public static String getPrefixedString(String string, String prefix) {
    return prefix + string;
  }
}
