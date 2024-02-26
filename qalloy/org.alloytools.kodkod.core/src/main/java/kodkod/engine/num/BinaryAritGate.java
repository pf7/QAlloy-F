package kodkod.engine.num;

import kodkod.engine.bool.Operator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An arithmetic gate with two inputs.
 */
public final class BinaryAritGate extends AritGate{

    private NumericValue left, right;

    /**
     * Constructs a new binary gate with the given operator, label, and inputs.
     */
    BinaryAritGate(Operator.NumNary op, int label, NumericValue left, NumericValue right){
        super(op, label);
        this.left = left;
        this.right = right;
    }


    /**
     * Returns an iterator over the inputs to this gate.
     *
     * @return an iterator over this.inputs.
     */
    @Override
    public Iterator<NumericValue> iterator() {
        return new Iterator<NumericValue>() {
            int next = 0;

            @Override
            public boolean hasNext() {
                return next < 2;
            }

            @Override
            public NumericValue next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                return (next++ == 0 ? left : right);
            }
        };
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
