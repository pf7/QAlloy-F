package kodkod.engine.fol2num;

import java.util.*;

import kodkod.ast.Expression;
import kodkod.ast.Node;
import kodkod.ast.Variable;
import kodkod.engine.fol2sat.Environment;
import kodkod.engine.fol2sat.FreeVariableCollector;
import kodkod.engine.num.NumericMatrix;
import kodkod.util.nodes.AnnotatedNode;

import static kodkod.engine.num.NumericConstant.ZERO;

/**
 * Quantitative Extension of {@link kodkod.engine.fol2sat.FOL2BoolCache}
 * ---------------------------------------------------------------------
 * Manages the caching policy for a translation from FOL to the numeric matrix representation.
 * In particular it determines which translations to cache, when to throw them out
 * of the cache, etc.
 *
 * @specfield node: Node // node being translated
 * @specfield cached: node.*children // the nodes whose translations are cached
 * @specfield cache: cached -> (Object ->lone Environment)
 */
final class FOL2NumCache {

    private final Map<Node,Record> cache;

    /**
     * Constructs a new translation cache for the given annotated node.
     *
     * @ensures this.node' = annotated.node
     */
    FOL2NumCache(AnnotatedNode< ? extends Node> annotated) {
        final CacheCollector collector = new CacheCollector(annotated.sharedNodes());
        annotated.node().accept(collector);

        this.cache = new IdentityHashMap<Node,Record>(collector.cache().size());
        for (Map.Entry<Node,Set<Variable>> e : collector.cache().entrySet()) {
            Set<Variable> freeVars = e.getValue();
            if (freeVars.isEmpty())
                this.cache.put(e.getKey(), new NoVarRecord());
            else
                this.cache.put(e.getKey(), new MultiVarRecord(freeVars));
        }
    }

    /**
     * If the translation of the given node, with its free variables bound as they
     * are in the given environment, has been cached, the cached value is returned.
     * Otherwise, null is returned.
     *
     * @return this.cache[node][Object] in env.map => this.cache[node].map, null
     */
    @SuppressWarnings("unchecked" )
    <T> T lookup(Node node, Environment<NumericMatrix, Expression> env) {
        final Record info = cache.get(node);
        return info == null ? null : (T) info.get(env);
    }

    /**
     * Caches the given translation for the specified node, if the given node is in
     * this.cached. Otherwise does nothing. The method returns the specified
     * translation.
     *
     * @ensures node in this.cached => this.cache' = this.cache ++
     *          node->translation->env, this.cache' = this.cache
     * @return translation
     */
    final <T> T cache(Node node, T translation, Environment<NumericMatrix,Expression> env) {
        final Record info = cache.get(node);
        if (info != null) {
            info.set(translation, env);
        }
        return translation;
    }

    /**
     * Collects the free variables of the nodes in a given AST whose translations
     * should be cached.
     *
     * @specfield root: Node
     * @specfield cached: root.*children -> Set<Variable>
     * @invariant all c: root.*children | some cached[c] => cached[c] =
     *            freeVariables(c)
     * @author Emina Torlak
     */
    private static final class CacheCollector extends FreeVariableCollector {

        /**
         * Constructs a new cache collector.
         */
        protected CacheCollector(Set<Node> cached) {
            super(cached);
        }

        /**
         * Returns this.cache. This method should be called *after* the visitor has been
         * applied to this.root.
         *
         * @return this.cache
         */
        final Map<Node,Set<Variable>> cache() {
            return cache;
        }

        /**
         * We record the set of free variables for the given node if the node is shared,
         * or if it has free variables, none of which is the most recently declared
         * variable.
         *
         * @ensures node in sharedNodes || ((node.^(~children) in (QuantifiedFormula +
         *          Comprehension)) && (some varsInScope.top() =>
         *          !freeVars.contains(varsInScope.top()))) => this.cache' = this.cache
         *          ++ node->varsInScope, this.cache' = this.cache
         * @return freeVars
         */
        @Override
        protected final Set<Variable> cache(Node node, Set<Variable> freeVars) {
            if (cached.contains(node) || !varsInScope.empty() && !freeVars.contains(varsInScope.peek())) {
                cache.put(node, reduce(freeVars));
            }
            return freeVars;
        }

    }

