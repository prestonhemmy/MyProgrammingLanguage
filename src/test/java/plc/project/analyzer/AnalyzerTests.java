package plc.project.analyzer;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.parser.Ast;
import plc.project.parser.ParseException;
import plc.project.parser.Parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the earlier project part for more information.
 */
final class AnalyzerTests {

    public sealed interface Input {
        record Ast(plc.project.parser.Ast ast) implements Input {}
        record Program(String program) implements Input {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Literal",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Literal("value"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Literal("value", Type.STRING))
                ))
            ),
            Arguments.of("Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("functionAny", List.of(new Ast.Expr.Literal("value"))))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Function("functionAny", List.of(new Ir.Expr.Literal("value", Type.STRING)), Type.ANY))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.ANY, Optional.empty()),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.ANY))
                ))
            ),
            Arguments.of("Declaration Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("String"), Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.STRING, Optional.empty()),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.STRING))
                ))
            ),
            Arguments.of("Initialization",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.STRING, Optional.of(new Ir.Expr.Literal("value", Type.STRING))),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.STRING))
                ))
            ),
            Arguments.of("Initialization Type Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Comparable"), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.COMPARABLE, Optional.of(new Ir.Expr.Literal("value", Type.STRING))),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.COMPARABLE))
                ))
            ),
            Arguments.of("Initialization Type Invalid",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Comparable"), Optional.of(new Ast.Expr.Literal(null)))
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Redefined",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Let("name", Optional.empty())
                ))),
                null //AnalyzeException
            ),
            // Additional Testcases
            Arguments.of("Invalid Type Name",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("NonExistentType"), Optional.empty())
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Nil Value with Explicit Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Equatable"), Optional.of(new Ast.Expr.Literal(null))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.EQUATABLE, Optional.of(new Ir.Expr.Literal(null, Type.NIL))),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.EQUATABLE))
                ))
            ),
            Arguments.of("Complex Expression Initialization",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("result", Optional.empty(), Optional.of(
                        new Ast.Expr.Binary("+",
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("2"))
                        )
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("result"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("result", Type.INTEGER, Optional.of(
                        new Ir.Expr.Binary("+",
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                            Type.INTEGER
                        )
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("result", Type.INTEGER))
                ))
            ),
            Arguments.of("Any Type with Specific Value",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("flexible", Optional.of("Any"), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("flexible"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("flexible", Type.ANY, Optional.of(new Ir.Expr.Literal("value", Type.STRING))),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("flexible", Type.ANY))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Invocation",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.empty(), List.of()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.ANY, List.of()),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.ANY))
                ))
            ),
            Arguments.of("Parameter Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("parameter"), List.of(Optional.of("String")), Optional.empty(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Variable("parameter"))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(new Ir.Stmt.Def.Parameter("parameter", Type.STRING)), Type.ANY, List.of(
                        new Ir.Stmt.Expression(new Ir.Expr.Variable("parameter", Type.STRING))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(new Ir.Expr.Literal("argument", Type.STRING)), Type.ANY))
                ))
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Def.
            Arguments.of("Return Type/Value",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.STRING, List.of(
                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.STRING))
                ))
            ),
            // Additional Testcases
            Arguments.of("Multiple Parameters",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("multiply",
                        List.of("a", "b"),
                        List.of(Optional.of("Integer"), Optional.of("Integer")),
                        Optional.of("Integer"),
                        List.of(
                            new Ast.Stmt.Return(Optional.of(
                                new Ast.Expr.Binary("*",
                                    new Ast.Expr.Variable("a"),
                                    new Ast.Expr.Variable("b")
                                )
                            ))
                        )
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("multiply",
                        List.of(
                            new Ast.Expr.Literal(new BigInteger("5")),
                            new Ast.Expr.Literal(new BigInteger("2"))
                        )
                    ))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("multiply",
                        List.of(
                            new Ir.Stmt.Def.Parameter("a", Type.INTEGER),
                            new Ir.Stmt.Def.Parameter("b", Type.INTEGER)
                        ),
                        Type.INTEGER,
                        List.of(
                            new Ir.Stmt.Return(Optional.of(
                                new Ir.Expr.Binary("*",
                                    new Ir.Expr.Variable("a", Type.INTEGER),
                                    new Ir.Expr.Variable("b", Type.INTEGER),
                                    Type.INTEGER
                                )
                            ))
                        )
                    ),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("multiply",
                        List.of(
                            new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER)
                        ),
                        Type.INTEGER
                    ))
                ))
            ),
            Arguments.of("Invalid Parameter Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("test",
                        List.of("param"),
                        List.of(Optional.of("NonExistentType")),
                        Optional.empty(),
                        List.of()
                    )
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Duplicate Parameters",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("test",
                        List.of("param", "param"),
                        List.of(Optional.empty(), Optional.empty()),
                        Optional.empty(),
                        List.of()
                    )
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Return Type Mismatch",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("wrongReturn",
                        List.of(),
                        List.of(),
                        Optional.of("Integer"),
                        List.of(
                            new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("string value")))
                        )
                    )
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Recursive Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("factorial",
                        List.of("n"),
                        List.of(Optional.of("Integer")),
                        Optional.of("Integer"),
                        List.of(
                            new Ast.Stmt.If(
                                new Ast.Expr.Binary("<=",
                                    new Ast.Expr.Variable("n"),
                                    new Ast.Expr.Literal(new BigInteger("1"))
                                ),
                                List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal(new BigInteger("1"))))),
                                List.of(
                                    new Ast.Stmt.Return(Optional.of(
                                        new Ast.Expr.Binary("*",
                                            new Ast.Expr.Variable("n"),
                                            new Ast.Expr.Function("factorial", List.of(
                                                new Ast.Expr.Binary("-",
                                                    new Ast.Expr.Variable("n"),
                                                    new Ast.Expr.Literal(new BigInteger("1"))
                                                )
                                            ))
                                        )
                                    ))
                                )
                            )
                        )
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("factorial",
                        List.of(new Ir.Stmt.Def.Parameter("n", Type.INTEGER)),
                        Type.INTEGER,
                        List.of(
                            new Ir.Stmt.If(
                                new Ir.Expr.Binary("<=",
                                    new Ir.Expr.Variable("n", Type.INTEGER),
                                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                                    Type.BOOLEAN
                                ),
                                List.of(new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER)))),
                                List.of(
                                    new Ir.Stmt.Return(Optional.of(
                                        new Ir.Expr.Binary("*",
                                            new Ir.Expr.Variable("n", Type.INTEGER),
                                            new Ir.Expr.Function("factorial", List.of(
                                                new Ir.Expr.Binary("-",
                                                    new Ir.Expr.Variable("n", Type.INTEGER),
                                                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                                                    Type.INTEGER
                                                )
                                            ), Type.INTEGER),
                                            Type.INTEGER
                                        )
                                    ))
                                )
                            )
                        )
                    )
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("If",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Literal(true), List.of(), List.of())
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(new Ir.Expr.Literal(true, Type.BOOLEAN), List.of(), List.of())
                ))
            ),
            Arguments.of("Condition Type Invalid",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Literal("true"), List.of(), List.of())
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Scope",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Let("name", Optional.empty())),
                        List.of(new Ast.Stmt.Let("name", Optional.empty()))
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(false, Type.BOOLEAN),
                        List.of(new Ir.Stmt.Let("name", Type.ANY, Optional.empty())),
                        List.of(new Ir.Stmt.Let("name", Type.ANY, Optional.empty()))
                    )
                ))
            ),
            // Additional Testcases
            Arguments.of("Empty Then Body",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Literal("else executed")))
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(true, Type.BOOLEAN),
                        List.of(),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("else executed", Type.STRING)))
                    )
                ))
            ),
            Arguments.of("Empty Else Body",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Literal("then executed"))),
                        List.of()
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(true, Type.BOOLEAN),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("then executed", Type.STRING))),
                        List.of()
                    )
                ))
            ),
            Arguments.of("Nested If",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(
                            new Ast.Stmt.If(
                                new Ast.Expr.Literal(false),
                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Literal("nested then"))),
                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Literal("nested else")))
                            )
                        ),
                        List.of()
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(true, Type.BOOLEAN),
                        List.of(
                            new Ir.Stmt.If(
                                new Ir.Expr.Literal(false, Type.BOOLEAN),
                                List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("nested then", Type.STRING))),
                                List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("nested else", Type.STRING)))
                            )
                        ),
                        List.of()
                    )
                ))
            ),
            Arguments.of("Complex Condition",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Binary("AND",
                            new Ast.Expr.Literal(true),
                            new Ast.Expr.Binary("OR",
                                new Ast.Expr.Literal(false),
                                new Ast.Expr.Literal(true)
                            )
                        ),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Literal("complex condition true"))),
                        List.of()
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(
                        new Ir.Expr.Binary("AND",
                            new Ir.Expr.Literal(true, Type.BOOLEAN),
                            new Ir.Expr.Binary("OR",
                                new Ir.Expr.Literal(false, Type.BOOLEAN),
                                new Ir.Expr.Literal(true, Type.BOOLEAN),
                                Type.BOOLEAN
                            ),
                            Type.BOOLEAN
                        ),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("complex condition true", Type.STRING))),
                        List.of()
                    )
                ))
            ),
            Arguments.of("Variable Boolean Condition",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("flag", Optional.empty(), Optional.of(new Ast.Expr.Literal(true))),
                    new Ast.Stmt.If(
                        new Ast.Expr.Variable("flag"),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Literal("condition is true"))),
                        List.of()
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("flag", Type.BOOLEAN, Optional.of(new Ir.Expr.Literal(true, Type.BOOLEAN))),
                    new Ir.Stmt.If(
                        new Ir.Expr.Variable("flag", Type.BOOLEAN),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("condition is true", Type.STRING))),
                        List.of()
                    )
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("5"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("element")))
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.For(
                        "element",
                        Type.INTEGER,
                        new Ir.Expr.Function("range", List.of(
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER)
                        ), Type.ITERABLE),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Variable("element", Type.INTEGER)))
                    )
                ))
            ),
            // Additional Testcases
            Arguments.of("Non-Iterable Expression",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Literal("not an iterable"),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("element")))
                    )
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Empty Body",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "i",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("10"))
                        )),
                        List.of()
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.For(
                        "i",
                        Type.INTEGER,
                        new Ir.Expr.Function("range", List.of(
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("10"), Type.INTEGER)
                        ), Type.ITERABLE),
                        List.of()
                    )
                ))
            ),
            Arguments.of("Nested For Loops",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "i",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("3"))
                        )),
                        List.of(
                            new Ast.Stmt.For(
                                "j",
                                new Ast.Expr.Function("range", List.of(
                                    new Ast.Expr.Literal(new BigInteger("1")),
                                    new Ast.Expr.Literal(new BigInteger("3"))
                                )),
                                List.of(
                                    new Ast.Stmt.Expression(
                                        new Ast.Expr.Binary("+",
                                            new Ast.Expr.Variable("i"),
                                            new Ast.Expr.Variable("j")
                                        )
                                    )
                                )
                            )
                        )
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.For(
                        "i",
                        Type.INTEGER,
                        new Ir.Expr.Function("range", List.of(
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("3"), Type.INTEGER)
                        ), Type.ITERABLE),
                        List.of(
                            new Ir.Stmt.For(
                                "j",
                                Type.INTEGER,
                                new Ir.Expr.Function("range", List.of(
                                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                                    new Ir.Expr.Literal(new BigInteger("3"), Type.INTEGER)
                                ), Type.ITERABLE),
                                List.of(
                                    new Ir.Stmt.Expression(
                                        new Ir.Expr.Binary("+",
                                            new Ir.Expr.Variable("i", Type.INTEGER),
                                            new Ir.Expr.Variable("j", Type.INTEGER),
                                            Type.INTEGER
                                        )
                                    )
                                )
                            )
                        )
                    )
                ))
            ),
            Arguments.of("Variable Shadowing",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("element", Optional.empty(), Optional.of(new Ast.Expr.Literal("outer value"))),
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("5"))
                        )),
                        List.of(
                            new Ast.Stmt.Expression(new Ast.Expr.Variable("element"))
                        )
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("element"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("element", Type.STRING, Optional.of(new Ir.Expr.Literal("outer value", Type.STRING))),
                    new Ir.Stmt.For(
                        "element",
                        Type.INTEGER,
                        new Ir.Expr.Function("range", List.of(
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER)
                        ), Type.ITERABLE),
                        List.of(
                            new Ir.Stmt.Expression(new Ir.Expr.Variable("element", Type.INTEGER))
                        )
                    ),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("element", Type.STRING))
                ))
            ),
            Arguments.of("Complex Iterable Expression",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("start", Optional.empty(), Optional.of(new Ast.Expr.Literal(new BigInteger("1")))),
                    new Ast.Stmt.Let("end", Optional.empty(), Optional.of(new Ast.Expr.Literal(new BigInteger("10")))),
                    new Ast.Stmt.For(
                        "i",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Variable("start"),
                            new Ast.Expr.Binary("+",
                                new Ast.Expr.Variable("end"),
                                new Ast.Expr.Literal(new BigInteger("1"))
                            )
                        )),
                        List.of(
                            new Ast.Stmt.Expression(new Ast.Expr.Variable("i"))
                        )
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("start", Type.INTEGER, Optional.of(new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER))),
                    new Ir.Stmt.Let("end", Type.INTEGER, Optional.of(new Ir.Expr.Literal(new BigInteger("10"), Type.INTEGER))),
                    new Ir.Stmt.For(
                        "i",
                        Type.INTEGER,
                        new Ir.Expr.Function("range", List.of(
                            new Ir.Expr.Variable("start", Type.INTEGER),
                            new Ir.Expr.Binary("+",
                                new Ir.Expr.Variable("end", Type.INTEGER),
                                new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                                Type.INTEGER
                            )
                        ), Type.ITERABLE),
                        List.of(
                            new Ir.Stmt.Expression(new Ir.Expr.Variable("i", Type.INTEGER))
                        )
                    )
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            //Part of the spec for Def, but duplicated here for clarity.
            Arguments.of("Inside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.STRING, List.of(
                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.STRING))
                ))
            ),
            Arguments.of("Outside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                null //AnalyzeException
            ),
            // Additional Testcases
            Arguments.of("Empty Return with Return Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("isEmpty", List.of(), List.of(), Optional.of("Equatable"), List.of(
                        new Ast.Stmt.Return(Optional.empty())
                    ))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("isEmpty", List.of(), Type.EQUATABLE, List.of(
                        new Ir.Stmt.Return(Optional.empty())
                    ))
                ))
            ),
            Arguments.of("Empty Return with Non-Equatable Return Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("willFail", List.of(), List.of(), Optional.of("Integer"), List.of(
                        new Ast.Stmt.Return(Optional.empty())
                    ))
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Return Value with No Return Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("implicitAny", List.of(), List.of(), Optional.empty(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal(new BigInteger("42"))))
                    ))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("implicitAny", List.of(), Type.ANY, List.of(
                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal(new BigInteger("42"), Type.INTEGER)))
                    ))
                ))
            ),
            Arguments.of("Return Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("returnSubtype", List.of(), List.of(), Optional.of("Comparable"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("string is comparable")))
                    ))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("returnSubtype", List.of(), Type.COMPARABLE, List.of(
                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("string is comparable", Type.STRING)))
                    ))
                ))
            ),
            Arguments.of("Multiple Return Statements",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("multipleReturns", List.of("x"), List.of(Optional.of("Integer")), Optional.of("Integer"), List.of(
                        new Ast.Stmt.If(
                            new Ast.Expr.Binary(">",
                                new Ast.Expr.Variable("x"),
                                new Ast.Expr.Literal(new BigInteger("0"))
                            ),
                            List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal(new BigInteger("1"))))),
                            List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal(new BigInteger("-1")))))
                        )
                    ))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("multipleReturns",
                        List.of(new Ir.Stmt.Def.Parameter("x", Type.INTEGER)),
                        Type.INTEGER,
                        List.of(
                            new Ir.Stmt.If(
                                new Ir.Expr.Binary(">",
                                    new Ir.Expr.Variable("x", Type.INTEGER),
                                    new Ir.Expr.Literal(new BigInteger("0"), Type.INTEGER),
                                    Type.BOOLEAN
                                ),
                                List.of(new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER)))),
                                List.of(new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal(new BigInteger("-1"), Type.INTEGER))))
                            )
                        )
                    )
                ))
            ),
            Arguments.of("Complex Return Expression",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("complexReturn", List.of(), List.of(), Optional.of("Integer"), List.of(
                        new Ast.Stmt.Return(Optional.of(
                            new Ast.Expr.Binary("+",
                                new Ast.Expr.Literal(new BigInteger("10")),
                                new Ast.Expr.Binary("*",
                                    new Ast.Expr.Literal(new BigInteger("5")),
                                    new Ast.Expr.Literal(new BigInteger("2"))
                                )
                            )
                        ))
                    ))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("complexReturn", List.of(), Type.INTEGER, List.of(
                        new Ir.Stmt.Return(Optional.of(
                            new Ir.Expr.Binary("+",
                                new Ir.Expr.Literal(new BigInteger("10"), Type.INTEGER),
                                new Ir.Expr.Binary("*",
                                    new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER),
                                    new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                                    Type.INTEGER
                                ),
                                Type.INTEGER
                            )
                        ))
                    ))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Literal",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Literal("literal"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Literal("literal", Type.STRING))
                )),
                List.of()
            ),
            Arguments.of("Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("variable", Type.STRING))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Literal",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Literal("literal"), new Ast.Expr.Literal("value"))
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Variable Type Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("variable"), new Ast.Expr.Literal("value"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Assignment.Variable(new Ir.Expr.Variable("variable", Type.STRING), new Ir.Expr.Literal("value", Type.STRING))
                ))
            ),
            Arguments.of("Variable Type Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("variable"), new Ast.Expr.Literal(null))
                ))),
                null //AnalyzeException
            ),
            // Additional Testcases
            Arguments.of("Property Assignment",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property"),
                        new Ast.Expr.Literal("new value")
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Assignment.Property(
                        new Ir.Expr.Property(
                            new Ir.Expr.Variable("object", Environment.scope().get("object", true).get()),
                            "property",
                            Type.STRING
                        ),
                        new Ir.Expr.Literal("new value", Type.STRING)
                    )
                ))
            ),
            Arguments.of("Undefined Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("undefinedVar"), new Ast.Expr.Literal("value"))
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Undefined Property",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "undefinedProperty"),
                        new Ast.Expr.Literal("value")
                    )
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Complex Assignment",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("num", Optional.of("Integer"), Optional.of(new Ast.Expr.Literal(new BigInteger("5")))),
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("num"),
                        new Ast.Expr.Binary("+",
                            new Ast.Expr.Variable("num"),
                            new Ast.Expr.Literal(new BigInteger("10"))
                        )
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("num", Type.INTEGER, Optional.of(new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER))),
                    new Ir.Stmt.Assignment.Variable(
                        new Ir.Expr.Variable("num", Type.INTEGER),
                        new Ir.Expr.Binary("+",
                            new Ir.Expr.Variable("num", Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("10"), Type.INTEGER),
                            Type.INTEGER
                        )
                    )
                ))
            ),
            Arguments.of("Assignment with Value Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("comp", Optional.of("Comparable"), Optional.empty()),
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("comp"),
                        new Ast.Expr.Literal("string is comparable")
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("comp", Type.COMPARABLE, Optional.empty()),
                    new Ir.Stmt.Assignment.Variable(
                        new Ir.Expr.Variable("comp", Type.COMPARABLE),
                        new Ir.Expr.Literal("string is comparable", Type.STRING)
                    )
                ))
            ),
            Arguments.of("Property Type Mismatch",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property"),
                        new Ast.Expr.Literal(new BigInteger("123"))
                    )
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Assignment to Method",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "method"),
                        new Ast.Expr.Literal("cannot assign to method")
                    )
                ))),
                null // AnalyzeException
            ),
            Arguments.of("Assignment with Any Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("any", Optional.of("Any"), Optional.empty()),
                    new Ast.Stmt.Let("anyValue", Optional.of("Any"), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("any"),
                        new Ast.Expr.Variable("anyValue")
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("any", Type.ANY, Optional.empty()),
                    new Ir.Stmt.Let("anyValue", Type.ANY, Optional.of(new Ir.Expr.Literal("value", Type.STRING))),
                    new Ir.Stmt.Assignment.Variable(
                        new Ir.Expr.Variable("any", Type.ANY),
                        new Ir.Expr.Variable("anyValue", Type.ANY)
                    )
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Input.Ast(
                    new Ast.Expr.Literal(true)
                ),
                new Ir.Expr.Literal(true, Type.BOOLEAN)
            ),
            Arguments.of("Integer",
                new Input.Ast(
                    new Ast.Expr.Literal(new BigInteger("1"))
                ),
                new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER)
            ),
            Arguments.of("String",
                new Input.Ast(
                    new Ast.Expr.Literal("string")
                ),
                new Ir.Expr.Literal("string", Type.STRING)
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Input.Ast(
                    new Ast.Expr.Group(new Ast.Expr.Literal("expr"))
                ),
                new Ir.Expr.Group(new Ir.Expr.Literal("expr", Type.STRING))
            ),
            // Additional Testcases
            Arguments.of("Nested Group",
                new Input.Ast(
                    new Ast.Expr.Group(new Ast.Expr.Group(new Ast.Expr.Literal("expr")))
                ),
                new Ir.Expr.Group(new Ir.Expr.Group(new Ir.Expr.Literal("expr", Type.STRING)))
            ),
            Arguments.of("Binary Expression Group",
                new Input.Ast(
                    new Ast.Expr.Group(
                        new Ast.Expr.Binary("+",
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("2"))
                        )
                    )
                ),
                new Ir.Expr.Group(
                    new Ir.Expr.Binary("+",
                        new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                        new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                        Type.INTEGER
                    )
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new Ir.Expr.Binary(
                    "+",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                    Type.INTEGER
                )
            ),
            Arguments.of("Op+ Invalid Right",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigDecimal("1.0"))
                    )
                ),
                null //AnalyzeException
            ),
            Arguments.of("Op+ String Right",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal("right")
                    )
                ),
                new Ir.Expr.Binary(
                    "+",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal("right", Type.STRING),
                    Type.STRING
                )
            ),
            Arguments.of("Op< Integer",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new Ir.Expr.Binary(
                    "<",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("Op< Right Invalid",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(null)
                    )
                ),
                null //AnalyzeException
            ),
            Arguments.of("OpAND Boolean",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "AND",
                        new Ast.Expr.Literal(true),
                        new Ast.Expr.Literal(false)
                    )
                ),
                new Ir.Expr.Binary(
                    "AND",
                    new Ir.Expr.Literal(true, Type.BOOLEAN),
                    new Ir.Expr.Literal(false, Type.BOOLEAN),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("OpOR Right Invalid",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "OR",
                        new Ast.Expr.Literal(true),
                        new Ast.Expr.Literal(null)
                    )
                ),
                null //AnalyzeException
            ),
            // Additional Testcases
            Arguments.of("Op+ String Left",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal("left"),
                        new Ast.Expr.Literal(new BigInteger("1"))
                    )
                ),
                new Ir.Expr.Binary(
                    "+",
                    new Ir.Expr.Literal("left", Type.STRING),
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    Type.STRING
                )
            ),
            Arguments.of("Op+ Invalid Left",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigDecimal("1.0")),
                        new Ast.Expr.Literal(new BigInteger("1"))
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Op- Integer",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "-",
                        new Ast.Expr.Literal(new BigInteger("5")),
                        new Ast.Expr.Literal(new BigInteger("3"))
                    )
                ),
                new Ir.Expr.Binary(
                    "-",
                    new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER),
                    new Ir.Expr.Literal(new BigInteger("3"), Type.INTEGER),
                    Type.INTEGER
                )
            ),
            Arguments.of("Op* Decimal Type Mismatch",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "*",
                        new Ast.Expr.Literal(new BigDecimal("2.5")),
                        new Ast.Expr.Literal(new BigDecimal("3.0"))
                    )
                ),
                new Ir.Expr.Binary(
                    "*",
                    new Ir.Expr.Literal(new BigDecimal("2.5"), Type.DECIMAL),
                    new Ir.Expr.Literal(new BigDecimal("3.0"), Type.DECIMAL),
                    Type.DECIMAL
                )
            ),
            Arguments.of("Op/ Mixed Types",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "/",
                        new Ast.Expr.Literal(new BigDecimal("10.0")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Op>= String Comparison",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        ">=",
                        new Ast.Expr.Literal("abc"),
                        new Ast.Expr.Literal("def")
                    )
                ),
                new Ir.Expr.Binary(
                    ">=",
                    new Ir.Expr.Literal("abc", Type.STRING),
                    new Ir.Expr.Literal("def", Type.STRING),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("Op<= Nil on Left Invalid",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<=",
                        new Ast.Expr.Literal(null),
                        new Ast.Expr.Literal("value")
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Op== Nil Comparison",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "==",
                        new Ast.Expr.Literal(null),
                        new Ast.Expr.Literal(null)
                    )
                ),
                new Ir.Expr.Binary(
                    "==",
                    new Ir.Expr.Literal(null, Type.NIL),
                    new Ir.Expr.Literal(null, Type.NIL),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("Op!= Different Types",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "!=",
                        new Ast.Expr.Literal("string"),
                        new Ast.Expr.Literal(new BigInteger("1"))
                    )
                ),
                new Ir.Expr.Binary(
                    "!=",
                    new Ir.Expr.Literal("string", Type.STRING),
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("OpAND Left Invalid",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "AND",
                        new Ast.Expr.Literal("true"),
                        new Ast.Expr.Literal(true)
                    )
                ),
                null // AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(
                    new Ast.Expr.Variable("variable")
                ),
                new Ir.Expr.Variable("variable", Type.STRING)
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Variable("undefined")
                ),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
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
                new Ir.Expr.Property(
                    new Ir.Expr.Variable("object", Environment.scope().get("object", true).get()),
                    "property",
                    Type.STRING
                )
            ),
            // Additional Testcases
            Arguments.of("Property DNE",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Variable("object"),
                        "nonExistentProperty"
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Non-Object Receiver",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Literal("not an object"),
                        "anyProperty"
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Method Property Access",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Variable("object"),
                        "method"
                    )
                ),
                new Ir.Expr.Property(
                    new Ir.Expr.Variable("object", Environment.scope().get("object", true).get()),
                    "method",
                    new Type.Function(List.of(), Type.NIL)
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Input.Ast(
                    new Ast.Expr.Function("function", List.of())
                ),
                new Ir.Expr.Function("function", List.of(), Type.NIL)
            ),
            Arguments.of("Argument",
                new Input.Ast(
                    new Ast.Expr.Function("functionAny", List.of(new Ast.Expr.Literal("argument")))
                ),
                new Ir.Expr.Function("functionAny", List.of(new Ir.Expr.Literal("argument", Type.STRING)), Type.ANY)
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Function("undefined", List.of())
                ),
                null //AnalyzeException
            ),
            // Additional Testcases
            Arguments.of("Extra Argument",
                new Input.Ast(
                    new Ast.Expr.Function("function",
                        List.of(new Ast.Expr.Literal("extra"))
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Missing Argument",
                new Input.Ast(
                    new Ast.Expr.Function("functionAny", List.of())
                ),
                null // AnalyzeException
            ),
            Arguments.of("Invalid Argument Type",
                new Input.Ast(
                    new Ast.Expr.Function("functionString",
                        List.of(new Ast.Expr.Literal(new BigInteger("123")))
                    )
                ),
                null // AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Variable("object"),
                        "methodAny",
                        List.of(new Ast.Expr.Literal("argument"))
                    )
                ),
                new Ir.Expr.Method(
                    new Ir.Expr.Variable("object", Environment.scope().get("object", true).get()),
                    "methodAny",
                    List.of(new Ir.Expr.Literal("argument", Type.STRING)),
                    Type.ANY
                )
            ),
            // Additional Testcases
            Arguments.of("Method DNE",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Variable("object"),
                        "undefinedMethod",
                        List.of()
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Non-Object Receiver",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Literal("not an object"),
                        "anyMethod",
                        List.of()
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Extra Argument",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Variable("object"),
                        "method",
                        List.of(new Ast.Expr.Literal("extra"))
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Invalid Argument Type",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Variable("object"),
                        "methodString",
                        List.of(new Ast.Expr.Literal(new BigInteger("123")))
                    )
                ),
                null // AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testObjectExpr() {
        Function<Map<String, Type>, Type.Object> createObjectType = types -> {
            var type = new Type.Object(new Scope(null));
            types.forEach(type.scope()::define);
            return type;
        };
        return Stream.of(
            Arguments.of("Empty",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of()
                    )
                ),
                new Ir.Expr.ObjectExpr(
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    createObjectType.apply(Map.of())
                )
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
                new Ir.Expr.Property(
                    new Ir.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(new Ir.Stmt.Let("field", Type.STRING, Optional.of(new Ir.Expr.Literal("value", Type.STRING)))),
                        List.of(),
                        createObjectType.apply(Map.of("field", Type.STRING))
                    ),
                    "field",
                    Type.STRING
                )
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
                new Ir.Expr.Method(
                    new Ir.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(new Ir.Stmt.Def("method", List.of(), Type.ANY, List.of())),
                        createObjectType.apply(Map.of("method", new Type.Function(List.of(), Type.ANY)))
                    ),
                    "method",
                    List.of(),
                    Type.ANY
                )
            ),
            // Additional Testcases
            Arguments.of("Forward Declaration",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(
                            new Ast.Stmt.Def(
                                "first",
                                List.of(),
                                List.of(),
                                Optional.empty(),
                                List.of(
                                    new Ast.Stmt.Expression(
                                        new Ast.Expr.Method(
                                            new Ast.Expr.Variable("this"),
                                            "second",
                                            List.of()
                                        )
                                    )
                                )
                            ),
                            new Ast.Stmt.Def(
                                "second",
                                List.of(),
                                List.of(),
                                Optional.empty(),
                                List.of()
                            )
                        )
                    )
                ),
                new Ir.Expr.ObjectExpr(
                    Optional.empty(),
                    List.of(),
                    List.of(
                        new Ir.Stmt.Def(
                            "first",
                            List.of(),
                            Type.ANY,
                            List.of(
                                new Ir.Stmt.Expression(
                                    new Ir.Expr.Method(
                                        new Ir.Expr.Variable("this", createObjectType.apply(Map.of(
                                            "first", new Type.Function(List.of(), Type.ANY),
                                            "second", new Type.Function(List.of(), Type.ANY)
                                        ))),
                                        "second",
                                        List.of(),
                                        Type.ANY
                                    )
                                )
                            )
                        ),
                        new Ir.Stmt.Def(
                            "second",
                            List.of(),
                            Type.ANY,
                            List.of()
                        )
                    ),
                    createObjectType.apply(Map.of(
                        "first", new Type.Function(List.of(), Type.ANY),
                        "second", new Type.Function(List.of(), Type.ANY)
                    ))
                )
            ),
            Arguments.of("Invalid Type Name",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.of("Integer"), // Using a reserved type name
                        List.of(),
                        List.of()
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Duplicate Field Names",
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
                null // AnalyzeException
            ),
            Arguments.of("Duplicate Method Names",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(
                            new Ast.Stmt.Def("method", List.of(), List.of(), Optional.empty(), List.of()),
                            new Ast.Stmt.Def("method", List.of(), List.of(), Optional.empty(), List.of())
                        )
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Type Mismatch in Field",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(new Ast.Stmt.Let("field", Optional.of("Integer"), Optional.of(new Ast.Expr.Literal("string")))),
                        List.of()
                    )
                ),
                null // AnalyzeException
            ),
            Arguments.of("Invalid Parameter Type",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(
                            new Ast.Stmt.Def(
                                "method",
                                List.of("param"),
                                List.of(Optional.of("InvalidType")),
                                Optional.empty(),
                                List.of()
                            )
                        )
                    )
                ),
                null // AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                //Input.Program makes tests *significantly* easier, but relies
                //on your Lexer and Parser being implemented correctly!
                new Input.Program("""
                    DEF main() DO
                        print("Hello, World!");
                    END
                    main();
                    """),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("main", List.of(), Type.ANY, List.of(
                        new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("Hello, World!", Type.STRING)), Type.NIL)
                    ))),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("main", List.of(), Type.ANY))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testRequireSubtype(String test, Type type, Type other, boolean expected) {
        try {
            Analyzer.requireSubtype(type, other);
            Assertions.assertTrue(expected, "Expected " + type + " to not be a subtype of " + other + ".");
        } catch (AnalyzeException e) {
            Assertions.assertFalse(expected, "Unexpected exception, expected " + type + " to be a subtype of " + other + ".");
        }
    }

    public static Stream<Arguments> testRequireSubtype() {
        return Stream.of(
            Arguments.of("Equal", Type.STRING, Type.STRING, true),
            Arguments.of("Subtype", Type.STRING, Type.ANY, true),
            Arguments.of("Supertype", Type.ANY, Type.STRING, false),
            Arguments.of("Nil Equal", Type.NIL, Type.NIL, true),
            Arguments.of("Nil Subtype", Type.NIL, Type.ANY, true),
            Arguments.of("Equatable Subtype", Type.STRING, Type.EQUATABLE, true),
            Arguments.of("Equatable Supertype", Type.ANY, Type.EQUATABLE, false),
            Arguments.of("Comparable Subtype", Type.STRING, Type.COMPARABLE, true),
            Arguments.of("Comparable Non-Subtype", Type.NIL, Type.COMPARABLE, false)
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
    private static void test(Input input, @Nullable Ir expected, ParserMethod<? extends Ast> method) {
        //First, get/parse the input AST.
        var ast = switch (input) {
            case Input.Ast i -> i.ast();
            case Input.Program i -> Assertions.assertDoesNotThrow(
                () -> method.invoke(new Parser(new Lexer(i.program).lex()))
            );
        };
        //Next, initialize the analyzer and scope.
        var scope = new Scope(Environment.scope());
        Analyzer analyzer = new Analyzer(scope);
        //Then, analyze the input and check the return value.
        try {
            var ir = analyzer.visit(ast);
            if (expected == null) {
                Assertions.fail("Expected an exception to be thrown, received " + ir + ".");
            }
            Assertions.assertEquals(expected, ir);
        } catch (AnalyzeException e) {
            if (expected != null) {
                Assertions.fail("Unexpected AnalyzeException thrown (" + e.getMessage() + "), expected " + expected + ".");
            }
        }
    }

}
