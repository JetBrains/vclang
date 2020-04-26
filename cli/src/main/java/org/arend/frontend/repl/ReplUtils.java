package org.arend.frontend.repl;

import org.antlr.v4.runtime.*;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.ArendLexer;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.ParserError;
import org.arend.frontend.parser.Position;
import org.jetbrains.annotations.NotNull;

public interface ReplUtils {
  static @NotNull ArendParser createParser(@NotNull String text, @NotNull ModulePath modulePath, @NotNull ErrorReporter reporter) {
    CharStream input = CharStreams.fromString(text);
    ArendLexer lexer = new ArendLexer(input);
    lexer.removeErrorListeners();
    BaseErrorListener errorListener = new BaseErrorListener() {
      @Override
      public void syntaxError(Recognizer<?, ?> recognizer, Object o, int line, int pos, String msg, RecognitionException e) {
        reporter.report(new ParserError(new Position(modulePath, line, pos), msg));
      }
    };
    lexer.addErrorListener(errorListener);

    CommonTokenStream tokens = new CommonTokenStream(lexer);
    ArendParser parser = new ArendParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(errorListener);
    // parser.addErrorListener(new DiagnosticErrorListener());
    // parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
    return parser;
  }
}