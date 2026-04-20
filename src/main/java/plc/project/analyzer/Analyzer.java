package plc.project.analyzer;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;

public final class Analyzer implements Ast.Visitor<Type, AnalyzeException> {

    private Context context;

    public Analyzer(Scope scope) {
        this.context = new Context(scope, Optional.empty(), new HashSet<>(), false);
    }

    public Context getContext() {
        return context;
    }

    @Override
    public Type visit(Ast.Source ast) throws AnalyzeException {
        Type type = Type.NIL;
        for (var statement : ast.statements()) {
            type = visit(statement);
        }
        return type;
    }

    @Override
    public Type visit(Ast.Stmt.Let ast) throws AnalyzeException {
        if (context.scope().get(ast.name()).isPresent()) {
            throw new AnalyzeException("Variable '" + ast.name() + "' is already defined in this scope.", ast);
        }

        Optional<Type> valueTypeOpt = Optional.empty();
        if (ast.value().isPresent()) {
            valueTypeOpt = Optional.of(visit(ast.value().get()));
        }

        // Determine declared variable type:
        // 1. Explicit type annotation  → look up in Environment.TYPES
        // 2. No annotation, value present → infer from value
        // 3. Neither → Dynamic
        Type varType;
        if (ast.type().isPresent()) {
            var typeName = ast.type().get();
            varType = Optional.ofNullable(Environment.TYPES.get(typeName))
                    .orElseThrow(() -> new AnalyzeException("Undefined type '" + typeName + "'.", ast));
            if (valueTypeOpt.isPresent() && !valueTypeOpt.get().isSubtypeOf(varType)) {
                throw new AnalyzeException(
                        "Value type is not a subtype of the declared variable type.", ast);
            }
        } else {
            varType = valueTypeOpt.orElse(Type.DYNAMIC);
        }

        context.scope().declare(ast.name(), varType);

        if (ast.value().isEmpty()) {
            context.uninitialized().add(ast.name());
        }

        return varType;
    }

    @Override
    public Type visit(Ast.Stmt.Def ast) throws AnalyzeException {
        if (context.scope().get(ast.name()).isPresent()) {
            throw new AnalyzeException("Function '" + ast.name() + "' is already defined in this scope.", ast);
        }
        if (ast.parameters().stream().distinct().count() != ast.parameters().size()) {
            throw new AnalyzeException("Duplicate parameter name in function definition.", ast);
        }

        var paramTypes = new ArrayList<Type>();
        for (var paramTypeOpt : ast.parameterTypes()) {
            if (paramTypeOpt.isPresent()) {
                var typeName = paramTypeOpt.get();
                var paramType = Optional.ofNullable(Environment.TYPES.get(typeName))
                        .orElseThrow(() -> new AnalyzeException("Undefined parameter type '" + typeName + "'.", ast));
                paramTypes.add(paramType);
            } else {
                paramTypes.add(Type.DYNAMIC);
            }
        }

        Type returnType;
        if (ast.returnType().isPresent()) {
            var typeName = ast.returnType().get();
            returnType = Optional.ofNullable(Environment.TYPES.get(typeName))
                    .orElseThrow(() -> new AnalyzeException("Undefined return type '" + typeName + "'.", ast));
        } else {
            returnType = Type.DYNAMIC;
        }

        var funcType = new Type.Function(List.copyOf(paramTypes), returnType);

        context.scope().declare(ast.name(), funcType);

        var paramScope = new Scope(context.scope());
        for (int i = 0; i < ast.parameters().size(); i++) {
            paramScope.declare(ast.parameters().get(i), paramTypes.get(i));
        }

        var savedContext = this.context;
        this.context = new Context(new Scope(paramScope), Optional.of(funcType), new HashSet<>(), false);

        for (var stmt : ast.body()) {
            visit(stmt);
        }

        boolean bodyReturns = this.context.returns();
        this.context = savedContext;

        // If the return type is concrete (not Dynamic/Nil), every path must return
        if (!returnType.equals(Type.DYNAMIC) && !returnType.equals(Type.NIL) && !bodyReturns) {
            throw new AnalyzeException("Function '" + ast.name() + "' does not always return a value.", ast);
        }

        return funcType;
    }

