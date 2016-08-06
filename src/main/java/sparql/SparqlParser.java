package sparql;/*
 * Copyright (c) 2009 Ken Wenzel
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

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Node_URI;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.expr.aggregate.AggregateRegistry;
import org.apache.jena.sparql.expr.aggregate.Args;
import org.apache.jena.sparql.syntax.*;
import org.apache.jena.sparql.util.ExprUtils;
import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;

/**
 * SPARQL Parser
 *
 * @author Ken Wenzel, adapted by Mathias Doenitz
 */
@SuppressWarnings({"InfiniteRecursion"})
@BuildParseTree
public class SparqlParser extends BaseParser<Object> {
    // <Parser>

    public Query getQuery() {
        int size = getContext().getValueStack().size();
        return (Query) peek(size > 0 ? size - 1 : 0);
    }

    public Rule Query() {
        return Sequence(push(new Query()), WS(), Prologue(), SelectQuery()
                , EOI);   //ConstructQuery(), DescribeQuery(), AskQuery()
    }

    public Rule Prologue() {
        return Sequence(Optional(BaseDecl()), ZeroOrMore(PrefixDecl()));
    }

    public Rule BaseDecl() {
        return Sequence(BASE(), IRI_REF(), pushQuery(((Query) pop(1)).setBaseURI(match())));
    }

    public Rule PrefixDecl() {
        return Sequence(PrefixBuild(), pushQuery(((Query) pop(1)).setPrefix((Prefix) pop())));
    }

    public Rule PrefixBuild() {
        return Sequence(PREFIX(), PNAME_NS(), push(new Prefix(match())), IRI_REF(), push(((Prefix) pop()).setURI(match())));
    }

    public Rule SelectQuery() {
        debug("SelectQuery");
        return Sequence(SELECT(), Optional(FirstOf(DISTINCT(),
                REDUCED())), FirstOf(
                OneOrMore(Sequence(Var(), pushQuery(((Query) pop(1)).addVariable((Node) pop())))), ASTERISK()),
                ZeroOrMore(DatasetClause()), WhereClause(), SolutionModifier()); //TODO FirstOf(WhereClause, Subselect())
    }

    public Rule ConstructQuery() {
        debug("ConstructQuery");
        return Sequence(CONSTRUCT(), ConstructTemplate(),
                ZeroOrMore(DatasetClause()), WhereClause(), SolutionModifier());
    }

    public Rule DescribeQuery() {
        return Sequence(DESCRIBE(), FirstOf(OneOrMore(VarOrIRIref()),
                ASTERISK()), ZeroOrMore(DatasetClause()),
                Optional(WhereClause()), SolutionModifier());
    }

    public Rule AskQuery() {
        return Sequence(ASK(), ZeroOrMore(DatasetClause()), WhereClause());
    }

    public Rule DatasetClause() {
        debug("DatasetClause");
        return Sequence(FROM(), FirstOf(DefaultGraphClause(),
                NamedGraphClause()));
    }

    public Rule DefaultGraphClause() {
        return Sequence(SourceSelector(), pushQuery(((Query) pop(1)).addGraphURI((Node_URI) pop())));
    }

    public Rule NamedGraphClause() {
        return Sequence(NAMED(), SourceSelector(), pushQuery(((Query) pop(1)).addNamedGraphURI((Node_URI) pop())));
    }

    public Rule SourceSelector() {
        return IriRef();
    }

    public Rule WhereClause() {
        debug("WhereClause");
        return Sequence(Optional(WHERE()), GroupGraphPattern(), addElementToQuery());
    }

    public boolean addElementToQuery() {
        return push(((Query) pop(1)).addElement(popElement()));
    }

    public Rule SolutionModifier() {
        return Sequence(Optional(OrderClause()), Optional(LimitOffsetClauses()));
    }

    public Rule LimitOffsetClauses() {
        return FirstOf(Sequence(LimitClause(), Optional(OffsetClause())),
                Sequence(OffsetClause(), Optional(LimitClause())));
    }

    public Rule OrderClause() {
        return Sequence(ORDER(), BY(), OneOrMore(OrderCondition()));
    }

