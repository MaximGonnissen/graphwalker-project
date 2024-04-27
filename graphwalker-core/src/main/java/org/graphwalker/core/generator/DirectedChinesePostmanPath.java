package org.graphwalker.core.generator;

import org.graphwalker.core.algorithm.DirectedChinesePostman;
import org.graphwalker.core.condition.StopCondition;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.model.Element;

/**
 * @author Maxim Gonnissen
 */
public class DirectedChinesePostmanPath extends PathGeneratorBase<StopCondition> {

  private static final Class<DirectedChinesePostman> DCPAlgorithm = DirectedChinesePostman.class;
  private boolean hasNextStep = true;

  public DirectedChinesePostmanPath(StopCondition stopCondition) {
    setStopCondition(stopCondition);
  }

  @Override
  public Context getNextStep() {
    Context context = super.getNextStep();
    DirectedChinesePostman directedChinesePostman = getDirectedChinesePostman(context);
    Element nextElement = directedChinesePostman.getNextElement();
    hasNextStep = directedChinesePostman.hasNextElement();
    return context.setCurrentElement(nextElement);
  }

  @Override
  public boolean hasNextStep() {
    return !getStopCondition().isFulfilled() && hasNextStep;
  }

  private DirectedChinesePostman getDirectedChinesePostman(Context context) {
    return context.getAlgorithm(DCPAlgorithm);
  }
}
