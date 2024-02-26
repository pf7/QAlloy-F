package kodkod.engine.num;

import kodkod.engine.bool.BooleanValue;
import kodkod.engine.bool.Operator;

import static kodkod.engine.bool.Operator.VAR;

/**
 * A numeric value which is constrained to be {0, 1}-valued, that is, a Boolean value within a quantitative context.
 * It is defined by both a {@link BooleanValue} and {@link NumericValue}, which share the same label, and represent
 * the same value perceived on different contexts, in particular, they are precisely related as follows:
 * num = bool ? 1 : 0
 *
 * @specfield bool : BooleanValue
 * @specfield num  : NumericValue
 */
public class BinaryValue extends NumericValue{

    private final int                  label;
    private final BooleanValue         bool;
    private final NumericValue         num;

    /**
     * Constructs a new BinaryValue specified by the given numeric and boolean values.
     *
     * @requires num != null && bool != null
     * @requires num.label == bool.label
     * @requires num = bool ? 1 : 0
     * @ensures this.label' = bool.label && this.num' = num && this.bool' = bool
     */
    BinaryValue(NumericValue num, BooleanValue bool){
        assert num.label() == bool.label();
        this.label = bool.label();
        this.num = num;
        this.bool = bool;
    }

    /**
     * Returns the label for this variable.
     *
     * @return this.label
     */
    @Override
    public int label() {
        return label;
    }

    /**
     * Returns the VAR operator.
     *
     * @return Operator.VAR
     */
    @Override
    public Operator op() {
        return VAR;
    }

    /**
     * Drops this this value to the Boolean domain.
     */
    public BooleanValue toBool(){
        return bool;
    }

    /**
     * Lifts this this value to the Numeric domain at hand.
     */
    public NumericValue toNumeric(){
        return num;
    }

    /**
     * Passes this value and the given argument value to the visitor, and returns
     * the resulting value.
     *
     * @return the value produced by the visitor when visiting this node with the
     *         given argument.
     */
    @Override
    public <T, A> T accept(NumericVisitor<T, A> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    /**
     * Returns a string representation of this binary value.
     *
     * @return a string representation of this binary value.
     */
    @Override
    public String toString(){ return "BV" + label + "{ " + num + ", " + bool + " }"; }
}
