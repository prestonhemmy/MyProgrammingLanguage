package plc.project.parser;

import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast.Source parseSource() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    public Ast.Stmt parseStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.Let parseLetStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.If parseIfStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.For parseForStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.Return parseReturnStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    public Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr();
    }
    private Ast.Expr parseLogicalExpr() throws ParseException {
        // logical_expr ::= comparison_expr (('AND' | 'OR') comparison_expr)*
        Ast.Expr expr = parseComparisonExpr();

        while (tokens.peek("AND") || tokens.peek("OR")) {
            var operator = tokens.get(0).literal();
            tokens.match(operator);
            Ast.Expr right = parseComparisonExpr();
            expr = new Ast.Expr.Binary(operator, expr, right);
        }

        return expr;
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        // comparison_expr ::= additive_expr (('<' | '<=' | '>' | '>=' | '==' | '!=') additive_expr)*
        Ast.Expr expr = parseAdditiveExpr();

        while (tokens.peek("<") || tokens.peek("<=") || tokens.peek(">") ||
                tokens.peek(">=") || tokens.peek("==") || tokens.peek("!=")) {
            var operator = tokens.get(0).literal();
            tokens.match(operator);
            Ast.Expr right = parseAdditiveExpr();
            expr = new Ast.Expr.Binary(operator, expr, right);
        }

        return expr;
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        // additive_expr ::= multiplicative_expr (('+' | '-') multiplicative_expr)*
        Ast.Expr expr = parseMultiplicativeExpr();

        while (tokens.peek("+") || tokens.peek("-")) {
            var operator = tokens.get(0).literal();
            tokens.match(operator);
            Ast.Expr right = parseMultiplicativeExpr();
            expr = new Ast.Expr.Binary(operator, expr, right);
        }

        return expr;
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        // multiplicative_expr ::= secondary_expr (('*' | '/') secondary_expr)*
        Ast.Expr expr = parseSecondaryExpr();

        while (tokens.peek("*") || tokens.peek("/")) {
            var operator = tokens.get(0).literal();
            tokens.match(operator);
            Ast.Expr right = parseSecondaryExpr();
            expr = new Ast.Expr.Binary(operator, expr, right);
        }

        return expr;
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        // secondary_expr ::= primary_expr ('.' identifier ('(' (expr (',' expr)*)? ')')?)*
        Ast.Expr expr = parsePrimaryExpr();

        // Check if Method or Property
        while (tokens.match(".")) {
            var name = tokens.get(0).literal();
            if (!tokens.match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier after '.' but found " + tokens.get(0));
            }

            // Check if Method
            if (tokens.match("(")) {
                var arguments = new ArrayList<Ast.Expr>();
                if (!tokens.peek(")")) {
                    do {
                        arguments.add(parsePrimaryExpr());
                    } while (tokens.match(","));
                }
                // Trailing comma check needed?

                // Check for closing parenthesis
                if (!tokens.peek(")")) {
                    throw new ParseException("Expected ')' but found " + tokens.get(0));
                }
                expr = new Ast.Expr.Method(expr, name, arguments);

            // O.W. Property
            } else {
                expr = new Ast.Expr.Property(expr, name);
            }
        }

        return expr;
    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        // primary_expr ::= literal_expr | group_expr | object_expr | variable_or_function_expr
        if (tokens.peek("NIL") || tokens.peek("TRUE") || tokens.peek("FALSE") ||
                tokens.peek(Token.Type.INTEGER) || tokens.peek(Token.Type.DECIMAL) ||
                tokens.peek(Token.Type.CHARACTER) || tokens.peek(Token.Type.STRING)) {
            return parseLiteralExpr();
        } else if (tokens.peek(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        } else if (tokens.peek("(")) {
            return parseGroupExpr();
        } else {
            throw new UnsupportedOperationException("Expected primary expression but found " + tokens.get(0));
        }
    }

    private Ast.Expr.Literal parseLiteralExpr() throws ParseException {
        // literal_expr ::= 'NIL' | 'TRUE' | 'FALSE' | integer | decimal | character | string
        if (tokens.match("NIL")) {
            return new Ast.Expr.Literal(null);
        } else if (tokens.match( "TRUE")) {
            return new Ast.Expr.Literal(true);
        } else if (tokens.match( "FALSE")) {
            return new Ast.Expr.Literal(false);
        } else if (tokens.match( Token.Type.INTEGER)) {
            return new Ast.Expr.Literal(new BigInteger(tokens.get(-1).literal()));
        } else if (tokens.match( Token.Type.DECIMAL)) {
            return new Ast.Expr.Literal(new BigDecimal(tokens.get(-1).literal()));
        } else if (tokens.match( Token.Type.CHARACTER)) {
            String literal = tokens.get(-1).literal();
            char value = literal.charAt(1);
            if (value == '\\') {
                value = parseEscapeCharacter(literal.charAt(2));
            }

            return new Ast.Expr.Literal(value);
        } else if (tokens.match( Token.Type.STRING)) {
            String literal = tokens.get(-1).literal();
            String value = literal.substring(1, literal.length() - 1);
            value = value.replace("\\b", "\b")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        .replace("\\'", "'")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");

            return new Ast.Expr.Literal(value);
        } else {
            throw new ParseException("Expected a valid token, but found " + tokens.get(0));     // Reachable?
        }
    }

    /** Helper function for parseLiteralExpr() */
    private char parseEscapeCharacter(char c) throws ParseException {
        return switch (c) {
            case 'b' -> '\b';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '\'' -> '\'';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> throw new ParseException("Invalid escape character: \\" + c);
        };
    }

    private Ast.Expr.Group parseGroupExpr() throws ParseException {
        // group_expr ::= '(' expr')'
        if (tokens.match("(")) {
//            Ast.Expr expr = parseExpr();        // Change to parsePrimaryExpr() once implemented
            Ast.Expr expr = parsePrimaryExpr();     // Updated
            if (!tokens.match(")")) {
                throw new ParseException("Expected ')' but found " + tokens.get(0));
            }

            return new Ast.Expr.Group(expr);
        } else {
            throw new ParseException("Expected '(' but found " + tokens.get(0));
        }
    }

    // SKIP IMPLEMENTATION FOR NOW
    private Ast.Expr.ObjectExpr parseObjectExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        // variable_or_function_expr ::= identifier ('(' (expr (',' expr)*)? ')')?
        checkState(tokens.match(Token.Type.IDENTIFIER));        // checkState() needed?
        var name = tokens.get(-1).literal();

        // Check if function
        if (tokens.match("(")) {
            var arguments = new ArrayList<Ast.Expr>();

            if (!tokens.peek(")")) {
                do {
                    arguments.add(parsePrimaryExpr());    // Use this when parsePrimaryExpr() has been implemented
//                    arguments.add(parseExpr());             // Use for current implementation testing
                } while (tokens.match(","));

                // Check for trailing comma in arguments
                if (tokens.peek(",")) {     // TODO: CURRENTLY UNREACHABLE: Error message defaults to parseExpr()
                    throw new ParseException("Expected a comma separated list of arguments but trailing comma");
                }
            }

            // Check for closing parenthesis
            if (!tokens.match(")")) {
                throw new ParseException("Expected ')' but found " + tokens.get(0));
            }

            return new Ast.Expr.Function(name, arguments);
        }

        // O.W. variable
        return new Ast.Expr.Variable(name);
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }

    }

}
