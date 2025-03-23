package plc.project.parser;

import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        // source ::= stmt*
        var statements = new ArrayList<Ast.Stmt>();

        while (tokens.has(0)) {
            statements.add(parseStmt());
        }

        return new Ast.Source(statements);
    }

    public Ast.Stmt parseStmt() throws ParseException {
        // new Token(Token.Type.IDENTIFIER, "literal")
        if (tokens.peek("LET")) {
            return parseLetStmt();
        } else if (tokens.peek("DEF", Token.Type.IDENTIFIER)) {
            return parseDefStmt();
        } else if (tokens.peek("IF") && !tokens.peek("IF", ";")) {
            return parseIfStmt();
        } else if (tokens.peek("FOR", Token.Type.IDENTIFIER)) {
            return parseForStmt();
        } else if (tokens.peek("RETURN")) {
            return parseReturnStmt();
        } else {
            return parseExpressionOrAssignmentStmt();
        }
    }

    private Ast.Stmt.Let parseLetStmt() throws ParseException {
        // let_stmt ::= 'LET' identifier ('=' expr)? ';'
        checkState(tokens.match("LET"));

        // Handle missing identifier
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected IDENTIFIER after 'LET' but found " + tokens.get(0));
        }

        var name = tokens.get(-1).literal();

        // Check if Initialization O.W. Declaration
        Optional<Ast.Expr> value = Optional.empty();
        if (tokens.match("=")) {
            if (!tokens.has(0)) {
                throw new ParseException("Expected expression after '=' but found nothing");
            }

            // Handle missing value
            if (tokens.peek(";")) {
                throw new ParseException("Expected expression after '=' but found ';'");
            }

            value = Optional.of(parseExpr());
        }

        requireStatementTerminator();

        return new Ast.Stmt.Let(name, value);
    }

    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        // def_stmt ::= 'DEF' identifier '(' (identifier (',' identifier)*)? ')' 'DO' stmt* 'END'
        checkState(tokens.match("DEF"));
        checkState(tokens.match(Token.Type.IDENTIFIER));

        var name = tokens.get(-1).literal();

        if (!tokens.match("(")) {
            throw new ParseException("Expected '(' but found " + tokens.get(0));
        }

        var parameters = new ArrayList<String>();

        // Check for parameters
        if (!tokens.peek(")")) {
            do {
                if (!tokens.match(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Expected identifier but found " + tokens.get(0));
                }

                var param = tokens.get(-1).literal();
                parameters.add(param);
            } while (tokens.match(","));
        }

        if (!tokens.match(")")) {
            throw new ParseException("Expected ')' but found " + tokens.get(0));
        }

        if (!tokens.match("DO")) {
            throw new ParseException("Expected DO but found " + tokens.get(0));
        }

        // Consume body if exists
        var body = new ArrayList<Ast.Stmt>();
        while (!tokens.peek("END")) {
            if (!tokens.has(0)) {
                throw new ParseException("Expected statement or 'END' but found neither.");
            }

            body.add(parseStmt());
        }

        if (!tokens.match("END")) {
            throw new ParseException("Expected END but found " + tokens.get(0));
        }

        return new Ast.Stmt.Def(name, parameters, body);
    }

    private Ast.Stmt.If parseIfStmt() throws ParseException {
        // if_stmt ::= 'IF' expr 'DO' stmt* ('ELSE' stmt*)? 'END'
        checkState(tokens.match("IF"));

        // Consume condition
        Ast.Expr condition = parseExpr();
        if (!tokens.match("DO")) {
            throw new ParseException("Expected DO but found " + tokens.get(0));
        }

        // Initialize and consume then body
        var statements = new ArrayList<Ast.Stmt>();
        while (!tokens.peek("ELSE") && !tokens.peek("END")) {
            if (!tokens.has(0)) {
                throw new ParseException("Expected statement, 'ELSE' or 'END' but found nothing.");
            }

            statements.add(parseStmt());
        }

        // Initialize o.w. body
        var statementsOtherwise = new ArrayList<Ast.Stmt>();

        // Check if ELSE clause exists
        if (tokens.match("ELSE")) {
            while (!tokens.peek("END")) {
                if (!tokens.has(0)) {
                    throw new ParseException("Expected statement or 'END' but found neither.");
                }

                statementsOtherwise.add(parseStmt());
            }
        }

        if (!tokens.match("END")) {
            throw new ParseException("Expected END but found " + tokens.get(0));
        }

        return new Ast.Stmt.If(condition, statements, statementsOtherwise);
    }

    private Ast.Stmt.For parseForStmt() throws ParseException {
        // for_stmt ::= 'FOR' identifier 'IN' expr 'DO' stmt* 'END'
        checkState(tokens.match("FOR"));
        checkState(tokens.match(Token.Type.IDENTIFIER));

        var name = tokens.get(-1).literal();

        if (!tokens.match("IN")) {
            throw new ParseException("Expected IN but found " + tokens.get(0));
        }

        // Consume iterable
        Ast.Expr expr = parseExpr();
        if (!tokens.match("DO")) {
            throw new ParseException("Expected DO but found " + tokens.get(0));
        }

        // Initialize and consume loop body
        var statements = new ArrayList<Ast.Stmt>();
        while (!tokens.peek("END")) {
            if (!tokens.has(0)) {
                throw new ParseException("Expected statement or 'END' but found neither.");
            }

            statements.add(parseStmt());
        }

        if (!tokens.match("END")) {
            throw new ParseException("Expected END but found " + tokens.get(0));
        }

        return new Ast.Stmt.For(name, expr, statements);
    }

    private Ast.Stmt.Return parseReturnStmt() throws ParseException {
        // return_stmt ::= 'RETURN' expr? ';'
        checkState(tokens.match("RETURN"));     // checkState() needed?

        // Check if return expression O.W. return empty
        Optional<Ast.Expr> expr = Optional.empty();
        if (!tokens.peek(";") && tokens.has(0)) {
            expr = Optional.of(parseExpr());
        }

        requireStatementTerminator();

        return new Ast.Stmt.Return(expr);
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        // expression_or_assignment_stmt ::= expr ('=' expr)? ';'
        // Handle missing expression
        if (tokens.peek(";")) {
            throw new ParseException("Expected expression but found nothing");
        }


        Ast.Expr expr = parseExpr();

        // Check if Assignment
        if (tokens.match("=")) {
            Ast.Expr right = parseExpr();

            // Check statement terminator
            requireStatementTerminator();

            return new Ast.Stmt.Assignment(expr, right);
        }

        // O.W. Expression
        requireStatementTerminator();

        return new Ast.Stmt.Expression(expr);
    }

    /** Helper function for parseStmt methods.
     * Checks if ends with semicolon, throwing an error if not. */
    private void requireStatementTerminator() throws ParseException {
        if (!tokens.match(";")) {
            if (!tokens.has(0)) {
                throw new ParseException("Expected ';' but found nothing");
            }

            throw new ParseException("Expected ';' but found " + tokens.get(0));
        }
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
            // Handle missing identifier
            if (!tokens.has(0)) {
                throw new ParseException("Expected identifier after '.' but found nothing");
            }

            var name = tokens.get(0).literal();

            // Handle invalid identifier
            if (!tokens.match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier after '.' but found " + tokens.get(0));
            }

            // Check if Method
            if (tokens.match("(")) {
                var arguments = new ArrayList<Ast.Expr>();
                if (!tokens.peek(")")) {
                    do {
                        arguments.add(parseExpr());
                    } while (tokens.match(","));
                }
                // Trailing comma check needed?

                // Check for closing parenthesis
                if (!tokens.match(")")) {
                    if (!tokens.has(0)) {
                        throw new ParseException("Expected ')' but found nothing");
                    }
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
        } else if (tokens.peek("OBJECT")) {
            return parseObjectExpr();
        } else if (tokens.peek(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        } else if (tokens.peek("(")) {
            return parseGroupExpr();
        } else {
            if (!tokens.has(0)) {
                throw new ParseException("Expected primary expression but found nothing");
            }
            throw new ParseException("Expected primary expression but found " + tokens.get(0));
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
        } else if (tokens.peek( Token.Type.INTEGER) || tokens.peek(Token.Type.DECIMAL)) {
            boolean isInt = tokens.peek(Token.Type.INTEGER);
            tokens.match(isInt ? Token.Type.INTEGER : Token.Type.DECIMAL);

            var literal = tokens.get(-1).literal();

            if (tokens.has(0) && tokens.peek(Token.Type.IDENTIFIER)) {
                var next_token = tokens.get(0).literal();

                // check if negative exponent (decimal)
                if (next_token.startsWith("e-")) {
                    tokens.match(Token.Type.IDENTIFIER);
                    literal = literal + next_token;

                    // assume decimal
                    try {
                        BigDecimal decimal = new BigDecimal(literal);
                        return new Ast.Expr.Literal(decimal);
                    } catch (NumberFormatException e) {
                        throw new ParseException("Invalid number format: " + literal);
                    }
                }
            }

            // O.W. non-negative exponent integer or decimal
            try {
                if (isInt) {
                    // assume integer
                    try {
                        return new Ast.Expr.Literal(new BigInteger(literal));
                    } catch (NumberFormatException e) {
                        // check for exponent
                        if (literal.toLowerCase().contains("e")) {
                            BigDecimal decimal = new BigDecimal(literal);

                            // check if exponent integer
                            try {
                                return new Ast.Expr.Literal(decimal.toBigIntegerExact());
                            } catch (ArithmeticException e1) {

                                // O.W. exponent decimal
                                return new Ast.Expr.Literal(decimal);
                            }
                        } else {
                            throw new ParseException("Unable to parse " + literal + " as a number");        // Reachable?
                        }
                    }
                } else {
                    // O.W. decimal
                    return new Ast.Expr.Literal(new BigDecimal(literal));
                }
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid number format: " + literal);
            }


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
        checkState(tokens.match("("));

        Ast.Expr expr = parseExpr();

        if (!tokens.has(0)) {
            throw new ParseException("Expected ')' but found nothing");
        }

        if (!tokens.match(")")) {
            throw new ParseException("Expected ')' but found " + tokens.get(0));
        }

        return new Ast.Expr.Group(expr);
    }

    private Ast.Expr.ObjectExpr parseObjectExpr() throws ParseException {
        // object_expr ::= 'OBJECT' identifier? 'DO' let_stmt* def_stmt* 'END'
        checkState(tokens.match("OBJECT"));

        Optional<String> name = Optional.empty();
        if (!tokens.peek("DO")) {
            if (tokens.match(Token.Type.IDENTIFIER)) {
                name = Optional.of(tokens.get(-1).literal());
            }
        }

        if (!tokens.match("DO")) {
            throw new ParseException("Expected DO but found " + tokens.get(0));
        }

        var fields = new ArrayList<Ast.Stmt.Let>();
        var methods = new ArrayList<Ast.Stmt.Def>();

        boolean def_stmt_reached = false;

        while (!tokens.peek("END")) {
            if (tokens.peek("LET")) {
                // Handle field declaration after method declaration
                if (def_stmt_reached) {
                    throw new ParseException("Fields must be declared before method declarations");
                }
                fields.add(parseLetStmt());
            } else if (tokens.peek("DEF")) {
                def_stmt_reached = true;
                methods.add(parseDefStmt());
            } else {
                if (tokens.has(0)) {
                    throw new ParseException("Expected 'LET', 'DEF' or 'END' but found " + tokens.get(0));
                }
                throw new ParseException("Expected 'LET', 'DEF', 'END' but found nothing");
            }
        }

        if (!tokens.match("END")) {
            throw new ParseException("Expected END but found " + tokens.get(0));
        }

        return new Ast.Expr.ObjectExpr(name, fields, methods);
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
                    arguments.add(parseExpr());
                } while (tokens.match(","));
            }

            // Check for closing parenthesis
            if (!tokens.match(")")) {
                if (!tokens.has(0)) {
                    throw new ParseException("Expected ')' but found nothing");
                }
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
