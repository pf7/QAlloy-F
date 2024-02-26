package kodkod.engine.fol2num;

import java.util.*;

import kodkod.ast.*;
import kodkod.ast.operator.*;
import kodkod.ast.visitor.ReturnVisitor;
import kodkod.engine.bool.*;
import kodkod.engine.fol2sat.Environment;
import kodkod.engine.fol2sat.HigherOrderDeclException;
import kodkod.engine.fol2sat.UnboundLeafException;
import kodkod.engine.num.NumericConstant;
import kodkod.engine.num.NumericFactory;
import kodkod.engine.num.NumericMatrix;
import kodkod.engine.num.NumericValue;
import kodkod.util.ints.IndexedEntry;
import kodkod.util.ints.Ints;
import kodkod.util.nodes.AnnotatedNode;
import kodkod.util.nodes.Nodes;

import static kodkod.engine.num.NumericConstant.ONE;
import static kodkod.engine.num.NumericConstant.ZERO;

/**
 * Translates an annotated node to the numeric matrix representation.
 *
 * @specfield node: AnnotatedNode<? extends Node> // node to translate
 * @specfield interpreter: LeafInterpreter // the interpreter used for
 *            translation
 * @specfield env: Environment<NumericMatrix> // current environment
 */
public class FOL2NumTranslator implements ReturnVisitor<NumericMatrix, BooleanValue, Object, NumericMatrix> {

    private Environment<NumericMatrix, Expression> env;
    private final FOL2NumCache cache;
    private final LeafInterpreter                   interpreter;
    private final NumericFactory                    factory;
    private final Map<LeafExpression,NumericMatrix> leafCache;
    // Fixed Point Equations to properly determine (reflexive) transitive closure
    private final List<BooleanValue>                fixedPointEq;

    protected FOL2NumTranslator(FOL2NumCache cache, LeafInterpreter interpreter) {
        this.cache = cache;
        this.env = Environment.empty();
        this.interpreter = interpreter;
        this.factory = interpreter.factory();
        this.leafCache = new HashMap<LeafExpression,NumericMatrix>(64);
        this.fixedPointEq = new ArrayList<>();
    }

    /**
     * Retrieves the cached translation for the given node, if any. Otherwise
     * returns null.
     *
     * @return the cached translation for the given node, if any. Otherwise returns
     *         null.
     */
    @SuppressWarnings("unchecked" )
    final <T> T lookup(Node node) {
        return (T) cache.lookup(node, env);
    }

    /**
     * The translation is cached, if necessary, and returned.
     *
     * @return translation
     * @ensures the translation may be cached
     */
    final <T> T cache(Node node, T translation) {
        return cache.cache(node, translation, env);
    }

    /**
     * Translates the given annotated formula or expression into a boolean formula,
     * numeric matrix or numeric value, using the provided interpreter.
     **/
    @SuppressWarnings("unchecked" )
    public static <T> T simpleTranslate(AnnotatedNode< ? extends Node> annotated, LeafInterpreter interpreter) {
        final FOL2NumCache cache = new FOL2NumCache(annotated);
        final FOL2NumTranslator translator = new FOL2NumTranslator(cache, interpreter) {};
        return (T) annotated.node().accept(translator);
    }


    /**
     * Translates the given annotated formula or expression into a collection of boolean formulas,
     * potentially containing quantitative constraints and taking advantage of the Numeric Matrix
     * representation.
     */
    public static Collection<BooleanValue> translate(final AnnotatedNode<Formula> annotated, LeafInterpreter interpreter){
        final FOL2NumCache cache = new FOL2NumCache(annotated);
        final FOL2NumTranslator translator = new FOL2NumTranslator(cache, interpreter) {
            BooleanValue cache(Formula formula, BooleanValue translation) {
                return super.cache(formula, translation);
            }
        };
        final List<BooleanValue> t = new ArrayList<>();

        for (Formula root : Nodes.conjuncts(annotated.node())) {
            t.add(root.accept(translator));
        }
        // Add the equations to the main circuit
        t.addAll(translator.fixedPointEq);

        return t;
    }


    /**
     * Calls lookup(decls) and returns the cached value, if any. If a translation
     * has not been cached, translates decls into a list of translations of its
     * children, calls cache(...) on it and returns it.
     *
     * @return let t = lookup(decls) | some t => t, cache(decl,
     *         decls.declarations.expression.accept(this))
     */
    @Override
    public List<NumericMatrix> visit(Decls decls) {
        List<NumericMatrix> ret = lookup(decls);
        if (ret != null)
            return ret;
        ret = new ArrayList<NumericMatrix>(decls.size());
        for (Decl decl : decls) {
            ret.add(visit(decl));
        }
        return cache(decls, ret);
    }

    /**
     * Calls lookup(decl) and returns the cached value, if any. If a translation has
     * not been cached, translates decl.expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(decl) | some t => t, cache(decl,
     *         decl.expression.accept(this))
     */
    @Override
    public NumericMatrix visit(Decl decl) {
        NumericMatrix matrix = lookup(decl);
        if (matrix != null)
            return matrix;
        if (decl.multiplicity() != Multiplicity.ONE)
            throw new HigherOrderDeclException(decl);
        return cache(decl, decl.expression().accept(this));
    }