    @Override
    public Type visit(Ast.Stmt.If ast) throws AnalyzeException {
        var condType = visit(ast.condition());
        if (!condType.isSubtypeOf(Type.BOOLEAN)) {
            throw new AnalyzeException("IF condition must be a Boolean.", ast.condition());
        }

        var savedContext = this.context;

        this.context = new Context(
                new Scope(savedContext.scope()),
                savedContext.function(),
                new HashSet<>(savedContext.uninitialized()),
                false
        );
        for (var stmt : ast.thenBody()) visit(stmt);
        var thenUninitialized = new HashSet<>(this.context.uninitialized());
        var thenReturns      = this.context.returns();

        this.context = new Context(
                new Scope(savedContext.scope()),
                savedContext.function(),
                new HashSet<>(savedContext.uninitialized()),
                false
        );
        for (var stmt : ast.elseBody()) visit(stmt);
        var elseUninitialized = new HashSet<>(this.context.uninitialized());
        var elseReturns       = this.context.returns();

        var mergedUninitialized = ContextHooks.mergeUninitialized(List.of(thenUninitialized, elseUninitialized));
        var mergedReturns       = ContextHooks.mergeReturns(List.of(thenReturns, elseReturns));

        this.context = new Context(
                savedContext.scope(),
                savedContext.function(),
                mergedUninitialized,
                savedContext.returns() || mergedReturns
        );

        return Type.DYNAMIC;
    }

    @Override
    public Type visit(Ast.Stmt.For ast) throws AnalyzeException {
        var exprType = visit(ast.expression());
        if (!exprType.isSubtypeOf(Type.ITERABLE)) {
            throw new AnalyzeException("FOR expression must be Iterable.", ast.expression());
        }

        var savedContext = this.context;

        var forScope = new Scope(savedContext.scope());
        forScope.declare(ast.name(), Type.INTEGER);

        this.context = new Context(
                new Scope(forScope),
                savedContext.function(),
                new HashSet<>(savedContext.uninitialized()),
                false
        );
        for (var stmt : ast.body()) {
            visit(stmt);
        }

        this.context = savedContext;
        return Type.NIL;
    }

    @Override
    public Type visit(Ast.Stmt.Return ast) throws AnalyzeException {
        if (context.function().isEmpty()) {
            throw new AnalyzeException("RETURN used outside of a function.", ast);
        }

        var valueType        = ast.value().isPresent() ? visit(ast.value().get()) : Type.NIL;
        var functionRetType  = context.function().get().returns();

        if (!valueType.isSubtypeOf(functionRetType)) {
            throw new AnalyzeException(
                    "Return value type is not a subtype of the function's declared return type.", ast);
        }

        this.context = new Context(
                context.scope(), context.function(), context.uninitialized(), true);

        return valueType;
    }

