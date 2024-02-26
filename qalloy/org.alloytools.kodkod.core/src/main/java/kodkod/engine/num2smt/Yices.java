package kodkod.engine.num2smt;

import java.util.*;

/**
 * Implements the solver specific details associated with Yices solver, following the {@link SMT2Solver} workflow.
 */
public class Yices extends SMT2Solver {

    // SMT-LIB specification at hand
    private final SMTSpecification spec;
    // SMT-LIB specification at hand, together with Yices specific commands/assertions.
    private final String smt2;
    // Solutions to this problem to be ignored in the following calls of {@see solve}.
    private final StringBuilder ignore;

    /**
     * {@inheritDoc}
     * @return Yices
     */
    @Override
    public String getSolver() {
        return "Yices";
    }

    /**
     * Attempt to find the yices binary location, in case it was not provided.
     */
    @Override
    public String defaultBinaryLocation(){
        String location = System.getenv("Yices_DIR");
        if(location == null)
            location = "./yices";
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<String> getCommand(boolean incremental){
        return incremental ? new ArrayList<>(Arrays.asList("--incremental", "--smt2-model-format")) : new ArrayList<>(Collections.singletonList("--smt2-model-format"));
    }

    /**
     * Given the assertion stack, adds the solving options accordingly, as supported by Yices.
     */
    private String setupSMT2(String stack){
        return
                // solution extraction
                "(set-option :produce-models true)\n" +
                // set logic
                "(set-logic " + spec.getLogic() + ")\n" +
                // assertion stack
                stack + "\n";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSMTSpecification() {
        return
                smt2 +
                // ignore solutions
                ignore.toString() +
                // check satisfiability and get model if SAT
                (spec.incremental() ? "" :
                "(check-sat)\n" +
                "(get-model)\n" +
                "(echo \"finished\")\n");
    }

    /**
     * Creates a new instance of a Yices.
     * @param smt2 SMT-LIB specification to be solved
     * @param yiceslocation Yices binary location
     */
    public Yices(SMTSpecification smt2, String yiceslocation){
        super(smt2, yiceslocation);
        this.spec = smt2;
        this.smt2 = setupSMT2(smt2.getOptionlessSmt2Specification());
        this.ignore = new StringBuilder();
    }

    /**
     * Creates a new instance of a Yices.
     * @param smt2 SMT-LIB specification to be solved
     */
    public Yices(SMTSpecification smt2){
        super(smt2);
        this.spec = smt2;
        this.smt2 = setupSMT2(smt2.getOptionlessSmt2Specification());
        this.ignore = new StringBuilder();
    }

    /**
     * Creates an instance of a Yices over a trivial solution.
     * @param smt2 SMT-LIB specification
     * @param trivialResult true => TRIVIALLY_SAT, TRIVIALLY_UNSAT
     */
    Yices(SMTSpecification smt2, boolean trivialResult){
        super(smt2, trivialResult);
        this.spec = smt2;
        this.smt2 = setupSMT2(smt2.getOptionlessSmt2Specification());
        this.ignore = new StringBuilder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void elimSolution(String sol) {
        ignore.append(sol);
    }
}
