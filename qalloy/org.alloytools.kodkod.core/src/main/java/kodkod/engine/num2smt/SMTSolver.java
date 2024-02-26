package kodkod.engine.num2smt;

import kodkod.engine.num2common.InstanceNotFoundException;
import kodkod.engine.num2common.QuantitativeSolver;
import kodkod.engine.num2common.VariableNotFoundException;

import java.util.Collection;

/**
 * Operations provided to interact with a SMT solver.
 */
public interface SMTSolver extends QuantitativeSolver {

    /**
     * Checks the satisfiability of the current state of this solver assertion stack.
     * @return true iff SAT
     */
    public boolean solve();

    /**
     * @return SMTResult representing the judgement obtained in the most recent "solve" call.
     *         null is returned if 'solve' wasn't called yet.
     */
    public SMTResult getResult();

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
     * Disallows the solver from further producing a solution which assigns the same values
     * from the previous solution to the specified variables.
     * @param vars identifiers of the variables.
     *             Identifiers which do not correspond to a valid numeric function symbol
     *             for the current SMT Problem will be ignored.
     */
    public void elimSolution(Collection<Integer> vars);

    /**
     * Returns the number of function symbols (numeric and boolean).
     */
    public int numberOfVariables();

    /**
     * Returns the number of assertions in the assertion stack
     */
    public int numberOfAssertions();

    /**
     * Resets the state of the solver.
     */
    public void reset();

    /**
     * Frees the resources allocated by this solver.
     * Further interaction with the solver after this method is called,
     * may result in undefined behaviour.
     */
    public void free();

    /**
     * Sets the full path to the executable binary of this solver.
     * If this SMT Solver is not executed through a binary,
     * calling this method does nothing.
     */
    public void setBinaryLocation(String path);
}