    @Override
    public Type visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        return visit(ast.expression());
    }

    @Override
    public Type visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        var valueType = visit(ast.value());

        if (ast.expression() instanceof Ast.Expr.Variable variable) {
            var varType = context.scope().resolve(variable.name())
                    .orElseThrow(() -> new AnalyzeException("Undefined variable '" + variable.name() + "'.", variable));
            if (!valueType.isSubtypeOf(varType)) {
                throw new AnalyzeException(
                        "Assignment value type is not a subtype of the variable's type.", ast);
            }
            context.uninitialized().remove(variable.name());
            return valueType;

        } else if (ast.expression() instanceof Ast.Expr.Property property) {
            var receiverType = visit(property.receiver());
            if (receiverType.equals(Type.DYNAMIC)) {
                return valueType;
            }
            if (!(receiverType instanceof Type.ObjectType objectType)) {
                throw new AnalyzeException(
                        "Property assignment receiver must be an object.", property.receiver());
            }
            var propType = resolveProperty(objectType, property.name())
                    .orElseThrow(() -> new AnalyzeException("Undefined property '" + property.name() + "'.", property));
            if (!valueType.isSubtypeOf(propType)) {
                throw new AnalyzeException(
                        "Assignment value type is not a subtype of the property's type.", ast);
            }
            return valueType;
        }

        throw new AnalyzeException("Invalid assignment target.", ast.expression());
    }

    @Override
    public Type visit(Ast.Expr.Literal ast) throws AnalyzeException {
        return switch (ast.value()) {
            case null -> Type.NIL;
            case Boolean _ -> Type.BOOLEAN;
            case BigInteger _ -> Type.INTEGER;
            case BigDecimal _ -> Type.DECIMAL;
            case Character _ -> Type.CHARACTER;
            case String _ -> Type.STRING;
            default -> throw new AssertionError(ast.value().getClass());
        };
    }

    @Override
    public Type visit(Ast.Expr.Group ast) throws AnalyzeException {
        return visit(ast.expression());
    }

    @Override
    public Type visit(Ast.Expr.Binary ast) throws AnalyzeException {
        return switch (ast.operator()) {

            case "AND", "OR" -> {
                var leftType  = visit(ast.left());
                var rightType = visit(ast.right());
                if (!leftType.isSubtypeOf(Type.BOOLEAN)) {
                    throw new AnalyzeException(
                            "Left operand of " + ast.operator() + " must be Boolean.", ast.left());
                }
                if (!rightType.isSubtypeOf(Type.BOOLEAN)) {
                    throw new AnalyzeException(
                            "Right operand of " + ast.operator() + " must be Boolean.", ast.right());
                }
                yield Type.BOOLEAN;
            }

            case "+" -> {
                var leftType  = visit(ast.left());
                var rightType = visit(ast.right());

                if (leftType.equals(Type.STRING) || rightType.equals(Type.STRING)) {
                    yield Type.STRING;
                }
                yield arithmeticType(leftType, rightType, ast);
            }

            case "-", "*", "/" -> {
                var leftType  = visit(ast.left());
                var rightType = visit(ast.right());
                yield arithmeticType(leftType, rightType, ast);
            }

            case "==", "!=" -> {
                var leftType  = visit(ast.left());
                var rightType = visit(ast.right());
                if (!leftType.isSubtypeOf(rightType) && !rightType.isSubtypeOf(leftType)) {
                    throw new AnalyzeException(
                            "Equality operands must have a subtype relationship.", ast);
                }
                yield Type.BOOLEAN;
            }

            case "<", "<=", ">", ">=" -> {
                var leftType  = visit(ast.left());
                var rightType = visit(ast.right());
                if (!leftType.isSubtypeOf(Type.COMPARABLE)) {
                    throw new AnalyzeException("Left operand must be Comparable.", ast.left());
                }
                if (!rightType.isSubtypeOf(Type.COMPARABLE)) {
                    throw new AnalyzeException("Right operand must be Comparable.", ast.right());
                }
                if (!leftType.equals(Type.DYNAMIC) && !rightType.equals(Type.DYNAMIC)
                        && !leftType.equals(rightType)) {
                    throw new AnalyzeException(
                            "Comparison operands must have the same type.", ast);
                }
                yield Type.BOOLEAN;
            }

            default -> throw new AnalyzeException(
                    "Unsupported binary operator '" + ast.operator() + "'.", ast);
        };
    }

    @Override
    public Type visit(Ast.Expr.Variable ast) throws AnalyzeException {
        var type = context.scope().resolve(ast.name())
                .orElseThrow(() -> new AnalyzeException("Undefined variable '" + ast.name() + "'.", ast));
        if (context.uninitialized().contains(ast.name())) {
            throw new AnalyzeException("Variable '" + ast.name() + "' is uninitialized.", ast);
        }
        return type;
    }

    @Override
    public Type visit(Ast.Expr.Property ast) throws AnalyzeException {
        var receiverType = visit(ast.receiver());
        if (receiverType.equals(Type.DYNAMIC)) {
            return Type.DYNAMIC;
        }
        if (!(receiverType instanceof Type.ObjectType objectType)) {
            throw new AnalyzeException("Property receiver must be an object.", ast.receiver());
        }
        return resolveProperty(objectType, ast.name())
                .orElseThrow(() -> new AnalyzeException("Undefined property '" + ast.name() + "'.", ast));
    }

    @Override
    public Type visit(Ast.Expr.Function ast) throws AnalyzeException {
        var type = context.scope().resolve(ast.name())
                .orElseThrow(() -> new AnalyzeException("Undefined function '" + ast.name() + "'.", ast));
        if (!(type instanceof Type.Function funcType)) {
            throw new AnalyzeException("'" + ast.name() + "' is not a function.", ast);
        }
        if (ast.arguments().size() != funcType.parameters().size()) {
            throw new AnalyzeException(
                    "Wrong number of arguments (expected " + funcType.parameters().size()
                            + ", received " + ast.arguments().size() + ").", ast);
        }
        for (int i = 0; i < ast.arguments().size(); i++) {
            var argType = visit(ast.arguments().get(i));
            if (!argType.isSubtypeOf(funcType.parameters().get(i))) {
                throw new AnalyzeException(
                        "Argument type mismatch at position " + i + ".", ast.arguments().get(i));
            }
        }
        return funcType.returns();
    }

    @Override
    public Type visit(Ast.Expr.Method ast) throws AnalyzeException {
        var receiverType = visit(ast.receiver());
        if (receiverType.equals(Type.DYNAMIC)) {
            for (var arg : ast.arguments()) visit(arg);
            return Type.DYNAMIC;
        }
        if (!(receiverType instanceof Type.ObjectType objectType)) {
            throw new AnalyzeException("Method receiver must be an object.", ast.receiver());
        }
        var rawType = resolveProperty(objectType, ast.name())
                .orElseThrow(() -> new AnalyzeException("Undefined method '" + ast.name() + "'.", ast));
        if (!(rawType instanceof Type.Function funcType)) {
            throw new AnalyzeException("'" + ast.name() + "' is not a method.", ast);
        }
        if (ast.arguments().size() != funcType.parameters().size()) {
            throw new AnalyzeException(
                    "Wrong number of method arguments (expected " + funcType.parameters().size()
                            + ", received " + ast.arguments().size() + ").", ast);
        }
        for (int i = 0; i < ast.arguments().size(); i++) {
            var argType = visit(ast.arguments().get(i));
            if (!argType.isSubtypeOf(funcType.parameters().get(i))) {
                throw new AnalyzeException(
                        "Method argument type mismatch at position " + i + ".", ast.arguments().get(i));
            }
        }
        return funcType.returns();
    }

    @Override
    public Type visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        var objectScope = new Scope(null);
        var objectType  = new Type.ObjectType(ast.name(), objectScope);

        for (var field : ast.fields()) {
            if (objectScope.get(field.name()).isPresent()) {
                throw new AnalyzeException("Field '" + field.name() + "' is already defined in this object.", field);
            }

            Optional<Type> valueTypeOpt = Optional.empty();
            if (field.value().isPresent()) {
                valueTypeOpt = Optional.of(visit(field.value().get()));
            }

            Type fieldType;
            if (field.type().isPresent()) {
                var typeName = field.type().get();
                fieldType = Optional.ofNullable(Environment.TYPES.get(typeName))
                        .orElseThrow(() -> new AnalyzeException("Undefined field type '" + typeName + "'.", field));
                if (valueTypeOpt.isPresent() && !valueTypeOpt.get().isSubtypeOf(fieldType)) {
                    throw new AnalyzeException("Field value type is not a subtype of the declared field type.", field);
                }
            } else {
                fieldType = valueTypeOpt.orElse(Type.DYNAMIC);
            }

            objectScope.declare(field.name(), fieldType);
        }

        for (var method : ast.methods()) {
            if (objectScope.get(method.name()).isPresent()) {
                throw new AnalyzeException("Method '" + method.name() + "' is already defined in this object.", method);
            }
            if (method.parameters().stream().distinct().count() != method.parameters().size()) {
                throw new AnalyzeException("Duplicate method parameter name.", method);
            }

            var paramTypes = new ArrayList<Type>();
            for (var paramTypeOpt : method.parameterTypes()) {
                if (paramTypeOpt.isPresent()) {
                    var typeName = paramTypeOpt.get();
                    var paramType = Optional.ofNullable(Environment.TYPES.get(typeName))
                            .orElseThrow(() -> new AnalyzeException("Undefined method parameter type '" + typeName + "'.", method));
                    paramTypes.add(paramType);
                } else {
                    paramTypes.add(Type.DYNAMIC);
                }
            }

            Type returnType;
            if (method.returnType().isPresent()) {
                var typeName = method.returnType().get();
                returnType = Optional.ofNullable(Environment.TYPES.get(typeName))
                        .orElseThrow(() -> new AnalyzeException("Undefined method return type '" + typeName + "'.", method));
            } else {
                returnType = Type.DYNAMIC;
            }

            var methodType = new Type.Function(List.copyOf(paramTypes), returnType);
            objectScope.declare(method.name(), methodType);

            var paramScope = new Scope(context.scope());
            for (int i = 0; i < method.parameters().size(); i++) {
                paramScope.declare(method.parameters().get(i), paramTypes.get(i));
            }
            var savedContext = this.context;
            this.context = new Context(new Scope(paramScope), Optional.of(methodType), new HashSet<>(), false);

            for (var stmt : method.body()) {
                visit(stmt);
            }

            boolean bodyReturns = this.context.returns();
            this.context = savedContext;

            if (!returnType.equals(Type.DYNAMIC) && !returnType.equals(Type.NIL) && !bodyReturns) {
                throw new AnalyzeException("Method '" + method.name() + "' does not always return a value.", method);
            }
        }

        return objectType;
    }

    /**
     * Resolves arithmetic result type for +, -, *, /.
     * Result is Dynamic only when both operands are Dynamic.
     * the concrete type of the non-Dynamic operand is used,
     * and both concrete operands must be the same numeric type.
     */
    private Type arithmeticType(Type left, Type right, Ast ast) throws AnalyzeException {
        if (left.equals(Type.DYNAMIC) && right.equals(Type.DYNAMIC)) {
            return Type.DYNAMIC;
        }
        if (left.equals(Type.DYNAMIC)) {
            if (!right.equals(Type.INTEGER) && !right.equals(Type.DECIMAL)) {
                throw new AnalyzeException("Arithmetic operand must be Integer or Decimal.", ast);
            }
            return right;
        }
        if (right.equals(Type.DYNAMIC)) {
            if (!left.equals(Type.INTEGER) && !left.equals(Type.DECIMAL)) {
                throw new AnalyzeException("Arithmetic operand must be Integer or Decimal.", ast);
            }
            return left;
        }
        if (left.equals(Type.INTEGER) && right.equals(Type.INTEGER)) return Type.INTEGER;
        if (left.equals(Type.DECIMAL) && right.equals(Type.DECIMAL)) return Type.DECIMAL;
        throw new AnalyzeException("Arithmetic operand type mismatch.", ast);
    }

    /**
     * Resolves a named property on an object type, following the prototype
     * chain (Prototypal Inheritance).
     */
    private Optional<Type> resolveProperty(Type.ObjectType objectType, String name) {
        var local = objectType.scope().get(name);
        if (local.isPresent()) return local;

        var proto = objectType.scope().get("prototype");
        if (proto.isPresent() && proto.get() instanceof Type.ObjectType protoObj) {
            return resolveProperty(protoObj, name);
        }
        return Optional.empty();
    }

    public static final class ContextHooks {

        public static Set<String> mergeUninitialized(List<Set<String>> children) {
            var result = new HashSet<String>();
            for (var child : children) {
                result.addAll(child);
            }
            return result;
        }

        public static boolean mergeReturns(List<Boolean> children) {
            return children.stream().allMatch(b -> b);
        }

    }

    public static final class EnvironmentHooks {

        public static final Type SQRT = new Type.Function(List.of(Type.DECIMAL), Type.DECIMAL);
        public static final Type RANGE = new Type.Function(List.of(Type.INTEGER, Type.INTEGER), Type.ITERABLE);

    }

    public static final class TypeHooks {

        public static boolean isSubtypeOf(Type subtype, Type supertype) {
            // Reflexivity
            if (subtype.equals(supertype)) return true;
            // Any is a universal supertype
            if (supertype.equals(Type.ANY)) return true;
            // Dynamic acts as both subtype and supertype (gradual typing)
            if (subtype.equals(Type.DYNAMIC) || supertype.equals(Type.DYNAMIC)) return true;

            // Equatable hierarchy
            if (supertype.equals(Type.EQUATABLE)) {
                return subtype.equals(Type.NIL)
                        || subtype.equals(Type.COMPARABLE)
                        || subtype.equals(Type.ITERABLE)
                        || subtype instanceof Type.ObjectType
                        || isSubtypeOf(subtype, Type.COMPARABLE);
            }

            // Comparable hierarchy
            if (supertype.equals(Type.COMPARABLE)) {
                return subtype.equals(Type.BOOLEAN)
                        || subtype.equals(Type.INTEGER)
                        || subtype.equals(Type.DECIMAL)
                        || subtype.equals(Type.CHARACTER)
                        || subtype.equals(Type.STRING);
            }

            // Prototypal inheritance for ObjectTypes
            if (subtype instanceof Type.ObjectType objectSubtype
                    && supertype instanceof Type.ObjectType) {
                var proto = objectSubtype.scope().get("prototype");
                if (proto.isPresent()) {
                    return isSubtypeOf(proto.get(), supertype);
                }
            }

            return false;
        }

    }

}
