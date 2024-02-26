package kodkod.engine.num2common;

/**
 * Caused by trying to access a variable which does not exist
 * in the current state of the QuantitativeSolver.
 */
public class VariableNotFoundException extends RuntimeException{

    public VariableNotFoundException(){ super(); }

    public VariableNotFoundException(String msg){ super(msg); }

}
