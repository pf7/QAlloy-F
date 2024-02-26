package kodkod.engine.num;

import kodkod.engine.bool.Dimensions;
import kodkod.util.ints.*;

import static kodkod.engine.num.NumericConstant.ONE;

/**
 * An extension to {@link NumericMatrix} which constraints its elements to be {0, 1}-valued
 * within the quantitative domain at hand.
 *
 * @invariant all e : this.cells | e :: BinaryValue || e == NumericConstant.ZERO || e == NumericConstant.ONE
 */
public class BinaryMatrix extends NumericMatrix implements Cloneable{

    /**
     * Constructs a new binary numeric matrix with the same characteristics as the given matrix.
     */
    private BinaryMatrix(BinaryMatrix m) {
        super(m);
    }

    /**
     * Constructs a new matrix with the given dimensions and factory. The
     * constructed matrix can store any kind of BinaryValue.
     */
    BinaryMatrix(Dimensions dims, NumericFactory factory) {
        super(dims, factory);
    }

    /**
     * Constructs a new matrix with the given dimensions and factory, and
     * initializes the indices in the given set to ONE.
     */
    BinaryMatrix(Dimensions dims, NumericFactory factory, IntSet allIndices, IntSet trueIndices) {
        super(dims, factory, allIndices, trueIndices);
    }

    /**
     * {@inheritDoc}
     * @return true
     */
    @Override
    public boolean isBoolean(){ return true; }

    /**
     * Returns a copy of this binary numeric matrix.
     */
    public BinaryMatrix clone() {
        return new BinaryMatrix(this);
    }

}
