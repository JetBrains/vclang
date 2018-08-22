package com.jetbrains.jetpad.vclang.core.definition;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.expr.FieldCallExpression;
import com.jetbrains.jetpad.vclang.core.expr.PiExpression;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.SubstVisitor;
import com.jetbrains.jetpad.vclang.naming.reference.TCFieldReferable;

import java.util.List;

public class ClassField extends Definition {
  private final ClassDefinition myParentClass;
  private PiExpression myType;

  public ClassField(TCFieldReferable referable, ClassDefinition parentClass) {
    super(referable, TypeCheckingStatus.HEADER_HAS_ERRORS);
    myParentClass = parentClass;
  }

  public ClassField(TCFieldReferable referable, ClassDefinition parentClass, PiExpression type) {
    super(referable, TypeCheckingStatus.NO_ERRORS);
    myParentClass = parentClass;
    myType = type;
  }

  @Override
  public TCFieldReferable getReferable() {
    return (TCFieldReferable) super.getReferable();
  }

  public ClassDefinition getParentClass() {
    return myParentClass;
  }

  public void setType(PiExpression type) {
    myType = type;
  }

  public PiExpression getType(Sort sortArgument) {
    return sortArgument == Sort.STD ? myType : new SubstVisitor(new ExprSubstitution(), sortArgument.toLevelSubstitution()).visitPi(myType, null);
  }

  @Override
  public Expression getTypeWithParams(List<? super DependentLink> params, Sort sortArgument) {
    PiExpression type = getType(sortArgument);
    params.add(type.getParameters());
    return type.getCodomain();
  }

  @Override
  public Expression getDefCall(Sort sortArgument, List<Expression> args) {
    return FieldCallExpression.make(this, sortArgument, args.get(0));
  }
}
