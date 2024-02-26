package kodkod.engine.num2smt;

import kodkod.engine.num2common.InstanceNotFoundException;
import kodkod.engine.num2common.VariableNotFoundException;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents an instance of a SMT solver that abides to the SMT-LIB standard.
 *
 * This implementation assumes that *at most*, only one instance of SMT2Solver is active at any point in time.
 * Preferably, the full path to the SMT2Solver binary {@code binary} should always be specified.
 *
 * The smt-lib specification fed into this solver should be produced by {@link Num2smtTranslator} or abide to the same
 * characteristics. Otherwise, the behaviour of this solver is undefined.
 *
 * If the solver at hand does not function on incremental mode, the specification will be written to a temporary
 * 'tmp.smt2' file, within the working directory, which will be fed to the solver in full.
 *
 * @specfield smt2 : SMTSpecification
 * @specfield binary : String
 */
abstract public class SMT2Solver implements SMTSolver {

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SOLVER
     * -----------------------------------------------------------------------------------------------------------------
     */

    // Specifies the SMT Solver binary location
    private static String binary = null;
    // Specifies the file to log errors
    private static String err = null;
    // Responsible for producing solver instances
    private static ProcessBuilder builder = null;
    // Represents the active solver instances at a given moment
    private static Process instance = null;
    // Reads the stdout of instance
    private static BufferedReader reader = null;
    // Writes to the stdin of instance
    private static BufferedWriter writer = null;

    /**
     * @return the name of the SMT Solver associated with this instance.
     */
    abstract public String getSolver();

    /**
     * Attempt to find the binary location, in case it was not provided.
     */
    abstract public String defaultBinaryLocation();

    /**
     * Obtain the command options according to the SMT solver at and and the solving options
     * defined by the user.
     * @param incremental requests the command options of the solver make the latter run in incremental mode,
     *                    in case it is supported, if true.
     */
    abstract protected List<String> getCommand(boolean incremental);

    /**
     * The SMT solver must provide the SMT specification produced by a {@link Num2smtTranslator} adapted to abide
     * to the solving options specified, as well as other solver specific assertions/commands/options.
     *
     * Solvers that *DO NOT* support the incremental solving flow implemented must:
     * - include the commands to check the satisfiability and request the solution
     * - update the specification to eliminate previous solutions
     * @return smt2
     */
    abstract public String getSMTSpecification();

    /**
     * Writes the SMT specification into the 'tmp.smt2' file within the working directory.
     * @throws FileNotFoundException if it was unable to write the SMT specification into the tmp.smt2 file.
     */
    private void writeSMT2() throws FileNotFoundException{
        PrintWriter file = new PrintWriter(new FileOutputStream(
                new File("./tmp.smt2")));
        file.write(getSMTSpecification());
        file.flush();
        file.close();
    }

    /**
     * Start the solver instance builder.
     */
    private void startBuilder(String path){
        binary = path;
        List<String> cmd = getCommand(smt2.incremental());
        cmd.add(0, binary);
        // If the solver is not functioning in incremental mode, it will be fed the temporary .smt2 file for solving
        if(!smt2.incremental())
            cmd.add("tmp.smt2");
        builder = new ProcessBuilder(cmd);
        if (err != null)
            builder.redirectError(new File(err));
    }

    /**
     * Specifies the location of the solver binary.
     * If the given path does not represent a file, does nothing.
     */
    @Override
    public void setBinaryLocation(String path){
        if(path != null && (new File(binary).isFile())) {
            this.resetSolver();
            startBuilder(path);
        }
    }

