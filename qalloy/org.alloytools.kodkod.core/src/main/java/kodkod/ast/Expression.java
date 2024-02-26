/*
 * Kodkod -- Copyright (c) 2005-present, Emina Torlak
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package kodkod.ast;

import static kodkod.ast.operator.ExprCastOperator.CARDINALITY;
import static kodkod.ast.operator.ExprCastOperator.SUM;
import static kodkod.ast.operator.ExprCompOperator.*;
import static kodkod.ast.operator.ExprOperator.*;
import static kodkod.ast.operator.Multiplicity.LONE;
import static kodkod.ast.operator.Multiplicity.NO;
import static kodkod.ast.operator.Multiplicity.ONE;
import static kodkod.ast.operator.Multiplicity.SOME;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import kodkod.ast.operator.*;
import kodkod.ast.visitor.ReturnVisitor;
import kodkod.util.collections.Containers;

/**
 * A relational expression. Unless otherwise noted, all methods in this class
 * throw a NullPointerException when given null arguments.
 *
 * @specfield arity: int
 * @invariant arity > 0
 * @author Emina Torlak
 */
public abstract class Expression extends Node {

    /**
     * The universal relation: contains all atoms in a
     * {@link kodkod.instance.Universe universe of discourse}.
     */
    public static final Expression UNIV = new ConstantExpression("univ", 1);

    /**
     * The identity relation: maps all atoms in a {@link kodkod.instance.Universe
     * universe of discourse} to themselves.
     */
    public static final Expression IDEN = new ConstantExpression("iden", 2);

    /** The empty relation: contains no atoms. */
    public static final Expression NONE = new ConstantExpression("none", 1);

    /**
     * The integer relation: contains all atoms {@link kodkod.instance.Bounds bound}
     * to integers
     */
    public static final Expression INTS = new ConstantExpression("ints", 1);

    /**
     * Constructs a leaf expression
     *
     * @ensures no this.children'
     */
    Expression() {}

    /**
     * Returns the join of this and the specified expression. The effect of this
     * method is the same as calling this.compose(JOIN, expr).
     *
     * @return this.compose(JOIN, expr)
     */
    public final Expression join(Expression expr) {
        return compose(JOIN, expr);
    }

    /**
     * Returns the join of this and the specified expression. The effect of this
     * method is the same as calling this.compose(MULTIJOIN, expr).
     *
     * @return this.compose(MULTIJOIN, expr)
     */
    public final Expression multijoin(Expression expr) {
        return compose(MULTIJOIN, expr);
    }

    /**
     * Returns the product of this and the specified expression. The effect of this
     * method is the same as calling this.compose(PRODUCT, expr).
     *
     * @return this.compose(PRODUCT, expr)
     */
    public final Expression product(Expression expr) {
        return compose(PRODUCT, expr);
    }

    /**
     * Returns the union of this and the specified expression. The effect of this
     * method is the same as calling this.compose(UNION, expr).
     *
     * @return this.compose(UNION, expr)
     */
    public final Expression union(Expression expr) {
        return compose(UNION, expr);
    }

    /**
     * Returns the pointwise addition of this and the specified expression. The effect of this
     * method is the same as calling this.compose(ADDITION, expr).
     *
     * @return this.compose(ADDITION, expr)
     */
    public final Expression addition(Expression expr) {
        return compose(ADDITION, expr);
    }

    /**
     * Returns the maximum union of this and the specified expression. The effect of this
     * method is the same as calling this.compose(MINUS, expr).
     *
     * @return this.compose(MINUS, expr)
     */
    public final Expression minus(Expression expr) {
        return compose(MINUS, expr);
    }

    /**
     * Returns the Hadamard product of this and the specified expression. The effect of this
     * method is the same as calling this.compose(HADAMARD_PRODUCT, expr).
     *
     * @return this.compose(HADAMARD_PRODUCT, expr)
     */
    public final Expression hadamardProduct(Expression expr) {
        return compose(HADAMARD_PRODUCT, expr);
    }

    /**
     * Returns the scalar multiplication of this constant and the specified expression. The effect of this
     * method is the same as calling this.compose(SCALAR, expr).
     *
     * @return this.compose(SCALAR, expr)
     */
    public final Expression scalar(Expression constant) {
        return compose(SCALAR, constant);
    }

