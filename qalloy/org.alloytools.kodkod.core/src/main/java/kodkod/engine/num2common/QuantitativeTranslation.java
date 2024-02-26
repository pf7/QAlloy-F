package kodkod.engine.num2common;

import java.util.*;
import kodkod.ast.Relation;
import kodkod.engine.config.QuantitativeOptions;
import kodkod.engine.fol2sat.Translation;
import kodkod.engine.fol2sat.TranslationLog;
import kodkod.engine.num2smt.SMTSolver;
import kodkod.engine.satlab.SATSolver;
import kodkod.instance.*;
import kodkod.util.ints.IntIterator;
import kodkod.util.ints.IntSet;
import kodkod.util.ints.Ints;

/**
 * Stores the translation of a quantitative Kodkod problem. A problem consists of a
 * {@linkplain kodkod.ast.Formula formula}, {@linkplain Bounds bounds} and
 * {@linkplain QuantitativeOptions options}.
 *
 * The translation can currently be a SmtTranslation, i.e., represented by a SMT-LIB2 specification.
 */
public abstract class QuantitativeTranslation extends Translation {

    private final QuantitativeSolver solver;
    private final QuantitativeOptions  options;
    private final Map<Relation,IntSet> primaryVarUsage;
    private final TranslationLog       log;
    private final int                  maxPrimaryVar;

    protected QuantitativeTranslation(Bounds bounds, QuantitativeOptions options, QuantitativeSolver solver, Map<Relation,IntSet> varUsage, int maxPrimaryVar, TranslationLog log) {
        super(bounds, null);
        this.options = options;
        this.solver = solver;
        this.log = log;
        this.maxPrimaryVar = maxPrimaryVar;
        this.primaryVarUsage = varUsage;
    }

    /**
     * Creates a smt translation using the given bounds, options, solver, var map,
     * and log.
     */
    public static SmtTranslation smtTranslation(Bounds bounds, QuantitativeOptions options, SMTSolver solver, Map<Relation,IntSet> varUsage, int maxPrimaryVar, TranslationLog log){
        return new SmtTranslation(bounds, options, solver, varUsage, maxPrimaryVar, log);
    }

    /**
     * If {@code this.solver.solve()} is true, returns an interpretation of the quantitative
     * solution as a weighted mapping from Relations to sets of weighted Tuples.
     * The returned instance maps all relations in {@code this.bounds} and, therefore,
     * all relations in {@code this.originalBounds}.
     *
     * @return a new instance of the problem
     */
    @Override
    public Instance interpret(){ return interpret(this.bounds); }

    @Override
    public Instance interpret(Bounds bounds) {
        final kodkod.instance.Instance instance = new Instance(bounds.universe());
        final TupleFactory f = bounds.universe().factory();

        for (Relation r : bounds.relations()) {
            TupleSet lower = bounds.lowerBound(r);
            IntSet indices = Ints.bestSet(lower.capacity());
            Map<Integer, Number> weight = new HashMap<>();

            indices.addAll(lower.indexView());

            IntIterator it = indices.iterator();
            while(it.hasNext())
                weight.put(it.next(), 1);

            IntSet vars = primaryVariables(r);
            if (!vars.isEmpty()) {
                // System.out.println(r + ": [" + vars.min() + ", " + vars.max()
                //        + "]");
                int id = vars.min() - 1;
                for (IntIterator iter = bounds.upperBound(r).indexView().iterator(); iter.hasNext();) {
                    final int index = iter.next();

                    if(solver.contains(id) && solver.getBooleanValue(id)) {
                        if(!indices.contains(index))
                            indices.add(index);
                        weight.put(index, solver.getValue(id));
                    }

                    id++;
                }
            }
            // Check if the relation is represented by a Boolean Matrix
            boolean isBoolean = weight.values().stream().allMatch(n -> n.doubleValue() == 1.0);

            if(!isBoolean && !r.isQuantitative())
                throw new IllegalArgumentException("Boolean relation with non-boolean weights: " + r + " - " + weight);

            instance.add(r, !r.isQuantitative() ? f.setOf(r.arity(), indices) : f.setOf(r.arity(), indices, weight));
        }

        return instance;
    }

    /**
     * {@inheritDoc}
     *
     * @see kodkod.engine.fol2sat.Translation#primaryVariables(kodkod.ast.Relation)
     */
    @Override
    public IntSet primaryVariables(Relation relation) {
        IntSet vars = primaryVarUsage.get(relation);
        return vars == null ? Ints.EMPTY_SET : vars;
    }

    /**
     * {@inheritDoc}
     *
     * @see kodkod.engine.fol2sat.Translation#numPrimaryVariables()
     */
    @Override
    public int numPrimaryVariables() {
        return maxPrimaryVar;
    }

    /**
     * Returns the quantitative options for this problem
     * @return this.options
     */
    public QuantitativeOptions getOptions(){
        return this.options;
    }

    /**
     * Throws an unsupported operation exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public SATSolver cnf() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the SMTSolver responsible for this translation.
     *
     * @return this.solver
     */
    public QuantitativeSolver solver(){
        return this.solver;
    }

    /**
     * Returns true iff this translation is trivially true or trivially false.
     */
    @Override
    public boolean trivial() {
        return this.solver.numberOfVariables() == 0;
    }

    /**
     * If translation logging was enabled (by setting
     * {@code this.options.logTranslation > 0}), returns the
     * {@linkplain TranslationLog log} of {@linkplain TranslationRecord records}
     * generated for this translation. Otherwise returns null.
     *
     * @return translation log for this translation, if one was generated, or null
     *         otherwise
     */
    public TranslationLog log() {
        return log;
    }

    /**
     * Translation into a SMT Problem.
     */
    public static final class SmtTranslation extends QuantitativeTranslation{

        private SmtTranslation(Bounds bounds, QuantitativeOptions options, SMTSolver solver, Map<Relation,IntSet> varUsage, int maxPrimaryVar, TranslationLog log) {
            super(bounds, options, solver, varUsage, maxPrimaryVar, log);
        }

    }
}
