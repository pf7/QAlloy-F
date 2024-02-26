package kodkod.engine.num;

/**
 * Visits {@link NumericValue numeric values}. In
 * addition to passing themselves as the argument to the visitor, the numeric
 * values also pass along satelite information of type A.
 */
public interface NumericVisitor<T,A> {

    /**
     * Visits the arithmetic gate and returns the result.
     *
     * @return the result of visiting the given AritGate
     */
    public T visit(AritGate aritgate, A arg);

    /**
     * Visits the choice gate and returns the result.
     *
     * @return the result of visiting the given ChoiceGate
     */
    public T visit(ChoiceGate choicegate, A arg);

    /**
     * Visits the unary gate and returns the result.
     *
     * @return the result of visiting the given UnaryGate
     */
    public T visit(UnaryGate unarygate, A arg);

    /**
     * Visits the numeric variable and returns the result.
     *
     * @return the result of visiting the given variable
     */
    public T visit(NumericVariable variable, A arg);

    /**
     * Visits the numeric constant and returns the result.
     *
     * @return the result of visiting the given constant
     */
    public T visit(NumericConstant constant, A arg);

    /**
     * Visits the binary numeric value and returns the result.
     *
     * @return the result of visiting the given value
     */
    default public T visit(BinaryValue bool, A arg){
        throw new IllegalArgumentException("This visitor does not support boolean optimizations over quantitative solving.");
    }
}
