package kodkod.engine.num;

import kodkod.engine.bool.Operator;

/**
 * An unary gate which specifies an operation over a numeric value.
 */
public final class UnaryGate extends NumericValue{

    final Operator.Unary op;

    private final NumericValue input;

    private final int   label;

    /**
     * Constructs a new choice gate with the given operator, label, and inputs.
     * @requires op = NEG | op = ABS | op = SGN
     */
    UnaryGate(Operator.Unary op, int label, NumericValue v) {
        assert label >= 0;
        assert op != null;
        this.label = label;
        this.op = op;
        this.input = v;
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
     * Returns the label for this value.
     *
     * @return this.label
     */
    @Override
    public int label() {
        return this.label;
    }

    /**
     * Returns the operator used to combine the input variables of this connective
     * gate.
     *
     * @return this.op
     */
    @Override
    public Operator op() {
        return this.op;
    }

    /**
     * Returns 1.
     *
     * @return 1
     */
    public int size() {
        return 1;
    }

    /**
     * Returns the input to this gate.
     *
     * @return this.input
     */
    public NumericValue input() {
        return this.input;
    }
}
