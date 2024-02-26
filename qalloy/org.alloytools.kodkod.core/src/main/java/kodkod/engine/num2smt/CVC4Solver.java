package kodkod.engine.num2smt;

import java.util.*;

/**
 * Implements the solver specific details associated with CVC4 Solver, following the {@link SMT2Solver} workflow.
 */
public class CVC4Solver extends SMT2Solver {

    // SMT-LIB specification at hand
    private final SMTSpecification spec;
    // SMT-LIB specification at hand, together with CVC4 specific commands/assertions.
    private String smt2;
    // Solutions to this problem to be ignored in the following calls of {@see solve}.
    private StringBuilder ignore;

    /**
     * {@inheritDoc}
     * @return CVC4
     */
    @Override
    public String getSolver() {
        return "CVC4";
    }

    /**
     * Attempt to find the cvc4 binary location, in case it was not provided.
     */
    @Override
    public String defaultBinaryLocation(){
        String location = System.getenv("CVC4_DIR");
        if(location == null)
            location = "./cvc4";
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<String> getCommand(boolean incremental){
        return new ArrayList<>(Arrays.asList("--lang", "smtlib2.6"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSMTSpecification() {
        return smt2 + "\n" + ignore.toString() +
                (spec.incremental() ? "" :
                    "(check-sat)\n" +
                    "(get-model)\n" +
                    "(echo \"finished\")\n");
    }

    /**
     * Creates a new instance of a CVC4Solver.
     * @param smt2 SMT-LIB specification to be solved
     * @param cvc4location CVC4 binary location
     */
    public CVC4Solver(SMTSpecification smt2, String cvc4location){
        super(smt2, cvc4location);
        this.spec = smt2;
        this.smt2 = smt2.getSmt2Specification();
        this.ignore = new StringBuilder();
    }

    /**
     * Creates a new instance of a CVC4Solver.
     * @param smt2 SMT-LIB specification to be solved
     */
    public CVC4Solver(SMTSpecification smt2){
        super(smt2);
        this.spec = smt2;
        this.smt2 = smt2.getSmt2Specification();
        this.ignore = new StringBuilder();
    }

    /**
     * Creates an instance of a CVC4Solver over a trivial solution.
     * @param smt2 SMT-LIB specification
     * @param trivialResult true => TRIVIALLY_SAT, TRIVIALLY_UNSAT
     */
    CVC4Solver(SMTSpecification smt2, boolean trivialResult){
        super(smt2, trivialResult);
        this.spec = smt2;
        this.smt2 = smt2.getSmt2Specification();
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
