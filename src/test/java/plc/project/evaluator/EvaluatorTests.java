package plc.project.evaluator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.parser.Ast;
import plc.project.parser.Parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the earlier project part for more information.
 */
final class EvaluatorTests {

    @ParameterizedTest
    @MethodSource
    void testEnvironment(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testEnvironment() {
        return Stream.of(
            Arguments.of("sqrt",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("sqrt", List.of(new Ast.Expr.Literal(new BigDecimal("4.0")))))
                )),
                new RuntimeValue.Primitive(new BigDecimal("2")),
                List.of()
            ),
            Arguments.of("range",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("range", List.of(
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("5"))
                    )))
                )),
                new RuntimeValue.Primitive(List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3")),
                    new RuntimeValue.Primitive(new BigInteger("4"))
                )),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Single",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("value"))))
                )),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Multiple",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("1"))))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("2"))))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("3")))))
                )),
                new RuntimeValue.Primitive(new BigInteger("3")),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3"))
                )
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Source.
            Arguments.of("Unhandled Return",
                new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                )),
                new EvaluateException("unused"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("name"))))
                )),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive(null))
            ),
            Arguments.of("Initialization",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("name"))))
                )),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Redefined",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Let("name", Optional.empty())
                )),
                new EvaluateException("unused"),
                List.of()
            ),
            Arguments.of("Shadowed",
                new Ast.Source(List.of(
                    //"variable" is defined to "variable" in Environment.scope()
                    new Ast.Stmt.Let("variable", Optional.empty())
                )),
                new RuntimeValue.Primitive(null),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Invocation",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("invoked"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                )),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("invoked"))
            ),
            Arguments.of("Parameter",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("parameter"), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("parameter"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of(new Ast.Expr.Literal("argument"))))
                )),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("argument"))
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Def.
            Arguments.of("Return Value",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                )),
                new RuntimeValue.Primitive("value"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("Then",
                new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                )),
                new RuntimeValue.Primitive("then"),
                List.of(new RuntimeValue.Primitive("then"))
            ),
            Arguments.of("Else",
                new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                )),
                new RuntimeValue.Primitive("else"),
                List.of(new RuntimeValue.Primitive("else"))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("list", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("2")),
                            new Ast.Expr.Literal(new BigInteger("3"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("element")))))
                    )
                )),
                new RuntimeValue.Primitive(null),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3"))
                )
            ),
            Arguments.of("Range",
                new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("5"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("element")))))
                    )
                )),
                new RuntimeValue.Primitive(null),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3")),
                    new RuntimeValue.Primitive(new BigInteger("4"))
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            //Part of the spec for Def, but duplicated here for clarity.
            Arguments.of("Inside Function",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                )),
                new RuntimeValue.Primitive("value"),
                List.of()
            ),
            //Part of the spec for Source, but duplicated here for clarity.
            Arguments.of("Outside Function",
                new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                )),
                new EvaluateException("unused"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                )),
                new RuntimeValue.Primitive("variable"),
                List.of()
            ),
            Arguments.of("Function",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("function", List.of(new Ast.Expr.Literal("argument"))))
                )),
                new RuntimeValue.Primitive(List.of(new RuntimeValue.Primitive("argument"))),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("variable"),
                        new Ast.Expr.Literal("value")
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("variable"))))
                )),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Property",
                new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property"),
                        new Ast.Expr.Literal("value")
                    ),
                    new Ast.Stmt.Expression(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property")
                    )
                )),
                new RuntimeValue.Primitive("value"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Ast.Expr.Literal(true),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Integer",
                new Ast.Expr.Literal(new BigInteger("1")),
                new RuntimeValue.Primitive(new BigInteger("1")),
                List.of()
            ),
            Arguments.of("String",
                new Ast.Expr.Literal("string"),
                new RuntimeValue.Primitive("string"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Ast.Expr.Group(new Ast.Expr.Literal("expr")),
                new RuntimeValue.Primitive("expr"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer Addition",
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(new BigInteger("2"))
                ),
                new RuntimeValue.Primitive(new BigInteger("3")),
                List.of()
            ),
            Arguments.of("Op+ Decimal Addition",
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(new BigDecimal("1.0")),
                    new Ast.Expr.Literal(new BigDecimal("2.0"))
                ),
                new RuntimeValue.Primitive(new BigDecimal("3.0")),
                List.of()
            ),
            Arguments.of("Op+ String Concatenation",
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal("left"),
                    new Ast.Expr.Literal("right")
                ),
                new RuntimeValue.Primitive("leftright"),
                List.of()
            ),
            Arguments.of("Op+ Evaluation Order Left Validation Error",
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(false),
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(true)))
                ),
                new EvaluateException("unused"),
                List.of(new RuntimeValue.Primitive(true))
            ),
            Arguments.of("Op- Evaluation Order Left Validation Error",
                new Ast.Expr.Binary(
                    "-",
                    new Ast.Expr.Literal("invalid"),
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("unevaluated")))
                ),
                new EvaluateException("unused"),
                List.of()
            ),
            Arguments.of("Op* Evaluation Order Left Execution Error",
                new Ast.Expr.Binary(
                    "*",
                    new Ast.Expr.Variable("undefined"),
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("unevaluated")))
                ),
                new EvaluateException("unused"),
                List.of()
            ),
            Arguments.of("Op/ Decimal Rounding Down",
                new Ast.Expr.Binary(
                    "/",
                    new Ast.Expr.Literal(new BigDecimal("5")),
                    new Ast.Expr.Literal(new BigDecimal("2"))
                ),
                new RuntimeValue.Primitive(new BigDecimal("2")),
                List.of()
            ),
            Arguments.of("Op< Integer True",
                new Ast.Expr.Binary(
                    "<",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(new BigInteger("2"))
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Op== Decimal False",
                new Ast.Expr.Binary(
                    "==",
                    new Ast.Expr.Literal(new BigDecimal("1.0")),
                    new Ast.Expr.Literal(new BigDecimal("2.0"))
                ),
                new RuntimeValue.Primitive(false),
                List.of()
            ),
            Arguments.of("OpAND False",
                new Ast.Expr.Binary(
                    "AND",
                    new Ast.Expr.Literal(true),
                    new Ast.Expr.Literal(false)
                ),
                new RuntimeValue.Primitive(false),
                List.of()
            ),
            Arguments.of("OpOR True Short-Circuit",
                new Ast.Expr.Binary(
                    "OR",
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(true))),
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(false)))
                ),
                new RuntimeValue.Primitive(true),
                List.of(new RuntimeValue.Primitive(true))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Ast.Expr.Variable("variable"),
                new RuntimeValue.Primitive("variable"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                new Ast.Expr.Property(
                    new Ast.Expr.Variable("object"),
                    "property"
                ),
                new RuntimeValue.Primitive("property"),
                List.of()
            ),
            Arguments.of("Prototypal Inheritance",
                new Ast.Expr.Property(
                    new Ast.Expr.Variable("object"),
                    "inherited_property"
                ),
                new RuntimeValue.Primitive("inherited_property"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Ast.Expr.Function("function", List.of()),
                new RuntimeValue.Primitive(List.of()),
                List.of()
            ),
            Arguments.of("Argument",
                new Ast.Expr.Function("function", List.of(
                    new Ast.Expr.Literal("argument")
                )),
                new RuntimeValue.Primitive(List.of(
                    new RuntimeValue.Primitive("argument"))
                ),
                List.of()
            ),
            Arguments.of("Undefined",
                new Ast.Expr.Function("undefined", List.of(
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("argument")))
                )),
                new EvaluateException("unused"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Ast.Expr.Method(
                    new Ast.Expr.Variable("object"),
                    "method",
                    List.of(new Ast.Expr.Literal("argument"))
                ),
                new RuntimeValue.Primitive(List.of(
                    new RuntimeValue.Primitive("argument")
                )),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testObjectExpr() {
        return Stream.of(
            Arguments.of("Field",
                new Ast.Expr.Property(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value")))),
                        List.of()
                    ),
                    "field"
                ),
                new RuntimeValue.Primitive("value"),
                List.of()
            ),
            Arguments.of("Method",
                new Ast.Expr.Method(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(new Ast.Stmt.Def(
                            "method",
                            List.of(),
                            List.of()
                        ))
                    ),
                    "method",
                    List.of()
                ),
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            Arguments.of("Method Parameter",
                new Ast.Expr.Method(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(new Ast.Stmt.Def(
                            "method",
                            List.of("parameter"),
                            List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("parameter"))))
                        ))
                    ),
                    "method",
                    List.of(new Ast.Expr.Literal("argument"))
                ),
                new RuntimeValue.Primitive("argument"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Object input, Object expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                """
                DEF main() DO
                    log("Hello, World!");
                END
                main();
                """,
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("Hello, World!"))
            )
        );
    }

    private static void test(String rule, Object input, Object expected, List<RuntimeValue> log) {
        //First, get/parse the input AST.
        var ast = switch (input) {
            case Ast parsed -> parsed;
            case String program -> Assertions.assertDoesNotThrow(() -> new Parser(new Lexer(program).lex()).parse(rule));
            default -> throw new AssertionError(input);
        };
        //Next, initialize the Evaluator with a scope containing a test version
        //of log that saves the logged arguments.
        var scope = Environment.scope();
        var logged = new ArrayList<RuntimeValue>();
        scope.define("log", new RuntimeValue.Function("log", arguments -> {
            if (arguments.size() != 1) {
                throw new EvaluateException("Expected log to be called with 1 argument.");
            }
            logged.add(arguments.getFirst());
            return arguments.getFirst();
        }));
        var evaluator = new Evaluator(new Scope(scope)); //New child scope to allow shadowing
        //Then, evaluate the input and check the return value.
        switch (expected) {
            case RuntimeValue value -> {
                var received = Assertions.assertDoesNotThrow(() -> evaluator.visit(ast), "Unexpected EvaluateException");
                Assertions.assertEquals(value, received);
            }
            case EvaluateException e -> {
                var received = Assertions.assertThrows(EvaluateException.class, () -> evaluator.visit(ast), "Expected EvaluateException");
                if (e.getAst().isPresent()) {
                    Assertions.assertEquals(e.getAst(), received.getAst());
                }
            }
            default -> throw new AssertionError(input);
        }
        //Finally, check the log results for evaluation order.
        Assertions.assertEquals(log, logged);
    }

}
