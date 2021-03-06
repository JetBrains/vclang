package org.arend.typechecking;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.param.SingleDependentLink;
import org.arend.core.expr.Expression;
import org.arend.core.sort.Level;
import org.arend.core.subst.LevelPair;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.prelude.Prelude;
import org.arend.typechecking.result.TypecheckingResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.arend.ExpressionFactory.*;
import static org.arend.core.expr.ExpressionFactory.Interval;
import static org.junit.Assert.assertEquals;

public class PathsTest extends TypeCheckingTestCase {
  @Test
  public void idpTest() {
    typeCheckDef("\\func idp {A : \\Type0} (a : A) : a = a => path (\\lam _ => a)");
  }

  @Test
  public void idpUntyped() {
    TypecheckingResult idp = typeCheckExpr("\\lam {A : \\Type0} (a : A) => path (\\lam _ => a)", null);
    SingleDependentLink A = singleParam(false, Collections.singletonList("A"), Universe(new Level(0), new Level(LevelVariable.HVAR)));
    SingleDependentLink a = singleParam("a", Ref(A));
    SingleDependentLink C = singleParam(null, Interval());
    List<Expression> pathArgs = new ArrayList<>();
    pathArgs.add(Lam(C, Ref(A)));
    pathArgs.add(Ref(a));
    pathArgs.add(Ref(a));
    Expression pathCall = ConCall(Prelude.PATH_CON, new LevelPair(new Level(0), Level.INFINITY), pathArgs, Lam(C, Ref(a)));
    assertEquals(Lam(A, Lam(a, pathCall)).normalize(NormalizationMode.NF), idp.expression);
    assertEquals(Pi(A, Pi(a, FunCall(Prelude.PATH_INFIX, new LevelPair(new Level(0), Level.INFINITY), Ref(A), Ref(a), Ref(a)))).normalize(NormalizationMode.NF), idp.type.normalize(NormalizationMode.NF));
  }

  @Test
  public void squeezeTest() {
    typeCheckModule(
        "\\func squeeze1 (i j : I) => coe (\\lam x => left = x) idp j @ i\n" +
        "\\func squeeze (i j : I) => coe (\\lam i => Path (\\lam j => left = squeeze1 i j) idp (path (\\lam j => squeeze1 i j))) idp right @ i @ j"
    );
  }

  @Test
  public void concatTest() {
    typeCheckModule(
        "\\func transport {A : \\Type} (B : A -> \\Type) {a a' : A} (p : a = a') (b : B a) => coe (\\lam i => B (p @ i)) b right\n" +
        "\\func concat {A : I -> \\Type} {a : A left} {a' a'' : A right} (p : Path A a a') (q : a' = a'') => transport (Path A a) q p\n" +
        "\\func *> {A : \\Type} {a a' a'' : A} (p : a = a') (q : a' = a'') => concat p q");
  }

  @Test
  public void inv0Test() {
    typeCheckModule(
        "\\func transport {A : \\Type} (B : A -> \\Type) {a a' : A} (p : a = a') (b : B a) => coe (\\lam i => B (p @ i)) b right\n" +
        "\\func inv {A : \\Type} {a a' : A} (p : a = a') => transport (\\lam a'' => a'' = a) p idp\n" +
        "\\func squeeze1 (i j : I) : I => coe (\\lam x => left = x) idp j @ i\n" +
        "\\func squeeze (i j : I) => coe (\\lam i => Path (\\lam j => left = squeeze1 i j) idp (path (\\lam j => squeeze1 i j))) idp right @ i @ j\n" +
        "\\func psqueeze {A : \\Type} {a a' : A} (p : a = a') (i : I) : a = p @ i => path (\\lam j => p @ squeeze i j)\n" +
        "\\func Jl {A : \\Type} {a : A} (B : \\Pi (a' : A) -> a = a' -> \\Type) (b : B a idp) {a' : A} (p : a = a') : B a' p\n" +
        "  => coe (\\lam i => B (p @ i) (psqueeze p i)) b right\n" +
        "\\func inv-inv {A : \\Type} {a a' : A} (p : a = a') : inv (inv p) = p => Jl (\\lam _ p => inv (inv p) = p) idp p\n" +
        "\\func path-sym {A : \\Type} (a a' : A) : (a = a') = (a' = a) => path (iso inv inv inv-inv inv-inv)");
  }

  @Test
  public void invTest() {
    typeCheckModule(
        "\\func transport {A : \\Type} (B : A -> \\Type) {a a' : A} (p : a = a') (b : B a) => coe (\\lam i => B (p @ i)) b right\n" +
        "\\func inv {A : \\Type} {a a' : A} (p : a = a') => transport (\\lam a'' => a'' = a) p idp\n" +
        "\\func squeeze1 (i j : I) : I => coe (\\lam x => left = x) idp j @ i\n" +
        "\\func squeeze (i j : I) => coe (\\lam i => Path (\\lam j => left = squeeze1 i j) idp (path (\\lam j => squeeze1 i j))) idp right @ i @ j\n" +
        "\\func psqueeze {A : \\Type} {a a' : A} (p : a = a') (i : I) : a = p @ i => path (\\lam j => p @ squeeze i j)\n" +
        "\\func Jl {A : \\Type} {a : A} (B : \\Pi (a' : A) -> a = a' -> \\Type) (b : B a idp) {a' : A} (p : a = a') : B a' p\n" +
        "  => coe (\\lam i => B (p @ i) (psqueeze p i)) b right\n" +
        "\\func inv-inv {A : \\Type} {a a' : A} (p : a = a') : inv (inv p) = p => Jl (\\lam _ p => inv (inv p) = p) idp p\n" +
        "\\func path-sym {A : \\Type} (a a' : A) : (a = a') = (a' = a) => path (iso inv inv inv-inv inv-inv)");
  }

  @Test
  public void idpTypeTest() {
    typeCheckDef("\\func f : 3 = 3 => idp");
  }
}