    /**
     * Returns this.interpreter.interpret(relation).
     *
     * @return this.interpreter.interpret(relation)
     */
    @Override
    public NumericMatrix visit(Relation relation) {
        NumericMatrix ret = leafCache.get(relation);
        if (ret == null) {
            ret = interpreter.interpret(relation);
            leafCache.put(relation, ret);
        }
        return ret;
    }

    /**
     * Calls this.env.lookup(variable) and returns the current binding for the given
     * variable.
     * If no binding is found, an UnboundLeafException is thrown.
     *
     * @return this.env.lookup(variable)
     * @throws UnboundLeafException no this.env.lookup(variable)
     */
    @Override
    public NumericMatrix visit(Variable variable) {
        final NumericMatrix ret = env.lookup(variable);
        if (ret == null)
            throw new UnboundLeafException("Unbound variable", variable);
        return ret;
    }

    /**
     * Returns this.interpreter.interpret(constExpr).
     *
     * @return this.interpreter.interpret(constExpr).
     */
    @Override
    public NumericMatrix visit(ConstantExpression constExpr) {
        NumericMatrix ret = leafCache.get(constExpr);
        if (ret == null) {
            ret = interpreter.interpret(constExpr);
            leafCache.put(constExpr, ret);
        }
        return ret;
    }

    /**
     * Calls lookup(unaryExpr) and returns the cached value, if any. If a
     * translation has not been cached, translates the expression, calls cache(...)
     * on it and returns it.
     *
     * @return let t = lookup(unaryExpr) | some t => t, let op =
     *         (unaryExpr.op).(TRANSPOSE->transpose + CLOSURE->closure +
     *         REFLEXIVE_CLOSURE->(lambda(m)(m.closure().plus(iden)) +
     *         DROP->drop) | cache(unaryExpr, op(unaryExpr.child))
     */
    @Override
    public NumericMatrix visit(UnaryExpression unaryExpr) {
        NumericMatrix ret = lookup(unaryExpr);
        if (ret != null)
            return ret;

        final NumericMatrix child = unaryExpr.expression().accept(this);
        final ExprOperator op = unaryExpr.op();

        switch (op) {
            case TRANSPOSE :
                ret = child.transpose();
                break;
            case CLOSURE :
                ret = child.closure();
                //ret = child.reflexiveClosure().dotMinMax(child);
                break;
            case REFLEXIVE_CLOSURE :
                ret = child.closure().union(visit((ConstantExpression) Expression.IDEN));
                //ret = child.reflexiveClosure(fixedPointEq);
                break;
            case DROP :
                ret = child.isBoolean() ? child : child.drop();
                break;
            default :
                throw new IllegalArgumentException("Unknown operator: " + op);
        }
        return cache(unaryExpr, ret);
    }

    /**
     * Calls lookup(binExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(binExpr) | some t => t, let op =
     *         (binExpr.op).(UNION->union + INTERSECTION->intersection + DIFFERENCE->difference
     *         + OVERRIDE->override + JOIN->dotMinMax + MULTIJOIN->dot + PRODUCT->cross + ADDITION->plus +
     *         LEFT_INTERSECTION->leftIntersection + RIGHT_INTERSECTION->rightIntersection) + SCALAR->scalar +
     *         ALPHA -> alphaCut + DOMAIN->domain + RANGE->range + HADAMARD_PRODUCT->product + HADAMARD_DIVISION->divide
     *         KHATRI_RAO->khatriRao | cache(binExpr, op(binExpr.left.accept(this), binExpr.right.accept(this)))
     */
    @Override
    public NumericMatrix visit(BinaryExpression binExpr) {
        NumericMatrix ret = lookup(binExpr);
        if (ret != null)
            return ret;

        final NumericMatrix left = binExpr.left().accept(this);
        final NumericMatrix right = binExpr.right().accept(this);
        final ExprOperator op = binExpr.op();

        switch (op) {
            case UNION :
                ret = left.union(right);
                break;
            case ADDITION:
                ret = left.plus(right);
                break;
            case INTERSECTION :
                ret = left.intersection(right);
                break;
            case DIFFERENCE :
                ret = left.difference(right);
                break;
            case MINUS:
                ret = left.minus(right);
                break;
            case OVERRIDE :
                ret = left.override(right);
                break;
            case JOIN :
                ret = left.dot(right);
                break;
            case MULTIJOIN:
                ret = left.multiDot(right);
                break;
            case PRODUCT :
                ret = left.cross(right);
                break;
            case LEFT_INTERSECTION:
                ret = left.leftIntersection(right);
                break;
            case RIGHT_INTERSECTION:
                ret = left.rightIntersection(right);
                break;
            case DOMAIN:
                ret = left.domain(right);
                break;
            case RANGE:
                ret = left.range(right);
                break;
            case HADAMARD_PRODUCT:
                ret = left.product(right);
                break;
            case HADAMARD_DIVISION:
                ret = left.divide(right);
                break;
            case KHATRI_RAO:
                ret = left.khatriRao(right);
                break;
            case SCALAR:
                ret = right.product((NumericConstant)left.getFirst());
                break;
            case ALPHA:
                ret = right.alphaCut((NumericConstant)left.getFirst());
                break;
            default :
                throw new IllegalArgumentException("Unknown operator: " + op);
        }

        return cache(binExpr, ret);
    }

