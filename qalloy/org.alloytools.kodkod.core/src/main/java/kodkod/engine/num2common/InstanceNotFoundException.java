package kodkod.engine.num2common;

/**
 * Happens when there is no SAT/UNKNOWN solution currently stored in the SMT Solver
 * and an attempt to extract information of such instance was made.
 */
public class InstanceNotFoundException extends RuntimeException{

    public InstanceNotFoundException(){ super(); }

    public InstanceNotFoundException(String msg){ super(msg); }
}
