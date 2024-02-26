package edu.mit.csail.sdg.alloy4viz;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorFatal;
import edu.mit.csail.sdg.alloy4.XMLNode;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.translator.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Quantitative extension of {@link StaticInstanceReader}.
 */
public final class QuantitativeInstanceReader extends StaticInstanceReader{

    private Map<AlloyRelation, Map<AlloyTuple, String>> qtrel = null;
    private Map<AlloyAtom, Map<String, String>> qtsig = null;

    private QuantitativeInstanceReader(XMLNode root) throws Err {
        super(root);
    }

    /**
     * Parses the XML into a quantitative solution.
     */
    @Override
    protected A4Solution readSolution(XMLNode root){
        return A4QtSolutionReader.read(new ArrayList<Sig>(), root);
    }

    /**
     * Constructs an quantitative AlloyInstance,
     * unless isMeta is true, in which case the expected AlloyInstance is returned,
     * given by {@link StaticInstanceReader#makeAlloyInstance(A4Solution, AlloyModel, Map, Map, boolean)}.
     */
    @Override
    protected AlloyInstance makeAlloyInstance(A4Solution sol, AlloyModel am, Map<AlloyAtom,Set<AlloySet>> atom2sets, Map<AlloyRelation,Set<AlloyTuple>> rels, boolean isMeta){
        if(isMeta)
            return super.makeAlloyInstance(sol, am, atom2sets, rels, isMeta);
        return new AlloyQuantitativeInstance(sol, sol.getOriginalFilename(), sol.getOriginalCommand(), am, atom2sets, rels, isMeta, qtrel, qtsig);
    }

    /**
     * Helper method to check if the given tuple set is filled with any quantitative tuples or not.
     * @return exists t in ts. t is quantitative
     */
    private boolean isQuantitative(A4TupleSet ts){
        boolean isQt = false;
        Iterator<A4Tuple> it = ts.iterator();
        while(it.hasNext() && !isQt)
            isQt = it.next() instanceof A4QtTuple;
        return isQt;
    }

    /**
     * {@inheritDoc}
     * Additionally, stores the quantities associated with each quantitative subset sig within this instance.
     * */
    @Override
    protected void setOrRel(A4Solution sol, String label, Expr expr, boolean isPrivate, boolean isMeta, Boolean isInt) throws Err {
        boolean isQt;
        for (List<Sig.PrimSig> ps : expr.type().fold()) {
            if (ps.size() == 1) {
                Sig.PrimSig t = ps.get(0);
                A4TupleSet ts = (A4TupleSet)sol.eval(t.domain(expr));

                // Check if the set is Boolean or Quantitative
                isQt = isInt == null ? isQuantitative(ts) : isInt;

                AlloySet set = makeSet(label, isPrivate, isMeta, isQt, sig(t));
                sets.add(set);

                if(isQt)
                    for (A4Tuple tp : ts) {
                        AlloyAtom at = string2atom.get(tp.atom(0));
                        atom2sets.get(at).add(set);

                        String q = (tp instanceof A4QtTuple) ? ((A4QtTuple) tp).getQuantity() : "1";
                        if(qtsig == null)
                            this.qtsig = new HashMap<>();
                        if(!qtsig.containsKey(at))
                            this.qtsig.put(at, new HashMap<>());
                        qtsig.get(at).put(label, q);
                    }
                else for (A4Tuple tp : (A4TupleSet) (sol.eval(expr.intersect(t)))) {
                    atom2sets.get(string2atom.get(tp.atom(0))).add(set);
                }

            } else {
                Expr mask = null;
                List<AlloyType> types = new ArrayList<AlloyType>(ps.size());
                for (int i = 0; i < ps.size(); i++) {
                    types.add(sig(ps.get(i)));
                    if (mask == null)
                        mask = ps.get(i);
                    else
                        mask = mask.product(ps.get(i));
                }
                // Check if the rel is Boolean or Quantitative
                isQt = isInt == null ? isQuantitative((A4TupleSet) sol.eval(expr)) : isInt;

                defRel(sol, label, expr, isPrivate, isMeta, isQt, types, mask);
            }
        }
    }

    /**
     * Defines the AlloyRelation w.r.t. the given expression,
     * while also tracking the quantity associated with its arcs.
     */
    @Override
    protected void defRel(A4Solution sol, String label, Expr expr, boolean isPrivate, boolean isMeta, boolean isInt, List<AlloyType> types, Expr mask) throws Err{
        AlloyRelation rel = makeRel(label, isPrivate, isMeta, isInt, types);
        Set<AlloyTuple> ts = new LinkedHashSet<AlloyTuple>();
        if(qtrel == null)
            this.qtrel = new HashMap<>();
        Map<AlloyTuple, String> relQt = new HashMap<>();

        for (A4Tuple tp : (A4TupleSet) sol.eval(expr)){ //(sol.eval(expr.intersect(mask)))) { //todo mask ?
            AlloyAtom[] atoms = new AlloyAtom[tp.arity()];
            for (int i = 0; i < tp.arity(); i++) {
                atoms[i] = string2atom.get(tp.atom(i));
                if (atoms[i] == null)
                    throw new ErrorFatal("Unexpected XML inconsistency: cannot resolve atom " + tp.atom(i));
            }
            AlloyTuple tuple = new AlloyTuple(atoms);
            ts.add(tuple);
            if(isInt)
                relQt.put(tuple, tp instanceof A4QtTuple ? ((A4QtTuple)tp).getQuantity() : "1"); //set tuple quantity
        }
        // Check if this is a quantitative relation
        if(isInt && relQt.size() > 0)
            this.qtrel.put(rel, relQt);

        rels.put(rel, ts);
    }

    /** Parse the file into an quantitative AlloyInstance if possible. */
    public static AlloyInstance parseInstance(File file) throws Err {
        try {
            return (new QuantitativeInstanceReader(new XMLNode(file))).ans;
        } catch (IOException ex) {
            throw new ErrorFatal("Error reading the XML file: " + ex, ex);
        }
    }
}