    /**
     * Calls lookup(expr) and returns the cached value, if any. If a translation has
     * not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(expr) | some t => t, let op = (expr.op).(UNION->union +
     *         INTERSECTION->intersection + OVERRIDE->override + PRODUCT->cross +
     *         ADDITION->plus + LEFT_INTERSECTION->leftIntersection +
     *         RIGHT_INTERSECTION->rightIntersection) + HADAMARD_PRODUCT->product +
     *         HADAMARD_DIVISION->divide |
     *         cache(expr, op(expr.left.accept(this),
     *         expr.right.accept(this)))
     */
    @Override
    public NumericMatrix visit(NaryExpression expr) {
        NumericMatrix ret = lookup(expr);
        if (ret != null)
            return ret;

        final ExprOperator op = expr.op();
        final NumericMatrix first = expr.child(0).accept(this);
        final NumericMatrix[] rest = new NumericMatrix[expr.size() - 1];
        for (int i = 0; i < rest.length; i++) {
            rest[i] = expr.child(i + 1).accept(this);
        }

        switch (op) {
            case UNION :
                ret = first.union(rest);
                break;
            case ADDITION:
                ret = first.plus(rest);
                break;
            case INTERSECTION :
                ret = first.intersection(rest);
                break;
            case OVERRIDE :
                ret = first.override(rest);
                break;
            case PRODUCT :
                ret = first.cross(rest);
                break;
            case LEFT_INTERSECTION:
                ret = first.leftIntersection(rest);
                break;
            case RIGHT_INTERSECTION:
                ret = first.rightIntersection(rest);
                break;
            case HADAMARD_PRODUCT:
                ret = first.product(rest);
                break;
            case HADAMARD_DIVISION:
                ret = first.divide(rest);
                break;
            default :
                throw new IllegalArgumentException("Unknown associative operator: " + op);
        }

        return cache(expr, ret);
    }

    /**
     * Adaptation of {@link kodkod.engine.fol2sat.FOL2BoolTranslator#comprehension(Decls, Formula, int, BooleanValue, int, BooleanMatrix)}
     * For Boolean relations by comprehension within a quantitative setting
     * ------------------------------------------------------------------------------------------------------------------------------------------------
     * Translates the given comprehension as follows (where A_0...A_|A| stand for
     * boolean variables that represent the tuples of the expression A, etc.): let
     * comprehension = "{ a: A, b: B, ..., x: X | F(a, b, ..., x) }" | { a: A, b: B,
     * ..., x: X | a in A && b in B && ... && x in X && F(a, b, ..., x) }.
     *
     * @param decls the declarations comprehension
     * @param formula the body of the comprehension
     * @param currentDecl currently processed declaration; should be 0 initially
     * @param declConstraints the constraints implied by the declarations; should be
     *            Boolean.TRUE intially
     * @param partialIndex partial index into the provided matrix; should be 0
     *            initially
     * @param matrix boolean matrix that will retain the final results; should be an
     *            empty matrix of dimensions universe.size^decls.length initially
     * @ensures the given matrix contains the translation of the comprehension "{
     *          decls | formula }"
     */
    private final void comprehension(Decls decls, Formula formula, int currentDecl, BooleanValue declConstraints, int partialIndex, NumericMatrix matrix) {
        if (currentDecl == decls.size()) {
            matrix.set(partialIndex, factory.implies(factory.and(declConstraints, formula.accept(this)), ONE));
            return;
        }

        final Decl decl = decls.get(currentDecl);
        final NumericMatrix declTransl = visit(decl);
        final int position = (int) StrictMath.pow(interpreter.universe().size(), decls.size() - currentDecl - 1);
        final NumericMatrix groundValue = factory.booleanMatrix(declTransl.dimensions());
        env = env.extend(decl.variable(), decl.expression(), groundValue);
        for (IndexedEntry<NumericValue> entry : declTransl) {
            groundValue.set(entry.index(), ONE);
            comprehension(decls, formula, currentDecl + 1, factory.and(entry.value(), declConstraints), partialIndex + entry.index() * position, matrix);
            groundValue.set(entry.index(), ZERO);
        }
        env = env.parent();
    }

    /**
     * Calls lookup(cexpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(cexpr) | some t => t, cache(cexpr, translate(cexpr))
     */
    @Override
    public NumericMatrix visit(Comprehension comprehension) {
        NumericMatrix ret = lookup(comprehension);
        if (ret != null)
            return ret;

        ret = interpreter.factory().booleanMatrix(Dimensions.square(interpreter.universe().size(), comprehension.decls().size()));
        comprehension(comprehension.decls(), comprehension.formula(), 0, BooleanConstant.TRUE, 0, ret);
        return cache(comprehension, ret);
    }

