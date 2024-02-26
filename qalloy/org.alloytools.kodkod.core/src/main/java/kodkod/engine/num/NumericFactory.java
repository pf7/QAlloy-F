package kodkod.engine.num;

import static kodkod.engine.config.QuantitativeOptions.QuantitativeType;
import kodkod.engine.bool.*;
import kodkod.engine.config.QuantitativeOptions;
import kodkod.util.ints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.BiFunction;

import static kodkod.engine.bool.BooleanConstant.FALSE;
import static kodkod.engine.bool.BooleanConstant.TRUE;
import static kodkod.engine.bool.Operator.*;
import static kodkod.engine.num.NumericConstant.ONE;
import static kodkod.engine.num.NumericConstant.ZERO;
import static kodkod.engine.config.QuantitativeOptions.*;

/**
 * A factory for creating {@link NumericValue numeric values} and {@link NumericMatrix matrices}.
 */
public abstract class NumericFactory {

    protected int label;
    private Map<Integer, NumericVariable> vars;
    private int maxPrimaryVariable;

    /**
     * Initializes a new numeric factory.
     */
    private NumericFactory(){
        this.label = 0;
        this.vars = new HashMap<>();
        this.maxPrimaryVariable = 0;
    }

    /**
     * Returns a new numeric factory of numeric values.
     * @param opt Solving options
     *
     * opt.getAnalysisType() -> Type of factory being instantiated,
     *           INTEGER       => IntegerFactory
     *           FUZZY         => FuzzyFactory
     */
    public static NumericFactory factory(QuantitativeOptions opt){
        switch(opt.getAnalysisType()) {
            case INTEGER:
                return new IntegerFactory();
            default: // FUZZY
                return new FuzzyFactory(opt.getTnorm());
        }
    }

    /**
     * Returns a new numeric factory of numeric values,
     * initialized to contain the given number of numeric variables.
     * @param opt Solving options
     *
     * opt.getAnalysisType() -> Type of factory being instantiated,
     *           INTEGER       => IntegerFactory
     *           FUZZY         => FuzzyFactory
     */
    public static NumericFactory factory(QuantitativeOptions opt, int numVars){
        NumericFactory f = NumericFactory.factory(opt);
        f.addVariables(numVars);
        return f;
    }

    /**
     * Returns the context of this factory.
     * @return this : IntegerFactory    => INTEGER
     *         this : FuzzyFactory      => FUZZY
     */
    public abstract QuantitativeType factoryType();

    /**
     * Returns the NumericConstant representing the given value.
     */
    public final NumericConstant constant(int value){
        if(value == 0)
            return ZERO;

        if(value == 1)
            return NumericConstant.ONE;

        return new NumericConstant(label++, value);
    }

    /**
     * Returns the NumericConstant representing the given value,
     * wrt this factory main type of values.
     * IntegerFactory    => value : Integer
     * FuzzyFactory      => value : Double
     */
    public abstract NumericConstant constant(Number value);

    /**
     * Creates a fresh numeric variable.
     */
    public final NumericVariable freshVariable(){
        NumericVariable v = new NumericVariable(label++);
        vars.put(v.label(), v);
        //Update the primary variable with the highest label
        maxPrimaryVariable = v.label() + 1;
        return v;
    }

    /**
     * Adds the specified number of fresh variables to {@code this.vars}.
     *
     * @requires numVars >= 0
     */
    public final void addVariables(int numVars) {
        if (numVars < 0) {
            throw new IllegalArgumentException("Expected numVars >= 0, given numVars = " + numVars);
        } else if (numVars > 0) {
            for (int i = 0; i < numVars; i++) {
                this.freshVariable();
            }
        } // else do nothing
    }

    /**
     * Returns the variable with the given label.
     *
     * @requires 0 < label <= numberOfVariables()
     * @return this.vars[label]
     */
    public final NumericVariable variable(int label) {
        if(!vars.containsKey(label))
            throw new IllegalArgumentException("Expected a variable label, given label = " + label);
        return vars.get(label);
    }

    /**
     * Returns the number of variables in this factory.
     *
     * @return #this.vars
     */
    public final int numberOfVariables() {
        return vars.size();
    }


    /**
     * Creates a fresh numeric variable which is strictly true from the boolean pov
     * Int || Double -> x != 0
     */
    public final NumericValue trueVariable(){
        NumericVariable v = new NumericVariable(label++, true);
        vars.put(v.label(), v);
        return v;
    }

    /**
     * Creates a fresh numeric variable which is strictly true from the boolean pov,
     * with the given label.
     * If there exists a variable with such identifier, its value constraint is set to true.
     * @requires id > this.label || this.vars.containsKey(id)
     * @param id label
     * @return variable
     */
    public final NumericValue trueVariable(int id){
        if(id > label)
            label = id + 1;

        NumericVariable v;
        if(vars.containsKey(id)){
            v = vars.get(id);
            v.setConstraint(true);
        }
        else{
            v = new NumericVariable(id, true);
            vars.put(id, v);
        }
        return v;
    }

    // List of the numeric constants { 0, 1 }
    private final List<NumericValue> binaryConst = Arrays.asList(ZERO, ONE);
    /**
     * Encapsulates the given variable together with its Boolean correspondent, now constrained to the {0, 1}-value,
     * @param v the numeric variable specified
     * @return { v(i) , b(i) }, with v(i) in { 0, 1 } and b(i) representing its boolean counterpart.
     */
    public final BinaryValue toBool(NumericVariable v){
        // v in { 0, 1 }
        v.setPotentialValues(binaryConst);

        return new BinaryValue(v, new BooleanVariable(v.label()));
    }

