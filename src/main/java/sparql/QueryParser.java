package sparql;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.aggregate.Args;
import org.apache.jena.sparql.syntax.*;
import org.parboiled.BaseParser;

/**
 * Created by Riccardo on 09/08/16.
 */
public class QueryParser extends BaseParser<Object> {
    public Query getQuery(int i) {
        if (i == -1) {
            int size = getContext().getValueStack().size();
            i = size > 0 ? size - 1 : 0;
            System.out.println("Unknown index " + i);
        }
        return (Query) peek(i);
    }

    public Query popQuery(int i) {

        if (i == -1) {
            int size = getContext().getValueStack().size();
            i = size > 0 ? size - 1 : 0;
        }

        return (Query) pop(i);
    }

    public boolean pushQuery(Query q) {
        return push(0, q);
    }


    public Element popElement() {
        return ((Element) pop());
    }


    public boolean addElementToQuery() {
        getQuery(1).addElement(popElement());
        return true;
    }

    public boolean addTemplateToQuery2() {
        getQuery(1).setConstructTemplate(new Template((((TripleCollectorBGP) pop()).getBGP())));
        return true;

    }

    public boolean addTemplateToQuery() {
        ((ElementGroup) peek(1)).addElement(new ElementPathBlock(((TripleCollectorBGP) peek()).getBGP()));
        getQuery(2).setConstructTemplate(new Template((((TripleCollectorBGP) pop()).getBGP())));
        return true;

    }

    public boolean addSubElement() {
        ((ElementGroup) peek(1)).addElement(popElement());
        return true;
    }

    public boolean addFilterElement() {
        return push(new ElementFilter((Expr) pop()));
    }

    public boolean addOptionalElement() {
        return push(new ElementOptional(popElement()));
    }

    public boolean createUnionElement() {
        return push(new ElementUnion(popElement()));
    }

    public boolean addUnionElement() {
        ((ElementUnion) peek(1)).addElement(popElement());
        return true;
    }

    public boolean addNamedGraphElement() {
        return push(new ElementNamedGraph((Node) pop(), popElement()));
    }

    public boolean addFunctionCall() {
        return push(((Function) pop()).build());
    }

    public boolean addArg() {
        ((Args) peek(1)).add((Expr) pop());
        return true;
    }

    public boolean addExprToExprList() {
        ((ExprList) peek(1)).add((Expr) pop());
        return true;
    }

    void debug(String calls) {
        System.out.println(calls);
    }

    public String trimMatch() {
        return match().trim();
    }

}