    /**
     * Extension of {@link kodkod.engine.fol2num.FOL2NumTranslator#comprehension(Decls, Formula, int, BooleanValue, int, BooleanMatrix)}
     * For quantitative expressions by comprehension.
     * ------------------------------------------------------------------------------------------------------------------------------------------------
     * Translates the given comprehension as follows (where A_0...A_|A| stand for
     * boolean variables that represent the tuples of the expression A, etc.): let
     * comprehension = "{ a: A, b: B, ..., x: X | N(a, b, ..., x) }" | { a: A, b: B,
     * ..., x: X | a in A && b in B && ... && x in X
     * && this(a -> b -> ... -> c) = N(a, b, ..., x) }.
     *
     * @param decls the declarations comprehension
     * @param body the body of the comprehension
     * @param currentDecl currently processed declaration; should be 0 initially
     * @param declConstraints the constraints implied by the declarations; should be
     *            Boolean.TRUE intially
     * @param partialIndex partial index into the provided matrix; should be 0
     *            initially
     * @param matrix numeric matrix that will retain the final evaluation; should be an
     *            empty matrix of dimensions universe.size^decls.length initially
     * @ensures the given matrix contains the translation of the comprehension "{
     *          decls | formula }"
     */
    private final void qt_comprehension(Decls decls, Expression body, int currentDecl, BooleanValue declConstraints, int partialIndex, NumericMatrix matrix) {
        if (currentDecl == decls.size()) {
            matrix.set(partialIndex, factory.implies(declConstraints, body.accept(this).getFirst()));
            return;
        }

        final Decl decl = decls.get(currentDecl);
        final NumericMatrix declTransl = visit(decl);
        final int position = (int) StrictMath.pow(interpreter.universe().size(), decls.size() - currentDecl - 1);
        final NumericMatrix groundValue = factory.matrix(declTransl.dimensions());
        env = env.extend(decl.variable(), decl.expression(), groundValue);
        for (IndexedEntry<NumericValue> entry : declTransl) {
            groundValue.set(entry.index(), ONE);
            qt_comprehension(decls, body, currentDecl + 1, factory.and(entry.value(), declConstraints), partialIndex + entry.index() * position, matrix);
            groundValue.set(entry.index(), ZERO);
        }
        env = env.parent();
    }

    /**
     * Calls lookup(cexpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(cexpr) | some t => t, cache(cexpr, translate(cexpr))
     */
    @Override
    public NumericMatrix visit(QtComprehension comprehension) {
        NumericMatrix ret = lookup(comprehension);
        if (ret != null)
            return ret;

        ret = interpreter.factory().matrix(Dimensions.square(interpreter.universe().size(), comprehension.decls().size()));
        qt_comprehension(comprehension.decls(), comprehension.body(), 0, BooleanConstant.TRUE, 0, ret);
        return cache(comprehension, ret);
    }

    /**
     * Calls lookup(ifExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(ifExpr) | some t => t, cache(ifExpr,
     *         ifExpr.condition.accept(this).choice(ifExpr.then.accept(this),
     *         ifExpr.else.accept(this)))
     */
    @Override
    public NumericMatrix visit(IfExpression ifExpr) {
        NumericMatrix ret = lookup(ifExpr);
        if (ret != null)
            return ret;

        final BooleanValue condition = ifExpr.condition().accept(this);
        final NumericMatrix thenExpr = ifExpr.thenExpr().accept(this);
        final NumericMatrix elseExpr = ifExpr.elseExpr().accept(this);
        ret = thenExpr.choice(condition, elseExpr);

        return cache(ifExpr, ret);
    }

    /**
     * Calls lookup(project) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(project) | some t => t, cache(project,
     *         project.expression.accept(this).project(translate(project.columns))
     */
    @Override
    public NumericMatrix visit(ProjectExpression project) {
        NumericMatrix ret = lookup(project);
        if (ret != null)
            return ret;

        final NumericValue[] cols = new NumericValue[project.arity()];
        for (int i = 0, arity = project.arity(); i < arity; i++) {
            cols[i] = project.column(i).accept(this).getFirst();
        }

        return cache(project, project.expression().accept(this).project(cols));
    }

    /**
     * Calls lookup(castExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * Since every numeric value is now represented by a matrix, there is no need to do anything.
     */
    @Override
    public NumericMatrix visit(IntToExprCast castExpr) {
        NumericMatrix ret = lookup(castExpr);
        if (ret != null)
            return ret;

        ret = castExpr.intExpr().accept(this);
        return cache(castExpr, ret);
    }

    /**
     * Represents the given integer constant as its corresponding constant NumericMatrix.
     */
    @Override
    public NumericMatrix visit(IntConstant intConst) {
        int univSize = interpreter.universe().size();
        return factory.constant(Dimensions.square(univSize, 1) ,
                Ints.rangeSet(Ints.range(0, univSize - 1)), factory.constant(intConst.value()));
    }

