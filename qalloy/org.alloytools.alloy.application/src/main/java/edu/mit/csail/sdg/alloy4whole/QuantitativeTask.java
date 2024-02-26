package edu.mit.csail.sdg.alloy4whole;

import edu.mit.csail.sdg.alloy4.*;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.TranslateQTAlloyToKodkod;

import java.io.File;
import java.util.*;

/**
 * Task that handles one or more quantitative solutions.
 * Adaptation of {@link edu.mit.csail.sdg.alloy4whole.SimpleReporter.SimpleTask1} and
 *               {@link edu.mit.csail.sdg.alloy4whole.SimpleReporter.SimpleTask2}.
 */
public class QuantitativeTask implements WorkerEngine.WorkerTask {

    private WorkerEngine.WorkerCallback wcb;
    private A4Options options;
    private int bundleIndex;
    private boolean            bundleWarningNonFatal;
    private int resolutionMode;
    private Map<String, String> map;
    public String             tempdir;
    public final String solverBinary;

    private static A4Solution currentSol = null;
    private static Module currentModule = null;
    private static ConstMap<String,String> latestKodkodSRC    = null;
    private static String                  latestKodkodXML    = null;
    //private static final Set<String>       latestKodkods      = new LinkedHashSet<String>();

    /* Set Commands that obtained the UNKOWN response. */
    private Set<String> unknownCmds;
    /* For each command, saves the time it took for the solver to terminate. */
    private Map<Command, Long> solvingTimes;

    public String filename;

    public QuantitativeTask(A4Options options, int bundleIndex, int resolutionMode, Map<String, String> map, String tmp, String solverLocation, boolean bundleWarningNonFatal){
        this.options = options;
        this.bundleIndex = bundleIndex;
        this.resolutionMode = resolutionMode;
        this.map = map;
        this.tempdir = tmp;
        this.filename = null;
        this.solverBinary = solverLocation;
        this.unknownCmds = new TreeSet<>();
        this.solvingTimes = new HashMap<>();
        this.bundleWarningNonFatal = bundleWarningNonFatal;
    }

    /* Common callbacks */
    private void cb(String msg) { wcb.callback(new Object[]{"", msg}); }
    private void cbBold(String msg) { wcb.callback(new Object[]{"S2", msg}); }
    private void cbDeclare(String f) { wcb.callback(new Object[]{"declare", f}); }
    private void cbLink(String msg, String link) { wcb.callback(new Object[]{"link", msg, link}); }
    private void cbKeyValue(String key, String value){ cbBold(key); cb(value);  }
    private void cbPop(String msg) { wcb.callback(new Object[]{"pop", msg}); }
    private void cbWarning(ErrorWarning e) { wcb.callback(new Object[]{"warning", e}); }
    private void cbWarnings() { wcb.callback(new Object[]{"warnings", bundleWarningNonFatal}); }

    /**
     * Logs the result of a single command.
     */
    private void result(Command r, String result, Long time){
        cb("  ");
        if (result.endsWith(".xml")) { // SAT
            cbLink(r.check ? "Counterexample found. " : "Instance found. ", "XML: " + result);
            cb(r.label + (r.check ? " is invalid." : " is consistent."));
        }
        else if(unknownCmds.contains(r.toString())) { // UNKNOWN
            cb("The solver was unable to determine the satisfiability of " + r.label + ",\n  delivering the");
            cbBold(" unknown");
            cb(" response.");
        }
        else{ // UNSAT

            //CHECK
            if (r.check)
                cb("No counterexample found. " + r.label + " may be valid.");
            else
                cb("No instance found. " + r.label + " may be inconsistent.");
        }
        cb(" " + solvingTimes.getOrDefault(r, 0L) + "ms.\n");
        if(time != null) cbKeyValue("  Elapsed time: ", + time + "ms.\n");
    }