    /**
     * Lifts the Boolean value provided into a binary numeric value.
     * @param b boolean value
     * @return b = TRUE -> ONE,
     *         b = FALSE -> ZERO,
     *         else { b ? 1 : 0 , b }
     */
    public final NumericValue toBinary(BooleanValue b){
        if(b == TRUE)
            return ONE;
        if(b == FALSE)
            return ZERO;

        return new BinaryValue(new ITEGate(b.label(), (BooleanFormula)b, ONE, ZERO), b);
    }

    /**
     * Lifts a boolean value into the numeric realm.
     * @return TRUE  => freshVar != 0,
     *         FALSE => 0,
     *         formula ? freshVar(v.label) != 0 : 0
     */
    public final NumericValue lift(BooleanValue v){
        if(v == TRUE)
            return trueVariable();

        if(v == FALSE)
            return ZERO;

        return new ITEGate(label++, (BooleanFormula)v, trueVariable(v.label()), ZERO);
    }

    /**
     * Drops a numeric value into its respective boolean representation.
     * @param v value
     * @return v != 0 => TRUE,
     *         v = 0 => FALSE,
     *         freshBoolVar(v.label)
     */
    public final BooleanValue drop(NumericValue v){
        if(v instanceof NumericConstant)
            return ((NumericConstant)v).getValue().doubleValue() == 0 ? FALSE : TRUE;

        if(v instanceof NumericVariable){
            NumericVariable nv = (NumericVariable)v;
            if(nv.isTrue()) return TRUE;
            if(nv.isFalse()) return FALSE;
        }

        //return new BooleanVariable(v.label()); TODO ?
        return this.neq(v, ZERO);
    }

    /**
     * Drops a numeric value into its respective boolean representation,
     * in a numeric context.
     * @param v value
     * @return v != 0 ? 1 : 0
     */
    public final NumericValue dropNum(NumericValue v){
        if(v instanceof NumericConstant)
            return ((NumericConstant)v).getValue().doubleValue() == 0 ? ZERO : NumericConstant.ONE;

        if(v instanceof NumericVariable){
            NumericVariable nv = (NumericVariable)v;
            if(nv.isTrue()) return NumericConstant.ONE;
            if(nv.isFalse()) return ZERO;
        }

        return this.ite(this.eq(v, ZERO) , ZERO, NumericConstant.ONE);
    }

    /*
     * @return Minimum of the specified values taking into account that zero means false
     */
    public NumericValue minZero(NumericValue n0, NumericValue n1){
        if(n0 == ZERO)
            return ZERO;

        if(n1 == ZERO)
            return ZERO;

        // n0 != 0 && n1 != 0 ? min(n0, n1) : 0
        return ite(and(neq(n0, ZERO), neq(n1, ZERO)),
                minimum(n0, n1),
                ZERO);
    }

    /*
     * @return Minimum of the specified values taking into account that zero means false
     * Coincides with minZero when the domain is not fuzzy.
     */
    public NumericValue tnorm(NumericValue n0, NumericValue n1){
        return minZero(n0, n1);
    }

    /*
     * Innermost operation of matrix product.
     * @return minZero, when the domain is not fuzzy.
     */
    public NumericValue meet(NumericValue n0, NumericValue n1){
        return minZero(n0, n1);
    }

    /*
     * @return Maximum of the specified values taking into account that zero means false
     */
    public NumericValue maxZero(NumericValue n0, NumericValue n1){
        if(n0 == ZERO)
            return n1;

        if(n1 == ZERO)
            return n0;

        // n0 != 0 && n1 != 0 ? max(n0, n1) : (n0 != 0 ? n0 : n1)
        return ite(and(neq(n0, ZERO), neq(n1, ZERO)),
                maximum(n0, n1),
                ite(neq(n0, ZERO), n0, n1));
    }

    /*
     * @return Maximum of the specified values taking into account that zero means false
     * Coincides with maxZero when the domain is not fuzzy.
     */
    public NumericValue tconorm(NumericValue n0, NumericValue n1){
        return maxZero(n0, n1);
    }

    /*
     * Outermost operation of matrix product.
     * @return maxZero, when the domain is not fuzzy.
     */
    public NumericValue join(NumericValue n0, NumericValue n1){
        return maxZero(n0, n1);
    }

    /**
     * Computes the specified arithmetic operation over two constants.
     * @return v0 op v1
     */
    protected abstract NumericValue arit(NumNary op, NumericConstant v0, NumericConstant v1);

    /**
     * Builds a new numeric value representing the addition of the two numeric values.
     * @return v0 = 0 => v1, v1 = 0 => v0, v0 + v1
     */
    public final NumericValue plus(NumericValue v0, NumericValue v1){
        if(v0 == ZERO)
            return v1;
        if(v1 == ZERO)
            return v0;
        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return arit(PLUS, (NumericConstant)v0, (NumericConstant)v1);

        return new BinaryAritGate(PLUS, label++, v0, v1);
    }

    /**
     * Builds a new numeric value representing the addition of more than two numeric values.
     * @return v0 + v1 + .. + vn
     */
    public final NumericValue plus(NumericValue... inputs){
        return accumulate(NumericAccumulator.treeGate(PLUS, inputs));
    }

    /**
     * Builds a new numeric value representing the difference between the inputs.
     * @return v1 = 0 => v0, v0 - v1 <= 0 ? 0 : v0 - 1
     */
    /*public final NumericValue difference(NumericValue v0, NumericValue v1){ // TODO ?
        if(v1 == ZERO)
            return v0;

        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant){
            NumericValue res = arit(MINUS, (NumericConstant)v0, (NumericConstant)v1);
            return ((NumericConstant) res).getValue().doubleValue() >= 0 ? res : ZERO;
        }

        return new BinaryAritGate(MINUS, label++, v0, v1);
    }*/

