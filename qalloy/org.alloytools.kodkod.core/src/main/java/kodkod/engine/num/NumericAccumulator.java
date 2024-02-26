package kodkod.engine.num;

import kodkod.engine.bool.BooleanFormula;
import kodkod.engine.bool.Operator;
import kodkod.util.ints.IndexedEntry;
import kodkod.util.ints.SparseSequence;
import kodkod.util.ints.TreeSequence;

import java.util.*;

/**
 * Quantitative adaptation of {@link kodkod.engine.bool.BooleanAccumulator}.
 * An accumulator for easy construction of gates with multiple inputs. An
 * accumulator cannot be combined with other numeric values using NumericFactory
 * methods. To use the circuit represented by an accumulator, one must first
 * convert it into a gate by calling
 * {@link NumericFactory#accumulate(NumericAccumulator)}.
 *
 * @specfield components: set NumericValue
 * @specfield op: Operator.NumNary
 */
public class NumericAccumulator extends NumericValue implements Iterable<NumericValue> {

    final Operator.NumNary                     op;
    private final SparseSequence<NumericValue> inputs;
    private final Map<Integer, Integer> occurrences;

    /**
     * Constructs a new accumulator with the given operator.
     *
     * @requires op != null
     * @ensures this.op' = op && this.label' = label
     */
    private NumericAccumulator(Operator.NumNary op) {
        this.op = op;
        inputs = new TreeSequence<NumericValue>();
        occurrences = new HashMap<>();
    }

    /**
     * Returns a tree based implementation of NumericAccumulator. The addInput
     * operation executes in O(lg n) time where n is the number of gate inputs.
     *
     * @return an empty tree based NumericAccumulator with the given operator.
     * @throws NullPointerException op = null
     */
    public static NumericAccumulator treeGate(Operator.NumNary op) {
        if (op == null)
            throw new NullPointerException();
        return new NumericAccumulator(op);
    }

    /**
     * Returns a tree based implementation of NumericAccumulator, initialized with
     * the given inputs. The addInput operation executes in O(lg n) time where n is
     * the number of gate inputs.
     *
     * @return a tree based NumericAccumulator with the given operator, initialized
     *         with the given inputs
     * @throws NullPointerException op = null || inputs = null
     */
    public static NumericAccumulator treeGate(Operator.NumNary op, NumericValue... inputs) {
        if (op == null)
            throw new NullPointerException();
        final NumericAccumulator ret = new NumericAccumulator(op);
        for (NumericValue v : inputs) {
            if (ret.add(v) != ret)
                break;
        }
        return ret;
    }

    /**
     * Returns the operator for this accumulator.
     *
     * @return this.op
     */
    @Override
    public Operator.NumNary op() {
        return op;
    }

    /**
     * Adds the given value to this.components and returns the result.
     * @return this
     */
    public NumericValue add(NumericValue v) {
        final int id = v.label();
        if(!inputs.containsIndex(id)) {
            inputs.put(id, v);
            occurrences.put(id, 1);
        }
        else occurrences.put(id, occurrences.get(id) + 1);
        return this;
    }

    /**
     * Returns the size of this accumulator.
     *
     * @return sum #this.inputs
     */
    public int size() {
        return occurrences.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Returns an iterator over this.components.
     * The returned iterator does not support removal.
     *
     * @return an iterator over this.components.
     */
    @Override
    public Iterator<NumericValue> iterator() {
        return new Iterator<NumericValue>() {

            final Iterator<IndexedEntry<NumericValue>> iter = inputs.iterator();
            private NumericValue currentValue = null;
            //Number of remaining occurrences of currentValue in a given moment
            private int occ = 0;

            @Override
            public boolean hasNext() {
                return iter.hasNext() || occ > 0;
            }

            @Override
            public NumericValue next() {
                if(occ == 0){
                    IndexedEntry<NumericValue> v = iter.next();
                    currentValue = v.value();
                    occ = occurrences.get(v.index()) - 1;
                }
                else occ--;

                return currentValue;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    /**
     * Throws an unsupported operation exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public BooleanFormula negation() {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws an unsupported operation exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public <T, A> T accept(NumericVisitor<T, A> visitor, A arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns 0.
     *
     * @return 0.
     */
    @Override
    public int label() {
        return 0;
    }

    /**
     * Returns a string representation of this accumulator.
     *
     * @return this.inputs.toString
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");

        for(IndexedEntry<NumericValue> i : inputs){
            if(occurrences.containsKey(i.index()))
                sb.append("(").append(i).append(",").append(occurrences.get(i.index())).append(")");
            else sb.append(i);
            sb.append(",");
        }

        sb.setLength(sb.length() - 1);

        return sb.append("]").toString();
    }
}