    public Rule OrderCondition() {
        return FirstOf(
                Sequence(FirstOf(ASC(), DESC()), BrackettedExpression()),
                FirstOf(Constraint(), Var()));
    }

    public Rule LimitClause() {
        return Sequence(LIMIT(), INTEGER());
    }

    public Rule OffsetClause() {
        return Sequence(OFFSET(), INTEGER());
    }

    public Rule GroupGraphPattern() {
        debug("GroupGraphPattern");
        return Sequence(OPEN_CURLY_BRACE(), GroupGraphPatternSub(), CLOSE_CURLY_BRACE());
    }

    public Rule GroupGraphPatternSub() {
        debug("GroupGraphPatternSub");
        return Sequence(push(new ElementGroup()), Optional(Sequence(TriplesBlock(), addSubElement())),
                ZeroOrMore(
                        Sequence(Sequence(GraphPatternNotTriples(), addSubElement()),
                                Optional(DOT()), Optional(Sequence(TriplesBlock(), addSubElement())))));
    }

    public boolean addSubElement() {
        debug("addSubElement");
        ((ElementGroup) peek(1)).addElement(popElement());
        return true;
    }

    public boolean addSubElement2() {
        debug("addSubElement2");
        ((ElementGroup) peek(2)).addElement(popElement());
        return true;
    }

    public boolean addOptionalElement() {
        return push(new ElementOptional(popElement()));
    }

    public boolean createUnionElement() {
        return push(new ElementUnion(popElement()));
    }

    public boolean addUnionElement() {
        debug("addUnionElement");
        ((ElementUnion) peek(1)).addElement(popElement());
        return true;
    }

    public boolean addNamedGraphElement() {
        debug("addNamedGraphElement");
        return push(new ElementNamedGraph((Node) pop(), popElement()));
    }

    public Rule TriplesBlock() {
        debug("TriplesBlock");
        return Sequence(TriplesSameSubject(), Optional(Sequence(DOT(),
                Optional(Sequence(swap(), TriplesBlock(), addSubElement(), swap())))));
    }

    public Rule GraphPatternNotTriples() {
        return FirstOf(Filter(), OptionalGraphPattern(), GroupOrUnionGraphPattern(),
                GraphGraphPattern());
    }

    public Rule OptionalGraphPattern() {
        debug("Optional");
        return Sequence(OPTIONAL(), GroupGraphPattern(),
                addOptionalElement());
    }


    public Rule GraphGraphPattern() {
        return Sequence(GRAPH(), VarOrIRIref(), GroupGraphPattern(), swap(), addNamedGraphElement());
    }

    public Rule GroupOrUnionGraphPattern() {
        return Sequence(GroupGraphPattern(), createUnionElement(), ZeroOrMore(Sequence(UNION(),
                GroupGraphPattern(), addUnionElement())));
    }

    public Rule Filter() {
        return Sequence(FILTER(), Constraint(), addFilterElement());
    }

    public boolean addFilterElement() {
        return push(new ElementFilter((Expr) pop()));
    }

    public Rule Constraint() {
        return FirstOf(BrackettedExpression(), BuiltInCall(), FunctionCall());
    }

    public Rule FunctionCall() {
        debug("FunctionCall");
        return Sequence(IriRef(), push(new Function(match())), ArgList(),
                push(((Function) pop(1)).add((Args) pop())),
                FirstOf(addFunctionCall(), addAggregateFunctionCall()));
    }

    public Rule addAggregateFunctionCall() {
        return Sequence(Test((AggregateRegistry.isRegistered(((Function) peek()).getIri()))),
                push(getQuery().allocAggregate(((Function) pop()).createCustom())));
    }

    public boolean addFunctionCall() {
        return push(((Function) pop()).build());
    }

    public Rule ArgList() {
        return Sequence(push(new Args()), FirstOf(Sequence(OPEN_BRACE(), CLOSE_BRACE()), Sequence(
                OPEN_BRACE(), Expression(), addArg(), ZeroOrMore(Sequence(COMMA(),
                        Expression(), addArg())), CLOSE_BRACE())));
    }

    public boolean addArg() {
        ((Args) peek(1)).add((Expr) pop());
        return true;
    }

