package edu.mit.csail.sdg.alloy4viz;

import java.util.*;

/**
 * {@link StaticProjector} for quantitative instances.
 */
public class QuantitativeProjector extends StaticProjector{

    /**
     * Quantitative adaptation of {@link StaticProjector#project(AlloyInstance, AlloyProjection)}
     * - Handle weight related changes
     * - AlloyInstance := QuantitativeAlloyInstance
     */
    public static AlloyInstance project(AlloyInstance oldInstance, AlloyProjection projection) {
        Map<AlloyRelation, List<Integer>> data = new LinkedHashMap<AlloyRelation,List<Integer>>();
        Map<AlloyAtom, Set<AlloySet>> atom2sets = new LinkedHashMap<AlloyAtom,Set<AlloySet>>();
        Map<AlloyRelation,Set<AlloyTuple>> rel2tuples = new LinkedHashMap<AlloyRelation,Set<AlloyTuple>>();
        AlloyModel newModel = project(oldInstance.model, projection.getProjectedTypes(), data);

        //Current quantities associated with each relation
        Map<AlloyRelation, Map<AlloyTuple, String>> oldRelQts = ((AlloyQuantitativeInstance)oldInstance).getQuantity();
        //New relation representation
        Map<AlloyRelation, Map<AlloyTuple, String>> qtrel = new HashMap<>();
        //New subset sigs representation
        Map<AlloyAtom, Map<String, String>> qtsig = ((AlloyQuantitativeInstance)oldInstance).getQtSubsigs();

        // First put all the atoms from the old instance into the new one
        for (AlloyAtom atom : oldInstance.getAllAtoms()) {
            atom2sets.put(atom, new LinkedHashSet<AlloySet>(oldInstance.atom2sets(atom)));
        }
        // Now, decide what tuples to generate
        for (AlloyRelation r : oldInstance.model.getRelations()) {
            List<Integer> list = data.get(r);
            if (list == null)
                continue; // This means that relation was deleted entirely
            tupleLabel: for (AlloyTuple oldTuple : oldInstance.relation2tuples(r)) {
                for (Integer i : list) {
                    // If an atom in the original tuple should be projected, but
                    // it doesn't match the
                    // chosen atom for that type, then this tuple must not be
                    // included in the new instance
                    AlloyAtom a = oldTuple.getAtoms().get(i);
                    AlloyType bt = r.getTypes().get(i);
                    bt = oldInstance.model.getTopmostSuperType(bt);
                    if (!a.equals(projection.getProjectedAtom(bt)))
                        continue tupleLabel;
                }
                List<AlloyAtom> newTuple = oldTuple.project(list);
                List<AlloyType> newObj = r.project(list);
                if (newObj.size() > 1 && newTuple.size() > 1) {
                    AlloyRelation r2 = new AlloyRelation(r.getName(), r.isPrivate, r.isMeta, r.isInt, newObj);
                    Set<AlloyTuple> answer = rel2tuples.get(r2);
                    if (answer == null)
                        rel2tuples.put(r2, answer = new LinkedHashSet<AlloyTuple>());
                    AlloyTuple tuple = new AlloyTuple(newTuple);
                    answer.add(tuple);
                    // Store quantities associated with the new tuple
                    if(r.isInt) {
                        Map<AlloyTuple, String> relQt = qtrel.get(r2);
                        if (relQt == null) {
                            relQt = new HashMap<>();
                            qtrel.put(r2, relQt);
                        }
                        relQt.put(tuple, oldRelQts.get(r).get(oldTuple));
                    }
                } else if (newObj.size() == 1 && newTuple.size() == 1) {
                    AlloyAtom a = newTuple.get(0);
                    Set<AlloySet> answer = atom2sets.get(a);
                    if (answer == null)
                        atom2sets.put(a, answer = new LinkedHashSet<AlloySet>());
                    answer.add(new AlloySet(r.getName(), r.isPrivate, r.isMeta, r.isInt, newObj.get(0)));
                    // Store quantities associated with the atom at hand
                    if (r.isInt) {
                        Map<String, String> sigQt = qtsig.get(a);
                        if(sigQt == null){
                            sigQt = new HashMap<>();
                            qtsig.put(a, sigQt);
                        }
                        sigQt.put(r.getName(), oldRelQts.get(r).get(oldTuple));
                    }
                }
            }
        }
        // Here, we don't have to explicitly filter out "illegal"
        // atoms/tuples/...
        // (that is, atoms that belong to types that no longer exist, etc).
        // That's because AlloyInstance's constructor must do the check too, so
        // there's no point in doing that twice.
        return new AlloyQuantitativeInstance(oldInstance.originalA4, oldInstance.filename, oldInstance.commandname, newModel, atom2sets, rel2tuples, oldInstance.isMetamodel, qtrel, qtsig);
    }
}
