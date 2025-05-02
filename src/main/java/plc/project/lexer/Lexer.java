package plc.project.lexer;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * The lexer works through a combination of {@link #lex()}, which repeatedly
 * calls {@link #lexToken()} and skips over whitespace/comments, and
 * {@link #lexToken()}, which determines the type of the next token and
 * delegates to the corresponding lex method.
 *
 * <p>Additionally, {@link CharStream} manages the lexer state and contains
 * {@link CharStream#peek} and {@link CharStream#match}. These are helpful
 * utilities for working with character state and building tokens.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() throws LexException {
        var tokens = new ArrayList<Token>();

        while (chars.has(0)) {
            lexWhitespace();

            lexComment();

            if (!chars.has(0)) {
                break;
            }

            tokens.add(lexToken());
        }

        return tokens;
    }

    private void lexWhitespace() {
        // whitespace ::= [\b\n\r\t]+
        while (chars.has(0)) {
            char c = chars.input.charAt(chars.index);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\b') {
                chars.index++;
            } else {
                break;
            }
        }

        // reset length to skip
        chars.length = 0;
    }

    private void lexComment() {
        // comment ::= '/''/'[^\n\r]*
        if (chars.has(1) && chars.input.charAt(chars.index) == '/' && chars.input.charAt(chars.index + 1) == '/') {
            chars.index += 2;

            while (chars.has(0)) {
                char c = chars.input.charAt(chars.index);
                if (c == '\n' || c == '\r') {
                    break;
                }

                chars.index++;
            }

            // reset the length to skip
            chars.length = 0;

            lexWhitespace();
            if (chars.has(1) && chars.input.charAt(chars.index) == '/' && chars.input.charAt(chars.index + 1) == '/') {
                lexComment();
            }
        }
    }

    private Token lexToken() throws LexException {
        if (chars.peek("[A-Za-z_]")) {
            return lexIdentifier();
        } else if (chars.peek("[+\\-0-9]")) {
            return lexNumber();
        } else if (chars.match("'")) {
            return lexCharacter();
        } else if (chars.match("\"")) {
            return lexString();
        } else {
            return lexOperator();
        }
    }

    private Token lexIdentifier() {
        // identifier ::= [A-Za-z_] [A-Za-z0-9_-]*
        checkState(chars.match("[A-Za-z_]"));

        // Repeatedly check for match with A-Za-z0-9_-
        while (chars.match("[A-Za-z0-9_-]")) {}

        return new Token(Token.Type.IDENTIFIER, chars.emit());
    }

    private Token lexNumber() throws LexException {
        // number ::= [+\-]? [0-9]+ ('.' [0-9]+)? ('e' [0-9]+)?
        boolean hasSign = chars.match("[+\\-]");

        // Check integer digits exist
        if (!chars.peek("[0-9]")) {
            if (hasSign) {
                return new Token(Token.Type.OPERATOR, chars.emit());
            } else {
                throw new LexException("Invalid number: missing digits");
            }
        }

        while (chars.match("[0-9]")) {}

        boolean isDecimal = chars.match("\\.");
        if (isDecimal) {
            // Check decimal digits exist
            if (!chars.match("[0-9]")) {
                chars.index--;
                chars.length--;
                return new Token(Token.Type.INTEGER, chars.emit());
            }

            while (chars.match("[0-9]")) {}
        }

        int saved_index = chars.index;
        int saved_length = chars.length;

        if (chars.match("e")) {
            // Check exponent digits exist
            if (!chars.match("[0-9]")) {
                chars.index = saved_index;
                chars.length = saved_length;

                return isDecimal ? new Token(Token.Type.DECIMAL, chars.emit()) : new Token(Token.Type.INTEGER, chars.emit());
            }

            while (chars.match("[0-9]")) {}
        }

        return isDecimal ? new Token(Token.Type.DECIMAL, chars.emit()) : new Token(Token.Type.INTEGER, chars.emit());
    }

    private Token lexCharacter() throws LexException {
        // character ::= ['] ([^'\n\r\\] | escape) [']
        if (chars.peek("'")) {
            throw new LexException("Invalid character literal: empty");
        } else if (chars.peek("[\\n\\r]")) {
            throw new LexException("Invalid character literal: NL or CR");
        } else if (chars.match("\\\\")) {
            lexEscape();
        } else {
            chars.match(".");
        }

        if (!chars.match("'")) {
            throw new LexException("Invalid character literal: missing closing single quote");
        }

        return new Token(Token.Type.CHARACTER, chars.emit());
    }

    private Token lexString() throws LexException {
        // string ::= '"' ([^"\n\r\\] | escape)* '"'
        if (!chars.has(0)) {
            throw new LexException("Invalid character literal: unterminated string");
        }

        while (!chars.peek("\"")) {
            if (chars.peek("[\\n\\r]")) {
                throw new LexException("Invalid string literal: NL or CR");
            }
            if (chars.match("\\\\")) {
                lexEscape();
            } else {
                chars.match(".");
            }

            if (!chars.has(0)) {
                throw new LexException("Invalid character literal: unterminated string");
            }
        }

        chars.match("\"");

        return new Token(Token.Type.STRING, chars.emit());
    }

    private void lexEscape() throws LexException {
        // escape ::= '\' [bnrt'"\]
        if (!chars.match("[bnrt'\"\\\\]")) {
            throw new LexException("Invalid escape character");
        }
    }

    public Token lexOperator() throws LexException {
        // operator ::= [<>!=] '='? | 'any other character'
        if (chars.match("[<>!=]")) {
            chars.match("=");
        } else {
            if (chars.match("\\n")) {
//                throw new LexException("Invalid operator: newline");
                lexWhitespace();
            }

            chars.match(".");
        }

        return new Token(Token.Type.OPERATOR, chars.emit());
    }

    /**
     * A helper class for maintaining the state of the character stream (input)
     * and methods for building up token literals.
     */
    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        /**
         * Returns true if there is a character at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < input.length();
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is a regex matching only ONE character!
         */
        public boolean peek(String... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var character = input.charAt(index + offset);
                if (!String.valueOf(character).matches(patterns[offset])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the character stream.
         */
        public boolean match(String... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
                length += patterns.length;
            }
            return peek;
        }

        /**
         * Returns the literal built by all characters matched since the last
         * call to emit(); also resetting the length for subsequent tokens.
         */
        public String emit() {
            var literal = input.substring(index - length, index);
            length = 0;
            return literal;
        }

    }

}