    public Rule ConstructTemplate() {

        return Sequence(OPEN_CURLY_BRACE(), push(new TripleCollectorBGP()), push(new Template((((TripleCollectorBGP) peek()).getBGP()))), swap(), Optional(ConstructTriples()),
                CLOSE_CURLY_BRACE());
    }

    public Rule ConstructTriples() {
        debug("ConstructTriples");
        return Sequence(TriplesSameSubject(), Optional(Sequence(DOT(),
                Optional(ConstructTriples()))));
    }

    public Rule TriplesSameSubject() {
        debug("TriplesSameSubject");
        return FirstOf(Sequence(Sequence(VarOrTerm(), push_TripleBuilder()), //building the map

                PropertyListNotEmpty(), push(((TripleBuilder) pop()).build())),

                Sequence(TriplesNode(), PropertyList()));
    }

    public boolean push_TripleBuilder() {
        debug("Push TripleBuilder");
        return push(new TripleBuilder((Node) pop()));
    }

    public Rule PropertyListNotEmpty() {
        debug("PropertyListNotEmpty");
        return Sequence(
                Sequence(Verb(), push(((TripleBuilder) peek(1)).add((Node) pop())),
                        ObjectList(), drop())
                , ZeroOrMore(Sequence(SEMICOLON(),
                        Optional
                                (Sequence(Verb(), push(((TripleBuilder) peek(1)).add((Node) pop())), ObjectList(), drop())))));
    }

    public Rule PropertyList() {
        debug("PropertyList");
        return Optional(PropertyListNotEmpty());
    }

    public Rule ObjectList() {
        debug("ObjectList");
        return Sequence(Object_(), push(((TripleBuilder) peek(2)).add((Node) pop(), (Node) pop())),
                ZeroOrMore(Sequence(COMMA(), Object_(), push(((TripleBuilder) peek(2)).add((Node) pop(), (Node) pop())))));
    }

    public Node peekNode(int i) {
        return (Node) peek(i);
    }


    public Rule Object_() {
        debug("Object_");
        return GraphNode();
    }

    public Rule Verb() {
        debug("Verb");
        return FirstOf(VarOrIRIref(), A());
    }

    public Rule TriplesNode() {
        debug("TriplesNode");
        return FirstOf(Collection(), BlankNodePropertyList());
    }

    public Rule BlankNodePropertyList() {
        return Sequence(OPEN_SQUARE_BRACE(), PropertyListNotEmpty(),
                CLOSE_SQUARE_BRACE());
    }

    public Rule Collection() {
        debug("Collection");
        return Sequence(OPEN_BRACE(), OneOrMore(GraphNode()), CLOSE_BRACE());
    }

    public Rule GraphNode() {
        debug("GraphNode");
        return FirstOf(VarOrTerm(), TriplesNode());
    }

    public Rule VarOrTerm() {
        debug("VarOrTerm");
        return FirstOf(Var(), GraphTerm());
    }

    public Rule VarOrIRIref() {
        return FirstOf(Var(), IriRef());
    }

    public Rule Var() {
        debug("Var");
        return FirstOf(VAR1(), VAR2());
    }

    public Rule GraphTerm() {
        return FirstOf(IriRef(), RdfLiteral(), NumericLiteral(),
                BooleanLiteral(), BlankNode(), Sequence(OPEN_BRACE(),
                        CLOSE_BRACE()));
    }

    public Rule Expression() {
        return ConditionalOrExpression();
    }

    public Rule ConditionalOrExpression() {
        return Sequence(ConditionalAndExpression(), ZeroOrMore(Sequence(OR(),
                ConditionalAndExpression()), push(new E_LogicalOr((Expr) pop(), (Expr) pop()))));
    }

    public Rule ConditionalAndExpression() {
        return Sequence(ValueLogical(), ZeroOrMore(Sequence(AND(),
                ValueLogical(), push(new E_LogicalAnd((Expr) pop(), (Expr) pop())))));
    }

    public Rule ValueLogical() {
        return RelationalExpression();
    }

