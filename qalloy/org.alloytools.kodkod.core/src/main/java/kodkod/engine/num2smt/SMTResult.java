package kodkod.engine.num2smt;

/**
 * Represents the possible outputs of a SMT Solver, when checking the satisfiability
 * of a given SMT Problem.
 */
public enum SMTResult {
    SAT,
    UNSAT,
    UNKNOWN;

    /**
     * Creates a SMTResult from a Boolean value
     * @param sat output
     * @return sat == null  => UNKNOWN
     *         sat == true  => SAT
     *         sat == false => UNSAT
     */
    public static SMTResult getResult(Boolean sat){
        return sat == null ? UNKNOWN : (sat ? SAT : UNSAT);
    }

    /**
     * Creates a SMTResult from a String
     * @param str non-null satisfiability output
     * @return sat == true  => SAT
     *         sat == false => UNSAT
     *         else            UNKNOWN
     */
    public static SMTResult getResult(String str){
        SMTResult result;

        switch(str.toUpperCase()){
            case "SAT":
                result = SAT;
                break;
            case "UNSAT":
                result = UNSAT;
                break;
            default:
                result = UNKNOWN;
                break;
        }

        return result;
    }

    /**
     * @return this == SAT
     */
    public boolean isSat(){
        return this == SAT;
    }

    /**
     * @return this == UNSAT
     */
    public boolean isUnsat(){
        return this == UNSAT;
    }

    /**
     * @return this == UNKNOWN
     */
    public boolean isUnknown(){
        return this == UNKNOWN;
    }
}
