package edu.mit.csail.sdg.translator;

import edu.mit.csail.sdg.alloy4.*;
import edu.mit.csail.sdg.ast.*;
import edu.mit.csail.sdg.ast.Decl;
import kodkod.ast.*;
import kodkod.ast.operator.ExprOperator;
import kodkod.ast.operator.IntCastOperator;
import kodkod.ast.operator.Multiplicity;
import kodkod.ast.visitor.ReturnVisitor;
import kodkod.engine.CapacityExceededException;
import kodkod.engine.fol2sat.HigherOrderDeclException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Quantitative extension of {@link TranslateAlloyToKodkod}.
 * Handles the augmented Alloy language to support quantitative analysis.
 */
public final class TranslateQTAlloyToKodkod extends TranslateAlloyToKodkod implements ReturnVisitor<Expression, Formula, Decls, IntExpression> {

    /**
     * {@link #TranslateAlloyToKodkod(A4Reporter, A4Options, Iterable<Sig>, Command)}
     */
    private TranslateQTAlloyToKodkod(A4Reporter rep, A4Options opt, Iterable<Sig> sigs, Command cmd) throws Err {
        super(rep == null ? new A4QtReporter() : rep, opt, sigs, cmd);
    }

    /**
     * {@link #TranslateAlloyToKodkod(int, int, Map, Map)}
     */
    private TranslateQTAlloyToKodkod(int bitwidth, int unrolls, Map<Expr,Expression> a2k, Map<String,Expression> s2k) throws Err {
        super(bitwidth, unrolls, a2k, s2k);
    }

