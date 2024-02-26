package kodkod.engine.num;

import kodkod.engine.bool.Operator;

/**
 * A binary gate which specifies the choice between two inputs,
 * given the criteria specified by some operator: MAX, MIN or ITE gates
 */
abstract public class ChoiceGate extends NumericValue {

    final Operator op;

    private final NumericValue left, right;

    private final int   label;

    /**
     * Constructs a new choice gate with the given operator, label, and inputs.
     * @requires op = MIN | op = MAX | op = ITE
     */
    ChoiceGate(Operator op, int label, NumericValue left, NumericValue right) {
        assert label >= 0;
        assert op != null;
        this.label = label;
        this.op = op;
        this.left = left;
        this.right = right;
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
     * Returns the operator used to combine the input variables of this connective
     * gate.
     *
     * @return this.op
     */
    @Override
    public Operator op() {
        return op;
    }

    /**
     * Returns a string representation of this aritgate.
     *
     * @return a string representation of this aritgate.
     */
    @Override
    public String toString(){
        return op + "(" + left + ", " + right + ")";
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
     * Returns 2.
     *
     * @return 2
     */
    public int size() {
        return 2;
    }

    /**
     * Returns the ith input to this gate.
     *
     * @return this.inputs[i]
     * @requires 0 <= i < size
     * @throws IndexOutOfBoundsException i < 0 || i >= #this.inputs
     */
    public NumericValue input(int i) {
        switch (i) {
            case 0 :
                return left;
            case 1 :
                return right;
            default :
                throw new IndexOutOfBoundsException();
        }
    }

}