    @Override
    public void run(WorkerEngine.WorkerCallback out) throws Exception {
        this.wcb = out;
        // 1st solution already seen => EnumerationTask
        if(filename != null) {
            new QTEnumerationTask().run(out);
            return;
        }
        //else deal with a single command

        this.unknownCmds = new TreeSet<>();
        this.solvingTimes = new HashMap<>();
        // Reporter to log the interesting solving details and metrics along with the flow of this task.
        A4QtReporter rep = new A4QtReporter(){
            private final String INITIAL_SPACE = "  ";

            /**
             * {@inheritDoc}
             */
            @Override
            public void translate(String solver, String context, Integer maxWeight) {
                boolean isFuzzy = options.analysisType.equals("Fuzzy");
                cb(INITIAL_SPACE + "Solver=" +  solver + " " + options.analysisType);
                if(isFuzzy)
                    cb(" T-norm = " + options.tnorm + "\n");
                else cb("\n");
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void smt2(int primaryVar, int fs, int assertions, long translationTime) {
                cb(INITIAL_SPACE +
                        primaryVar + " primary variables, " +
                        fs + " function symbols, " +
                        assertions + " assertions. " +
                        translationTime + "ms.\n");
            }

            /**
             * Helper method to handle a given command and its solving time for some result.
             * @return command is a valid Command object
             */
            private boolean result(Object command, long solvingTime) {
                boolean validCommand;
                if((validCommand = command instanceof Command))
                    solvingTimes.put((Command)command, solvingTime);
                return validCommand;
            }

            /**
             * Called to report the SAT outcome obtained when solving the quantitative problem at hand.
             *
             * @param command Original command
             * @param solvingTime The time it took the solver to terminate, in milliseconds.
             * @param solution The resulting A4Solution object for this problem.
             */
            @Override
            public void resultSAT(Object command, long solvingTime, Object solution) {
                result(command, solvingTime);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void resultUNKNOWN(Object command, long solvingTime, Object solution) {
                if(result(command, solvingTime))
                    unknownCmds.add(((Command)command).toString());
            }

            /**
             * Called to report the UNSAT outcome obtained when solving the quantitative problem at hand.
             *
             * @param command Original command
             * @param solvingTime The time it took the solver to terminate, in milliseconds.
             * @param solution The resulting A4Solution object for this problem.
             */
            @Override
            public void resultUNSAT(Object command, long solvingTime, Object solution) {
                result(command, solvingTime);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void warning(ErrorWarning msg) {
                this.newWarning();
                cbWarning(msg);
            }
        };

        final Module world = CompUtil.parseEverything_fromFile(rep, map, options.originalFilename, resolutionMode);
        // T-norm imposed in the .als has priority
        if(!((CompModule)world).tnorm.equals("")){
            options.tnorm = ((CompModule)world).tnorm;
        }
        final ConstList<Command> cmds = world.getAllCommands();

        cbWarnings();
        if(rep.getNumberOfWarnings() > 0 && !bundleWarningNonFatal)
            return;

        List<String> result = new ArrayList<String>(cmds.size());
        Map<Integer, Long> elapsedTimes = new HashMap<>();
        for (int i = 0; i < cmds.size(); i++)
            if (bundleIndex < 0 || i == bundleIndex) {
                synchronized (SimpleReporter.class) {
                    currentModule = world;
                    latestKodkodSRC = ConstMap.make(map);
                }

                final Command cmd = cmds.get(i);
                cbBold("Executing \"" + cmd + "\"\n");

                final String tempXML = tempdir + File.separatorChar + i + ".smt2.xml";
                /* Solve */
                long time = System.currentTimeMillis();
                A4Solution sol = TranslateQTAlloyToKodkod.execute_command(rep, world.getAllReachableSigs(), cmd, options, solverBinary);
                time = System.currentTimeMillis() - time;
                elapsedTimes.put(i, time);

                // SAT
                if (sol.satisfiable()) {
                    sol.writeXML(tempXML, world.getAllFunc(), latestKodkodSRC);
                    cbDeclare(tempXML);
                    synchronized (SimpleReporter.class) {
                        currentSol = sol;
                        latestKodkodXML = tempXML;
                        /*latestKodkods.clear();
                        latestKodkods.add(sol.toString());*/
                    }
                    result.add(tempXML);
                }
                else{ // UNKNOWN or UNSAT
                    if(sol.unknown()) // UNKNOWN
                        unknownCmds.add(cmd.toString());
                    result.add("");
                }
            }

        (new File(tempdir)).delete(); // In case it was UNSAT, or
        // canceled...

        //A single command was executed
        if(result.size() == 1){
            result(world.getAllCommands().get(bundleIndex), result.get(0), elapsedTimes.get(bundleIndex));
        }
        else if (result.size() > 1) {
            cbBold(result.size() + " commands were executed. The results are:\n");

            for (int i = 0; i < result.size(); i++) {
                Command r = world.getAllCommands().get(i);
                if (result.get(i) == null) {
                    cb("   #" + (i + 1) + ": Unknown command.\n");
                    continue;
                }
                cb("   #" + (i + 1) + ": ");
                result(r, result.get(i), null);
            }
           cbKeyValue("Elapsed time: ", elapsedTimes.values().stream().mapToLong(Long::longValue).sum() + "ms.\n");
        }

        if (rep.getNumberOfWarnings() > 1)
            cbBold("Note: There were " + rep.getNumberOfWarnings() + " compilation warnings. Please scroll up to see them.\n");
        if (rep.getNumberOfWarnings() == 1)
            cbBold("Note: There was 1 compilation warning. Please scroll up to see it.\n");
    }

    /**
     * Task responsible for quantitative enumeration
     */
    private class QTEnumerationTask implements WorkerEngine.WorkerTask{

        @Override
        public void run(WorkerEngine.WorkerCallback out) throws Exception {
            cbBold("Enumerating...\n");
            A4Solution sol;
            Module mod;
            synchronized (SimpleReporter.class) {
                if (latestKodkodXML == null || !latestKodkodXML.equals(filename)) {
                    cbPop( "You can only enumerate the solutions of the most-recently-solved command.");
                    return;
                }
                if (currentSol == null || currentModule == null || latestKodkodSRC == null) {
                    cbPop("Error: the solver that generated the instance has exited,\nso we cannot enumerate unless you re-solve that command.\n");
                    return;
                }
                sol = currentSol;
                mod = currentModule;
            }
            if (!sol.satisfiable()) {
                cbPop("Error: This command is unsatisfiable,\nso there are no solutions to enumerate.");
                return;
            }
            //int tries = 0;
            //while (true) {
                sol = sol.next();
                if (!sol.satisfiable()) {
                    if(sol.unknown()) //UNKNOWN
                        cbPop("The SMT solver was unable to find the satisfiability of other instances\n" +
                                "and terminated with UNKNOWN judgement.\n\n" +
                                "There may or may not be further instances.");

                    else cbPop("There are no more satisfying instances.\n\n" +  // UNSAT
                            "Note: due to symmetry breaking and other optimizations,\n" +
                            "some equivalent solutions may have been omitted.");
                    return;
                }

                synchronized (SimpleReporter.class) {
                    /*if (!latestKodkods.add(sol.toString()))
                        if (tries < 100) {
                            tries++;
                            continue;
                        }*/
                    // The counter is needed to avoid a Kodkod bug where
                    // sometimes we might repeat the same solution infinitely
                    // number of times; this at least allows the user to keep
                    // going

                    sol.writeXML(filename, mod.getAllFunc(), latestKodkodSRC);
                    currentSol = sol;
                }
                cbDeclare(filename);
                return;
            }
        //}
    }
}

