package org.arend.naming;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.naming.error.PrecedenceError;
import org.arend.naming.reference.*;
import org.arend.naming.renamer.Renamer;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.term.Fixity;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.order.PartialComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This class extracts and invokes meta resolvers from a binOp sequences and replaces corresponding subsequences with results
 */
public class MetaBinOpParser {
  public static class ResolvedReference {
    public final Concrete.ReferenceExpression refExpr;
    public final UnresolvedReference originalReference;
    public final List<Referable> resolvedList;

    public ResolvedReference(Concrete.ReferenceExpression refExpr, UnresolvedReference originalReference, List<Referable> resolvedList) {
      this.refExpr = refExpr;
      this.originalReference = originalReference;
      this.resolvedList = resolvedList;
    }

    boolean isMeta() {
      return refExpr.getReferent() instanceof MetaReferable && ((MetaReferable) refExpr.getReferent()).getResolver() != null;
    }
  }

  private final ExpressionResolveNameVisitor myVisitor;
  private Concrete.BinOpSequenceExpression myExpression;
  private final List<ResolvedReference> myResolvedReferences;
  private final List<Concrete.BinOpSequenceElem> myResult = new ArrayList<>();

  // The sizes of myResolvedReferences and myExpression.getSequence() are the same.
  // An element of myResolvedReferences is null if corresponding element in myExpression.getSequence() is not a (possibly) infix ReferenceExpression.
  // Non-null elements of myResolvedReferences are already resolved but not reported.
  // Thus, every such element must be either finalized by ExpressionResolveNameVisitor.finalizeReference or reset by resetReference and passed to a meta resolver.

  public MetaBinOpParser(ExpressionResolveNameVisitor visitor, Concrete.BinOpSequenceExpression expr, List<ResolvedReference> resolvedReferences) {
    this.myVisitor = visitor;
    this.myExpression = expr;
    this.myResolvedReferences = resolvedReferences;
  }

  public Concrete.Expression parse() {
    List<Concrete.BinOpSequenceElem> sequence = myExpression.getSequence();
    Concrete.BinOpSequenceElem first = sequence.get(0);
    if (first.fixity == Fixity.INFIX || first.fixity == Fixity.POSTFIX) {
      LocalReferable firstArg = new LocalReferable(Renamer.UNNAMED);
      myResolvedReferences.add(0, null);
      List<Concrete.BinOpSequenceElem> newSequence = new ArrayList<>(sequence.size() + 1);
      newSequence.add(new Concrete.BinOpSequenceElem(new Concrete.ReferenceExpression(myExpression.getData(), firstArg)));
      newSequence.addAll(sequence);
      myExpression = new Concrete.BinOpSequenceExpression(myExpression.getData(), newSequence);
      return new Concrete.LamExpression(myExpression.getData(), Collections.singletonList(new Concrete.NameParameter(myExpression.getData(), true, firstArg)), parse());
    }

    parse(0, sequence.size());
    return myResult.size() == 1 ? myResult.get(0).expression : new Concrete.BinOpSequenceExpression(myExpression.getData(), myResult);
  }

