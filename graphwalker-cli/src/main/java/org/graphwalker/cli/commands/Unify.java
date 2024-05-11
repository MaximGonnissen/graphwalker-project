package org.graphwalker.cli.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Will unify a multi-model file into a single model, if possible.")
public class Unify {

  @Parameter(names = {"--input", "-i"}, required = true, arity = 1, description = "The input model file.")
  public String input = "";

  @Parameter(names = {"--output", "-o"}, required = false, arity = 1, description = "The output model file. If not specified, will append '_unified' to the input file.")
  public String output = "";
}
