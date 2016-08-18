package it.polimi.sr.mql.streams;

import lombok.*;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Node_URI;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Riccardo on 12/08/16.
 */
@Data
@NoArgsConstructor
@ToString(exclude = {"regex", "p"})
@RequiredArgsConstructor
public class Window {

    @NonNull
    private Node iri;
    private Integer beta;
    private Integer omega;
    private String unit_omega;
    private String unit_beta;
    private Stream stream;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final private String regex = "([0-9]+)\\s*(ms|s|m|h|d|GRAPH|TRIPLES)";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final private Pattern p = Pattern.compile(regex);

    private WindowType type = WindowType.Logical;

    public Window addConstrain(String match) {
        //TODO hide visibility out of the package
        Matcher matcher = p.matcher(match);
        if (matcher.find()) {
            MatchResult res = matcher.toMatchResult();
            this.beta = this.omega = Integer.parseInt(res.group(1));
            this.unit_beta = this.unit_omega = res.group(2);
            if ("GRAPH".equals(unit_omega) || "TRIPLE".equals(unit_omega)) {
                this.type = WindowType.Physical;
            }

        }
        return this;
    }

    public Window addSlide(String match) {
        //TODO hide visibility out of the package
        Matcher matcher = p.matcher(match);
        if (matcher.find()) {
            MatchResult res = matcher.toMatchResult();
            this.beta = Integer.parseInt(res.group(1));
            this.unit_beta = res.group(2);
            if ("GRAPH".equals(unit_beta) || "TRIPLE".equals(unit_beta)) {
                this.type = WindowType.Physical;
            }
        }
        return this;
    }

    public Window addStreamUri(Node_URI uri) {
        //TODO hide visibility out of the package
        if (stream == null) {
            stream = new Stream(uri);
        }
        stream.setIri(uri);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Window window = (Window) o;

        if (iri != null ? !iri.equals(window.iri) : window.iri != null) return false;
        if (beta != null ? !beta.equals(window.beta) : window.beta != null) return false;
        if (omega != null ? !omega.equals(window.omega) : window.omega != null) return false;
        if (unit_omega != null ? !unit_omega.equals(window.unit_omega) : window.unit_omega != null) return false;
        if (unit_beta != null ? !unit_beta.equals(window.unit_beta) : window.unit_beta != null) return false;
        if (stream != null ? !stream.equals(window.stream) : window.stream != null) return false;
        if (regex != null ? !regex.equals(window.regex) : window.regex != null) return false;
        if (p != null ? !p.equals(window.p) : window.p != null) return false;
        return type == window.type;

    }

    @Override
    public int hashCode() {
        int result = iri != null ? iri.hashCode() : 0;
        result = 31 * result + (beta != null ? beta.hashCode() : 0);
        result = 31 * result + (omega != null ? omega.hashCode() : 0);
        result = 31 * result + (unit_omega != null ? unit_omega.hashCode() : 0);
        result = 31 * result + (unit_beta != null ? unit_beta.hashCode() : 0);
        result = 31 * result + (stream != null ? stream.hashCode() : 0);
        result = 31 * result + (regex != null ? regex.hashCode() : 0);
        result = 31 * result + (p != null ? p.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }


    public enum WindowType {
        Logical, Physical;
    }


}