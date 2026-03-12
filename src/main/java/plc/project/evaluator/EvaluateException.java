package plc.project.evaluator;

import plc.project.parser.Ast;

import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class EvaluateException extends Exception {

    private final Optional<Ast> ast;

    public EvaluateException(String message) {
        super(message);
        this.ast = Optional.empty();
    }

    public EvaluateException(String message, Ast ast) {
        super(message + "\n - @ ast " + ast);
        this.ast = Optional.of(ast);
    }

    public Optional<Ast> getAst() {
        return ast;
    }

}
