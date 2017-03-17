package com.jetbrains.jetpad.vclang.core.expr;

import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.context.param.*;
import com.jetbrains.jetpad.vclang.core.definition.*;
import com.jetbrains.jetpad.vclang.core.internal.FieldSet;
import com.jetbrains.jetpad.vclang.core.pattern.ConstructorPattern;
import com.jetbrains.jetpad.vclang.core.pattern.PatternArgument;
import com.jetbrains.jetpad.vclang.core.pattern.Patterns;
import com.jetbrains.jetpad.vclang.core.pattern.elimtree.*;
import com.jetbrains.jetpad.vclang.core.sort.Level;
import com.jetbrains.jetpad.vclang.core.sort.LevelArguments;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.typechecking.error.local.LocalTypeCheckingError;

import java.util.*;

public class ExpressionFactory {
  public static Expression Apps(Expression function, Expression... arguments) {
    return arguments.length == 0 ? function : new AppExpression(function, new ArrayList<>(Arrays.asList(arguments)));
  }

  public static Expression Apps(Expression fun, Collection<? extends Expression> arguments) {
    return arguments.isEmpty() ? fun : new AppExpression(fun, arguments);
  }

  public static FunCallExpression FunCall(FunctionDefinition definition, LevelArguments polyParams, List<Expression> arguments) {
    return new FunCallExpression(definition, polyParams, arguments);
  }

  public static FunCallExpression FunCall(FunctionDefinition definition, LevelArguments polyParams, Expression... arguments) {
    return FunCall(definition, polyParams, Arrays.asList(arguments));
  }

  public static FunCallExpression FunCall(FunctionDefinition definition, Level lp, Level lh, List<Expression> arguments) {
    return FunCall(definition, new LevelArguments(lp, lh), arguments);
  }

  public static FunCallExpression FunCall(FunctionDefinition definition, Level lp, Level lh, Expression... arguments) {
    return FunCall(definition, lp, lh, Arrays.asList(arguments));
  }

  public static DataCallExpression DataCall(DataDefinition definition, LevelArguments polyParams, List<Expression> arguments) {
    return new DataCallExpression(definition, polyParams, arguments);
  }

  public static DataCallExpression DataCall(DataDefinition definition, LevelArguments polyParams, Expression... arguments) {
    return DataCall(definition, polyParams, Arrays.asList(arguments));
  }

  public static DataCallExpression DataCall(DataDefinition definition, Level lp, Level lh, List<Expression> arguments) {
    return new DataCallExpression(definition, new LevelArguments(lp, lh), arguments);
  }

  public static DataCallExpression DataCall(DataDefinition definition, Level lp, Level lh, Expression... arguments) {
    return DataCall(definition, lp, lh, Arrays.asList(arguments));
  }

  public static Expression FieldCall(ClassField definition, Expression thisExpr) {
    if (thisExpr.toNew() != null) {
      FieldSet.Implementation impl = thisExpr.toNew().getExpression().getFieldSet().getImplementation(definition);
      assert impl != null;
      return impl.term;
    } else {
      return new FieldCallExpression(definition, thisExpr);
    }
  }

  public static ClassCallExpression ClassCall(ClassDefinition definition, LevelArguments polyArgs) {
    return new ClassCallExpression(definition, polyArgs);
  }

  public static ClassCallExpression ClassCall(ClassDefinition definition) {
    return ClassCall(definition, LevelArguments.STD);
  }

  public static ClassCallExpression ClassCall(ClassDefinition definition, LevelArguments polyParams, FieldSet fieldSet) {
    return new ClassCallExpression(definition, polyParams, fieldSet);
   }

  public static ConCallExpression ConCall(Constructor definition, LevelArguments polyParams, List<Expression> parameters, List<Expression> arguments) {
    return new ConCallExpression(definition, polyParams, parameters, arguments);
  }

  public static ConCallExpression ConCall(Constructor definition, LevelArguments polyParams, List<Expression> dataTypeArguments, Expression... arguments) {
    return ConCall(definition, polyParams, dataTypeArguments, Arrays.asList(arguments));
  }

  public static ConCallExpression ConCall(Constructor definition, Level lp, Level lh, List<Expression> dataTypeArguments, List<Expression> arguments) {
    return new ConCallExpression(definition, new LevelArguments(lp, lh), dataTypeArguments, arguments);
  }

  public static ConCallExpression ConCall(Constructor definition, Level lp, Level lh, List<Expression> dataTypeArguments, Expression... arguments) {
    return ConCall(definition, lp, lh, dataTypeArguments, Arrays.asList(arguments));
  }

  public static DataCallExpression Interval() {
    return DataCall(Prelude.INTERVAL, LevelArguments.ZERO, Collections.<Expression>emptyList());
  }

  public static ConCallExpression Left() {
    return ConCall(Prelude.LEFT, LevelArguments.ZERO, Collections.<Expression>emptyList(), Collections.<Expression>emptyList());
  }