    /**
     * Builds a new numeric value representing the difference between the inputs.
     * @return v0 - v1
     */
    public final NumericValue minus(NumericValue v0, NumericValue v1){
        if(v1 == ZERO)
            return v0;

        if(v0 == ZERO)
            return this.negate(v1);

        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return arit(MINUS, (NumericConstant)v0, (NumericConstant)v1);

        return new BinaryAritGate(MINUS, label++, v0, v1);
    }

    /**
     * Builds a new numeric value representing the difference of more than two numeric values.
     * @return v0 - v1 - .. - vn
     */
    public final NumericValue minus(NumericValue... inputs){
        return accumulate(NumericAccumulator.treeGate(MINUS, inputs));
    }

    /**
     * Builds a new numeric value representing the product of the two numeric values.
     * @return v0 = 1 => v1, v1 = 0 => v0, v0 = 0 | v1 = 0 => 0, v0 * v1
     */
    public final NumericValue times(NumericValue v0, NumericValue v1){
        if(v0 instanceof NumericConstant && ((NumericConstant) v0).getValue().doubleValue() == 1)
            return v1;

        if(v1 instanceof NumericConstant && ((NumericConstant) v1).getValue().doubleValue() == 1)
            return v0;

        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return arit(TIMES, (NumericConstant)v0, (NumericConstant)v1);

        return v0 == ZERO || v1 == ZERO ? ZERO : new BinaryAritGate(TIMES, label++, v0, v1);
    }

    /**
     * Builds a new numeric value representing the multiplication of more than two numeric values.
     * @return v0 * v1 * .. * vn
     */
    public final NumericValue times(NumericValue... inputs){
        return accumulate(NumericAccumulator.treeGate(TIMES, inputs));
    }

    /**
     * Builds a new numeric value representing the division of the two numeric values.
     * @return v1 = 1 => v0,
     *              v1 != 0 => v0/v1, 0 (division by zero detection)
     */
    public final NumericValue divide(NumericValue v0, NumericValue v1){
        if(v1 == NumericConstant.ONE)
            return v0;

        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return arit(DIV, (NumericConstant)v0, (NumericConstant)v1);

        return this.ite(this.eq(v1, ZERO), ZERO, new BinaryAritGate(DIV, label++, v0, v1));
    }

    /**
     * Builds a new numeric value representing the modulo between two numeric values.
     * @return v1 = 1 => 0, v0 = v1 => 1, v0 mod v1
     */
    public final NumericValue modulo(NumericValue v0, NumericValue v1){

        if(v1 == NumericConstant.ONE)
            return ZERO;

        if(v0 == v1)
            return NumericConstant.ONE;

        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return arit(MOD, (NumericConstant)v0, (NumericConstant)v1);

        return new BinaryAritGate(MOD, label++, v0, v1);
    }

    /**
     * Constructs the boolean value representing the negation of the numeric value.
     * @return !v
     */
    public final BooleanValue negation(NumericValue v){
        return v.negation();
    }

    /**
     * Builds a new numeric value specifying the minimum value between the two inputs.
     * @return min(v0, v1)
     */
    public final NumericValue minimum(NumericValue v0, NumericValue v1){
        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return ((NumericConstant)v0).getValue().doubleValue() > ((NumericConstant)v1).getValue().doubleValue() ? v1 : v0;

        return new MinGate(label++, v0, v1);
    }

    /**
     * Builds a new numeric value specifying the maximum value between the two inputs.
     * @return max(v0, v1)
     */
    public final NumericValue maximum(NumericValue v0, NumericValue v1){
        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return ((NumericConstant)v0).getValue().doubleValue() < ((NumericConstant)v1).getValue().doubleValue() ? v1 : v0;

        return new MaxGate(label++, v0, v1);
    }

    /**
     * Computes the specified arithmetic operation over two integers.
     * @return v0 op v1
     */
    protected abstract Number arit(NumNary op, Number v0, Number v1);

    /**
     * Converts the given accumulator into an immutable numeric value.
     */
    public final NumericValue accumulate(NumericAccumulator g) {
        boolean isConst = true;
        Number value = 0;
        Iterator<NumericValue> it = g.iterator();
        NumericValue v;

        if(it.hasNext()){
            // Initial value
            v = it.next();
            if(v instanceof NumericConstant)
                value = ((NumericConstant) v).getValue();
            else isConst = false;

            while(it.hasNext() && isConst){
                v = it.next();
                if(v instanceof NumericConstant)
                    value = arit(g.op, value, ((NumericConstant) v).getValue());
                else isConst = false;
            }
        }

        NumericValue acc = ZERO;
        if(isConst)
            acc = constant(value);
        else if(g.size() == 1)
            acc = g.iterator().next();
        else if(g.size() == 2){
            it = g.iterator();
            acc = new BinaryAritGate(g.op, label++, it.next(), it.next());
        }
        else if(g.size() > 1)
            acc = new NaryAritGate(g, label++);

        return acc;
    }

    /**
     * Converts the given accumulator into an immutable boolean value.
     */
    public final BooleanValue accumulate(BooleanAccumulator g) {
        boolean isConst = true;
        boolean value = g.op() == AND;
        Iterator<BooleanValue> it = g.iterator();
        BooleanValue b;

        if(it.hasNext()){
            // Initial value
            b = it.next();
            if(b instanceof BooleanConstant)
                value = ((BooleanConstant) b).booleanValue();
            else isConst = false;

            boolean bv;
            while(it.hasNext() && isConst){
                b = it.next();
                if(b instanceof BooleanConstant) {
                    bv = ((BooleanConstant) b).booleanValue();
                    value = g.op() == AND ? value && bv : value || bv;
                }
                else isConst = false;
            }
        }
        else value = g.op().identity().booleanValue();; // Empty Accumulator

        return isConst ? BooleanConstant.constant(value) : new NaryGate(g, label++, -1);
    }

