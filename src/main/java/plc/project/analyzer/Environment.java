package plc.project.analyzer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class Environment {

    public static final Map<String, Type> TYPES = Stream.of(
        Type.ANY,
        Type.NIL,
        Type.DYNAMIC,
        Type.BOOLEAN,
        Type.INTEGER,
        Type.DECIMAL,
        Type.CHARACTER,
        Type.STRING,
        Type.EQUATABLE,
        Type.COMPARABLE,
        Type.ITERABLE
    ).collect(Collectors.toMap(Type.Primitive::name, t -> t));

    public static Scope scope() {
        var scope = new Scope(null);
        //Helper variables for testing non-literal types;
        scope.declare("any", Type.ANY);
        scope.declare("dynamic", Type.DYNAMIC);
        scope.declare("equatable", Type.EQUATABLE);
        scope.declare("comparable", Type.COMPARABLE);
        scope.declare("iterable", Type.ITERABLE);
        //"Native" functions for printing.
        scope.declare("debug", new Type.Function(List.of(Type.ANY), Type.NIL));
        scope.declare("print", new Type.Function(List.of(Type.ANY), Type.NIL));
        //"list" has been removed, since our type system can't represent is (why?)
        //Helpers for testing variables, functions, and objects.
        scope.declare("variable", Type.STRING);
        scope.declare("function", new Type.Function(List.of(), Type.NIL));
        scope.declare("function_any", new Type.Function(List.of(Type.ANY), Type.ANY));
        scope.declare("function_string", new Type.Function(List.of(Type.STRING), Type.STRING));
        var prototype = new Type.ObjectType(Optional.of("Prototype"), new Scope(null));
        prototype.scope().declare("inherited_property", Type.STRING);
        prototype.scope().declare("inherited_method", new Type.Function(List.of(), Type.NIL));
        var object = new Type.ObjectType(Optional.of("Object"), new Scope(null));
        scope.declare("object", object);
        object.scope().declare("prototype", prototype);
        object.scope().declare("property", Type.STRING);
        object.scope().declare("method", new Type.Function(List.of(), Type.NIL));
        object.scope().declare("method_any", new Type.Function(List.of(Type.ANY), Type.ANY));
        object.scope().declare("method_string", new Type.Function(List.of(Type.STRING), Type.STRING));
        //Stdlib functions implemented for this assignment.
        scope.declare("sqrt", Analyzer.EnvironmentHooks.SQRT);
        scope.declare("range", Analyzer.EnvironmentHooks.RANGE);
        return scope;
    }

}
