package org.graphwalker.core.generator;

import org.graphwalker.core.condition.StopCondition;
import org.graphwalker.core.machine.Context;

public class UnifiedDummyPathGenerator extends PathGeneratorBase<StopCondition> {

  PathGenerator<?> parentPathGenerator;

  public UnifiedDummyPathGenerator(PathGenerator<?> parentPathGenerator) {
    this.parentPathGenerator = parentPathGenerator;
  }

  @Override
  public boolean hasNextStep() {
    return parentPathGenerator.hasNextStep();
  }

  @Override
  public Context getContext() {
    return parentPathGenerator.getContext();
  }

  @Override
  public StopCondition getStopCondition() {
    return parentPathGenerator.getStopCondition();
  }
}
