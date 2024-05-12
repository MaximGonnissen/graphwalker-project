package org.graphwalker.cli.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.graphwalker.io.factory.ContextFactoryScanner;

@Parameters(commandDescription = "Will unify a multi-model file into a single model, if possible.")
public class Unify {

  @Parameter(names = {"--input", "-i"}, required = true, arity = 1, description = "The input model file.")
  public String input = "";

  @Parameter(names = {"--output", "-o"}, required = false, arity = 1, description = "The output model file. If not specified, will append '_unified' to the input file.")
  public String output = "";

  @Parameter(names = {"--format", "-f"}, required = false, arity = 1, description = "The output format. Default is JSON.")
  public String format = ContextFactoryScanner.JSON;

  @Parameter(names = {"--require_strongly_connected", "-rsc"}, required = false, arity = 1, description = "Require the model to be strongly connected.")
  public boolean requireStronglyConnected = false;

  @Parameter(names = {"--verbose", "-v"}, required = false, arity = 1, description = "Prints out more information.")
  public boolean verbose = false;
}