    public Rule RelationalExpression() {
        return Sequence(NumericExpression(), Optional(FirstOf(//
                Sequence(EQUAL(), NumericExpression(), swap(), push(new E_Equals((Expr) pop(), (Expr) pop()))), //
                Sequence(NOT_EQUAL(), NumericExpression(), swap(), push(new E_NotEquals((Expr) pop(), (Expr) pop()))), //
                Sequence(LESS(), NumericExpression(), swap(), push(new E_LessThan((Expr) pop(), (Expr) pop()))), //
                Sequence(GREATER(), NumericExpression(), swap(), push(new E_GreaterThan((Expr) pop(), (Expr) pop()))), //
                Sequence(LESS_EQUAL(), NumericExpression(), swap(), push(new E_LessThanOrEqual((Expr) pop(), (Expr) pop()))), //
                Sequence(GREATER_EQUAL(), NumericExpression(), swap(), push(new E_GreaterThanOrEqual((Expr) pop(), (Expr) pop()))) //
                ) //
        ));
    }

    public Rule NumericExpression() {
        return AdditiveExpression();
    }

    public Rule AdditiveExpression() {
        return Sequence(MultiplicativeExpression(), //
                ZeroOrMore(FirstOf(
                        Sequence(PLUS(), MultiplicativeExpression(),
                                push(new E_Add((Expr) pop(), (Expr) pop()))), //
                        Sequence(MINUS(), MultiplicativeExpression()//TODO DOUBLE_NEGATIVE
                                , swap(),
                                push(new E_Subtract((Expr) pop(), (Expr) pop()))))));
    }

    public Rule MultiplicativeExpression() {
        return Sequence(UnaryExpression(), ZeroOrMore(FirstOf(Sequence(
                ASTERISK(), UnaryExpression(),
                push(new E_Multiply((Expr) pop(), (Expr) pop()))), Sequence(DIVIDE(),
                UnaryExpression(), swap(), push(new E_Divide((Expr) pop(), (Expr) pop()))))));
    }

    public Rule UnaryExpression() {
        return FirstOf(Sequence(NOT(), PrimaryExpression()), Sequence(PLUS(),
                PrimaryExpression()), Sequence(MINUS(), PrimaryExpression()),
                PrimaryExpression());
    }

    public Rule PrimaryExpression() {
        return FirstOf(BrackettedExpression(), BuiltInCall(),
                IriRefOrFunction(), Sequence(RdfLiteral(), asExpr()), Sequence(NumericLiteral(), asExpr()),
                Sequence(BooleanLiteral(), asExpr()), Sequence(Var(), asExpr()));
    }

    public Rule BrackettedExpression() {
        return Sequence(OPEN_BRACE(), Expression(), CLOSE_BRACE());
    }

    public Rule BuiltInCall() {
        return FirstOf(
                //TODO verify is the are all
                Sequence(STR(), OPEN_BRACE(), Expression(), push(new E_Str((Expr) pop())), CLOSE_BRACE()),
                Sequence(LANG(), OPEN_BRACE(), Expression(), push(new E_Lang((Expr) pop())), CLOSE_BRACE()),
                Sequence(LANGMATCHES(), OPEN_BRACE(), Expression(), COMMA(),
                        Expression(), push(new E_LangMatches((Expr) pop(), (Expr) pop())), CLOSE_BRACE()),
                Sequence(DATATYPE(), OPEN_BRACE(), Expression(), push(new E_Datatype((Expr) pop())), CLOSE_BRACE()),
                Sequence(BOUND(), OPEN_BRACE(), Var(), push(new E_Bound(new ExprVar((String) pop()))), CLOSE_BRACE()),
                Sequence(SAMETERM(), OPEN_BRACE(), Expression(), COMMA(),
                        Expression(), push(new E_SameTerm((Expr) pop(), (Expr) pop())), CLOSE_BRACE()),
                Sequence(ISIRI(), OPEN_BRACE(), Expression(), push(new E_IsIRI((Expr) pop())), CLOSE_BRACE()),
                Sequence(ISURI(), OPEN_BRACE(), Expression(), push(new E_IsURI((Expr) pop())), CLOSE_BRACE()),
                Sequence(ISBLANK(), OPEN_BRACE(), Expression(), push(new E_IsBlank((Expr) pop())), CLOSE_BRACE()),
                Sequence(ISLITERAL(), OPEN_BRACE(), Expression(), push(new E_IsLiteral((Expr) pop())), CLOSE_BRACE()),
                RegexExpression());
    }