  private void parse(int start, int end) {
    if (start == end) {
      return;
    }

    int conflictIndex = -1;
    int minIndex = -1;
    Precedence minPrecedence = Precedence.DEFAULT;
    List<Concrete.BinOpSequenceElem> sequence = myExpression.getSequence();

    for (int i = start; i < end; i++) {
      ResolvedReference resolvedRef = myResolvedReferences.get(i);
      if (resolvedRef == null) {
        continue;
      }

      Concrete.BinOpSequenceElem elem = sequence.get(i);
      Precedence precedence = resolvedRef.refExpr.getReferent() instanceof GlobalReferable ? ((GlobalReferable) resolvedRef.refExpr.getReferent()).getPrecedence() : null;
      if (elem.fixity == Fixity.INFIX || elem.fixity == Fixity.POSTFIX || elem.fixity == Fixity.UNKNOWN && precedence != null && precedence.isInfix) {
        if (precedence == null) {
          precedence = Precedence.DEFAULT;
        }
        if (minIndex != -1) {
          PartialComparator.Result cmp = sequence.get(minIndex).fixity == Fixity.POSTFIX ? PartialComparator.Result.GREATER : comparePrecedence(minPrecedence, precedence);
          if (cmp == PartialComparator.Result.UNCOMPARABLE && elem.fixity == Fixity.POSTFIX) {
            if (minPrecedence.associativity == Precedence.Associativity.LEFT_ASSOC) {
              cmp = PartialComparator.Result.GREATER;
            } else if (minPrecedence.associativity == Precedence.Associativity.RIGHT_ASSOC) {
              cmp = PartialComparator.Result.LESS;
            }
          }
          if (cmp == PartialComparator.Result.LESS) {
            continue;
          }
          if (cmp == PartialComparator.Result.UNCOMPARABLE) {
            conflictIndex = minIndex;
          }
        }
        minIndex = i;
        minPrecedence = precedence;
      }
    }

    if (minIndex == -1) {
      ResolvedReference firstRef = myResolvedReferences.get(start);
      if (firstRef != null && firstRef.isMeta()) {
        myVisitor.finalizeReference(sequence.get(start), firstRef);
        List<Concrete.Argument> args = new ArrayList<>(end - start - 1);
        for (int i = start + 1; i < end; i++) {
          resetReference(sequence.get(i), myResolvedReferences.get(i));
          args.add(new Concrete.Argument(sequence.get(i).expression, sequence.get(i).isExplicit));
        }
        myResult.add(new Concrete.BinOpSequenceElem(ExpressionResolveNameVisitor.castExpr(Objects.requireNonNull(((MetaReferable) firstRef.refExpr.getReferent()).getResolver()).resolvePrefix(myVisitor, firstRef.refExpr, args), firstRef.refExpr.getData())));
      } else {
        for (int i = start; i < end; i++) {
          myVisitor.finalizeReference(sequence.get(i), myResolvedReferences.get(i));
          myResult.add(sequence.get(i));
        }
      }
      return;
    }

    boolean isMeta = myResolvedReferences.get(minIndex).isMeta();
    if (conflictIndex != -1 && (isMeta || myResolvedReferences.get(conflictIndex).isMeta())) {
      myVisitor.getErrorReporter().report(new PrecedenceError((GlobalReferable) myResolvedReferences.get(conflictIndex).refExpr.getReferent(), (GlobalReferable) myResolvedReferences.get(minIndex).refExpr.getReferent(), myResolvedReferences.get(isMeta ? minIndex : conflictIndex).refExpr));
    }

    if (isMeta) {
      Concrete.ReferenceExpression refExpr = myResolvedReferences.get(minIndex).refExpr;
      myVisitor.finalizeReference(sequence.get(minIndex), myResolvedReferences.get(minIndex));
      for (int i = start; i < end; i++) {
        if (i != minIndex) {
          resetReference(myExpression.getSequence().get(i), myResolvedReferences.get(i));
        }
      }

      MetaResolver metaDef = Objects.requireNonNull(((MetaReferable) refExpr.getReferent()).getResolver());
      ConcreteExpression metaResult;
      Concrete.Expression leftArg = start == minIndex ? null : new Concrete.BinOpSequenceExpression(myExpression.getData(), sequence.subList(start, minIndex));
      if (sequence.get(minIndex).fixity == Fixity.POSTFIX) {
        List<Concrete.Argument> args = new ArrayList<>(end - minIndex - 1);
        for (int i = minIndex + 1; i < end; i++) {
          args.add(new Concrete.Argument(sequence.get(i).expression, sequence.get(i).isExplicit));
        }
        metaResult = metaDef.resolvePostfix(myVisitor, refExpr, leftArg, args);
      } else {
        metaResult = metaDef.resolveInfix(myVisitor, refExpr, leftArg, minIndex + 1 == end ? null : new Concrete.BinOpSequenceExpression(myExpression.getData(), sequence.subList(minIndex + 1, end)));
      }
      myResult.add(new Concrete.BinOpSequenceElem(ExpressionResolveNameVisitor.castExpr(metaResult, refExpr.getData())));
    } else {
      parse(start, minIndex);
      myVisitor.finalizeReference(sequence.get(minIndex), myResolvedReferences.get(minIndex));
      myResult.add(sequence.get(minIndex));
      parse(minIndex + 1, end);
    }
  }

  private static PartialComparator.Result comparePrecedence(Precedence prec1, Precedence prec2) {
    return prec1.priority < prec2.priority
      ? PartialComparator.Result.LESS
      : prec1.priority > prec2.priority
        ? PartialComparator.Result.GREATER
        : prec1.associativity != prec2.associativity || prec1.associativity == Precedence.Associativity.NON_ASSOC
          ? PartialComparator.Result.UNCOMPARABLE
          : prec1.associativity == Precedence.Associativity.LEFT_ASSOC
            ? PartialComparator.Result.GREATER
            : PartialComparator.Result.LESS;
  }

  public void resetReference(Concrete.BinOpSequenceElem elem, MetaBinOpParser.ResolvedReference resolvedReference) {
    if (resolvedReference != null) {
      resolvedReference.originalReference.reset();
      elem.expression = new Concrete.ReferenceExpression(resolvedReference.refExpr.getData(), resolvedReference.originalReference, resolvedReference.refExpr.getPLevel(), resolvedReference.refExpr.getHLevel());
    }
  }
}