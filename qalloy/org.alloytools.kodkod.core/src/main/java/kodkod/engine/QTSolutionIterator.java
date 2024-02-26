package kodkod.engine;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import kodkod.ast.Formula;
import kodkod.ast.Relation;
import kodkod.engine.config.QuantitativeOptions;
import kodkod.engine.fol2sat.Translator;
import kodkod.engine.num2common.QuantitativeSolver;
import kodkod.engine.num2common.QuantitativeTranslation;
import kodkod.engine.num2smt.SMTSolver;
import kodkod.engine.num2smt.SMTStatistics;
import kodkod.engine.satlab.SATAbortedException;
import kodkod.instance.Bounds;
import kodkod.instance.TupleSet;

/**
 * Quantitative extension of {@link SolutionIterator}.
 */
public final class QTSolutionIterator implements Iterator<Solution> {

    private QuantitativeTranslation translation;
    private long                    translTime;
    private int                     trivial;
    private QuantitativeOptions     options;

    /**
     * Constructs a solution iterator for the given formula, bounds, and quantitative options.
     */
    QTSolutionIterator(Formula formula, Bounds bounds, QuantitativeOptions options) {
        this.translTime = System.currentTimeMillis();
        this.translation = Translator.translate(formula, bounds, options);
        this.translTime = System.currentTimeMillis() - translTime;
        this.trivial = 0;
        this.options = options;
    }

    /**
     * Returns true if there is another solution.
     *
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        return translation != null;
    }

    /**
     * Returns the next solution if any.
     *
     * @see java.util.Iterator#next()
     */
    @Override
    public Solution next() {
        if (!hasNext())
            throw new NoSuchElementException();
        try {
            return translation.trivial() ? nextTrivialSolution() : nextNonTrivialSolution();
        } catch (SATAbortedException sae) {
            translation.solver().free();
            throw new AbortedException(sae);
        }
    }

    /** @throws UnsupportedOperationException */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Solves {@code translation.solver} and adds the negation of the found model to
     * the set of clauses. The latter has the effect of forcing the solver to come
     * up with the next solution or return UNSAT. If
     * {@code this.translation.solver.solve()} is false, sets {@code this.translation}
     * to null.
     *
     * @requires this.translation != null
     * @ensures this.translation.solver is modified to eliminate the current solution
     *          from the set of possible solutions
     * @return current solution
     */
    private Solution nextNonTrivialSolution() {
        final QuantitativeTranslation transl = translation;

        final QuantitativeSolver solver = transl.solver();
        final int primaryVars = transl.numPrimaryVariables();

        final long startSolve = System.currentTimeMillis();
        final boolean isSat = solver.solve();
        final long endSolve = System.currentTimeMillis();

        final Statistics stats;
        final Solution sol;

        // Statistics
        switch(options.solver()){
            case CVC4:
            case Z3:
            case MathSAT:
            case Yices:
                stats = new SMTStatistics(transl, translTime, endSolve - startSolve);
                break;
            default:
                stats = new Statistics(transl, translTime, endSolve - startSolve);
                break;
        }

        if (isSat) {
            // extract the current solution; can't use the sat(..) method
            // because it frees the sat solver
            sol = Solution.satisfiable(stats, transl.interpret());
            // add the negation of the current model to the solver
            Collection<Integer> notModel = IntStream.range(0, primaryVars)
                                                    .boxed()
                                                    .collect(Collectors.toSet());
            solver.elimSolution(notModel);
        } else {
            // Check for unknown result from a SMT Problem
            if(stats instanceof SMTStatistics && ((SMTSolver) solver).getResult().isUnknown()){
                sol = Solver.unknown((QuantitativeTranslation.SmtTranslation)transl, (SMTStatistics)stats);
            }
            else sol = Solver.unsat(transl, stats);
            translation = null; // unknown/unsat, no more solutions
            solver.free(); //free resources
        }
        return sol;
    }

    /**
     * Returns the trivial solution corresponding to the trivial translation stored
     * in {@code this.translation}, and if {@code this.translation.solver.solve()} is
     * true, sets {@code this.translation} to a new translation that eliminates the
     * current trivial solution from the set of possible solutions. The latter has
     * the effect of forcing either the translator or the solver to come up with the
     * next solution or return UNSAT. If {@code this.translation.solver.solve()} is
     * false, sets {@code this.translation} to null.
     *
     * @requires this.translation != null
     * @ensures this.translation is modified to eliminate the current trivial
     *          solution from the set of possible solutions
     * @return current solution
     */
    private Solution nextTrivialSolution() {
        final QuantitativeTranslation transl = this.translation;

        final Solution sol = Solver.trivial(transl, translTime);

        if (sol.instance() == null) {
            translation = null; // unsat, no more solutions
        } else {
            trivial++;

            final Bounds bounds = transl.bounds();
            final Bounds newBounds = bounds.clone();
            final List<Formula> changes = new ArrayList<Formula>();

            for (Relation r : bounds.relations()) {
                final TupleSet lower = bounds.lowerBound(r);

                if (lower != bounds.upperBound(r)) { // r may change
                    if (lower.isEmpty()) {
                        changes.add(r.some());
                    } else {
                        final Relation rmodel = !r.isQuantitative() ? Relation.nary(r.name() + "_" + trivial, r.arity()) : Relation.quantitative_nary(r.name() + "_" + trivial, r.arity());
                        newBounds.boundExactly(rmodel, lower);
                        changes.add(r.eq(rmodel).not());
                    }
                }
            }

            // nothing can change => there can be no more solutions (besides the
            // current trivial one).
            // note that transl.formula simplifies to the constant true with
            // respect to
            // transl.bounds, and that newBounds is a superset of transl.bounds.
            // as a result, finding the next instance, if any, for
            // transl.formula.and(Formula.or(changes))
            // with respect to newBounds is equivalent to finding the next
            // instance of Formula.or(changes) alone.
            final Formula formula = changes.isEmpty() ? Formula.FALSE : Formula.or(changes);

            final long startTransl = System.currentTimeMillis();
            translation = Translator.translate(formula, newBounds, options);
            translTime += System.currentTimeMillis() - startTransl;
        }
        return sol;
    }

}