    public Rule RegexExpression() {
        return Sequence(REGEX(), OPEN_BRACE(), Expression(), COMMA(),
                Expression(), Optional(Sequence(COMMA(), Expression())),
                CLOSE_BRACE());
    }

    public Rule IriRefOrFunction() {
        return Sequence(IriRef(), push(new Function((Node_URI) pop())), Optional(Sequence(ArgList()
                , push(((Function) pop(1)).add((Args) pop())), FirstOf(addFunctionCall(), addAggregateFunctionCall())
        )));
    }

    public Rule RdfLiteral() {
        return Sequence(String(), Optional(FirstOf(LANGTAG(), Sequence(
                REFERENCE(), IriRef()))));
    }

    public Rule NumericLiteral() {
        return FirstOf(NumericLiteralUnsigned(), NumericLiteralPositive(),
                NumericLiteralNegative());
    }

    public Rule NumericLiteralUnsigned() {
        return FirstOf(DOUBLE(), DECIMAL(), INTEGER());
    }

    public boolean asExpr() {
        return push(ExprUtils.nodeToExpr((Node) pop()));
    }

    public Rule NumericLiteralPositive() {
        return FirstOf(DOUBLE_POSITIVE(), DECIMAL_POSITIVE(),
                INTEGER_POSITIVE());
    }

    public Rule NumericLiteralNegative() {
        return FirstOf(DOUBLE_NEGATIVE(), DECIMAL_NEGATIVE(),
                INTEGER_NEGATIVE());
    }

    public Rule BooleanLiteral() {
        return Sequence(FirstOf(TRUE(), FALSE()), push(NodeFactory.createLiteralByValue(match(), XSDDatatype.XSDboolean)));
    }

    public Rule String() {
        return Sequence(FirstOf(STRING_LITERAL_LONG1(), STRING_LITERAL1(),
                STRING_LITERAL_LONG2(), STRING_LITERAL2()), push(NodeFactory.createLiteralByValue(match().trim().replace("\"",""), XSDDatatype.XSDstring)));
    }

    public Rule IriRef() {
        return FirstOf(Sequence(IRI_REF(), push(NodeFactory.createURI(match()))), PrefixedName());
    }

    public Rule PrefixedName() {
        return FirstOf(Sequence(PNAME_LN(), resolvePNAME(match())), Sequence(PNAME_NS(), resolvePNAME(match())));
    }

    public boolean resolvePNAME(String match) {
        String uri = getQuery().getQ().getPrologue().expandPrefixedName(match.trim());
        return push(NodeFactory.createURI(uri));
    }

    public Rule BlankNode() {
        return FirstOf(BLANK_NODE_LABEL(), Sequence(OPEN_SQUARE_BRACE(),
                CLOSE_SQUARE_BRACE()));
    }
    // </Parser>

    // <Lexer>

    public Rule WS() {
        return ZeroOrMore(FirstOf(COMMENT(), WS_NO_COMMENT()));
    }

    public Rule WS_NO_COMMENT() {
        return FirstOf(Ch(' '), Ch('\t'), Ch('\f'), EOL());
    }

    public Rule PNAME_NS() {
        return Sequence(Optional(PN_PREFIX()), ChWS(':'));
    }

    public Rule PNAME_LN() {
        return Sequence(PNAME_NS(), PN_LOCAL());
    }

    public Rule BASE() {
        return StringIgnoreCaseWS("BASE");
    }

    public Rule PREFIX() {
        return StringIgnoreCaseWS("PREFIX");
    }

    public Rule SELECT() {
        return Sequence(pushQuery(popQuery().setSelectQuery()), StringIgnoreCaseWS("SELECT"));
    }

    public Rule DISTINCT() {
        return Sequence(StringIgnoreCaseWS("DISTINCT"), pushQuery(popQuery().setDistinct(match())));
    }

    public Rule REDUCED() {
        return StringIgnoreCaseWS("REDUCED");
    }

