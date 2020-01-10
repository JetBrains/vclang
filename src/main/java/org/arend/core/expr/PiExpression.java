package org.arend.core.expr;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.binding.inference.InferenceLevelVariable;
import org.arend.core.context.param.SingleDependentLink;
import org.arend.core.expr.type.Type;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.expr.visitor.NormalizeVisitor;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.SubstVisitor;
import org.arend.error.ErrorReporter;
import org.arend.ext.core.expr.CoreAbsExpression;
import org.arend.ext.core.expr.CorePiExpression;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.implicitargs.equations.Equations;
import org.arend.util.Decision;

import javax.annotation.Nonnull;

public class PiExpression extends Expression implements Type, CorePiExpression, CoreAbsExpression {
  private final Sort myResultSort;
  private final SingleDependentLink myLink;
  private final Expression myCodomain;

  public PiExpression(Sort resultSort, SingleDependentLink link, Expression codomain) {
    assert link.hasNext();
    myResultSort = resultSort;
    myLink = link;
    myCodomain = codomain;
  }

  public static Sort generateUpperBound(Sort domSort, Sort codSort, Equations equations, Concrete.SourceNode sourceNode) {
    if (domSort.getPLevel().getVar() == null || codSort.getPLevel().getVar() == null || domSort.getPLevel().getVar() == codSort.getPLevel().getVar()) {
      return new Sort(domSort.getPLevel().max(codSort.getPLevel()), codSort.getHLevel());
    }

    InferenceLevelVariable pl = new InferenceLevelVariable(LevelVariable.LvlType.PLVL, false, sourceNode);
    equations.addVariable(pl);
    Level pLevel = new Level(pl);
    equations.addEquation(domSort.getPLevel(), pLevel, Equations.CMP.LE, sourceNode);
    equations.addEquation(codSort.getPLevel(), pLevel, Equations.CMP.LE, sourceNode);
    return new Sort(pLevel, codSort.getHLevel());
  }

  public Sort getResultSort() {
    return myResultSort;
  }

  @Nonnull
  @Override
  public SingleDependentLink getParameters() {
    return myLink;
  }

  @Nonnull
  @Override
  public Expression getCodomain() {
    return myCodomain;
  }

  @Override
  public Expression applyExpression(Expression expression) {
    SingleDependentLink link = myLink;
    ExprSubstitution subst = new ExprSubstitution(link, expression);
    link = link.getNext();
    Expression result = myCodomain;
    if (link.hasNext()) {
      result = new PiExpression(myResultSort, link, result);
    }
    return result.subst(subst);
  }

  @Override
  public Expression applyExpression(Expression expression, boolean normalizing) {
    return applyExpression(expression);
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitPi(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitPi(this, param1, param2);
  }

  @Override
  public PiExpression getExpr() {
    return this;
  }

  @Override
  public Sort getSortOfType() {
    return myResultSort;
  }

  @Override
  public PiExpression subst(SubstVisitor substVisitor) {
    return substVisitor.isEmpty() ? this : substVisitor.visitPi(this, null);
  }

  @Override
  public PiExpression strip(ErrorReporter errorReporter) {
    return new StripVisitor(errorReporter).visitPi(this, null);
  }

  @Override
  public PiExpression normalize(NormalizeVisitor.Mode mode) {
    return NormalizeVisitor.INSTANCE.visitPi(this, mode);
  }

  @Override
  public Decision isWHNF() {
    return Decision.YES;
  }

  @Override
  public Expression getStuckExpression() {
    return null;
  }

  @Nonnull
  @Override
  public SingleDependentLink getBinding() {
    return myLink;
  }

  @Nonnull
  @Override
  public Expression getExpression() {
    return myCodomain;
  }
}
