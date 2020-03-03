package org.arend.core.expr;

import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.EmptyDependentLink;
import org.arend.core.elimtree.Body;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.expr.visitor.NormalizeVisitor;
import org.arend.core.subst.LevelSubstitution;
import org.arend.ext.core.expr.CoreExpressionVisitor;
import org.arend.ext.core.expr.CorePEvalExpression;
import org.arend.util.Decision;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class PEvalExpression extends Expression implements CorePEvalExpression {
  private final Expression myExpression;

  public PEvalExpression(Expression expression) {
    myExpression = expression;
  }

  @Nonnull
  @Override
  public Expression getExpression() {
    return myExpression;
  }

  public Body getBody() {
    if (myExpression instanceof FunCallExpression) {
      return ((FunCallExpression) myExpression).getDefinition().getActualBody();
    } else {
      return myExpression instanceof CaseExpression ? ((CaseExpression) myExpression).getElimBody() : null;
    }
  }

  public DependentLink getParameters() {
    if (myExpression instanceof FunCallExpression) {
      return ((FunCallExpression) myExpression).getDefinition().getParameters();
    } else {
      return myExpression instanceof CaseExpression ? ((CaseExpression) myExpression).getParameters() : EmptyDependentLink.getInstance();
    }
  }

  public LevelSubstitution getLevelSubstitution() {
    return myExpression instanceof FunCallExpression ? ((FunCallExpression) myExpression).getSortArgument().toLevelSubstitution() : LevelSubstitution.EMPTY;
  }

  public List<? extends Expression> getArguments() {
    return myExpression instanceof FunCallExpression ? ((FunCallExpression) myExpression).getDefCallArguments() : myExpression instanceof CaseExpression ? ((CaseExpression) myExpression).getArguments() : Collections.emptyList();
  }

  public Expression eval() {
    return NormalizeVisitor.INSTANCE.eval(myExpression);
  }

  @Override
  public boolean canBeConstructor() {
    return false;
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitPEval(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitPEval(this, param1, param2);
  }

  @Override
  public <P, R> R accept(@Nonnull CoreExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitPEval(this, params);
  }

  @Override
  public Decision isWHNF() {
    return Decision.YES;
  }

  @Override
  public Expression getStuckExpression() {
    return null;
  }
}
