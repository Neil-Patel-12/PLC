package plc.project.lexer;

import java.util.ArrayList;
import java.util.List;

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
            if (chars.peek("[ \\n\\r\\t]")) {
                lexWhitespace();
            } else if (chars.peek("/", "/")) {
                lexComment();
            } else {
                tokens.add(lexToken());
            }
        }
        return tokens;
    }

    private void lexWhitespace() {
        while (chars.match("[ \\n\\r\\t]")) {
            // skip
        }
        // reset token length
        chars.skip();
    }

    private void lexComment() {
        chars.match("/", "/");
        while (chars.has(0) && !chars.peek("[\\n\\r]")) {
            chars.advance();
        }
        // reset token length
        chars.skip();
    }

    private Token lexToken() throws LexException {
        if (chars.peek("[A-Za-z_]")) {
            return lexIdentifier();
        } else if (chars.peek("[+-]", "[0-9]") || chars.peek("[0-9]")) {
            return lexNumber();
        } else if (chars.peek("'")) {
            return lexCharacter();
        } else if (chars.peek("\"")) {
            return lexString();
        } else {
            return lexOperator();
        }
    }

    private Token lexIdentifier() {
        chars.match("[A-Za-z_]");
        while (chars.match("[A-Za-z0-9_-]")) {
            // consume
        }
        return chars.emit(Token.Type.IDENTIFIER);
    }

    private Token lexNumber() {
        chars.match("[+-]");

        while (chars.match("[0-9]")) {
            // integer part
        }

        boolean decimal = false;

        if (chars.peek("\\.", "[0-9]")) {
            decimal = true;
            chars.match("\\.");
            while (chars.match("[0-9]")) {
                // decimal part
            }
        }

        if (chars.peek("[eE]")) {
            if (chars.peek("[eE]", "[+-]", "[0-9]") || chars.peek("[eE]", "[0-9]")) {
                chars.match("[eE]");
                chars.match("[+-]");
                while (chars.match("[0-9]")) {
                    // exponent digits
                }
            }
        }

        return chars.emit(decimal ? Token.Type.DECIMAL : Token.Type.INTEGER);
    }

    private Token lexCharacter() throws LexException {
        chars.match("'");

        if (chars.match("\\\\")) {
            lexEscape();
        } else if (chars.match("[^'\\n\\r\\\\]")) {
            // normal char
        } else {
            throw new LexException("Invalid character literal", chars.index);
        }

        if (!chars.match("'")) {
            throw new LexException("Unterminated character literal", chars.index);
        }

        return chars.emit(Token.Type.CHARACTER);
    }

    private Token lexString() throws LexException {
        chars.match("\"");

        while (chars.has(0) && !chars.peek("\"")) {
            if (chars.match("\\\\")) {
                lexEscape();
            } else if (chars.match("[^\"\\n\\r\\\\]")) {
                // consume
            } else {
                throw new LexException("Invalid string literal", chars.index);
            }
        }

        if (!chars.match("\"")) {
            throw new LexException("Unterminated string literal", chars.index);
        }

        return chars.emit(Token.Type.STRING);
    }

    private void lexEscape() throws LexException {
        if (!chars.match("[bnrt'\"\\\\]")) {
            throw new LexException("Invalid escape sequence", chars.index);
        }
    }

    public Token lexOperator() {
        if (chars.match("[<>!=]")) {
            chars.match("=");
        } else {
            chars.advance();
        }
        return chars.emit(Token.Type.OPERATOR);
    }

    /**
     * A helper class for maintaining the state of the character stream (input)
     * and methods for building up token literals.
     */
    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        void skip() {
            length = 0;
        }

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        /**
         * Returns true if the next character(s) match their corresponding
         * pattern(s). Each pattern is a regex matching ONE character, e.g.:
         *  - peek("/") is valid and will match the next character
         *  - peek("/", "/") is valid and will match the next two characters
         *  - peek("/+") is conceptually invalid, but will match one character
         *  - peek("//") is strictly invalid as it can never match one character
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
        void advance() {
            index++;
            length++;
        }
        Token emit(Token.Type type) {
            int start = index - length;
            String literal = input.substring(start, index);
            length = 0;
            return new Token(type, literal);
        }
    }
}
