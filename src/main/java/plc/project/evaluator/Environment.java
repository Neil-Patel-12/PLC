package plc.project.evaluator;

import java.util.List;
import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class Environment {

    public static Scope scope() {
        var scope = new Scope(null);
        //"Native" functions for printing and creating lists.
        scope.define("debug", new RuntimeValue.Function("debug", Environment::debug));
        scope.define("print", new RuntimeValue.Function("print", Environment::print));
        scope.define("list", new RuntimeValue.Function("list", Environment::list));
        //Helper functions for testing variables, functions, and objects.
        scope.define("variable", new RuntimeValue.Primitive("variable"));
        scope.define("function", new RuntimeValue.Function("function", Environment::function));
        var object = new RuntimeValue.ObjectValue(Optional.of("Object"), new Scope(null));
        scope.define("object", object);
        object.scope().define("property", new RuntimeValue.Primitive("property"));
        object.scope().define("method", new RuntimeValue.Function("method", Environment::method));
        var prototype = new RuntimeValue.ObjectValue(Optional.of("Prototype"), new Scope(null));
        prototype.scope().define("inherited_property", new RuntimeValue.Primitive("inherited_property"));
        prototype.scope().define("inherited_method", new RuntimeValue.Function("inherited_method", Environment::method));
        object.scope().define("prototype", prototype);
        //Stdlib functions implemented for this assignment.
        scope.define("sqrt", new RuntimeValue.Function("sqrt", Evaluator.Environment::sqrt));
        scope.define("range", new RuntimeValue.Function("range", Evaluator.Environment::range));
        return scope;
    }

    /**
     * Prints the raw RuntimeValue.toString() result.
     */
    private static RuntimeValue debug(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected debug to be called with 1 argument.");
        }
        System.out.println(arguments.getFirst());
        return new RuntimeValue.Primitive(null);
    }

    /**
     * Prints a formatted RuntimeValue.
     */
    public static RuntimeValue print(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected print to be called with 1 argument.");
        }
        System.out.println(arguments.getFirst().print());
        return new RuntimeValue.Primitive(null);
    }

    /**
     * Returns a List value containing all arguments.
     */
    private static RuntimeValue list(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    /**
     * Returns a list of all function arguments.
     */
    private static RuntimeValue function(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    /**
     * Returns a list of all method arguments. Question: why the difference?
     */
    private static RuntimeValue method(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments.subList(1, arguments.size()));
    }

}