    /**
     * Builds a boolean formula specifying if the input numeric matrix is semi-negative.
     * @return forall i in [0, m.size[. m[i] <= 0
     *
     * As long as the numeric values are non-negative, the given matrix must be equal
     * to the null matrix.
     */
    public final BooleanValue semiNegative(SparseSequence<NumericValue> m){
        if(isConstant(m)){
            boolean semiNeg = true;
            Iterator<IndexedEntry<NumericValue>> it = m.iterator();
            while(semiNeg && it.hasNext())
                //semiNeg = cmp(LEQ, (NumericConstant)it.next().value(), ZERO) == TRUE;
                semiNeg = cmp(EQ, (NumericConstant)it.next().value(), ZERO) == TRUE;

            return BooleanConstant.constant(semiNeg);
        }

        final BooleanAccumulator g = BooleanAccumulator.treeGate(AND);
        for (IndexedEntry<NumericValue> v : m) {
            //if (g.add(this.lte(v.value(), ZERO)) == FALSE)
            if (g.add(this.eq(v.value(), ZERO)) == FALSE)
                return FALSE;
        }
        return this.accumulate(g);

    }

    /**
     * Adds the ZERO constant to the matrix entries that are zero from the sparse matrix
     * representation perspective, given that they are included in the given indices.
     * @param original matrix
     * @param idx entries indices
     * @return clone of the original matrix with the added constants
     * @throws CloneNotSupportedException
     */
    private SparseSequence<NumericValue> addZeros(SparseSequence<NumericValue> original, IntSet idx) throws CloneNotSupportedException{
        IntIterator it = idx.iterator();
        SparseSequence<NumericValue> res = new TreeSequence<>();
        res.putAll(original.clone());

        while(it.hasNext()){
            int i = it.next();
            if(!original.containsIndex(i))
                res.put(i, ZERO);
        }

        return res;
    }

    /**
     * Checks if the given numeric matrix is composed by constants exclusively.
     */
    private boolean isConstant(SparseSequence<NumericValue> m){
        boolean isConst = true;
        Iterator<NumericValue> it = m.values().iterator();

        while(isConst && it.hasNext())
            isConst = it.next() instanceof NumericConstant;

        return isConst;
    }

    /**
     * Compares two constants wrt the given comparison operator.
     * @return v0 op v1
     */
    protected abstract BooleanConstant cmp(Operator.Comparison op, NumericConstant v0, NumericConstant v1);

    /**
     * Compares two numeric values wrt the given comparison operator.
     * @return v0 op v1
     */
    private BooleanValue cmp(Operator.Comparison op, NumericValue v0, NumericValue v1){
        final List<Operator.Comparison> includes = Arrays.asList(EQ, GEQ, LEQ);

        if(v0 == v1)
            return includes.contains(op) ? TRUE : FALSE;

        return (v0 instanceof NumericConstant && v1 instanceof NumericConstant) ?
                cmp(op, (NumericConstant)v0, (NumericConstant)v1) :
                new CmpGate(op, label++, v0, v1);
    }

    /**
     * Builds the specified boolean gate over the comparison of each element of the matrix.
     * Assumes the method addZeros was called over the input matrices beforehand.
     * @requires mZ = addZeros(m) && nZ = addZeros(n)
     * @return let m_idx = m.indices(), n_idx = n.indices() |
     *      gate = AND => forall i in m_idx + n_idx.
     *      gate = OR  => some   i in m_idx + n_idx.
     *                      i in m_idx & n_idx          => m[i] op n[i],
     *                      i in m_idx && !(i in n_idx) => m[i] op 0,
     *                      !(i in m_idx) && i in n_idx => 0 op n[i]
     */
    private BooleanValue cmpGate(Nary gate, Comparison op, SparseSequence<NumericValue> mZ, SparseSequence<NumericValue> nZ){
        BooleanConstant zero = gate == OR ? TRUE : FALSE;
        final BooleanAccumulator g = BooleanAccumulator.treeGate(gate);
        for (IndexedEntry<NumericValue> vm : mZ) {
            NumericValue vn = nZ.get(vm.index()); // addZeros ensures that it exists
            if (g.add(this.cmp(op, vm.value(), vn)) == zero)
                return zero;
        }
        return this.accumulate(g);
    }

    /**
     * Builds a boolean formula comparing the two matrices wrt the comparison operator specified.
     * =, <=, >= require every pair of elements in each index of both matrices to obey the operator;
     * m > n iff m >= n and some i | m[i] > n[i]
     * m < n iff m <= n and some i | m[i] < n[i]
     */
    public final BooleanValue cmp(Comparison op, SparseSequence<NumericValue> m, SparseSequence<NumericValue> n){
        if(m.size() == 0 && n.size() == 0) return op == GT || op == LT ? FALSE : TRUE;
        try {
            SparseSequence<NumericValue> mZ = addZeros(m, n.indices());
            SparseSequence<NumericValue> nZ = addZeros(n, m.indices());

            BooleanValue ret = TRUE;
            if(op == GT)
                ret = cmpGate(AND, GEQ, mZ, nZ);
            if(op == LT)
                ret = cmpGate(AND, LEQ, mZ, nZ);

            if(ret != FALSE)
                ret = op == GT || op == LT ? this.and(ret, cmpGate(OR, op, mZ, nZ)) : cmpGate(AND, op, mZ, nZ);

            return ret;
        }catch(CloneNotSupportedException e){ e.printStackTrace(); return FALSE; }
    }

