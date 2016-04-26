package com.jetbrains.jetpad.vclang.typechecking.exprorder;

import com.jetbrains.jetpad.vclang.term.Preprelude;
import com.jetbrains.jetpad.vclang.term.definition.TypeUniverseNew;
import com.jetbrains.jetpad.vclang.term.expr.ClassCallExpression;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory;
import com.jetbrains.jetpad.vclang.term.expr.NewExpression;
import com.jetbrains.jetpad.vclang.term.expr.visitor.CompareVisitor;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.typechecking.implicitargs.equations.Equations;

public class LevelOrder implements ExpressionOrder {
  public static Boolean compareLevel(Expression expr1, Expression expr2, CompareVisitor visitor, Equations.CMP expectedCMP) {
    return new LevelOrder().compare(expr1, expr2, visitor, expectedCMP);
  }

  public static Expression maxLevel(Expression expr1, Expression expr2) {
    return new LevelOrder().max(expr1, expr2);
  }

  @Override
  public boolean isComparable(Expression expr) {
    Expression type = expr.getType().normalize(NormalizeVisitor.Mode.NF);
    ClassCallExpression classCall = type.toClassCall();

    return classCall != null && classCall.getDefinition() == Preprelude.LEVEL;
  }

  @Override
  public Boolean compare(Expression expr1, Expression expr2, CompareVisitor visitor, Equations.CMP expectedCMP) {
    NewExpression new1 = expr1.toNew();
    NewExpression new2 = expr2.toNew();

    if (new1 == null || new2 == null) {
      return null;
    }

    ClassCallExpression classCall1 = new1.getExpression().toClassCall();
    ClassCallExpression classCall2 = new2.getExpression().toClassCall();

    if (classCall1 == null || classCall2 == null) {
      return null;
    }

    if (!classCall1.getImplementStatements().containsKey(Preprelude.HLEVEL) || !classCall1.getImplementStatements().containsKey(Preprelude.PLEVEL) ||
            !classCall2.getImplementStatements().containsKey(Preprelude.HLEVEL) || !classCall2.getImplementStatements().containsKey(Preprelude.PLEVEL)) {
      return null;
    }

    Expression hlevel1 = TypeUniverseNew.exprToHLevel(classCall1.getImplementStatements().get(Preprelude.HLEVEL).term);
    Expression hlevel2 = TypeUniverseNew.exprToHLevel(classCall2.getImplementStatements().get(Preprelude.HLEVEL).term);
    Expression plevel1 = TypeUniverseNew.exprToPLevel(classCall1.getImplementStatements().get(Preprelude.PLEVEL).term);
    Expression plevel2 = TypeUniverseNew.exprToPLevel(classCall2.getImplementStatements().get(Preprelude.PLEVEL).term);

    boolean cmp1 = LevelExprOrder.compareLevel(hlevel1, hlevel2, visitor, expectedCMP); // CNatOrder.compareCNat(hlevel1, hlevel2, visitor, expectedCMP);
    boolean cmp2 = LevelExprOrder.compareLevel(plevel1, plevel2, visitor, expectedCMP); // CNatOrder.compareCNat(hlevel1, hlevel2, visitor, expectedCMP);

    if (LevelExprOrder.isZero(hlevel1) || LevelExprOrder.isZero(hlevel2)) {
      return cmp1;
    }

    return cmp1 && cmp2;
  }

  @Override
  public Expression max(Expression expr1, Expression expr2) {
    if (Expression.compare(expr1, expr2, Equations.CMP.GE)) {
      return expr1;
    }

    if (Expression.compare(expr1, expr2, Equations.CMP.LE)) {
      return expr2;
    }/**/

    NewExpression new1 = expr1.toNew();
    NewExpression new2 = expr2.toNew();
    Expression plevel1, plevel2, hlevel1, hlevel2;

    if (new1 != null) {
      ClassCallExpression classCall = new1.getExpression().toClassCall();
      plevel1 = classCall.getImplementStatements().get(Preprelude.PLEVEL).term;
      hlevel1 = classCall.getImplementStatements().get(Preprelude.HLEVEL).term;
    } else {
      plevel1 = ExpressionFactory.PLevel().applyThis(expr1);
      hlevel1 = ExpressionFactory.HLevel().applyThis(expr1);
    }

    if (new2 != null) {
      ClassCallExpression classCall = new2.getExpression().toClassCall();
      plevel2 = classCall.getImplementStatements().get(Preprelude.PLEVEL).term;
      hlevel2 = classCall.getImplementStatements().get(Preprelude.HLEVEL).term;
    } else {
      plevel2 = ExpressionFactory.PLevel().applyThis(expr2);
      hlevel2 = ExpressionFactory.HLevel().applyThis(expr2);
    }

    return ExpressionFactory.Level(LvlOrder.maxLvl(plevel1, plevel2), CNatOrder.maxCNat(hlevel1, hlevel2));
  }
}