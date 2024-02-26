package kodkod.engine.num2common;

import java.util.Collection;

/**
 * Operations to interact with a quantitative solver.
 */
public interface QuantitativeSolver {

    /**
     * Check the satisfiability of the model at hand.
     */
    public boolean solve();

    /**
     * Checks if there is a variable with the given label in the current state of the solver.
     * @param label identifier
     */
    public boolean contains(int label);

    /**
     * Given a SAT response, returns the integer/real value of the variable with the given label associated.
     * @param label identifier
     * @return Numeric value
     * @throws VariableNotFoundException no numeric variable with the given identifier
     * @throws InstanceNotFoundException solve was not called previously || UNSAT
     */
    public Number getValue(int label) throws VariableNotFoundException, InstanceNotFoundException;

    /**
     * Given a SAT response, returns the boolean value of the variable with the given label associated.
     * @param label identifier
     * @return boolean value
     * @throws VariableNotFoundException no boolean expression with the given identifier
     * @throws InstanceNotFoundException solve was not called previously || UNSAT
     */
    public boolean getBooleanValue(int label) throws VariableNotFoundException, InstanceNotFoundException;

    /**
     * Explicitly stops the solver from considering the recentmost solution produced for the variables
     * identified by {@code vars}.
     */
    public void elimSolution(Collection<Integer> vars);

    /**
     * Frees the resources allocated by this solver.
     * Further interaction with the solver after this method is called,
     * may result in undefined behaviour.
     */
    public void free();

    /**
     * @return Total number of variables in the model being analysed.
     */
    public int numberOfVariables();
}
