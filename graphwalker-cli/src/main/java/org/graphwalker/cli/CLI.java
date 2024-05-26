package org.graphwalker.cli;

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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.jersey.api.container.grizzly2.GrizzlyServerFactory;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.grizzly.http.server.HttpServer;
import org.graphwalker.cli.commands.*;
import org.graphwalker.cli.util.BenchmarkResult;
import org.graphwalker.cli.util.LoggerUtil;
import org.graphwalker.cli.util.UnsupportedFileFormat;
import org.graphwalker.core.condition.AlternativeCondition;
import org.graphwalker.core.condition.StopCondition;
import org.graphwalker.core.condition.TimeDuration;
import org.graphwalker.core.event.EventType;
import org.graphwalker.core.generator.PathGenerator;
import org.graphwalker.core.generator.SingletonRandomGenerator;
import org.graphwalker.core.machine.*;
import org.graphwalker.core.model.Edge;
import org.graphwalker.core.model.Element;
import org.graphwalker.core.model.Requirement;
import org.graphwalker.core.model.Vertex;
import org.graphwalker.dsl.antlr.DslException;
import org.graphwalker.dsl.antlr.generator.GeneratorFactory;
import org.graphwalker.io.common.ResourceUtils;
import org.graphwalker.io.factory.ContextFactory;
import org.graphwalker.io.factory.dot.DotContextFactory;
import org.graphwalker.io.factory.java.JavaContext;
import org.graphwalker.io.factory.java.JavaContextFactory;
import org.graphwalker.io.factory.json.JsonContext;
import org.graphwalker.io.factory.json.JsonContextFactory;
import org.graphwalker.io.factory.yed.YEdContextFactory;
import org.graphwalker.java.test.TestExecutor;
import org.graphwalker.java.test.UnifiedTestExecutor;
import org.graphwalker.modelchecker.ContextsChecker;
import org.graphwalker.restful.Restful;
import org.graphwalker.restful.Util;
import org.graphwalker.websocket.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.graphwalker.core.common.Objects.isNullOrEmpty;
import static org.graphwalker.io.common.Util.printVersionInformation;

public class CLI {

  private static final Logger logger = LoggerFactory.getLogger(CLI.class);

  private Offline offline;
  private Online online;
  private Methods methods;
  private Requirements requirements;
  private Convert convert;
  private Source source;
  private Check check;
  private Unify unify;
  private Benchmark benchmark;
  private Command command = Command.NONE;

  public static void main(String[] args) {
    CLI cli = new CLI();
    try {
      int status = cli.run(args);
      System.exit(status);
    } catch (Exception e) {
      // We should have caught all exceptions up until here, but there
      // might have been problems with the command parser for instance...
      System.err.println(e + System.lineSeparator());
      logger.error("An unexpected error occurred when running command: " + StringUtils.join(args, " "), e);
    }
  }