    public Rule CONSTRUCT() {
        return Sequence(pushQuery(popQuery().setConstructQuery()), StringIgnoreCaseWS("CONSTRUCT"));
    }

    public Rule DESCRIBE() {
        return Sequence(pushQuery(popQuery().setDescribeQuery()), StringIgnoreCaseWS("DESCRIBE"));
    }

    public Rule ASK() {
        return Sequence(pushQuery(popQuery().setAskQuery()), StringIgnoreCaseWS("ASK"));

    }

    public Rule FROM() {
        return StringIgnoreCaseWS("FROM");
    }

    public Rule NAMED() {
        return StringIgnoreCaseWS("NAMED");
    }

    public Rule WHERE() {
        return StringIgnoreCaseWS("WHERE");
    }

    public Rule ORDER() {
        return StringIgnoreCaseWS("ORDER");
    }

    public Rule BY() {
        return StringIgnoreCaseWS("BY");
    }

    public Rule ASC() {
        return StringIgnoreCaseWS("ASC");
    }

    public Rule DESC() {
        return StringIgnoreCaseWS("DESC");
    }

    public Rule LIMIT() {
        return StringIgnoreCaseWS("LIMIT");
    }

    public Rule OFFSET() {
        return StringIgnoreCaseWS("OFFSET");
    }

    public Rule OPTIONAL() {
        return StringIgnoreCaseWS("OPTIONAL");
    }

    public Rule GRAPH() {
        return StringIgnoreCaseWS("GRAPH");
    }

    public Rule UNION() {
        return StringIgnoreCaseWS("UNION");
    }

    public Rule FILTER() {
        return StringIgnoreCaseWS("FILTER");
    }

    public Rule A() {
        return ChWS('a');
    }

    public Rule STR() {
        return StringIgnoreCaseWS("STR");
    }

    public Rule LANG() {
        return StringIgnoreCaseWS("LANG");
    }

    public Rule LANGMATCHES() {
        return StringIgnoreCaseWS("LANGMATCHES");
    }

    public Rule DATATYPE() {
        return StringIgnoreCaseWS("DATATYPE");
    }

    public Rule BOUND() {
        return StringIgnoreCaseWS("BOUND");
    }

    public Rule SAMETERM() {
        return StringIgnoreCaseWS("SAMETERM");
    }

    public Rule ISIRI() {
        return StringIgnoreCaseWS("ISIRI");
    }

    public Rule ISURI() {
        return StringIgnoreCaseWS("ISURI");
    }

    public Rule ISBLANK() {
        return StringIgnoreCaseWS("ISBLANK");
    }

    public Rule ISLITERAL() {
        return StringIgnoreCaseWS("ISLITERAL");
    }

    public Rule REGEX() {
        return StringIgnoreCaseWS("REGEX");
    }

    public Rule TRUE() {
        return StringIgnoreCaseWS("TRUE");
    }

    public Rule FALSE() {
        return StringIgnoreCaseWS("FALSE");
    }

    public Rule IRI_REF() {
        return Sequence(LESS_NO_COMMENT(), //
                ZeroOrMore(Sequence(TestNot(FirstOf(LESS_NO_COMMENT(), GREATER(), '"', OPEN_CURLY_BRACE(),
                        CLOSE_CURLY_BRACE(), '|', '^', '\\', '`', CharRange('\u0000', '\u0020'))), ANY)), //
                GREATER());
    }

    public Rule BLANK_NODE_LABEL() {
        return Sequence("_:", PN_LOCAL(), WS());
    }

    public Rule VAR1() {
        debug("VAR1");
        return Sequence('?', VARNAME(), isPush(match()), WS());
    }

    public boolean isPush(String s) {
        System.out.println(s);
        return push(Var.alloc(s));
    }

    public Rule VAR2() {
        debug("VAR2");
        return Sequence('$', VARNAME(), isPush(match()), WS());
    }

    public Rule LANGTAG() {
        return Sequence('@', OneOrMore(PN_CHARS_BASE()), ZeroOrMore(Sequence(
                MINUS(), OneOrMore(Sequence(PN_CHARS_BASE(), DIGIT())))), WS());
    }

    public Rule INTEGER() {
        return Sequence(Sequence(OneOrMore(DIGIT()), push(NodeFactory.createLiteral(match(), XSDDatatype.XSDinteger))), WS());
    }

