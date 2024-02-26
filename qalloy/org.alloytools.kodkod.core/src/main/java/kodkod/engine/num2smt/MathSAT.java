package kodkod.engine.num2smt;

import java.util.*;

/**
 * Implements the solver specific details associated with MathSAT solver, following the {@link SMT2Solver} workflow.
 */
public class MathSAT extends SMT2Solver {

    // SMT-LIB specification at hand
    private final SMTSpecification spec;
    // SMT-LIB specification at hand, together with MathSAT specific commands/assertions.
    private final String smt2;
    // Solutions to this problem to be ignored in the following calls of {@see solve}.
    private final StringBuilder ignore;

    /**
     * {@inheritDoc}
     * @return MathSAT
     */
    @Override
    public String getSolver() {
        return "MathSAT";
    }

    /**
     * Attempt to find the MathSAT binary location, in case it was not provided.
     */
    @Override
    public String defaultBinaryLocation(){
        String location = System.getenv("MathSAT_DIR");
        if(location == null)
            location = "./mathsat";
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<String> getCommand(boolean incremental){
        return new ArrayList<>();
    }

    /**
     * Given the assertion stack, adds the solving options accordingly, as supported by MathSAT.
     */
    private String setupSMT2(String stack){
        return
                // set logic
                "(set-logic " + spec.getLogic() + ")\n" +
                // solution extraction
                "(set-option :produce-models true)\n" +
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
     * Creates a new instance of a MathSAT.
     * @param smt2 SMT-LIB specification to be solved
     * @param msatlocation MathSAT binary location
     */
    public MathSAT(SMTSpecification smt2, String msatlocation){
        super(smt2, msatlocation);
        this.spec = smt2;
        this.smt2 = setupSMT2(smt2.getOptionlessSmt2Specification());
        this.ignore = new StringBuilder();
    }

    /**
     * Creates a new instance of a MathSAT.
     * @param smt2 SMT-LIB specification to be solved
     */
    public MathSAT(SMTSpecification smt2){
        super(smt2);
        this.spec = smt2;
        this.smt2 = setupSMT2(smt2.getOptionlessSmt2Specification());
        this.ignore = new StringBuilder();
    }

    /**
     * Creates an instance of a MathSAT over a trivial solution.
     * @param smt2 SMT-LIB specification
     * @param trivialResult true => TRIVIALLY_SAT, TRIVIALLY_UNSAT
     */
    MathSAT(SMTSpecification smt2, boolean trivialResult){
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