    /**
     * A container class that stores the translation of a shared node (BooleanValue
     * for formulas and NumericMatrix for expressions) and bindings for the node's
     * free variables which were used to generate the translation. Storing the
     * bindings is necessary for proper handling of sharing within quantified
     * formulas and comprehensions.
     *
     * @specfield varBinding: Variable -> lone int
     * @specfield translation: lone Object
     */
    private static abstract class Record {

        Object translation;

        /**
         * Returns this.translation if the given environment has the same mappings for
         * the free variables of the translated node as the ones used to generate
         * this.translation. Otherwise returns null.
         *
         * @requires all v: varBinding.int | some e.lookup(v)
         * @return all v: varBinding.int | e.lookup(v).get(varBinding[v])=TRUE =>
         *         this.translation, null
         * @throws NullPointerException e = null
         */
        abstract Object get(Environment<NumericMatrix,Expression> e);

        /**
         * Sets this.translation to the given translation and sets the free variable
         * bindings to those given by the specified environment.
         *
         * @requires all v: varBinding.int | some env.lookup(v)
         * @ensures this.translation' = translation && this.varBinding' = {v:
         *          this.varBinding.int, tupleIndex: int | tupleIndex =
         *          env.lookup(v).iterator().next().index() }
         */
        abstract void set(Object transl, Environment<NumericMatrix,Expression> env);
    }

    /**
     * A TranslationInfo for a node with one or more free variables.
     */
    private static final class MultiVarRecord extends Record {

        final Variable[] vars;
        final int[]      tuples;

        /**
         * Constructs a translation unit for a node which has the given set of free
         * variables.
         *
         * @ensures this.freeVariables' = vars && no this.translation'
         */
        MultiVarRecord(Set<Variable> freeVariables) {
            this.vars = freeVariables.toArray(new Variable[freeVariables.size()]);
            this.tuples = new int[freeVariables.size()];
        }

        /**
         * @see FOL2NumCache.Record#get(kodkod.engine.fol2sat.Environment)
         */
        @Override
        Object get(Environment<NumericMatrix,Expression> e) {
            if (translation == null)
                return null;
            for (int i = 0; i < vars.length; i++) {
                if (e.lookup(vars[i]).get(tuples[i]) == ZERO)
                    return null;
            }
            return translation;
        }

        /**
         * @see FOL2NumCache.Record#set(java.lang.Object,
         *      kodkod.engine.fol2sat.Environment)
         */
        @Override
        void set(Object transl, Environment<NumericMatrix,Expression> env) {
            translation = transl;
            for (int i = 0; i < vars.length; i++) {
                final NumericMatrix varVal = env.lookup(vars[i]);
                tuples[i] = varVal.iterator().next().index();
                if (transl == varVal) {
                    translation = varVal.clone();
                }
            }
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            final StringBuilder b = new StringBuilder("{");
            b.append(String.valueOf(translation));
            for (int i = 0; i < vars.length; i++) {
                b.append(" (");
                b.append(vars[i]);
                b.append(", ");
                b.append(tuples[i]);
                b.append(")");
            }
            b.append("}");
            return b.toString();
        }
    }

    /**
     * A TranslationInfo for a node with no free variables.
     */
    private static final class NoVarRecord extends Record {

        /**
         * @see FOL2NumCache.Record#get(kodkod.engine.fol2sat.Environment)
         */
        @Override
        Object get(Environment<NumericMatrix,Expression> e) {
            return translation;
        }

        /**
         * @see FOL2NumCache.Record#set(java.lang.Object,
         *      kodkod.engine.fol2sat.Environment)
         */
        @Override
        void set(Object transl, Environment<NumericMatrix,Expression> env) {
            translation = transl;
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "{" + translation + "}";
        }
    }
}

