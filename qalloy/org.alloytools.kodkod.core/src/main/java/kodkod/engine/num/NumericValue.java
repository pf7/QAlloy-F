package kodkod.engine.num;

import kodkod.engine.bool.BooleanFormula;
import kodkod.engine.bool.BooleanValue;

/**
 * Extends {@link kodkod.engine.bool.BooleanValue} to the quantitative realm,
 * which may deal with integer or real numbers in the current implementation.
 * Numeric values are produced by {@link NumericFactory factories}. Each
 * value is associated with an integer label; the labels are unique within a
 * given factory.
 * The {@link NumericConstant} ZERO and ONE are the only values shared among factories.
 */
public abstract class NumericValue extends BooleanValue {

    private BooleanFormula negation;

    /**
     * Passes this value and the given argument value to the visitor, and returns
     * the resulting value.
     *
     * @return the value produced by the visitor when visiting this node with the
     *         given argument.
     */
    public abstract <T, A> T accept(NumericVisitor<T,A> visitor, A arg);

    /**
     * {@link BooleanFormula#negation()}
     */
    @Override
    public BooleanValue negation() {
        if (negation == null) {
            negation = new NumNotGate(this);
        }
        return negation;
    }
}
