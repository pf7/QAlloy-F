package kodkod.engine.num2common;

import kodkod.engine.bool.*;
import kodkod.engine.bool.ITEGate;
import kodkod.engine.num.*;
import kodkod.util.collections.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static kodkod.engine.bool.Operator.DIV;
import static kodkod.engine.num.NumericConstant.ZERO;

/**
 * Implements a traversal over Numeric Circuits to gather the details of each division that occurs within:
 * - division id
 * - denominator value id
 * - primary variables that occur on the numerator
 * - primary variables that occur on the denominator
 */
public class DivisionDetector implements BooleanVisitor<Integer,Object>, NumericVisitor<Integer, Object> {

    // The factory responsible for the circuit at hand
    private final NumericFactory factory;
    /*
     * Associates each (chained) division gate label with mapping between the identifier of the
     * denominator of each fraction and the primary variables that occur on both its numerator
     * and denominator.
     * <Div, <Denominator, <NumeratorPVar*, DenominatorPVar*>*>>
     */
    private final Map<Integer, Map<Integer, Pair<Set<Integer>, Set<Integer>>>> denominators;
    // Stores each node relevant to the divisions that occur in the given circuit.
    private final Map<Integer, NumericValue> nodes;
    // Flag indicating if the primary variables occur in the division
    private boolean divFlag;
    // Refers to the numerator/denominator primary variables set of the division being traversed at a point in time
    private Set<Integer> current;

    private DivisionDetector(NumericFactory factory) {
        this.factory = factory;
        this.denominators = new HashMap<>();
        this.nodes = new HashMap<>();
        this.divFlag = false;
    }

    /**
     * Gathers the details of each division occuring within the given Numeric Circuit.
     */
    public static DivisionDetector detectDivision(NumericFactory factory, Collection<BooleanFormula> problem){
        final DivisionDetector detector = new DivisionDetector(factory);
        for(BooleanFormula f : problem)
            f.accept(detector, null);
        return detector;
    }

    /**
     * @return true if there are divisions on the circuit.
     */
    public boolean hasDivision(){
        return this.denominators.size() > 0;
    }

    /**
     * @return Formula checking if division by zero occurs for the given circuit.
     */
    public BooleanValue divisionByZero(){
        BooleanValue divByZero = BooleanConstant.FALSE;
        BooleanValue fracDivByZero;

        Function<Integer, BooleanValue> isNEQzero = p -> factory.neq(nodes.get(p), ZERO);

        if(this.hasDivision()){
            for(Map<Integer, Pair<Set<Integer>, Set<Integer>>> fracs : this.denominators.values())
                for(Map.Entry<Integer, Pair<Set<Integer>, Set<Integer>>> frac : fracs.entrySet()){
                    Integer denom = frac.getKey();
                    Set<Integer> nums = frac.getValue().a;
                    Set<Integer> denoms = frac.getValue().b;

                    // denominator = 0
                    fracDivByZero = factory.eq(nodes.get(denom), ZERO);

                    // denominator = 0 & exists p : primary variables appearing on the fraction | p != 0
                    if(!(nums.size() == 0 && denoms.size() == 0))
                        fracDivByZero = factory.and(fracDivByZero,
                                Stream.concat(nums.stream(), denoms.stream())
                                        .map(isNEQzero)
                                        .reduce((BooleanValue) BooleanConstant.FALSE, factory::or, factory::or));

                    divByZero = factory.or(divByZero, fracDivByZero);
                }
        }

        return divByZero;
    }

    /**
     * Checks the gate's inputs.
     * @return Gate identifier
     */
    @Override
    public Integer visit(MultiGate multigate, Object arg) {
        final int gate = multigate.label();
        for(BooleanFormula input : multigate)
            input.accept(this, arg);
        return gate;

    }

    /**
     * Checks the gate's inputs.
     * @return Gate identifier
     */
    @Override
    public Integer visit(ITEGate itegate, Object arg) {
        final int gate = itegate.label();
        itegate.input(0).accept(this, arg);
        itegate.input(1).accept(this, arg);
        itegate.input(2).accept(this, arg);
        return gate;
    }

