package kodkod.instance;

import kodkod.util.ints.*;
import java.util.*;

/**
 * Quantitative extension of a {@link TupleSet set of tuples}.
 * Each tuple now has a weight associated with it.
 */
public class QtTupleSet extends TupleSet{

    private final Map<Integer, Number> weight;

    /**
     * Returns a quantitative set of the given arity that contains all tuples whose indeces are
     * contained in the given int set. Throws an IllegalArgumentException if the set
     * contains an index that is either negative or greater than
     * this.universe.size()^arity - 1. An attempt to iterate over a tuple set backed
     * by an invalid index set will result in a runtime exception.
     */
    QtTupleSet(Universe universe, int arity, IntSet tupleIndeces, Map<Integer, Number> w){
        super(universe, arity, tupleIndeces);
        weight = w;
    }

    QtTupleSet(TupleSet original){
        super(original);
        if(original instanceof QtTupleSet) {
            weight = new HashMap<>();
            ((QtTupleSet) original).weight.forEach(this.weight::put);
        }
        else {
            weight = new HashMap<>();
            IntIterator it = original.indexView().iterator();
            while (it.hasNext()) { weight.put(it.next(), 1); }
        }
    }

    QtTupleSet(Universe universe, int arity) {
        super(universe, arity);
        this.weight = new HashMap<>();
    }

    /**
     * Copy constructor.
     *
     * @ensures constructs a deep copy of the given qttupleset
     */
    private QtTupleSet(QtTupleSet original) {
        super(original);
        weight = new HashMap<>();
        original.weight.forEach(this.weight :: put);
    }

    /**
     * Adds the specified tuple to this tupleset. Returns true if this set was
     * changed as the result of the operation.
     * The added tuple has default 1 weight.
     */
    @Override
    public boolean add(Tuple t) {
        weight.put(t.index(), 1);
        return super.add(t);
    }

    /**
     * Adds the specified tuple to this tupleset with the specified weight.
     * Returns true if this set was changed as the result of the operation.
     * @param n weight of t
     * @requires n != 0
     */
    public boolean add(Tuple t, Number n) {
        assert n.doubleValue() != 0;
        weight.put(t.index(), n);
        return super.add(t);
    }

    /**
     * Reads the weight of the tuple.
     * @param t index of the tuple
     * @requires t must identify a tuple of this universe
     * @return weight of
     */
    public Number getWeight(int t){
        return weight.get(t);
    }

    /**
     * Returns an unmodifiable view of the this qttupleset. This method allows modules
     * to provide "read-only" access to internal tuple sets. Query operations on the
     * returned set "read through" to the specified set, and attempts to modify the
     * returned set, whether direct or via its iterator, result in an
     * UnsupportedOperationException.
     *
     * @return an unmodifiable view of the this qttupleset
     */
    @Override
    public QtTupleSet unmodifiableView() {
        return new QtTupleSet(super.universe(), super.arity(), super.indexView(), weight);
    }

    /**
     * If tuple t exists in this set, updates its weight by the additional n provided;
     * otherwise, adds tuple t with weight n to the set.
     */
    private void update(Integer t, Number n){
        Number current = weight.get(t);
        if(current != null)
            weight.put(t, current instanceof Double ? current.doubleValue() + n.doubleValue() : current.intValue() + n.intValue());
        else weight.put(t, n);
    }

    /**
     * {@inheritDoc}
     *
     * Also updates the weights, inserting the weight of new atoms or
     * adding it together with the existing weight.
     */
    @Override
    public boolean addAll(Collection< ? extends Tuple> c) {
        boolean updated = super.addAll(c);

        // Check if weights need to be updated
        if(!updated)
            updated = c.iterator().hasNext();

        if(updated && c instanceof QtTupleSet) {
            final QtTupleSet qt = (QtTupleSet) c;
            qt.weight.forEach(this :: update);
        }
        else if(updated && c instanceof TupleSet){
            final TupleSet ts = (TupleSet) c;
            IntIterator it = ts.indexView().iterator();
            while (it.hasNext()) {
                int i = it.next();
                update(i, 1);
            }
        }

        return updated;
    }

    /**
     * String representation of this QtTupleSet.
     */
    @Override
    public String toString() {
        Iterator<Tuple> it = iterator();
        if (! it.hasNext())
            return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            Tuple e = it.next();
            sb.append("(").append(e).append(", ").append(getWeight(e.index())).append(")");

            if (! it.hasNext())
                return sb.append(']').toString();
            sb.append(',').append(' ');
        }
    }

    /**
     * Returns a deep copy of this quantitative tuple set.
     */
    @Override
    public QtTupleSet clone() {
        return new QtTupleSet(this);
    }

}
