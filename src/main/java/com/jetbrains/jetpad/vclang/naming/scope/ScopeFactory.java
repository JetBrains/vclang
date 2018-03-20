package com.jetbrains.jetpad.vclang.naming.scope;

import com.jetbrains.jetpad.vclang.module.scopeprovider.EmptyModuleScopeProvider;
import com.jetbrains.jetpad.vclang.module.scopeprovider.ModuleScopeProvider;
import com.jetbrains.jetpad.vclang.naming.reference.Referable;
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference;
import com.jetbrains.jetpad.vclang.naming.scope.local.LetScope;
import com.jetbrains.jetpad.vclang.naming.scope.local.PatternScope;
import com.jetbrains.jetpad.vclang.naming.scope.local.TelescopeScope;
import com.jetbrains.jetpad.vclang.prelude.PreludeLibrary;
import com.jetbrains.jetpad.vclang.term.NamespaceCommand;
import com.jetbrains.jetpad.vclang.term.abs.Abstract;
import com.jetbrains.jetpad.vclang.term.group.ChildGroup;
import com.jetbrains.jetpad.vclang.term.group.Group;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ScopeFactory {
  public static @Nonnull Scope forGroup(@Nullable Group group, @Nonnull ModuleScopeProvider moduleScopeProvider) {
    ChildGroup parentGroup = group instanceof ChildGroup ? ((ChildGroup) group).getParentGroup() : null;
    Scope parentScope;
    if (parentGroup == null) {
      Scope preludeScope = PreludeLibrary.getPreludeScope();
      if (group == null) {
        return preludeScope == null ? EmptyScope.INSTANCE : preludeScope;
      }

      ImportedScope importedScope = new ImportedScope(group, moduleScopeProvider);
      parentScope = preludeScope == null ? importedScope : new MergeScope(preludeScope, importedScope);
    } else {
      parentScope = forGroup(parentGroup, moduleScopeProvider);
    }
    return LexicalScope.insideOf(group, parentScope);
  }

  public static Scope forSourceNode(Scope parentScope, Abstract.SourceNode sourceNode) {
    if (sourceNode == null) {
      return parentScope;
    }

    // We cannot use any references in level expressions
    if (sourceNode instanceof Abstract.LevelExpression) {
      return EmptyScope.INSTANCE;
    }

    // We can use only global definitions in patterns
    if (sourceNode instanceof Abstract.Pattern) {
      return parentScope.getGlobalSubscope();
    }

    Abstract.SourceNode parentSourceNode = sourceNode.getParentSourceNode();

    // After namespace command
    if (parentSourceNode instanceof Abstract.NamespaceCommandHolder && sourceNode instanceof Abstract.Reference) {
      Scope scope = ((Abstract.NamespaceCommandHolder) parentSourceNode).getKind() == NamespaceCommand.Kind.IMPORT ? new ImportedScope(parentScope.getImportedSubscope() /* TODO[scopes]: may be null? */, EmptyModuleScopeProvider.INSTANCE) : parentScope.getGlobalSubscope();
      if (sourceNode.equals(((Abstract.NamespaceCommandHolder) parentSourceNode).getOpenedReference())) {
        return scope;
      } else {
        scope = Scope.Utils.resolveNamespace(scope, ((Abstract.NamespaceCommandHolder) parentSourceNode).getPath());
        return scope == null ? EmptyScope.INSTANCE : scope;
      }
    }

    // After a dot
    if (parentSourceNode instanceof Abstract.LongReference && sourceNode instanceof Abstract.Reference) {
      Abstract.Reference headRef = ((Abstract.LongReference) parentSourceNode).getHeadReference();
      if (headRef == null || sourceNode.equals(headRef)) {
        return parentScope;
      }

      Scope scope = parentScope.getGlobalSubscope().resolveNamespace(headRef.getReferent().textRepresentation(), true);
      for (Abstract.Reference reference : ((Abstract.LongReference) parentSourceNode).getTailReferences()) {
        if (scope == null || reference == null) {
          return EmptyScope.INSTANCE;
        }
        if (sourceNode.equals(reference)) {
          return scope;
        }
        scope = scope.resolveNamespace(reference.getReferent().textRepresentation(), true);
      }

      return EmptyScope.INSTANCE;
    }

    // We cannot use any references in the universe of a data type
    if (parentSourceNode instanceof Abstract.DataDefinition && sourceNode instanceof Abstract.Expression) {
      return EmptyScope.INSTANCE;
    }

    if (parentSourceNode instanceof Abstract.EliminatedExpressionsHolder) {
      // We can use only parameters in eliminated expressions
      if (sourceNode instanceof Abstract.Reference) {
        Collection<? extends Abstract.Reference> elimExprs = ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getEliminatedExpressions();
        if (elimExprs != null) {
          for (Abstract.Reference elimExpr : elimExprs) {
            if (sourceNode.equals(elimExpr)) {
              return new TelescopeScope(EmptyScope.INSTANCE, ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getParameters());
            }
          }
        }
      }

      // Remove eliminated expressions from the scope in clauses
      if (sourceNode instanceof Abstract.Clause) {
        Collection<? extends Abstract.Reference> elimExprs = ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getEliminatedExpressions();
        if (elimExprs != null) {
          if (elimExprs.isEmpty()) {
            return parentScope;
          } else {
            List<Referable> excluded = new ArrayList<>(elimExprs.size());
            Scope parametersScope = new TelescopeScope(EmptyScope.INSTANCE, ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getParameters());
            for (Abstract.Reference elimExpr : elimExprs) {
              Referable referable = elimExpr.getReferent();
              if (referable instanceof UnresolvedReference) {
                referable = ((UnresolvedReference) referable).resolve(parametersScope);
              }
              excluded.add(referable);
            }
            return new TelescopeScope(parentScope, ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getParameters(), excluded);
          }
        }
      }
    }

    // Extend the scope with parameters
    if (parentSourceNode instanceof Abstract.ParametersHolder) {
      List<? extends Abstract.Parameter> parameters = ((Abstract.ParametersHolder) parentSourceNode).getParameters();
      if (sourceNode instanceof Abstract.Parameter && !(sourceNode instanceof Abstract.Expression)) {
        List<Abstract.Parameter> parameters1 = new ArrayList<>(parameters.size());
        for (Abstract.Parameter parameter : parameters) {
          if (sourceNode.equals(parameter)) {
            break;
          }
          parameters1.add(parameter);
        }
        return new TelescopeScope(parentScope, parameters1);
      } else {
        return new TelescopeScope(parentScope, parameters);
      }
    }

    // Extend the scope with let clauses
    if (parentSourceNode instanceof Abstract.LetClausesHolder) {
      Collection<? extends Abstract.LetClause> clauses = ((Abstract.LetClausesHolder) parentSourceNode).getLetClauses();
      List<Abstract.LetClause> clauses1;
      if (sourceNode instanceof Abstract.LetClause) {
        clauses1 = new ArrayList<>(clauses.size());
        for (Abstract.LetClause clause : clauses) {
          if (sourceNode.equals(clause)) {
            break;
          }
          clauses1.add(clause);
        }
      } else {
        clauses1 = new ArrayList<>(clauses);
      }
      return new LetScope(parentScope, clauses1);
    }

    // Replace the scope with class fields in class extensions
    if (parentSourceNode instanceof Abstract.ClassFieldImpl && !(sourceNode instanceof Abstract.Expression)) { // TODO[scopes]: always false?
      Abstract.SourceNode parentParent = parentSourceNode.getParentSourceNode();
      if (parentParent instanceof Abstract.ClassReferenceHolder) {
        return new ClassFieldImplScope(((Abstract.ClassReferenceHolder) parentParent).getClassReference());
      }
    }

    // Extend the scope with patterns
    if (parentSourceNode instanceof Abstract.Clause && sourceNode instanceof Abstract.Expression) {
      return new PatternScope(parentScope, ((Abstract.Clause) parentSourceNode).getPatterns());
    }

    return parentScope;
  }
}