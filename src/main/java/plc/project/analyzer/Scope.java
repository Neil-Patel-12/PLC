package plc.project.analyzer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class Scope {

    private final Scope parent;
    private final Map<String, Type> variables = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    /**
     * Returns the type of a variable declared specifically in the *current*
     * scope, i.e. does not perform "full" scope resolution.
     */
    public Optional<Type> get(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    /**
     * Returns the type of a variable defined in this scope, i.e. includes
     * "inherited" values from parent scopes and thus performs "full" scope resolution.
     */
    public Optional<Type> resolve(String name) {
        if (variables.containsKey(name)) {
            return Optional.of(variables.get(name));
        } else if (parent != null) {
            return parent.resolve(name);
        } else {
            return Optional.empty();
        }
    }

    public void declare(String name, Type type) {
        if (!variables.containsKey(name)) {
            variables.put(name, type);
        } else {
            throw new IllegalStateException("Variable is already defined.");
        }
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append("Scope[");
        builder.append("parent=").append(parent).append(", ");
        builder.append("variables=Map["); //format Map like record for prettify
        variables.forEach((key, value) -> {
            builder.append(key).append("=").append(value).append(", ");
        });
        builder.append("]");
        return builder.toString();
    }

    //IMPORTANT: For use in Type.ObjectType, NOT the Analyzer.
    public Map<String, Type> collect() {
        var map = parent != null ? parent.collect() : new LinkedHashMap<String, Type>();
        map.putAll(variables);
        return map;
    }

}
