package org.graphwalker.core.machine;

import org.graphwalker.core.event.EventType;
import org.graphwalker.core.generator.UnifiedDummyPathGenerator;
import org.graphwalker.core.model.*;
import org.graphwalker.core.utils.Unify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.*;

import static org.graphwalker.core.common.Objects.isNotNull;
import static org.graphwalker.core.common.Objects.isNull;

/**
 * <h1>UnifiedMachine</h1>
 * <p>UnifiedMachine is a machine that uses a unified context and a collection of original contexts to simulate a unified
 * model. It makes calls to the original contexts' methods for test execution, but uses the unified context for the
 * actual simulation.</p>
 */
public class UnifiedMachine extends MachineBase {

  private static final Logger logger = LoggerFactory.getLogger(UnifiedMachine.class);
  private final Context unifiedContext;
  private final List<Context> originalContexts;
  private final Map<Element, Context> originalContextMap = new HashMap<>();
  private final Map<Element, Element> originalElementMap = new HashMap<>();
  private org.graalvm.polyglot.Context globalExecutionEnvironment;

  public UnifiedMachine(Context unifiedContext, Context... originalContexts) {
    this.unifiedContext = unifiedContext;
    this.originalContexts = Arrays.asList(originalContexts);
    executeInitActions();
    setCurrentContext(this.unifiedContext);
  }

  private void executeInitActions() {
    globalExecutionEnvironment = org.graalvm.polyglot.Context.newBuilder().allowAllAccess(true).option("engine.WarnInterpreterOnly", "false").build();

    unifiedContext.setGlobalExecutionEnvironment(globalExecutionEnvironment);
    unifiedContext.setProfiler(getProfiler());
    if (isNull(unifiedContext.getModel())) {
      throw new MachineException("A context must be associated with a model");
    }
    execute(unifiedContext.getModel().getActions());

    for (Context context : originalContexts) {
      context.setGlobalExecutionEnvironment(globalExecutionEnvironment);
      context.setProfiler(getProfiler());
      context.setPathGenerator(new UnifiedDummyPathGenerator(unifiedContext.getPathGenerator()));
      if (isNull(context.getModel())) {
        throw new MachineException("A context must be associated with a model");
      }
      // TODO: Should we execute the actions of the original contexts?
    }
  }

  private void execute(List<Action> actions) {
    for (Action action : actions) {
      execute(action);
    }
  }

  private void execute(Action action) {
    getCurrentContext().execute(action);
  }

  @Override
  public Context getNextStep() {
    MDC.put("trace", UUID.randomUUID().toString());

    walk();

    notifyOriginalObservers(getCurrentContext().getCurrentElement(), EventType.BEFORE_ELEMENT);

    getProfiler().start(getCurrentContext());
    execute(getCurrentContext().getCurrentElement());
    getProfiler().stop(getCurrentContext());
    if (getCurrentContext().getLastElement() instanceof Edge.RuntimeEdge) {
      updateRequirements(getCurrentContext().getLastElement());
    }
    if (getCurrentContext().getCurrentElement() instanceof Vertex.RuntimeVertex) {
      updateRequirements(getCurrentContext().getCurrentElement());
    }

    notifyOriginalObservers(getCurrentContext().getCurrentElement(), EventType.AFTER_ELEMENT);

    return getCurrentContext();
  }

  private void notifyOriginalObservers(Element element, EventType eventType) {
    notifyObservers(element, eventType);

    Context originalContext = getOriginalContext(element);
    if (isNull(originalContext)) {
      return;
    }

    Element originalElement = getOriginalElement(element);
    if (isNull(originalElement)) {
      return;
    }

    setCurrentContext(originalContext);

    notifyObservers(originalElement, eventType);

    setCurrentContext(unifiedContext);
  }

  private Context getOriginalContext(Element element) {
    if (originalContextMap.containsKey(element)) {
      return originalContextMap.get(element);
    }

    try {
      String possibleModelName = element.getName().split("_")[0];
      for (Context context : originalContexts) {
        if (context.getModel().getName().equals(possibleModelName)) {
          originalContextMap.put(element, context);
          return context;
        }
      }
      throw new Exception();  // Force catch block
    } catch (Exception e) {
      // Assume shared element
      for (Context context : originalContexts) {
        for (String sharedStateName : context.getModel().getSharedStates()) {
          if (sharedStateName.equals(element.getName())) {
            originalContextMap.put(element, context);
            return context;
          }
        }
      }
    }

    logger.error("Could not find original context for element: {}", element.getName());
    return null;
  }

