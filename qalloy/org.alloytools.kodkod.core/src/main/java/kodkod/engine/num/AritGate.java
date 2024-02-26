package kodkod.engine.num;

import kodkod.engine.bool.Operator;

import java.util.Iterator;

/**
 * An arithmetic gate with two or more inputs: PLUS, MINUS, TIMES or DIV
 *
 * @specfield op: Operator.NumNary
 */
public abstract class AritGate extends NumericValue implements Iterable<NumericValue>{

    final Operator.NumNary op;

    private final int   label;

    /**
     * Constructs a new AritGate gate with the given operator and label.
     *
     * @requires op != null && label >= 0
     * @ensures this.op' = op && this.label' = label
     */
    AritGate(Operator.NumNary op, int label){
        assert op != null;
        assert label >= 0;
        this.op = op;
        this.label = label;
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
    public String toString() {
        final StringBuilder builder = new StringBuilder("(");
        final Iterator<NumericValue> children = iterator();
        builder.append(children.next());
        while (children.hasNext()) {
            builder.append(op);
            builder.append(children.next());
        }
        builder.append(")");
        return builder.toString();
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
