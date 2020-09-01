package org.arend.core.definition;

import org.arend.core.pattern.ExpressionPattern;
import org.arend.naming.reference.TCDefReferable;

public class DConstructor extends FunctionDefinition {
  private int myNumberOfParameters;
  private ExpressionPattern myPattern;

  public DConstructor(TCDefReferable referable) {
    super(referable);
  }

  public int getNumberOfParameters() {
    return myNumberOfParameters;
  }

  public void setNumberOfParameters(int numberOfParameters) {
    myNumberOfParameters = numberOfParameters;
  }

  public ExpressionPattern getPattern() {
    return myPattern;
  }

  public void setPattern(ExpressionPattern pattern) {
    myPattern = pattern;
  }
}
