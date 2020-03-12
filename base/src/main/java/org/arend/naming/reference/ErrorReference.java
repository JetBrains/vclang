package org.arend.naming.reference;

import org.arend.ext.error.LocalError;
import org.arend.naming.error.NotInScopeError;
import org.jetbrains.annotations.NotNull;

public class ErrorReference implements Referable {
  private final String myText;
  private final LocalError myError;

  public ErrorReference(Object data, Referable referable, int index, String name) {
    myText = name;
    myError = new NotInScopeError(data, referable, index, name);
  }

  public ErrorReference(Object data, String name) {
    myText = name;
    myError = new NotInScopeError(data, null, 0, name);
  }

  public ErrorReference(LocalError error, String name) {
    myText = name;
    myError = error;
  }

  public LocalError getError() {
    return myError;
  }

  @NotNull
  @Override
  public String textRepresentation() {
    return myText;
  }
}