    /**
     * {@link TranslateAlloyToKodkod#makeFacts(Expr)}
     * - where a field R : A -> B -> .. -> Z is declared as
     * drop (A <: R) in B -> .. -> Z
     * instead of
     * A <: R in B -> .. -> Z
     * due to A, B, .. and Z being sets of atoms (their weight is 1 if present, and 0 otherwise),
     * without drop R would be declared as an ordinary relation.
     *
     * In general, to handle the multiplicity declarations R : A m -> n B w -> z C x -> ...
     * first, the Kodkod constraints are generated based on 'drop' as described previously,
     * however, there is the need to remove 'drop' for the multiplicities ONE and LONE,
     * since '(L)ONE (drop ...)' will be true if a single tuple has weight higher than 1 and thus,
     * the intended constraint wouldn't be imposed.
     * Furthermore the necessity of taking advantage of 'drop' that arose for the SETOF multiplicity
     * isn't there anymore for those cases.
     * For that {@link TranslateQTAlloyToKodkod} implements {@link ReturnVisitor}, removing 'drop'
     * from the sub-nodes of {@link MultiplicityFormula} of multiplicity ONE or LONE.
     *
     * - (l)one sig S{} constraints need to be explicitly enforced,
     * to avoid a singular atom with free weight (>1).
     */
    private void makeFacts(Expr facts) throws Err {
        rep.debug("Generating facts...\n");
        // convert into a form that hopefully gives better unsat core
        facts = (new ConvToConjunction()).visitThis(facts);

        // add the field facts and appended facts
        for (Sig s : frame.getAllReachableSigs()) {

            // one s
            if(s.isOne != null){
                frame.addFormula(a2k(s).one(), (Pos) null);
            } //lone s
            else if(s.isLone != null){
                frame.addFormula(a2k(s).lone(), (Pos) null);
            }

            for (Decl d : s.getFieldDecls()) {
                k2pos_enabled = false;
                for (ExprHasName n : d.names) {
                    Sig.Field f = (Sig.Field) n;
                    Expr form = s.decl.get().join(f.drop()).in(d.expr);
                    form = s.isOne == null ? form.forAll(s.decl) : ExprLet.make(null, (ExprVar) (s.decl.get()), s, form);
                    frame.addFormula(cform(form).accept(this), f); //Remove drop where applicable
                    // Given the above, we can be sure that every column is
                    // well-bounded (except possibly the first column).
                    // Thus, we need to add a bound that the first column is a
                    // subset of s.
                    if (s.isOne == null) {
                        Expression sr = a2k(s), fr = a2k(f).drop();
                        for (int i = f.type().arity(); i > 1; i--)
                            fr = fr.join(Expression.UNIV);
                        frame.addFormula(fr.in(sr), f);
                    }
                }
                if (s.isOne == null && d.disjoint2 != null)
                    for (ExprHasName f : d.names) {
                        Decl that = s.oneOf("that");
                        Expr formula = s.decl.get().equal(that.get()).not().implies(s.decl.get().join(f).intersect(that.get().join(f)).no());
                        frame.addFormula(cform(formula.forAll(that).forAll(s.decl)), d.disjoint2);
                    }
                if (d.names.size() > 1 && d.disjoint != null) {
                    frame.addFormula(cform(ExprList.makeDISJOINT(d.disjoint, null, d.names)), d.disjoint);
                }
            }
            k2pos_enabled = true;
            for (Expr f : s.getFacts()) {
                Expr form = s.isOne == null ? f.forAll(s.decl) : ExprLet.make(null, (ExprVar) (s.decl.get()), s, f);
                frame.addFormula(cform(form), f);
            }

            /**
             * ---------------------------------------------------------------------------------------------------------
             * Sigs, Multisigs, Subsigs and Subset Sigs
             * ---------------------------------------------------------------------------------------------------------
             */

            // Force children of (non-multi) sigs declared as quantitative to be Boolean-valued.
            /*if(s instanceof Sig.PrimSig && s.isInt == null && s != Sig.UNIV)
                for(Sig.PrimSig subS : ((Sig.PrimSig)s).children())
                    if(subS.isInt != null) {
                        Expression sub = a2k(subS);
                        // sub = drop sub
                        frame.addFormula(sub.eq(sub.drop()), subS.equal(subS.drop()));
                    }*/

            // Handle the weights of the Remainder Relation of a Quantitative Sig
            if(s.isInt != null && s.isAbstract == null && s instanceof Sig.PrimSig) {
                Expr rem = s;
                // Sig remainder
                for(Sig.PrimSig c : ((Sig.PrimSig) s).children())
                    rem = rem.minus(c);

                if(rem != s)
                    for(Sig.PrimSig c : ((Sig.PrimSig) s).children()){
                        // no Remainder & Children
                        Expr f = rem.intersect(c).no();
                        frame.addFormula(cform(f), f);
                    }
            }

            // Enforce the weights of Subset Sigs to the parent signatures
            /*if(s instanceof Sig.SubsetSig)
                for(Sig p : ((Sig.SubsetSig) s).parents)
                    if(p.isInt != null && p instanceof Sig.PrimSig){
                        Decl d = s.oneOf("x");
                        ExprHasName x = d.get();
                        // all x : sub | x = x <: parent
                        Expr f = x.equal(x.domain(p)).forAll(d);
                        frame.addFormula(cform(f), f);
                    }*/

            /**
             * ---------------------------------------------------------------------------------------------------------
             * Quantity value range depending on domain TODO
             * (Currently assumes Fuzzy only [0, 1])
             * ---------------------------------------------------------------------------------------------------------
             */
            Expression type, rel, none;
            if(s.isInt != null) {
                type = super.cset(s.type().toExpr());
                rel = cset(s);
                frame.addFormula(rel.gte(Expression.NONE).and(rel.lte(type)), (Pos)null);
            }
            for(Sig.Field field : s.getFields()){
                type = super.cset(field.type().toExpr());
                rel = cset(field);
                none = Expression.NONE;
                for (int i = field.type().arity(); i > 1; i--)
                    none = none.product(Expression.NONE);
                frame.addFormula(rel.gte(none).and(rel.lte(type)), (Pos)null);
            }
        }
        k2pos_enabled = true;
        recursiveAddFormula(facts);
    }

    /**
     * Execute one command in a quantitative context.
     */
    public static A4Solution execute_command(A4QtReporter rep, Iterable<Sig> sigs, Command cmd, A4Options opt, String solverBinary) throws Err {
        TranslateQTAlloyToKodkod tr = null;
        try {
            tr = new TranslateQTAlloyToKodkod(rep, opt, sigs, cmd);
            tr.makeFacts(cmd.formula);
            return tr.frame.solve(rep, cmd, new Simplifier(), null, solverBinary);
        } catch (UnsatisfiedLinkError ex) {
            throw new ErrorFatal("The required JNI library cannot be found: " + ex.toString().trim(), ex);
        } catch (CapacityExceededException ex) {
            throw rethrow(ex);
        } catch (HigherOrderDeclException ex) {
            Pos p = tr != null ? tr.frame.kv2typepos(ex.decl().variable()).b : Pos.UNKNOWN;
            throw new ErrorType(p, "Analysis cannot be performed since it requires higher-order quantification that could not be skolemized.");
        } catch (Throwable ex) {
            if (ex instanceof Err)
                throw (Err) ex;
            else
                throw new ErrorFatal("Unknown exception occurred: " + ex, ex);
        }
    }

