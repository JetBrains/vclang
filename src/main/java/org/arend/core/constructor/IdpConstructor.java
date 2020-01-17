package org.arend.core.constructor;

import org.arend.core.expr.ConCallExpression;
import org.arend.core.expr.Expression;
import org.arend.core.expr.FunCallExpression;
import org.arend.core.expr.LamExpression;
import org.arend.core.expr.visitor.NormalizingFindBindingVisitor;
import org.arend.ext.core.elimtree.CoreIdpBranchKey;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.prelude.Prelude;

import java.util.Collections;
import java.util.List;

public class IdpConstructor extends SingleConstructor implements CoreIdpBranchKey {
  @Override
  public int getNumberOfParameters() {
    return 0;
  }

  @Override
  public List<Expression> getMatchedArguments(Expression argument, boolean normalizing) {
    argument = argument.getUnderlyingExpression();
    if (argument instanceof FunCallExpression) {
      return ((FunCallExpression) argument).getDefinition() == Prelude.IDP ? Collections.emptyList() : null;
    }

    if (!normalizing || !(argument instanceof ConCallExpression && ((ConCallExpression) argument).getDefinition() == Prelude.PATH_CON)) {
      return null;
    }

    LamExpression lamExpr = ((ConCallExpression) argument).getDefCallArguments().get(0).normalize(NormalizationMode.WHNF).cast(LamExpression.class);
    if (lamExpr == null) {
      return null;
    }
    Expression body = lamExpr.getParameters().getNext().hasNext() ? new LamExpression(lamExpr.getResultSort(), lamExpr.getParameters().getNext(), lamExpr.getBody()) : lamExpr.getBody();
    return NormalizingFindBindingVisitor.findBinding(body, lamExpr.getParameters()) ? null : Collections.emptyList();
  }
}
