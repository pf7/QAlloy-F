package edu.mit.csail.sdg.alloy4whole;

import edu.mit.csail.sdg.alloy4.A4QtReporter;
import edu.mit.csail.sdg.alloy4.ConstList;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.TranslateQTAlloyToKodkod;

import java.io.*;
import java.util.*;

import static edu.mit.csail.sdg.alloy4whole.SimpleGUI.alloyHome;

public class Benchmark {

    // Command being currently solved
    public static volatile Command current;
    // n is the number of times that the current command has been solved
    public static int n;
    // Which command within the current model is being benchmarked
    public static int i;
    // Indicate if the 'n' run of the current command 'i' finished in time, or if it timed out.
    public static boolean wasTimeout;

    // Helper method to determine the machine OS
    private static String getOS(){
        String os = "linux";
        if(Util.onMac())
            os = "mac";
        else if(Util.onWindows())
            os = "windows";

        return os;
    }

    // Directory where the benchmark will be written
    private static final String benchmark = "." + File.separatorChar + "results";

    /**
     * Writes the results of solving the specified command according to the
     * specific model using the solver provided.
     */
    private static void printResult(String file, String solver, String tnorm, String cmd, List<Long> times) throws IOException {
        File f = new File(benchmark + File.separatorChar + file + ".csv");
        // If the file already exists, append the new results.
        boolean exists = f.isFile();
        PrintWriter res = new PrintWriter(new FileOutputStream(
                f,
                exists));

        // <Command, Solver, T-norm, Response Time> columns
        if(!exists) res.append("Command, Solver, T-norm, Response Time(ms)\n");

        for(Long t : times)
            res.append(cmd).append(", ")
                    .append(solver).append(", ")
                    .append(tnorm).append(", ")
                    .append(t == null ? "timeout" : String.valueOf(t)).append("\n");
        /*{
            res.append(cmd).append(", ").append(t == null || t > 5000 ? "timeout" : String.valueOf(t)).append("\n");
        }*/

        res.flush();
        res.close();
    }

    private static void doSolve(String example, String als, String solver, String solverBinary, A4Options options, int N, int timeout, A4QtReporter rep) throws IOException{
        System.out.println("Example = " + example);
        Module world = CompUtil.parseEverything_fromFile(rep, null, als + File.separatorChar + example, 1);
        ConstList<Command> cmds = world.getAllCommands();
        // <Command, <Response Times>>
        Map<Integer, List<Long>> times = new HashMap<>();
        i = 0;
        wasTimeout = false;

        // Task for SMT Solvers
        Runnable solveSMT = () -> {
            long time = System.currentTimeMillis();
            TranslateQTAlloyToKodkod.execute_command(rep, world.getAllReachableSigs(), current, options, solverBinary + File.separatorChar + solver + "_" + getOS());
            time = System.currentTimeMillis() - time;
            if(!wasTimeout)
                times.get(i).add(time);
        };


        Thread worker;
        for (; i < cmds.size(); i++) {
            current = cmds.get(i);
            times.put(i, new ArrayList<>());
            wasTimeout = false;
            for (n = 0; n < N && !wasTimeout; n++) {
                worker = new Thread(solveSMT);
                // Execute the solving process
                System.out.println(current + "#" + n);
                worker.start();
                try {
                    // wait for the timeout
                    worker.join(timeout);
                    if (worker.isAlive()) {
                        worker.interrupt();
                        wasTimeout = true;
                        times.get(i).add(null);
                        //System.out.println("Interrupted command " + i + ", run " + n);
                        killSMTSolver(solverBinary + File.separatorChar + solver);
                    }
                    // ensure that the threads don't overlap
                    worker.join();
                } catch (InterruptedException e) {
                    // TODO
                    System.out.println("Interrupted.");
                }
            }
        }

        // Write the results to .CSV
        String csvName = example.substring(0, example.length() - 4);
        for(Integer c : times.keySet())
            printResult(csvName, solver, options.tnorm, cmds.get(c).toString().replaceAll(",", " / "), times.get(c));
        System.out.println("The response times of " + example + "[" + solver + "/" + options.tnorm + "] have been written to " + csvName + ".csv");
    }

