package kodkod.engine.num;

import static kodkod.engine.bool.Operator.MAX;

/**
 * Specifies the selection of the maximum value between two numeric inputs.
 */
public final class MaxGate extends ChoiceGate{
    MaxGate(int label, NumericValue left, NumericValue right){
        super(MAX, label, left, right);
    }
}