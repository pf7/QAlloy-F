package kodkod.engine.num;

import kodkod.engine.bool.BooleanValue;
import kodkod.engine.bool.Operator;

import java.util.List;

import static kodkod.engine.bool.BooleanConstant.FALSE;
import static kodkod.engine.bool.BooleanConstant.TRUE;
import static kodkod.engine.bool.Operator.VAR;
import static kodkod.engine.num.NumericConstant.ZERO;

/**
 * Represents a numeric variable,
 * which can be one of the following:
 * - integer variable -- will assume a integer value
 * - fuzzy variable -- will represent a double value            [0 <= this <= 1]
 *
 * @invariant op = Operator.VAR
 * @specfield constraint : Boolean
 *
 * constraint = null => free variable
 * constraint = true => this != 0
 * constraint = false => this = 0
 *
 * @specfield maxValue : NumericConstant
 *
 * maxValue = null => free variable
 * maxValue != null => this <= maxValue
 *
 * @specfield potentialValues : NumericValue*
 *
 * Optionally, potentialValues enumerates the values that this variable is allowed to assume.
 * In case potentialValues is non-null, *all* the previous specification should be ignored,
 * and only this list of values be taken into account, as long as the solver at hand
 * has any way of handling such listing.
 *
 */
public class NumericVariable extends NumericValue{

    private final int                             label;
    private Boolean                          constraint;
    private NumericConstant                    maxValue;
    private List<NumericValue>          potentialValues;

    /**
     * Constructs a new NumericVariable with the given label.
     *
     * @requires label != 0
     * @ensures this.label' = label && constraint' = null
     */
    NumericVariable(int label){
        this.label = label;
        this.constraint = null;
        this.maxValue = null;
        this.potentialValues = null;
    }

    /**
     * Constructs a new NumericVariable with the given label,
     * and the intended boolean representation
     *
     * @requires label != 0
     * @ensures this.label' = label && constraint' = constraint
     */
    NumericVariable(int label, Boolean representsTrue){
        this.label = label;
        this.constraint = representsTrue;
        this.maxValue = null;
    }

    /**
     * Returns true if this variable is expected to represent a true value from the
     * boolean point-of-view.
     *
     * @return constraint != null && constraint
     */
    public boolean isTrue(){
        return constraint != null && constraint;
    }

    /**
     * Returns false if this variable is expected to represent a false value from the
     * boolean point-of-view.
     *
     * @return constraint != null && !constraint
     */
    public boolean isFalse(){
        return constraint != null && !constraint;
    }

    /**
     * Updates the value constraint of this variable, from the boolean point-of-view.
     * @param representsTrue true => this != 0, false => this = 0, null => free value
     */
    public void setConstraint(Boolean representsTrue){
        this.constraint = representsTrue;
    }

    /**
     * Returns the current maximum value of this variable
     * @return maxValue
     */
    public NumericConstant getMaximumValue() {
        return maxValue;
    }

    /**
     * Sets the maximum value of this variable
     * @param maxValue Maximum value (can be null)
     */
    public void setMaximumValue(NumericConstant maxValue) {
        this.maxValue = maxValue;
    }

    /**
     * Specifies the list of potential values that this variable can assume.
     * @param possibleValues List of allowed values (can be null)
     *                       If non-null, but the list no elements, it is
     *                       the same as a singleton list with 0 has its only element.
     */
    public void setPotentialValues(List<NumericValue> possibleValues){
        if(possibleValues != null && possibleValues.size() == 0)
            possibleValues.add(ZERO);
        this.potentialValues = possibleValues;
    }

    /**
     * Returns the supported values for this variable
     * @return this.potentialValues
     */
    public List<NumericValue> getPotentialValues() {
        return potentialValues;
    }

    /**
     * Returns the negation of this variable.
     */
    @Override
    public BooleanValue negation() {
        return isTrue() ? FALSE : (isFalse() ? TRUE : super.negation());
    }

    /**
     * Passes this value and the given argument value to the visitor, and returns
     * the resulting value.
     *
     * @return the value produced by the visitor when visiting this node with the
     *         given argument.
     */
    @Override
    public <T, A> T accept(NumericVisitor<T, A> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    /**
     * Returns the label for this variable.
     *
     * @return this.label
     */
    @Override
    public int label() {
        return label;
    }

    /**
     * Returns the VAR operator.
     *
     * @return Operator.VAR
     */
    @Override
    public Operator op() {
        return VAR;
    }

    /**
     * Returns a string representation of this numeric variable.
     *
     * @return a string representation of this numeric variable.
     */
    @Override
    public String toString(){ return "VAR" + label; }
}