    public Rule DECIMAL() {
        return Sequence(FirstOf( //
                Sequence(Sequence(OneOrMore(DIGIT()), DOT(), ZeroOrMore(DIGIT())), push(NodeFactory.createLiteral(match(), XSDDatatype.XSDdecimal))), //
                Sequence(Sequence(DOT(), OneOrMore(DIGIT())), push(NodeFactory.createLiteral(match(), XSDDatatype.XSDdecimal))) //
        ), WS());


    }

    public Rule DOUBLE() {
        return Sequence(FirstOf(//
                Sequence(Sequence(OneOrMore(DIGIT()), DOT(), ZeroOrMore(DIGIT()),
                        EXPONENT()), push(NodeFactory.createLiteral(match(), XSDDatatype.XSDdouble))), //
                Sequence(Sequence(DOT(), OneOrMore(DIGIT()), EXPONENT()), push(NodeFactory.createLiteral(match(), XSDDatatype.XSDdouble))), // //
                Sequence(Sequence(OneOrMore(DIGIT()), EXPONENT()), push(NodeFactory.createLiteral(match(), XSDDatatype.XSDdouble)))), WS());
    }

    public Rule INTEGER_POSITIVE() {
        return Sequence(PLUS(), INTEGER());
    }

    public Rule DECIMAL_POSITIVE() {
        return Sequence(PLUS(), DECIMAL());
    }

    public Rule DOUBLE_POSITIVE() {
        return Sequence(PLUS(), DOUBLE());
    }

    public Rule INTEGER_NEGATIVE() {
        return Sequence(MINUS(), INTEGER());
    }

    public Rule DECIMAL_NEGATIVE() {
        return Sequence(MINUS(), DECIMAL());
    }

    public Rule DOUBLE_NEGATIVE() {
        return Sequence(MINUS(), DOUBLE());
    }

    public Rule EXPONENT() {
        return Sequence(IgnoreCase('e'), Optional(FirstOf(PLUS(), MINUS())),
                OneOrMore(DIGIT()));
    }

    public Rule STRING_LITERAL1() {
        return Sequence("'", ZeroOrMore(FirstOf(Sequence(TestNot(FirstOf("'",
                '\\', '\n', '\r')), ANY), ECHAR())), "'", WS());
    }

    public Rule STRING_LITERAL2() {
        return Sequence('"', ZeroOrMore(FirstOf(Sequence(TestNot(AnyOf("\"\\\n\r")), ANY), ECHAR())), '"', WS());
    }

    public Rule STRING_LITERAL_LONG1() {
        return Sequence("'''", ZeroOrMore(Sequence(
                Optional(FirstOf("''", "'")), FirstOf(Sequence(TestNot(FirstOf(
                        "'", "\\")), ANY), ECHAR()))), "'''", WS());
    }

    public Rule STRING_LITERAL_LONG2() {
        return Sequence("\"\"\"", ZeroOrMore(Sequence(Optional(FirstOf("\"\"", "\"")),
                FirstOf(Sequence(TestNot(FirstOf("\"", "\\")), ANY), ECHAR()))), "\"\"\"", WS());
    }

    public Rule ECHAR() {
        return Sequence('\\', AnyOf("tbnrf\\\"\'"));
    }

    public Rule PN_CHARS_U() {
        return FirstOf(PN_CHARS_BASE(), '_');
    }

    public Rule VARNAME() {
        debug("VARNAME");
        return Sequence(FirstOf(PN_CHARS_U(), DIGIT()),
                ZeroOrMore(
                        FirstOf(
                                PN_CHARS_U(),
                                DIGIT(), '\u00B7', CharRange('\u0300', '\u036F'),
                                CharRange('\u203F', '\u2040')))
                , WS());
    }


    public Rule PN_CHARS() {
        return FirstOf(MINUS(), DIGIT(), PN_CHARS_U(), '\u00B7',
                CharRange('\u0300', '\u036F'), CharRange('\u203F', '\u2040'));
    }

    public Rule PN_PREFIX() {
        return Sequence(PN_CHARS_BASE(), Optional(ZeroOrMore(FirstOf(PN_CHARS(), Sequence(DOT(), PN_CHARS())))));
    }