    /**
     * Returns the alpha-cut of this constant and the specified expression. The effect of this
     * method is the same as calling this.compose(ALPHA, expr).
     *
     * @return this.compose(ALPHA, expr)
     */
    public final Expression alpha(Expression constant) {
        return compose(ALPHA, constant);
    }

    /**
     * Returns the Hadamard division of this and the specified expression. The effect of this
     * method is the same as calling this.compose(HADAMARD_DIVISION, expr).
     *
     * @return this.compose(HADAMARD_DIVISION, expr)
     */
    public final Expression hadamardDivision(Expression expr) {
        return compose(HADAMARD_DIVISION, expr);
    }

    /**
     * Returns the Khatri-Rao product of this and the specified expression. The effect of this
     * method is the same as calling this.compose(KHATRI_RAO, expr).
     *
     * @return this.compose(KHATRI_RAO, expr)
     */
    public final Expression khatriRao(Expression expr) {
        return compose(KHATRI_RAO, expr);
    }

    /**
     * Returns the difference of this and the specified expression. The effect of
     * this method is the same as calling this.compose(DIFFERENCE, expr).
     *
     * @return this.compose(DIFFERENCE, expr)
     */
    public final Expression difference(Expression expr) {
        return compose(DIFFERENCE, expr);
    }

    /**
     * Returns the intersection of this and the specified expression. The effect of
     * this method is the same as calling this.compose(INTERSECTION, expr).
     *
     * @return this.compose(INTERSECTION, expr)
     */
    public final Expression intersection(Expression expr) {
        return compose(INTERSECTION, expr);
    }

    /**
     * Returns the leftmost intersection of this and the specified expression. The effect of
     * this method is the same as calling this.compose(LEFT_INTERSECTION, expr).
     *
     * @return this.compose(LEFT_INTERSECTION, expr)
     */
    public final Expression leftIntersection(Expression expr) {
        return compose(LEFT_INTERSECTION, expr);
    }

    /**
     * Returns the rightmost intersection of this and the specified expression. The effect of
     * this method is the same as calling this.compose(RIGHT_INTERSECTION, expr).
     *
     * @return this.compose(RIGHT_INTERSECTION, expr)
     */
    public final Expression rightIntersection(Expression expr) {
        return compose(RIGHT_INTERSECTION, expr);
    }

    /**
     * Returns the this with the same domain as the specified expression. The effect of
     * this method is the same as calling this.compose(DOMAIN, expr).
     *
     * @return this.compose(DOMAIN, expr)
     */
    public final Expression domain(Expression expr) {
        return compose(DOMAIN, expr);
    }

    /**
     * Returns the this with the same range as the specified expression. The effect of
     * this method is the same as calling this.compose(RANGE, expr).
     *
     * @return this.compose(RANGE, expr)
     */
    public final Expression range(Expression expr) {
        return compose(RANGE, expr);
    }

    /**
     * Returns the relational override of this with the specified expression. The
     * effect of this method is the same as calling this.compose(OVERRIDE, expr).
     *
     * @return this.compose(OVERRIDE, expr)
     */
    public final Expression override(Expression expr) {
        return compose(OVERRIDE, expr);
    }

    /**
     * Returns the composition of this and the specified expression, using the given
     * binary operator.
     *
     * @requires op in ExprOperator.BINARY
     * @return {e: Expression | e.left = this and e.right = expr and e.op = this }
     */
    public final Expression compose(ExprOperator op, Expression expr) {
        return new BinaryExpression(this, op, expr);
    }

    /**
     * Returns the union of the given expressions. The effect of this method is the
     * same as calling compose(UNION, exprs).
     *
     * @return compose(UNION, exprs)
     */
    public static Expression union(Expression... exprs) {
        return compose(UNION, exprs);
    }

    /**
     * Returns the union of the given expressions. The effect of this method is the
     * same as calling compose(UNION, exprs).
     *
     * @return compose(UNION, exprs)
     */
    public static Expression union(Collection< ? extends Expression> exprs) {
        return compose(UNION, exprs);
    }

    /**
     * Returns the intersection of the given expressions. The effect of this method
     * is the same as calling compose(INTERSECTION, exprs).
     *
     * @return compose(INTERSECTION, exprs)
     */
    public static Expression intersection(Expression... exprs) {
        return compose(INTERSECTION, exprs);
    }

