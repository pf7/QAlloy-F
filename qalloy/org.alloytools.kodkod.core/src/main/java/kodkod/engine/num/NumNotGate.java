package kodkod.engine.num;

import kodkod.engine.bool.BooleanFormula;
import kodkod.engine.bool.BooleanVisitor;
import kodkod.engine.bool.Operator;

import java.util.Iterator;

/**
 * A NOT gate over a numeric value.
 *
 * @invariant this.op = Operator.NOT
 * @invariant #inputs = 1
 */
public final class NumNotGate extends BooleanFormula {

    private final NumericValue nv;

    /**
     * Constructs a new numeric NotGate with the given label and input.
     */
    NumNotGate(NumericValue v){
        super(null);
        this.nv = v;
    }

    /**
     * Throws an unsupported operation exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public int hash(Operator op) {
        throw new UnsupportedOperationException("NumNotGate cannot be present in qualitative analysis.");
    }

    /**
     * Passes this value and the given argument value to the visitor, and returns
     * the resulting value.
     *
     * @return the value produced by the visitor when visiting this node with the
     *         given argument.
     */
    @Override
    public <T, A> T accept(BooleanVisitor<T, A> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    /**
     * Throws an unsupported operation exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public Iterator<BooleanFormula> iterator() {
        throw new UnsupportedOperationException("NumNotGate cannot be present in qualitative analysis.");
    }

    /**
     * Returns 1.
     *
     * @return 1.
     */
    @Override
    public int size() {
        return 1;
    }

    /**
     * Throws an unsupported operation exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public BooleanFormula input(int i) {
        throw new UnsupportedOperationException("NumNotGate cannot be present in qualitative analysis.");
    }

    /**
     * Returns the label for this gate.
     *
     * @return this.label
     */
    @Override
    public int label() {
        return -nv.label();
    }

    /**
     * Returns Operator.NOT.
     *
     * @return Operator.NOT
     */
    @Override
    public Operator op() {
        return Operator.NOT;
    }

    /**
     * Returns the input to this gate.
     *
     * @return this.nv
     */
    public NumericValue input(){
        return nv;
    }

    /**
     * Returns a string representation of this inverter.
     *
     * @return a string representation of this inverter.
     */
    @Override
    public String toString() {
        return "!" + nv.toString();
    }
}