    /**
     * Compares the non-zero entries of a constant matrix with a constant wrt the given comparison operator.
     * @requires isConstant(m)
     * @throws ClassCastException if m contains non-constant elements
     */
    private BooleanConstant constantCmp(Operator.Comparison op, SparseSequence<NumericValue> m, NumericConstant n) throws ClassCastException{
        BooleanConstant result = TRUE;
        Iterator<IndexedEntry<NumericValue>> it = m.iterator();

        while(result == TRUE && it.hasNext())
            result = cmp(op, ((NumericConstant)it.next().value()), n);

        return result;
    }

    /**
     * Builds a boolean formula comparing the non-zero cells of a matrix
     * with a numeric value wrt the comparison operator specified.
     * @return forall i in  m.indices() | m[i] op v
     */
    public final BooleanValue cmp(Operator.Comparison op, SparseSequence<NumericValue> m, NumericValue v){
        if(m.size() == 0 && (v instanceof NumericConstant || v instanceof NumericVariable)){
            boolean isZero = (v instanceof NumericConstant &&((NumericConstant) v).getValue().doubleValue() == 0) ||
                    (v instanceof NumericVariable && ((NumericVariable) v).isFalse());
            boolean isTrue = (v instanceof NumericConstant &&((NumericConstant) v).getValue().doubleValue() != 0) ||
                             (v instanceof NumericVariable && ((NumericVariable) v).isTrue());

            if(isZero) {
                if (op == GT || op == LT)
                    return FALSE;
                else return TRUE;
            }
            else if(isTrue){
                if(op == LT || op == LEQ)
                    return TRUE;
                else return FALSE;
            }
            else if((v instanceof NumericConstant &&((NumericConstant) v).getValue().doubleValue() < 0)){
                if(op == GT || op == GEQ)
                    return TRUE;
                else return FALSE;
            }
        }
        if(isConstant(m) && v instanceof NumericConstant) return constantCmp(op, m, (NumericConstant)v);

        final BooleanAccumulator g = BooleanAccumulator.treeGate(AND);
        for (IndexedEntry<NumericValue> vm : m) {
            if (g.add(this.cmp(op, vm.value(), v)) == FALSE)
                return FALSE;
        }
        return this.accumulate(g);
    }

    /**
     * Specifies the boolean formula which states that the given values must be equal.
     * @return v0 = v1
     */
    public final BooleanValue eq(NumericValue v0, NumericValue v1){
        if(v0 == v1)
            return TRUE;

        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return cmp(EQ, (NumericConstant)v0, (NumericConstant)v1);

        return new CmpGate(EQ, label++, v0, v1);
    }

    /**
     * Specifies the boolean formula which states that the given values must be distinct.
     * @return v0 != v1
     */
    public final BooleanValue neq(NumericValue v0, NumericValue v1){
        return this.eq(v0, v1).negation();
    }

    /**
     * Defines the comparison of the value {@code v0} being less than the value {@code v1}.
     * @return v0 < v1
     */
    public final BooleanValue lt(NumericValue v0, NumericValue v1){
        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return cmp(LT, (NumericConstant)v0, (NumericConstant)v1);

        return new CmpGate(LT, label++, v0, v1);
    }

    /**
     * Defines the comparison of the value {@code v0} being less than or equal to the value {@code v1}.
     * @return v0 <= v1
     */
    public final BooleanValue lte(NumericValue v0, NumericValue v1){
        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return cmp(LEQ, (NumericConstant)v0, (NumericConstant)v1);

        return new CmpGate(LEQ, label++, v0, v1);
    }

    /**
     * Defines the comparison of the value {@code v0} being greater than the value {@code v1}.
     * @return v0 > v1
     */
    public final BooleanValue gt(NumericValue v0, NumericValue v1){
        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return cmp(GT, (NumericConstant)v0, (NumericConstant)v1);

        return new CmpGate(GT, label++, v0, v1);
    }

    /**
     * Defines the comparison of the value {@code v0} being greater than or equal to the value {@code v1}.
     * @return v0 >= v1
     */
    public final BooleanValue gte(NumericValue v0, NumericValue v1){
        if(v0 instanceof NumericConstant && v1 instanceof NumericConstant)
            return cmp(GEQ, (NumericConstant)v0, (NumericConstant)v1);

        return new CmpGate(GEQ, label++, v0, v1);
    }

    /**
     * Returns a boolean value whose meaning is the conjunction of the input components.
     * @return v0 = FALSE | v1 = FALSE => FALSE,
     *         v0 = FALSE => v1, v1 = FALSE => v0,
     *         v0 && v1
     */
    public final BooleanValue and(BooleanValue v0, BooleanValue v1){
        if(v0 == FALSE || v1 == FALSE ||
                v0 instanceof NumericConstant && ((NumericConstant)v0).getValue().doubleValue() == 0 ||
                v1 instanceof NumericConstant && ((NumericConstant)v1).getValue().doubleValue() == 0 ||
                v0 instanceof NumericVariable && ((NumericVariable)v0).isFalse() ||
                v1 instanceof NumericVariable && ((NumericVariable)v1).isFalse())
            return FALSE;

        BooleanValue bv0 = v0 instanceof NumericValue ? drop((NumericValue)v0) : v0;
        BooleanValue bv1 = v1 instanceof NumericValue ? drop((NumericValue)v1) : v1;

        if(v0 == TRUE || bv0 == TRUE || v0 instanceof NumericConstant && ((NumericConstant)v0).getValue().doubleValue() != 0)
            return bv1;

        if(v1 == TRUE || bv1 == TRUE || v1 instanceof NumericConstant && ((NumericConstant)v1).getValue().doubleValue() != 0)
            return bv0;

        return new BinaryGate(AND, label++, -1, (BooleanFormula)bv0, (BooleanFormula)bv1);
    }