    /**
     * Returns the intersection of the given expressions. The effect of this method
     * is the same as calling compose(INTERSECTION, exprs).
     *
     * @return compose(INTERSECTION, exprs)
     */
    public static Expression intersection(Collection< ? extends Expression> exprs) {
        return compose(INTERSECTION, exprs);
    }

    /**
     * Returns the product of the given expressions. The effect of this method is
     * the same as calling compose(PRODUCT, exprs).
     *
     * @return compose(PRODUCT, exprs)
     */
    public static Expression product(Expression... exprs) {
        return compose(PRODUCT, exprs);
    }

    /**
     * Returns the product of the given expressions. The effect of this method is
     * the same as calling compose(PRODUCT, exprs).
     *
     * @return compose(PRODUCT, exprs)
     */
    public static Expression product(Collection< ? extends Expression> exprs) {
        return compose(PRODUCT, exprs);
    }

    /**
     * Returns the override of the given expressions. The effect of this method is
     * the same as calling compose(OVERRIDE, exprs).
     *
     * @return compose(OVERRIDE, exprs)
     */
    public static Expression override(Expression... exprs) {
        return compose(OVERRIDE, exprs);
    }

    /**
     * Returns the override of the given expressions. The effect of this method is
     * the same as calling compose(OVERRIDE, exprs).
     *
     * @return compose(OVERRIDE, exprs)
     */
    public static Expression override(Collection< ? extends Expression> exprs) {
        return compose(OVERRIDE, exprs);
    }

    /**
     * Returns the maximum union of the given expressions. The effect of this method is
     * the same as calling compose(ADDITION, exprs).
     *
     * @return compose(ADDITION, exprs)
     */
    public static Expression addition(Expression... exprs) {
        return compose(ADDITION, exprs);
    }

    /**
     * Returns the maximum union of the given expressions. The effect of this method is
     * the same as calling compose(ADDITION, exprs).
     *
     * @return compose(ADDITION, exprs)
     */
    public static Expression addition(Collection< ? extends Expression> exprs) {
        return compose(ADDITION, exprs);
    }

    /**
     * Returns the leftmost intersection of the given expressions. The effect of this method is
     * the same as calling compose(LEFT_INTERSECTION, exprs).
     *
     * @return compose(LEFT_INTERSECTION, exprs)
     */
    public static Expression leftIntersection(Expression... exprs) {
        return compose(LEFT_INTERSECTION, exprs);
    }

    /**
     * Returns the leftmost intersection of the given expressions. The effect of this method is
     * the same as calling compose(LEFT_INTERSECTION, exprs).
     *
     * @return compose(LEFT_INTERSECTION, exprs)
     */
    public static Expression leftIntersection(Collection< ? extends Expression> exprs) {
        return compose(LEFT_INTERSECTION, exprs);
    }

    /**
     * Returns the rightmost intersection of the given expressions. The effect of this method is
     * the same as calling compose(RIGHT_INTERSECTION, exprs).
     *
     * @return compose(RIGHT_INTERSECTION, exprs)
     */
    public static Expression rightIntersection(Expression... exprs) {
        return compose(RIGHT_INTERSECTION, exprs);
    }

    /**
     * Returns the rightmost intersection of the given expressions. The effect of this method is
     * the same as calling compose(RIGHT_INTERSECTION, exprs).
     *
     * @return compose(RIGHT_INTERSECTION, exprs)
     */
    public static Expression rightIntersection(Collection< ? extends Expression> exprs) {
        return compose(RIGHT_INTERSECTION, exprs);
    }

    /**
     * Returns the Hadamard product of the given expressions. The effect of this method is
     * the same as calling compose(HADAMARD_PRODUCT, exprs).
     *
     * @return compose(HADAMARD_PRODUCT, exprs)
     */
    public static Expression hadamardProduct(Expression... exprs) {
        return compose(HADAMARD_PRODUCT, exprs);
    }

    /**
     * Returns the Hadamard product of the given expressions. The effect of this method is
     * the same as calling compose(HADAMARD_PRODUCT, exprs).
     *
     * @return compose(HADAMARD_PRODUCT, exprs)
     */
    public static Expression hadamardProduct(Collection< ? extends Expression> exprs) {
        return compose(HADAMARD_PRODUCT, exprs);
    }

