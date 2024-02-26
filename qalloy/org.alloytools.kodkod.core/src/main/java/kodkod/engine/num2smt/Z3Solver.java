package kodkod.engine.num2smt;

import java.util.*;

/**
 * Implements the solver specific details associated with Z3 Solver, following the {@link SMT2Solver} workflow.
 */
public class Z3Solver extends SMT2Solver {

    // SMT-LIB specification at hand
    private final SMTSpecification spec;
    // SMT-LIB specification at hand, together with Z3 specific commands/assertions.
    private final String smt2;
    // Solutions to this problem to be ignored in the following calls of {@see solve}.
    private final StringBuilder ignore;

    /**
     * {@inheritDoc}
     * @return Z3
     */
    @Override
    public String getSolver() {
        return "Z3";
    }

    /**
     * Attempt to find the z3 binary location, in case it was not provided.
     */
    @Override
    public String defaultBinaryLocation(){
        String location = System.getenv("Z3_DIR");
        if(location == null)
            location = "./z3";
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<String> getCommand(boolean incremental){
        return incremental ? new ArrayList<>(Collections.singletonList("-in")) : new ArrayList<>();
    }

    /**
     * Given the assertion stack, adds the solving options accordingly, as supported by Z3.
     */
    private String setupSMT2(String stack){
        return
                // set logic
                "(set-logic " + spec.getLogic() + ")\n" +
                // solution extraction
                "(set-option :produce-models true)\n" +
                // consider 16 decimal places during solving
                "(set-option :pp.decimal true)\n" +
                "(set-option :pp.decimal_precision 16)" +
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
     * Creates a new instance of a Z3Solver.
     * @param smt2 SMT-LIB specification to be solved
     * @param z3location Z3 binary location
     */
    public Z3Solver(SMTSpecification smt2, String z3location){
        super(smt2, z3location);
        this.spec = smt2;
        this.smt2 = setupSMT2(smt2.getOptionlessSmt2Specification());
        this.ignore = new StringBuilder();
    }

    /**
     * Creates a new instance of a Z3Solver.
     * @param smt2 SMT-LIB specification to be solved
     */
    public Z3Solver(SMTSpecification smt2){
        super(smt2);
        this.spec = smt2;
        this.smt2 = setupSMT2(smt2.getOptionlessSmt2Specification());
        this.ignore = new StringBuilder();
    }

    /**
     * Creates an instance of a Z3Solver over a trivial solution.
     * @param smt2 SMT-LIB specification
     * @param trivialResult true => TRIVIALLY_SAT, TRIVIALLY_UNSAT
     */
    Z3Solver(SMTSpecification smt2, boolean trivialResult){
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
