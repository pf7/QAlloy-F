package edu.mit.csail.sdg.alloy4viz;

import edu.mit.csail.sdg.translator.A4Solution;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Quantitative extension of {@link AlloyInstance}.
 * For each AlloyAtom within each quantitative subset sigs
 * and each AlloyTuple of every quantitative relation within this instance
 * stores the respective string representation of its quantity.
 */
public class AlloyQuantitativeInstance extends AlloyInstance {

    private final Map<AlloyRelation, Map<AlloyTuple, String>> qtrel;
    private final Map<AlloyAtom, Map<String, String>> qtsig;

    public AlloyQuantitativeInstance(A4Solution originalA4, String filename, String commandname, AlloyModel model, Map<AlloyAtom, Set<AlloySet>> atom2sets, Map<AlloyRelation, Set<AlloyTuple>> rel2tuples, boolean isMetamodel, Map<AlloyRelation, Map<AlloyTuple, String>> qtrel, Map<AlloyAtom, Map<String, String>> msig) {
        super(originalA4, filename, commandname, model, atom2sets, rel2tuples, isMetamodel);
        this.qtrel = qtrel == null ? new HashMap<>() : qtrel;
        this.qtsig = msig == null ? new HashMap<>() : msig;
    }

    /**
     * Obtain the quantity of the AlloyAtom specified.
     * Returns null if such tuple does not exist.
     */
    public String getQuantity(AlloyAtom a, String subsig){
        return qtsig.getOrDefault(a, new HashMap<>()).get(subsig);
    }

    /**
     * Returns the quantities associated with each AlloyAtom contained in a quantitative subsig of this instance.
     */
    public Map<AlloyAtom, Map<String, String>> getQtSubsigs(){
        return this.qtsig;
    }

    /**
     * Returns true if the given atom is contained in a multisig of this instance.
     */
    public boolean isQuantitative(AlloyAtom a, String subsig){
        return qtsig.containsKey(a) && qtsig.get(a).containsKey(subsig);
    }

    /**
     * Obtain the quantity of the AlloyTuple specified within the given AlloyRelation.
     * Returns null if such tuple does not exist.
     */
    public String getQuantity(AlloyRelation r, AlloyTuple t){
        return qtrel.containsKey(r) ? qtrel.get(r).get(t) : null;
    }

    /**
     * Returns the quantities associated with each AlloyTuple within every AlloyRelation contained in this instance.
     */
    public Map<AlloyRelation, Map<AlloyTuple, String>> getQuantity(){
        return this.qtrel;
    }

    /**
     * Returns true if the given relation is a quantitative relation of this instance.
     */
    public boolean isQuantitative(AlloyRelation r){
        return qtrel.containsKey(r);
    }

    /*@Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Instance's weights:\n");
        weight.forEach((t, w) -> sb.append("(").append(t).append(", ").append(w).append(")\n"));
        return super.toString() + sb.toString();
    }*/

}
