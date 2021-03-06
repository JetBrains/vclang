package org.arend.typechecking.error.local;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.Constructor;
import org.arend.core.expr.DataCallExpression;
import org.arend.core.expr.Expression;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.naming.reference.GlobalReferable;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class ExpectedConstructorError extends TypecheckingError {
  public final GlobalReferable referable;
  public final DataCallExpression dataCall;
  public final DependentLink parameter;
  public final List<Expression> caseExpressions;
  public final DependentLink patternParameters;
  public final DependentLink clauseParameters;
  private final boolean myConstructorOfData;

  public ExpectedConstructorError(GlobalReferable referable,
                                  @Nullable DataCallExpression dataCall,
                                  @Nullable DependentLink parameter,
                                  Concrete.SourceNode cause,
                                  @Nullable List<Expression> caseExpressions,
                                  DependentLink patternParameters,
                                  @Nullable DependentLink clauseParameters) {
    super("", cause);
    this.referable = referable;
    this.dataCall = dataCall;
    this.parameter = parameter;
    this.caseExpressions = caseExpressions;
    this.patternParameters = patternParameters;
    this.clauseParameters = clauseParameters;

    boolean constructorOfData = false;
    if (dataCall != null) {
      for (Constructor constructor : dataCall.getDefinition().getConstructors()) {
        if (constructor.getReferable() == referable) {
          constructorOfData = true;
          break;
        }
      }
    }

    myConstructorOfData = constructorOfData;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig ppConfig) {
    return hList(text("'"), refDoc(referable), text("' is not a constructor"), dataCall == null ? empty() : hList(text(" of data type "), myConstructorOfData ? termLine(dataCall, ppConfig) : refDoc(dataCall.getDefinition().getReferable())));
  }

  @NotNull
  @Override
  public Stage getStage() {
    return dataCall == null ? Stage.RESOLVER : Stage.TYPECHECKER;
  }

  @Override
  public boolean hasExpressions() {
    return myConstructorOfData;
  }
}
