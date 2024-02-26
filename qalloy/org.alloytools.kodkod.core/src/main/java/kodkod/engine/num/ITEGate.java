package kodkod.engine.num;

import kodkod.engine.bool.BooleanFormula;

import static kodkod.engine.bool.Operator.ITE;

/**
 * Specifies an if-then-else gate which selects one of two numeric values given some condition.
 */
public final class ITEGate extends ChoiceGate{

    private final BooleanFormula condition;

    ITEGate(int label, BooleanFormula i, NumericValue t, NumericValue e) {
        super(ITE, label, t, e);
        this.condition = i;
    }

    /**
     * Returns the choice condition of this gate.
     * @return this.condition
     */
    public BooleanFormula getCondition(){
        return condition;
    }

    @Override
    public String toString() {
        return "(" + condition + "?" + super.input(0) + ":" + super.input(1) + ")";
    }

}