    /**
     * Returns a conjunction of the boolean values specified.
     *
     * @param inputs  boolean values
     * @return inputs[0] && inputs[1] && ... && inputs[inputs.length - 1]
     */
    public final BooleanValue and(BooleanValue... inputs){
        final BooleanAccumulator g = BooleanAccumulator.treeGate(AND);
        for (BooleanValue b : inputs) {
            if (g.add(b) == FALSE)
                return FALSE;
        }
        return this.accumulate(g);
    }

    /**
     * Returns a conjunction of the negation of the boolean values specified.
     *
     * @param inputs  boolean values
     * @return !inputs[0] && !inputs[1] && ... && !inputs[inputs.length - 1]
     */
    public final BooleanValue nand(BooleanValue... inputs){
        final BooleanAccumulator g = BooleanAccumulator.treeGate(AND);
        for (BooleanValue b : inputs) {
            if (g.add(b.negation()) == FALSE)
                return FALSE;
        }
        return this.accumulate(g);
    }

    /**
     * Returns a boolean value whose meaning is the disjunction of the input components.
     * @return v0 = TRUE | v1 = TRUE => TRUE,
     *         v0 = FALSE => v1, v1 = FALSE => v0,
     *         v0 && v1
     */
    public final BooleanValue or(BooleanValue v0, BooleanValue v1){
        if(v0 == TRUE || v1 == TRUE ||
                v0 instanceof NumericConstant && ((NumericConstant)v0).getValue().doubleValue() != 0 ||
                v1 instanceof NumericConstant && ((NumericConstant)v1).getValue().doubleValue() != 0 ||
                v0 instanceof NumericVariable && ((NumericVariable)v0).isTrue() ||
                v1 instanceof NumericVariable && ((NumericVariable)v1).isTrue())
            return TRUE;

        BooleanValue bv0 = v0 instanceof NumericValue ? drop((NumericValue)v0) : v0;
        BooleanValue bv1 = v1 instanceof NumericValue ? drop((NumericValue)v1) : v1;

        if(v0 == FALSE || bv0 == FALSE || v0 instanceof NumericConstant && ((NumericConstant)v0).getValue().doubleValue() == 0)
            return bv1;

        if(v1 == FALSE || bv1 == FALSE || v1 instanceof NumericConstant && ((NumericConstant)v1).getValue().doubleValue() == 0)
            return bv0;

        return new BinaryGate(OR, label++, -1,(BooleanFormula)bv0, (BooleanFormula)bv1);
    }

    /**
     * Returns a boolean value whose meaning is [[v0]] => [[v1]]
     * @return !v0 | v1
     */
    public final BooleanValue implies(BooleanValue v0, BooleanValue v1){
       return this.or(v0.negation(), v1);
    }

    /**
     * Returns a boolean value whose meaning is [[v0]] <=> [[v1]]
     * @return (v0 => v1) && (v1 => v0)
     */
    public final BooleanValue iff(BooleanValue v0, BooleanValue v1){
        return this.and(this.implies(v0, v1), this.implies(v1, v0));
    }

    /**
     * Returns the negation of the given boolean value.
     * @return !v
     */
    public final BooleanValue not(BooleanValue v) {
        return v.negation();
    }

    /**
     * Specfies the choice between the values {@code v0} and {@code v1} with respect to the given condition.
     * @return condition ? v0 : v1
     */
    public final NumericValue ite(BooleanValue condition, NumericValue v0, NumericValue v1){
        if(v0 == v1)
            return v0;

        if(condition == TRUE)
            return v0;

        if(condition == FALSE)
            return v1;

        return new ITEGate(label++, (BooleanFormula)condition, v0, v1);
    }

    /**
     * Selects the given value in case the condition holds, and the null value otherwise.
     * @return condition ? v : ZERO
     */
    public final NumericValue implies(BooleanValue condition, NumericValue v){
        return this.ite(condition, v, ZERO);
    }

    /**
     * Returns the negation of the numeric constant specified.
     * @return -c
     */
    protected abstract NumericConstant negate(NumericConstant c);

    /**
     * Returns the negation of the numeric value specified.
     * @return -v
     */
    public final NumericValue negate(NumericValue v){
        if(v instanceof NumericConstant)
            return negate((NumericConstant)v);

        return v instanceof UnaryGate && v.op() == NEG ? ((UnaryGate) v).input() : new UnaryGate(NEG, label++, v);
    }

    /**
     * Returns the absolute value of the given constant.
     * @return abs(c)
     */
    protected abstract NumericConstant abs(NumericConstant c);

    /**
     * Applies the absolute value function to the given numeric value.
     * @return abs(v)
     */
    public final NumericValue abs(NumericValue v){
        if(v instanceof NumericConstant)
            return abs((NumericConstant)v);

        return v instanceof UnaryGate && v.op() == ABS ? v : new UnaryGate(ABS, label++, v);
    }

    /**
     * Applies the sign function to the given numeric value.
     * @return sgn(v)
     */
    public final NumericValue signum(NumericValue v){
        if(v instanceof NumericConstant){
            double value = ((NumericConstant) v).getValue().doubleValue();
            NumericValue sgn = ZERO;

            if(value > 0)
                sgn = NumericConstant.ONE;
            else if(value < 0)
                sgn = constant(-1);

            return sgn;
        }

        return v instanceof UnaryGate && v.op() == SGN ? v : new UnaryGate(SGN, label++, v);
    }