    /**
     * Checks the gate's input.
     * @return Gate identifier
     */
    @Override
    public Integer visit(NotGate negation, Object arg) {
        int gate = negation.label();
        negation.input(0).accept(this, arg);
        return gate;
    }

    /**
     * Does nothing.
     * @return variable
     */
    @Override
    public Integer visit(BooleanVariable variable, Object arg) {
        return variable.label();
    }

    /**
     * Checks the gate's inputs if op != DIV.
     * If op = DIV, gathers the information associated with this division.
     *
     * @return Gate identifier
     */
    @Override
    public Integer visit(AritGate aritgate, Object arg) {
        final int gate = aritgate.label();
        if(aritgate.op() == DIV){
            // Information associated with this division
            Map<Integer, Pair<Set<Integer>, Set<Integer>>> fracs = new HashMap<>();
            // Primary variables of Denominator
            Set<Integer> denom;
            // Primary variables of <Numerator, Denominator>
            Pair<Set<Integer>, Set<Integer>> frac;

            denominators.put(gate, fracs);

            divFlag = true;
            // First numerator
            current = new TreeSet<>();
            Iterator<NumericValue> it = aritgate.iterator();
            Integer x = it.next().accept(this, arg);
            NumericValue denumValue;
            // Handling division chain
            while(it.hasNext()){
                denom = new TreeSet<>();
                // Fraction: This numerator is the previous denominator, due to chaining
                frac = new Pair<>(current, denom);
                // Denominator
                current = denom;
                denumValue = it.next();
                x = denumValue.accept(this, arg);
                fracs.put(x, frac);
                nodes.put(x, denumValue);
            }
            divFlag = false;
        }
        else for(NumericValue input : aritgate)
            input.accept(this, arg);
        return gate;
    }

    /**
     * Checks the gate's inputs.
     * @return Gate identifier
     */
    @Override
    public Integer visit(ChoiceGate choicegate, Object arg) {
        final int gate = choicegate.label();
        choicegate.input(0).accept(this, arg);
        choicegate.input(1).accept(this, arg);
        if(choicegate instanceof kodkod.engine.num.ITEGate)
            ((kodkod.engine.num.ITEGate)choicegate).getCondition().accept(this, arg);
        return gate;
    }

    /**
     * Checks the gate's input.
     * @return Gate identifier
     */
    @Override
    public Integer visit(UnaryGate unarygate, Object arg) {
        final int gate = unarygate.label();
        unarygate.input().accept(this, arg);
        return gate;
    }

    /**
     * Stores the primary variable in the current set if it it's within a division (sub)-expression.
     * @return variable id
     */
    @Override
    public Integer visit(NumericVariable variable, Object arg) {
        int l = variable.label();
        if(divFlag){
            current.add(l);
            nodes.put(l, variable);
        }
        return l;
    }

    /**
     * Does nothing.
     * @return variable
     */
    @Override
    public Integer visit(NumericConstant constant, Object arg) {
        return constant.label();
    }

    /**
     * Checks the gate's inputs.
     * @return Gate identifier
     */
    @Override
    public Integer visit(CmpGate cmpgate, Object arg) {
        final int gate = cmpgate.label();
        cmpgate.inputNum(0).accept(this, arg);
        cmpgate.inputNum(1).accept(this, arg);
        return gate;
    }

    /**
     * Checks the gate's input.
     * @return Gate identifier
     */
    @Override
    public Integer visit(NumNotGate notg, Object arg) {
        final int gate = notg.label();
        notg.input().accept(this, arg);
        return gate;
    }

    /**
     * Checks the numeric component of this binary value.
     * @return binary value identifier
     */
    @Override
    public Integer visit(BinaryValue bool, Object arg) {
        final int bv = bool.label();
        bool.toNumeric().accept(this, arg);
        return bv;
    }
}