    /**
     * Returns the Hadamard division of the given expressions. The effect of this method is
     * the same as calling compose(HADAMARD_DIVISION, exprs).
     *
     * @return compose(HADAMARD_DIVISION, exprs)
     */
    public static Expression hadamardDivision(Expression... exprs) {
        return compose(HADAMARD_DIVISION, exprs);
    }

    /**
     * Returns the Hadamard division of the given expressions. The effect of this method is
     * the same as calling compose(HADAMARD_DIVISION, exprs).
     *
     * @return compose(HADAMARD_DIVISION, exprs)
     */
    public static Expression hadamardDivision(Collection< ? extends Expression> exprs) {
        return compose(HADAMARD_DIVISION, exprs);
    }

    /**
     * Returns the composition of the given expressions using the given operator.
     *
     * @requires exprs.length = 2 => op.binary(), exprs.length > 2 => op.nary()
     * @return exprs.length=1 => exprs[0] else {e: Expression | e.children = exprs
     *         and e.op = this }
     */
    public static Expression compose(ExprOperator op, Expression... exprs) {
        switch (exprs.length) {
            case 0 :
                throw new IllegalArgumentException("Expected at least one argument: " + Arrays.toString(exprs));
            case 1 :
                return exprs[0];
            case 2 :
                return new BinaryExpression(exprs[0], op, exprs[1]);
            default :
                return new NaryExpression(op, Containers.copy(exprs, new Expression[exprs.length]));
        }
    }

    /**
     * Returns the composition of the given expressions using the given operator.
     *
     * @requires exprs.size() = 2 => op.binary(), exprs.size() > 2 => op.nary()
     * @return exprs.size()=1 => exprs.iterator().next() else {e: Expression |
     *         e.children = exprs.toArray() and e.op = this }
     */
    public static Expression compose(ExprOperator op, Collection< ? extends Expression> exprs) {
        switch (exprs.size()) {
            case 0 :
                throw new IllegalArgumentException("Expected at least one argument: " + exprs);
            case 1 :
                return exprs.iterator().next();
            case 2 :
                final Iterator< ? extends Expression> itr = exprs.iterator();
                return new BinaryExpression(itr.next(), op, itr.next());
            default :
                return new NaryExpression(op, exprs.toArray(new Expression[exprs.size()]));
        }
    }

    /**
     * Returns the transpose of this. The effect of this method is the same as
     * calling this.apply(TRANSPOSE).
     *
     * @return this.apply(TRANSPOSE)
     */
    public final Expression transpose() {
        return apply(TRANSPOSE);
    }

    /**
     * Returns the drop of this. The effect of this method is the same as
     * calling this.apply(DROP).
     *
     * @return this.apply(DROP)
     */
    public final Expression drop(){
        return apply(DROP);
    }

    /**
     * Returns the transitive closure of this. The effect of this method is the same
     * as calling this.apply(CLOSURE).
     *
     * @return this.apply(CLOSURE)
     */
    public final Expression closure() {
        return apply(CLOSURE);
    }

    /**
     * Returns the reflexive transitive closure of this. The effect of this method
     * is the same as calling this.apply(REFLEXIVE_CLOSURE).
     *
     * @return this.apply(REFLEXIVE_CLOSURE)
     */
    public final Expression reflexiveClosure() {
        return apply(REFLEXIVE_CLOSURE);
    }

    /**
     * Returns the expression that results from applying the given unary operator to
     * this.
     *
     * @requires op.unary()
     * @return {e: Expression | e.expression = this && e.op = this }
     * @throws IllegalArgumentException this.arity != 2
     */
    public final Expression apply(ExprOperator op) {
        return new UnaryExpression(op, this);
    }

    /**
     * Returns the projection of this expression onto the specified columns.
     *
     * @return {e: Expression | e = project(this, columns) }
     * @throws IllegalArgumentException columns.length < 1
     */
    public final Expression project(IntExpression... columns) {
        return new ProjectExpression(this, columns);
    }

    /**
     * Returns the quantitative comprehension expression constructed from this expression and the
     * given declarations.
     *
     * @requires all d: decls.decls[int] | decl.variable.arity = 1 and
     *           decl.multiplicity = ONE
     * @return {e: Expression | e.decls = decls and e.body = this }
     */
    public final Expression comprehension(Decls decls) {
        return new QtComprehension(decls, this);
    }

    /**
     * Returns the cardinality of this expression. The effect of this method is the
     * same as calling this.apply(CARDINALITY).
     *
     * @return this.apply(CARDINALITY)
     */
    public final IntExpression count() {
        return apply(CARDINALITY);
    }