  public static ConCallExpression Right() {
    return ConCall(Prelude.RIGHT, LevelArguments.ZERO, Collections.<Expression>emptyList(), Collections.<Expression>emptyList());
  }

  public static NewExpression New(ClassCallExpression expression) {
    return new NewExpression(expression);
  }

  public static ReferenceExpression Reference(Binding binding) {
    return new ReferenceExpression(binding);
  }

  public static LamExpression Lam(DependentLink link, Expression body) {
    return new LamExpression(link, body);
  }

  public static LetExpression Let(List<LetClause> clauses, Expression expr) {
    return new LetExpression(clauses, expr);
  }

  public static List<LetClause> lets(LetClause... letClauses) {
    return Arrays.asList(letClauses);
  }

  public static LetClause let(String name, Expression expr) {
    return let(name, leaf(expr));
  }

  public static LetClause let(String name, ElimTreeNode elimTree) {
    return let(name, Collections.emptyList(), elimTree);
  }

  public static LetClause let(String name, SingleDependentLink param, Expression expr) {
    return let(name, Collections.singletonList(param), leaf(expr));
  }

  public static LetClause let(String name, List<SingleDependentLink> params, Expression expr) {
    return let(name, params, leaf(expr));
  }

  public static LetClause let(String name, List<SingleDependentLink> params, ElimTreeNode elimTree) {
    return let(name, params, null, elimTree);
  }

  public static LetClause let(String name, List<SingleDependentLink> params, Expression resultType, Abstract.Definition.Arrow arrow, Expression expr) {
    return let(name, params, resultType, leaf(arrow, expr));
  }

  public static LetClause let(String name, SingleDependentLink param, Expression resultType, Expression expr) {
    return let(name, Collections.singletonList(param), resultType, leaf(expr));
  }

  public static LetClause let(String name, List<SingleDependentLink> params, Expression resultType, Expression expr) {
    return let(name, params, resultType, leaf(expr));
  }

  public static LetClause let(String name, List<SingleDependentLink> params, Expression resultType, ElimTreeNode elimTree) {
    return new LetClause(name, Collections.nCopies(params.size(), new Level(0)), params, resultType, elimTree);
  }

  public static DependentLink params(DependentLink... links) {
    for (int i = 0; i < links.length - 1; i++) {
      links[i].setNext(links[i + 1]);
    }
    return links[0];
  }

  public static DependentLink param(boolean explicit, String var, Expression type) {
    return new TypedDependentLink(explicit, var, type, EmptyDependentLink.getInstance());
  }

  public static TypedDependentLink param(String var, Expression type) {
    return new TypedDependentLink(true, var, type, EmptyDependentLink.getInstance());
  }

  public static DependentLink param(Expression type) {
    return new TypedDependentLink(true, null, type, EmptyDependentLink.getInstance());
  }

  public static List<String> vars(String... vars) {
    return Arrays.asList(vars);
  }

  public static DependentLink param(boolean explicit, List<String> names, Expression type) {
    DependentLink link = new TypedDependentLink(explicit, names.get(names.size() - 1), type, EmptyDependentLink.getInstance());
    for (int i = names.size() - 2; i >= 0; i--) {
      link = new UntypedDependentLink(names.get(i), link);
    }
    return link;
  }

  public static TypedSingleDependentLink singleParam(String name, Expression type) {
    return new TypedSingleDependentLink(true, name, type);
  }

  public static SingleDependentLink singleParam(boolean explicit, List<String> names, Expression type) {
    SingleDependentLink link = new TypedSingleDependentLink(explicit, names.get(names.size() - 1), type);
    for (int i = names.size() - 2; i >= 0; i--) {
      link = new UntypedSingleDependentLink(names.get(i), link);
    }
    return link;
  }

  public static DependentLink param(Abstract.Argument argument, Expression type) {
    if (argument instanceof Abstract.TelescopeArgument) {
      return param(argument.getExplicit(), ((Abstract.TelescopeArgument) argument).getNames(), type);
    } else {
      return param(type);
    }
  }

  public static PiExpression Pi(DependentLink domain, Expression codomain) {
    assert domain.hasNext();
    return new PiExpression(Collections.singletonList(new Level(0)), domain, codomain);
  }

  public static PiExpression Pi(Expression domain, Expression codomain) {
    return new PiExpression(Collections.singletonList(new Level(0)), param(domain), codomain);
  }

  public static SigmaExpression Sigma(DependentLink domain) {
    return new SigmaExpression(Sort.PROP, domain);
  }

  public static TupleExpression Tuple(List<Expression> fields, SigmaExpression type) {
    return new TupleExpression(fields, type);
  }

  public static TupleExpression Tuple(SigmaExpression type, Expression... fields) {
    return new TupleExpression(Arrays.asList(fields), type);
  }

  public static ProjExpression Proj(Expression expr, int field) {
    return new ProjExpression(expr, field);
  }

