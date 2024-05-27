package org.graphwalker.cli.commands;

/*
 * #%L
 * GraphWalker Command Line Interface
 * %%
 * Copyright (C) 2005 - 2014 GraphWalker
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;

@Parameters(commandDescription = "Run benchmarks on a provided model.")
public class Benchmark {

  @Parameter(names = {"--verbose", "-v"}, required = false, description = "Will print more details to stdout.")
  public boolean verbose = false;

  @Parameter(names = {"--output", "-o"}, required = false, description = "Output directory for benchmarking results.")
  public String output = "";

  @Parameter(names = {"--threads", "-t"}, required = false, validateWith = PositiveInteger.class, description = "Number of threads to use during benchmarking. Warning: Using more than one thread will make the results non-deterministic.")
  public int threads = 1;

  @Parameter(names = {"--runs", "-r"}, required = false, validateWith = PositiveInteger.class, description = "Number of runs to do.")
  public int runs = 1;

  @Parameter(names = {"--median_runs", "-m"}, required = false, validateWith = PositiveInteger.class, description = "Number of runs to do for median calculation.")
  public int medianRuns = 5;

  @Parameter(names = {"--input", "-i"}, required = true, arity = 1, description = "The model, as a GRAPHML or JSON file.")
  public String model = "";

  @Parameter(names = {"--generators", "-g"}, required = true, arity = 1, description = "File for pathgenerators to use during benchmarking." + "The configuration file is a newline separated list of path generators + stop condition to use." + "e.g. RANDOM(EDGE_COVERAGE(100))")
  public String generators = "";

  @Parameter(names = {"--unified"}, required = false, description = "Use the Unified Path Generator.")
  public boolean unified = false;

  @Parameter(names = {"--seed", "-d"}, required = false, description = "Seed the random generator using the provided number. Using a seeded number, will generate the same benchmarks every time.")
  public long seed = 0;

  @Parameter(names = {"--kill_after", "-k"}, required = false, validateWith = PositiveInteger.class, description = "Kill a benchmark run after the provided number of seconds have passed.")
  public int killAfter = 0;
}
