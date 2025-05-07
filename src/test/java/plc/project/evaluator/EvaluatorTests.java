package plc.project.evaluator;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.lexer.Token;
import plc.project.parser.Ast;
import plc.project.parser.ParseException;
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

    public sealed interface Input {
        record Ast(plc.project.parser.Ast ast) implements Input {}
        record Program(String program) implements Input {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Single",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("value"))))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Multiple",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("1"))))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("2"))))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("3")))))
                ))),
                new RuntimeValue.Primitive(new BigInteger("3")),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3"))
                )
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Source.
            Arguments.of("Unhandled Return",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                null, //EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    // CORE
    void testLetStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("name"))))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive(null))
            ),
            Arguments.of("Initialization",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("name"))))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Redefined",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Let("name", Optional.empty())
                ))),
                null,
                List.of()
            ),
            Arguments.of("Shadowed",
                new Input.Ast(new Ast.Source(List.of(
                    //"variable" is defined to "variable" in Environment.scope()
                    new Ast.Stmt.Let("variable", Optional.empty())
                ))),
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Initialization with complex expression",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("result", Optional.of(
                        new Ast.Expr.Binary(
                            "+",
                            new Ast.Expr.Literal(new BigInteger("5")),
                            new Ast.Expr.Literal(new BigInteger("3"))
                        )
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("result"))))
                ))),
                new RuntimeValue.Primitive(new BigInteger("8")),
                List.of(new RuntimeValue.Primitive(new BigInteger("8")))
            ),
            Arguments.of("Initialization with exception-throwing expression",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("result", Optional.of(
                        new Ast.Expr.Variable("undefinedVariable")
                    ))
                ))),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Invocation",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("invoked"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("invoked"))
            ),
            Arguments.of("Parameter",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("parameter"), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("parameter"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("argument"))
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Def.
            Arguments.of("Return Value",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Function with duplicate parameters",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("duplicate", List.of("param", "param"), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("called"))))
                    ))
                ))),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Function with wrong argument count",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("twoParams", List.of("p1", "p2"), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("called"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("twoParams", List.of(new Ast.Expr.Literal("one"))))
                ))),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Function accessing parent scope",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("outer", Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Def("funct", List.of(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("outer"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("funct", List.of()))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Multiple return statements (first takes precedence)",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("multiReturn", List.of(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("before return")))),
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("first"))),
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("never reached")))),
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("second")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("multiReturn", List.of()))
                ))),
                new RuntimeValue.Primitive("first"),
                List.of(new RuntimeValue.Primitive("before return"))
            ),
            Arguments.of("Redefining existing function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("sameName", List.of(), List.of()),
                    new Ast.Stmt.Def("sameName", List.of(), List.of())
                ))),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    // CORE
    void testIfStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("Then",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                ))),
                new RuntimeValue.Primitive("then"),
                List.of(new RuntimeValue.Primitive("then"))
            ),
            Arguments.of("Else",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                ))),
                new RuntimeValue.Primitive("else"),
                List.of(new RuntimeValue.Primitive("else"))
            ),
            // Additional Testcases
            Arguments.of("Invalid condition",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(new BigInteger("1")),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of()
                    )
                ))),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Empty then body",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                ))),
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            Arguments.of("Empty else body",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of()
                    )
                ))),
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            Arguments.of("Assignment in then body",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("x", Optional.of(new Ast.Expr.Literal(new BigInteger("1")))),
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(
                            new Ast.Stmt.Assignment(
                                new Ast.Expr.Variable("x"),
                                new Ast.Expr.Literal(new BigInteger("2"))
                            ),
                            new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("x"))))
                        ),
                        List.of()
                    )
                ))),
                new RuntimeValue.Primitive(new BigInteger("2")),
                List.of(new RuntimeValue.Primitive(new BigInteger("2")))
            ),
            Arguments.of("Nested if statements",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(
                            new Ast.Stmt.If(
                                new Ast.Expr.Literal(true),
                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("nested-then"))))),
                                List.of()
                            )
                        ),
                        List.of()
                    )
                ))),
                new RuntimeValue.Primitive("nested-then"),
                List.of(new RuntimeValue.Primitive("nested-then"))
            ),
            Arguments.of("Exception in condition evaluation",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Variable("undefined"),
                        List.of(),
                        List.of()
                    )
                ))),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("list", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("2")),
                            new Ast.Expr.Literal(new BigInteger("3"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("element")))))
                    )
                ))),
                new RuntimeValue.Primitive(null),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3"))
                )
            ),
            // Additional Testcases
            Arguments.of("Empty iterable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("list", List.of()),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("element")))))
                    )
                ))),
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            Arguments.of("Value not iterable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Literal(new BigInteger("5")),
                        List.of()
                    )
                ))),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("NIL iterable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Literal(null),
                        List.of()
                    )
                ))),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Nested for loops",
                new Input.Program("""
                    FOR i IN list(1, 2) DO
                        FOR j IN list("a", "b") DO
                            log(i + j);
                        END
                    END
                """),
                new RuntimeValue.Primitive(null),
                List.of(
                    new RuntimeValue.Primitive("1a"),
                    new RuntimeValue.Primitive("1b"),
                    new RuntimeValue.Primitive("2a"),
                    new RuntimeValue.Primitive("2b")
                )
            ),
            Arguments.of("Variable shadowing in for loop",
                new Input.Program("""
                    LET element = "outer";
                    FOR element IN list("inner") DO
                        log(element);
                    END
                    log(element);
                """),
                new RuntimeValue.Primitive("outer"),
                List.of(new RuntimeValue.Primitive("inner"), new RuntimeValue.Primitive("outer"))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            //Part of the spec for Def, but duplicated here for clarity.
            Arguments.of("Inside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of()
            ),
            //Part of the spec for Source, but duplicated here for clarity.
            Arguments.of("Outside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                null, //EvaluateException
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Return with exception-throwing expression",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("funct", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("undefinedVariable")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("funct", List.of()))
                ))),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Return in nested scope within function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("funct", List.of(), List.of(
                        new Ast.Stmt.If(
                            new Ast.Expr.Literal(true),
                            List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("nested")))),
                            List.of()
                        )
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("funct", List.of()))
                ))),
                new RuntimeValue.Primitive("nested"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                ))),
                new RuntimeValue.Primitive("variable"),
                List.of()
            ),
            Arguments.of("Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("function", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new RuntimeValue.Primitive(List.of(new RuntimeValue.Primitive("argument"))),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }
    // CORE (Variable Test)
    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("variable"),
                        new Ast.Expr.Literal("value")
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("variable"))))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Property",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property"),
                        new Ast.Expr.Literal("value")
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property")
                    ))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("nil", List.of()))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            // Additional Testcases
            Arguments.of("Assign to undefined variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("undefinedVariable"),
                        new Ast.Expr.Literal("value")
                    )
                ))),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Assign with exception-throwing expression",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("x", Optional.of(new Ast.Expr.Literal(new BigInteger("1")))),
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("x"),
                        new Ast.Expr.Variable("undefinedVariable")
                    )
                ))),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Assign to variable in parent scope",
                new Input.Program("""
                    LET x = 1;
                    IF TRUE DO
                        x = 2;
                        log(x);
                    END
                    log(x);
                """),
                new RuntimeValue.Primitive(new BigInteger("2")),
                List.of(new RuntimeValue.Primitive(new BigInteger("2")), new RuntimeValue.Primitive(new BigInteger("2")))
            ),
            Arguments.of("Assign to non variable/property",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Literal("literal"),
                        new Ast.Expr.Literal("value")
                    )
                ))),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Input.Ast(
                    new Ast.Expr.Literal(true)
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Integer",
                new Input.Ast(
                    new Ast.Expr.Literal(new BigInteger("1"))
                ),
                new RuntimeValue.Primitive(new BigInteger("1")),
                List.of()
            ),
            Arguments.of("String",
                new Input.Ast(
                    new Ast.Expr.Literal("string")
                ),
                new RuntimeValue.Primitive("string"),
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Null/NIL Literal",
                new Input.Ast(
                    new Ast.Expr.Literal(null)
                ),
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            Arguments.of("Large Integer Literal",
                new Input.Ast(
                    new Ast.Expr.Literal(new BigInteger("9999999999999999999999"))
                ),
                new RuntimeValue.Primitive(new BigInteger("9999999999999999999999")),
                List.of()
            ),
            Arguments.of("Special String Characters",
                new Input.Ast(
                    new Ast.Expr.Literal("Line 1\nLine 2\t\"quoted\"")
                ),
                new RuntimeValue.Primitive("Line 1\nLine 2\t\"quoted\""),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    // CORE
    void testGroupExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Input.Ast(
                    new Ast.Expr.Group(new Ast.Expr.Literal("expr"))
                ),
                new RuntimeValue.Primitive("expr"),
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Nested group expressions",
                new Input.Ast(
                    new Ast.Expr.Group(
                        new Ast.Expr.Group(
                            new Ast.Expr.Literal("nested")
                        )
                    )
                ),
                new RuntimeValue.Primitive("nested"),
                List.of()
            ),
            Arguments.of("Group with complex binary expression",
                new Input.Ast(
                    new Ast.Expr.Group(
                        new Ast.Expr.Binary(
                            "+",
                            new Ast.Expr.Literal(new BigInteger("3")),
                            new Ast.Expr.Literal(new BigInteger("7"))
                        )
                    )
                ),
                new RuntimeValue.Primitive(new BigInteger("10")),
                List.of()
            ),
            Arguments.of("Group with exception-throwing expression",
                new Input.Ast(
                    new Ast.Expr.Group(
                        new Ast.Expr.Variable("undefined")
                    )
                ),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    // CORE
    void testBinaryExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer Addition",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new RuntimeValue.Primitive(new BigInteger("3")),
                List.of()
            ),
            Arguments.of("Op+ Decimal Addition",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigDecimal("1.0")),
                        new Ast.Expr.Literal(new BigDecimal("2.0"))
                    )
                ),
                new RuntimeValue.Primitive(new BigDecimal("3.0")),
                List.of()
            ),
            Arguments.of("Op+ String Concatenation",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal("left"),
                        new Ast.Expr.Literal("right")
                    )
                ),
                new RuntimeValue.Primitive("leftright"),
                List.of()
            ),
            Arguments.of("Op- Evaluation Order Left Validation Error",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "-",
                        new Ast.Expr.Literal("invalid"),
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("evaluated")))
                    )
                ),
                null, //EvaluateException
                List.of(new RuntimeValue.Primitive("evaluated"))
            ),
            Arguments.of("Op* Evaluation Order Left Execution Error",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "*",
                        new Ast.Expr.Variable("undefined"),
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("1"))))
                    )
                ),
                null, //EvaluateException
                List.of()
            ),
            Arguments.of("Op/ Decimal Rounding Down",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "/",
                        new Ast.Expr.Literal(new BigDecimal("5")),
                        new Ast.Expr.Literal(new BigDecimal("2"))
                    )
                ),
                new RuntimeValue.Primitive(new BigDecimal("2")),
                List.of()
            ),
            Arguments.of("Op< Integer True",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Op== Decimal False",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "==",
                        new Ast.Expr.Literal(new BigDecimal("1.0")),
                        new Ast.Expr.Literal(new BigDecimal("2.0"))
                    )
                ),
                new RuntimeValue.Primitive(false),
                List.of()
            ),
            Arguments.of("OpAND False",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "AND",
                        new Ast.Expr.Literal(true),
                        new Ast.Expr.Literal(false)
                    )
                ),
                new RuntimeValue.Primitive(false),
                List.of()
            ),
            Arguments.of("OpOR True Short-Circuit",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "OR",
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(true))),
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(false)))
                    )
                ),
                new RuntimeValue.Primitive(true),
                List.of(new RuntimeValue.Primitive(true))
            ),
            // Additional Testcases
            Arguments.of("OpAND False Short-Circuit",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "AND",
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(false))),
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(true)))
                    )
                ),
                new RuntimeValue.Primitive(false),
                List.of(new RuntimeValue.Primitive(false))
            ),
            Arguments.of("Negative number addition",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("-5")),
                        new Ast.Expr.Literal(new BigInteger("3"))
                    )
                ),
                new RuntimeValue.Primitive(new BigInteger("-2")),
                List.of()
            ),
            Arguments.of("String concatenation with NIL",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal("string-"),
                        new Ast.Expr.Literal(null)
                    )
                ),
                new RuntimeValue.Primitive("string-NIL"),
                List.of()
            ),
            Arguments.of("Type mismatch in arithmetic",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "*",
                        new Ast.Expr.Literal(new BigInteger("5")),
                        new Ast.Expr.Literal(new BigDecimal("3.14"))
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Decimal division rounding",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "/",
                        new Ast.Expr.Literal(new BigDecimal("10.0")),
                        new Ast.Expr.Literal(new BigDecimal("3.0"))
                    )
                ),
                new RuntimeValue.Primitive(new BigDecimal("3.3")),
                List.of()
            ),
            Arguments.of("Division by zero with integer",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "/",
                        new Ast.Expr.Literal(new BigInteger("10")),
                        new Ast.Expr.Literal(new BigInteger("0"))
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Comparison with incomparable types",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("5")),
                        new Ast.Expr.Literal("string")
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Op== Object Equality",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "==",
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value")))),
                            List.of()
                        ),
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value")))),
                            List.of()
                        )
                    )
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Op!= Object Inequality",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "!=",
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value1")))),
                            List.of()
                        ),
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value2")))),
                            List.of()
                        )
                    )
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Op== Object Equality 2",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "==",
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(
                                new Ast.Stmt.Let("field1", Optional.of(new Ast.Expr.Literal("value1"))),
                                new Ast.Stmt.Let("field2", Optional.of(new Ast.Expr.Literal("value2")))
                            ),
                            List.of(
                                new Ast.Stmt.Def("method", List.of(), List.of())
                            )
                        ),
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(
                                new Ast.Stmt.Let("field1", Optional.of(new Ast.Expr.Literal("value1"))),
                                new Ast.Stmt.Let("field2", Optional.of(new Ast.Expr.Literal("value2")))
                            ),
                            List.of(
                                new Ast.Stmt.Def("method", List.of(), List.of())
                            )
                        )
                    )
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Op== Object-Primitive Equality",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "==",
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(),
                            List.of()
                        ),
                        new Ast.Expr.Literal("string")
                    )
                ),
                new RuntimeValue.Primitive(false),
                List.of()
            ),
            Arguments.of("Op!= Object-Primitive Inequality",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "!=",
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(),
                            List.of()
                        ),
                        new Ast.Expr.Literal("string")
                    )
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    // CORE
    void testVariableExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(
                    new Ast.Expr.Variable("variable")
                ),
                new RuntimeValue.Primitive("variable"),
                List.of()
            ),
            // Additional Testcase
            Arguments.of("Undefined variable",
                new Input.Ast(
                        new Ast.Expr.Variable("undefinedVariable")
                ),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Variable("object"),
                        "property"
                    )
                ),
                new RuntimeValue.Primitive("property"),
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Property access on non-object",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Literal(new BigInteger("5")),
                        "property"
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Undefined property",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value")))),
                            List.of()
                        ),
                        "undefinedProperty"
                    )
                ),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    // CORE
    void testFunctionExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Input.Ast(
                    new Ast.Expr.Function("function", List.of())
                ),
                new RuntimeValue.Primitive(List.of()),
                List.of()
            ),
            Arguments.of("Argument",
                new Input.Ast(
                    new Ast.Expr.Function("function", List.of(
                        new Ast.Expr.Literal("argument")
                    ))
                ),
                new RuntimeValue.Primitive(List.of(new RuntimeValue.Primitive("argument"))),
                List.of()
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Function("undefined", List.of(
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("argument")))
                    ))
                ),
                null, //EvaluateException
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Function with name conflict",
                new Input.Program("""
                    LET funct = "variable";
                    DEF funct() DO
                        RETURN "function";
                    END
                    log(funct);
                """),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Exception in argument evaluation",
                new Input.Ast(
                    new Ast.Expr.Function(
                        "function",
                        List.of(
                            new Ast.Expr.Variable("undefined")
                        )
                    )
                ),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Variable("object"),
                        "method",
                        List.of(new Ast.Expr.Literal("argument"))
                    )
                ),
                new RuntimeValue.Primitive(List.of(new RuntimeValue.Primitive("argument"))),
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Method on non object",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Literal("string"),
                        "method",
                        List.of()
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Undefined method",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(),
                            List.of()
                        ),
                        "undefined",
                        List.of()
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Method with wrong arity",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(),
                            List.of(new Ast.Stmt.Def(
                                "method",
                                List.of("parameter"),
                                List.of()
                            ))
                        ),
                        "method",
                        List.of(new Ast.Expr.Literal("arg1"), new Ast.Expr.Literal("arg2"))
                    )
                ),
                null, // EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testObjectExpr() {
        return Stream.of(
            Arguments.of("Empty",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of()
                    )
                ),
                new RuntimeValue.ObjectValue(Optional.empty(), new Scope(null)),
                List.of()
            ),
            Arguments.of("Field",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value")))),
                            List.of()
                        ),
                        "field"
                    )
                ),
                new RuntimeValue.Primitive("value"),
                List.of()
            ),
            Arguments.of("Method",
                new Input.Ast(
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
                    )
                ),
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            Arguments.of("Method Parameter",
                new Input.Ast(
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
                    )
                ),
                new RuntimeValue.Primitive("argument"),
                List.of()
            ),
            // Additional Testcases
            Arguments.of("Object with duplicate field names",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(
                            new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value1"))),
                            new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value2")))
                        ),
                        List.of()
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Object with duplicate method names",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(
                            new Ast.Stmt.Def("method", List.of(), List.of()),
                            new Ast.Stmt.Def("method", List.of(), List.of())
                        )
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Object with field initializer that throws exception",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(
                                new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Variable("undefinedVariable")))
                        ),
                        List.of()
                    )
                ),
                null, // EvaluateException
                List.of()
            ),
            Arguments.of("Field Shadowing",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(new Ast.Stmt.Let("variable", Optional.of(new Ast.Expr.Literal("inner")))),
                            List.of(new Ast.Stmt.Def(
                                "getField",
                                List.of(),
                                List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("variable"))))
                            ))
                        ),
                        "getField",
                        List.of()
                    )
                ),
                new RuntimeValue.Primitive("inner"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                //Input.Program makes tests *significantly* easier, but relies
                //on your Lexer and Parser being implemented correctly!
                new Input.Program("""
                    DEF main() DO
                        log("Hello, World!");
                    END
                    main();
                    """),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("Hello, World!"))
            )
        );
    }

    interface ParserMethod<T extends Ast> {
        T invoke(Parser parser) throws ParseException;
    }

    /**
     * Test function for the Evaluator. The {@link Input} behaves the same as
     * in parser tests, but will now rely on the parser behavior too. This
     * function tests both the return value of evaluation and evaluation order
     * via the use of a custom log function that tracks invocations.
     */
    private static void test(Input input, @Nullable RuntimeValue expected, List<RuntimeValue> log, ParserMethod<? extends Ast> method) {
        //First, get/parse the input AST.
        var ast = switch (input) {
            case Input.Ast i -> i.ast();
            case Input.Program i -> Assertions.assertDoesNotThrow(
                () -> method.invoke(new Parser(new Lexer(i.program).lex()))
            );
        };
        //Next, initialize the evaluator and scope.
        var scope = new Scope(Environment.scope());
        //This one is a bit weird, but it allows statement tests to force NIL as
        //the return value for Ast.Source for reduced overlap in testing.
        scope.define("nil", new RuntimeValue.Function("nil", arguments -> {
            if (!arguments.isEmpty()) {
                throw new EvaluateException("Expected nil to be called with 0 arguments.");
            }
            return new RuntimeValue.Primitive(null);
        }));
        //Log allows tracking when expressions are evaluated, allowing tests to
        //also inspect the evaluation order and control flow.
        var logged = new ArrayList<RuntimeValue>();
        scope.define("log", new RuntimeValue.Function("log", arguments -> {
            if (arguments.size() != 1) {
                throw new EvaluateException("Expected log to be called with 1 argument.");
            }
            logged.add(arguments.getFirst());
            return arguments.getFirst();
        }));
        Evaluator evaluator = new Evaluator(scope);
        //Then, evaluate the input and check the return value.
        try {
            var value = evaluator.visit(ast);
            Assertions.assertNotNull(expected, "Expected an exception to be thrown, received " + value + ".");
            Assertions.assertEquals(expected, value);
        } catch (EvaluateException e) {
            Assertions.assertNull(expected, "Unexpected EvaluateException thrown (" + e.getMessage() +"), expected " + expected + ".");
        }
        //Finally, check the log results for evaluation order.
        Assertions.assertEquals(log, logged);
    }

}
