package org.arend.repl;

import org.arend.core.expr.Expression;
import org.arend.module.FullModulePath;
import org.arend.repl.action.ReplAction;
import org.arend.repl.action.ReplCommand;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.result.TypecheckingResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public interface ReplApi {
  @NotNull FullModulePath replModulePath = new FullModulePath(null, FullModulePath.LocationKind.TEST, Collections.singletonList("Repl"));

  void checkStatements(@NotNull String line);

  void registerAction(@NotNull ReplCommand action);

  boolean unregisterAction(@NotNull ReplAction action);

  void clearActions();

  /**
   * A replacement of {@link System#out#println(Object)} where it uses the
   * output stream of the REPL.
   *
   * @param anything whose {@link Object#toString()} is invoked.
   */
  void println(Object anything);

  /**
   * A replacement of {@link System#err#println(Object)} where it uses the
   * error output stream of the REPL.
   *
   * @param anything whose {@link Object#toString()} is invoked.
   */
  void eprintln(Object anything);

  @NotNull StringBuilder prettyExpr(@NotNull StringBuilder builder, @NotNull Expression expression);

  /**
   * @param expr input concrete expression.
   * @see ReplApi#preprocessExpr(String)
   */
  @Nullable TypecheckingResult checkExpr(@NotNull Concrete.Expression expr, @Nullable Expression expectedType);

  /**
   * @see ReplApi#checkExpr(Concrete.Expression, Expression)
   */
  @Nullable Concrete.Expression preprocessExpr(@NotNull String text);

  /**
   * Check and print errors.
   *
   * @return true if there is error(s).
   */
  boolean checkErrors();
}