    /**
     * {@link TranslateAlloyToKodkod#alloy2kodkod(A4Solution, Expr)} for quantitative settings.
     */
    public static Object alloy2kodkod(A4Solution sol, Expr expr) throws Err {
        if (expr.ambiguous && !expr.errors.isEmpty())
            expr = expr.resolve(expr.type(), null);
        if (!expr.errors.isEmpty())
            throw expr.errors.pick();
        TranslateAlloyToKodkod tr = new TranslateQTAlloyToKodkod(sol.getBitwidth(), sol.unrolls(), sol.a2k(), sol.s2k());
        Object ans;
        try {
            ans = tr.visitThis(expr);
        } catch (UnsatisfiedLinkError ex) {
            throw new ErrorFatal("The required JNI library cannot be found: " + ex.toString().trim());
        } catch (CapacityExceededException ex) {
            throw rethrow(ex);
        } catch (HigherOrderDeclException ex) {
            throw new ErrorType("Analysis cannot be performed since it requires higher-order quantification that could not be skolemized.");
        } catch (Throwable ex) {
            if (ex instanceof Err)
                throw (Err) ex;
            throw new ErrorFatal("Unknown exception occurred: " + ex, ex);
        }
        if ((ans instanceof IntExpression) || (ans instanceof Formula) || (ans instanceof Expression))
            return ans;
        throw new ErrorFatal("Unknown internal error encountered in the quantitative evaluator.");
    }