    /**
     * @param args .als files to be considered for benchmark, assumed to be located in the same directory.
     */
    public static void main(String[] args) throws IOException {
        // Number of runs per command
        int N = 10;
        // Timeout considered (in ms)
        int timeout = 600000;
        // als idx
        int idx = 0;

        // Check if the number of runs/cmd & timeout is the default or was provided by the user
        if(args.length > 1 && isInt(args[0])){
            N = Integer.parseInt(args[0]);
            if(args.length > 2 && isInt(args[1])){
                timeout = Integer.parseInt(args[1]);
                idx = 2;
            }else idx = 1;
        }

        // QAlloy models
        String[] alsModels = Arrays.copyOfRange(args, idx, args.length);

        // Solvers being evaluated
        final String[] solvers = { "z3", "mathsat", "cvc4", "yices" };
        final Map<String, String> solverOpt = new HashMap<>();
        solverOpt.put("mathsat", "MathSAT");
        solverOpt.put("z3", "Z3");
        solverOpt.put("cvc4", "CVC4");
        solverOpt.put("yices", "Yices");
        // T-norms to be considered
        final String[] tnorms = { "Godelian", "Lukasiewicz", "Product" };

        // Directory of the QAlloy models
        String model = "."; // + File.separatorChar + "models" + File.separatorChar + "qalloy";
        String solverBinary = alloyHome() + File.separatorChar + "binary";

        // Create the benchmark directory if it does not exist
        File directory = new File(benchmark);
        if (! directory.exists()){
            directory.mkdirs();
        }

        // Copy the binaries
        directory = new File(alloyHome() + File.separatorChar + "binary");
        if(! directory.exists())
            copyFromJAR();

        A4QtReporter rep = new A4QtReporter();
        A4Options options = new A4Options();
        //options.analysisType = "Fuzzy";

        // -------------------------------------------------------------------------------------------------------------------------------------------------
        // Benchmark the quantitative models
        System.out.println("Running " + N + " times per cmd/solver/tnorm with a timeout of " + timeout + " ms for the " + alsModels.length + " .als models specified.");

        for (String alsM : alsModels){
            String als = alsM.contains(".als") ? alsM : alsM + ".als";
            for (String solver : solvers) {
                System.out.println("Solver = " + solver);
                options.quantitativeSolver = solverOpt.get(solver);
                for (String tnorm : tnorms) {
                    options.tnorm = tnorm;
                    doSolve(als, model, solver, solverBinary, options, N, timeout, rep);
                }
            }}

        System.out.println("Finished.");
    }