  /**
   * Parses the command line.
   */
  public int run(String[] args) {
    Options options = new Options();
    JCommander jc = new JCommander(options);
    jc.setProgramName("graphwalker");

    offline = new Offline();
    jc.addCommand("offline", offline);

    online = new Online();
    jc.addCommand("online", online);

    methods = new Methods();
    jc.addCommand("methods", methods);

    requirements = new Requirements();
    jc.addCommand("requirements", requirements);

    convert = new Convert();
    jc.addCommand("convert", convert);

    source = new Source();
    jc.addCommand("source", source);

    check = new Check();
    jc.addCommand("check", check);

    unify = new Unify();
    jc.addCommand("unify", unify);

    benchmark = new Benchmark();
    jc.addCommand("benchmark", benchmark);

    try {
      jc.parse(args);
      setLogLevel(options);

      if (options.help) {
        jc.usage();
        return 0;
      } else if (options.version) {
        System.out.println(printVersionInformation());
        return 0;
      } else if (jc.getParsedCommand() == null) {
        throw new MissingCommandException("Missing a command. Use the '--help' option to list all available commands.");
      }

      // Parse for commands
      if (jc.getParsedCommand().equalsIgnoreCase("offline")) {
        command = Command.OFFLINE;
        runCommandOffline();
      } else if (jc.getParsedCommand().equalsIgnoreCase("online")) {
        command = Command.ONLINE;
        runCommandOnline();
      } else if (jc.getParsedCommand().equalsIgnoreCase("methods")) {
        command = Command.METHODS;
        runCommandMethods();
      } else if (jc.getParsedCommand().equalsIgnoreCase("requirements")) {
        command = Command.REQUIREMENTS;
        runCommandRequirements();
      } else if (jc.getParsedCommand().equalsIgnoreCase("convert")) {
        command = Command.CONVERT;
        runCommandConvert();
      } else if (jc.getParsedCommand().equalsIgnoreCase("source")) {
        command = Command.SOURCE;
        runCommandSource();
      } else if (jc.getParsedCommand().equalsIgnoreCase("check")) {
        command = Command.CHECK;
        return runCommandCheck();
      } else if (jc.getParsedCommand().equalsIgnoreCase("unify")) {
        command = Command.UNIFY;
        runCommandUnify();
      } else if (jc.getParsedCommand().equalsIgnoreCase("benchmark")) {
        command = Command.BENCHMARK;
        runCommandBenchmark();
      }
    } catch (UnsupportedFileFormat | MissingCommandException e) {
      System.err.println(e.getMessage() + System.lineSeparator());
      return 1;
    } catch (ParameterException e) {
      System.err.println("An error occurred when running command: '" + StringUtils.join(args, " ") + "'.");
      System.err.println(e.getMessage() + System.lineSeparator());
      jc.usage();
      return 1;
    } catch (Exception e) {
      logger.error("An error occurred when running command: " + StringUtils.join(args, " "), e);
      System.err.println("An error occurred when running command: '" + StringUtils.join(args, " ") + "'.");
      System.err.println(e.getMessage() + System.lineSeparator());
      return 2;
    }

    return 0;
  }

