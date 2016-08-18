package it.polimi.sr.run;

import it.polimi.sr.mql.streams.Window;
import it.polimi.sr.mql.events.declaration.EventDecl;
import it.polimi.sr.mql.parser.MQLParser;
import it.polimi.sr.mql.MQLQuery;
import it.polimi.sr.mql.events.calculus.MatchClause;
import org.apache.commons.io.FileUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.query.SortCondition;
import org.apache.jena.riot.system.IRIResolver;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.core.VarExprList;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.lang.SyntaxVarScope;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementNamedGraph;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MQLMain {

    public static void main(String[] args) throws IOException {

        String input = getInput();

        MQLParser parser = Parboiled.createParser(MQLParser.class);

        parser.setResolver(IRIResolver.create());

        ParsingResult<MQLQuery> result = new ReportingParseRunner(parser.Query()).run(input);

        MQLQuery q = result.resultValue;

        System.out.println(q.toString());
        print(q);
        System.out.println("Check valid");
        SyntaxVarScope.check(q);
    }

    private static void print(MQLQuery q) {
        System.out.println("---");
        System.out.println(q.getGraphURIs());
        System.out.println(q.getQueryType());
        System.out.println(q.getNamedGraphURIs());

        VarExprList project = q.getProject();

        if (q.isSelectType()) {

            for (Var v : project.getVars()) {
                System.out.println("Project Var " + v.toString() + " Expr " + project.getExpr(v));
            }
            for (String v : q.getResultVars()) {
                System.out.println("Result Var " + v);
            }
        } else if (q.isConstructType()) {
            Map<org.apache.jena.graph.Node, BasicPattern> graphPattern = q.getConstructTemplate().getGraphPattern();
            for (org.apache.jena.graph.Node b : graphPattern.keySet()) {
                System.out.println("Node " + b + " Pattern " + graphPattern.get(b));
            }
        }

        Element queryPattern = q.getQueryPattern();
        System.out.println("queryPattern " + queryPattern);

        System.out.println("PREFIXES");

        Map<String, String> nsPrefixMap = q.getPrologue().getPrefixMapping().getNsPrefixMap();
        for (String prefix : nsPrefixMap.keySet()) {
            String uri = nsPrefixMap.get(prefix);
            System.out.println(prefix + ":" + uri);
        }

        List<SortCondition> orderBy = q.getOrderBy();

        if (orderBy != null && !orderBy.isEmpty())
            for (SortCondition sc : orderBy) {
                System.out.println(sc.getExpression().toString() + "  " +
                        ((org.apache.jena.query.Query.ORDER_DESCENDING == sc.direction) ? "DESC" : "ASC"));
            }

        System.out.println("LIMIT " + q.getLimit());
        System.out.println("OFFSET " + q.getOffset());

        VarExprList groupBy = q.getGroupBy();

        System.out.println("GROUP BY");
        List<Var> vars = groupBy.getVars();
        for (Var v : vars) {
            System.out.println("VAR " + v + " EXPR " +
                    groupBy.getExpr(v));
        }

        System.out.println("HAVING");
        List<Expr> havingExprs = q.getHavingExprs();
        for (Expr e : havingExprs) {
            System.out.println("EXPR " + e.toString());
        }
        System.out.println("---");

        if (q.getNamedwindows() != null) {

            for (Node w : q.getNamedwindows().keySet()) {
                System.out.println(q.getNamedwindows().get(w));
            }
        }

        if (q.getWindows() != null) {
            for (Window w : q.getWindows()) {
                System.out.println(w.toString());
            }
        }

        if (q.getWindowGraphElements() != null) {
            for (ElementNamedGraph windowGraphElement : q.getWindowGraphElements()) {
                System.out.println(windowGraphElement.toString());
            }
        }

        if (q.getEventDeclarations() != null) {
            for (String k : q.getEventDeclarations().keySet()) {
                EventDecl x = q.getEventDeclarations().get(k);
                System.out.println(x);
                if (x.getIfdecl() != null) {
                    Set<Var> seleect = new HashSet<Var>();
                    Var v = Var.alloc("o");
                    seleect.add(v);
                    System.out.println(x.getIfdecl().toSPARQL(seleect).toString());
                }
            }
        }

        if (q.getMatchclauses() != null) {
            for (MatchClause matchclause : q.getMatchclauses()) {
                System.out.println(matchclause.toString());
            }
        }

        System.out.println(q.getHeader());

        System.out.println(q.toString());

    }

    public static String getInput() throws IOException {
        File file = new File("/Users/Riccardo/_Projects/Streamreasoning/c-sparql_parser/src/main/resources/query.q");
        return FileUtils.readFileToString(file);
    }
}