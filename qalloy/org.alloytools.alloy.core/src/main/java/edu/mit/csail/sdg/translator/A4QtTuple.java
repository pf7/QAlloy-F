package edu.mit.csail.sdg.translator;

import kodkod.instance.Tuple;

/**
 * Quantitative extension of {@link A4Tuple}.
 * Associated with this tuple is its quantity.
 */
public class A4QtTuple extends A4Tuple {

    private final String quantity;

    A4QtTuple(Tuple tuple, A4Solution sol, Number quantity){
        super(tuple, sol);
        this.quantity = quantity.toString();
    }

    A4QtTuple(Tuple tuple, A4Solution sol, String quantity){
        super(tuple, sol);
        this.quantity = quantity;
    }

    /**
     * returns the weight associated with this tuple.
     */
    public String getQuantity(){
        return quantity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(quantity).append(" ** ").append(super.toString());
        return sb.toString();
    }
}
