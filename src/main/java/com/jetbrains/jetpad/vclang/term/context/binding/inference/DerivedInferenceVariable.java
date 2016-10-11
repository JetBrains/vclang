package com.jetbrains.jetpad.vclang.term.context.binding.inference;

import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.type.Type;
import com.jetbrains.jetpad.vclang.typechecking.error.local.LocalTypeCheckingError;

public class DerivedInferenceVariable extends InferenceVariable {
  private final InferenceVariable myVar;

  public DerivedInferenceVariable(String name, InferenceVariable binding) {
    super(name, binding.getType(), binding.getSourceNode());
    myVar = binding;
  }

  @Override
  public LocalTypeCheckingError getErrorInfer(Expression... candidates) {
    return myVar.getErrorInfer(candidates);
  }

  @Override
  public LocalTypeCheckingError getErrorMismatch(Expression expectedType, Type actualType, Expression candidate) {
    return myVar.getErrorMismatch(expectedType, actualType, candidate);
  }
}
