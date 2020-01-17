package org.arend.typechecking.error;

import org.arend.core.definition.Definition;
import org.arend.ext.error.GeneralError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.reference.ArendRef;
import org.arend.naming.reference.GlobalReferable;
import org.arend.term.concrete.Concrete;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class CycleError extends GeneralError {
  public final List<? extends GlobalReferable> cycle;
  public final Concrete.SourceNode cause;
  private final GlobalReferable myCauseReferable;

  private CycleError(List<? extends GlobalReferable> cycle, GlobalReferable causeReferable, Concrete.SourceNode cause) {
    super(Level.ERROR, "Dependency cycle");
    this.cycle = cycle;
    this.cause = cause;
    myCauseReferable = causeReferable;
  }

  public CycleError(List<? extends GlobalReferable> cycle, Concrete.SourceNode cause) {
    this(cycle, null, cause);
  }

  public CycleError(List<? extends GlobalReferable> cycle) {
    this(cycle, null, null);
  }

  public static CycleError fromConcrete(List<? extends Concrete.Definition> cycle) {
    List<GlobalReferable> refs = new ArrayList<>(cycle.size());
    for (Concrete.Definition definition : cycle) {
      refs.add(definition.getData());
    }
    return new CycleError(refs, null);
  }

  public static CycleError fromTypechecked(List<? extends Definition> cycle, Concrete.SourceNode cause) {
    List<GlobalReferable> refs = new ArrayList<>(cycle.size());
    for (Definition definition : cycle) {
      refs.add(definition.getReferable());
    }
    return new CycleError(refs, cause);
  }

  @Override
  public Concrete.SourceNode getCauseSourceNode() {
    return cause;
  }

  @Override
  public Object getCause() {
    if (cause != null) {
      Object data = cause.getData();
      if (data != null) {
        return data;
      }
    }
    return myCauseReferable != null ? myCauseReferable : cycle;
  }

  @Override
  public Doc getCauseDoc(PrettyPrinterConfig ppConfig) {
    Doc causeDoc = super.getCauseDoc(ppConfig);
    return causeDoc != null ? causeDoc : refDoc(cycle.get(0));
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig src) {
    List<LineDoc> docs = new ArrayList<>(cycle.size() + 1);
    for (GlobalReferable definition : cycle) {
      docs.add(refDoc(definition));
    }
    docs.add(refDoc(cycle.get(0)));
    return hSep(text(" - "), docs);
  }

  @Override
  public void forAffectedDefinitions(BiConsumer<ArendRef, GeneralError> consumer) {
    Object causeData = cause != null ? cause.getData() : null;
    if (causeData instanceof GlobalReferable) {
      consumer.accept((GlobalReferable) causeData, this);
    } else {
      for (GlobalReferable ref : cycle) {
        consumer.accept(ref, new CycleError(cycle, ref, cause));
      }
    }
  }

  @Override
  public Stage getStage() {
    return Stage.TYPECHECKER;
  }
}
