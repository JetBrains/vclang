package org.arend.typechecking.error.local;

import org.arend.core.context.param.DependentLink;
import org.arend.core.pattern.Pattern;
import org.arend.error.doc.LineDoc;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrinterConfig;

import javax.annotation.Nonnull;

import static org.arend.error.doc.DocFactory.*;

public class PatternUnificationError extends TypecheckingError {
  public final DependentLink parameter;
  public final Pattern pattern;

  public PatternUnificationError(DependentLink parameter, Pattern pattern, @Nonnull Concrete.SourceNode cause) {
    super("", cause);
    this.parameter = parameter;
    this.pattern = pattern;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig ppConfig) {
    return hList(text("Cannot unify parameter '" + parameter.getName() + "' with pattern "), termLine(pattern.toPatternExpression(), ppConfig), text(" since the parameter is matched by an idp constructor"));
  }
}