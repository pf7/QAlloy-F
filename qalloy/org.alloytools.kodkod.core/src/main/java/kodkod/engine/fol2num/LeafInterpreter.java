package kodkod.engine.fol2num;

import kodkod.ast.ConstantExpression;
import kodkod.ast.Expression;
import kodkod.ast.Relation;
import kodkod.engine.bool.Dimensions;
import kodkod.engine.config.QuantitativeOptions;
import kodkod.engine.fol2sat.UnboundLeafException;
import kodkod.engine.num.*;
import kodkod.instance.*;
import kodkod.util.ints.*;

import java.util.*;

import static kodkod.engine.num.NumericConstant.ONE;

/**
 * Quantitative Extension of {@link kodkod.engine.fol2sat.LeafInterpreter}
 * ------------------------------------------------------------------------
 * <p>
 * Interprets the unquantified leaf expressions of a kodkod ast,
 * {@link kodkod.ast.Relation relations} and
 * {@link kodkod.ast.ConstantExpression constant expressions}, as
 * {@link NumericMatrix matrices} of
 * {@link kodkod.engine.num.NumericValue numeric values}
 * </p>
 */
public final class LeafInterpreter {

    private final NumericFactory           factory;
    private final Universe universe;
    private final Map<Relation, IntRange>   vars;
    private final Map<Relation, TupleSet>   lowers, uppers;
    private final SparseSequence<TupleSet> ints;

    // true if this interpreter will be handling constant numeric circuits
    private boolean isConstant = false;

    /**
     * Constructs a new LeafInterpreter using the given values.
     *
     * @requires lowers.keySet() = uppers.keySet()
     * @ensures this.universe' = universe && this.relations' = lowers.keySet() &&
     *          this.ints' = ints.indices && this.factory' = factory &&
     *          this.ubounds' = uppers && this.lbounds' = lowers && this.ibounds' =
     *          ints
     */
    private LeafInterpreter(Universe universe, Map<Relation,TupleSet> lowers, Map<Relation,TupleSet> uppers, SparseSequence<TupleSet> ints, NumericFactory factory, Map<Relation,IntRange> vars) {
        this.universe = universe;
        this.lowers = lowers;
        this.uppers = uppers;
        this.ints = ints;
        this.factory = factory;
        this.vars = vars;
    }

    /**
     * Constructs a new LeafInterpreter for constant numeric circuits using the given values.
     *
     * @requires lowers.keySet() = uppers.keySet()
     * @ensures this.universe' = universe && this.relations' = lowers.keySet() &&
     *          this.ints' = ints.indices && this.factory' = factory &&
     *          this.ubounds' = uppers && this.lbounds' = lowers && this.ibounds' =
     *          ints
     *
     * @param options Quantitative options to be taken into account
     * Depending on the QuantitativeType, the type of factory being instantiated is one of the following:
     *  INTEGER       => IntegerFactory
     *  FUZZY         => FuzzyFactory
     */
    @SuppressWarnings("unchecked" )
    private LeafInterpreter(Universe universe, Map<Relation,TupleSet> rbound, SparseSequence<TupleSet> ints, QuantitativeOptions options) {
        this(universe, rbound, rbound, ints, NumericFactory.factory(options, 0), Collections.EMPTY_MAP);
        this.isConstant = true;
    }

    /**
     * Returns an exact leaf interpreter based on the given instance and quantitative options.
     * Depending on the QuantitativeType, the type of factory being instantiated is one of the following:
     *  INTEGER       => IntegerFactory
     *  FUZZY         => FuzzyFactory
     */
    public static LeafInterpreter exact(Instance instance, QuantitativeOptions options) {
        return new LeafInterpreter(instance.universe(), instance.relationTuples(), instance.intTuples(), options);
    }

    /**
     * Returns an exact interpreter for the given bounds and options.
     * Depending on the QuantitativeType, the type of factory being instantiated is one of the following:
     *  INTEGER       => IntegerFactory
     *  FUZZY         => FuzzyFactory
     */
    public static LeafInterpreter exact(Bounds bounds, QuantitativeOptions options, boolean incremental) {
        final Map<Relation,IntRange> vars = new LinkedHashMap<Relation,IntRange>();
        final Map<Relation,TupleSet> lowers = incremental ? new LinkedHashMap<Relation,TupleSet>(bounds.lowerBounds()) : bounds.lowerBounds();
        final Map<Relation,TupleSet> uppers = incremental ? new LinkedHashMap<Relation,TupleSet>(bounds.upperBounds()) : bounds.upperBounds();
        final int numVars = allocateVars(1, vars, bounds.relations(), lowers, uppers);
        return new LeafInterpreter(bounds.universe(), lowers, uppers, bounds.intBounds(), NumericFactory.factory(options, numVars), vars);
    }

    /**
     * Populates the {@code vars} map with bindings from each relation in
     * {@code rels} to an integer range, which specifies the identifiers of the
     * variables used to encode the contents of that relation. The resulting integer
     * ranges put together form a complete range that starts at {@code minVar}.
     *
     * @requires lowers.universe = uppers.universe
     * @requires all r: rels | lowers.get(r).tuples in uppers.get(r).tuples
     * @ensures vars.map' = vars.map ++ { r: rels, v: IntRange | v.size() =
     *          uppers.get(r).size() && v.size() > 0 }
     * @ensures min(vars.map'[rels]) = minVar && max(vars.map'[rels]) = minVar +
     *          (sum r: rels | vars.map'[r].size()) - 1
     * @return sum r: rels | vars.map'[r].size()
     */
    private static int allocateVars(int minVar, Map<Relation,IntRange> vars, Set<Relation> rels, Map<Relation,TupleSet> lowers, Map<Relation,TupleSet> uppers) {
        int maxVar = minVar;
        for (Relation r : rels) {
            int rVars = uppers.get(r).size();
            if(rVars > 0){
                vars.put(r, Ints.range(maxVar, maxVar + rVars - 1));
                maxVar += rVars;
            }
        }
        return maxVar - minVar;
    }

