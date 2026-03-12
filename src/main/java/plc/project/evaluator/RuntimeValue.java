package plc.project.evaluator;

import java.util.List;
import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public sealed interface RuntimeValue {

    record Primitive(
        Object value
    ) implements RuntimeValue {

        @Override
        public String toString() {
            var clazz = value != null ? value.getClass().getSimpleName() : "N/A";
            return "Primitive[value=" + value + ", class=" + clazz + "]";
        }

    }

    record Function(
        String name,
        Definition definition
    ) implements RuntimeValue {

        @FunctionalInterface
        public interface Definition {
            RuntimeValue invoke(List<RuntimeValue> arguments) throws EvaluateException;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Function function
                && name.equals(function.name);
        }

    }

    //Using "ObjectValue" to avoid confusion with Java's "Object"
    record ObjectValue(
        Optional<String> name, //Think of this like a "tag"
        Scope scope
    ) implements RuntimeValue {

        @Override
        public boolean equals(Object obj) {
            //Note: Compare
            return obj instanceof ObjectValue object &&
                name.equals(object.name) &&
                scope.collect().equals(object.scope.collect());
        }

    }

    default String print() {
        return switch (this) {
            case Primitive primitive -> switch (primitive.value) {
                case null -> "NIL";
                case Boolean b -> b.toString().toUpperCase();
                default -> primitive.value.toString();
            };
            case Function function -> "DEF " + function.name + "(?) DO ... END";
            case ObjectValue object -> {
                var builder = new StringBuilder();
                builder.append("OBJECT ");
                builder.append(object.name.map(n -> n + " ").orElse(""));
                builder.append("DO");
                object.scope.collect().forEach((key, value) -> {
                    if (value instanceof Function) {
                        builder.append("\n    ").append(value.print());
                    } else {
                        var printed = value.print().replaceAll("\n *(?=LET|DEF|END)", " "); //inline nested objects
                        builder.append("\n    LET ").append(key).append(" = ").append(printed).append(";");
                    }
                });
                builder.append("\nEND");
                yield builder.toString();
            }
        };
    }

}