  private Element getOriginalElement(Element element) {
    if (originalElementMap.containsKey(element)) {
      return originalElementMap.get(element);
    }

    Context originalContext = getOriginalContext(element);

    if (isNull(originalContext)) {
      logger.error("Could not find original context for element: {}", element.getName());
      return null;
    }

    Element foundElement;

    if (isVertex(element)) {
      foundElement = getOriginalVertex(element, originalContext);
    } else {
      foundElement = getOriginalEdge(element, originalContext);
    }

    if (isNull(foundElement)) {
      logger.error("Could not find original element for element: {}", element.getName());
    }

    originalElementMap.put(element, foundElement);

    return foundElement;
  }

  private Element getOriginalVertex(Element element, Context originalContext) {
    for (Vertex.RuntimeVertex vertex : originalContext.getModel().getVertices()) {
      if (getPrefixedName(vertex, originalContext).equals(element.getName())) {
        return vertex;
      }
      if (vertex.hasSharedState()) {
        if (vertex.getSharedState().equals(element.getName())) {
          return vertex;
        }
      }
    }
    return null;
  }

  private Element getOriginalEdge(Element element, Context originalContext) {
    for (Edge.RuntimeEdge edge : originalContext.getModel().getEdges()) {
      if (getPrefixedName(edge, originalContext).equals(element.getName())) {
        return edge;
      }
    }
    return null;
  }

  private String getPrefixedName(Element element, Context context) {
    return Unify.getPrefixedString(element.getName(), context.getModel().getName() + "_");
  }

  private void walk() {
    try {
      takeNextStep();
      if (ExecutionStatus.NOT_EXECUTED.equals(unifiedContext.getExecutionStatus())) {
        unifiedContext.setExecutionStatus(ExecutionStatus.EXECUTING);
      }
    } catch (Throwable t) {
      logger.error(t.getMessage());
      getExceptionStrategy().handle(this, new MachineException(unifiedContext, t));
    }
  }

  private void takeNextStep() {
    if (isNotNull(unifiedContext.getNextElement())) {
      unifiedContext.setCurrentElement(unifiedContext.getNextElement());
    } else {
      unifiedContext.getPathGenerator().getNextStep();
    }
    updateOriginalContextElement();
  }

  private void updateOriginalContextElement() {
    Context originalContext = getOriginalContext(getCurrentContext().getCurrentElement());
    if (isNull(originalContext)) {
      return;
    }
    Element originalElement = getOriginalElement(getCurrentContext().getCurrentElement());
    if (isNull(originalElement)) {
      return;
    }
    originalContext.setCurrentElement(originalElement);
  }

  private void execute(Element element) {
    Context originalContext = getOriginalContext(element);
    if (isNull(originalContext)) {
      return;
    }
    Element originalElement = getOriginalElement(element);

    originalContext.execute(originalElement);
  }

  @Override
  public boolean hasNextStep() {
    MDC.put("trace", UUID.randomUUID().toString());
    ExecutionStatus status = unifiedContext.getExecutionStatus();
    if (ExecutionStatus.COMPLETED.equals(status) || ExecutionStatus.FAILED.equals(status)) {
      return false;
    }
    if (isNull(unifiedContext.getPathGenerator())) {
      throw new MachineException("No path generator is defined");
    }
    boolean hasMoreSteps = unifiedContext.getPathGenerator().hasNextStep();
    if (!hasMoreSteps) {
      unifiedContext.setExecutionStatus(ExecutionStatus.COMPLETED);
      updateRequirements(unifiedContext.getModel());
    }
    return hasMoreSteps;
  }

  private boolean isVertex(Element element) {
    return element instanceof Vertex.RuntimeVertex;
  }

  private void updateRequirements(Element element) {
    if (element.hasRequirements()) {
      for (Requirement requirement : element.getRequirements()) {
        unifiedContext.setRequirementStatus(requirement, RequirementStatus.PASSED);
      }
    }

    Context originalContext = getOriginalContext(element);
    if (isNull(originalContext)) {
      return;
    }
    Element originalElement = getOriginalElement(element);
    if (isNull(originalElement)) {
      return;
    }
    updateRequirements(originalContext, originalElement);
  }

  private void updateRequirements(Context context, Element element) {
    if (element.hasRequirements()) {
      for (Requirement requirement : element.getRequirements()) {
        context.setRequirementStatus(requirement, RequirementStatus.PASSED);
      }
    }
  }
}
