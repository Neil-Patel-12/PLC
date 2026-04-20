package plc.project.analyzer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.parser.Parser;
import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the earlier project part for more information.
 */
final class AnalyzerTests {

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Literal",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Literal("value"))
                )),
                Type.STRING
            ),
            Arguments.of("Function",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("function_any", List.of(new Ast.Expr.Literal("value"))))
                )),
                Type.ANY
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.empty())
                )),
                Type.DYNAMIC
            ),
            Arguments.of("Declaration Type",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("String"), Optional.empty())
                )),
                Type.STRING
            ),
            Arguments.of("Initialization",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                )),
                Type.STRING
            ),
            Arguments.of("Initialization Type Subtype",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Comparable"), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                )),
                Type.COMPARABLE
            ),
            Arguments.of("Initialization Type Invalid",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Undefined"), Optional.of(new Ast.Expr.Literal(null)))
                )),
                new AnalyzeException("unused")
            ),
            Arguments.of("Redefined",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.empty()),
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.empty())
                )),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Invocation",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.empty(), List.of()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                )),
                Type.DYNAMIC
            ),
            Arguments.of("Parameter Type",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("parameter"), List.of(Optional.of("String")), Optional.empty(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Variable("parameter"))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of(new Ast.Expr.Literal("argument"))))
                )),
                Type.DYNAMIC
            ),
            Arguments.of("Nil Return Type",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("Nil"), List.of()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                )),
                Type.NIL
            )
            //Note: See testReturnStmt for other return type tests.
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("If",
                new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Literal(true), List.of(), List.of())
                )),
                Type.DYNAMIC
            ),
            Arguments.of("Condition Type Invalid",
                new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Literal("true"), List.of(), List.of())
                )),
                new AnalyzeException("unused")
            ),
            Arguments.of("Scope",
                new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Let("name", Optional.empty(), Optional.empty())),
                        List.of(new Ast.Stmt.Let("name", Optional.empty(), Optional.empty()))
                    )
                )),
                Type.DYNAMIC
            )
            //Note: See testContextX for related tests around context merging.
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("5"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("element")))
                    )
                )),
                Type.NIL
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            //Important: Return heavily depends on Def!
            Arguments.of("Inside Function",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                )),
                Type.STRING
            ),
            Arguments.of("Outside Function",
                new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                )),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Literal",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Literal("literal"))
                )),
                Type.STRING
            ),
            Arguments.of("Variable",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                )),
                Type.STRING
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Literal",
                new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Literal("literal"), new Ast.Expr.Literal("value"))
                )),
                new AnalyzeException("unused")
            ),
            Arguments.of("Variable Value Subtype",
                new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("variable"), new Ast.Expr.Literal("value"))
                )),
                Type.STRING
            ),
            Arguments.of("Variable Value Invalid",
                new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("variable"), new Ast.Expr.Literal(null))
                )),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Object input, Object expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Ast.Expr.Literal(true),
                Type.BOOLEAN
            ),
            Arguments.of("Integer",
                new Ast.Expr.Literal(new BigInteger("1")),
                Type.INTEGER
            ),
            Arguments.of("String",
                new Ast.Expr.Literal("string"),
                Type.STRING
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Object input, Object expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Ast.Expr.Group(new Ast.Expr.Literal("expr")),
                Type.STRING
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Object input, Object expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer",
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(new BigInteger("2"))
                ),
                Type.INTEGER
            ),
            Arguments.of("Op+ Invalid Right",
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(new BigDecimal("1.0"))
                ),
                new AnalyzeException("unused")
            ),
            Arguments.of("Op+ String Right",
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal("right")
                ),
                Type.STRING
            ),
            Arguments.of("Op+ Dynamic Left",
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Variable("dynamic"),
                    new Ast.Expr.Literal(new BigInteger("1"))
                ),
                Type.INTEGER
            ),
            Arguments.of("Op< Integer",
                new Ast.Expr.Binary(
                    "<",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(new BigInteger("2"))
                ),
                Type.BOOLEAN
            ),
            Arguments.of("Op< Right Invalid",
                new Ast.Expr.Binary(
                    "<",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(null)
                ),
                new AnalyzeException("unused")
            ),
            Arguments.of("OpAND Boolean",
                new Ast.Expr.Binary(
                    "AND",
                    new Ast.Expr.Literal(true),
                    new Ast.Expr.Literal(false)
                ),
                Type.BOOLEAN
            ),
            Arguments.of("OpOR Right Invalid",
                new Ast.Expr.Binary(
                    "OR",
                    new Ast.Expr.Literal(true),
                    new Ast.Expr.Literal(null)
                ),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Object input, Object expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Ast.Expr.Variable("variable"),
                Type.STRING
            ),
            Arguments.of("Undefined",
                new Ast.Expr.Variable("undefined"),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Object input, Object expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                new Ast.Expr.Property(
                    new Ast.Expr.Variable("object"),
                    "property"
                ),
                Type.STRING
            ),
            Arguments.of("Dynamic",
                new Ast.Expr.Property(
                    new Ast.Expr.Variable("dynamic"),
                    "property"
                ),
                Type.DYNAMIC
            ),
            Arguments.of("Undefined",
                new Ast.Expr.Property(
                    new Ast.Expr.Variable("object"),
                    "undefined"
                ),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Object input, Object expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Ast.Expr.Function("function", List.of()),
                Type.NIL
            ),
            Arguments.of("Argument",
                new Ast.Expr.Function("function_any", List.of(new Ast.Expr.Literal("argument"))),
                Type.ANY
            ),
            Arguments.of("Undefined",
                new Ast.Expr.Function("undefined", List.of()),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Object input, Object expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Ast.Expr.Method(
                    new Ast.Expr.Variable("object"),
                    "method_any",
                    List.of(new Ast.Expr.Literal("argument"))
                ),
                Type.ANY
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Object input, Object expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testObjectExpr() {
        Function<Map<String, Type>, Scope> scopeOf = types -> {
            var scope = new Scope(null);
            types.forEach(scope::declare);
            return scope;
        };
        return Stream.of(
            Arguments.of("Empty",
                new Ast.Expr.ObjectExpr(
                    Optional.empty(),
                    List.of(),
                    List.of()
                ),
                new Type.ObjectType(Optional.empty(), scopeOf.apply(Map.of()))
            ),
            Arguments.of("Field",
                new Ast.Expr.Property(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(new Ast.Stmt.Let("field", Optional.empty(), Optional.of(new Ast.Expr.Literal("value")))),
                        List.of()
                    ),
                    "field"
                ),
                Type.STRING
            ),
            Arguments.of("Method",
                new Ast.Expr.Method(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(new Ast.Stmt.Def(
                            "method",
                            List.of(),
                            List.of(),
                            Optional.of("Nil"),
                            List.of()
                        ))
                    ),
                    "method",
                    List.of()
                ),
                Type.NIL
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                //String input makes writing tests *significantly* easier, but
                //relies on your Lexer and Parser being implemented correctly!
                """
                DEF main() DO
                    print("Hello, World!");
                END
                main();
                """,
                Type.DYNAMIC
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testContextUninitialized(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    public static Stream<Arguments> testContextUninitialized() {
        return Stream.of(
            Arguments.of("Uninitialized",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Nil"), Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                )),
                new AnalyzeException("unused")
            ),
            Arguments.of("Initialized",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Nil"), Optional.empty()),
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("name"), new Ast.Expr.Literal(null)),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                )),
                Type.NIL
            ),
            Arguments.of("If Uninitialized",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Nil"), Optional.empty()),
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(new Ast.Stmt.Assignment(new Ast.Expr.Variable("name"), new Ast.Expr.Literal(null))),
                        List.of()
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                )),
                new AnalyzeException("unused")
            ),
            Arguments.of("If Initialized",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Nil"), Optional.empty()),
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(new Ast.Stmt.Assignment(new Ast.Expr.Variable("name"), new Ast.Expr.Literal(null))),
                        List.of(new Ast.Stmt.Assignment(new Ast.Expr.Variable("name"), new Ast.Expr.Literal(null)))
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                )),
                Type.NIL
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testContextReturns(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    public static Stream<Arguments> testContextReturns() {
        return Stream.of(
            Arguments.of("Returns",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("Boolean"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal(true)))
                    ))
                )),
                new Type.Function(List.of(), Type.BOOLEAN)
            ),
            Arguments.of("Missing Returns",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("Boolean"), List.of(
                        new Ast.Stmt.If(
                            new Ast.Expr.Literal(true),
                            List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal(true)))),
                            List.of()
                        )
                    ))
                )),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIsSubtypeOf(String test, Type type, Type other, boolean expected) {
        Assertions.assertEquals(expected, type.isSubtypeOf(other), "Expected " + type + " to " + (expected ? "" : "not ") + "be a subtype of " + other + ".");
    }

    public static Stream<Arguments> testIsSubtypeOf() {
        return Stream.of(
            Arguments.of("Equal", Type.STRING, Type.STRING, true),
            Arguments.of("Disjoint", Type.INTEGER, Type.DECIMAL, false),
            Arguments.of("Subtype", Type.STRING, Type.ANY, true),
            Arguments.of("Supertype", Type.ANY, Type.STRING, false),
            Arguments.of("Nil Equal", Type.NIL, Type.NIL, true),
            Arguments.of("Nil Subtype", Type.NIL, Type.ANY, true),
            Arguments.of("Dynamic Subtype", Type.DYNAMIC, Type.STRING, true),
            Arguments.of("Dynamic Supertype", Type.STRING, Type.DYNAMIC, true),
            Arguments.of("Equatable Subtype", Type.STRING, Type.EQUATABLE, true),
            Arguments.of("Equatable Supertype", Type.ANY, Type.EQUATABLE, false),
            Arguments.of("Comparable Subtype", Type.STRING, Type.COMPARABLE, true),
            Arguments.of("Comparable Disjoint", Type.NIL, Type.COMPARABLE, false)
        );
    }

    private static void test(Object input, Object expected, String rule) {
        //First, get/parse the input AST.
        var ast = switch (input) {
            case Ast parsed -> parsed;
            case String program -> Assertions.assertDoesNotThrow(() -> new Parser(new Lexer(program).lex()).parse(rule));
            default -> throw new AssertionError(input);
        };
        //Next, initialize the Analyzer.
        var scope = Environment.scope();
        var analyzer = new Analyzer(new Scope(scope)); //child for shadowing
        //Then, analyzer the input and check the return value.
        switch (expected) {
            case Type type -> {
                var received = Assertions.assertDoesNotThrow(() -> analyzer.visit(ast), "Unexpected AnalyzeException");
                Assertions.assertEquals(type, received);
            }
            case AnalyzeException e -> {
                var received = Assertions.assertThrows(AnalyzeException.class, () -> analyzer.visit(ast), "Expected AnalyzeException");
                if (e.getAst().isPresent()) {
                    Assertions.assertEquals(e.getAst(), received.getAst(), "Unexpected AnalyzeException Ast");
                }
            }
            default -> throw new AssertionError(input);
        }
    }

}
