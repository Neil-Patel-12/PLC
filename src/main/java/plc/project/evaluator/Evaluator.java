package plc.project.evaluator;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        RuntimeValue value = new RuntimeValue.Primitive(null);
        try {
            for (var stmt : ast.statements()) {
                value = visit(stmt);
            }
        } catch (Return returnValue) {
            throw new EvaluateException("RETURN cannot be used outside of a function.", ast);
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        if (scope.get(ast.name()).isPresent()) {
            throw new EvaluateException("Variable is already defined in this scope.", ast);
        }
        var value = ast.value().isPresent()
                ? visit(ast.value().get())
                : new RuntimeValue.Primitive(null);
        scope.define(ast.name(), value);
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        if (scope.get(ast.name()).isPresent()) {
            throw new EvaluateException("Function is already defined in this scope.", ast);
        }
        if (ast.parameters().stream().distinct().count() != ast.parameters().size()) {
            throw new EvaluateException("Duplicate parameter name in function definition.", ast);
        }

        Scope definitionScope = scope;
        RuntimeValue.Function function = new RuntimeValue.Function(ast.name(), arguments -> {
            if (arguments.size() != ast.parameters().size()) {
                throw new EvaluateException("Invalid argument count when calling function.", ast);
            }

            Scope previous = this.scope;
            try {
                this.scope = new Scope(definitionScope);
                for (int i = 0; i < ast.parameters().size(); i++) {
                    this.scope.define(ast.parameters().get(i), arguments.get(i));
                }

                this.scope = new Scope(this.scope);
                RuntimeValue value = new RuntimeValue.Primitive(null);
                for (var stmt : ast.body()) {
                    value = visit(stmt);
                }
                return value;
            } catch (Return returnValue) {
                return returnValue.value;
            } finally {
                this.scope = previous;
            }
        });

        scope.define(ast.name(), function);
        return function;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        var condition = visit(ast.condition());
        var bool = requireType(condition, Boolean.class)
                .orElseThrow(() -> new EvaluateException("IF condition must be a boolean.", ast.condition()));

        Scope previous = scope;
        try {
            scope = new Scope(previous);
            RuntimeValue value = new RuntimeValue.Primitive(null);
            var body = bool ? ast.thenBody() : ast.elseBody();
            for (var stmt : body) {
                value = visit(stmt);
            }
            return value;
        } finally {
            scope = previous;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        var expression = visit(ast.expression());
        var iterable = requireType(expression, RuntimeValue.Primitive.class)
                .map(RuntimeValue.Primitive::value)
                .filter(Iterable.class::isInstance)
                .map(value -> (Iterable<?>) value)
                .orElseThrow(() -> new EvaluateException("FOR expression must be an iterable primitive.", ast.expression()));

        for (Object element : iterable) {
            if (!(element instanceof RuntimeValue runtimeValue)) {
                throw new EvaluateException("FOR iterable elements must be RuntimeValue values.", ast.expression());
            }
            Scope previous = scope;
            try {
                scope = new Scope(previous);
                scope.define(ast.name(), runtimeValue);
                scope = new Scope(scope);
                for (var stmt : ast.body()) {
                    visit(stmt);
                }
            } finally {
                scope = previous;
            }
        }
        return new RuntimeValue.Primitive(null);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        RuntimeValue value = ast.value().isPresent()
                ? visit(ast.value().get())
                : new RuntimeValue.Primitive(null);
        throw new Return(value);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        RuntimeValue value = visit(ast.value());
        if (ast.expression() instanceof Ast.Expr.Variable variable) {
            if (scope.resolve(variable.name()).isEmpty()) {
                throw new EvaluateException("Undefined variable.", variable);
            }
            scope.assign(variable.name(), value);
            return value;
        } else if (ast.expression() instanceof Ast.Expr.Property property) {
            var receiver = visit(property.receiver());
            var object = requireType(receiver, RuntimeValue.ObjectValue.class)
                    .orElseThrow(() -> new EvaluateException("Property assignment receiver must be an object.", property.receiver()));
            if (resolveProperty(object, property.name()).isEmpty()) {
                throw new EvaluateException("Undefined property.", property);
            }
            if (object.scope().get(property.name()).isPresent()) {
                object.scope().assign(property.name(), value);
            } else {
                object.scope().define(property.name(), value);
            }
            return value;
        }
        throw new EvaluateException("Invalid assignment target.", ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        return switch (ast.operator()) {
            case "AND" -> {
                var left = requireType(visit(ast.left()), Boolean.class)
                        .orElseThrow(() -> new EvaluateException("AND left operand must be a boolean.", ast.left()));
                if (!left) {
                    yield new RuntimeValue.Primitive(false);
                }
                var right = requireType(visit(ast.right()), Boolean.class)
                        .orElseThrow(() -> new EvaluateException("AND right operand must be a boolean.", ast.right()));
                yield new RuntimeValue.Primitive(right);
            }
            case "OR" -> {
                var left = requireType(visit(ast.left()), Boolean.class)
                        .orElseThrow(() -> new EvaluateException("OR left operand must be a boolean.", ast.left()));
                if (left) {
                    yield new RuntimeValue.Primitive(true);
                }
                var right = requireType(visit(ast.right()), Boolean.class)
                        .orElseThrow(() -> new EvaluateException("OR right operand must be a boolean.", ast.right()));
                yield new RuntimeValue.Primitive(right);
            }
            case "+" -> {
                var left = visit(ast.left());
                var right = visit(ast.right());
                if (requireType(left, String.class).isPresent() || requireType(right, String.class).isPresent()) {
                    yield new RuntimeValue.Primitive(left.print() + right.print());
                }
                yield arithmetic(ast, left, right, "+");
            }
            case "-", "*", "/" -> {
                var left = visit(ast.left());
                if (requireType(left, BigInteger.class).isEmpty() && requireType(left, BigDecimal.class).isEmpty()) {
                    throw new EvaluateException("Arithmetic left operand must be an Integer/Decimal.", ast.left());
                }
                var right = visit(ast.right());
                yield arithmetic(ast, left, right, ast.operator());
            }
            case "==" -> new RuntimeValue.Primitive(Objects.equals(visit(ast.left()), visit(ast.right())));
            case "!=" -> new RuntimeValue.Primitive(!Objects.equals(visit(ast.left()), visit(ast.right())));
            case "<", "<=", ">", ">=" -> {
                var left = requireType(visit(ast.left()), RuntimeValue.Primitive.class)
                        .map(RuntimeValue.Primitive::value)
                        .filter(Comparable.class::isInstance)
                        .orElseThrow(() -> new EvaluateException("Comparison left operand must be comparable.", ast.left()));
                var right = requireType(visit(ast.right()), RuntimeValue.Primitive.class)
                        .map(RuntimeValue.Primitive::value)
                        .orElseThrow(() -> new EvaluateException("Comparison right operand must be primitive.", ast.right()));
                if (!left.getClass().equals(right.getClass())) {
                    throw new EvaluateException("Comparison operands must have the same type.", ast.right());
                }
                int cmp = ((Comparable<Object>) left).compareTo(right);
                boolean result = switch (ast.operator()) {
                    case "<" -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    case ">" -> cmp > 0;
                    case ">=" -> cmp >= 0;
                    default -> throw new AssertionError(ast.operator());
                };
                yield new RuntimeValue.Primitive(result);
            }
            default -> throw new EvaluateException("Unsupported binary operator.", ast);
        };
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        return scope.resolve(ast.name())
                .orElseThrow(() -> new EvaluateException("Undefined variable.", ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        var receiver = visit(ast.receiver());
        var object = requireType(receiver, RuntimeValue.ObjectValue.class)
                .orElseThrow(() -> new EvaluateException("Property receiver must be an object.", ast.receiver()));
        return resolveProperty(object, ast.name())
                .orElseThrow(() -> new EvaluateException("Undefined property.", ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        var value = scope.resolve(ast.name())
                .orElseThrow(() -> new EvaluateException("Undefined function.", ast));
        var function = requireType(value, RuntimeValue.Function.class)
                .orElseThrow(() -> new EvaluateException("Identifier is not a function.", ast));

        var arguments = new ArrayList<RuntimeValue>();
        for (var argument : ast.arguments()) {
            arguments.add(visit(argument));
        }
        return function.definition().invoke(arguments);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        var receiver = visit(ast.receiver());
        var object = requireType(receiver, RuntimeValue.ObjectValue.class)
                .orElseThrow(() -> new EvaluateException("Method receiver must be an object.", ast.receiver()));
        var functionValue = resolveProperty(object, ast.name())
                .orElseThrow(() -> new EvaluateException("Undefined method.", ast));
        var function = requireType(functionValue, RuntimeValue.Function.class)
                .orElseThrow(() -> new EvaluateException("Property is not a method.", ast));

        var arguments = new ArrayList<RuntimeValue>();
        arguments.add(object);
        for (var argument : ast.arguments()) {
            arguments.add(visit(argument));
        }
        return function.definition().invoke(arguments);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        var object = new RuntimeValue.ObjectValue(ast.name(), new Scope(null));

        for (var field : ast.fields()) {
            if (object.scope().get(field.name()).isPresent()) {
                throw new EvaluateException("Field is already defined in this object.", field);
            }
            RuntimeValue value = field.value().isPresent()
                    ? visit(field.value().get())
                    : new RuntimeValue.Primitive(null);
            object.scope().define(field.name(), value);
        }

        for (var method : ast.methods()) {
            if (object.scope().get(method.name()).isPresent()) {
                throw new EvaluateException("Method is already defined in this object.", method);
            }
            if (method.parameters().contains("this")) {
                throw new EvaluateException("Method parameter cannot be named this.", method);
            }
            if (method.parameters().stream().distinct().count() != method.parameters().size()) {
                throw new EvaluateException("Duplicate method parameter name.", method);
            }

            Scope definitionScope = scope;
            RuntimeValue.Function function = new RuntimeValue.Function(method.name(), arguments -> {
                if (arguments.size() != method.parameters().size() + 1) {
                    throw new EvaluateException("Invalid argument count for method call.", method);
                }

                Scope previous = this.scope;
                try {
                    this.scope = new Scope(definitionScope);
                    this.scope.define("this", arguments.getFirst());
                    for (int i = 0; i < method.parameters().size(); i++) {
                        this.scope.define(method.parameters().get(i), arguments.get(i + 1));
                    }

                    this.scope = new Scope(this.scope);
                    RuntimeValue value = new RuntimeValue.Primitive(null);
                    for (var stmt : method.body()) {
                        value = visit(stmt);
                    }
                    return value;
                } catch (Return returnValue) {
                    return returnValue.value;
                } finally {
                    this.scope = previous;
                }
            });
            object.scope().define(method.name(), function);
        }

        return object;
    }

    private RuntimeValue arithmetic(Ast.Expr.Binary ast, RuntimeValue left, RuntimeValue right, String operator) throws EvaluateException {
        var leftInteger = requireType(left, BigInteger.class);
        if (leftInteger.isPresent()) {
            var rightInteger = requireType(right, BigInteger.class)
                    .orElseThrow(() -> new EvaluateException("Right operand must be BigInteger.", ast.right()));
            return switch (operator) {
                case "+" -> new RuntimeValue.Primitive(leftInteger.get().add(rightInteger));
                case "-" -> new RuntimeValue.Primitive(leftInteger.get().subtract(rightInteger));
                case "*" -> new RuntimeValue.Primitive(leftInteger.get().multiply(rightInteger));
                case "/" -> {
                    if (rightInteger.equals(BigInteger.ZERO)) {
                        throw new EvaluateException("Cannot divide by zero.", ast.right());
                    }
                    yield new RuntimeValue.Primitive(leftInteger.get().divide(rightInteger));
                }
                default -> throw new EvaluateException("Unsupported arithmetic operator.", ast);
            };
        }

        var leftDecimal = requireType(left, BigDecimal.class)
                .orElseThrow(() -> new EvaluateException("Left operand must be BigInteger/BigDecimal.", ast.left()));
        var rightDecimal = requireType(right, BigDecimal.class)
                .orElseThrow(() -> new EvaluateException("Right operand must be BigDecimal.", ast.right()));
        return switch (operator) {
            case "+" -> new RuntimeValue.Primitive(leftDecimal.add(rightDecimal));
            case "-" -> new RuntimeValue.Primitive(leftDecimal.subtract(rightDecimal));
            case "*" -> new RuntimeValue.Primitive(leftDecimal.multiply(rightDecimal));
            case "/" -> {
                if (rightDecimal.compareTo(BigDecimal.ZERO) == 0) {
                    throw new EvaluateException("Cannot divide by zero.", ast.right());
                }
                yield new RuntimeValue.Primitive(leftDecimal.divide(rightDecimal, RoundingMode.HALF_EVEN));
            }
            default -> throw new EvaluateException("Unsupported arithmetic operator.", ast);
        };
    }

    private Optional<RuntimeValue> resolveProperty(RuntimeValue.ObjectValue object, String name) {
        var local = object.scope().get(name);
        if (local.isPresent()) {
            return local;
        }

        var prototype = object.scope().get("prototype")
                .flatMap(value -> requireType(value, RuntimeValue.ObjectValue.class));
        return prototype.flatMap(value -> resolveProperty(value, name));
    }

    private static final class Return extends RuntimeException {
        private final RuntimeValue value;

        private Return(RuntimeValue value) {
            this.value = value;
        }
    }

    /**
     * Helper function for extracting RuntimeValues of specific types. If type
     * is a subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value must be a {@link RuntimeValue.Primitive} and
     * the check applies to the primitive value.
     */
    private static <T> Optional<T> requireType(RuntimeValue value, Class<T> type) {
        //To be discussed in lecture
        Optional<Object> unwrapped = RuntimeValue.class.isAssignableFrom(type)
            ? Optional.of(value)
            : requireType(value, RuntimeValue.Primitive.class).map(RuntimeValue.Primitive::value);
        return (Optional<T>) unwrapped.filter(type::isInstance); //cast checked by isInstance
    }

    public static class Environment {

        public static RuntimeValue sqrt(List<RuntimeValue> arguments) throws EvaluateException {
            if (arguments.size() != 1) {
                throw new EvaluateException("Expected sqrt to be called with 1 argument.");
            }
            var argument = arguments.getFirst();
            var integer = requireType(argument, BigInteger.class);
            if (integer.isPresent()) {
                if (integer.get().compareTo(BigInteger.ZERO) < 0) {
                    throw new EvaluateException("Cannot take sqrt of a negative integer.");
                }
                return new RuntimeValue.Primitive(integer.get().sqrt());
            }

            var decimal = requireType(argument, BigDecimal.class)
                    .orElseThrow(() -> new EvaluateException("sqrt argument must be an Integer/Decimal."));
            if (decimal.compareTo(BigDecimal.ZERO) < 0) {
                throw new EvaluateException("Cannot take sqrt of a negative decimal.");
            }
            return new RuntimeValue.Primitive(decimal.sqrt(MathContext.DECIMAL64));
        }

        public static RuntimeValue range(List<RuntimeValue> arguments) throws EvaluateException {
            if (arguments.size() != 2) {
                throw new EvaluateException("Expected range to be called with 2 arguments.");
            }
            var start = requireType(arguments.get(0), BigInteger.class)
                    .orElseThrow(() -> new EvaluateException("range start must be an integer."));
            var end = requireType(arguments.get(1), BigInteger.class)
                    .orElseThrow(() -> new EvaluateException("range end must be an integer."));
            if (start.compareTo(end) > 0) {
                throw new EvaluateException("range requires start <= end.");
            }

            var list = new ArrayList<RuntimeValue>();
            for (BigInteger value = start; value.compareTo(end) < 0; value = value.add(BigInteger.ONE)) {
                list.add(new RuntimeValue.Primitive(value));
            }
            return new RuntimeValue.Primitive(list);
        }

    }

}