    /**
     * Returns this.factory.
     *
     * @return this.factory.
     */
    public final NumericFactory factory() {
        return this.factory;
    }

    /**
     * Returns the universe of discourse.
     *
     * @return this.universe
     */
    public final Universe universe() {
        return universe;
    }

    /**
     * Returns this.vars.
     *
     * @return this.vars.
     */
    public final Map<Relation, IntSet> vars() {
        final Map<Relation,IntSet> ret = new LinkedHashMap<Relation,IntSet>((vars.size() * 4) / 3);
        for (Map.Entry<Relation,IntRange> e : vars.entrySet()) {
            ret.put(e.getKey(), Ints.rangeSet(e.getValue()));
        }
        return ret;
    }

    /**
     * Returns a set view of the variables assigned to the given relation, or empty
     * set if no variables were assigned to the given relation.
     *
     * @return this.vars[r]
     */
    public final IntSet vars(Relation r) {
        final IntRange v = vars.get(r);
        return v == null ? Ints.EMPTY_SET : Ints.rangeSet(v);
    }

    /**
     * Returns a {@link NumericMatrix matrix} m of
     * {@link kodkod.engine.num.NumericValue numeric values} representing the
     * specified relation.
     */
    public final NumericMatrix interpret(Relation r) {
        if (!lowers.containsKey(r))
            throw new UnboundLeafException("Unbound relation: ", r);
        final IntSet lowerBound = lowers.get(r).indexView();
        final IntSet upperBound = uppers.get(r).indexView();

        final NumericMatrix m = r.isQuantitative() ?
                        factory.matrix(Dimensions.square(universe().size(), r.arity()), upperBound, lowerBound) :
                        factory.booleanMatrix(Dimensions.square(universe().size(), r.arity()), upperBound, lowerBound);

        if(vars.containsKey(r)) {
            int varId = vars.get(r).min() - 1;
            boolean isQuantitative = r.isQuantitative();
            for (IntIterator indeces = upperBound.iterator(); indeces.hasNext(); ) {
                int tupleIndex = indeces.next();
                if(lowerBound.contains(tupleIndex) && !isQuantitative) {
                    // R[i] = 1
                    m.set(tupleIndex, ONE);
                }
                else{
                    NumericVariable cell = factory.variable(varId++);
                    NumericValue v = cell;
                    if(lowerBound.contains(tupleIndex))
                        // R[i] != 0
                        cell.setConstraint(true);
                    else if(!lowerBound.contains(tupleIndex) && !isQuantitative) {
                        // R[i] = 0 | R[i] = 1
                        v = factory.toBool(cell);
                    }
                    // else !lb && isQuantitative => free variable, no need to add further constraints at this point
                    m.set(tupleIndex, v);
                }
            }
        }
        else if(isConstant){
                // Numeric Matrix
                if(r.isQuantitative() && uppers.get(r) instanceof QtTupleSet){
                    final QtTupleSet qtSet = (QtTupleSet) uppers.get(r);
                    for (IntIterator indeces = upperBound.iterator(); indeces.hasNext(); ) {
                        int tupleIndex = indeces.next();
                        m.set(tupleIndex, factory.constant(qtSet.getWeight(tupleIndex)));
                    }
                }
                // Boolean Matrix
                else for (IntIterator indeces = upperBound.iterator(); indeces.hasNext(); ) {
                    int tupleIndex = indeces.next();
                    m.set(tupleIndex, ONE);
                }
        }

        return m;
    }

    /**
     * Returns a {@link NumericMatrix matrix} m of
     * {@link kodkod.engine.num.NumericValue numeric values} representing the
     * specified constant expression.
     */
    public final NumericMatrix interpret(ConstantExpression c) {
        final int univSize = universe().size();
        if (c == Expression.UNIV) {
            final IntSet all = Ints.rangeSet(Ints.range(0, univSize - 1));
            return factory().booleanMatrix(Dimensions.square(univSize, 1), all, all);
        } else if (c == Expression.IDEN) {
            final Dimensions dim2 = Dimensions.square(univSize, 2);
            final IntSet iden = Ints.bestSet(dim2.capacity());
            for (int i = 0; i < univSize; i++) {
                iden.add(i * univSize + i);
            }
            return factory().matrix(dim2, iden, iden);
        } else if (c == Expression.NONE) {
            return factory().matrix(Dimensions.square(univSize, 1), Ints.EMPTY_SET, Ints.EMPTY_SET);
        } else if (c == Expression.INTS) {
            final IntSet ints = Ints.bestSet(univSize);
            for (IntIterator iter = ints().iterator(); iter.hasNext();) {
                ints.add(interpret(iter.next()));
            }
            return factory().matrix(Dimensions.square(univSize, 1), ints, ints);
        } else {
            throw new IllegalArgumentException("unknown constant expression: " + c);
        }
    }

    /**
     * Returns the set of all integers corresponding to some atom in this.universe.
     *
     * @return this.ints
     */
    public final IntSet ints() {
        return ints.indices();
    }

    /**
     * Returns the index of the atom from this.universe which represents the given
     * integer.
     *
     * @requires i in this.ints
     * @return this.ibounds[i].indexView().min()
     */
    public final int interpret(int i) {
        return ints.get(i).indexView().min();
    }
}

