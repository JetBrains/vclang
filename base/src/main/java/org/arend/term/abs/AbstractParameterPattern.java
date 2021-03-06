package org.arend.term.abs;

import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AbstractParameterPattern implements Abstract.Pattern {
  private final Abstract.Parameter myParameter;
  private final Referable myReferable;

  public AbstractParameterPattern(Abstract.Parameter parameter, Referable referable) {
    myParameter = parameter;
    myReferable = referable;
  }

  @Override
  public @NotNull Abstract.SourceNode getTopmostEquivalentSourceNode() {
    return myParameter.getTopmostEquivalentSourceNode();
  }

  @Override
  public @Nullable Abstract.SourceNode getParentSourceNode() {
    return myParameter.getParentSourceNode();
  }

  @Override
  public @Nullable Object getData() {
    return myParameter.getData();
  }

  @Override
  public boolean isUnnamed() {
    return myReferable == null;
  }

  @Override
  public boolean isExplicit() {
    return myParameter.isExplicit();
  }

  @Override
  public @Nullable Integer getInteger() {
    return null;
  }

  @Override
  public @Nullable Referable getHeadReference() {
    return myReferable;
  }

  @Override
  public @NotNull List<? extends Abstract.Pattern> getArguments() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Abstract.Expression getType() {
    return myParameter.getType();
  }

  @Override
  public @NotNull List<? extends Abstract.TypedReferable> getAsPatterns() {
    return Collections.emptyList();
  }
}
