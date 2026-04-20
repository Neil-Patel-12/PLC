package plc.project.parser;

import com.google.common.base.Preconditions;
import plc.project.lexer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.math.BigDecimal;
import java.math.BigInteger;

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

    public Ast parse(String rule) throws ParseException {
        var ast = switch (rule) {
            case "source" -> parseSource();
            case "stmt" -> parseStmt();
            case "expr" -> parseExpr();
            default -> throw new AssertionError(rule);
        };
        if (tokens.has(0)) {
            throw new ParseException("Expected end of input.", tokens.getNext());
        }
        return ast;
    }

    private Ast.Source parseSource() throws ParseException {
        var statements = new ArrayList<Ast.Stmt>();
        while (tokens.has(0)) {
            statements.add(parseStmt());
        }
        return new Ast.Source(statements);
    }

    private Ast.Stmt parseStmt() throws ParseException {
        if (tokens.peek("LET")) {
            return parseLetStmt();
        } else if (tokens.peek("DEF")) {
            return parseDefStmt();
        } else if (tokens.peek("IF")) {
            return parseIfStmt();
        } else if (tokens.peek("FOR")) {
            return parseForStmt();
        } else if (tokens.peek("RETURN")) {
            return parseReturnStmt();
        } else {
            return parseExpressionOrAssignmentStmt();
        }
    }

    private Ast.Stmt parseLetStmt() throws ParseException {
        require("LET");

        Token nameTok = require(Token.Type.IDENTIFIER);
        String name = nameTok.literal();

        // Optional type annotation: ':' identifier
        Optional<String> type = Optional.empty();
        if (tokens.match(":")) {
            type = Optional.of(require(Token.Type.IDENTIFIER).literal());
        }

        Optional<Ast.Expr> init = Optional.empty();
        if (tokens.match("=")) {
            init = Optional.of(parseExpr());
        }

        require(";");
        return new Ast.Stmt.Let(name, type, init);
    }

    private Ast.Stmt parseDefStmt() throws ParseException {
        require("DEF");
        String name = require(Token.Type.IDENTIFIER).literal();

        require("(");
        var params = new ArrayList<String>();
        var paramTypes = new ArrayList<Optional<String>>();
        if (!tokens.peek(")")) {
            params.add(require(Token.Type.IDENTIFIER).literal());
            if (tokens.match(":")) {
                paramTypes.add(Optional.of(require(Token.Type.IDENTIFIER).literal()));
            } else {
                paramTypes.add(Optional.empty());
            }
            while (tokens.match(",")) {
                params.add(require(Token.Type.IDENTIFIER).literal());
                if (tokens.match(":")) {
                    paramTypes.add(Optional.of(require(Token.Type.IDENTIFIER).literal()));
                } else {
                    paramTypes.add(Optional.empty());
                }
            }
        }
        require(")");

        // Optional return type annotation: ':' identifier
        Optional<String> returnType = Optional.empty();
        if (tokens.match(":")) {
            returnType = Optional.of(require(Token.Type.IDENTIFIER).literal());
        }

        require("DO");
        var body = new ArrayList<Ast.Stmt>();
        while (!tokens.peek("END")) {
            if (!tokens.has(0)) throw new ParseException("Expected END.", tokens.getNext());
            body.add(parseStmt());
        }
        require("END");

        return new Ast.Stmt.Def(name, params, paramTypes, returnType, body);
    }

    private Ast.Stmt parseIfStmt() throws ParseException {
        require("IF");
        Ast.Expr cond = parseExpr();

        require("DO");
        var thenStmts = new ArrayList<Ast.Stmt>();
        while (!tokens.peek("ELSE") && !tokens.peek("END")) {
            if (!tokens.has(0)) throw new ParseException("Expected END.", tokens.getNext());
            thenStmts.add(parseStmt());
        }

        var elseStmts = new ArrayList<Ast.Stmt>();
        if (tokens.match("ELSE")) {
            while (!tokens.peek("END")) {
                if (!tokens.has(0)) throw new ParseException("Expected END.", tokens.getNext());
                elseStmts.add(parseStmt());
            }
        }

        require("END");
        return new Ast.Stmt.If(cond, thenStmts, elseStmts);
    }

    private Ast.Stmt parseForStmt() throws ParseException {
        require("FOR");
        String name = require(Token.Type.IDENTIFIER).literal();

        // if IN is missing
        require("IN");

        Ast.Expr iterable = parseExpr();
        require("DO");

        var body = new ArrayList<Ast.Stmt>();
        while (!tokens.peek("END")) {
            if (!tokens.has(0)) throw new ParseException("Expected END.", tokens.getNext());
            body.add(parseStmt());
        }
        require("END");

        return new Ast.Stmt.For(name, iterable, body);
    }

    private Ast.Stmt parseReturnStmt() throws ParseException {
        require("RETURN");

        if (tokens.match("IF")) {
            Ast.Expr cond = parseExpr();
            require(";");
            return new Ast.Stmt.If(
                    cond,
                    List.of(new Ast.Stmt.Return(Optional.empty())),
                    List.of()
            );
        }

        Optional<Ast.Expr> value = Optional.empty();
        if (!tokens.peek(";")) {
            value = Optional.of(parseExpr());
        }

        if (tokens.match("IF")) {
            Ast.Expr cond = parseExpr();
            require(";");
            return new Ast.Stmt.If(
                    cond,
                    List.of(new Ast.Stmt.Return(value)),
                    List.of()
            );
        }

        require(";");
        return new Ast.Stmt.Return(value);
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        Ast.Expr left = parseExpr();
        if (tokens.match("=")) {
            Ast.Expr right = parseExpr();
            require(";");
            return new Ast.Stmt.Assignment(left, right);
        }
        require(";");
        return new Ast.Stmt.Expression(left);
    }

    private Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr();
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        Ast.Expr expr = parseComparisonExpr();
        while (tokens.peek("AND") || tokens.peek("OR")) {
            String op = require(Token.Type.IDENTIFIER).literal();
            // AND/OR are IDENTIFIER tokens in tests
            Ast.Expr right = parseComparisonExpr();
            expr = new Ast.Expr.Binary(op, expr, right);
        }
        return expr;
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        Ast.Expr expr = parseAdditiveExpr();
        while (tokens.peek("<") || tokens.peek("<=") || tokens.peek(">") || tokens.peek(">=") ||
                tokens.peek("==") || tokens.peek("!=")) {
            String op = require(Token.Type.OPERATOR).literal();
            Ast.Expr right = parseAdditiveExpr();
            expr = new Ast.Expr.Binary(op, expr, right);
        }
        return expr;
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        Ast.Expr expr = parseMultiplicativeExpr();
        while (tokens.peek("+") || tokens.peek("-")) {
            String op = require(Token.Type.OPERATOR).literal();
            Ast.Expr right = parseMultiplicativeExpr();
            expr = new Ast.Expr.Binary(op, expr, right);
        }
        return expr;
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        Ast.Expr expr = parseSecondaryExpr();
        while (tokens.peek("*") || tokens.peek("/")) {
            String op = require(Token.Type.OPERATOR).literal();
            Ast.Expr right = parseSecondaryExpr();
            expr = new Ast.Expr.Binary(op, expr, right);
        }
        return expr;
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        Ast.Expr expr = parsePrimaryExpr();
        while (tokens.peek(".")) {
            expr = parsePropertyOrMethod(expr);
        }
        return expr;
    }

    private Ast.Expr parsePropertyOrMethod(Ast.Expr receiver) throws ParseException {
        require(".");

        // Missing name should throw with Optional.empty() in the tests.
        if (!tokens.has(0)) throw new ParseException("Expected identifier after '.'.", Optional.empty());
        if (!tokens.peek(Token.Type.IDENTIFIER)) throw new ParseException("Expected identifier after '.'.", tokens.getNext());

        String name = require(Token.Type.IDENTIFIER).literal();

        if (tokens.match("(")) {
            var args = new ArrayList<Ast.Expr>();
            if (!tokens.peek(")")) {
                args.add(parseExpr());
                while (tokens.match(",")) {
                    args.add(parseExpr());
                }
            }
            require(")");
            return new Ast.Expr.Method(receiver, name, args);
        }

        return new Ast.Expr.Property(receiver, name);
    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        if (tokens.peek("NIL") || tokens.peek("TRUE") || tokens.peek("FALSE") ||
                tokens.peek(Token.Type.INTEGER) || tokens.peek(Token.Type.DECIMAL) ||
                tokens.peek(Token.Type.CHARACTER) || tokens.peek(Token.Type.STRING)) {
            return parseLiteralExpr();
        } else if (tokens.peek("(")) {
            return parseGroupExpr();
        } else if (tokens.peek("OBJECT")) {
            return parseObjectExpr();
        } else if (tokens.peek(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        } else {
            throw new ParseException("Expected primary expression.", tokens.getNext());
        }
    }

    private Ast.Expr parseLiteralExpr() throws ParseException {
        if (tokens.match("NIL")) {
            return new Ast.Expr.Literal(null);
        }
        if (tokens.match("TRUE")) {
            return new Ast.Expr.Literal(true);
        }
        if (tokens.match("FALSE")) {
            return new Ast.Expr.Literal(false);
        }

        if (tokens.peek(Token.Type.INTEGER)) {
            Token t = tokens.get(0);
            tokens.match(Token.Type.INTEGER);
            try {
                return new Ast.Expr.Literal(new BigInteger(t.literal()));
            } catch (NumberFormatException ex) {
                // If your lexer ever allows exponent-style integer literals, accept only exact integers.
                try {
                    BigInteger bi = new BigDecimal(t.literal()).toBigIntegerExact();
                    return new Ast.Expr.Literal(bi);
                } catch (Exception e) {
                    throw new ParseException("Invalid integer literal.", Optional.of(t));
                }
            }
        }

        if (tokens.peek(Token.Type.DECIMAL)) {
            Token t = tokens.get(0);
            tokens.match(Token.Type.DECIMAL);
            try {
                return new Ast.Expr.Literal(new BigDecimal(t.literal()));
            } catch (NumberFormatException ex) {
                throw new ParseException("Invalid decimal literal.", Optional.of(t));
            }
        }

        if (tokens.peek(Token.Type.CHARACTER)) {
            Token t = tokens.get(0);
            tokens.match(Token.Type.CHARACTER);
            String lit = t.literal(); // includes quotes
            if (lit.length() < 2 || lit.charAt(0) != '\'' || lit.charAt(lit.length() - 1) != '\'') {
                throw new ParseException("Invalid character literal.", Optional.of(t));
            }
            String inner = lit.substring(1, lit.length() - 1);
            char c;
            if (inner.startsWith("\\")) {
                c = unescapeChar(inner, t);
            } else if (inner.length() == 1) {
                c = inner.charAt(0);
            } else {
                throw new ParseException("Invalid character literal.", Optional.of(t));
            }
            return new Ast.Expr.Literal(c);
        }

        if (tokens.peek(Token.Type.STRING)) {
            Token t = tokens.get(0);
            tokens.match(Token.Type.STRING);
            String lit = t.literal(); // includes quotes
            if (lit.length() < 2 || lit.charAt(0) != '"' || lit.charAt(lit.length() - 1) != '"') {
                throw new ParseException("Invalid string literal.", Optional.of(t));
            }
            String inner = lit.substring(1, lit.length() - 1);
            return new Ast.Expr.Literal(unescapeString(inner, t));
        }

        throw new ParseException("Expected literal.", tokens.getNext());
    }

    private Ast.Expr parseGroupExpr() throws ParseException {
        require("(");
        if (tokens.peek(")")) {
            throw new ParseException("Expected expression.", tokens.getNext());
        }

        Ast.Expr expr = parseExpr();
        require(")");
        return new Ast.Expr.Group(expr);
    }

    private Ast.Expr parseObjectExpr() throws ParseException {
        require("OBJECT");

        Optional<String> name = Optional.empty();

        if (tokens.peek(Token.Type.IDENTIFIER) && !tokens.peek("DO")) {
            name = Optional.of(require(Token.Type.IDENTIFIER).literal());
        }

        require("DO");

        var fields = new ArrayList<Ast.Stmt.Let>();
        var methods = new ArrayList<Ast.Stmt.Def>();

        while (tokens.peek("LET")) {
            fields.add((Ast.Stmt.Let) parseLetStmt());
        }
        while (tokens.peek("DEF")) {
            methods.add((Ast.Stmt.Def) parseDefStmt());
        }

        require("END");
        return new Ast.Expr.ObjectExpr(name, fields, methods);
    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        String name = require(Token.Type.IDENTIFIER).literal();

        if (tokens.match("(")) {
            var args = new ArrayList<Ast.Expr>();
            if (!tokens.peek(")")) {
                args.add(parseExpr());
                while (tokens.match(",")) {
                    args.add(parseExpr());
                }
            }
            require(")");
            return new Ast.Expr.Function(name, args);
        }

        return new Ast.Expr.Variable(name);
    }

//    private Token require(Object... pattern) throws ParseException {
//        if (!tokens.match(pattern)) {
//            throw new ParseException("Unexpected token.", tokens.getNext());
//        }
//        // tokens.match advanced index; token matched is at -1
//        return tokens.get(-1); // not supported by TokenStream; so we must not use this.
//    }

    private Token require(Token.Type type) throws ParseException {
        if (!tokens.has(0)) throw new ParseException("Unexpected end of input.", tokens.getNext());
        Token t = tokens.get(0);
        if (!tokens.match(type)) {
            throw new ParseException("Expected " + type + ".", tokens.getNext());
        }
        return t;
    }

    private void require(String literal) throws ParseException {
        if (!tokens.match(literal)) {
            throw new ParseException("Expected '" + literal + "'.", tokens.getNext());
        }
    }

    private char unescapeChar(String inner, Token t) throws ParseException {
        if (inner.length() != 2 || inner.charAt(0) != '\\') {
            throw new ParseException("Invalid escape.", Optional.of(t));
        }
        return switch (inner.charAt(1)) {
            case 'b' -> '\b';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '\'' -> '\'';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> throw new ParseException("Invalid escape.", Optional.of(t));
        };
    }

    private String unescapeString(String s, Token t) throws ParseException {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= s.length()) {
                throw new ParseException("Invalid escape.", Optional.of(t));
            }
            char n = s.charAt(++i);
            out.append(switch (n) {
                case 'b' -> '\b';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case '\'' -> '\'';
                case '"' -> '"';
                case '\\' -> '\\';
                default -> throw new ParseException("Invalid escape.", Optional.of(t));
            });
        }
        return out.toString();
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
            Preconditions.checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns the next token, if present.
         */
        public Optional<Token> getNext() {
            return index < tokens.size() ? Optional.of(tokens.get(index)) : Optional.empty();
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
                Preconditions.checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
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