    /**
     * Returns a NumericMatrix with the given dimensions and this as the factory for
     * its non-FALSE components. The returned matrix can store any value from at all
     * indices between 0, inclusive, and d.capacity(), exclusive.
     */
    public NumericMatrix matrix(Dimensions d) {
        if (d == null)
            throw new NullPointerException();
        return new NumericMatrix(d, this);
    }

    /**
     * Returns a BinaryMatrix with the given dimensions and this as the factory for
     * its non-FALSE components. The returned matrix can store any value from at all
     * indices between 0, inclusive, and d.capacity(), exclusive.
     */
    public NumericMatrix booleanMatrix(Dimensions d) {
        if (d == null)
            throw new NullPointerException();
        return new BinaryMatrix(d, this);
    }

    /**
     * @throws IllegalArgumentException indices !in [0..d.capacity())
     */
    private static void validate(IntSet indices, Dimensions d) {
        if (!indices.isEmpty()) {
            if (!d.validate(indices.min()) || !d.validate(indices.max()))
                throw new IllegalArgumentException();
        }
    }

    /**
     * Returns a NumericMatrix <tt>m</tt> with the given dimensions, this as its
     * factory, and the indices from the set <tt>trueIndices</tt> initialized to
     * the numeric constant 1.
     */
    public final NumericMatrix matrix(Dimensions d, IntSet allIndices, IntSet trueIndices) {
        assert allIndices.size() >= trueIndices.size(); // sanity check
        validate(allIndices, d);
        validate(trueIndices, d);
        try {
            return new NumericMatrix(d, this, allIndices, trueIndices.clone());
        } catch (CloneNotSupportedException e) {
            throw new IllegalArgumentException();
        }

    }