    /**
     * Calls lookup(intExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(intExpr) | some t => t, cache(intExpr,
     *         intExpr.condition.accept(this).choice(intExpr.then.accept(this),
     *         intExpr.else.accept(this)))
     */
    @Override
    public NumericMatrix visit(IfIntExpression intExpr) {
        NumericMatrix ret = lookup(intExpr);
        if (ret != null)
            return ret;

        final BooleanValue condition = intExpr.condition().accept(this);
        final NumericMatrix thenExpr = intExpr.thenExpr().accept(this);
        final NumericMatrix elseExpr = intExpr.elseExpr().accept(this);
        ret = thenExpr.choice(condition, elseExpr);
        return cache(intExpr, ret);
    }

    /**
     * Calls lookup(intExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(intExpr) | some t => t, cache(intExpr,
     *         translate(intExpr))
     */
    @Override
    public NumericMatrix visit(ExprToIntCast intExpr) {
        NumericMatrix ret = lookup(intExpr);
        if (ret != null)
            return ret;
        NumericMatrix expr = intExpr.expression().accept(this);
        switch (intExpr.op()) {
            case CARDINALITY :
                ret = expr.cardinality();
                break;
            case SUM :
                ret = expr.sum(); //TODO
                break;
            default :
                throw new IllegalArgumentException("unknown operator: " + intExpr.op());
        }
        return cache(intExpr, ret);
    }

    /**
     * Calls lookup(intExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(intExpr) | some t => t, cache(intExpr,
     *         intExpr.left.accept(this) intExpr.op intExpr.right.accept(this))
     */
    @Override
    public NumericMatrix visit(NaryIntExpression intExpr) {
        NumericMatrix ret = lookup(intExpr);
        if (ret != null)
            return ret;

        final NumericMatrix first = intExpr.child(0).accept(this);
        final NumericMatrix[] inputs = new NumericMatrix[intExpr.size() - 1];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = intExpr.child(i + 1).accept(this);
        }

