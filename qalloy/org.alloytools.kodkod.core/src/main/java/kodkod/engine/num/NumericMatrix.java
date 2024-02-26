package kodkod.engine.num;

import kodkod.engine.bool.*;
import kodkod.engine.config.QuantitativeOptions;
import kodkod.util.collections.Containers;
import kodkod.util.collections.Pair;
import kodkod.util.ints.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static kodkod.engine.bool.BooleanConstant.FALSE;
import static kodkod.engine.bool.BooleanConstant.TRUE;
import static kodkod.engine.bool.Operator.*;
import static kodkod.engine.num.NumericConstant.ONE;
import static kodkod.engine.num.NumericConstant.ZERO;

/**
 * Quantitative extension of {@link BooleanMatrix}
 * <p>
 * An n-dimensional matrix of {@link NumericValue numeric values}.
 * Numeric matrices are indexed using flat integer indeces. For
 * example, let m be a the 2 x 3 matrix of numeric variables identifed by labels
 * [0 4 1; 5 10 2]. Then, m[0] = 0, m[3] = 5, m[5] = 2, etc.
 * </p>
 * <p>
 * All values stored in the same matrix must be created by the same
 * {@link NumericFactory factory}. All methods that
 * accept another NumericMatrix as an input will throw an
 * IllegalArgumentException if the values in the input matrix do not belong to
 * the same factory as the values in the receiver matrix.
 * </p>
 *
 * @specfield dimensions: Dimensions
 * @specfield factory: NumericFactory
 * @specfield elements: [0..dimensions.capacity) -> one factory.components
 */
public class NumericMatrix implements Iterable<IndexedEntry<NumericValue>>, Cloneable{

    private final Dimensions dims;
    private final NumericFactory               factory;
    private final SparseSequence<NumericValue> cells;

    /**
     * Constructs a new matrix with the given dimensions, factory, and entries.
     */
    private NumericMatrix(Dimensions dimensions, NumericFactory factory, SparseSequence<NumericValue> seq) {
        this.dims = dimensions;
        this.factory = factory;
        this.cells = seq;
    }

    /**
     * Constructs a new numeric matrix with the same characteristics as the given numeric matrix.
     */
    NumericMatrix(NumericMatrix n){
        try {
            this.dims = n.dims;
            this.factory = n.factory;
            this.cells = n.cells.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(); // unreachable code.
        }
    }

    /**
     * Constructs a new matrix with the given dimensions and factory, backed by a
     * sparse sequence which can most efficiently hold the elements storable in the
     * sparse sequences s0 and s1.
     */
    private NumericMatrix(Dimensions d, NumericFactory f, SparseSequence<NumericValue> s0, SparseSequence<NumericValue> s1) {
        this.dims = d;
        this.factory = f;
        final Class< ? > c0 = s0.getClass(), c1 = s1.getClass();
        if (c0 != c1 || c0 == RangeSequence.class)
            this.cells = new RangeSequence<NumericValue>();
        //else if (c0 == HomogenousSequence.class)
        //    this.cells = new HomogenousSequence<NumericValue>(f.trueVariable(), Ints.bestSet(d.capacity()));
        else
            this.cells = new TreeSequence<NumericValue>();
    }

    /**
     * Constructs a new matrix with the given dimensions and factory, backed by a
     * sparse sequence which can most efficiently hold the elements storable in the
     * matrices m and rest.
     */
    private NumericMatrix(Dimensions d, NumericMatrix m, NumericMatrix... rest) {
        this.dims = d;
        this.factory = m.factory;

        final Class< ? > h = HomogenousSequence.class, t = TreeSequence.class;
        final boolean sameDim = d.equals(m.dims);

        Class< ? > c = m.cells.getClass();
        int cId = c == h ? 1 : c == t ? 2 : 4;

        for (NumericMatrix other : rest) {
            checkFactory(factory, other.factory);
            if (sameDim)
                checkDimensions(d, other.dims);

            c = other.cells.getClass();
            cId |= c == h ? 1 : c == t ? 2 : 4;
        }

        switch (cId) {
            /*case 1 :
                this.cells = new HomogenousSequence<NumericValue>(this.factory.trueVariable(), Ints.bestSet(d.capacity()));
                break;*/
            case 2 :
                this.cells = new TreeSequence<NumericValue>();
                break;
            default :
                this.cells = new RangeSequence<NumericValue>();
        }

    }

    /**
     * Constructs a new matrix with the given dimensions and factory. The
     * constructed matrix can store any kind of NumericValue.
     */
    NumericMatrix(Dimensions dims, NumericFactory factory) {
        this.dims = dims;
        this.factory = factory;
        this.cells = new RangeSequence<NumericValue>();
    }

    /**
     * Constructs a new matrix with the given dimensions and factory, and
     * initializes the indices in the given set to ONE.
     */
    NumericMatrix(Dimensions dims, NumericFactory factory, IntSet allIndices, IntSet trueIndices) {
        this.dims = dims;
        this.factory = factory;
        final int tsize = trueIndices.size(), asize = allIndices.size();
        /*if (tsize == asize)
            this.cells = new HomogenousSequence<NumericValue>(ONE, trueIndices);
        else {*/
            this.cells = tsize == 0 || asize / tsize >= 2 ? new ArraySequence<NumericValue>(allIndices) : new RangeSequence<NumericValue>();
            for (IntIterator iter = trueIndices.iterator(); iter.hasNext();) {
                int id = iter.next();
                cells.put(id, ONE);
            }
        //}
    }

    /**
     * Constructs a new matrix with the given dimensions and factory, and
     * sets the indices in the given set to the NumericValue specified.
     */
    protected NumericMatrix(Dimensions dims, NumericFactory factory, IntSet indices, NumericValue v) {
        this.dims = dims;
        this.factory = factory;
        this.cells = new HomogenousSequence<NumericValue>(v, indices);
    }

    /**
     * Returns the dimensions of this matrix.
     *
     * @return this.dimensions
     */
    public final Dimensions dimensions() {
        return dims;
    }

    /**
     * Returns the factory used to construct all the non-ZERO entries in this
     * matrix.
     *
     * @return this.factory
     */
    public final NumericFactory factory() {
        return factory;
    }

    /**
     * Returns the number of non-ZERO entries in this matrix.
     *
     * @return #this.elements.(NumericValue - ZERO)
     */
    public final int density() {
        return cells.size();
    }

    /**
     * Returns an IndexedEntry-based view of the non-ZERO entries in this matrix.
     * @return an iterator over IndexedEntries representing the non-FALSE entries in
     *         this matrix.
     */
    @Override
    public final Iterator<IndexedEntry<NumericValue>> iterator() {
        return cells.iterator();
    }

    /**
     * Returns the set of all indices in this matrix that contain non-ZERO values.
     *
     * @return the set of all indices in this matrix that contain non-ZERO values.
     */
    public final IntSet denseIndices() {
        return cells.indices();
    }

    /**
     * Return ZERO if value is null; otherwise return value itself.
     *
     * @return ZERO if value is null; otherwise return value itself.
     */
    private final NumericValue maskNull(NumericValue value) {
        return value == null ? ZERO : value;
    }

    /**
     * Returns the value at the given index, without checking that the index is in
     * bounds.
     *
     * @return this.elements[index]
     */
    private final NumericValue fastGet(final int index) {
        return maskNull(cells.get(index));
    }

    /**
     * Returns the element at the specified index.
     *
     * @return this.elements[index]
     * @throws IndexOutOfBoundsException index < 0 || index >=
     *             this.dimensions.capacity
     */
    public final NumericValue get(final int index) {
        if (!dims.validate(index))
            throw new IndexOutOfBoundsException(index + " is not a valid index.");
        return maskNull(cells.get(index));
    }

    /**
     * @throws IllegalArgumentException f != this.factory
     */
    private static final void checkFactory(NumericFactory f0, NumericFactory f1) {
        if (f0 != f1)
            throw new IllegalArgumentException("Incompatible factories: " + f0 + " and " + f1);
    }

    /**
     * @throws IllegalArgumentException !d0.equals(d1)
     */
    private static final void checkDimensions(Dimensions d0, Dimensions d1) {
        if (!d0.equals(d1))
            throw new IllegalArgumentException("Incompatible dimensions: " + d0 + " and " + d1);
    }

