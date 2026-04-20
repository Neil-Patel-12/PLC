package plc.project.analyzer;

import java.util.List;
import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public sealed interface Type {

    Primitive ANY = new Primitive("Any");
    Primitive DYNAMIC = new Primitive("Dynamic");
    Primitive NIL = new Primitive("Nil");
    Primitive BOOLEAN = new Primitive("Boolean");
    Primitive INTEGER = new Primitive("Integer");
    Primitive DECIMAL = new Primitive("Decimal");
    Primitive CHARACTER = new Primitive("Character");
    Primitive STRING = new Primitive("String");
    Primitive EQUATABLE = new Primitive("Equatable");
    Primitive COMPARABLE = new Primitive("Comparable");
    Primitive ITERABLE = new Primitive("Iterable");

    record Primitive(
        String name
    ) implements Type {}

    record Function(
        List<Type> parameters,
        Type returns
    ) implements Type {}

    //Using "ObjectType" to avoid confusion with Java's "Object"
    record ObjectType(
        Optional<String> name, //for debugging
        Scope scope
    ) implements Type {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ObjectType object &&
                name.equals(object.name) &&
                //Note: Not strictly accurate, but sufficient for our needs.
                scope.collect().equals(object.scope.collect());
        }

        @Override
        public String toString() {
            return "Object[name=" + name + ", scope=" + scope.collect() + "]";
        }

    }

    default boolean isSubtypeOf(Type supertype) {
        return Analyzer.TypeHooks.isSubtypeOf(this, supertype);
    }

}
