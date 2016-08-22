package it.polimi.sr.rsp;

import it.polimi.sr.rsp.streams.Register;
import it.polimi.sr.rsp.streams.Window;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryException;
import org.apache.jena.sparql.core.Prologue;
import org.apache.jena.sparql.syntax.ElementNamedGraph;

import java.util.*;

/**
 * Created by Riccardo on 05/08/16.
 */
@Data
@NoArgsConstructor
public class RSPQuery extends SPARQLQuery {

    private Map<Node, Window> namedwindows;
    private Set<Window> windows;
    private List<ElementNamedGraph> windowGraphElements;
    private Register header;

    public RSPQuery(Prologue prologue) {
        super(prologue);
    }

    public RSPQuery setSelectQuery() {
        setQuerySelectType();
        return this;
    }

    public Query getQ() {
        return this;
    }

    public RSPQuery addNamedWindow(Window nw) {
        if (namedwindows == null)
            namedwindows = new HashMap<Node, Window>();
        if (namedwindows.containsKey(nw.getIri()))
            throw new QueryException("Window [" + nw.getIri() +
                    " ] already opened on a stream: " + namedwindows.get(nw.getIri()));

        addNamedGraphURI(nw.getIri());
        namedwindows.put(nw.getIri(), nw);
        return this;
    }

    public RSPQuery addWindow(Window w) {

        if (w.isNamed()) {
            return addNamedWindow(w);
        }

        if (windows == null)
            windows = new HashSet<Window>();
        if (windows.contains(w))
            throw new QueryException("Window already opened on default stream: " + w.getStream().getIri());

        addGraphURI(w.getStream().getIri());
        windows.add(w);
        return this;
    }

    public RSPQuery addElement(ElementNamedGraph elm) {
        if (windowGraphElements == null) {
            windowGraphElements = new ArrayList<ElementNamedGraph>();
        }
        windowGraphElements.add(elm);
        return this;
    }

    public RSPQuery setRegister(Register register) {
        this.header = register;
        return this;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}