    /**
     * Returns true if the elements of this matrix are constrained to be {0, 1}-valued.
     * @return false
     */
    public boolean isBoolean(){ return false; }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * bounded addition of the corresponding entries in this and other matrix.
     * @return this.cells + other.cells
     */
    public final NumericMatrix plus(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        final NumericMatrix ret = new NumericMatrix(dims, factory, cells, other.cells);

        final SparseSequence<NumericValue> s1 = other.cells;

        for (IndexedEntry<NumericValue> e0 : cells) {
            NumericValue v1 = s1.get(e0.index());
            if (v1 != null) {
                NumericValue a = factory.plus(e0.value(), v1);
                ret.fastSet(e0.index(), factory.ite(factory.gte(a, ONE), ONE, a)); // r[i] = min(M[i] + N[i], 1)
            }
            else ret.fastSet(e0.index(), e0.value()); // r[i] = M[i]
        }

        for (IndexedEntry<NumericValue> e1 : s1) {
            NumericValue v0 = cells.get(e1.index());
            if (v0 == null)
                ret.fastSet(e1.index(), e1.value()); // r[i] = N[i]
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * bounded addition of the corresponding entries in this and the other matrices.
     * @return this.cells + sum other : others | other.cells
     */
    public final NumericMatrix plus(final NumericMatrix... others) {
        final NumericMatrix ret = new NumericMatrix(dims, this, others);

        // Every non-zero entry of, at least, one matrix
        IntSet entries = new IntTreeSet();
        entries.addAll(this.denseIndices());
        for(NumericMatrix other : others)
            entries.addAll(other.denseIndices());

        IntIterator it = entries.iterator();
        while(it.hasNext()){
            int idx = it.next();
            final NumericAccumulator acc = NumericAccumulator.treeGate(PLUS, this.fastGet(idx));
            for (NumericMatrix other : others) {
                acc.add(other.fastGet(idx));
            }
            NumericValue sum = factory.accumulate(acc);
            ret.fastSet(idx, factory.ite(factory.gt(sum, ONE), ONE, sum));
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * difference of the corresponding entries in this and other matrix.
     * @return this.cells != 0 => this.cells - min(this.cells, other.cells)
     */
    public final NumericMatrix difference(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = isB1 && isB2 ?
                new BinaryMatrix(dims, factory) : // TODO
                new NumericMatrix(dims, factory, cells, other.cells);

        BiFunction<NumericValue, NumericValue, NumericValue> diff = isB1 && isB2 ?
                (b1, b2) -> factory.toBinary(factory.and(toBool(b1), toBool(b2).negation())) :
                (x1, x2) -> {
                    NumericValue n1 = this.toNumeric(x1);
                    NumericValue n2 = this.toNumeric(x2);
                    return factory.implies(factory.neq(n1, ZERO), factory.minus(n1, factory.minZero(n1, n2)));
                };

        final SparseSequence<NumericValue> s1 = other.cells;
        NumericValue v1, r;
        for (IndexedEntry<NumericValue> e0 : cells) {
            v1 = s1.get(e0.index());
            if (v1 != null) {
                r = diff.apply(e0.value(), v1); // r[i] = M[i] != 0? M[i] - min(M[i], N[i]) : 0
                if(r != ZERO) ret.fastSet(e0.index(), r);
            }
            else ret.fastSet(e0.index(), toNumeric(e0.value())); // r[i] = M[i]
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * bounded subtraction of the corresponding entries in this and other matrix.
     * @return this.cells - other.cells
     */
    public final NumericMatrix minus(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        final NumericMatrix ret = new NumericMatrix(dims, factory, cells, other.cells);

        final SparseSequence<NumericValue> s1 = other.cells;
        for (IndexedEntry<NumericValue> e0 : cells) {
            NumericValue v1 = s1.get(e0.index());
            if (v1 != null) {
                NumericValue r =  factory.minus(toNumeric(e0.value()), toNumeric(v1)); // r[i] = M[i] - N[i]
                r = factory.ite(factory.lt(r, ZERO), ZERO, r); // r[i] = max(0, r[i])
                if(r != ZERO) ret.fastSet(e0.index(), r);
            }
            else ret.fastSet(e0.index(), toNumeric(e0.value())); // r[i] = M[i]
        }

        for (IndexedEntry<NumericValue> e1 : s1){
            if(cells.get(e1.index()) == null)
                ret.fastSet(e1.index(), factory.negate(toNumeric(e1.value()))); // r[i] = - N[i]
        }

        return ret;
    }

    /**
     * Helper method to specify the numeric multiplication operation between two numeric values
     * that better takes advantage of their respective matrix kind.
     *
     * @param isB1 is true iff the leftmost matrix is a BinaryMatrix
     * @param isB2 is true iff the rightmost matrix is a BinaryMatrix
     * @return multiplication method
     */
    private BiFunction<NumericValue, NumericValue, NumericValue> times(boolean isB1, boolean isB2){
        BiFunction<NumericValue, NumericValue, NumericValue> f;
        if(isB1 && isB2)
            // b1 x b2 = b1 && b2
            f = (b1, b2) -> factory.toBinary(factory.and(toBool(b1), toBool(b2)));
        else if(isB1 || isB2)
            // b x n = n x b = b ? n : 0
            f = (x1, x2) -> {
                BooleanValue b = isB1 ? toBool(x1) : toBool(x2);
                NumericValue n = isB1 ? x2 : x1;
                return factory.ite(b, n, ZERO);
            };
        else // n1 x n2 = n1 * n2
            f = factory :: times;

        return f;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * Hadamard product of the corresponding entries in this and other matrix.
     * @return this.cells ⊙ other.cells
     */
    public final NumericMatrix product(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = isB1 && isB2 ?
                new BinaryMatrix(dims, factory) : // TODO
                new NumericMatrix(dims, factory, cells, other.cells);

        BiFunction<NumericValue, NumericValue, NumericValue> times = times(isB1, isB2);

        final SparseSequence<NumericValue> s1 = other.cells;

        for (IndexedEntry<NumericValue> e0 : cells) {
            NumericValue v1 = s1.get(e0.index());
            if (v1 != null)
                ret.fastSet(e0.index(), times.apply(e0.value(), v1));
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * Hadamard product of the corresponding entries in this and the other matrices.
     * @return this.cells ⊙ others[0].cells ⊙ ... ⊙ others[others.length-1].cells
     */
    public final NumericMatrix product(final NumericMatrix... others) {
        final NumericMatrix ret = new NumericMatrix(dims, this, others);

        for (IndexedEntry<NumericValue> cell : cells) {
            final NumericAccumulator acc = NumericAccumulator.treeGate(TIMES, cell.value());
            for (NumericMatrix other : others) {
                acc.add(other.fastGet(cell.index()));
            }
            ret.fastSet(cell.index(), factory.accumulate(acc));
        }
        return ret;
    }

    /**
     * Returns the result of multiplying every element of this matrix by the given scalar.
     * @return scalar . this.cells
     */
    public final NumericMatrix product(NumericConstant scalar) {
        if(scalar == ONE)
            return this.clone();

        final NumericMatrix ret = this.isBoolean() && scalar == ZERO ?
                new BinaryMatrix(dims, factory) :
                new NumericMatrix(dims, factory, cells, cells);

        if(this.cells.isEmpty() || scalar == ZERO)
            return ret;

        for (IndexedEntry<NumericValue> e0 : cells) {
            ret.fastSet(e0.index(), factory.times(e0.value(), scalar));
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * Hadamard (bounded) division of the corresponding entries in this and other matrix.
     * Division by zero detected by constant circuits or by the solver
     * result in UNSAT instances.
     *
     * @return this.cells ⊘ other.cells
     */
    public final NumericMatrix divide(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = !isB1 || !isB2 ?
                new NumericMatrix(dims, factory, cells, other.cells) :
                new BinaryMatrix(dims, factory); // TODO

        Function<NumericValue, NumericValue> dividend, divisor;
        dividend = x -> isB1 ? toNumeric(x) : x;
        divisor = x -> isB2 ? toNumeric(x) : x;

        for (IndexedEntry<NumericValue> e0 : this.cells) {
            NumericValue v1 = other.cells.get(e0.index());
            if (v1 != null) {
                NumericValue div = factory.divide(dividend.apply(e0.value()), divisor.apply(v1));
                ret.fastSet(e0.index(), factory.ite(factory.gt(div, ONE), ONE, div));
            }
            // Division by zero
            else throw new IllegalArgumentException("Division by zero is undefined.");
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * Hadamard (bounded) division of the corresponding entries in this and the other matrices.
     *
     * @return this.cells ⊘ others[0].cells ⊘ ... ⊘ others[others.length-1].cells
     */
    public final NumericMatrix divide(final NumericMatrix... others) {
        final NumericMatrix ret = new NumericMatrix(dims, this, others);

        for (IndexedEntry<NumericValue> cell : cells) {
            final NumericAccumulator acc = NumericAccumulator.treeGate(DIV, cell.value());
            for (NumericMatrix other : others) {
                acc.add(other.fastGet(cell.index()));
            }
            NumericValue divs = factory.accumulate(acc);
            ret.fastSet(cell.index(), factory.ite(factory.gt(divs, ONE), ONE, divs));
        }
        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * intersection of the corresponding entries in this and the other matrix.
     * @return tnorm(this.cells, other.cells)
     */
    public final NumericMatrix intersection(NumericMatrix other){
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = isB1 && isB2 ?
                new BinaryMatrix(dims, factory) : // TODO
                new NumericMatrix(dims, factory, cells, other.cells);

        BiFunction<NumericValue, NumericValue, NumericValue> intersection;
        if(isB1 && isB2)
            // b1 & b2 = b1 && b2
            intersection = (b1, b2) -> factory.toBinary(factory.and(toBool(b1), toBool(b2)));
        /*else if(isB1 || isB2)
            // n & b = b & n = b && n > 0 ? 1 : 0
            intersection = (x1, x2) -> {
                BooleanValue b = isB1 ? this.toBool(x1) : this.toBool(x2);
                NumericValue n = isB1 ? x2 : x1;
                return factory.implies(factory.and(b, factory.gt(n, ZERO)), ONE);
            };*/
        else // n1 & n2 = min(n1, n2)
            //intersection = factory :: minimum;
            intersection = (x1, x2) -> factory.tnorm(toNumeric(x1), toNumeric(x2));

        final SparseSequence<NumericValue> s1 = other.cells;

        if (cells.isEmpty() || s1.isEmpty())
            return ret;

        for (IndexedEntry<NumericValue> e0 : cells) {
            NumericValue v1 = s1.get(e0.index());
            if (v1 != null)
                ret.fastSet(e0.index(), intersection.apply(e0.value(), v1));
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * union of the corresponding entries in this and the other matrix.
     * @return max(this.cells, other.cells)
     */
    public final NumericMatrix union(NumericMatrix other){
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = isB1 && isB2 ?
                new BinaryMatrix(dims, factory) : // TODO
                new NumericMatrix(dims, factory, cells, other.cells);

        BiFunction<NumericValue, NumericValue, NumericValue> union;
        if(isB1 && isB2)
            // b1 + b2 = b1 or b2
            union = (b1, b2) -> factory.toBinary(factory.or(toBool(b1), toBool(b2)));
        else /*if(isB1 || isB2)
            // n + b = b + n = n >= 1 ? n : lift(b)
            union = (x1, x2) -> {
              NumericValue b = isB1 ? this.toNumeric(x1) : this.toNumeric(x2);
              NumericValue n = isB1 ? x2 : x1;
              return factory.ite(factory.gte(n, ONE), n, b);
            };
        else*/ // n1 + n2 = max(n1, n2)
            union = (x0, x1) -> factory.tconorm(toNumeric(x0), toNumeric(x1));

        final SparseSequence<NumericValue> s1 = other.cells;

        for (IndexedEntry<NumericValue> e0 : cells) {
            NumericValue v1 = s1.get(e0.index());
            if (v1 != null)
                ret.fastSet(e0.index(), union.apply(e0.value(), v1));
            else ret.fastSet(e0.index(), e0.value());
        }

        //Remaining cells of other
        for (IndexedEntry<NumericValue> e1 : s1) {
            NumericValue v0 = cells.get(e1.index());
            if (v0 == null)
                ret.fastSet(e1.index(), toNumeric(e1.value()));
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * intersection of the corresponding entries in this and the other matrix,
     * wrt to this matrix non-zero values.
     * @return ret | all i : this.cells.indices | ret[i] = max(this.cells[i], other.cells[i])
     */
    public final NumericMatrix leftIntersection(NumericMatrix other){
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        final NumericMatrix ret = new NumericMatrix(dims, factory, cells, other.cells);

        final SparseSequence<NumericValue> s1 = other.cells;

        if (cells.isEmpty() || s1.isEmpty())
            return ret;

        for (IndexedEntry<NumericValue> e0 : cells) {
            NumericValue v1 = s1.get(e0.index());
            if (v1 != null){
                BooleanValue nonZero = factory.and(factory.neq(e0.value(), ZERO), factory.neq(v1, ZERO));
                if(nonZero != FALSE) ret.fastSet(e0.index(), factory.ite(nonZero, factory.maximum(e0.value(), v1), ZERO));
            }
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * intersection of the corresponding entries in this and the other matrix,
     * wrt to the given matrix non-zero values.
     * @return ret | all i : other.cells.indices | ret[i] = max(this.cells[i], other.cells[i])
     */
    public final NumericMatrix rightIntersection(NumericMatrix other){
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        final NumericMatrix ret = new NumericMatrix(dims, factory, cells, other.cells);

        final SparseSequence<NumericValue> s1 = other.cells;

        if (cells.isEmpty() || s1.isEmpty())
            return ret;

        for (IndexedEntry<NumericValue> e1 : s1) {
            NumericValue v0 = cells.get(e1.index());
            if (v0 != null){
                BooleanValue nonZero = factory.and(factory.neq(e1.value(), ZERO), factory.neq(v0, ZERO));
                if(nonZero != FALSE) ret.fastSet(e1.index(), factory.ite(nonZero, factory.maximum(e1.value(), v0), ZERO));
            }
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * intersection of the corresponding entries in this and the other matrices.
     * @return tnorm(this.cells, other[0].cells, other[1].cells, .., other[n].cells)
     */
    public final NumericMatrix intersection(final NumericMatrix... others) {
        final NumericMatrix ret = new NumericMatrix(dims, this, others);

        for (IndexedEntry<NumericValue> cell : cells) {
            NumericValue min = cell.value();
            for (NumericMatrix other : others) {
                min = this.factory.tnorm(min, other.fastGet(cell.index()));
                if(min == ZERO)
                    break;
            }
            if(min != ZERO) ret.fastSet(cell.index(), min);
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * union of the corresponding entries in this and the other matrices,
     * @return max(this.cells, other[0].cells, other[1].cells, .., other[n].cells)
     */
    public final NumericMatrix union(final NumericMatrix... others) {
        final NumericMatrix ret = new NumericMatrix(dims, this, others);
        Set<Integer> visited = new TreeSet<>();

        for (IndexedEntry<NumericValue> cell : cells) {
            NumericValue max = cell.value();
            for (NumericMatrix other : others) {
                max = factory.tconorm(max, other.fastGet(cell.index()));
            }
            ret.fastSet(cell.index(), max);
            visited.add(cell.index());
        }

        for(int i = 0; i < others.length; i++)
            for(IndexedEntry<NumericValue> cell : others[i])
                if(!visited.contains(cell.index())){
                    NumericValue max = cell.value();
                    for (int j = i + 1; j < others.length; j++) {
                        max = factory.tconorm(max, others[j].fastGet(cell.index()));
                    }
                    ret.fastSet(cell.index(), max);
                    visited.add(cell.index());
                }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * intersection of the corresponding entries in this and the other matrices,
     * wrt to the leftmost matrix non-zero values.
     * @return ret | all i : this.cells.indices | ret[i] = max(this.cells[i], others[0].cells[i], others[1].cells[i], .., others[n].cells[i])
     */
    public final NumericMatrix leftIntersection(final NumericMatrix... others) {
        final NumericMatrix ret = new NumericMatrix(dims, this, others);

        for (IndexedEntry<NumericValue> cell : cells) {
            NumericValue max = cell.value();
            BooleanValue nonZero = factory.neq(cell.value(), ZERO);

            for (NumericMatrix other : others) {
                NumericValue otherValue = other.fastGet(cell.index());
                if(otherValue == ZERO){
                    nonZero = FALSE;
                    break;
                }

                max = factory.maximum(max, otherValue);
                nonZero = factory.and(nonZero, otherValue);
            }

            if(nonZero != FALSE) ret.fastSet(cell.index(), factory.ite(nonZero, max, ZERO));
        }

        return ret;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * intersection of the corresponding entries in this and the other matrices,
     * wrt to the rightmost matrix non-zero values.
     * @return ret | all i : others[n].cells.indices | ret[i] = max(this.cells[i], others[0].cells[i], others[1].cells[i], .., others[n].cells[i])
     */
    public final NumericMatrix rightIntersection(final NumericMatrix... others) {
        final NumericMatrix ret = new NumericMatrix(dims, this, others);

        for (IndexedEntry<NumericValue> cell : others[others.length - 1]) {
            NumericValue max = cell.value();
            max = factory.maximum(max, this.fastGet(cell.index()));

            BooleanValue nonZero = factory.neq(cell.value(), ZERO);
            nonZero = factory.and(nonZero, factory.neq(this.fastGet(cell.index()), ZERO));

            for (int i = 0; nonZero != FALSE && i < others.length - 2; i++){
                NumericValue otherValue = others[i].fastGet(cell.index());
                max = factory.maximum(max, otherValue);
                nonZero = factory.and(nonZero, otherValue);
            }

            if(nonZero != FALSE) ret.fastSet(cell.index(), factory.ite(nonZero, max, ZERO));
        }

        return ret;
    }

    /**
     * Given a vector, constructs a new matrix containing this matrix's entries whose
     * value in the last dimension occurs in that vector.
     * @return v : other | v != 0 => ret[0, 1, .., v] = this[0, 1, .., v]
     */
    public final NumericMatrix range(NumericMatrix other){
        checkFactory(this.factory, other.factory);
        if(other.dims.numDimensions() > 1)
            throw new IllegalArgumentException("The given matrix must be a vector, instead has dimensions:" + other.dims);

        final NumericMatrix ret = !this.isBoolean() ?
                new NumericMatrix(dims, factory, cells, cells) :
                new BinaryMatrix(dims, factory); // TODO

        final SparseSequence<NumericValue> s1 = other.cells;

        if (cells.isEmpty() || s1.isEmpty())
            return ret;

        Function<NumericValue, BooleanValue> occurrence = other.isBoolean() ? this :: toBool : n -> factory.neq(n, ZERO);
        int cap = dims.capacity();
        int nDim = dims.numDimensions();
        int d = dims.dimension(nDim - 1);
        for (IndexedEntry<NumericValue> e1 : s1) {
            int i = e1.index();
            while(i < cap){
                NumericValue cell = cells.get(i);
                if (cell != null)
                    ret.set(i, factory.implies(occurrence.apply(e1.value()), cell));
                // Iterate over the last dimension
                i += d;
            }
        }

        return ret;
    }

    /**
     * Given a vector, constructs a new matrix containing this matrix's entries whose
     * value in the first dimension occurs in that vector.
     * @return v : other | v != 0 => ret[v, 1, .., n-1] = this[v, 1, .., n-1]
     */
    public final NumericMatrix domain(NumericMatrix other){
        checkFactory(this.factory, other.factory);
        if(other.dims.numDimensions() > 1)
            throw new IllegalArgumentException("The given matrix must be a vector, instead has dimensions:" + other.dims);

        final NumericMatrix ret = !this.isBoolean() ?
                new NumericMatrix(dims, factory, cells, cells) :
                new BinaryMatrix(dims, factory); // TODO

        final SparseSequence<NumericValue> s1 = other.cells;

        if (cells.isEmpty() || s1.isEmpty())
            return ret;

        Function<NumericValue, BooleanValue> occurrence = other.isBoolean() ? this :: toBool : n -> factory.neq(n, ZERO);
        int nDim = dims.numDimensions();
        int rowSize = 1;
        for(int d = 1; d < nDim; d++)
            rowSize *= dims.dimension(d);

        for (IndexedEntry<NumericValue> e1 : s1) {
            // Find the index wrt to this matrix dimensions
            int i = e1.index() * rowSize;
            // Iterate over the row that starts on that index
            for(int p = 0; p < rowSize; p++){
                NumericValue cell = cells.get(p + i);
                if (cell != null)
                    ret.set(p + i, factory.implies(occurrence.apply(e1.value()), cell));
            }
        }

        return ret;
    }


    /**
     * Returns the cross product of this and other matrix.
     *
     * @return { m: NumericMatrix | m = this x other }
     * @throws NullPointerException other = null
     * @throws IllegalArgumentException this.factory != other.factory
     */
    public final NumericMatrix cross(final NumericMatrix other) {
        checkFactory(this.factory, other.factory);

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = isB1 && isB2 ?
                new BinaryMatrix(dims.cross(other.dims), factory) : // TODO
                new NumericMatrix(dims.cross(other.dims), factory, cells, other.cells);

        if (cells.isEmpty() || other.cells.isEmpty())
            return ret;

        BiFunction<NumericValue, NumericValue, NumericValue> times = times(isB1, isB2);
        final int ocap = other.dims.capacity();
        for (IndexedEntry<NumericValue> e0 : cells) {
            int i = ocap * e0.index();
            for (IndexedEntry<NumericValue> e1 : other.cells) {
                NumericValue multiplication = factory.factoryType() == QuantitativeOptions.QuantitativeType.FUZZY ?
                        factory.tnorm(e0.value(), e1.value()) :
                        times.apply(e0.value(), e1.value());
                if (multiplication != ZERO)
                    ret.cells.put(i + e1.index(), multiplication);
            }
        }

        return ret;
    }

    /**
     * Updates the itrs and idxs arrays for the next step of the cross-product
     * computation and returns a partial index based on the updated idxs values.
     *
     * @requires matrices.length = itrs.length = idxs.length
     * @requires all m: matrices[int] | m.density() > 0
     * @requires currentIdx is a partial index based on the current value of idxs
     * @ensures updates the itrs and idxs arrays for the next step cross-product
     *          computation
     * @return a partial index based on the freshly updated idxs values.
     */
    private static int nextCross(final NumericMatrix[] matrices, final IntIterator[] itrs, final int[] idxs, int currentIdx) {

        int mult = 1;
        for (int i = itrs.length - 1; i >= 0; i--) {
            if (itrs[i].hasNext()) {
                final int old = idxs[i];
                idxs[i] = itrs[i].next();
                return currentIdx - mult * old + mult * idxs[i];
            } else {
                itrs[i] = matrices[i].cells.indices().iterator();
                final int old = idxs[i];
                idxs[i] = itrs[i].next();
                currentIdx = currentIdx - mult * old + mult * idxs[i];
                mult *= matrices[i].dims.capacity();
            }
        }

        return -1;
    }

    /**
     * Initializes the itrs and idxs arrays for cross-product computation and
     * returns a partial index based on the freshly computed idxs values.
     *
     * @requires matrices.length = itrs.length = idxs.length
     * @requires all m: matrices[int] | m.density() > 0
     * @ensures initializes the itrs and idxs arrays for cross-product computation
     * @return a partial index based on the freshly computed idxs values.
     */
    private static int initCross(final NumericMatrix[] matrices, final IntIterator[] itrs, final int[] idxs) {
        int mult = 1, idx = 0;
        for (int i = matrices.length - 1; i >= 0; i--) {
            itrs[i] = matrices[i].cells.indices().iterator();
            idxs[i] = itrs[i].next();
            idx += mult * idxs[i];
            mult *= matrices[i].dims.capacity();
        }
        return idx;
    }

    /**
     * Returns the cross product of this and other matrices.
     *
     * @requires this.factory = others[int].factory
     * @return others.length=0 => { m: NumericMatrix | m.dimensions =
     *         this.dimensions && no m.elements } else { m: NumericMatrix | m = this
     *         x others[0] x ... x others[others.length-1] }
     * @throws NullPointerException others = null
     * @throws IllegalArgumentException this.factory != others[int].factory
     */
    public final NumericMatrix cross(final NumericMatrix... others) {
        Dimensions retDims = dims;
        boolean empty = cells.isEmpty();
        for (NumericMatrix other : others) {
            retDims = retDims.cross(other.dims);
            empty = empty || other.cells.isEmpty();
        }

        final NumericMatrix ret = new NumericMatrix(retDims, this, others);
        if (empty)
            return ret;

        final IntIterator[] itrs = new IntIterator[others.length];
        final int[] otherIdxs = new int[others.length];

        final int ocap = retDims.capacity() / dims.capacity();

        for (IndexedEntry<NumericValue> cell : cells) {
            final int idx = ocap * cell.index();
            for (int restIdx = initCross(others, itrs, otherIdxs); restIdx >= 0; restIdx = nextCross(others, itrs, otherIdxs, restIdx)) {
                final NumericAccumulator acc = NumericAccumulator.treeGate(TIMES, cell.value());
                for (int i = others.length - 1; i >= 0; i--) {
                    if (acc.add(others[i].fastGet(otherIdxs[i])) == ZERO)
                        break;
                }
                ret.fastSet(idx + restIdx, factory.accumulate(acc));
            }

        }

        return ret;
    }

    /**
     * Sets the value at the specified index to the given value; returns the value
     * previously at the specified position. It performs no index or null checking.
     *
     * @ensures this.elements'[index] = value
     */
    private final void fastSet(final int index, final NumericValue value) {
        if (value == ZERO)
            cells.remove(index);
        else
            cells.put(index, value);
    }

    /**
     * Returns the dot product of this and other matrix.
     *
     * @return { m: NumericMatrix | m = this*other }
     * @throws NullPointerException other = null
     * @throws IllegalArgumentException this.factory != other.factory
     * @throws IllegalArgumentException dimensions incompatible for multiplication
     */
    public final NumericMatrix multiDot(final NumericMatrix other) {
        checkFactory(this.factory, other.factory);

        final NumericMatrix ret = new NumericMatrix(dims.dot(other.dims), factory, cells, other.cells);

        if (cells.isEmpty() || other.cells.isEmpty())
            return ret;

        final SparseSequence<NumericValue> mutableCells = ret.clone().cells;
        final int b = other.dims.dimension(0);
        final int c = other.dims.capacity() / b;

        BiFunction<NumericValue, NumericValue, NumericValue> times = times(this.isBoolean(), other.isBoolean());

        for (IndexedEntry<NumericValue> e0 : cells) {
            int i = e0.index();
            NumericValue iVal = e0.value();
            int rowHead = (i % b) * c, rowTail = rowHead + c - 1;
            for (Iterator<IndexedEntry<NumericValue>> iter1 = other.cells.iterator(rowHead, rowTail); iter1.hasNext();) {
                IndexedEntry<NumericValue> e1 = iter1.next();
                NumericValue retVal = times.apply(iVal, e1.value());
                if (retVal != ZERO) {
                    int k = (i / b) * c + e1.index() % c;
                    NumericValue kVal = mutableCells.get(k);
                    if (kVal == null) {
                        kVal = NumericAccumulator.treeGate(PLUS);
                        mutableCells.put(k, kVal);
                    }
                    ((NumericAccumulator) kVal).add(retVal);
                }
            }
        }

        // make mutable gates immutable
        for (IndexedEntry<NumericValue> e : mutableCells) {
            ret.fastSet(e.index(), factory.accumulate((NumericAccumulator) e.value()));
        }

        return ret;
    }

    /**
     * Returns the min-max product of this and other matrix.
     *
     * @return { m: NumericMatrix | m = this.other }
     * @throws NullPointerException other = null
     * @throws IllegalArgumentException this.factory != other.factory
     * @throws IllegalArgumentException dimensions incompatible for multiplication
     */
    public final NumericMatrix dot(final NumericMatrix other) {
        checkFactory(this.factory, other.factory);

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = isB1 && isB2 ?
                new BinaryMatrix(dims.dot(other.dims), factory) : // TODO
                new NumericMatrix(dims.dot(other.dims), factory, cells, other.cells);

        if (cells.isEmpty() || other.cells.isEmpty())
            return ret;

        BiFunction<NumericValue, NumericValue, NumericValue> addition, multiplication;

        if(isB1 && isB2){
            // b1 + b2 = b1 or b2
            addition = (b1, b2) -> factory.toBinary(factory.or(toBool(b1), toBool(b2)));
            // b1 * b2 = b1 and b2
            multiplication = (b1, b2) -> factory.toBinary(factory.and(toBool(b1), toBool(b2)));
        }/*else if(isB1 || isB2){
            // Since it is the outermost operation, its inputs will be numeric
            // n1 + n2 = max(n1, n2)
            addition = factory :: maximum;
            // n * b = b * n = b && n > 0 ? 1 : (b && n < 0 ? n : 0) // TODO ?
            multiplication = (b1, b2) -> {
                BooleanValue b = isB1 ? toBool(b1) : toBool(b2);
                NumericValue n = isB1 ? b2 : b1;
                // return factory.implies(factory.and(b, factory.gt(n, ZERO)), ONE);
                return factory.ite(factory.and(b, factory.gt(n, ZERO)), ONE, factory.implies(factory.and(b, factory.lt(n, ZERO)), n));
            };
        }*/else{ // both matrices are numeric
            // n1 + n2 = max(n1, n2)
            addition = (x1, x2) -> factory.join(toNumeric(x1), toNumeric(x2));
            // n1 * n2 = min(n1, n2)
            multiplication = (x1, x2) -> factory.meet(toNumeric(x1), toNumeric(x2));
        }

        final int b = other.dims.dimension(0);
        final int c = other.dims.capacity() / b;

        for (IndexedEntry<NumericValue> e0 : cells) {
            int i = e0.index();
            NumericValue iVal = e0.value();
            int rowHead = (i % b) * c, rowTail = rowHead + c - 1;
            for (Iterator<IndexedEntry<NumericValue>> iter1 = other.cells.iterator(rowHead, rowTail); iter1.hasNext();) {
                IndexedEntry<NumericValue> e1 = iter1.next();
                NumericValue retVal = multiplication.apply(iVal, e1.value());
                if (retVal != ZERO) {
                    int k = (i / b) * c + e1.index() % c;
                    NumericValue kVal = ret.get(k);
                    ret.fastSet(k,  kVal == ZERO ? retVal : addition.apply(kVal, retVal));
                }
            }
        }

        return ret;
    }

    /**
     * Returns the transitive closure of this matrix.
     *
     * @return { m: NumericMatrix | m = ^this }
     * @throws UnsupportedOperationException #this.dimensions != 2 ||
     *             !this.dimensions.square()
     */
    public final NumericMatrix closure() {
        if (dims.numDimensions() != 2 || !dims.isSquare()) {
            throw new UnsupportedOperationException("#this.dimensions != 2 || !this.dimensions.square()");
        }
        if (cells.isEmpty())
            return clone();

        NumericMatrix ret = this;

        // compute the number of rows in the matrix
        int rowNum = 0;
        final int rowFactor = dims.dimension(1);
        for (IndexedEntry<NumericValue> rowLead = cells.first(); rowLead != null; rowLead = cells.ceil(((rowLead.index() / rowFactor) + 1) * rowFactor)) {
            rowNum++;
        }

        // compute closure using iterative squaring TODO
        for (int i = 1; i < rowNum; i *= 2) {
            ret = ret.union(ret.dot(ret));
        }

        return ret == this ? clone() : ret;
    }

    /**
     * Returns the reflexive transitive closure of this matrix.
     * {@code fpEq} will be updated to contain the fixed point equations
     * necessary to define the transitive closure.
     *
     * @return { m: NumericMatrix | m = *this }
     * @throws UnsupportedOperationException #this.dimensions != 2 ||
     *             !this.dimensions.square()
     * @throws NullPointerException fpEq = null
     */
    public final NumericMatrix reflexiveClosure(List<BooleanValue> fpEq) {
        if (dims.numDimensions() != 2 || !dims.isSquare()) {
            throw new UnsupportedOperationException("#this.dimensions != 2 || !this.dimensions.square()");
        }

        final NumericMatrix ret = new NumericMatrix(dims, factory);
        // *0 = id
        if(cells.isEmpty()){
            int n = dims.dimension(0);
            for(int i = 0; i < n; i++)
                ret.set(i * n + i, ONE);
        }else if(dims.dimension(0) == 1) {
            NumericValue t = this.fastGet(0);
            t = factory.maximum(t, ONE);
            ret.fastSet(0, t);
        }else{
            int n = dims.dimension(0);
            int m = n % 2 == 0 ? n/2 : n/2 + 1;
            int p = n - m;

            // Dividing this matrix into four submatrices
            NumericMatrix a11 = new NumericMatrix(Dimensions.square(m, 2), factory);
            NumericMatrix a22 = new NumericMatrix(Dimensions.square(p, 2), factory);
            NumericMatrix a12 = new NumericMatrix(Dimensions.rectangular(new int[]{m, p}), factory);
            NumericMatrix a21 = new NumericMatrix(Dimensions.rectangular(new int[]{p, m}), factory);

            int i, col, row;
            int cap = dims.capacity();
            int rowSize = cap / dims.dimension(0);

            // Fill each submatrix accordingly
            for(IndexedEntry<NumericValue> e : cells){
                i = e.index();
                col = i % n;
                row = i / rowSize;

                if(col < m && row < m)
                    a11.set(m * row + col, e.value());
                else if(col >= m && row >= m)
                    a22.set(p * (row - m) + col - m, e.value());
                else if(col < m)  // && row >= m
                    a21.set((row - m) * m + col, e.value());
                else a12.set(row * p + col - m, e.value()); // col >= m && row < m
            }

            // Assign the values of the resulting closure submatrices to the return matrix
            BiFunction<Integer, NumericMatrix, Void> assignClosure = (s, a) -> {
                Dimensions d = a.dims;
                // Determine the pair <row, col> in the current submatrix
                Function<Integer, Pair<Integer, Integer>> getEntry =
                        idx -> new Pair<>(idx / (d.capacity() / d.dimension(0)), idx % d.dimension(1));
                // Transforms the flat index of the submatrix into the correspondent index in the original matrix
                Function<Integer, Integer> toIndex;
                switch (s) {
                    case 0:
                        toIndex = idx -> {
                            Pair<Integer, Integer> e = getEntry.apply(idx);
                            return n * e.a + e.b;
                        };
                        break;
                    case 1:
                        toIndex = idx -> {
                            Pair<Integer, Integer> e = getEntry.apply(idx);
                            return n * (m + e.a) + e.b;
                        };
                        break;
                    case 2:
                        toIndex = idx -> {
                            Pair<Integer, Integer> e = getEntry.apply(idx);
                            return e.a * n + m + e.b;
                        };
                        break;
                    default: // case 3
                        toIndex = idx -> {
                            Pair<Integer, Integer> e = getEntry.apply(idx);
                            return n * (m + e.a) + m + e.b;
                        };
                        break;
                }

                for(IndexedEntry<NumericValue> e : a.cells)
                    ret.set(toIndex.apply(e.index()), e.value());
                return null;
            };

            // Matrices containing the reflexive transitive closure per block
            NumericMatrix a11star = a11.reflexiveClosure(fpEq);
            NumericMatrix a22star = a22.reflexiveClosure(fpEq);
            // x11 = *(a11 + a12.*a22.a21)
            NumericMatrix x11 = a11.union(a12.dot(a22star).dot(a21)).reflexiveClosure(fpEq);
            assignClosure.apply(0, x11);
            // x22 = *(a22 + a21.*a11.a12)
            NumericMatrix x22 = a22.union(a21.dot(a11star).dot(a12)).reflexiveClosure(fpEq);
            assignClosure.apply(3, x22);
            // x12 = x11.a12.*a22
            NumericMatrix x12 = x11.dot(a12).dot(a22star);
            assignClosure.apply(2, x12);
            // x21 = x22.a21.*a11
            NumericMatrix x21 = x22.dot(a21).dot(a11star);
            assignClosure.apply(1, x21);

            // *a11.a12.x22 = x11.a12.*a22
            fpEq.add(x12.eq(a11star.dot(a12).dot(x22)));
            // *a22.a21.x11 = x22.a21.*a11
            fpEq.add(x21.eq(a22star.dot(a21).dot(x11)));

            // x11 = id + a11.x11 + a12.x21
            /*NumericMatrix idm = new NumericMatrix(Dimensions.square(m, 2), factory);
            for(int j = 0; j < m; j++)
                idm.set(j * m + j, ONE);
            fpEq.add(x11.eq(idm.union(a11.dotMinMax(x11)).union(a12.dotMinMax(x21))));
            // x12 = a11.x12 + a12.x22
            fpEq.add(x12.eq(a11.dotMinMax(x12).union(a12.dotMinMax(x22))));*/
            /*// Should be ensured by symmetry
            // x22 = id + a21.x12 + a22.x22
            NumericMatrix idp = new NumericMatrix(Dimensions.square(p, 2), factory);
            for(int j = 0; j < p; j++)
                idp.set(j * p + j, ONE);
            fpEq.add(x22.eq(idp.union(a21.dotMinMax(x12)).union(a22.dotMinMax(x22))));
            // x21 = a21.x11 + a22.x21
            fpEq.add(x21.eq(a21.dotMinMax(x11).union(a22.dotMinMax(x21))));*/

            NumericMatrix id = new NumericMatrix(Dimensions.square(n, 2), factory);
            for(int j = 0; j < n; j++)
                id.set(j * n + j, ONE);
            // ret = id + this . ret
            fpEq.add(ret.eq(id.union(this.dot(ret))));
        }

        return ret;
    }

    /**
     * Determines the submatrix {@code sub}, with dimensions {@code n} x {@code n}, of the identity matrix @{code iden},
     * from the row {@code start} forwards.
     */
    /*private void setSubIdentity(NumericMatrix iden, NumericMatrix sub, int n, int start){
        int N = iden.dims.dimension(0);
        int j;
        for(int i = 0; i < n; i++) {
            j = i + start;
            sub.set(i * n + i, iden.fastGet(j * N + j));
        }
    }

    /**
     * Helper method to determine the transitive closure of this, taking into account that it is a submatrix,
     * delimited by {@code start} and {@code end}.
     */
    /*private NumericMatrix reflexiveClosure(NumericMatrix iden, int start, int end, List<BooleanValue> fpEq){
        final NumericMatrix ret = new NumericMatrix(dims, factory);
        // *0 = id
        if(cells.isEmpty()){
            setSubIdentity(iden, ret, end - start + 1, start);
        }else if(dims.dimension(0) == 1) {
            NumericValue t = this.fastGet(0);
            t = factory.maximum(t, iden.fastGet(start * iden.dims.dimension(0) + start));
            ret.fastSet(0, t);
        }else{
            int n = dims.dimension(0);
            int m = n % 2 == 0 ? n/2 : n/2 + 1;
            int p = n - m;

            // Dividing this matrix into four submatrices
            NumericMatrix a11 = new NumericMatrix(Dimensions.square(m, 2), factory);
            NumericMatrix a22 = new NumericMatrix(Dimensions.square(p, 2), factory);
            NumericMatrix a12 = new NumericMatrix(Dimensions.rectangular(new int[]{m, p}), factory);
            NumericMatrix a21 = new NumericMatrix(Dimensions.rectangular(new int[]{p, m}), factory);

            int i, col, row;
            int cap = dims.capacity();
            int rowSize = cap / dims.dimension(0);

            // Fill each submatrix accordingly
            for(IndexedEntry<NumericValue> e : cells){
                i = e.index();
                col = i % n;
                row = i / rowSize;

                if(col < m && row < m)
                    a11.set(m * row + col, e.value());
                else if(col >= m && row >= m)
                    a22.set(p * (row - m) + col - m, e.value());
                else if(col < m)  // && row >= m
                    a21.set((row - m) * m + col, e.value());
                else a12.set(row * p + col - m, e.value()); // col >= m && row < m
            }

            // Assign the values of the resulting closure submatrices to the return matrix
            BiFunction<Integer, NumericMatrix, Void> assignClosure = (s, a) -> {
                Dimensions d = a.dims;
                // Determine the pair <row, col> in the current submatrix
                Function<Integer, Pair<Integer, Integer>> getEntry =
                        idx -> new Pair<>(idx / (d.capacity() / d.dimension(0)), idx % d.dimension(1));
                // Transforms the flat index of the submatrix into the correspondent index in the original matrix
                Function<Integer, Integer> toIndex;
                switch (s) {
                    case 0:
                        toIndex = idx -> {
                            Pair<Integer, Integer> e = getEntry.apply(idx);
                            return n * e.a + e.b;
                        };
                        break;
                    case 1:
                        toIndex = idx -> {
                            Pair<Integer, Integer> e = getEntry.apply(idx);
                            return n * (m + e.a) + e.b;
                        };
                        break;
                    case 2:
                        toIndex = idx -> {
                            Pair<Integer, Integer> e = getEntry.apply(idx);
                            return e.a * n + m + e.b;
                        };
                        break;
                    default: // case 3
                        toIndex = idx -> {
                            Pair<Integer, Integer> e = getEntry.apply(idx);
                            return n * (m + e.a) + m + e.b;
                        };
                        break;
                }

                for(IndexedEntry<NumericValue> e : a.cells)
                    ret.set(toIndex.apply(e.index()), e.value());
                return null;
            };

            // Matrices containing the reflexive transitive closure per block
            NumericMatrix a11star = a11.reflexiveClosure(iden, start, start + m - 1, fpEq);
            NumericMatrix a22star = a22.reflexiveClosure(iden, start + m, end, fpEq);
            // x11 = *(a11 + a12.*a22.a21)
            NumericMatrix x11 = a11.union(a12.dotMinMax(a22star).dotMinMax(a21)).reflexiveClosure(iden, start, start + m - 1, fpEq);
            assignClosure.apply(0, x11);
            // x22 = *(a22 + a21.*a11.a12)
            NumericMatrix x22 = a22.union(a21.dotMinMax(a11star).dotMinMax(a12)).reflexiveClosure(iden, start + m, end, fpEq);
            assignClosure.apply(3, x22);
            // x12 = x11.a12.*a22
            NumericMatrix x12 = x11.dotMinMax(a12).dotMinMax(a22star);
            assignClosure.apply(2, x12);
            // x21 = x22.a21.*a11
            NumericMatrix x21 = x22.dotMinMax(a21).dotMinMax(a11star);
            assignClosure.apply(1, x21);

            // *a11.a12.x22 = x11.a12.*a22
            fpEq.add(x12.eq(a11star.dotMinMax(a12).dotMinMax(x22)));
            // *a22.a21.x11 = x22.a21.*a11
            fpEq.add(x21.eq(a22star.dotMinMax(a21).dotMinMax(x11)));

            // ret = id + this . ret
            NumericMatrix id = new NumericMatrix(Dimensions.square(n, 2), factory);
            setSubIdentity(iden, id, n, start);
            fpEq.add(ret.eq(id.union(this.dotMinMax(ret))));
        }

        return ret;
    }

    /**
     * TODO
     * Returns the reflexive transitive closure of this matrix.
     * {@code fpEq} will be updated to contain the fixed point equations
     * necessary to define the transitive closure.
     *
     * @return { m: NumericMatrix | m = *this }
     * @throws UnsupportedOperationException #this.dimensions != 2 ||
     *             !this.dimensions.square()
     * @throws NullPointerException fpEq = null || iden = null
     */
    /*public final NumericMatrix reflexiveClosure(NumericMatrix iden, List<BooleanValue> fpEq) {
        if (dims.numDimensions() != 2 || !dims.isSquare()) {
            throw new UnsupportedOperationException("#this.dimensions != 2 || !this.dimensions.square()");
        }
        return reflexiveClosure(iden, 0, dims.dimension(0) - 1, fpEq);
    }*/

    /**
     * Returns the transpose of this matrix.
     *
     * @return { m: NumericMatrix | m = ~this }
     * @throws UnsupportedOperationException #this.dimensions != 2
     */
    public final NumericMatrix transpose() {
        final NumericMatrix ret = !this.isBoolean() ?
                new NumericMatrix(dims.transpose(), factory, cells, cells) :
                new BinaryMatrix(dims.transpose(), factory); // TODO

        final int rows = dims.dimension(0), cols = dims.dimension(1);
        for (IndexedEntry<NumericValue> e0 : cells) {
            ret.cells.put((e0.index() % cols) * rows + (e0.index() / cols), e0.value());
        }

        return ret;
    }

    /**
     * Perceives this matrix from the boolean point-of-view in a numeric context.
     */
    public final NumericMatrix drop() {
        if(this.isBoolean())
            return this.clone();

        final NumericMatrix ret = new BinaryMatrix(dims, factory);

        for (IndexedEntry<NumericValue> e0 : cells) {
            ret.cells.put(e0.index(), factory.toBinary(factory.drop(e0.value())));
        }

        return ret;
    }

    /**
     * Performs the alpha-cut of this matrix.
     * @param alpha in [0, 1]
     * @requires alpha in [0, 1]
     * @return { x | membership_degree(x) >= alpha }
     */
    public final NumericMatrix alphaCut(NumericConstant alpha){
        assert alpha.getValue().doubleValue() >= 0 && alpha.getValue().doubleValue() <= 1;
        if(this.isBoolean())
            return this.clone();

        final NumericMatrix ret = new BinaryMatrix(dims, factory);
        for (IndexedEntry<NumericValue> e0 : cells) {
            BooleanValue cut = factory.gte(e0.value(), alpha);
            if(cut != FALSE)
                ret.cells.put(e0.index(), factory.toBinary(cut));
        }

        return ret;
    }

    /**
     * Returns a numeric matrix m such that m = this if the given condition
     * evaluates to TRUE and m = other otherwise.
     */
    public final NumericMatrix choice(BooleanValue condition, NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);
        if (condition == TRUE)
            return this.clone();
        else if (condition == FALSE)
            return other.clone();

        final NumericMatrix ret = this.isBoolean() && other.isBoolean() ?
                new BinaryMatrix(dims, factory) :
                new NumericMatrix(dims, factory);

        for (IndexedEntry<NumericValue> e0 : cells) {
            NumericValue v1 = other.cells.get(e0.index());
            if (v1 == null)
                ret.fastSet(e0.index(), factory.implies(condition, e0.value()));
            else
                ret.fastSet(e0.index(), factory.ite(condition, e0.value(), v1));
        }
        for (IndexedEntry<NumericValue> e1 : other.cells) {
            if (!cells.containsIndex(e1.index()))
                ret.fastSet(e1.index(), factory.implies(condition.negation(), e1.value()));
        }

        return ret;
    }

    /**
     * Returns a formula stating that the entries in this matrix are a subset of the
     * entries in the given matrix
     * @return forall i . this.cells(i) != 0 => other.cells(i) != 0 && this.cells(i) <= other.cells(i)
     */
    public final BooleanValue subset(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        BiFunction<NumericValue, NumericValue, BooleanValue> isIn = this.isBoolean() && other.isBoolean() ?
                // !b0 || b1
                (b0, b1) -> factory.or(toBool(b0).negation(), toBool(b1)) :
                // n0 != 0 => n1 != 0 && n0 <= n1
                (x0, x1) -> {
                    NumericValue n0 = toNumeric(x0);
                    NumericValue n1 = toNumeric(x1);
                    return factory.implies(
                            factory.neq(n0, ZERO),
                            factory.and(factory.neq(n1, ZERO), factory.lte(n0, n1))
                    );
                };

        final BooleanAccumulator a = BooleanAccumulator.treeGate(AND);
        for (IndexedEntry<NumericValue> e0 : cells) {
            if (a.add(isIn.apply(e0.value(), other.fastGet(e0.index()))) == FALSE)
                return FALSE;
        }
        return factory.accumulate(a);
    }

    /**
     * Returns a formula stating that the entries in this matrix are equivalent to the
     * entries in the given matrix.
     */
    public final BooleanValue eq(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);
        return factory.cmp(EQ, this.cells, other.cells);
        //return factory.and(this.subset(other), other.subset(this));
    }

    /**
     * Returns a formula specifying that each non-zero entry in this matrix is
     * equal to the given value
     * @return this.cells = v
     */
    public final BooleanValue eq(NumericValue v) {
        return factory.cmp(EQ, this.cells, v);
    }

    /**
     * Returns a formula specifying that at least one non-zero entry in this matrix is
     * not equal to the given value
     * @return exists i : this.cells | this.cells[i] != v
     */
    public final BooleanValue neq(NumericValue v) {
        return factory.cmp(EQ, this.cells, v).negation();
    }

    /**
     * Returns a formula specifying that each entry in this matrix is less than
     * the entry in the same position of the given matrix.
     * @return this.cells < other.cells
     */
    public final BooleanValue lt(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);
        return factory.cmp(LT, this.cells, other.cells);
    }

    /**
     * Returns a formula specifying that each non-zero entry in this matrix is
     * less than the given value
     * @return this.cells < v
     */
    public final BooleanValue lt(NumericValue v) {
        return factory.cmp(LT, this.cells, v);
    }

    /**
     * Returns a formula specifying that each entry in this matrix is less than or equal
     * to the entry in the same position of the given matrix.
     * @return this.cells <= other.cells
     */
    public final BooleanValue lte(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);
        return factory.cmp(LEQ, this.cells, other.cells);
    }

    /**
     * Returns a formula specifying that each non-zero entry in this matrix is
     * less than or equal to the given value
     * @return this.cells <= v
     */
    public final BooleanValue lte(NumericValue v) {
        return factory.cmp(LEQ, this.cells, v);
    }

    /**
     * Returns a formula specifying that each entry in this matrix is greater than
     * the entry in the same position of the given matrix.
     * @return this.cells > other.cells
     */
    public final BooleanValue gt(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);
        return factory.cmp(GT, this.cells, other.cells);
    }

    /**
     * Returns a formula specifying that each non-zero entry in this matrix is
     * greater than the given value
     * @return this.cells > v
     */
    public final BooleanValue gt(NumericValue v) {
        return factory.cmp(GT, this.cells, v);
    }

    /**
     * Returns a formula specifying that each entry in this matrix is greater than or equal
     * to the entry in the same position of the given matrix.
     * @return this.cells >= other.cells
     */
    public final BooleanValue gte(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);
        return factory.cmp(GEQ, this.cells, other.cells);
    }

    /**
     * Returns a formula specifying that each non-zero entry in this matrix is
     * greater than or equal to the given value
     * @return this.cells >= v
     */
    public final BooleanValue gte(NumericValue v) {
        return factory.cmp(GEQ, this.cells, v);
    }

    /**
     * Returns a matrix m such that the relational value of m is equal to the
     * relational value of this projected on the specified columns.
     *
     * TODO: Project of non-constant input {@code columns}.
     * TODO: Require whole NumericValue when working over real number values.
     *
     * @requires column[int] in this.dimensions.dimensions[int]
     * @requires this.dimensions.isSquare()
     * @return { m: NumericMatrix | [[m]] = project([[this]], columns) }
     * @throws IllegalArgumentException columns.length < 1 ||
     *             !this.dimensions.isSquare()
     * @throws NullPointerException columns = null
     */
    public final NumericMatrix project(NumericValue[] columns) {
        if (!dims.isSquare())
            throw new IllegalArgumentException("!this.dimensions.isSquare()");

        final int rdnum = columns.length;

        if (rdnum < 1)
            throw new IllegalArgumentException("columns.length < 1");

        final Dimensions rdims = Dimensions.square(dims.dimension(0), rdnum);
        final NumericMatrix ret = new NumericMatrix(rdims, factory, cells, cells);

        final int tdnum = dims.numDimensions();
        final int[] tvector = new int[tdnum];
        final int[] ivector = new int[rdnum];
        final int[] rvector = new int[rdnum];

        int nVarCols = 1;

        // detect constant columns to avoid unnecessary looping;
        for (int i = 0; i < rdnum; i++) {
            if (columns[i] instanceof NumericConstant) {
                int value = ((NumericConstant)columns[i]).getValue().intValue();
                if (value < 0 || value >= tdnum) {
                    return ret;
                } else { // distinguish constants by making them negative
                    ivector[i] = -value;
                }
            } else {
                nVarCols *= tdnum;
            }
        }

        PROJECT: for (int i = 0; i < nVarCols; i++) {
            BooleanValue colVal = TRUE;
            for (int j = 0; j < rdnum; j++) {
                // if the jth column is non-constant, check that it can take on
                // the value ivector[j]
                if (ivector[j] >= 0) {
                    colVal = factory.and(colVal, factory.eq(columns[j], factory.constant(ivector[j])));
                    if (colVal == FALSE)
                        continue PROJECT;
                }
            }
            for (IndexedEntry<NumericValue> e : cells) {
                dims.convert(e.index(), tvector);
                for (int j = 0; j < rdnum; j++) {
                    rvector[j] = tvector[StrictMath.abs(ivector[j])];
                }
                int rindex = rdims.convert(rvector);
                ret.fastSet(rindex, factory.ite(factory.and(e.value(), colVal), e.value(), ret.fastGet(rindex)));
            }
            for (int j = rdnum - 1; j >= 0; j--) { // update ivector
                // update ivector[j] only if the jth column is not constant
                if (ivector[j] >= 0) {
                    if (ivector[j] + 1 == tdnum) {
                        ivector[j] = 0;
                    } else {
                        ivector[j] += 1;
                        break;
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Overrides the values in this matrix with those in <code>other</code>.
     * Specifically, for each index i of the returned matrix m,
     * m.elements[i] contains the value of this.elements[i] if the number of
     * all non-zero elements of other in the same row adds up to 0,
     * and the value of other.elements[i] otherwise.
     *
     * @throws NullPointerException other = null
     * @throws IllegalArgumentException other.dimensions != this.dimensions
     */
    public final NumericMatrix override(NumericMatrix other){
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);
        if (other.cells.isEmpty())
            return this.clone();

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = isB1 && isB2 ?
                new BinaryMatrix(dims, factory) : // TODO
                new NumericMatrix(dims, factory, cells, other.cells);
        ret.cells.putAll(other.cells);

        final int rowLength = dims.capacity() / dims.dimension(0);
        int row = -1;
        NumericValue rowVal = ZERO;

        for (IndexedEntry<NumericValue> e0 : cells) {
            int e0row = e0.index() / rowLength;
            if (row != e0row) {
                row = e0row;
                Set<NumericValue> rowValues = new TreeSet<>();
                Iterator<IndexedEntry<NumericValue>> it = other.cells.iterator(row * rowLength, (row + 1) * rowLength - 1);

                while(it.hasNext())
                    rowValues.add(factory.dropNum(it.next().value()));

                //rowVal = sum other row values
                rowVal = rowValues.size() == 0 ? ZERO :
                        !isB2 ?
                                factory.plus(rowValues.toArray(new NumericValue[0])) :
                                factory.toBinary(factory.and(rowValues.stream().map(this :: toBool).toArray(BooleanValue[] :: new)));
            }

            if(rowVal == ZERO)
                ret.fastSet(e0.index(), e0.value());
            else ret.fastSet(e0.index(),
                    isB1 && isB2 ?
                            factory.toBinary(factory.or(ret.fastGet(e0.index()), factory.and(e0.value(), factory.eq(rowVal, ZERO)))) :
                            factory.ite(factory.eq(rowVal, ZERO), e0.value(), ret.fastGet(e0.index()))
                    );

        }

        return ret;
    }

    /**
     * {@link BooleanMatrix#override(BooleanMatrix...)}
     */
    public final NumericMatrix override(NumericMatrix... others) {
        if (others.length == 0)
            return clone();
        final NumericMatrix[] matrices = Containers.copy(others, 0, new NumericMatrix[others.length + 1], 1, others.length);
        matrices[0] = this;
        for (int part = matrices.length; part > 1; part -= part / 2) {
            final int max = part - 1;
            for (int i = 0; i < max; i += 2) {
                matrices[i / 2] = matrices[i].override(matrices[i + 1]);
            }
            if (max % 2 == 0) { // even max => odd number of entries
                matrices[max / 2] = matrices[max];
            }
        }
        return matrices[0];
    }

    /**
     * Returns the number of arcs of the relation represented by this matrix.
     * The sum is then assigned to every element of the matrix.
     * @return sum i : this.cells | this.cells[i]
     */
    public final NumericMatrix cardinality() {
        NumericValue c = factory.plus(cells.values().toArray(new NumericValue[cells.size()]));
        c = factory.ite(factory.gte(c, ONE), ONE, c);

        return factory.constant(dims, Ints.rangeSet(Ints.range(0, dims.capacity() - 1)), c);
    }

    /**
     * Returns the number of arcs of the relation represented by this matrix.
     * The sum is then assigned to every element of the matrix.
     * @return sum i : this.cells | this.cells[i]
     */
    private NumericMatrix cardinalityUnbounded() {
        return factory.constant(dims, Ints.rangeSet(Ints.range(0, dims.capacity() - 1)), factory.plus(cells.values().toArray(new NumericValue[cells.size()])));
    }

    /**
     * Returns the sum of all elements of this matrix.
     * @return sum i : this.cells | this.cells[i]
     */
    public final NumericMatrix sum() {
        return this.cardinality();
    }

    /**
     * Returns a BooleanValue that constrains at least one value in this matrix to
     * be true.
     * @return exists i | this.cells[i] != 0
     */
    public final BooleanValue some(){
        if(cells.size() == 0)
            return FALSE;

        Function<NumericValue, BooleanValue> occurrence = this.isBoolean() ? this :: toBool : n -> factory.neq(n, ZERO);
        final BooleanAccumulator g = BooleanAccumulator.treeGate(OR);
        for (IndexedEntry<NumericValue> v : cells) {
            if (g.add(occurrence.apply(v.value())) == TRUE) // this.cells[i] != 0
                return TRUE;
        }
        return factory.accumulate(g);
    }

    /**
     * Returns a BooleanValue that constrains this matrix to contain a positive value
     * in exactly one of its elements.
     * @return (sum i : this.cells.drop() | this.cells[i]) = 1
     */
    public final BooleanValue one(){
        return // Cardinality returns a constant matrix and thus, the first value stored contains the resulting numeric value
                factory.eq(this.drop().cardinalityUnbounded().getFirst(), ONE);
    }

    /**
     * Returns a BooleanValue that constraints all values in this.elements to be
     * false from the Boolean point-of-view.
     * @return (sum i : this.cells | this.cells[i]) = 0
     */
    public final BooleanValue none(){
        if(cells.isEmpty())
            return TRUE;
        return !this.isBoolean() ?
                // Cardinality returns a constant matrix and thus, the first value stored contains the resulting numeric value
                factory.eq(this.cardinality().getFirst(), ZERO) :
                factory.nand(cells.values().stream().map(this :: toBool).toArray(BooleanValue[] :: new));
    }

    /**
     * Returns a BooleanValue that constrains at most one value in this matrix to
     * be positive.
     * @return (sum i : this.cells.drop() | this.cells[i]) <= 1
     */
    public final BooleanValue lone(){
        return // Cardinality returns a constant matrix and thus, the first value stored contains the resulting numeric value
                factory.lte(this.drop().cardinalityUnbounded().getFirst(), ONE);
    }

    /**
     * Produces the Khatri-Rao product between this and other matrix.
     *
     * @requires this.dimensions.isSquare() & other.dimensions.isSquare()
     * @return this ▿ other
     */
    public final NumericMatrix khatriRao(NumericMatrix other){
        checkFactory(this.factory, other.factory);
        if (!dims.isSquare() || !other.dims.isSquare() || dims.dimension(0) != other.dims.dimension(0)) {
            throw new UnsupportedOperationException("!this.dimensions.square() || !other.dimensions.square()" +
                    " or the dimensions do not have the same side size.");
        }

        int u = dims.dimension(0);
        int m = dims.numDimensions() - 1;
        int n = other.dims.numDimensions() - 1;

        final NumericMatrix ret = new NumericMatrix(Dimensions.square(u, m + n + 1), factory, cells, other.cells);
        if (cells.isEmpty() || other.cells.isEmpty())
            return ret;

        for (IndexedEntry<NumericValue> e0 : cells) {
            int a = e0.index() % u;;

            for (IndexedEntry<NumericValue> e1 : other.cells) {
                int i = e0.index() * (int) Math.pow(u, n) + e1.index() - (a * (int) Math.pow(u, n));
                // Must be in the same column
                if(a == e1.index() % u)// && i >= 0 && i < ret.dims.capacity()) {
                    ret.set(i, factory.times(e0.value(), e1.value()));
            }
        }

        return ret;
    }

    /*
     * Numeric Expression methods
     */

    /**
     * @return The non-zero value associated with the matrix entry with the smallest index.
     * In case this matrix does not have any, returns 0.
     */
    public NumericValue getFirst(){
        return this.density() > 0 ? cells.first().value() : ZERO;
    }

    /**
     * Returns a new matrix such that an entry in the returned matrix represents the
     * pointwise modulo of the corresponding entries in this and other matrix.
     * @return this.cells mod other.cells
     */
    public final NumericMatrix modulo(NumericMatrix other) {
        checkFactory(this.factory, other.factory);
        checkDimensions(this.dims, other.dims);

        boolean isB1 = this.isBoolean();
        boolean isB2 = other.isBoolean();

        final NumericMatrix ret = isB1 || isB2 ?
                new BinaryMatrix(dims, factory) : // TODO
                new NumericMatrix(dims, factory, cells, other.cells);

        Function<NumericValue, NumericValue> dividend, divisor;
        dividend = x -> isB1 ? toNumeric(x) : x;
        divisor = x -> isB2 ? toNumeric(x) : x;

        for (IndexedEntry<NumericValue> e0 : this.cells) {
            NumericValue v1 = other.cells.get(e0.index());
            if (v1 != null)
                ret.fastSet(e0.index(), factory.modulo(dividend.apply(e0.value()), divisor.apply(v1)));
            // Mod zero
            else throw new IllegalArgumentException("Mod of zero is undefined.");
        }

        return ret;
    }

    /**
     * Builds the numeric matrix containing the negation of each element in this matrix.
     * @return - this
     */
    public final NumericMatrix negate() {
        // Check if this matrix is constant
        if(this.cells instanceof HomogenousSequence) {
            return factory.constant(dims, cells.indices(), factory.negate(this.getFirst()));
        }
        // Otherwise, negate each value individually
        final NumericMatrix ret = new NumericMatrix(dims, factory, cells, cells);
        for (IndexedEntry<NumericValue> e0 : cells)
            ret.cells.put(e0.index(), factory.negate(toNumeric(e0.value())));

        return ret;
    }

    /**
     * Returns a matrix with the absolute value of each element in this matrix.
     * @return abs(this)
     */
    public final NumericMatrix abs() {
        // Check if this matrix is constant
        if(this.cells instanceof HomogenousSequence) {
            return factory.constant(dims, cells.indices(), factory.abs(this.getFirst()));
        }
        // Otherwise, negate each value individually
        final NumericMatrix ret = new NumericMatrix(dims, factory, cells, cells);
        for (IndexedEntry<NumericValue> e0 : cells)
            ret.cells.put(e0.index(), factory.abs(toNumeric(e0.value())));

        return ret;
    }

    /**
     * Applies the sign function each entry of this matrix.
     * @return sgn(this)
     */
    public final NumericMatrix signum() {
        // Check if this matrix is constant
        if(this.cells instanceof HomogenousSequence) {
            return factory.constant(dims, cells.indices(), factory.signum(this.getFirst()));
        }
        // Otherwise, negate each value individually
        final NumericMatrix ret = new NumericMatrix(dims, factory, cells, cells);
        NumericValue s;
        for (IndexedEntry<NumericValue> e0 : cells) {
            s = factory.signum(toNumeric(e0.value()));
            if(s != ZERO)
                ret.cells.put(e0.index(), s);
        }

        return ret;
    }

    /**
     * Helper method to extract the numeric component of a binary numeric value.
     * If the numeric value provided is not binary, does nothing.
     * @param n numeric value
     * @return n = { x, b } ? x : n
     */
    private NumericValue toNumeric(NumericValue n){
        return n instanceof BinaryValue ? ((BinaryValue) n).toNumeric() : n;
    }

    /**
     * Helper method to drop the given numeric value into the Boolean realm.
     * If the value provided is binary, extracts the Boolean component, otherwise drops the numeric value.
     * @param b numeric value
     * @return b = { n, x } ? x : drop(b)
     */
    private BooleanValue toBool(NumericValue b) {
        return b instanceof BinaryValue ? ((BinaryValue) b).toBool() : factory.drop(b);
    }

    /**
     * Sets the specified index to the given value.
     */
    public final void set(final int index, final NumericValue value) {
        if (!dims.validate(index))
            throw new IndexOutOfBoundsException("index < 0 || index >= this.dimensions.capacity");
        if (value == null)
            throw new NullPointerException("formula=null");
        if (value == ZERO)
            cells.remove(index);
        else
            cells.put(index, value);
    }

    /**
     * Returns a copy of this numeric matrix.
     */
    public NumericMatrix clone() {
        try {
            return new NumericMatrix(dims, factory, cells.clone());
        } catch (CloneNotSupportedException e) {
            throw new InternalError(); // unreachable code.
        }
    }

    /**
     * Returns a string representation for this matrix.
     */
    @Override
    public String toString() {
        final StringBuilder buff = new StringBuilder("dimensions: ");
        buff.append(dims);
        buff.append(", elements: ");
        buff.append(cells);
        return buff.toString();
    }
}

