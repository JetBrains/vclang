package org.arend.core.expr;

import org.arend.core.context.binding.inference.InferenceVariable;
import org.arend.core.definition.ClassField;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.ext.core.expr.CoreExpressionVisitor;
import org.arend.ext.core.expr.CoreInferenceReferenceExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.typechecking.implicitargs.equations.Equations;
import org.arend.util.Decision;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class InferenceReferenceExpression extends Expression implements CoreInferenceReferenceExpression {
  private final InferenceVariable myVar;
  private Set<ClassField> myImplementedFields;
  private Expression mySubstExpression;

  public static Expression make(InferenceVariable binding, Equations equations) {
    InferenceReferenceExpression result = new InferenceReferenceExpression(binding);
    if (!equations.supportsExpressions()) {
      return result;
    }

    if (!binding.resetClassCall()) {
      return result;
    }

    Expression type = binding.getType().normalize(NormalizationMode.WHNF);
    ClassCallExpression classCall = type.cast(ClassCallExpression.class);
    if (classCall != null && !classCall.getDefinition().getFields().isEmpty()) {
      type = new ClassCallExpression(classCall.getDefinition(), classCall.getLevels());
      binding.setType(type);
      result.myImplementedFields = new HashSet<>();
      for (ClassField field : classCall.getDefinition().getFields()) {
        if (!field.isProperty()) {
          Expression impl = classCall.getImplementationHere(field, result);
          if (impl != null) {
            equations.addEquation(FieldCallExpression.make(field, classCall.getLevels(), result), impl.normalize(NormalizationMode.WHNF), classCall.getDefinition().getFieldType(field, classCall.getLevels(), result), CMP.EQ, binding.getSourceNode(), binding, impl.getStuckInferenceVariable(), false);
            if (result.getSubstExpression() != null) {
              Expression solution = result.getSubstExpression();
              binding.setType(classCall);
              binding.unsolve();
              return equations.solve(binding, solution) ? solution : result;
            }
            result.myImplementedFields.add(field);
          }
        }
      }
    }
    return result;
  }

  public InferenceReferenceExpression(InferenceVariable binding, Expression substExpression) {
    myVar = binding;
    mySubstExpression = substExpression;
  }

  public InferenceReferenceExpression(InferenceVariable binding) {
    myVar = binding;
    binding.setReference(this);
  }

  @Override
  public InferenceVariable getVariable() {
    return mySubstExpression == null ? myVar : null;
  }

  public InferenceVariable getOriginalVariable() {
    return myVar;
  }

  public boolean isFieldImplemented(ClassField field) {
    return myImplementedFields != null && myImplementedFields.contains(field);
  }

  @Override
  public Expression getSubstExpression() {
    return mySubstExpression;
  }

  public void setSubstExpression(Expression substExpression) {
    mySubstExpression = substExpression;
  }

  @Override
  public boolean canBeConstructor() {
    return mySubstExpression == null || mySubstExpression.canBeConstructor();
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitInferenceReference(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitInferenceReference(this, param1, param2);
  }

  @Override
  public <P, R> R accept(@NotNull CoreExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitInferenceReference(this, params);
  }

  @NotNull
  @Override
  public Expression getUnderlyingExpression() {
    return mySubstExpression == null ? this : mySubstExpression.getUnderlyingExpression();
  }

  @Override
  public <T extends Expression> boolean isInstance(Class<T> clazz) {
    return mySubstExpression != null && mySubstExpression.isInstance(clazz) || clazz.isInstance(this);
  }

  @Override
  public <T extends Expression> T cast(Class<T> clazz) {
    return clazz.isInstance(this) ? clazz.cast(this) : mySubstExpression != null ? mySubstExpression.cast(clazz) : null;
  }

  @Override
  public Decision isWHNF() {
    return mySubstExpression == null ? Decision.MAYBE : mySubstExpression.isWHNF();
  }

  @Override
  public Expression getStuckExpression() {
    return mySubstExpression != null ? mySubstExpression.getStuckExpression() : this;
  }
}