    /**
     * Helper method to stop the SMT solver midway, after the user stops the run/check cmd.
     * TODO: for non-unix systems
     */
    private static void killSMTSolver(String solverBinary) {
        if(!Util.onWindows()) {
            List<String> cmd = new ArrayList<>();
            cmd.add("/bin/sh");
            cmd.add("-c");
            cmd.add("ps -h | grep " + solverBinary);
            ProcessBuilder psBuild = new ProcessBuilder(cmd);

            // Find the PID of the active SMT Solver
            try {
                Process psGrep = psBuild.start();
                BufferedReader inP = new BufferedReader(new InputStreamReader(psGrep.getInputStream()));

                String line = inP.readLine();

                boolean foundPID = false;
                StringBuilder pid = new StringBuilder("");
                for (int i = 0, n = line.length(); i < n && !foundPID; i++) {
                    char c = line.charAt(i);
                    if (Character.isDigit(c))
                        pid.append(c);
                    else if (!pid.toString().equals(""))
                        foundPID = true;
                }
                inP.close();

                // Kill solver
                new ProcessBuilder("/bin/sh", "-c", "kill -9 " + pid).start();
                System.out.println("Killed the solver with PID = " + pid);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        } else System.out.println("Unable to terminate the current SMT solver.");
    }

    // TODO: Setup solver binaries
    private static void copyFromJAR() {
        // Compute the appropriate platform
        String os = System.getProperty("os.name").toLowerCase(Locale.US).replace(' ', '-');
        if (os.startsWith("mac-"))
            os = "mac";
        else if (os.startsWith("windows-"))
            os = "windows";
        String arch = System.getProperty("os.arch").toLowerCase(Locale.US).replace(' ', '-');
        if (arch.equals("powerpc"))
            arch = "ppc-" + os;
        else
            arch = arch.replaceAll("\\Ai[3456]86\\z", "x86") + "-" + os;
        if (os.equals("mac"))
            arch = "x86-mac"; // our pre-compiled binaries are all universal
        // binaries
        // Find out the appropriate Alloy directory
        final String platformBinary = alloyHome() + File.separatorChar + "binary";
        // Write a few test files
        try {
            (new File(platformBinary)).mkdirs();
            Util.writeAll(platformBinary + File.separatorChar  + "tmp.cnf", "p cnf 3 1\n1 0\n");
        } catch (Err er) {
            // The error will be caught later by the "berkmin" or "spear" test
        }
        // Copy the platform-dependent binaries
        Util.copy(true, false, platformBinary, arch + "/libminisat.so", arch + "/libminisatx1.so", arch + "/libminisat.jnilib", arch + "/libminisat.dylib", arch + "/libminisatprover.so", arch + "/libminisatproverx1.so", arch + "/libminisatprover.jnilib", arch + "/libminisatprover.dylib", arch + "/libzchaff.so", arch + "/libzchaffmincost.so", arch + "/libzchaffx1.so", arch + "/libzchaff.jnilib", arch + "/liblingeling.so", arch + "/liblingeling.dylib", arch + "/liblingeling.jnilib", arch + "/plingeling", arch + "/libglucose.so", arch + "/libglucose.dylib", arch + "/libglucose.jnilib", arch + "/libcryptominisat.so", arch + "/libcryptominisat.la", arch + "/libcryptominisat.dylib", arch + "/libcryptominisat.jnilib", arch + "/berkmin", arch + "/spear", arch + "/cryptominisat");
        Util.copy(false, false, platformBinary, arch + "/minisat.dll", arch + "/cygminisat.dll", arch + "/libminisat.dll.a", arch + "/minisatprover.dll", arch + "/cygminisatprover.dll", arch + "/libminisatprover.dll.a", arch + "/glucose.dll", arch + "/cygglucose.dll", arch + "/libglucose.dll.a", arch + "/zchaff.dll", arch + "/berkmin.exe", arch + "/spear.exe");

        // Supported SMT Solvers binaries
        for(String smt : Arrays.asList("cvc4", "z3", "mathsat", "yices")){
            Util.copy(true, false, platformBinary, smt + File.separatorChar   + smt + "_" + os);
            Util.copy(true, false, platformBinary, smt + "/" + smt + "_" + os);
        }
        // SMT Solvers JNI, dll, ...
        Util.copy(true, false, platformBinary, arch + "/libcvc4jni.so", arch + "/libcvc4jni.jnilib", arch + "/libz3.dylib", arch + "/libz3java.dylib", arch + "/libz3.so", arch + "/libz3java.so");
        // ensure that windows dlls are copied
        if(arch.contains("windows"))
            arch = "x86-windows";
        Util.copy(false, false, platformBinary, arch + "/libcvc4.dll.a", arch + "/libcvc4.a", arch + "/cvc4.exe", arch + "/libz3.dll", arch + "/libz3java.dll", arch + "/mathsat.dll", arch + "/mpir.dll", arch + "/libyices.dll");

        // Copy the model files
        Util.copy(false, true, alloyHome(), "models/book/appendixA/addressBook1.als", "models/book/appendixA/addressBook2.als", "models/book/appendixA/barbers.als", "models/book/appendixA/closure.als", "models/book/appendixA/distribution.als", "models/book/appendixA/phones.als", "models/book/appendixA/prison.als", "models/book/appendixA/properties.als", "models/book/appendixA/ring.als", "models/book/appendixA/spanning.als", "models/book/appendixA/tree.als", "models/book/appendixA/tube.als", "models/book/appendixA/undirected.als", "models/book/appendixE/hotel.thm", "models/book/appendixE/p300-hotel.als", "models/book/appendixE/p303-hotel.als", "models/book/appendixE/p306-hotel.als", "models/book/chapter2/addressBook1a.als", "models/book/chapter2/addressBook1b.als", "models/book/chapter2/addressBook1c.als", "models/book/chapter2/addressBook1d.als", "models/book/chapter2/addressBook1e.als", "models/book/chapter2/addressBook1f.als", "models/book/chapter2/addressBook1g.als", "models/book/chapter2/addressBook1h.als", "models/book/chapter2/addressBook2a.als", "models/book/chapter2/addressBook2b.als", "models/book/chapter2/addressBook2c.als", "models/book/chapter2/addressBook2d.als", "models/book/chapter2/addressBook2e.als", "models/book/chapter2/addressBook3a.als", "models/book/chapter2/addressBook3b.als", "models/book/chapter2/addressBook3c.als", "models/book/chapter2/addressBook3d.als", "models/book/chapter2/theme.thm", "models/book/chapter4/filesystem.als", "models/book/chapter4/grandpa1.als", "models/book/chapter4/grandpa2.als", "models/book/chapter4/grandpa3.als", "models/book/chapter4/lights.als", "models/book/chapter5/addressBook.als", "models/book/chapter5/lists.als", "models/book/chapter5/sets1.als", "models/book/chapter5/sets2.als", "models/book/chapter6/hotel.thm", "models/book/chapter6/hotel1.als", "models/book/chapter6/hotel2.als", "models/book/chapter6/hotel3.als", "models/book/chapter6/hotel4.als", "models/book/chapter6/mediaAssets.als", "models/book/chapter6/memory/abstractMemory.als", "models/book/chapter6/memory/cacheMemory.als", "models/book/chapter6/memory/checkCache.als", "models/book/chapter6/memory/checkFixedSize.als", "models/book/chapter6/memory/fixedSizeMemory.als", "models/book/chapter6/memory/fixedSizeMemory_H.als", "models/book/chapter6/ringElection.thm", "models/book/chapter6/ringElection1.als", "models/book/chapter6/ringElection2.als", "models/examples/algorithms/dijkstra.als", "models/examples/algorithms/dijkstra.thm", "models/examples/algorithms/messaging.als", "models/examples/algorithms/messaging.thm", "models/examples/algorithms/opt_spantree.als", "models/examples/algorithms/opt_spantree.thm", "models/examples/algorithms/peterson.als", "models/examples/algorithms/ringlead.als", "models/examples/algorithms/ringlead.thm", "models/examples/algorithms/s_ringlead.als", "models/examples/algorithms/stable_mutex_ring.als", "models/examples/algorithms/stable_mutex_ring.thm", "models/examples/algorithms/stable_orient_ring.als", "models/examples/algorithms/stable_orient_ring.thm", "models/examples/algorithms/stable_ringlead.als", "models/examples/algorithms/stable_ringlead.thm", "models/examples/case_studies/INSLabel.als", "models/examples/case_studies/chord.als", "models/examples/case_studies/chord2.als", "models/examples/case_studies/chordbugmodel.als", "models/examples/case_studies/com.als", "models/examples/case_studies/firewire.als", "models/examples/case_studies/firewire.thm", "models/examples/case_studies/ins.als", "models/examples/case_studies/iolus.als", "models/examples/case_studies/sync.als", "models/examples/case_studies/syncimpl.als", "models/examples/puzzles/farmer.als", "models/examples/puzzles/farmer.thm", "models/examples/puzzles/handshake.als", "models/examples/puzzles/handshake.thm", "models/examples/puzzles/hanoi.als", "models/examples/puzzles/hanoi.thm", "models/examples/systems/file_system.als", "models/examples/systems/file_system.thm", "models/examples/systems/javatypes_soundness.als", "models/examples/systems/lists.als", "models/examples/systems/lists.thm", "models/examples/systems/marksweepgc.als", "models/examples/systems/views.als", "models/examples/toys/birthday.als", "models/examples/toys/birthday.thm", "models/examples/toys/ceilingsAndFloors.als", "models/examples/toys/ceilingsAndFloors.thm", "models/examples/toys/genealogy.als", "models/examples/toys/genealogy.thm", "models/examples/toys/grandpa.als", "models/examples/toys/grandpa.thm", "models/examples/toys/javatypes.als", "models/examples/toys/life.als", "models/examples/toys/life.thm", "models/examples/toys/numbering.als", "models/examples/toys/railway.als", "models/examples/toys/railway.thm", "models/examples/toys/trivial.als", "models/examples/tutorial/farmer.als", "models/util/boolean.als", "models/util/graph.als", "models/util/integer.als", "models/util/natural.als", "models/util/ordering.als", "models/util/relation.als", "models/util/seqrel.als", "models/util/sequence.als", "models/util/sequniv.als", "models/util/ternary.als", "models/util/time.als", "models/util/linear.als", "models/util/mf.als");
        // Record the locations
        System.setProperty("alloy.theme0", alloyHome() + File.separatorChar + "models");
        System.setProperty("alloy.home", alloyHome());
    }

    public static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }
}