  public static DataCallExpression Nat() {
    return DataCall(Prelude.NAT, LevelArguments.ZERO, Collections.<Expression>emptyList());
  }

  public static ConCallExpression Zero() {
    return ConCall(Prelude.ZERO, LevelArguments.ZERO, Collections.<Expression>emptyList(), Collections.<Expression>emptyList());
  }

  public static ConCallExpression Suc(Expression expr) {
    return ConCall(Prelude.SUC, LevelArguments.ZERO, Collections.<Expression>emptyList(), Collections.singletonList(expr));
  }

  public static UniverseExpression Universe(int pLevel) {
    return new UniverseExpression(Sort.TypeOfLevel(pLevel));
  }

  public static UniverseExpression Universe(int pLevel, int hLevel) {
    return new UniverseExpression(hLevel == -1 ? Sort.PROP : new Sort(pLevel, hLevel));
  }

  public static UniverseExpression Universe(Level pLevel, Level hLevel) {
    return new UniverseExpression(new Sort(pLevel, hLevel));
  }

  public static UniverseExpression Universe(Sort universe) {
    return new UniverseExpression(universe);
  }

  public static ErrorExpression Error(Expression expr, LocalTypeCheckingError error) {
    return new ErrorExpression(expr, error);
  }

  public static PatternArgument match(boolean isExplicit, Constructor constructor, PatternArgument... patternArgs) {
    return new PatternArgument(new ConstructorPattern(constructor, new Patterns(Arrays.asList(patternArgs))), isExplicit, false);
  }

  public static PatternArgument match(Constructor constructor, PatternArgument... patterns) {
    return match(true, constructor, patterns);
  }

  public static class ConstructorClausePair {
    private final Constructor constructor;
    private final DependentLink parameters;
    private final ElimTreeNode child;

    private ConstructorClausePair(Constructor constructor, DependentLink parameters, ElimTreeNode child) {
      this.constructor = constructor;
      this.parameters = parameters;
      this.child = child;
    }
  }

  public static List<Binding> tail(Binding... bindings) {
    return Arrays.asList(bindings);
  }

  public static ElimTreeNode top(DependentLink parameters, ElimTreeNode tree) {
    tree.updateLeavesMatched(DependentLink.Helper.toContext(parameters));
    return tree;
  }

  public static ElimTreeNode top(List<SingleDependentLink> parameters, ElimTreeNode tree) {
    List<Binding> context = new ArrayList<>();
    for (SingleDependentLink link : parameters) {
      context.addAll(DependentLink.Helper.toContext(link));
    }
    tree.updateLeavesMatched(context);
    return tree;
  }

  public static BranchElimTreeNode branch(Binding reference, List<Binding> tail, ConstructorClausePair... clauses) {
    BranchElimTreeNode result = new BranchElimTreeNode(reference, tail);
    for (ConstructorClausePair pair : clauses) {
      if (pair.constructor != null) {
        ConstructorClause clause = result.addClause(pair.constructor, DependentLink.Helper.toNames(pair.parameters));
        ExprSubstitution subst = clause.getSubst();
        assert DependentLink.Helper.size(pair.constructor.getParameters()) == DependentLink.Helper.size(pair.parameters);
        for (DependentLink linkFake = pair.parameters, linkTrue = clause.getParameters();
             linkFake.hasNext(); linkFake = linkFake.getNext(), linkTrue = linkTrue.getNext()) {
          subst.add(linkFake, Reference(linkTrue));
        }
        clause.setChild(pair.child.subst(subst));
      } else {
        OtherwiseClause clause = result.addOtherwiseClause();
        clause.setChild(pair.child);
      }
    }
    return result;
  }

  public static LeafElimTreeNode leaf(Expression expression) {
    return new LeafElimTreeNode(Abstract.Definition.Arrow.RIGHT, expression);
  }

  public static LeafElimTreeNode leaf(Abstract.Definition.Arrow arrow, Expression expression) {
    return new LeafElimTreeNode(arrow, expression);
  }

  public static ConstructorClausePair clause(Constructor constructor, DependentLink parameters, BranchElimTreeNode node) {
    return new ConstructorClausePair(constructor, parameters, node);
  }

  public static ConstructorClausePair clause(ElimTreeNode node) {
    return new ConstructorClausePair(null, null, node);
  }

  public static ConstructorClausePair clause(Expression expr) {
    return clause(leaf(expr));
  }

  public static ConstructorClausePair clause(Constructor constructor, DependentLink parameters, Abstract.Definition.Arrow arrow, Expression expr) {
    return new ConstructorClausePair(constructor, parameters, leaf(arrow, expr));
  }

  public static ConstructorClausePair clause(Constructor constructor, DependentLink parameters, Expression expr) {
    return new ConstructorClausePair(constructor, parameters, leaf(Abstract.Definition.Arrow.RIGHT, expr));
  }
}