    /**
     * Sets the path to the file where the stderr of the solver instances will be redirected
     */
    private void setErrFile(String path){
        if(path != null && (new File(binary).isFile())) {
            err = path;
            if(builder != null)
                builder.redirectError(new File(err));
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SPECIFICATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    //SMT-LIB specification at hand and its characteristics
    private final SMTSpecification smt2;

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SOLUTION
     * -----------------------------------------------------------------------------------------------------------------
     */

    // Specifies if the 'solve' method was called previously.
    private boolean solved;
    // if solved is true, then result contains the SAT/UNSAT/UNKNOWN judgement for that instance
    private SMTResult result;
    // Contains the values assigned to the numeric function symbols in the most recent instance found
    private Map<Integer, Number> solNumFS;
    // If the value assigned to a given fs comes as a fraction, while the decimal representation is stored
    // in {@code solNumFS}, solFractions saves the original fraction in SMT syntax (/ num denom)
    private Map<Integer, String> solFractions;
    // Contains the values assigned to the boolean function symbols in the most recent instance found
    private Map<Integer, Boolean> solBoolFS;
    // Parsing rule to extract the value assigned to one function symbol
    private final Pattern fsModelFormat = Pattern.compile("[(]define-fun (?<id>.*?) [(][)] (?<type>Bool|Int|Real) (?<value>.*?)[)]");
    // Regex to deconstruct the numerator/denominator of a fraction represented in the SMT-LIB language
    private final Pattern fraction = Pattern.compile("(/ (?<num>(-?)[0-9]+([.][0-9]+)?) (?<denom>(-?)[0-9]+([.][0-9]+)?))");
    // Used to find out if the string that did not match {@code fsModelFormat} is the beginning of a function symbol
    private final Pattern broken = Pattern.compile("[(]define-fun.*");

    /**
     * Creates a new instance of a SMT Solver.
     * @param smt2 SMT-LIB specification to be solved
     * @param location binary location
     */
    public SMT2Solver(SMTSpecification smt2, String location){
        this.smt2 = smt2;

        if(instance != null)
            resetSolver();
        if(location != null && !location.equals(binary)){
            binary = location;
            startBuilder(binary);
        }

        this.solved = false;
        this.result = null;
        this.solNumFS = new HashMap<>();
        this.solFractions = new HashMap<>();
        this.solBoolFS = new HashMap<>();
    }

    /**
     * Creates a new instance of a SMT Solver.
     * @param smt2 SMT-LIB specification to be solved
     */
    public SMT2Solver(SMTSpecification smt2){
        this(smt2, smt2.options().getBinaryLocation());
    }

    /**
     * Creates an instance of a SMT Solver over a trivial solution.
     * @param smt2 SMT-LIB specification
     * @param trivialResult true => TRIVIALLY_SAT, TRIVIALLY_UNSAT
     */
    SMT2Solver(SMTSpecification smt2, boolean trivialResult){
        this(smt2, null);
        this.solved = true;
        this.result = trivialResult ? SMTResult.SAT : SMTResult.UNSAT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean solve() {
        //Trivial solutions need no further solving
        if(smt2.trivial())
            return result.isSat();

        if(builder == null)
            startBuilder(defaultBinaryLocation());
        if(builder == null)
            throw new NullPointerException(getSolver() + " is not located at the expected location. Solving cannot continue.");

        try {
            //Create a new instance of the SMT Solver and feed it the current state of the assertion stack
            //in case it isn't created/alive or in incremental solving mode.
            if(instance == null || !instance.isAlive() || !smt2.incremental()) {
                // Update the temporary SMT2 file to the most recent state of the specification for single execution
                if(!smt2.incremental())
                    writeSMT2();

                // Create the solver instance
                instance = builder.start();

                if(reader != null)
                    reader.close();
                reader = new BufferedReader(new InputStreamReader(instance.getInputStream()));

                // Writer only works on incremental mode
                if(smt2.incremental()) {
                    if (writer != null) {
                        writer.flush();
                        writer.close();
                    }

                    writer = new BufferedWriter(new OutputStreamWriter(instance.getOutputStream()));
                    writer.write(getSMTSpecification());
                }
            }

            if(smt2.incremental()) {
                writer.write("(check-sat)\n");
                writer.flush();
            }

            String line = reader.readLine();
            result = SMTResult.getResult(line == null ? "UNKNOWN" : line);
            solved = true;

            // Parse instance if SAT
            if(result.isSat()){
                this.solNumFS = new HashMap<>();
                this.solBoolFS = new HashMap<>();

                if(smt2.incremental()) {
                    writer.write("(get-model)\n");
                    writer.write("(echo \"finished\")\n");
                    writer.flush();
                }

                // if true, specifies that so far we have parsed part of the function symbol's model data
                boolean isBroken = false;
                // current function symbol being parsed
                String fs = "";

                while((line = reader.readLine()) != null) {
                    // Remove extra whitespace
                    line = line.replaceAll("\\s+", " ");

                    // If the info of the function symbol is separated by lines, we keep combining the lines
                    // otherwise, the line contains all data
                    fs = isBroken ? fs + line : line;

                    Matcher m = fsModelFormat.matcher(fs);
                    if(m.find()) {
                        String var = m.group("id");
                        int id  = Integer.parseInt(var.replaceAll("[^0-9-]", ""));
                        String type = m.group("type");
                        String value  = m.group("value");

                        if(type.equals("Int")){
                            solNumFS.put(id, Integer.parseInt(value.replaceAll("[^0-9-]", "")));
                        }
                        else if(type.equals("Real")){
                            if(value.contains("/")){
                                m = fraction.matcher(value);
                                if(m.find()) {
                                    double num = Double.parseDouble(m.group("num"));
                                    double denom = Double.parseDouble(m.group("denom"));
                                    solNumFS.put(id, num / denom);
                                    // Store the fraction representation
                                    solFractions.put(id, value + ")");
                                }
                            }
                            else solNumFS.put(id, Double.parseDouble(value.replaceAll("[^0-9-.]", "")));
                        }
                        else{ //Bool
                            solBoolFS.put(id, Boolean.valueOf(value));
                        }

                        // Reset before moving on to the next fs
                        isBroken = false;
                        fs = "";
                    }
                    else if(broken.matcher(fs).find()) // Identify fs
                        isBroken = true;
                    else{
                        isBroken = false;
                        fs = "";
                    }

                    // The data defining the value of a FS under the SMT-LIB standard is of the form
                    // (define-fun id () type value)
                    // meaning that it cannot be broken in more than 5 words.
                    // (it is assumed that each word is not split by '\n';
                    // this step prevents infinite concatenation of lines read)
                    if(isBroken && fs.trim().split("\\s+").length >= 5){
                        isBroken = false;
                        fs = "";
                    }

                    if(line.contains("finished"))
                        break;
                }
            }

            //Free this instance resources if it isn't in incremental solving mode
            if(!smt2.incremental())
                resetSolver();

            return result.isSat();
        }catch(IOException e){
            e.printStackTrace();
            resetSolver();
            result = SMTResult.UNKNOWN;
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SMTResult getResult() {
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(int label) {
        return solNumFS.containsKey(label) || solBoolFS.containsKey(label);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Number getValue(int label) throws VariableNotFoundException, InstanceNotFoundException {
        if(!solved)
            throw new InstanceNotFoundException("There is no previous solution in the current state of the solver. 'solve' method must be called beforehand.");
        if(result.isUnsat())
            throw new InstanceNotFoundException("Cannot get an interpretation unless immediately preceded by SAT/UNKNOWN response.");
        if(!this.contains(label))
            throw new VariableNotFoundException("No such variable with the given identifier: " + label);

        return solNumFS.containsKey(label) ? solNumFS.get(label) : (this.getBooleanValue(label) ? 1 : 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getBooleanValue(int label) throws VariableNotFoundException, InstanceNotFoundException {
        if(!solved)
            throw new InstanceNotFoundException("There is no previous solution in the current state of the solver. 'solve' method must be called beforehand.");
        if(result.isUnsat())
            throw new InstanceNotFoundException("Cannot get an interpretation unless immediately preceded by SAT/UNKNOWN response.");
        if(!this.contains(label))
            throw new VariableNotFoundException("No such boolean expression with the given identifier: " + label);

        return solBoolFS.containsKey(label) ? solBoolFS.get(label) : this.getValue(label).doubleValue() != 0;
    }

    /**
     * Returns the proper SMT-LIB representation of the number specified.
     * @return v > 0 ? v : (- v)
     */
    private String getSMTofNum(Number v){
        if(v.doubleValue() < 0){
            return "(- " + (v instanceof Integer ? -v.intValue() : -v.doubleValue()) + ")";
        }else return String.valueOf(v);
    }

    /**
     * Helper method to specify the assignment of a numeric function symbol {@code fs}
     * identified by {@code id}, to the most recent solution, exactly as portrayed
     * initially by the SMT solver, i.e., either in decimal or fraction form.
     * @return (= fs previousValue)
     */
    private String getAssignment(int id, String fs){
        return "(= " + fs + " " +
                (
                        solFractions.containsKey(id) ?
                                solFractions.get(id) :      // Fraction representation
                                getSMTofNum(getValue(id))   // Decimal value
                )
                + ")";
    }

    /**
     * Update this solver's specific SMT specification to include the specified assertion that ignores such solution.
     */
    abstract protected void elimSolution(String sol);

    /**
     * {@inheritDoc}
     */
    @Override
    public void elimSolution(Collection<Integer> is) {
        if(solved) {
            Map<Integer, String> numFunctionSymbols = smt2.getNumFunctionSymbols();
            String notSol = "(assert (not (and " +
                    is.stream()
                            .filter(numFunctionSymbols :: containsKey)
                            .map(i -> getAssignment(i, numFunctionSymbols.get(i)))
                            .collect(Collectors.joining(" ")) + ")))\n";

            elimSolution(notSol);
            if(smt2.incremental() && writer != null)
                try {
                    writer.write(notSol);
                    writer.flush();
                }catch (IOException e){
                    e.printStackTrace();
                    resetSolver();
                }
        }else throw new InstanceNotFoundException("There is no previous solution in the current state of the solver. 'solve' method must be called beforehand.");
    }

    /**
     * Stops the SMT Solver instance and frees its resources.
     */
    private void resetSolver(){
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
                writer = null;
            }
            if (reader != null) {
                reader.close();
                reader = null;
            }
        }catch(IOException e){
            writer = null;
            reader = null;
        }

        if(instance != null){
            instance.destroyForcibly();
            instance = null;
        }
    }

    /**
     * The solver is reset to the point when the constructor was initially called, i.e.,
     * the SMT problem remains the same, but information regarding previous solving attempts is discarded.
     */
    @Override
    public void reset() {
        resetSolver();
        if(!this.smt2.trivial()) {
            this.solved = false;
            this.result = null;
        }
        this.solNumFS = new HashMap<>();
        this.solFractions = new HashMap<>();
        this.solBoolFS = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int numberOfVariables() {
        return smt2.getNumberOfVariables();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int numberOfAssertions() {
        return smt2.getNumberOfAssertions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void free() {
        resetSolver();
        binary = null;
        builder = null;
        this.result = null;
        this.solNumFS = null;
        this.solFractions = null;
        this.solBoolFS = null;
    }
}
