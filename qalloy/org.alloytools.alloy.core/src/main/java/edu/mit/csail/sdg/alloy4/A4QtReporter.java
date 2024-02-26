package edu.mit.csail.sdg.alloy4;

/**
 * Analogous to {@link A4Reporter}, supports further messages, useful to quantitative analysis.
 * However, unlike {@link A4Reporter}, does not support message forwarding.
 */
public class A4QtReporter extends A4Reporter{

    // Number of warnings
    private int warn;

    public A4QtReporter() {
        super();
        this.warn = 0;
    }

    /**
     * Called before generating an adequate specification (smt-lib)
     * to the given solver with respect to the analysis context.
     *
     * @param solver Quantitative Solver
     * @param context Quantitative analysis context
     * @param maxWeight Maximum weight allowed by the solver
     */
    public void translate(String solver, String context, Integer maxWeight){;}

    /**
     * Called to report some characteristics of the generated smt2-lib specification
     * as well as the time it took to generate such specification.
     *
     * @param primaryVar Number of the numeric function symbols representing primary variables
     * @param fs Total number of function symbols
     * @param assertions Number of assertions in the current state of the assertion stack
     * @param translationTime Number of milliseconds to arrive at the smt2 specification
     */
    public void smt2(int primaryVar, int fs, int assertions, long translationTime){}

    /**
     * Called to report the UNKNOWN outcome obtained when solving the SMT problem at hand.
     *
     * @param command Original command
     * @param solvingTime The time it took the solver to terminate, in milliseconds.
     * @param solution The resulting A4Solution object for this problem.
     */
    public void resultUNKNOWN(Object command, long solvingTime, Object solution){}

    /**
     * To be called when a new warning is reported,
     * i.e., after method {@link A4Reporter#warning(ErrorWarning)}.
     */
    public void newWarning(){
        warn++;
    }

    /**
     * @return The number of warnings reported
     */
    public int getNumberOfWarnings(){
        return warn;
    }
}