    /**
     * Returns a Boolean matrix <tt>m</tt> with the given dimensions, this as its
     * factory, and the indices from the set <tt>trueIndices</tt> initialized to true.
     */
    public final NumericMatrix booleanMatrix(Dimensions d, IntSet allIndices, IntSet trueIndices) {
        assert allIndices.size() >= trueIndices.size(); // sanity check
        validate(allIndices, d);
        validate(trueIndices, d);
        try {
            return new BinaryMatrix(d, this, allIndices, trueIndices.clone());
        } catch (CloneNotSupportedException e) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Returns a NumericMatrix <tt>m</tt> with the given dimensions, this as its
     * factory, and the indices from the set <tt>trueIndices</tt> initialized to
     * the same fresh free variable.
     */
    public final NumericMatrix constant(Dimensions d, IntSet indices, NumericValue v){
        validate(indices, d);
        return new NumericMatrix(d, this, indices, v);
    }

    /**
     * Returns the maximum label of a {@link NumericVariable primary variable} produced.
     */
    public final int maxVariable() {
        return maxPrimaryVariable;//label;
    }

    /**
     * Numeric factory for integer values.
     */
    private static final class IntegerFactory extends NumericFactory{
        IntegerFactory() { super(); }

        /**
         * {@inheritDoc}
         */
        @Override
        public QuantitativeType factoryType() {
            return QuantitativeType.INTEGER;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public NumericConstant constant(Number value) {
            return constant(value.intValue());
        }

        /**
         * {@inheritDoc}
         * Assumes that v0 and v1 represent integer values,
         * and thus the value will be rounded even if they are not.
         */
        @Override
        protected NumericValue arit(NumNary op, NumericConstant v0, NumericConstant v1) {
            int result = v0.getValue().intValue();
            int v = v1.getValue().intValue();

            if(op == PLUS)
                result += v;
            else if(op == MINUS)
                result -= v;
            else if(op == TIMES)
                result *= v;
            else if(op == DIV) {
                if(v == 0)
                    throw new IllegalArgumentException("Cannot divide by zero: " + result + " / 0");
                result = result / v;
            }
            else if(op == MOD) {
                if(v == 0)
                    throw new IllegalArgumentException("Cannot divide by zero: " + result + "% 0");
                result = result % v;
            }

            return constant(result);
        }

        /**
         * {@inheritDoc}
         * Assumes v0 : Integer & v1 : Integer, values will be rounded otherwise.
         */
        @Override
        protected Number arit(NumNary op, Number v0, Number v1) {
            int result = v0.intValue();
            int intV1 = v1.intValue();

            if(op == PLUS)
                result += intV1;
            else if(op == MINUS)
                result -= intV1;
            else if(op == TIMES)
                result *= intV1;
            else if(op == DIV)
                result = intV1 != 0 ? result/intV1 : 0;
            else if(op == MOD)
                result = intV1 != 0 ? result%intV1 : 0;

            return result;
        }

        /**
         * {@inheritDoc}
         * Assumes v0.value : Integer & v1.value : Integer
         */
        @Override
        protected BooleanConstant cmp(Comparison op, NumericConstant v0, NumericConstant v1) {
            boolean result = true;

            int x = v0.getValue().intValue();
            int y = v1.getValue().intValue();

            if(op == EQ)
                result = x == y;
            else if(op == GT)
                result = x > y;
            else if(op == LT)
                result = x < y;
            else if(op == GEQ)
                result = x >= y;
            else if(op == LEQ)
                result = x <= y;

            return BooleanConstant.constant(result);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected NumericConstant negate(NumericConstant c) {
            return constant(-c.getValue().intValue());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected NumericConstant abs(NumericConstant c) {
            int x = c.getValue().intValue();
            return x >= 0 ? c : constant(-x);
        }
    }

    /**
     * Numeric factory for real values.
     */
    private static final class FuzzyFactory extends DoubleFactory {

        private final BiFunction<NumericValue, NumericValue, NumericValue> t;
        private final BiFunction<NumericValue, NumericValue, NumericValue> s;

        FuzzyFactory() {
            super();
            this.t = super :: tnorm;
            this.s = super :: tconorm;
        }

        FuzzyFactory(Tnorm T){
            super();
            switch (T){
                case Lukasiewicz:
                    // max(0, a + b - 1)
                    this.t = (a, b) -> maximum(ZERO, minus(plus(a, b), ONE));
                    // min(a + b, 1)
                    this.s = (a, b) -> minimum(plus(a, b), ONE);
                    break;
                case Product:
                    // a * b
                    this.t = this :: times;
                    // a + b - a * b
                    this.s = (a, b) -> minus(plus(a, b), times(a, b));
                    break;
                case Drastic:
                    // b = 1 => a, a = 1 => b, 0
                    this.t = (a, b) -> ite(eq(b, ONE), a,
                                            ite(eq(a, ONE), b, ZERO));
                    // b = 0 => a, a = 0 => b, 0
                    this.s = (a, b) -> ite(eq(b, ZERO), a,
                                            ite(eq(a, ZERO), b, ZERO));
                    break;
                case Einstein:
                    // a * b / (1 + (1 - a) * (1 - b))
                    this.t = (a, b) -> divide(
                            times(a, b),
                            plus(ONE, times(minus(ONE, a), minus(ONE, b)))
                    );
                    // a + b / (1 + a * b)
                    this.s = (a, b) -> divide(
                            plus(a, b),
                            plus(ONE, times(a, b))
                    );
                    break;
                case ADD_MIN:
                    // min
                    this.t = super :: tnorm;
                    // bounded addition: min(a + b, 1)
                    this.s = (a, b) -> minimum(plus(a, b), ONE);
                    break;
                case MAX_PRODUCT:
                    // a * b
                    this.t = this :: times;
                    // max
                    this.s = super :: tconorm;
                    break;
                default: //case Godelian:
                    this.t = super :: tnorm;
                    this.s = super :: tconorm;
                    break;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public QuantitativeType factoryType() {
            return QuantitativeType.FUZZY;
        }

        @Override
        public NumericValue tnorm(NumericValue n0, NumericValue n1) {
            return t.apply(n0, n1);
        }

        @Override
        public NumericValue tconorm(NumericValue n0, NumericValue n1) {
            return s.apply(n0, n1);
        }

        @Override
        public NumericValue meet(NumericValue n0, NumericValue n1) {
            return t.apply(n0, n1);
        }

        @Override
        public NumericValue join(NumericValue n0, NumericValue n1) {
            return s.apply(n0, n1);
        }
    }

    /**
     * Numeric factory for real values.
     */
    private static abstract class DoubleFactory extends NumericFactory{
        DoubleFactory() { super(); }

        /**
         * Returns the NumericConstant representing the given value.
         */
        public final NumericConstant constant(double value){
            if(value == 0)
                return ZERO;

            if(value == 1)
                return NumericConstant.ONE;

            return new NumericConstant(label++, round(value));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public NumericConstant constant(Number value) {
            return constant(round(value.doubleValue()));
        }

        /**
         * {@inheritDoc}
         * Assumes that v0 and v1 represent double values,
         * and thus the value will be rounded even if they are not.
         */
        @Override
        protected NumericValue arit(NumNary op, NumericConstant v0, NumericConstant v1) {
            double result = v0.getValue().doubleValue();
            double v = v1.getValue().doubleValue();

            if(op == PLUS) {
                result += v;
            }
            else if(op == MINUS) {
                result -= v;
            }
            else if(op == TIMES)
                result *= v;
            else if(op == DIV) {
                if(v == 0)
                    throw new IllegalArgumentException("Cannot divide by zero: " + result + " / 0");
                result = result / v;
            }
            else if(op == MOD) {
                if(v == 0)
                    throw new IllegalArgumentException("Cannot divide by zero: " + result + "% 0");
                result = result % v;
            }

            return constant(round(result));
        }

        /**
         * {@inheritDoc}
         * Assumes v0 : Double & v1 : Double, values will be rounded otherwise.
         */
        @Override
        protected Number arit(NumNary op, Number v0, Number v1) {
            double result = v0.doubleValue();
            double intV1 = v1.doubleValue();

            if(op == PLUS) {
                result += intV1;
            }
            else if(op == MINUS) {
                result -= intV1;
            }
            else if(op == TIMES)
                result *= intV1;
            else if(op == DIV)
                result = intV1 != 0 ? result/intV1 : 0;
            else if(op == MOD)
                result = intV1 != 0 ? result%intV1 : 0;

            return round(result);
        }

        /**
         * {@inheritDoc}
         * Assumes v0.value : Double & v1.value : Double
         */
        @Override
        protected BooleanConstant cmp(Comparison op, NumericConstant v0, NumericConstant v1) {
            boolean result = true;

            double x = v0.getValue().doubleValue();
            double y = v1.getValue().doubleValue();

            if(op == EQ)
                result = x == y;
            else if(op == GT)
                result = x > y;
            else if(op == LT)
                result = x < y;
            else if(op == GEQ)
                result = x >= y;
            else if(op == LEQ)
                result = x <= y;

            return BooleanConstant.constant(result);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected NumericConstant negate(NumericConstant c) {
            return constant(-c.getValue().doubleValue());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected NumericConstant abs(NumericConstant c) {
            double x = c.getValue().doubleValue();
            return x >= 0 ? c : constant(-x);
        }

        // Number of decimal places considered
        public final int DECIMAL_PLACES = 16;

        // Helper method to round the value to the decimal places considered.
        private double round(double value) {
            //if (DECIMAL_PLACES < 0) throw new IllegalArgumentException();
            BigDecimal bd = BigDecimal.valueOf(value);
            bd = bd.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
    }
}
