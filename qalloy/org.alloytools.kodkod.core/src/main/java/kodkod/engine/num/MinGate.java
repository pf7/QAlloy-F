package kodkod.engine.num;

import static kodkod.engine.bool.Operator.MIN;

/**
 * Specifies the selection of the minimum value between two numeric inputs.
 */
public final class MinGate extends ChoiceGate{
    MinGate(int label, NumericValue left, NumericValue right){
        super(MIN, label, left, right);
    }
}
