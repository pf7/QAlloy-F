package edu.mit.csail.sdg.translator;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorFatal;
import edu.mit.csail.sdg.alloy4.Pair;
import edu.mit.csail.sdg.alloy4.XMLNode;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.ExprVar;
import edu.mit.csail.sdg.ast.Sig;
import kodkod.ast.Relation;
import kodkod.instance.QtTupleSet;
import kodkod.instance.Tuple;
import kodkod.instance.TupleSet;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Quantitative extension of {@link A4SolutionReader}.
 */
public class A4QtSolutionReader extends A4SolutionReader {

    public A4QtSolutionReader(Iterable<Sig> sigs, XMLNode xml) throws IOException, Err {
        super(sigs, xml);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void integerAtoms(final TreeSet<String> atoms, int bitwidth, int min, int max){}

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finishParsing(A4Solution sol, Map<Expr,TupleSet>  expr2ts) throws IOException, Err{
        Map<Relation, TupleSet> qts = new HashMap<>();

        for (Map.Entry<Expr,TupleSet> e : expr2ts.entrySet()) {
            ExprVar v = (ExprVar) (e.getKey());
            TupleSet ts = e.getValue();
            Relation r = sol.addRel(v.label, ts, ts, ts instanceof QtTupleSet);
            sol.kr2type(r, v.type());
            //handle quantitative tuple sets
            qts.put(r, ts);
        }

        sol.solve(null, null, null, qts, null);
    }

    /** Parse tuple. */
    private Pair<Tuple, String> parseTuple(XMLNode tuple, int arity, Boolean isBoolean) throws Err {
        Tuple ans = null;
        String weight = null;
        try {
            for (XMLNode sub : tuple)
                if (sub.is("atom")) {
                    Tuple x = factory.tuple(sub.getAttribute("label"));
                    if (ans == null)
                        ans = x;
                    else
                        ans = ans.product(x);
                }
                else if(sub.is("weight"))
                    if(weight == null)
                        weight = sub.getAttribute("value");
                    else throw new ErrorFatal("Expecting exactly one <weight value=\"..\"/> for each tuple.");

            if (ans == null)
                throw new ErrorFatal("Expecting: <tuple> <atom label=\"..\"/> .. </tuple> or <tuple> <atom label=\"..\"/> .. <weight value=\"..\"/> </tuple>");
            if (weight == null && isBoolean != null && !isBoolean)
                throw new ErrorFatal("Expecting: <tuple> <atom label=\"..\"/> .. <weight value=\"..\"/> </tuple>");
            if (weight != null && isBoolean != null && isBoolean)
                throw new ErrorFatal("Expecting: <tuple> <atom label=\"..\"/> .. </tuple>");
            if (ans.arity() != arity)
                throw new ErrorFatal("Expecting: tuple of arity " + arity + " but got tuple of arity " + ans.arity());
            return new Pair<>(ans, weight);
        } catch (Throwable ex) {
            throw new ErrorFatal("Expecting: <tuple> <atom label=\"..\"/> .. </tuple> or <tuple> <atom label=\"..\"/> .. <weight value=\"..\"/> </tuple>", ex);
        }
    }

    /**
     * Parses the tuple quantity depending on it being an integer or double value.
     */
    private Number parseNum(String n){
        return Pattern.matches("(-?)[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?", n) ?
                        Double.parseDouble(n) :
                        Integer.parseInt(n);
    }

    /** Parse tuples. */
    @Override
    protected TupleSet parseTuples(XMLNode tuples, int arity) throws Err {
        TupleSet ans;
        Iterator<XMLNode> it = tuples.iterator();

        if(it.hasNext()){
            XMLNode n = it.next();
            while(it.hasNext() && !n.is("tuple"))
                n = it.next();

            if(n.is("tuple")) {
                Pair<Tuple, String> t = parseTuple(n, arity, null);
                // Check if its a Numeric or Boolean Relation
                boolean isBoolean = t.b == null;

                if (isBoolean) {
                    ans = factory.noneOf(arity);
                    ans.add(t.a);
                } else {
                    ans = factory.noneOfQt(arity);
                    //Assuming that the XML is well formed, weight is either a double or an integer
                    ((QtTupleSet) ans).add(t.a, parseNum(t.b));
                }

                // Remaining tuples
                while (it.hasNext())
                    if ((n = it.next()).is("tuple")) {
                        t = parseTuple(n, arity, isBoolean);
                        if (isBoolean)
                            ans.add(t.a);
                        else ((QtTupleSet) ans).add(t.a, parseNum(t.b));
                    }
            }
            // Empty
            else ans = factory.noneOf(arity);
        }
        // Empty
        else ans = factory.noneOf(arity);

        return ans;
    }

    /** Context dependent atom parsing. */
    protected TupleSet parseAtom(XMLNode sub, TupleSet atoms){
        String q = sub.getAttribute("value", null);
        Tuple t = factory.tuple(sub.getAttribute("label"));
        if(q == null) atoms.add(t); // Boolean
        else{ // Quantitative
            TupleSet current = atoms.clone();
            atoms = factory.noneOfQt(1);
            atoms.addAll(current);
            ((QtTupleSet)atoms).add(t, parseNum(q));
        }
        return atoms;
    }

    /** Context dependent sig parsing. */
    protected TupleSet parseParentSig(TupleSet parent, TupleSet child){
        parent = parent.clone();
        if(child instanceof QtTupleSet){
            TupleSet ts = parent.clone();
            parent = factory.noneOfQt(ts.arity());
            parent.addAll(ts);
        }
        parent.addAll(child);
        return parent;
    }

    /**
     * Parsing of a Quantitative Alloy Instance.
     * {@see A4SolutionReader#read}
     */
    public static A4Solution read(Iterable<Sig> sigs, XMLNode xml) throws Err {
        try {
            if (sigs == null)
                sigs = new ArrayList<Sig>();
            A4SolutionReader x = new A4QtSolutionReader(sigs, xml);
            return x.sol;
        } catch (Throwable ex) {
            if (ex instanceof Err)
                throw ((Err) ex);
            else
                throw new ErrorFatal("Fatal error occured: " + ex, ex);
        }
    }

}
