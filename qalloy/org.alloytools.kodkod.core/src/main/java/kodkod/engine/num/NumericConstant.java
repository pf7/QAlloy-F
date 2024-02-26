package kodkod.engine.num;

import kodkod.engine.bool.BooleanValue;
import kodkod.engine.bool.Operator;

import static kodkod.engine.bool.BooleanConstant.FALSE;
import static kodkod.engine.bool.BooleanConstant.TRUE;

/**
 * A numeric constant that represents either an integer number or a double value.
 *
 * @specfield value : Number
 * @invariant this.op = Operator.CONST
 * @invariant value : Integer || value : Double
 */
public class NumericConstant extends NumericValue{


    private final int                                label;
    private final Number                             value;

    public static final NumericConstant ONE  = new NumericConstant(1);
    public static final NumericConstant ZERO = new NumericConstant(0);

    /**
     * Constructs a NumericConstant that represent the given integer value.
     */
    private NumericConstant(int value) {
        assert value >= 0;
        this.label = -Integer.MAX_VALUE + value;
        this.value = value;
    }

    /**
     * Constructs a NumericConstant that represents the given integer value,
     * identified by the specified label.
     */
    NumericConstant(int label, int value) {
        this.label = label;
        this.value = value;
    }

    /**
     * Constructs a NumericConstant that represents the given double value,
     * identified by the specified label.
     */
    NumericConstant(int label, double value) {
        assert value >= 0;
        this.label = label;
        this.value = value;
    }

    /**
     * Returns the primitive boolean representation of this numeric constant.
     *
     * @return this.value != 0
     */
    public boolean booleanValue() {
        return value.doubleValue() != 0;
    }

    /**
     * Returns the value of this constant.
     *
     * @return this.value
     */
    public Number getValue() { return value; }

    /**
     * Returns the negation of this value.
     * @return this.value == 0 ? false : true
     */
    @Override
    public BooleanValue negation() {
        return value.doubleValue() == 0 ? FALSE : TRUE;
    }

    /**
     * Returns the label for this value.
     *
     * @return this.label
     */
    @Override
    public int label() {
        return label;
    }

    /**
     * Returns a string representation of this numeric constant.
     *
     * @return a string representation of this numeric constant.
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }

    /**
     * Returns Operator.CONST.
     *
     * @return Operator.CONST
     */
    @Override
    public Operator op() {
        return Operator.CONST;
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
}