  private void setLogLevel(Options options) {
    // OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL
    if (options.debug.equalsIgnoreCase("OFF")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.OFF);
    } else if (options.debug.equalsIgnoreCase("ERROR")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.ERROR);
    } else if (options.debug.equalsIgnoreCase("WARN")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.WARN);
    } else if (options.debug.equalsIgnoreCase("INFO")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.INFO);
    } else if (options.debug.equalsIgnoreCase("DEBUG")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.DEBUG);
    } else if (options.debug.equalsIgnoreCase("TRACE")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.TRACE);
    } else if (options.debug.equalsIgnoreCase("ALL")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.ALL);
    } else {
      throw new ParameterException("Incorrect argument to --debug");
    }
  }

  private int runCommandCheck() throws Exception, UnsupportedFileFormat {
    List<Context> contexts = getContextsWithPathGenerators(check.model.iterator());
    if (check.blocked) {
      org.graphwalker.io.common.Util.filterBlockedElements(contexts);
    }

    List<String> issues = ContextsChecker.hasIssues(contexts);
    if (!issues.isEmpty()) {
      for (String issue : issues) {
        System.out.println(issue);
      }
      return 1;
    } else {
      System.out.println("No issues found with the model(s).");
      return 0;
    }
  }

  private void runCommandRequirements() throws Exception, UnsupportedFileFormat {
    SortedSet<String> reqs = new TreeSet<>();
    List<Context> contexts = getContexts(requirements.model.iterator());
    if (requirements.blocked) {
      org.graphwalker.io.common.Util.filterBlockedElements(contexts);
    }

    for (Context context : contexts) {
      for (Requirement req : context.getRequirements()) {
        reqs.add(req.getKey());
      }
    }
    for (String req : reqs) {
      System.out.println(req);
    }
  }

  private void runCommandMethods() throws Exception, UnsupportedFileFormat {
    SortedSet<String> names = new TreeSet<>();
    List<Context> contexts = getContexts(methods.model.iterator());
    if (methods.blocked) {
      org.graphwalker.io.common.Util.filterBlockedElements(contexts);
    }

    for (Context context : contexts) {
      for (Vertex.RuntimeVertex vertex : context.getModel().getVertices()) {
        if (vertex.getName() != null) {
          names.add(vertex.getName());
        }
      }
      for (Edge.RuntimeEdge edge : context.getModel().getEdges()) {
        if (edge.getName() != null) {
          names.add(edge.getName());
        }
      }
    }

    for (String name : names) {
      System.out.println(name);
    }
  }

  private void runCommandOnline() throws Exception, UnsupportedFileFormat {
    if (online.service.equalsIgnoreCase(Online.SERVICE_WEBSOCKET)) {
      WebSocketServer GraphWalkerWebSocketServer = new WebSocketServer(online.port);
      try {
        GraphWalkerWebSocketServer.startService();
      } catch (Exception e) {
        logger.error("Something went wrong.", e);
      }
    } else if (online.service.equalsIgnoreCase(Online.SERVICE_RESTFUL)) {
      ResourceConfig rc = new DefaultResourceConfig();
      try {
        List<Context> contexts = getContextsWithPathGenerators(online.model.iterator());
        if (offline.unified) {
          Context unifiedContext = org.graphwalker.core.utils.Unify.CreateUnifiedContext(new JavaContext(), contexts.toArray(new Context[0]));
          unifiedContext.setPathGenerator(contexts.get(0).getPathGenerator());
          contexts = new ArrayList<>(Collections.singletonList(unifiedContext));
        }

        rc.getSingletons().add(new Restful(contexts, online.verbose, online.unvisited, online.blocked));
      } catch (MachineException e) {
        System.err.println("Was the argument --model correctly?");
        throw e;
      }

      String url = "http://0.0.0.0:" + online.port;

      HttpServer server = GrizzlyServerFactory.createHttpServer(url, rc);
      System.out.println("Try http://localhost:" + online.port + "/graphwalker/hasNext or http://localhost:" + online.port + "/graphwalker/getNext");
      System.out.println("Press Control+C to end...");

      try {
        server.start();
        Thread.currentThread().join();
      } catch (InterruptedException e) {
        // Typically the user pressed Ctrl+C
      } catch (Exception e) {
        logger.error("An error occurred when running command online: ", e);
      } finally {
        server.stop();
      }
    } else {
      throw new ParameterException("--service expected either WEBSOCKET or RESTFUL.");
    }
  }

  private void runCommandConvert() throws Exception, UnsupportedFileFormat {
    String inputFileName = convert.input;

    ContextFactory inputFactory = getContextFactory(inputFileName);
    List<Context> contexts;
    try {
      contexts = inputFactory.create(Paths.get(inputFileName));
    } catch (DslException e) {
      throw new Exception("The following syntax error occurred when parsing: '" + inputFileName + "'." + System.lineSeparator() + "Syntax Error: " + e.getMessage() + System.lineSeparator());
    }

    if (convert.blocked) {
      org.graphwalker.io.common.Util.filterBlockedElements(contexts);
    }

    ContextFactory outputFactory = getContextFactory("foo." + convert.format);
    System.out.println(outputFactory.getAsString(contexts));
  }

  private void runCommandSource() throws Exception, UnsupportedFileFormat {
    String modelFileName = source.input.get(0);
    String templateFileName = source.input.get(1);

    // Read the model
    ContextFactory inputFactory = getContextFactory(modelFileName);
    List<Context> contexts;
    try {
      contexts = inputFactory.create(Paths.get(modelFileName));
      if (isNullOrEmpty(contexts)) {
        logger.error("No valid models found in: " + modelFileName);
        throw new RuntimeException("No valid models found in: '" + modelFileName + "'.");
      }
    } catch (DslException e) {
      throw new Exception("The following syntax error occurred when parsing: '" + modelFileName + "'." + System.lineSeparator() + "Syntax Error: " + e.getMessage());
    }

    if (source.blocked) {
      org.graphwalker.io.common.Util.filterBlockedElements(contexts);
    }

    for (Context context : contexts) {
      SortedSet<String> names = new TreeSet<>();
      for (Vertex.RuntimeVertex vertex : context.getModel().getVertices()) {
        if (vertex.hasName()) {
          names.add(vertex.getName());
        }
      }
      for (Edge.RuntimeEdge edge : context.getModel().getEdges()) {
        if (edge.hasName()) {
          names.add(edge.getName());
        }
      }

      // Read the template
      StringBuilder templateStrBuilder = new StringBuilder();
      String line;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(ResourceUtils.getResourceAsStream(templateFileName)))) {
        while ((line = reader.readLine()) != null) {
          templateStrBuilder.append(line).append("\n");
        }
      } catch (IOException e) {
        logger.error(e.getMessage());
        throw new RuntimeException("Could not read the file: '" + templateFileName + "'.");
      }
      String templateStr = templateStrBuilder.toString();

      // Apply the template and generate the source code to std out
      String header = "", body = "", footer = "";
      Pattern p = Pattern.compile("HEADER<\\{\\{([.\\s\\S]+)\\}\\}>HEADER([.\\s\\S]+)FOOTER<\\{\\{([.\\s\\S]+)\\}\\}>FOOTER");
      Matcher m = p.matcher(templateStr);
      if (m.find()) {
        header = m.group(1);
        body = m.group(2);
        footer = m.group(3);
      }

      System.out.println(header);
      for (String name : names) {
        System.out.println(body.replaceAll("\\{LABEL\\}", name));
      }
      System.out.println(footer);
    }
  }

  private void runCommandOffline() throws Exception, UnsupportedFileFormat {
    if (offline.model.size() > 0) {

      List<Context> contexts = getContextsWithPathGenerators(offline.model.iterator());
      if (offline.blocked) {
        org.graphwalker.io.common.Util.filterBlockedElements(contexts);
      }

      TestExecutor executor;
      if (offline.unified) {
        executor = new UnifiedTestExecutor(contexts);
        executor.getMachine().getCurrentContext().setPathGenerator(contexts.get(0).getPathGenerator());
      } else {
        executor = new TestExecutor(contexts);
      }

      executor.getMachine().addObserver((machine, element, type) -> {
        if (EventType.BEFORE_ELEMENT.equals(type)) {
          System.out.println(Util.getStepAsJSON(machine, offline.verbose, offline.unvisited));
        }
      });

      if (offline.seed != 0) {
        SingletonRandomGenerator.setSeed(offline.seed);
      }

      executor.execute();
    } else if (!offline.gw.isEmpty()) {
      List<Context> contexts = new JsonContextFactory().create(Paths.get(offline.gw));

      if (offline.seed != 0) {
        SingletonRandomGenerator.setSeed(offline.seed);
      }

      if (offline.blocked) {
        org.graphwalker.io.common.Util.filterBlockedElements(contexts);
      }

      Machine machine;
      if (offline.unified) {
        Context unifiedContext = org.graphwalker.core.utils.Unify.CreateUnifiedContext(new JavaContext(), contexts.toArray(new Context[0]));
        machine = new UnifiedMachine(unifiedContext, contexts);
      } else {
        machine = new SimpleMachine(contexts);
      }
      while (machine.hasNextStep()) {
        machine.getNextStep();
        System.out.println(Util.getStepAsJSON(machine, offline.verbose, offline.unvisited));
      }
    }
  }

  private void runCommandUnify() throws Exception, UnsupportedFileFormat {
    String inputFileName = unify.input;
    String outputFileName = unify.output;

    if (unify.rounding <= 0) {
      unify.rounding = 1;
    }

    // Sort out the output file name
    if (outputFileName == null || outputFileName.isEmpty()) {
      outputFileName = inputFileName.replaceFirst("\\..*$", "_unified." + unify.format.toLowerCase());
    } else if (!outputFileName.toLowerCase().endsWith("." + unify.format.toLowerCase())) {
      outputFileName = outputFileName + "." + unify.format.toLowerCase();
    }

    // Read the model
    ContextFactory inputFactory = getContextFactory(inputFileName);
    List<Context> contexts;
    try {
      contexts = inputFactory.create(Paths.get(inputFileName));
      if (isNullOrEmpty(contexts)) {
        logger.error("No valid models found in: " + inputFileName);
        throw new RuntimeException("No valid models found in: '" + inputFileName + "'.");
      }
    } catch (DslException e) {
      throw new Exception("The following syntax error occurred when parsing: '" + inputFileName + "'." + System.lineSeparator() + "Syntax Error: " + e.getMessage());
    }

    if (unify.verbose) System.out.println("Found " + contexts.size() + " models in " + inputFileName);

    // Check if the model is strongly connected
    if (unify.requireStronglyConnected) {
      if (!checkStronglyConnected(contexts)) {
        logger.error("The model is not strongly connected.");
        throw new Exception("The model is not strongly connected.");
      }
    }

    String unifiedModelName = Paths.get(inputFileName).getFileName().toString().replaceFirst("\\..*$", "") + "_unified";

    Context[] contextsToUnify = new Context[contexts.size()];
    for (int i = 0; i < contexts.size(); i++) {
      contextsToUnify[i] = contexts.get(i);
    }

    Context unifiedContext = org.graphwalker.core.utils.Unify.CreateUnifiedContext((float) unify.rounding, unifiedModelName, new JsonContext(), contextsToUnify);

    if (unify.verbose) System.out.println("Saving unified model to " + outputFileName);

    ContextFactory outputFactory = getContextFactory(outputFileName);
    String json = outputFactory.getAsString(new ArrayList<>(Collections.singletonList(unifiedContext)));

    try (FileWriter fileWriter = new FileWriter(outputFileName)) {
      fileWriter.write(json);
    }
  }

  private void runCommandBenchmark() throws Exception, UnsupportedFileFormat {
    ContextFactory contextFactory = getContextFactory(benchmark.model);

    List<BenchmarkResult> benchmarkResults = new ArrayList<>();

    Random seedGenerator = new Random(benchmark.seed);

    int threadsCount = Math.max(1, benchmark.threads - 1);  // -1 because the main thread is also used
    Thread[] threads = new Thread[threadsCount];

    try (BufferedReader reader = new BufferedReader(new FileReader(benchmark.generators))) {
      while (reader.ready()) {
        String generatorString = reader.readLine();

        if (generatorString == null || generatorString.isEmpty() || generatorString.startsWith("#")) {
          continue;
        }

        if (benchmark.verbose) System.out.println("Running benchmark(s) for generator: " + generatorString);

        boolean threadStarted = false;
        for (int i = 0; i < threadsCount; i++) {
          if (threads[i] == null || !threads[i].isAlive()) {
            threads[i] = new Thread(() -> {
              try {
                for (int j = 0; j < benchmark.runs; j++) {
                  long seed = seedGenerator.nextLong();
                  BenchmarkResult result = RunBenchmark(generatorString, contextFactory, seed, j);
                  benchmarkResults.add(result);
                }
              } catch (Exception e) {
                logger.error("An error occurred when running benchmark threaded: {}", generatorString, e);
              } catch (UnsupportedFileFormat e) {
                throw new RuntimeException(e);
              }
            });
            threads[i].start();
            threadStarted = true;
            break;
          }
        }
        if (!threadStarted) {
          for (int i = 0; i < benchmark.runs; i++) {
            long seed = seedGenerator.nextLong();
            BenchmarkResult result = RunBenchmark(generatorString, contextFactory, seed, i);
            benchmarkResults.add(result);
          }
        }
      }
    } catch (IOException e) {
      logger.error("Could not read the file: '{}'.", benchmark.generators);
      throw new RuntimeException("Could not read the file: '" + benchmark.generators + "': " + e.getMessage());
    } catch (DslException e) {
      logger.error("The following syntax error occurred when parsing: '{}'.{}Syntax Error: {}", benchmark.generators, System.lineSeparator(), e.getMessage());
      throw new RuntimeException("The following syntax error occurred when parsing: '" + benchmark.generators + "'." + System.lineSeparator() + "Syntax Error: " + e.getMessage());
    }

    for (Thread thread : threads) {
      if (thread != null && thread.isAlive()) {
        thread.join();
      }
    }

    if (benchmark.verbose) System.out.println("All benchmarks completed.");

    Path outputFolder;
    if (!benchmark.output.isEmpty()) {
      Path output = Paths.get(benchmark.output);
      outputFolder = output.toFile().isDirectory() ? output : output.getParent().resolve("benchmarks");
    } else {
      Path output = Paths.get(benchmark.generators);
      String folderName = output.getFileName().toString().replaceFirst("\\..*$", "");
      outputFolder = output.getParent().resolve(folderName);
    }

    if (benchmark.verbose) System.out.println("Will output benchmark results to " + outputFolder);

    if (!outputFolder.toFile().exists()) outputFolder.toFile().mkdirs();

    GsonBuilder gsonBuilder = new GsonBuilder();
    Gson gson = gsonBuilder.setPrettyPrinting().create();

    JsonObject reportJson = new JsonObject();
    reportJson.addProperty("BaseSeed", benchmark.seed);
    reportJson.addProperty("Runs", benchmark.runs);
    reportJson.addProperty("Generators", benchmark.generators);
    reportJson.addProperty("Model", benchmark.model);
    reportJson.addProperty("Unified", benchmark.unified);
    reportJson.addProperty("Verbose", benchmark.verbose);

    Map<String, List<BenchmarkResult>> groups = new HashMap<>();
    for (BenchmarkResult result : benchmarkResults) {
      if (!groups.containsKey(result.group)) {
        groups.put(result.group, new ArrayList<>());
      }
      groups.get(result.group).add(result);
    }

    for (String group : groups.keySet()) {
      JsonObject groupJson = new JsonObject();
      Path groupOutput = outputFolder.resolve("runs").resolve(group);
      if (!groupOutput.toFile().exists()) {
        groupOutput.toFile().mkdirs();
      }

      long totalGenerationTime = 0;
      long totalTestSuiteSize = 0;
      long minGenerationTime = Long.MAX_VALUE;
      long maxGenerationTime = Long.MIN_VALUE;
      long minTestSuiteSize = Long.MAX_VALUE;
      long maxTestSuiteSize = Long.MIN_VALUE;
      for (BenchmarkResult result : groups.get(group)) {
        JsonObject runJson = new JsonObject();

        Path runOutput = groupOutput.resolve("run_" + result.identifier + "_path.json");
        Path runReport = groupOutput.resolve("run_" + result.identifier + "_report.json");
        try (FileWriter fileWriter = new FileWriter(runOutput.toFile())) {
          fileWriter.write(result.path);
        }

        totalGenerationTime += result.generationTime;
        totalTestSuiteSize += result.testSuiteSize;
        minGenerationTime = Math.min(minGenerationTime, result.generationTime);
        maxGenerationTime = Math.max(maxGenerationTime, result.generationTime);
        minTestSuiteSize = Math.min(minTestSuiteSize, result.testSuiteSize);
        maxTestSuiteSize = Math.max(maxTestSuiteSize, result.testSuiteSize);
        runJson.addProperty("Seed", result.seed);
        runJson.addProperty("GenerationTime", result.generationTime);
        runJson.addProperty("TestSuiteSize", result.testSuiteSize);
        try (FileWriter fileWriter = new FileWriter(runReport.toFile())) {
          fileWriter.write(gson.toJson(runJson));
        }
      }
      groupJson.addProperty("TotalGenerationTime", totalGenerationTime);
      groupJson.addProperty("TotalTestSuiteSize", totalTestSuiteSize);
      groupJson.addProperty("AverageGenerationTime", totalGenerationTime / groups.get(group).size());
      groupJson.addProperty("AverageTestSuiteSize", totalTestSuiteSize / groups.get(group).size());
      groupJson.addProperty("MinGenerationTime", minGenerationTime);
      groupJson.addProperty("MaxGenerationTime", maxGenerationTime);
      groupJson.addProperty("MinTestSuiteSize", minTestSuiteSize);
      groupJson.addProperty("MaxTestSuiteSize", maxTestSuiteSize);
      reportJson.add(group, groupJson);
    }

    try (FileWriter fileWriter = new FileWriter(outputFolder.resolve("report.json").toFile())) {
      fileWriter.write(gson.toJson(reportJson));
    }
  }

  private BenchmarkResult RunBenchmark(String generatorString, ContextFactory contextFactory, long seed, int identifier) throws Exception, UnsupportedFileFormat {
    PathGenerator<StopCondition> generator = GeneratorFactory.parse(generatorString);
    if (benchmark.killAfter > 0) {
      AlternativeCondition newCondition = new AlternativeCondition();
      newCondition.addStopCondition(generator.getStopCondition());
      newCondition.addStopCondition(new TimeDuration(benchmark.killAfter, TimeUnit.SECONDS));
      generator.setStopCondition(newCondition);
    }

    try {
      List<Context> contexts = contextFactory.create(Paths.get(benchmark.model));
      BenchmarkResult result = GetBenchmarkedRun(identifier, generator.toString(), contexts, generator, seed);
      if (benchmark.verbose)
        System.out.println("- " + generatorString + ": Run " + (identifier + 1) + " of " + benchmark.runs + " completed: " + result.testSuiteSize + " elements visited in " + result.generationTime + " μs.");
      return result;
    } catch (DslException e) {
      throw new Exception("The following syntax error occurred when parsing: '" + benchmark.model + "'." + System.lineSeparator() + "Syntax Error: " + e.getMessage());
    }
  }

  private BenchmarkResult GetBenchmarkedRun(int identifier, String group, List<Context> contexts, PathGenerator<StopCondition> pathGenerator, long seed) throws Exception {
    TestExecutor executor;
    if (benchmark.unified) {
      executor = new UnifiedTestExecutor(contexts);
    } else {
      executor = new TestExecutor(contexts);
    }

    executor.getMachine().getCurrentContext().setPathGenerator(pathGenerator);

    StringBuilder path = new StringBuilder();
    executor.getMachine().addObserver((machine, element, type) -> {
      if (EventType.BEFORE_ELEMENT.equals(type)) {
        path.append(Util.getStepAsJSON(machine, benchmark.verbose, false));
      }
    });

    SingletonRandomGenerator.setSeed(seed);

    executor.execute();

    return new BenchmarkResult(identifier, group, path.toString(), executor.getMachine().getProfiler(), pathGenerator, seed);
  }

  private boolean checkStronglyConnected(List<Context> contexts) throws Exception {
    boolean[][] connected = new boolean[contexts.size()][contexts.size()];
    for (int i = 0; i < contexts.size(); i++) {
      for (int j = 0; j < contexts.size(); j++) {
        connected[i][j] = false;
      }
      connected[i][i] = true;
    }

    for (int i = 0; i < contexts.size(); i++) {
      for (int j = 0; j < contexts.size(); j++) {
        if (i != j) {
          if (connected[i][j]) {
            continue;
          }
          for (Vertex.RuntimeVertex vertexI : contexts.get(i).getModel().getVertices()) {
            for (Vertex.RuntimeVertex vertexJ : contexts.get(j).getModel().getVertices()) {
              if (vertexI.hasSharedState() && vertexJ.hasSharedState() && vertexI.getSharedState().equals(vertexJ.getSharedState())) {
                int iInEdges = contexts.get(i).getModel().getInEdges(vertexI).size();
                int iOutEdges = contexts.get(i).getModel().getOutEdges(vertexI).size();
                int jInEdges = contexts.get(j).getModel().getInEdges(vertexJ).size();
                int jOutEdges = contexts.get(j).getModel().getOutEdges(vertexJ).size();

                if (iInEdges > 0 && jOutEdges > 0) {
                  connected[i][j] = true;
                }
                if (jInEdges > 0 && iOutEdges > 0) {
                  connected[j][i] = true;
                }
              }
            }
          }
        }
      }
    }

    for (int i = 0; i < contexts.size(); i++) {
      for (int j = 0; j < contexts.size(); j++) {
        if (connected[i][j]) {
          for (int k = 0; k < contexts.size(); k++) {
            if (connected[j][k]) {
              connected[i][k] = true;
            }
          }
        }
      }
    }

    for (int i = 0; i < contexts.size(); i++) {
      for (int j = 0; j < contexts.size(); j++) {
        if (!connected[i][j]) {
          return false;
        }
      }
    }

    return true;
  }

  public List<Context> getContextsWithPathGenerators(Iterator itr) throws Exception, UnsupportedFileFormat {
    List<Context> executionContexts = new ArrayList<>();
    boolean triggerOnce = true;
    while (itr.hasNext()) {
      String modelFileName = (String) itr.next();
      ContextFactory factory = getContextFactory(modelFileName);
      List<Context> contexts;
      try {
        contexts = factory.create(Paths.get(modelFileName));
      } catch (DslException e) {
        throw new Exception("The following syntax error occurred when parsing: '" + modelFileName + "'." + System.lineSeparator() + "Syntax Error: " + e.getMessage());
      }

      // TODO fix all occurrences of get(0) is not safe
      contexts.get(0).setPathGenerator(GeneratorFactory.parse((String) itr.next()));

      if (triggerOnce && (!offline.startElement.isEmpty() || !online.startElement.isEmpty())) {
        triggerOnce = false;

        List<Element> elements = null;
        if (command == Command.OFFLINE) {
          elements = contexts.get(0).getModel().findElements(offline.startElement);
        } else if (command == Command.ONLINE) {
          elements = contexts.get(0).getModel().findElements(online.startElement);
        }

        if (elements == null) {
          throw new ParameterException("--start-element Did not find matching element in the model: '" + modelFileName + "'.");
        } else if (elements.size() > 1) {
          throw new ParameterException("--start-element There are more than one matching element in the model: '" + modelFileName + "'.");
        }
        contexts.get(0).setNextElement(elements.get(0));
      }

      executionContexts.addAll(contexts);
    }
    return executionContexts;
  }

  private ContextFactory getContextFactory(String modelFileName) throws UnsupportedFileFormat {
    ContextFactory factory;
    if (new YEdContextFactory().accept(Paths.get(modelFileName))) {
      factory = new YEdContextFactory();
    } else if (new JsonContextFactory().accept(Paths.get(modelFileName))) {
      factory = new JsonContextFactory();
    } else if (new DotContextFactory().accept(Paths.get(modelFileName))) {
      factory = new DotContextFactory();
    } else if (new JavaContextFactory().accept(Paths.get(modelFileName))) {
      factory = new JavaContextFactory();
    } else {
      throw new UnsupportedFileFormat(modelFileName);
    }
    return factory;
  }

  private List<Context> getContexts(Iterator itr) throws Exception, UnsupportedFileFormat {
    List<Context> executionContexts = new ArrayList<>();
    while (itr.hasNext()) {
      String modelFileName = (String) itr.next();
      ContextFactory factory = getContextFactory(modelFileName);
      List<Context> contexts;
      try {
        contexts = factory.create(Paths.get(modelFileName));
      } catch (DslException e) {
        throw new Exception("The following syntax error occurred when parsing: '" + modelFileName + "'." + System.lineSeparator() + "Syntax Error: " + e.getMessage());
      }
      executionContexts.addAll(contexts);
    }
    return executionContexts;
  }

  enum Command {
    NONE, OFFLINE, ONLINE, METHODS, REQUIREMENTS, CONVERT, SOURCE, CHECK, UNIFY, BENCHMARK
  }
}
