package kodkod.engine.num2smt;

import kodkod.engine.config.QuantitativeOptions;

import java.util.Map;

/**
 * Stores a SMT-LIB2 specification conforming to some {@link QuantitativeOptions options}.
 *
 * @specfield smt2 : String // Raw specification
 * @specfield numFunctionSymbols : [Integer, String] // Associates each numeric fs identifier in this.smt2 to its name
 * @specfield numberOfVariables : int // Total number of fs (boolean and numeric)
 * @specfield numberOfAssertions : int // Number of assertions in the assertion stack
 * @specfield options : QuantitativeOptions // Solving options
 */
public class SMTSpecification {
    private final String logic; // Logic to be used during solving
    private final String smt2options; // default smt syntax that encompasses the specified {@code options}
    private final String smt2; // raw specification, without options
    private final Map<Integer, String> numFunctionSymbols;
    private final int numberOfVariables;
    private final int numberOfAssertions;
    private final QuantitativeOptions options;

    public SMTSpecification(String logic, String smt2options, String smt2, Map<Integer, String> numFunctionSymbols, int numberOfVariables, int numberOfAssertions, QuantitativeOptions options){
        this.logic = logic;
        this.smt2options = smt2options;
        this.smt2 = smt2;
        this.numFunctionSymbols = numFunctionSymbols;
        this.numberOfVariables = numberOfVariables;
        this.numberOfAssertions = numberOfAssertions;
        this.options = options;
    }

    public String getLogic(){
        return logic;
    }

    /**
     * @return The generated solving options together with the assertion stack
     */
    public String getSmt2Specification() {
        return smt2options + smt2;
    }

    /**
     * @return the assertion stack without any solving details specified (logic, options, ...)
     */
    public String getOptionlessSmt2Specification() {
        return smt2;
    }

    public Map<Integer, String> getNumFunctionSymbols() {
        return numFunctionSymbols;
    }

    public int getNumberOfVariables() {
        return numberOfVariables;
    }

    public int getNumberOfAssertions() {
        return numberOfAssertions;
    }

    public QuantitativeOptions options(){
        return this.options;
    }

    public boolean incremental() {
        return options.incremental();
    }

    public boolean trivial(){ return smt2 == null || numberOfVariables == 0; }

    public String toString(){
        return  "----- SMT Specification -----\n" + smt2 +
                "\nNumFunctionSymbols:" + numFunctionSymbols +
                "\nNumber of Variables:" + numberOfVariables +
                "\nNumber of Assertions:" + numberOfAssertions +
                "\n----- Quantitative Options -----\n" + options;
    }
}
