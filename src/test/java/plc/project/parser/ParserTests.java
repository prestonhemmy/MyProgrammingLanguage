package plc.project.parser;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.lexer.Token;

import javax.swing.text.html.Option;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the last project part for more information.
 */
final class ParserTests {

    public sealed interface Input {
        record Tokens(List<Token> tokens) implements Input {}
        record Program(String program) implements Input {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, Ast.Source expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Single",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "stmt"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt"))
                ))
            ),
            Arguments.of("Multiple",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "third"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("first")),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("second")),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("third"))
                ))
            ),
            // Additional Testcase
            Arguments.of("Empty",
                new Input.Tokens(List.of(
                    new Token(Token.Type.OPERATOR, ";")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Input input, Ast.Stmt.Let expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Let("name", Optional.empty())
            ),
            Arguments.of("Initialization",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Let("name", Optional.of(new Ast.Expr.Variable("expr")))
            ),
            Arguments.of("Missing Semicolon",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "expr")
                )),
                null //ParseException
            ),
            // Additional Testcase
            Arguments.of("Complex Initialization",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "a"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "b"),
                    new Token(Token.Type.OPERATOR, "*"),
                    new Token(Token.Type.IDENTIFIER, "c"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Let("name", Optional.of(
                    new Ast.Expr.Binary("+",
                        new Ast.Expr.Variable("a"),
                        new Ast.Expr.Binary("*",
                            new Ast.Expr.Variable("b"),
                            new Ast.Expr.Variable("c")
                        )
                    )
                ))
            ),
            Arguments.of("Missing Name",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                null // ParseException
            ),
            Arguments.of("Missing Value",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                null // ParseException
            ),
            Arguments.of("Missing Value and Semicolon",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "=")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, Ast.Stmt.Def expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Base",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.Def("name", List.of(), List.of())
            ),
            Arguments.of("Parameter",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "parameter"),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.Def("name", List.of("parameter"), List.of())
            ),
            // Additional Testcase
            Arguments.of("Missing DO Keyword",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Input input, Ast.Stmt.If expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("If",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "then"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond"),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                    List.of()
                )
            ),
            Arguments.of("Else",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "then"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "ELSE"),
                    new Token(Token.Type.IDENTIFIER, "else"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond"),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("else")))
                )
            ),
            // Additional Testcases
            Arguments.of("Empty",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond"),
                    List.of(),
                    List.of()
                )
            ),
            Arguments.of("Nested",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond1"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond2"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "then"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond1"),
                    List.of(
                        new Ast.Stmt.If(
                            new Ast.Expr.Variable("cond2"),
                            List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                            List.of()
                        )
                    ),
                    List.of()
                )
            ),
            Arguments.of("Inner Nested Else",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond1"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond2"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "then"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "ELSE"),
                    new Token(Token.Type.IDENTIFIER, "else"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond1"),
                    List.of(
                        new Ast.Stmt.If(
                            new Ast.Expr.Variable("cond2"),
                            List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                            List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("else")))
                        )
                    ),
                    List.of()
                )
            ),
            Arguments.of("Outer Nested Else",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond1"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond2"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "then"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END"),
                    new Token(Token.Type.IDENTIFIER, "ELSE"),
                    new Token(Token.Type.IDENTIFIER, "else"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond1"),
                    List.of(
                        new Ast.Stmt.If(
                            new Ast.Expr.Variable("cond2"),
                            List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                            List.of()
                        )
                    ),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("else")))
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, Ast.Stmt.For expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "FOR"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.IDENTIFIER, "IN"),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "stmt"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.For(
                    "name",
                    new Ast.Expr.Variable("expr"),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt")))
                )
            ),
            Arguments.of("Missing In",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "FOR"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "stmt"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                null //ParseException
            ),
            // Additional Testcases
            Arguments.of("Empty Body",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "FOR"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.IDENTIFIER, "IN"),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Stmt.For(
                    "name",
                    new Ast.Expr.Variable("expr"),
                    List.of()
                )
            ),
            Arguments.of("Missing END Keyword",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "FOR"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.IDENTIFIER, "IN"),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "stmt"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, Ast.Stmt.Return expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            Arguments.of("Return",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "RETURN"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Return(Optional.empty())
            ),
            // Additional Testcases
            Arguments.of("Return Value",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "RETURN"),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("expr")))
            ),
            Arguments.of("Missing Semicolon After Value",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "RETURN"),
                    new Token(Token.Type.IDENTIFIER, "expr")
                )),
                null // ParseException
            ),
            Arguments.of("Missing Semicolon After RETURN",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "RETURN")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, Ast.Stmt.Expression expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "variable"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
            ),
            Arguments.of("Function",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Expression(new Ast.Expr.Function("function", List.of()))
            ),
            Arguments.of("Missing Semicolon",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "variable")
                )),
                null //ParseException
            ),
            // Additional Testcases
            Arguments.of("Invalid Expression",
                new Input.Tokens(List.of(
                    new Token(Token.Type.OPERATOR, "?"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, Ast.Stmt.Assignment expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "variable"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "value"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Assignment(
                    new Ast.Expr.Variable("variable"),
                    new Ast.Expr.Variable("value")
                )
            ),
            Arguments.of("Property",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "object"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "property"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "value"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                new Ast.Stmt.Assignment(
                    new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property"),
                    new Ast.Expr.Variable("value")
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, Ast.Expr.Literal expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Nil",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "NIL")
                )),
                new Ast.Expr.Literal(null)
            ),
            Arguments.of("Boolean",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "TRUE")
                )),
                new Ast.Expr.Literal(true)
            ),
            Arguments.of("Integer",
                new Input.Tokens(List.of(
                    new Token(Token.Type.INTEGER, "1")
                )),
                new Ast.Expr.Literal(new BigInteger("1"))
            ),
            Arguments.of("Decimal",
                new Input.Tokens(List.of(
                    new Token(Token.Type.DECIMAL, "1.0")
                )),
                new Ast.Expr.Literal(new BigDecimal("1.0"))
            ),
            Arguments.of("Character",
                new Input.Tokens(List.of(
                    new Token(Token.Type.CHARACTER, "\'c\'")
                )),
                new Ast.Expr.Literal('c')
            ),
            Arguments.of("String",
                new Input.Tokens(List.of(
                    new Token(Token.Type.STRING, "\"string\"")
                )),
                new Ast.Expr.Literal("string")
            ),
            Arguments.of("String Newline Escape",
                new Input.Tokens(List.of(
                    new Token(Token.Type.STRING, "\"Hello,\\nWorld!\"")
                )),
                new Ast.Expr.Literal("Hello,\nWorld!")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Input input, Ast.Expr.Group expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Input.Tokens(List.of(
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, ")")
                )),
                new Ast.Expr.Group(new Ast.Expr.Variable("expr"))
            ),
            // Additional Testcases
            Arguments.of("Int Addition Expression",
                new Input.Tokens(List.of(
                        new Token(Token.Type.OPERATOR, "("),
                        new Token(Token.Type.INTEGER, "1"),
                        new Token(Token.Type.OPERATOR, "+"),
                        new Token(Token.Type.INTEGER, "2"),
                        new Token(Token.Type.OPERATOR, ")")
                )),
                new Ast.Expr.Group(
                        new Ast.Expr.Binary(
                            "+",
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("2"))
                        )
                )
            ),
            Arguments.of("Missing Expression",
                    new Input.Tokens(List.of(
                            new Token(Token.Type.OPERATOR, "("),
                            new Token(Token.Type.OPERATOR, ")")
                    )),
                    null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Input input, Ast.Expr.Binary expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Addition",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "left"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "right")
                )),
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Variable("left"),
                    new Ast.Expr.Variable("right")
                )
            ),
            Arguments.of("Multiplication",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "left"),
                    new Token(Token.Type.OPERATOR, "*"),
                    new Token(Token.Type.IDENTIFIER, "right")
                )),
                new Ast.Expr.Binary(
                    "*",
                    new Ast.Expr.Variable("left"),
                    new Ast.Expr.Variable("right")
                )
            ),
            Arguments.of("Equal Precedence",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "third")
                )),
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Variable("first"),
                        new Ast.Expr.Variable("second")
                    ),
                    new Ast.Expr.Variable("third")
                )
            ),
            Arguments.of("Lower Precedence",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, "*"),
                    new Token(Token.Type.IDENTIFIER, "third")
                )),
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Variable("first"),
                    new Ast.Expr.Binary(
                        "*",
                        new Ast.Expr.Variable("second"),
                        new Ast.Expr.Variable("third")
                    )
                )
            ),
            Arguments.of("Higher Precedence",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, "*"),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "third")
                )),
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Binary(
                        "*",
                        new Ast.Expr.Variable("first"),
                        new Ast.Expr.Variable("second")
                    ),
                    new Ast.Expr.Variable("third")
                )
            ),
            // Additional Testcases
            Arguments.of("Logical Missing Operand",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.IDENTIFIER, "AND")
                )),
                null // ParseException
            ),
            Arguments.of("Comparison Missing Operand",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, "==")
                )),
                null // ParseException
            ),
            Arguments.of("Multiplicative Missing Operand",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, "*")
                )),
                null // ParseException
            ),
            Arguments.of("Additive Missing Operand",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, "+")
                )),
                null // ParseException
            ),
            Arguments.of("Missing Left Operand",
                new Input.Tokens(List.of(
                    new Token(Token.Type.OPERATOR, "-"),
                    new Token(Token.Type.IDENTIFIER, "expr")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Input input, Ast.Expr.Variable expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "variable")
                )),
                new Ast.Expr.Variable("variable")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, Ast.Expr.Property expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "property")
                )),
                new Ast.Expr.Property(
                    new Ast.Expr.Variable("receiver"),
                    "property"
                )
            ),
            // Additional Testcases
            Arguments.of("Missing Identifier After Dot",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, ".")
                )),
                null // ParseException
            ),
            Arguments.of("Invalid Identifier After Dot",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.OPERATOR, "+")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Input input, Ast.Expr.Function expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")")
                )),
                new Ast.Expr.Function("function", List.of())
            ),
            Arguments.of("Argument",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "argument"),
                    new Token(Token.Type.OPERATOR, ")")
                )),
                new Ast.Expr.Function("function", List.of(
                    new Ast.Expr.Variable("argument")
                ))
            ),
            // Additional Testcases
            Arguments.of("Multiple Arguments",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "argument1"),
                    new Token(Token.Type.IDENTIFIER, ","),
                    new Token(Token.Type.IDENTIFIER, "argument2"),
                    new Token(Token.Type.IDENTIFIER, ","),
                    new Token(Token.Type.IDENTIFIER, "argument3"),
                    new Token(Token.Type.OPERATOR, ")")
                )),
                new Ast.Expr.Function("function", List.of(
                    new Ast.Expr.Variable("argument1"),
                    new Ast.Expr.Variable("argument2"),
                    new Ast.Expr.Variable("argument3")
                ))
            ),
            Arguments.of("Trailing Comma",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "argument1"),
                    new Token(Token.Type.IDENTIFIER, ","),
                    new Token(Token.Type.IDENTIFIER, "argument2"),
                    new Token(Token.Type.IDENTIFIER, ","),
                    new Token(Token.Type.IDENTIFIER, "argument3"),
                    new Token(Token.Type.IDENTIFIER, ","),
                    new Token(Token.Type.OPERATOR, ")")
                )),
                null // ParseException
            ),
            Arguments.of("Missing Closing Parenthesis",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "argument")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, Ast.Expr.Method expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")")
                )),
                new Ast.Expr.Method(
                    new Ast.Expr.Variable("receiver"),
                    "method",
                    List.of()
                )
            ),
            // Additional Testcases
            Arguments.of("Trailing Comma",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "argument"),
                    new Token(Token.Type.OPERATOR, ","),
                    new Token(Token.Type.OPERATOR, ")")
                )),
                null // ParseException
            ),
            Arguments.of("Missing Closing Parenthesis",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "argument")
                )),
                null // ParseException
            ),
            Arguments.of("Nested Method Call",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "outer"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "inner"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.OPERATOR, ")")
                )),
                new Ast.Expr.Method(
                    new Ast.Expr.Variable("outer"),
                    "method",
                    List.of(
                        new Ast.Expr.Method(
                            new Ast.Expr.Variable("inner"),
                            "method",
                            List.of()
                        )
                    )
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, Ast.Expr.ObjectExpr expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testObjectExpr() {
        return Stream.of(
            Arguments.of("Field",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "OBJECT"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "field"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Expr.ObjectExpr(
                    Optional.empty(),
                    List.of(new Ast.Stmt.Let("field", Optional.empty())),
                    List.of()
                )
            ),
            Arguments.of("Method",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "OBJECT"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                new Ast.Expr.ObjectExpr(
                    Optional.empty(),
                    List.of(),
                    List.of(new Ast.Stmt.Def("method", List.of(), List.of()))
                )
            ),
            // Additional Testcases
            Arguments.of("Missing DO",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "OBJECT"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                null // ParseException
            ),
            Arguments.of("Field After Method",
                // OBJECT DO
                //   DEF method() DO
                //   END
                //   LET field;
                // END
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "OBJECT"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END"),
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "field"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                )),
                null // ParseException
            ),
            Arguments.of("Missing END",
                new Input.Tokens(List.of(
                    new Token(Token.Type.IDENTIFIER, "OBJECT"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "field"),
                    new Token(Token.Type.OPERATOR, ";")
                )),
                null // ParseException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, Ast.Source expected) {
        test(input, expected, Parser::parseSource);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                //Input.Program makes this much easier, but relies on your Lexer
                //being implemented correctly!
                new Input.Program("""
                    DEF main() DO
                        print("Hello, World!");
                    END
                    """),
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("main", List.of(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function(
                            "print",
                            List.of(new Ast.Expr.Literal("Hello, World!"))
                        ))
                    ))
                ))
            )
        );
    }

    interface ParserMethod<T> {
        T invoke(Parser parser) throws ParseException;
    }

    /**
     * Test function for the Parser. The {@link Input} parameter handles parser
     * input, which may either be a direct list of tokens or a String program.
     * Using a String program is easier for tests, but relies on your Lexer
     * working properly!
     */
    private static <T extends Ast> void test(Input input, @Nullable T expected, ParserMethod<T> method) {
        var tokens = switch (input) {
            case Input.Tokens i -> i.tokens();
            case Input.Program i -> Assertions.assertDoesNotThrow(() -> new Lexer(i.program).lex());
        };
        Parser parser = new Parser(tokens);
        if (expected != null) {
            var ast = Assertions.assertDoesNotThrow(() -> method.invoke(parser));
            Assertions.assertEquals(expected, ast);
        } else {
            Assertions.assertThrows(ParseException.class, () -> method.invoke(parser));
        }
    }

}