    public Rule PN_LOCAL() {
        return Sequence(FirstOf(PN_CHARS_U(), DIGIT()),
                Optional(ZeroOrMore(FirstOf(PN_CHARS(), Sequence(DOT(), PN_CHARS())))), WS());
    }

    public Rule PN_CHARS_BASE() {
        return FirstOf( //
                CharRange('A', 'Z'),//
                CharRange('a', 'z'), //
                CharRange('\u00C0', '\u00D6'), //
                CharRange('\u00D8', '\u00F6'), //
                CharRange('\u00F8', '\u02FF'), //
                CharRange('\u0370', '\u037D'), //
                CharRange('\u037F', '\u1FFF'), //
                CharRange('\u200C', '\u200D'), //
                CharRange('\u2070', '\u218F'), //
                CharRange('\u2C00', '\u2FEF'), //
                CharRange('\u3001', '\uD7FF'), //
                CharRange('\uF900', '\uFDCF'), //
                CharRange('\uFDF0', '\uFFFD') //
        );
    }

    public Rule DIGIT() {
        return CharRange('0', '9');
    }

    public Rule COMMENT() {
        return Sequence('#', ZeroOrMore(Sequence(TestNot(EOL()), ANY)), EOL());
    }

    public Rule EOL() {
        return AnyOf("\n\r");
    }

    public Rule REFERENCE() {
        return StringWS("^^");
    }

    public Rule LESS_EQUAL() {
        return StringWS("<=");
    }

    public Rule GREATER_EQUAL() {
        return StringWS(">=");
    }

    public Rule NOT_EQUAL() {
        return StringWS("!=");
    }

    public Rule AND() {
        return StringWS("&&");
    }

    public Rule OR() {
        return StringWS("||");
    }

    public Rule OPEN_BRACE() {
        return ChWS('(');
    }

    public Rule CLOSE_BRACE() {
        return ChWS(')');
    }

    public Rule OPEN_CURLY_BRACE() {
        return ChWS('{');
    }

    public Rule CLOSE_CURLY_BRACE() {
        return ChWS('}');
    }

    public Rule OPEN_SQUARE_BRACE() {
        return ChWS('[');
    }

    public Rule CLOSE_SQUARE_BRACE() {
        return ChWS(']');
    }

    public Rule SEMICOLON() {
        return ChWS(';');
    }

    public Rule DOT() {
        return ChWS('.');
    }

    public Rule PLUS() {
        return ChWS('+');
    }

    public Rule MINUS() {
        return ChWS('-');
    }

    public Rule ASTERISK() {
        return Sequence(ChWS('*'), pushQuery(popQuery().setQueryStar()));
    }

    public Query popQuery() {
        debug("popQuery");
        return (Query) pop(0);
    }

    public boolean pushQuery(Query q) {
        debug("pushQuery");
        return push(0, q);
    }

    public Query peekQuery() {
        debug("peekQuery");
        return (Query) peek(0);
    }


    public Element popElement() {
        return ((Element) pop(0));
    }

    public Rule COMMA() {
        return ChWS(',');
    }

    public Rule NOT() {
        return ChWS('!');
    }

    public Rule DIVIDE() {
        return ChWS('/');
    }

    public Rule EQUAL() {
        return ChWS('=');
    }

    public Rule LESS_NO_COMMENT() {
        return Sequence(Ch('<'), ZeroOrMore(WS_NO_COMMENT()));
    }

    public Rule LESS() {
        return ChWS('<');
    }

    public Rule GREATER() {
        return ChWS('>');
    }
    // </Lexer>

    public Rule ChWS(char c) {
        return Sequence(Ch(c), WS());
    }

    public Rule StringWS(String s) {
        return Sequence(String(s), WS());
    }

    public Rule StringIgnoreCaseWS(String string) {
        return Sequence(IgnoreCase(string), WS());
    }

    public boolean my_push(Object o) {
        System.out.println(o.getClass());
        return push(o);

    }

    public Object my_peek() {
        return peek();

    }

    void debug(String calls) {
        System.out.println(calls);
    }
}