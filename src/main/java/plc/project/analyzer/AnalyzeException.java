package plc.project.analyzer;

import plc.project.parser.Ast;

import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class AnalyzeException extends Exception {

    private final Optional<Ast> ast;

    public AnalyzeException(String message) {
        super(message);
        this.ast = Optional.empty();
    }

    public AnalyzeException(String message, Ast ast) {
        super(message + "\n - @ ast " + ast);
        this.ast = Optional.of(ast);
    }

    public Optional<Ast> getAst() {
        return ast;
    }

}