        switch (intExpr.op()) {
            case PLUS :
                ret = first.plus(inputs);
                break;
            case MULTIPLY :
                ret = first.product(inputs);
                break;
            case AND :
            case OR :
                throw new UnsupportedOperationException("The nary operator " + intExpr.op() + " over numeric expressions is unsupported in this type of analysis.");
            default :
                throw new IllegalArgumentException("Unknown nary operator: " + intExpr.op());
        }
        return cache(intExpr, ret);
    }

    /**
     * Calls lookup(intExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(intExpr) | some t => t, cache(intExpr,
     *         intExpr.left.accept(this) intExpr.op intExpr.right.accept(this))
     */
    @Override
    public NumericMatrix visit(BinaryIntExpression intExpr) {
        NumericMatrix ret = lookup(intExpr);
        if (ret != null)
            return ret;
        final NumericMatrix left = intExpr.left().accept(this);
        final NumericMatrix right = intExpr.right().accept(this);

        switch (intExpr.op()) {
            case PLUS :
                ret = left.plus(right);
                break;
            case MINUS :
                ret = left.minus(right);
                break;
            case MULTIPLY :
                ret = left.product(right);
                break;
            case DIVIDE :
                ret = left.divide(right);
                break;
            case MODULO :
                ret = left.modulo(right);
                break;
            case AND :
            case OR :
            case XOR :
            case SHL :
            case SHR :
            case SHA :
                throw new UnsupportedOperationException("The operator " + intExpr.op() + " over numeric expressions is unsupported in this type of analysis.");
            default :
                throw new IllegalArgumentException("Unknown operator: " + intExpr.op());
        }
        return cache(intExpr, ret);
    }

    /**
     * Calls lookup(intExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(intExpr) | some t => t, cache(intExpr,
     *         intExpr.op(intExpr.expression.accept(this)))
     */
    @Override
    public NumericMatrix visit(UnaryIntExpression intExpr) {
        NumericMatrix ret = lookup(intExpr);
        if (ret != null)
            return ret;
        final NumericMatrix child = intExpr.intExpr().accept(this);
        switch (intExpr.op()) {
            case NEG :
                ret = child.negate();
                break;
            case NOT :
                throw new UnsupportedOperationException("The operator " + intExpr.op() + " over a numeric expression is unsupported in this type of analysis.");
            case ABS :
                ret = child.abs();
                break;
            case SGN :
                ret = child.signum();
                break;
            default :
                throw new IllegalArgumentException("Unknown operator: " + intExpr.op());
        }
        return cache(intExpr, ret);
    }

    /**
     * Translates the given sum expression as follows (where A_0...A_|A| stand for
     * boolean variables that represent the tuples of the expression A, etc.): let
     * sum = "sum a: A, b: B, ..., x: X | E(a, b, ..., x) " | sum a: A, b: B, ...,
     * x: X | if (a in A && b in B && ... && x in X) then E(a, b, ..., x) else 0 }.
     *
     * @param decls numeric expression declarations
     * @param expr expression body
     * @param currentDecl currently processed declaration; should be 0 initially
     * @param declConstraints the constraints implied by the declarations; should be
     *            Boolean.TRUE intially
     * @param values numeric values computed so far
     */
    private final void sum(Decls decls, IntExpression expr, int currentDecl, BooleanValue declConstraints, List<NumericMatrix> values) {
        final NumericFactory factory = interpreter.factory();
        if (decls.size() == currentDecl) {
            NumericMatrix intExpr = expr.accept(this);
            NumericMatrix newInt = intExpr.choice(declConstraints, factory.constant(intExpr.dimensions(), intExpr.denseIndices(), ZERO));
            //NumericMatrix newInt = intExpr.choice(declConstraints, factory.constant(intExpr.dimensions(), Ints.rangeSet(Ints.range(0, intExpr.dimensions().capacity() - 1)), ZERO));
            values.add(newInt);
            return;
        }

        final Decl decl = decls.get(currentDecl);
        final NumericMatrix declTransl = visit(decl);
        final NumericMatrix groundValue = factory.matrix(declTransl.dimensions());
        env = env.extend(decl.variable(), decl.expression(), groundValue);
        for (IndexedEntry<NumericValue> entry : declTransl) {
            groundValue.set(entry.index(), entry.value());
            sum(decls, expr, currentDecl + 1, factory.and(entry.value(), declConstraints), values);
            groundValue.set(entry.index(), ZERO);
        }
        env = env.parent();
    }

    /**
     * Calls lookup(intExpr) and returns the cached value, if any. If a translation
     * has not been cached, translates the expression, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(intExpr) | some t => t, cache(intExpr,
     *         translate(intExpr))
     */
    @Override
    public NumericMatrix visit(SumExpression intExpr) {
        final NumericMatrix ret = lookup(intExpr);
        if (ret != null)
            return ret;
        final List<NumericMatrix> values = new ArrayList<NumericMatrix>();

        // Values to sum
        sum(intExpr.decls(), intExpr.intExpr(), 0, BooleanConstant.TRUE, values);

        // Matrix bound
        NumericMatrix type = null;
        for(Decl d : intExpr.decls())
            type = type == null ? visit(d) : type.cross(visit(d));

        // Perform sum
        NumericValue v = ZERO;
        if(!values.isEmpty()){
            NumericMatrix first = values.remove(0);
            NumericMatrix sumValue = values.isEmpty() ? first : first.plus(values.toArray(new NumericMatrix[0]));
            v = sumValue.cardinality().getFirst();
        }

        Dimensions d = type.dimensions();
        return cache(intExpr, factory.constant(d, type.denseIndices(), v));
    }

    /**
     * Calls lookup(intComp) and returns the cached value, if any. If a translation
     * has not been cached, translates the formula, calls cache(...) on it and
     * returns it.
     *
     * @return let t = lookup(intComp) | some t => t, cache(intComp,
     *         intComp.left.accept(this) intComp.op intComp.right.accept(this))
     */
    @Override
    public BooleanValue visit(IntComparisonFormula intComp) {
        BooleanValue ret = lookup(intComp);
        if (ret != null)
            return ret;
        final NumericMatrix left = intComp.left().accept(this);
        final NumericMatrix right = intComp.right().accept(this);
        switch (intComp.op()) {
            case EQ :
                ret = left.eq(right);
                break;
            case NEQ :
                ret = left.eq(right).negation();
                break;
            case LT :
                ret = left.lt(right);
                break;
            case LTE :
                ret = left.lte(right);
                break;
            case GT :
                ret = left.gt(right);
                break;
            case GTE :
                ret = left.gte(right);
                break;
            default :
                throw new IllegalArgumentException("Unknown operator: " + intComp.op());
        }
        return cache(intComp, ret);
    }

    /**
     * Translates the given universally quantified formula as follows (where
     * A_0...A_|A| stand for boolean variables that represent the tuples of the
     * expression A, etc.): let quantFormula = "all a: A, b: B, ..., x: X | F(a, b,
     * ..., x)" | (A_0 && B_0 && ... && X_0 => translate(F(A_0, B_0, ..., X_0))) &&
     * ... && (A_|A| && B_|B| && ... && X_|X| => translate(F(A_|A|, B_|B|, ...,
     * X_|X|))).
     *
     * @param decls formula declarations
     * @param formula the formula body
     * @param currentDecl currently processed declaration; should be 0 initially
     * @param declConstraints the constraints implied by the declarations; should be
     *            Boolean.FALSE initially
     * @param acc the accumulator that contains the top level conjunction; should be
     *            an empty AND accumulator initially
     * @ensures the given accumulator contains the translation of the formula "all
     *          decls | formula"
     */
    private void all(Decls decls, Formula formula, int currentDecl, BooleanValue declConstraints, BooleanAccumulator acc) {
        if (acc.isShortCircuited())
            return;

        if (decls.size() == currentDecl) {
            BooleanValue formulaCircuit = formula.accept(this);
            BooleanValue finalCircuit = factory.or(declConstraints, formulaCircuit);
            acc.add(finalCircuit);
            return;
        }

        final Decl decl = decls.get(currentDecl);
        final NumericMatrix declTransl = visit(decl);
        final NumericMatrix groundValue = declTransl.isBoolean() ? factory.booleanMatrix(declTransl.dimensions()) : factory.matrix(declTransl.dimensions());
        env = env.extend(decl.variable(), decl.expression(), groundValue, Quantifier.ALL);
        for (IndexedEntry<NumericValue> entry : declTransl) {
            groundValue.set(entry.index(), entry.value());
            all(decls, formula, currentDecl + 1, factory.or(factory.eq(entry.value(), ZERO), declConstraints), acc);
            groundValue.set(entry.index(), ZERO);
        }
        env = env.parent();
    }

    /**
     * Translates the given existentially quantified formula as follows (where
     * A_0...A_|A| stand for boolean variables that represent the tuples of the
     * expression A, etc.): let quantFormula = "some a: A, b: B, ..., x: X | F(a, b,
     * ..., x)" | (A_0 && B_0 && ... && X_0 && translate(F(A_0, B_0, ..., X_0))) ||
     * ... || (A_|A| && B_|B| && ... && X_|X| && translate(F(A_|A|, B_|B|, ...,
     * X_|X|)).
     *
     * @param decls formula declarations
     * @param formula the formula body
     * @param currentDecl currently processed declaration; should be 0 initially
     * @param declConstraints the constraints implied by the declarations; should be
     *            Boolean.TRUE intially
     * @param acc the accumulator that contains the top level conjunction; should be
     *            an empty OR accumulator initially
     * @ensures the given accumulator contains the translation of the formula "some
     *          decls | formula"
     */
    private void some(Decls decls, Formula formula, int currentDecl, BooleanValue declConstraints, BooleanAccumulator acc) {
        if (acc.isShortCircuited())
            return;

        if (decls.size() == currentDecl) {
            BooleanValue formulaCircuit = formula.accept(this);
            BooleanValue finalCircuit = factory.and(declConstraints, formulaCircuit);
            acc.add(finalCircuit);
            return;
        }

        final Decl decl = decls.get(currentDecl);
        final NumericMatrix declTransl = visit(decl);
        final NumericMatrix groundValue = declTransl.isBoolean() ? factory.booleanMatrix(declTransl.dimensions()) : factory.matrix(declTransl.dimensions());
        env = env.extend(decl.variable(), decl.expression(), groundValue, Quantifier.SOME);
        for (IndexedEntry<NumericValue> entry : declTransl) {
            groundValue.set(entry.index(), entry.value());
            some(decls, formula, currentDecl + 1, factory.and(entry.value(), declConstraints), acc);
            groundValue.set(entry.index(), ZERO);
        }
        env = env.parent();
    }

    /**
     * Calls lookup(quantFormula) and returns the cached value, if any. If a
     * translation has not been cached, translates the formula, calls cache(...) on
     * it and returns it.
     *
     * @return let t = lookup(quantFormula) | some t => t, cache(quantFormula,
     *         translate(quantFormula))
     */
    @Override
    public final BooleanValue visit(QuantifiedFormula quantFormula) {
        BooleanValue ret = lookup(quantFormula);
        if (ret != null)
            return ret;

        final Quantifier quantifier = quantFormula.quantifier();
        switch (quantifier) {
            case ALL :
                final BooleanAccumulator and = BooleanAccumulator.treeGate(Operator.AND);
                all(quantFormula.decls(), quantFormula.formula(), 0, BooleanConstant.FALSE, and);
                ret = factory.accumulate(and);
                break;
            case SOME :
                final BooleanAccumulator or = BooleanAccumulator.treeGate(Operator.OR);
                some(quantFormula.decls(), quantFormula.formula(), 0, BooleanConstant.TRUE, or);
                ret = factory.accumulate(or);
                break;
            default :
                throw new IllegalArgumentException("Unknown quantifier: " + quantifier);
        }
        return cache(quantFormula, ret);
    }

    /**
     * {@link kodkod.engine.fol2sat.FOL2BoolTranslator#visit(NaryFormula)}
     */
    @Override
    public BooleanValue visit(NaryFormula formula) {
        final BooleanValue ret = lookup(formula);
        if (ret != null)
            return ret;

        final FormulaOperator op = formula.op();
        final Operator.Nary boolOp;

        switch (op) {
            case AND :
                boolOp = Operator.AND;
                break;
            case OR :
                boolOp = Operator.OR;
                break;
            default :
                throw new IllegalArgumentException("Unknown nary operator: " + op);
        }

        final BooleanAccumulator acc = BooleanAccumulator.treeGate(boolOp);
        final BooleanValue shortCircuit = boolOp.shortCircuit();
        for (Formula child : formula) {
            if (acc.add(child.accept(this)) == shortCircuit)
                break;
        }

        return cache(formula, factory.accumulate(acc));
    }

    /**
     * Calls lookup(binFormula) and returns the cached value, if any. If a
     * translation has not been cached, translates the formula, calls cache(...) on
     * it and returns it.
     *
     * @return let t = lookup(binFormula) | some t => t, cache(binFormula,
     *         binFormula.op(binFormula.left.accept(this),
     *         binFormula.right.accept(this))
     */
    @Override
    public BooleanValue visit(BinaryFormula binFormula) {
        BooleanValue ret = lookup(binFormula);
        if (ret != null)
            return ret;

        final BooleanValue left = binFormula.left().accept(this);
        final BooleanValue right = binFormula.right().accept(this);
        final FormulaOperator op = binFormula.op();

        switch (op) {
            case AND :
                ret = factory.and(left, right);
                break;
            case OR :
                ret = factory.or(left, right);
                break;
            case IMPLIES :
                ret = factory.implies(left, right);
                break;
            case IFF :
                ret = factory.iff(left, right);
                break;
            default :
                throw new IllegalArgumentException("Unknown operator: " + op);
        }
        return cache(binFormula, ret);
    }


    /**
     * Calls lookup(not) and returns the cached value, if any. If a translation has
     * not been cached, translates the formula, calls cache(...) on it and returns
     * it.
     *
     * @return let t = lookup(not) | some t => t, cache(not,
     *         !not.formula.accept(this))
     */
    @Override
    public BooleanValue visit(NotFormula not) {
        BooleanValue ret = lookup(not);
        if (ret != null)
            return ret;
        env.negate();
        ret = cache(not, factory.not(not.formula().accept(this)));
        env.negate();
        return ret;
    }

    /**
     * {@link kodkod.engine.fol2sat.FOL2BoolTranslator#visit(ConstantFormula)}
     */
    @Override
    public BooleanValue visit(ConstantFormula constant) {
        return cache(constant, BooleanConstant.constant(constant.booleanValue()));
    }

    /**
     * Calls lookup(compFormula) and returns the cached value, if any. If a
     * translation has not been cached, translates the formula, calls cache(...) on
     * it and returns it.
     *
     * @return let t = lookup(compFormula) | some t => t, let op =
     *         (binExpr.op).(SUBSET->subset + EQUALS->eq + LT->lt + LTE->lte + GT->gt + GTE->gte) |
     *         cache(compFormula, op(compFormula.left.accept(this), compFormula.right.accept(this)))
     */
    @Override
    public BooleanValue visit(ComparisonFormula compFormula) {
        BooleanValue ret = lookup(compFormula);
        if (ret != null)
            return ret;

        final NumericMatrix left = compFormula.left().accept(this);
        final NumericMatrix right = compFormula.right().accept(this);
        final ExprCompOperator op = compFormula.op();

        switch (op) {
            case SUBSET :
                ret = left.subset(right);
                break;
            case EQUALS :
                ret = left.eq(right);
                break;
            case LT :
                ret = left.lt(right);
                break;
            case LTE :
                ret = left.lte(right);
                break;
            case GT :
                ret = left.gt(right);
                break;
            case GTE :
                ret = left.gte(right);
                break;
            default :
                throw new IllegalArgumentException("Unknown operator: " + compFormula.op());
        }
        return cache(compFormula, ret);
    }

    /**
     * Calls lookup(multFormula) and returns the cached value, if any. If a
     * translation has not been cached, translates the formula, calls cache(...) on
     * it and returns it.
     *
     * @return let t = lookup(multFormula) | some t => t, let op =
     *         (multFormula.mult).(NO->none + SOME->some + ONE->one + LONE->lone) |
     *         cache(multFormula, op(multFormula.expression.accept(this)))
     */
    @Override
    public BooleanValue visit(MultiplicityFormula multFormula) {
        BooleanValue ret = lookup(multFormula);
        if (ret != null)
            return ret;

        final NumericMatrix child = multFormula.expression().accept(this);
        final Multiplicity mult = multFormula.multiplicity();

        switch (mult) {
            case NO :
                ret = child.none();
                break;
            case SOME :
                ret = child.some();
                break;
            case ONE :
                ret = child.one();
                break;
            case LONE :
                ret = child.lone();
                break;
            default :
                throw new IllegalArgumentException("Unknown multiplicity: " + mult);
        }

        return cache(multFormula, ret);
    }

    /**
     * {@link kodkod.engine.fol2sat.FOL2BoolTranslator#visit(RelationPredicate)}
     */
    @Override
    public BooleanValue visit(RelationPredicate predicate) {
        BooleanValue ret = lookup(predicate);
        return ret != null ? ret : cache(predicate, predicate.toConstraints().accept(this));
    }

    /**
     * {@link kodkod.engine.fol2sat.FOL2BoolTranslator#visit(FixFormula)}
     */
    @Override
    public BooleanValue visit(FixFormula fixFormula) {
        // cannot translate this to FOL
        throw new HigherOrderDeclException(fixFormula);
    }
}

