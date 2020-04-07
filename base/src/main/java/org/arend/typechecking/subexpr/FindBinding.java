package org.arend.typechecking.subexpr;

import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.EmptyDependentLink;
import org.arend.core.expr.*;
import org.arend.core.expr.let.LetClause;
import org.arend.naming.reference.Referable;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class FindBinding {
  public static @Nullable DependentLink visitLam(
      Referable referable,
      Concrete.LamExpression expr,
      LamExpression lam
  ) {
    return parameters(lam.getParameters(), referable, new LambdaParam(lam), expr.getParameters());
  }

  private static class LambdaParam implements Function<DependentLink, DependentLink> {
    public LambdaParam(@NotNull LamExpression lambda) {
      this.lambda = lambda;
    }

    private LamExpression lambda;

    @Override
    public DependentLink apply(DependentLink link) {
      DependentLink next = link.getNext();
      if (next instanceof EmptyDependentLink) {
        lambda = lambda.getBody().cast(LamExpression.class);
        if (lambda == null) return null;
        return lambda.getParameters();
      } else return next;
    }
  }

  public static @Nullable DependentLink visitPi(
      Referable referable,
      Concrete.PiExpression expr,
      PiExpression pi
  ) {
    return parameters(pi.getBinding(), referable, new Function<DependentLink, DependentLink>() {
      private PiExpression piExpr = pi;

      @Override
      public DependentLink apply(DependentLink link) {
        DependentLink next = link.getNext();
        if (next instanceof EmptyDependentLink) {
          piExpr = piExpr.getCodomain().cast(PiExpression.class);
          if (piExpr == null) return null;
          return piExpr.getBinding();
        } else return next;
      }
    }, expr.getParameters());
  }

  public static @Nullable DependentLink visitSigma(
      Referable referable,
      Concrete.SigmaExpression expr,
      SigmaExpression sigma
  ) {
    return parameters(sigma.getParameters(), referable, DependentLink::getNext, expr.getParameters());
  }

  public static @Nullable Expression visitLet(
      Object patternData,
      Concrete.LetExpression expr,
      LetExpression let
  ) {
    List<Concrete.LetClause> exprClauses = expr.getClauses();
    List<LetClause> coreClauses = let.getClauses();
    for (int i = 0; i < exprClauses.size(); i++) {
      LetClause coreLetClause = coreClauses.get(i);
      Concrete.LetClause exprLetClause = exprClauses.get(i);

      if (Objects.equals(exprLetClause.getPattern().getData(), patternData))
        return coreLetClause.getTypeExpr();
    }
    return null;
  }

  public static @Nullable DependentLink visitLetParam(
      Referable patternParam,
      Concrete.LetExpression expr,
      LetExpression let
  ) {
    List<Concrete.LetClause> exprClauses = expr.getClauses();
    List<LetClause> coreClauses = let.getClauses();
    for (int i = 0; i < exprClauses.size(); i++) {
      LetClause coreLetClause = coreClauses.get(i);
      Concrete.LetClause exprLetClause = exprClauses.get(i);

      LamExpression coreLamExpr = coreLetClause.getExpression().cast(LamExpression.class);
      List<Concrete.Parameter> parameters = exprLetClause.getParameters();
      if (coreLamExpr != null && !parameters.isEmpty()) {
        DependentLink link = parameters(coreLamExpr.getParameters(), patternParam, new LambdaParam(coreLamExpr), parameters);
        if (link != null) return link;
      }
    }
    return null;
  }

  private static @Nullable DependentLink parameters(
      DependentLink core,
      Referable referable,
      Function<DependentLink, DependentLink> next,
      List<? extends Concrete.Parameter> parameters
  ) {
    for (Concrete.Parameter concrete : parameters)
      for (Referable ref : concrete.getReferableList()) {
        if (ref == referable) return core;
        if (concrete.isExplicit() != core.isExplicit()) continue;
        core = next.apply(core);
        if (core == null) return null;
      }
    return null;
  }
}
