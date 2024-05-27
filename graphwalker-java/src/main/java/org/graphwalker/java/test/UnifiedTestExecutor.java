package org.graphwalker.java.test;

import org.graphwalker.core.event.Observer;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.machine.Machine;
import org.graphwalker.core.machine.UnifiedMachine;
import org.graphwalker.io.factory.java.JavaContext;

import java.io.IOException;
import java.util.Collection;

import static org.graphwalker.core.utils.Unify.CreateUnifiedContext;

/**
 * <h1>UnifiedTestExecutor</h1>
 * <p>UnifiedTestExecutor is a test executor which first unifies all the models based on provided implementation classes
 * prior to executing them. This allows for usage of algorithms such as Directed Chinese Postman, or AllShortestPath,
 * which rely on the entire graph being strongly connected, and work better if the entire graph is available to them.</p>
 */
public final class UnifiedTestExecutor extends TestExecutor {
  public UnifiedTestExecutor(Configuration configuration) throws IOException {
    super(configuration);
  }

  public UnifiedTestExecutor(Class<?>... tests) throws IOException {
    super(tests);
  }

  public UnifiedTestExecutor(Context... contexts) {
    super(contexts);
  }

  public UnifiedTestExecutor(Collection<Context> contexts) {
    super(contexts);
  }

  @Override
  protected Machine createMachine(Collection<Context> contexts) {
    Context[] contextsArray = new Context[contexts.size()];
    int i = 0;
    for (Context context : contexts) {
      contextsArray[i++] = context;
    }

    Context unifiedContext = CreateUnifiedContext(new JavaContext(), contextsArray);

    Machine machine = new UnifiedMachine(unifiedContext, contextsArray);
    for (Context context : machine.getContexts()) {
      if (context instanceof Observer) {
        machine.addObserver((Observer) context);
      }
    }
    return machine;
  }

  /**
   * Alternative createMachine which unifies things before creating the machine.
   *
   * @param machineConfiguration The machine configuration to use.
   * @return The created machine.
   */
  @Override
  protected Machine createMachine(MachineConfiguration machineConfiguration) throws IOException {
    Collection<Context> originalContexts = createContexts(machineConfiguration);
    return createMachine(originalContexts);
  }
}
