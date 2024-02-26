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

import kodkod.ast.visitor.ReturnVisitor;
import kodkod.ast.visitor.VoidVisitor;

/**
 * A quantitative comprehension expression
 *
 * @specfield decls: Declarations
 * @specfield body: Expression
 * @invariant arity = sum(decls.declarations().arity)
 * @invariant children = 0->decls + 1->body
 */
public final class QtComprehension extends Expression {

    private final Decls   decls;
    private final Expression body;
    private final int     arity;

    /**
     * Constructs a comprehension expression with the specified decls and formula
     *
     * @ensures this.decls' = decls && this.body' = body
     * @throws NullPointerException decls = null || body = null
     */
    QtComprehension(Decls declarations, Expression body) {
        if (body == null)
            throw new NullPointerException("null expression");
        this.decls = declarations;
        this.body = body;
        int arity = 0;
        for (Decl d : decls)
            arity += d.variable().arity();
        this.arity = arity;
    }

    /**
     * @return this.body
     */
    public Expression body() {
        return body;
    }

    /**
     * @return this.decls
     */
    public Decls decls() {
        return decls;
    }

    /**
     * Returns the arity of this comprehension expression, which is the sum of the
     * arities of declared variables
     *
     * @return #this.decls
     */
    @Override
    public int arity() {
        return arity;
    }

    /**
     * {@inheritDoc}
     *
     * @see kodkod.ast.Expression#accept(kodkod.ast.visitor.ReturnVisitor)
     */
    @Override
    public <E, F, D, I> E accept(ReturnVisitor<E,F,D,I> visitor) {
        return visitor.visit(this);
    }

    /**
     * {@inheritDoc}
     *
     * @see kodkod.ast.Node#accept(kodkod.ast.visitor.VoidVisitor)
     */
    @Override
    public void accept(VoidVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * {@inheritDoc}
     *
     * @see kodkod.ast.Node#toString()
     */
    @Override
    public String toString() {
        return "{ " + decls().toString() + " | " + body().toString() + " }";
    }

}