    /**
     * Returns the sum of the integer atoms in this expression. The effect of this
     * method is the same as calling this.apply(SUM).
     *
     * @return this.apply(SUM)
     */
    public final IntExpression sum() {
        return apply(SUM);
    }

    /**
     * Returns the cast of this expression to an integer expression, that represents
     * either the cardinality of this expression (if op is CARDINALITY) or the sum
     * of the integer atoms it contains (if op is SUM).
     *
     * @return {e: IntExpression | e.op = op && e.expression = this}
     */
    public final IntExpression apply(ExprCastOperator op) {
        return new ExprToIntCast(this, op);
    }

    /**
     * Returns the formula 'this = expr'. The effect of this method is the same as
     * calling this.compare(EQUALS, expr).
     *
     * @return this.compare(EQUALS, expr)
     */
    public final Formula eq(Expression expr) {
        return compare(EQUALS, expr);
    }

    /**
     * Returns the formula 'this in expr'. The effect of this method is the same as
     * calling this.compare(SUBSET, expr).
     *
     * @return this.compare(SUBSET, expr)
     */
    public final Formula in(Expression expr) {
        return compare(SUBSET, expr);
    }

    /**
     * Returns the formula 'this < expr'. The effect of this method is the same as
     * calling this.compare(LT, expr).
     *
     * @return this.compare(LT, expr)
     */
    public final Formula lt(Expression expr){
        return compare(LT, expr);
    }

    /**
     * Returns the formula 'this > expr'. The effect of this method is the same as
     * calling this.compare(GT, expr).
     *
     * @return this.compare(GT, expr)
     */
    public final Formula gt(Expression expr){
        return compare(GT, expr);
    }

    /**
     * Returns the formula 'this <= expr'. The effect of this method is the same as
     * calling this.compare(LTE, expr).
     *
     * @return this.compare(LTE, expr)
     */
    public final Formula lte(Expression expr){
        return compare(LTE, expr);
    }

    /**
     * Returns the formula 'this >= expr'. The effect of this method is the same as
     * calling this.compare(GTE, expr).
     *
     * @return this.compare(GTE, expr)
     */
    public final Formula gte(Expression expr){
        return compare(GTE, expr);
    }

    /**
     * Returns the formula that represents the comparison of this and the given
     * expression using the given comparison operator.
     *
     * @return {f: Formula | f.left = this && f.right = expr && f.op = op}
     */
    public final Formula compare(ExprCompOperator op, Expression expr) {
        return new ComparisonFormula(this, op, expr);
    }

    /**
     * Returns the formula 'some this'. The effect of this method is the same as
     * calling this.apply(SOME).
     *
     * @return this.apply(SOME)
     */
    public final Formula some() {
        return apply(SOME);
    }

    /**
     * Returns the formula 'no this'. The effect of this method is the same as
     * calling this.apply(NO).
     *
     * @return this.apply(NO)
     */
    public final Formula no() {
        return apply(NO);
    }

    /**
     * Returns the formula 'one this'. The effect of this method is the same as
     * calling this.apply(ONE).
     *
     * @return this.apply(ONE)
     */
    public final Formula one() {
        return apply(ONE);
    }

    /**
     * Returns the formula 'lone this'. The effect of this method is the same as
     * calling this.apply(LONE).
     *
     * @return this.apply(LONE)
     */
    public final Formula lone() {
        return apply(LONE);
    }

    public Expression pre() {
        return new UnaryExpression(ExprOperator.PRE, this);
    }

    /**
     * Returns the formula that results from applying the specified multiplicity to
     * this expression. The SET multiplicity is not allowed.
     *
     * @return {f: Formula | f.multiplicity = mult && f.expression = this}
     * @throws IllegalArgumentException mult = SET
     */
    public final Formula apply(Multiplicity mult) {
        return new MultiplicityFormula(mult, this);
    }

    /**
     * Returns the arity of this expression.
     *
     * @return this.arity
     */
    public abstract int arity();

    /**
     * Accepts the given visitor and returns the result.
     *
     * @see kodkod.ast.Node#accept(kodkod.ast.visitor.ReturnVisitor)
     */
    @Override
    public abstract <E, F, D, I> E accept(ReturnVisitor<E,F,D,I> visitor);

}
