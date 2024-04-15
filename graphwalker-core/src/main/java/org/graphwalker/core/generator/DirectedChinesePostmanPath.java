package org.graphwalker.core.generator;

import org.graphwalker.core.algorithm.DirectedChinesePostman;
import org.graphwalker.core.condition.ReachedStopCondition;
import org.graphwalker.core.condition.StopCondition;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.model.Element;

public class DirectedChinesePostmanPath extends PathGeneratorBase<StopCondition> {

  private static final Class<DirectedChinesePostman> DCPAlgorithm = DirectedChinesePostman.class;

  public DirectedChinesePostmanPath(ReachedStopCondition stopCondition) {
    setStopCondition(stopCondition);
  }

  @Override
  public Context getNextStep() {
    Context context = super.getNextStep();
    Element currentElement = context.getCurrentElement();

    // TODO?

    DirectedChinesePostman directedChinesePostman = getDirectedChinesePostman(context);
    return context.setCurrentElement(directedChinesePostman.getNextElement(currentElement));
  }

  @Override
  public boolean hasNextStep() {
    return !getStopCondition().isFulfilled();
  }

  private DirectedChinesePostman getDirectedChinesePostman(Context context) {
    return context.getAlgorithm(DCPAlgorithm);
  }
}