    /** {@inheritDoc} */
    @Override
    public Object visit(ExprUnary x) throws Err {
        switch(x.op){
            case DROP:
                return super.cset(x.sub).drop();

                /*Expression e;
                Iterator<Type.ProductType> it = x.sub.type().iterator();

                kodkod.ast.Decl d;
                Decls ds;
                Expression fdrop = null, subdrop;
                Expression zero;

                while(it.hasNext()) {
                    e = cset(x.sub);
                    ds = null;
                    Type.ProductType t = it.next();
                    int N = t.arity();

                    for (int i = 0; i < N; i++) {
                        Variable a = Variable.unary("a(" + t + ")" + i);

                        d = a.oneOf(cset(t.get(i)));
                        if (ds == null)
                            ds = d;
                        else ds = ds.and(d);

                        if (i + 1 == N)
                            e = e.domain(a);
                        else {
                            e = a.join(e);
                        }
                    }

                    zero = IntConstant.constant(0).toExpression().scalar(cset(t.get(N - 1)));
                    subdrop = e.eq(zero).not().comprehension(ds);
                    fdrop = (fdrop == null) ? subdrop : fdrop.union(subdrop);
                }

                if(fdrop == null)
                    throw new ErrorType("The unary expression cannot be subject to DROP: " + x);

                return fdrop;*/
            case RCLOSURE:
                return super.cset(x.sub).reflexiveClosure();
            case CARDINALITY:
                return super.cset(x.sub).count().cast(IntCastOperator.INTCAST).hadamardProduct(super.cset(x.sub.type().toExpr()));
            default:
                return super.visit(x);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Object visit(ExprBinary x) throws Err {
        Expr a = x.left, b = x.right;
        Expression s, s2;
        IntExpression i;
        Formula f;
        boolean isIntExpr;
        switch (x.op) {
            case JOIN:
            case MULTIJOIN:
                a = a.deNOP();
                s = cset(a);
                s2 = cset(b);
                return x.op == ExprBinary.Op.JOIN ? s.join(s2) : s.multijoin(s2);
            case LT :
                s = cset(a);
                f = s.lt(cset(b));
                return k2pos(f, x);
            case LTE :
                s = cset(a);
                f = s.lte(cset(b));
                return k2pos(f, x);
            case GT :
                s = cset(a);
                f = s.gt(cset(b));
                return k2pos(f, x);
            case GTE :
                s = cset(a);
                f = s.gte(cset(b));
                return k2pos(f, x);
            case NOT_LT :
                s = cset(a);
                f = s.lt(cset(b)).not();
                return k2pos(f, x);
            case NOT_LTE :
                s = cset(a);
                f = s.lte(cset(b)).not();
                return k2pos(f, x);
            case NOT_GT :
                s = cset(a);
                f = s.gt(cset(b)).not();
                return k2pos(f, x);
            case NOT_GTE :
                s = cset(a);
                f = s.gte(cset(b)).not();
                return k2pos(f, x);
            case DOMAIN :
                s = cset(a);
                s2 = cset(b);
                return s2.domain(s);
                /*s = cset(b);
                s2 = cset(b);
                for (int j = s.arity(); j > 1; j--)
                    s2 = s2.join(Expression.UNIV);
                s2 = s2.hadamardProduct(cset(a));
                for (int j = s.arity(); j > 1; j--)
                    s2 = s2.product(Expression.UNIV);
                return s2.intersection(s);*/
            case RANGE :
                s = cset(a);
                s2 = cset(b);
                return s.range(s2);
                /*s = cset(a);
                s2 = cset(a);
                for (int j = s2.arity(); j > 1; j--)
                    s = Expression.UNIV.join(s);
                s = s.hadamardProduct(cset(b));
                for (int j = s2.arity(); j > 1; j--)
                    s = Expression.UNIV.product(s);
                return s.intersection(s2);*/
            case MUL :
                isIntExpr = a.type().is_int() && b.type().is_int();
                // Multiplication between two numbers
                if(isIntExpr) {
                    i = cint(a);
                    return i.multiply(cint(b));
                }
                // Hadamard product
                s = cset(a);
                s2 = cset(b);
                return s.hadamardProduct(s2);
            case DIV :
                isIntExpr = a.type().is_int() && b.type().is_int();
                // Multiplication between two numbers
                if(isIntExpr) {
                    i = cint(a);
                    return i.divide(cint(b));
                }
                // Hadamard division
                s = cset(a);
                s2 = cset(b);
                return s.hadamardDivision(s2);
            case IPLUS:
                return cset(a).addition(cset(b));
            case IMINUS:
                return cset(a).minus(cset(b));
            case SCALAR:
                return cset(a).scalar(cset(b)); // TODO
            case ALPHA:
                return cset(a).alpha(cset(b)); // TODO
        }
        return super.visit(x);
    }

    /**
     * -----------------------------------------------------------------------------------------------------------------
     * Implementation of ReturnVisitor to remove DROP from the sub-nodes of MultiplicityFormulas,
     * whose multiplicity is ONE or LONE.
     * -----------------------------------------------------------------------------------------------------------------
     */
    // Flag indicating if the DROP operator is to be removed at a given point in time.
    private boolean rmDROP = false;

    /**
     * Does nothing.
     * @return decls
     */
    @Override
    public Decls visit(Decls decls) {
        return decls;
    }

    /**
     * Does nothing.
     * @return decl
     */
    @Override
    public Decls visit(kodkod.ast.Decl decl) {
        return decl;
    }

    /**
     * Does nothing.
     * @return relation
     */
    @Override
    public Expression visit(Relation relation) {
        return relation;
    }

    /**
     * Does nothing.
     * @return variable
     */
    @Override
    public Expression visit(Variable variable) {
        return variable;
    }

    /**
     * Does nothing.
     * @return constExpr
     */
    @Override
    public Expression visit(ConstantExpression constExpr) {
        return constExpr;
    }

    /**
     * If rmDROP is true, then DROP is removed from the given UnaryExpression and its sub-nodes,
     * then the flag is flipped, as the removal was complete;
     * else traverses its sub-nodes.
     */
    @Override
    public Expression visit(UnaryExpression unaryExpr) {
        Expression u;
        if(rmDROP && unaryExpr.op() == ExprOperator.DROP){
            u = unaryExpr.expression().accept(this);
            rmDROP = false;
        }else u = unaryExpr.expression().accept(this).apply(unaryExpr.op());
        return u;
    }

    /**
     * Checks its sub-nodes.
     */
    @Override
    public Expression visit(BinaryExpression binExpr) {
        Expression left = binExpr.left().accept(this);
        Expression right = binExpr.right().accept(this);
        return left.compose(binExpr.op(), right);
    }

    /**
     * Checks its sub-nodes.
     */
    @Override
    public Expression visit(NaryExpression expr) {
        List<Expression> exps = new ArrayList<>();
        expr.iterator().forEachRemaining(e -> exps.add(e.accept(this)));
        return NaryExpression.compose(expr.op(), exps);
    }

    /**
     * Does nothing.
     * @return comprehension
     */
    @Override
    public Expression visit(Comprehension comprehension) {
        return comprehension;
    }

    /**
     * Checks its sub-nodes.
     */
    @Override
    public Expression visit(IfExpression ifExpr) {
        Formula c = ifExpr.condition().accept(this);
        Expression t = ifExpr.thenExpr().accept(this);
        Expression e = ifExpr.elseExpr().accept(this);
        return c.thenElse(t, e);
    }

    /**
     * Does nothing.
     * @return project
     */
    @Override
    public Expression visit(ProjectExpression project) {
        return project;
    }

    /**
     * Does nothing.
     * @return castExpr
     */
    @Override
    public Expression visit(IntToExprCast castExpr) {
        return castExpr;
    }

    /**
     * Does nothing.
     * @return intConst
     */
    @Override
    public IntExpression visit(IntConstant intConst) {
        return intConst;
    }

    /**
     * Does nothing.
     * @return intExpr
     */
    @Override
    public IntExpression visit(IfIntExpression intExpr) {
        return intExpr;
    }

    /**
     * Does nothing.
     * @return intExpr
     */
    @Override
    public IntExpression visit(ExprToIntCast intExpr) {
        return intExpr;
    }

    /**
     * Does nothing.
     * @return intExpr
     */
    @Override
    public IntExpression visit(NaryIntExpression intExpr) {
        return intExpr;
    }

    /**
     * Does nothing.
     * @return intExpr
     */
    @Override
    public IntExpression visit(BinaryIntExpression intExpr) {
        return intExpr;
    }

    /**
     * Does nothing.
     * @return intExpr
     */
    @Override
    public IntExpression visit(UnaryIntExpression intExpr) {
        return intExpr;
    }

    /**
     * Does nothing.
     * @return intExpr
     */
    @Override
    public IntExpression visit(SumExpression intExpr) {
        return intExpr;
    }

    /**
     * Does nothing.
     * @return intComp
     */
    @Override
    public Formula visit(IntComparisonFormula intComp) {
        return intComp;
    }

    /**
     * Checks its sub-nodes.
     */
    @Override
    public Formula visit(QuantifiedFormula quantFormula) {
        Formula body = quantFormula.body().accept(this);
        Formula domain = quantFormula.domain().accept(this);
        return body.quantify(quantFormula.quantifier(), quantFormula.decls(), domain);
    }

    /**
     * Checks its sub-nodes.
     */
    @Override
    public Formula visit(NaryFormula formula) {
        List<Formula> fs = new ArrayList<>();
        formula.iterator().forEachRemaining(f -> fs.add(f.accept(this)));
        return Formula.compose(formula.op(), fs);
    }

    /**
     * Checks its sub-nodes.
     */
    @Override
    public Formula visit(BinaryFormula binFormula) {
        Formula left = binFormula.left().accept(this);
        Formula right = binFormula.right().accept(this);
        return left.compose(binFormula.op(), right);
    }

    /**
     * Checks its sub-nodes.
     */
    @Override
    public Formula visit(NotFormula not) {
        return not.formula().accept(this).not();
    }

    /**
     * Does nothing.
     * @return constant
     */
    @Override
    public Formula visit(ConstantFormula constant) {
        return constant;
    }

    /**
     * Checks its sub-nodes.
     */
    @Override
    public Formula visit(ComparisonFormula compFormula) {
        Expression left = compFormula.left().accept(this);
        Expression right = compFormula.right().accept(this);
        return left.compare(compFormula.op(), right);
    }

    /**
     * If the multiplicity of this formula is ONE or LONE, sets the flag to true,
     * then traverses its sub-nodes.
     */
    @Override
    public Formula visit(MultiplicityFormula multFormula) {
        Multiplicity m = multFormula.multiplicity();
        if(m == Multiplicity.ONE || m == Multiplicity.LONE)
            rmDROP = true;
        return multFormula.expression().accept(this).apply(m);
    }

    /**
     * Does nothing.
     * @return predicate
     */
    @Override
    public Formula visit(RelationPredicate predicate) {
        return predicate;
    }

    /**
     * Does nothing.
     * @return fixFormula
     */
    @Override
    public Formula visit(FixFormula fixFormula) {
        return fixFormula;
    }
}
