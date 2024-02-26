package kodkod.engine.num;

import kodkod.util.collections.Containers;

import java.util.*;

/**
 * An arithmetic gate with more than two inputs.
 */
public class NaryAritGate extends AritGate{

    private NumericValue[] inputs;

    /**
     * Constructs a new n-ary gate with the given label, from the given mutable
     * arithmetic gate.
     *
     * @requires g != null && #g.inputs > 2
     * @ensures this.op' = g.op && this.inputs' = g.inputs && this.label' = label
     */
    NaryAritGate(NumericAccumulator g, int label) {
        super(g.op, label);
        this.inputs = new NumericValue[g.size()];
        int index = 0;
        for(NumericValue v : g){
            inputs[index] = v;
            index++;
        }
    }

    /**
     * Returns an iterator over the inputs to this gate.
     *
     * @return an iterator over this.inputs
     */
    @Override
    public Iterator<NumericValue> iterator() {
        return Containers.iterate(inputs);
    }

    /**
     * Returns the number of inputs to this gate.
     *
     * @return #this.inputs
     */
    public int size() {
        return inputs.length;
    }

    /**
     * Returns the ith input to this gate.
     *
     * @return this.inputs[i]
     * @requires 0 <= i < size
     * @throws IndexOutOfBoundsException i < 0 || i >= #this.inputs
     */
    public NumericValue input(int i) {
        if (i < 0 || i > inputs.length)
            throw new IndexOutOfBoundsException();
        return inputs[i];
    }
}
