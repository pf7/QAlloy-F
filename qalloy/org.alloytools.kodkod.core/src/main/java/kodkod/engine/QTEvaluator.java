package kodkod.engine;

import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IntExpression;
import kodkod.engine.config.QuantitativeOptions;
import kodkod.engine.fol2sat.Translator;
import kodkod.engine.num.NumericConstant;
import kodkod.engine.num.NumericMatrix;
import kodkod.instance.Instance;
import kodkod.instance.TupleFactory;
import kodkod.instance.TupleSet;
import kodkod.util.ints.IntIterator;

import java.util.*;

/**
 * Quantitative extension of {@link Evaluator}.
 *
 * @specfield options: QuantitativeOptions
 */
public class QTEvaluator extends Evaluator {

    private final QuantitativeOptions options;

    /**
     * Constructs a new QTEvaluator for the given instance, with the given quantitative configuration.
     *
     * @ensures this.instance' = instance && this.options' = options
     * @throws NullPointerException instance = null
     */
    public QTEvaluator(Instance instance, QuantitativeOptions options) {
        super(instance);
        this.options = options;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean evaluate(Formula formula) {
        if (formula == null)
            throw new NullPointerException("formula");
        return Translator.evaluate(formula, super.instance(), options).booleanValue();
    }

    /**
     * Helper method to convert the given matrix with a specific arity into the adequate kind of TupleSet.
     * The resulting TupleSet is constructed as a {@link kodkod.instance.QtTupleSet} if
     * it is represented by a Numeric Matrix and as a {@link kodkod.instance.TupleSet}
     * in case it is characterized by a Boolean Matrix.
     */
    private TupleSet matrixToTS(NumericMatrix m, int arity){
        //Extract the weight associated with each tuple
        final Map<Integer, Number> weight = new HashMap<>();
        IntIterator it = m.denseIndices().iterator();
        while(it.hasNext()){
            int i = it.next();
            if(m.get(i) instanceof NumericConstant)
                weight.put(i, ((NumericConstant)m.get(i)).getValue());
        }

        TupleFactory f = super.instance().universe().factory();
        // Check if the relation is represented by a Boolean Matrix
        return m.isBoolean() ? f.setOf(arity, m.denseIndices()) : f.setOf(arity, m.denseIndices(), weight);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TupleSet evaluate(Expression expression) {
        if (expression == null)
            throw new NullPointerException("expression");
        return matrixToTS(Translator.evaluate(expression, super.instance(), options), expression.arity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object evaluate(IntExpression intExpr) {
        if (intExpr == null)
            throw new NullPointerException("intexpression");
        final NumericMatrix sol = Translator.evaluate(intExpr, super.instance(), options);
        int arity = sol.dimensions().numDimensions();
        return matrixToTS(sol, arity);
    }

    /**
     * Returns the quantitative options used by this evaluator.
     *
     * @return this.options
     */
    public QuantitativeOptions quantitativeOptions(){
        return this.options;
    }

    /**
     * @return false
     */
    @Override
    public boolean wasOverflow() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return super.toString() + "\n" + "Quantitative Options:\n" + options;
    }
}
