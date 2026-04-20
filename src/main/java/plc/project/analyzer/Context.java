package plc.project.analyzer;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class Context {

    private final Scope scope;
    private final Optional<Type.Function> function;
    private Set<String> uninitialized;
    private boolean returns;

    public Context(Scope scope, Optional<Type.Function> function, Set<String> uninitialized, boolean returns) {
        this.scope = scope;
        this.function = function;
        this.uninitialized = uninitialized;
        this.returns = returns;
    }

    public Context(Context parent) {
        this.scope = new Scope(parent.scope);
        this.function = parent.function;
        this.uninitialized = new HashSet<>(parent.uninitialized);
        this.returns = parent.returns;
    }

    public Scope scope() { return scope; }
    public Optional<Type.Function> function() { return function; }
    public Set<String> uninitialized() { return uninitialized; }
    public boolean returns() { return returns; }
    public void returns(boolean returns) { this.returns = returns; }

    public void merge(List<Context> children) {
        this.uninitialized = Analyzer.ContextHooks.mergeUninitialized(children.stream()
            .filter(c -> !c.returns) //only non-returning branches matter for considering initialization (why?).
            .map(c -> c.uninitialized)
            .toList());
        this.returns = Analyzer.ContextHooks.mergeReturns(children.stream().map(Context::returns).toList());
    }

}
