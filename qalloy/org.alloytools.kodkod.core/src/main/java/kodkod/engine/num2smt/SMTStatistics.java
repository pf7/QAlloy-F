package kodkod.engine.num2smt;

import kodkod.engine.Statistics;
import kodkod.engine.fol2sat.Translation;
import kodkod.engine.num2common.QuantitativeTranslation;

/**
 * Adaptation of {@link Statistics} to the SMT background, where
 * - variables() returns the total number of function symbols;
 * - clauses() returns the number of assertions in the stack.
 */
public class SMTStatistics extends Statistics {
    public SMTStatistics(Translation translation, long translationTime, long solvingTime){
        super(translation.numPrimaryVariables(),
                ((QuantitativeTranslation)translation).solver().numberOfVariables(),
                ((SMTSolver) ((QuantitativeTranslation) translation).solver()).numberOfAssertions(),
                translationTime, solvingTime);
    }

    @Override
    public String toString(){
        return "function symbols: " +
                super.variables() +
                NEW_LINE +
                "assertions: " +
                super.clauses() +
                NEW_LINE +
                "primary variables: " +
                super.primaryVariables() +
                NEW_LINE +
                "translation time: " +
                super.translationTime() +
                " ms" + NEW_LINE +
                "solving time: " +
                super.solvingTime() +
                " ms";
    }
}
