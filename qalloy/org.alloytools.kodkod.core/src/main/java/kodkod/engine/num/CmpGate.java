package kodkod.engine.num;

import kodkod.engine.bool.BooleanFormula;
import kodkod.engine.bool.BooleanVisitor;
import kodkod.engine.bool.Operator;
import kodkod.util.ints.IndexedEntry;
import kodkod.util.ints.SparseSequence;
import kodkod.util.ints.TreeSequence;

import java.util.Iterator;

/**
 * A comparison gate between two NumericConstants/NumericVariables x,y
 * x op y
 *
 * where op represents an (in)equality operator, i.e., is one of: =, >, <, >= or <=
 *
 * @specfield left: input defining the left-hand side of the comparison
 * @specfield right: input defining the right-hand side of the comparison
 *
 */
public class CmpGate extends BooleanFormula {

    final Operator.Comparison op;

    private final int   label;

    private NumericValue left, right;

    private CmpGate(Operator.Comparison op, int label){
        super(null);
        assert op != null;
        assert label >= 0;
        this.op = op;
        this.label = label;
    }

    /**
     * Constructs a new comparison gate of the form left op right with the given operator, label, and inputs.
     */
    CmpGate(Operator.Comparison op, int label, NumericValue left, NumericValue right) {
        this(op, label);
        this.left = left;
        this.right = right;
    }

    /**
     * Throws an unsupported operation exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public int hash(Operator op) {
        throw new UnsupportedOperationException("CmpGate cannot be present in qualitative analysis.");
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
        throw new UnsupportedOperationException("CmpGate cannot be present in qualitative analysis.");
    }

    /**
     * Returns the number of inputs to this gate.
     *
     * @return 2
     */
    @Override
    public int size() {
        return 2;
    }

    /**
     * Throws an unsupported operation exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public BooleanFormula input(int i) {
        throw new UnsupportedOperationException("CmpGate cannot be present in qualitative analysis.");
    }

    /**
     * Returns the ith input to this gate.
     *
     * @return this.inputs[i]
     * @requires 0 <= i < size
     * @throws IndexOutOfBoundsException i < 0 || i >= #this.inputs
     */
    public NumericValue inputNum(int i) {
        switch (i) {
            case 0 :
                return left;
            case 1 :
                return right;
            default :
                throw new IndexOutOfBoundsException();
        }
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
     * Returns a string representation of this cmpgate.
     *
     * @return a string representation of this cmpgate.
     */
    @Override
    public String toString(){
        final StringBuilder builder = new StringBuilder("(");
        builder.append(left);
        builder.append(op);
        builder.append(right);
        builder.append(")");
        return builder.toString();
    }
}
