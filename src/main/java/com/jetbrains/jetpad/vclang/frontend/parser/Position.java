package com.jetbrains.jetpad.vclang.frontend.parser;

import com.jetbrains.jetpad.vclang.error.SourcePosition;
import com.jetbrains.jetpad.vclang.module.ModulePath;

public class Position extends SourcePosition {
  public final ModulePath module;

  public Position(ModulePath module, int line, int column) {
    super(module == null ? null : module.toString(), line, column + 1);
    this.module = module;
  }

  @Override
  public String toString() {
    return (module == null ? "" : module + ":") + line + ":" + column;
  }
}
