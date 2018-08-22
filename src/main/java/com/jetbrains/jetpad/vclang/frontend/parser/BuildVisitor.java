package com.jetbrains.jetpad.vclang.frontend.parser;

import com.jetbrains.jetpad.vclang.error.ErrorReporter;
import com.jetbrains.jetpad.vclang.frontend.reference.*;
import com.jetbrains.jetpad.vclang.module.ModulePath;
import com.jetbrains.jetpad.vclang.naming.reference.*;
import com.jetbrains.jetpad.vclang.term.Fixity;
import com.jetbrains.jetpad.vclang.term.NamespaceCommand;
import com.jetbrains.jetpad.vclang.term.Precedence;
import com.jetbrains.jetpad.vclang.term.concrete.Concrete;
import com.jetbrains.jetpad.vclang.term.group.*;
import com.jetbrains.jetpad.vclang.util.Pair;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.jetpad.vclang.frontend.parser.VcgrammarParser.*;

public class BuildVisitor extends VcgrammarBaseVisitor {
  private final ModulePath myModule;
  private final ErrorReporter myErrorReporter;

  public BuildVisitor(ModulePath module, ErrorReporter errorReporter) {
    myModule = module;
    myErrorReporter = errorReporter;
  }

  private String getVar(AtomFieldsAccContext ctx) {
    if (!ctx.fieldAcc().isEmpty() || !(ctx.atom() instanceof AtomLiteralContext)) {
      return null;
    }
    LiteralContext literal = ((AtomLiteralContext) ctx.atom()).literal();
    if (literal instanceof UnknownContext) {
      return "_";
    }
    if (literal instanceof NameContext && ((NameContext) literal).ID() != null) {
      return ((NameContext) literal).ID().getText();
    }
    return null;
  }

  private boolean getVars(ExprContext expr, List<ParsedLocalReferable> vars) {
    if (!(expr instanceof AppContext && ((AppContext) expr).appExpr() instanceof AppArgumentContext && ((AppContext) expr).NEW() == null && ((AppContext) expr).implementStatements() == null)) {
      return false;
    }

    AppArgumentContext argCtx = (AppArgumentContext) ((AppContext) expr).appExpr();
    if (!argCtx.onlyLevelAtom().isEmpty()) {
      return false;
    }
    String var = getVar(argCtx.atomFieldsAcc());
    if (var == null) {
      return false;
    }
    if (var.equals("_")) {
      var = null;
    }
    vars.add(new ParsedLocalReferable(tokenPosition(argCtx.start), var));

    for (ArgumentContext argument : argCtx.argument()) {
      if (argument instanceof ArgumentInfixContext) {
        vars.add(new ParsedLocalReferable(tokenPosition(((ArgumentInfixContext) argument).INFIX().getSymbol()), getInfixText(((ArgumentInfixContext) argument).INFIX())));
      } else if (argument instanceof ArgumentPostfixContext) {
        vars.add(new ParsedLocalReferable(tokenPosition(((ArgumentPostfixContext) argument).POSTFIX().getSymbol()), getPostfixText(((ArgumentPostfixContext) argument).POSTFIX())));
      } else if (argument instanceof ArgumentExplicitContext) {
        String arg = getVar(((ArgumentExplicitContext) argument).atomFieldsAcc());
        if (arg == null) {
          return false;
        }
        if (arg.equals("_")) {
          arg = null;
        }

        vars.add(new ParsedLocalReferable(tokenPosition(((ArgumentExplicitContext) argument).atomFieldsAcc().start), arg));
      } else {
        return false;
      }
    }

    return true;
  }

  private void getVarList(ExprContext expr, List<ParsedLocalReferable> vars) {
    if (!getVars(expr, vars)) {
      myErrorReporter.report(new ParserError(tokenPosition(expr.start), "Expected a list of variables"));
      throw new ParseException();
    }
  }

  private String getInfixText(TerminalNode node) {
    String name = node.getText();
    return name.substring(1, name.length() - 1);
  }

  private String getPostfixText(TerminalNode node) {
    String name = node.getText();
    return name.substring(1, name.length());
  }

  public Concrete.Expression visitExpr(ExprContext expr) {
    return (Concrete.Expression) visit(expr);
  }

  private Concrete.Expression visitExpr(AtomContext expr) {
    return (Concrete.Expression) visit(expr);
  }

  private Concrete.Expression visitExpr(LiteralContext expr) {
    return (Concrete.Expression) visit(expr);
  }

  private Concrete.UniverseExpression visitExpr(UniverseAtomContext expr) {
    return (Concrete.UniverseExpression) visit(expr);
  }

  private void visitStatementList(List<StatementContext> statementCtxs, List<Group> subgroups, List<SimpleNamespaceCommand> namespaceCommands, ChildGroup parent, TCClassReferable enclosingClass) {
    for (StatementContext statementCtx : statementCtxs) {
      try {
        Object statement = visitStatement(statementCtx, parent, enclosingClass);
        if (statement instanceof Group) {
          subgroups.add((Group) statement);
        } else if (statement instanceof SimpleNamespaceCommand) {
          namespaceCommands.add((SimpleNamespaceCommand) statement);
        } else {
          if (statementCtx != null) {
            myErrorReporter.report(new ParserError(tokenPosition(statementCtx.start), "Unknown statement"));
          }
        }
      } catch (ParseException ignored) {

      }
    }
  }

  private Object visitStatement(StatementContext statementCtx, ChildGroup parent, TCClassReferable enclosingClass) {
    if (statementCtx instanceof StatCmdContext) {
      return visitStatCmd((StatCmdContext) statementCtx, parent);
    } else if (statementCtx instanceof StatDefContext) {
      return visitDefinition(((StatDefContext) statementCtx).definition(), parent, enclosingClass);
    } else {
      return null;
    }
  }

  @Override
  public FileGroup visitStatements(StatementsContext ctx) {
    List<Group> subgroups = new ArrayList<>();
    List<SimpleNamespaceCommand> namespaceCommands = new ArrayList<>();
    FileGroup parentGroup = new FileGroup(new ModuleReferable(myModule), subgroups, namespaceCommands);
    visitStatementList(ctx.statement(), subgroups, namespaceCommands, parentGroup, null);
    return parentGroup;
  }

  public ChildGroup visitDefinition(DefinitionContext ctx, ChildGroup parent, TCClassReferable enclosingClass) {
    if (ctx instanceof DefFunctionContext) {
      return visitDefFunction((DefFunctionContext) ctx, parent, enclosingClass);
    } else if (ctx instanceof DefDataContext) {
      return visitDefData((DefDataContext) ctx, parent, enclosingClass);
    } else if (ctx instanceof DefClassContext) {
      return visitDefClass((DefClassContext) ctx, parent, enclosingClass);
    } else if (ctx instanceof DefInstanceContext) {
      return visitDefInstance((DefInstanceContext) ctx, parent, enclosingClass);
    } else if (ctx instanceof DefModuleContext) {
      return visitDefModule((DefModuleContext) ctx, parent, enclosingClass);
    } else {
      if (ctx != null) {
        myErrorReporter.report(new ParserError(tokenPosition(ctx.start), "Unknown definition"));
      }
      throw new ParseException();
    }
  }

  private SimpleNamespaceCommand visitStatCmd(StatCmdContext ctx, ChildGroup parent) {
    NamespaceCommand.Kind kind = (NamespaceCommand.Kind) visit(ctx.nsCmd());
    List<String> path = visitAtomFieldsAccPath(ctx.atomFieldsAcc());
    if (path == null) {
      throw new ParseException();
    }

    List<SimpleNamespaceCommand.SimpleNameRenaming> openedReferences;
    NsUsingContext nsUsing = ctx.nsUsing();
    if (nsUsing == null) {
      openedReferences = Collections.emptyList();
    } else {
      openedReferences = new ArrayList<>();
      for (NsIdContext nsIdCtx : nsUsing.nsId()) {
        Position position = tokenPosition(nsIdCtx.ID(0).getSymbol());
        openedReferences.add(new SimpleNamespaceCommand.SimpleNameRenaming(position,
          new NamedUnresolvedReference(position, nsIdCtx.ID(0).getText()),
          nsIdCtx.precedence() == null ? null : visitPrecedence(nsIdCtx.precedence()),
          nsIdCtx.ID().size() < 2 ? null : nsIdCtx.ID(1).getText()));
      }
    }

    List<Referable> hiddenReferences = new ArrayList<>();
    for (TerminalNode id : ctx.ID()) {
      hiddenReferences.add(new NamedUnresolvedReference(tokenPosition(id.getSymbol()), id.getText()));
    }

    return new SimpleNamespaceCommand(tokenPosition(ctx.start), kind, path, nsUsing == null || nsUsing.USING() != null, openedReferences, hiddenReferences, parent);
  }

  @Override
  public NamespaceCommand.Kind visitOpenCmd(OpenCmdContext ctx) {
    return NamespaceCommand.Kind.OPEN;
  }

  @Override
  public NamespaceCommand.Kind visitImportCmd(ImportCmdContext ctx) {
    return NamespaceCommand.Kind.IMPORT;
  }

  private Precedence visitPrecedence(PrecedenceContext ctx) {
    return ctx == null ? Precedence.DEFAULT : (Precedence) visit(ctx);
  }

  @Override
  public Precedence visitNoPrecedence(NoPrecedenceContext ctx) {
    return Precedence.DEFAULT;
  }

  @Override
  public Precedence visitWithPrecedence(WithPrecedenceContext ctx) {
    int priority = Integer.parseInt(ctx.NUMBER().getText());
    if (priority < 1 || priority > 9) {
      myErrorReporter.report(new ParserError(tokenPosition(ctx.NUMBER().getSymbol()), "Precedence out of range: " + priority));

      if (priority < 1) {
        priority = 1;
      } else {
        priority = 9;
      }
    }

    PrecedenceWithoutPriority prec = (PrecedenceWithoutPriority) visit(ctx.associativity());
    return new Precedence(prec.associativity, (byte) priority, prec.isInfix);
  }

  private class PrecedenceWithoutPriority {
    private Precedence.Associativity associativity;
    private boolean isInfix;

    private PrecedenceWithoutPriority(Precedence.Associativity associativity, boolean isInfix) {
      this.associativity = associativity;
      this.isInfix = isInfix;
    }
  }

  @Override
  public PrecedenceWithoutPriority visitNonAssocInfix(NonAssocInfixContext ctx) {
    return new PrecedenceWithoutPriority(Precedence.Associativity.NON_ASSOC, true);
  }

  @Override
  public PrecedenceWithoutPriority visitLeftAssocInfix(LeftAssocInfixContext ctx) {
    return new PrecedenceWithoutPriority(Precedence.Associativity.LEFT_ASSOC, true);
  }

  @Override
  public PrecedenceWithoutPriority visitRightAssocInfix(RightAssocInfixContext ctx) {
    return new PrecedenceWithoutPriority(Precedence.Associativity.RIGHT_ASSOC, true);
  }

  @Override
  public PrecedenceWithoutPriority visitNonAssoc(NonAssocContext ctx) {
    return new PrecedenceWithoutPriority(Precedence.Associativity.NON_ASSOC, false);
  }

  @Override
  public PrecedenceWithoutPriority visitLeftAssoc(LeftAssocContext ctx) {
    return new PrecedenceWithoutPriority(Precedence.Associativity.LEFT_ASSOC, false);
  }

  @Override
  public PrecedenceWithoutPriority visitRightAssoc(RightAssocContext ctx) {
    return new PrecedenceWithoutPriority(Precedence.Associativity.RIGHT_ASSOC, false);
  }

  private Concrete.Pattern visitPattern(PatternContext ctx) {
    return (Concrete.Pattern) visit(ctx);
  }

  @Override
  public Concrete.Pattern visitPatternAtom(PatternAtomContext ctx) {
    return (Concrete.Pattern) visit(ctx.atomPattern());
  }

  @Override
  public Concrete.Pattern visitPatternConstructor(PatternConstructorContext ctx) {
    List<AtomPatternOrIDContext> atomPatternOrIDs = ctx.atomPatternOrID();
    TerminalNode id = ctx.ID();
    if (atomPatternOrIDs.isEmpty()) {
      return new Concrete.NamePattern(tokenPosition(ctx.start), new ParsedLocalReferable(tokenPosition(id.getSymbol()), id.getText()));
    } else {
      List<Concrete.Pattern> patterns = new ArrayList<>(atomPatternOrIDs.size());
      for (AtomPatternOrIDContext atomCtx : atomPatternOrIDs) {
        patterns.add(visitAtomPattern(atomCtx));
      }
      return new Concrete.ConstructorPattern(tokenPosition(ctx.start), new NamedUnresolvedReference(tokenPosition(id.getSymbol()), id.getText()), patterns);
    }
  }

  private Concrete.Pattern visitAtomPattern(AtomPatternOrIDContext ctx) {
    return (Concrete.Pattern) visit(ctx);
  }

  @Override
  public Concrete.Pattern visitPatternExplicit(PatternExplicitContext ctx) {
    List<PatternContext> patternCtxs = ctx.pattern();
    if (patternCtxs.isEmpty()) {
      return new Concrete.TuplePattern(tokenPosition(ctx.start), Collections.emptyList());
    }
    if (patternCtxs.size() == 1) {
      return visitPattern(patternCtxs.get(0));
    }

    List<Concrete.Pattern> patterns = new ArrayList<>(patternCtxs.size());
    for (PatternContext patternCtx : patternCtxs) {
      patterns.add(visitPattern(patternCtx));
    }
    return new Concrete.TuplePattern(tokenPosition(ctx.start), patterns);
  }

  @Override
  public Concrete.Pattern visitPatternImplicit(PatternImplicitContext ctx) {
    Concrete.Pattern pattern = visitPattern(ctx.pattern());
    if (pattern != null) {
      pattern.setExplicit(false);
    }
    return pattern;
  }

  @Override
  public Concrete.Pattern visitPatternOrIDAtom(PatternOrIDAtomContext ctx) {
    return (Concrete.Pattern) visit(ctx.atomPattern());
  }

  @Override
  public Concrete.Pattern visitPatternID(PatternIDContext ctx) {
    Position position = tokenPosition(ctx.start);
    return new Concrete.NamePattern(position, new ParsedLocalReferable(position, ctx.ID().getText()));
  }

  @Override
  public Concrete.Pattern visitPatternNumber(PatternNumberContext ctx) {
    String text = ctx.NUMBER().getText();
    int value;
    if (text.length() >= 10) {
      value = Concrete.NumberPattern.MAX_VALUE;
    } else {
      value = Integer.parseInt(ctx.NUMBER().getText(), 10);
    }

    return new Concrete.NumberPattern(tokenPosition(ctx.start), value);
  }

  @Override
  public Concrete.Pattern visitPatternNegativeNumber(PatternNegativeNumberContext ctx) {
    String text = ctx.NEGATIVE_NUMBER().getText();
    int value;
    if (text.length() >= 9) {
      value = -Concrete.NumberPattern.MAX_VALUE;
    } else {
      value = Integer.parseInt(ctx.NEGATIVE_NUMBER().getText(), 10);
    }

    return new Concrete.NumberPattern(tokenPosition(ctx.start), value);
  }

  @Override
  public Concrete.Pattern visitPatternAny(PatternAnyContext ctx) {
    return new Concrete.NamePattern(tokenPosition(ctx.start), null);
  }

  private ConcreteLocatedReferable makeReferable(Position position, String name, Precedence precedence, ChildGroup parent) {
    return parent instanceof FileGroup
      ? new ConcreteLocatedReferable(position, name, precedence, myModule)
      : new ConcreteLocatedReferable(position, name, precedence, (TCReferable) parent.getReferable(), LocatedReferableImpl.Kind.TYPECHECKABLE);
  }

  private StaticGroup visitDefInstance(DefInstanceContext ctx, ChildGroup parent, TCClassReferable enclosingClass) {
    List<Concrete.Parameter> parameters = visitFunctionParameters(ctx.tele());
    ConcreteLocatedReferable reference = makeReferable(tokenPosition(ctx.start), ctx.ID().getText(), Precedence.DEFAULT, parent);
    Concrete.Instance instance = new Concrete.Instance(reference, parameters, visitExpr(ctx.expr()), visitCoClauses(ctx.coClauses()));
    instance.enclosingClass = enclosingClass;
    reference.setDefinition(instance);
    List<Group> subgroups = new ArrayList<>();
    List<SimpleNamespaceCommand> namespaceCommands = new ArrayList<>();
    StaticGroup resultGroup = new StaticGroup(reference, subgroups, namespaceCommands, parent);
    visitWhere(ctx.where(), subgroups, namespaceCommands, resultGroup, enclosingClass);
    return resultGroup;
  }

  private List<Concrete.ClassFieldImpl> visitCoClauses(CoClausesContext ctx) {
    if (ctx instanceof CoClausesWithBracesContext) {
      return visitCoClausesWithBraces((CoClausesWithBracesContext) ctx);
    }
    if (ctx instanceof CoClausesWithoutBracesContext) {
      return visitCoClausesWithoutBraces((CoClausesWithoutBracesContext) ctx);
    }
    throw new IllegalStateException();
  }

  private List<Concrete.ClassFieldImpl> visitCoClauses(List<CoClauseContext> coClausesCtx) {
    List<Concrete.ClassFieldImpl> coClauses = new ArrayList<>(coClausesCtx.size());
    for (CoClauseContext coClause : coClausesCtx) {
      Concrete.ClassFieldImpl impl = visitCoClause(coClause);
      if (impl != null) {
        coClauses.add(impl);
      }
    }
    return coClauses;
  }

  @Override
  public List<Concrete.ClassFieldImpl> visitCoClausesWithoutBraces(CoClausesWithoutBracesContext ctx) {
    return visitCoClauses(ctx.coClause());
  }

  @Override
  public List<Concrete.ClassFieldImpl> visitCoClausesWithBraces(CoClausesWithBracesContext ctx) {
    return visitCoClauses(ctx.coClause());
  }

  private void visitWhere(WhereContext ctx, List<Group> subgroups, List<SimpleNamespaceCommand> namespaceCommands, ChildGroup parent, TCClassReferable enclosingClass) {
    if (ctx != null) {
      visitStatementList(ctx.statement(), subgroups, namespaceCommands, parent, enclosingClass);
    }
  }

  @Override
  public List<Concrete.ReferenceExpression> visitElim(ElimContext ctx) {
    if (ctx == null) {
      return Collections.emptyList();
    }
    List<AtomFieldsAccContext> atomFieldsAccs = ctx.atomFieldsAcc();
    if (atomFieldsAccs != null && !atomFieldsAccs.isEmpty()) {
      List<Concrete.Expression> expressions = new ArrayList<>(atomFieldsAccs.size());
      for (AtomFieldsAccContext exprCtx : atomFieldsAccs) {
        expressions.add(visitAtomFieldsAcc(exprCtx));
      }
      return checkElimExpressions(expressions);
    } else {
      return Collections.emptyList();
    }
  }

  private StaticGroup visitDefFunction(DefFunctionContext ctx, ChildGroup parent, TCClassReferable enclosingClass) {
    ExprContext exprCtx = ctx.expr();
    Concrete.Expression resultType = exprCtx != null ? visitExpr(exprCtx) : null;
    Concrete.FunctionBody body;
    FunctionBodyContext functionBodyCtx = ctx.functionBody();
    if (functionBodyCtx instanceof WithElimContext) {
      WithElimContext elimCtx = ((WithElimContext) functionBodyCtx);
      body = new Concrete.ElimFunctionBody(tokenPosition(elimCtx.start), visitElim(elimCtx.elim()), visitClauses(elimCtx.clauses()));
    } else {
      body = new Concrete.TermFunctionBody(tokenPosition(ctx.start), visitExpr(((WithoutElimContext) functionBodyCtx).expr()));
    }

    List<Group> subgroups = new ArrayList<>();
    List<SimpleNamespaceCommand> namespaceCommands = new ArrayList<>();
    ConcreteLocatedReferable referable = makeReferable(tokenPosition(ctx.start), ctx.ID().getText(), visitPrecedence(ctx.precedence()), parent);
    boolean isCoerce = ctx.funcKw() instanceof FuncKwCoerceContext;
    Concrete.FunctionDefinition funDef = isCoerce
      ? Concrete.CoerceDefinition.make(referable, visitFunctionParameters(ctx.tele()), resultType, body, parent.getReferable())
      : new Concrete.FunctionDefinition(referable, visitFunctionParameters(ctx.tele()), resultType, body);
    if (isCoerce && !funDef.isCoerce()) {
      myErrorReporter.report(new ParserError(tokenPosition(ctx.funcKw().start), "\\coerce is not allowed on the top level"));
    }

    funDef.enclosingClass = enclosingClass;
    referable.setDefinition(funDef);
    StaticGroup resultGroup = new StaticGroup(referable, subgroups, namespaceCommands, parent);
    visitWhere(ctx.where(), subgroups, namespaceCommands, resultGroup, enclosingClass);
    return resultGroup;
  }

  private List<Concrete.Parameter> visitFunctionParameters(List<TeleContext> teleCtx) {
    List<Concrete.Parameter> arguments = new ArrayList<>();
    for (TeleContext tele : teleCtx) {
      List<Concrete.Parameter> args = visitLamTele(tele);
      if (args != null) {
        if (args.get(0) instanceof Concrete.TelescopeParameter) {
          arguments.add(args.get(0));
        } else {
          myErrorReporter.report(new ParserError(tokenPosition(tele.getStart()), "Expected a typed variable"));
        }
      }
    }
    return arguments;
  }

  private StaticGroup visitDefData(DefDataContext ctx, ChildGroup parent, TCClassReferable enclosingClass) {
    final Concrete.UniverseExpression universe;
    ExprContext exprCtx = ctx.expr();
    if (exprCtx != null) {
      Concrete.Expression expr = visitExpr(exprCtx);
      if (expr instanceof Concrete.UniverseExpression) {
        universe = (Concrete.UniverseExpression) expr;
      } else {
        myErrorReporter.report(new ParserError(tokenPosition(exprCtx.start), "Specified type of the data definition is not a universe"));
        universe = null;
      }
    } else {
      universe = null;
    }

    List<InternalConcreteLocatedReferable> constructors = new ArrayList<>();
    DataBodyContext dataBodyCtx = ctx.dataBody();
    List<Concrete.ReferenceExpression> eliminatedReferences = dataBodyCtx instanceof DataClausesContext ? visitElim(((DataClausesContext) dataBodyCtx).elim()) : null;
    ConcreteLocatedReferable referable = makeReferable(tokenPosition(ctx.start), ctx.ID().getText(), visitPrecedence(ctx.precedence()), parent);
    Concrete.DataDefinition dataDefinition = new Concrete.DataDefinition(referable, visitTeles(ctx.tele()), eliminatedReferences, ctx.TRUNCATED() != null, universe, new ArrayList<>());
    dataDefinition.enclosingClass = enclosingClass;
    referable.setDefinition(dataDefinition);
    visitDataBody(dataBodyCtx, dataDefinition, constructors);

    List<Group> subgroups = new ArrayList<>();
    List<SimpleNamespaceCommand> namespaceCommands = new ArrayList<>();
    DataGroup resultGroup = new DataGroup(referable, constructors, subgroups, namespaceCommands, parent);
    visitWhere(ctx.where(), subgroups, namespaceCommands, resultGroup, enclosingClass);

    List<TCReferable> coercingFunctions = collectCoercingFunctions(subgroups, null);
    if (coercingFunctions != null) {
      dataDefinition.setCoercingFunctions(coercingFunctions);
    }

    return resultGroup;
  }

  private List<TCReferable> collectCoercingFunctions(List<Group> groups, List<TCReferable> coercingFunctions) {
    for (Group subgroup : groups) {
      if (subgroup.getReferable() instanceof ConcreteLocatedReferable) {
        Concrete.ReferableDefinition def = ((ConcreteLocatedReferable) subgroup.getReferable()).getDefinition();
        if (def instanceof Concrete.FunctionDefinition && ((Concrete.FunctionDefinition) def).isCoerce()) {
          if (coercingFunctions == null) {
            coercingFunctions = new ArrayList<>();
          }
          coercingFunctions.add((TCReferable) subgroup.getReferable());
        }
      }
    }
    return coercingFunctions;
  }

  private void visitDataBody(DataBodyContext ctx, Concrete.DataDefinition def, List<InternalConcreteLocatedReferable> constructors) {
    if (ctx instanceof DataClausesContext) {
      for (ConstructorClauseContext clauseCtx : ((DataClausesContext) ctx).constructorClause()) {
        try {
          List<Concrete.Pattern> patterns = new ArrayList<>(clauseCtx.pattern().size());
          for (PatternContext patternCtx : clauseCtx.pattern()) {
            patterns.add(visitPattern(patternCtx));
          }
          def.getConstructorClauses().add(new Concrete.ConstructorClause(tokenPosition(clauseCtx.start), patterns, visitConstructors(clauseCtx.constructor(), def, constructors)));
        } catch (ParseException ignored) {

        }
      }
    } else if (ctx instanceof DataConstructorsContext) {
      def.getConstructorClauses().add(new Concrete.ConstructorClause(tokenPosition(ctx.start), null, visitConstructors(((DataConstructorsContext) ctx).constructor(), def, constructors)));
    }
  }

  private List<Concrete.Constructor> visitConstructors(List<ConstructorContext> conContexts, Concrete.DataDefinition def, List<InternalConcreteLocatedReferable> constructors) {
    List<Concrete.Constructor> result = new ArrayList<>(conContexts.size());
    for (ConstructorContext conCtx : conContexts) {
      try {
        List<Concrete.FunctionClause> clauses;
        List<ClauseContext> clauseCtxs = conCtx.clause();
        ElimContext elimCtx = conCtx.elim();
        if (elimCtx != null || !clauseCtxs.isEmpty()) {
          clauses = new ArrayList<>(clauseCtxs.size());
          for (ClauseContext clauseCtx : clauseCtxs) {
            clauses.add(visitClause(clauseCtx));
          }
        } else {
          clauses = Collections.emptyList();
        }

        InternalConcreteLocatedReferable reference = new InternalConcreteLocatedReferable(tokenPosition(conCtx.start), conCtx.ID().getText(), visitPrecedence(conCtx.precedence()), true, def.getData(), LocatedReferableImpl.Kind.CONSTRUCTOR);
        Concrete.Constructor constructor = new Concrete.Constructor(reference, def, visitTeles(conCtx.tele()), visitElim(elimCtx), clauses);
        reference.setDefinition(constructor);
        constructors.add(reference);
        result.add(constructor);
      } catch (ParseException ignored) {

      }
    }
    return result;
  }

  private void visitInstanceStatements(List<ClassStatContext> ctx, List<Concrete.ClassField> fields, List<Concrete.ClassFieldImpl> implementations, List<Group> subgroups, Concrete.ClassDefinition parentClass, ChildGroup parent) {
    for (ClassStatContext statementCtx : ctx) {
      if (statementCtx == null) {
        continue;
      }

      try {
        if (statementCtx instanceof ClassFieldContext) {
          ClassFieldContext fieldCtx = (ClassFieldContext) statementCtx;
          List<TeleContext> teleCtxs = fieldCtx.tele();
          List<Concrete.TypeParameter> parameters = visitTeles(teleCtxs);
          Concrete.Expression type = visitExpr(fieldCtx.expr());
          if (!parameters.isEmpty()) {
            type = new Concrete.PiExpression(tokenPosition(teleCtxs.get(0).start), parameters, type);
          }

          ConcreteClassFieldReferable reference = new ConcreteClassFieldReferable(tokenPosition(fieldCtx.start), fieldCtx.ID().getText(), visitPrecedence(fieldCtx.precedence()), true, true, parentClass.getData(), LocatedReferableImpl.Kind.FIELD);
          Concrete.ClassField field = new Concrete.ClassField(reference, parentClass, true, type);
          reference.setDefinition(field);
          fields.add(field);
        } else if (statementCtx instanceof ClassImplementContext) {
          Concrete.ClassFieldImpl impl = visitClassImplement((ClassImplementContext) statementCtx);
          if (impl != null) {
            implementations.add(impl);
          }
        } else if (statementCtx instanceof ClassDefinitionContext) {
          subgroups.add(visitDefinition(((ClassDefinitionContext) statementCtx).definition(), parent, parentClass.getData()));
        } else {
          myErrorReporter.report(new ParserError(tokenPosition(statementCtx.start), "Unknown class statement"));
        }
      } catch (ParseException ignored) {

      }
    }
  }

  private StaticGroup visitDefModule(DefModuleContext ctx, ChildGroup parent, TCClassReferable enclosingClass) {
    WhereContext where = ctx.where();
    List<Group> staticSubgroups = where == null ? Collections.emptyList() : new ArrayList<>();
    List<SimpleNamespaceCommand> namespaceCommands = where == null ? Collections.emptyList() : new ArrayList<>();

    Position position = tokenPosition(ctx.start);
    String name = ctx.ID().getText();
    ConcreteLocatedReferable reference = parent instanceof FileGroup
        ? new ConcreteLocatedReferable(position, name, Precedence.DEFAULT, myModule)
        : new ConcreteLocatedReferable(position, name, Precedence.DEFAULT, (TCReferable) parent.getReferable(), GlobalReferable.Kind.OTHER);

    StaticGroup resultGroup = new StaticGroup(reference, staticSubgroups, namespaceCommands, parent);
    visitWhere(where, staticSubgroups, namespaceCommands, resultGroup, enclosingClass);
    return resultGroup;
  }

  private ClassGroup visitDefClass(DefClassContext ctx, ChildGroup parent, TCClassReferable enclosingClass) {
    WhereContext where = ctx.where();

    List<Concrete.ClassFieldImpl> implementations = Collections.emptyList();
    List<Group> staticSubgroups = where == null ? Collections.emptyList() : new ArrayList<>();
    List<SimpleNamespaceCommand> namespaceCommands = where == null ? Collections.emptyList() : new ArrayList<>();

    List<Concrete.ReferenceExpression> superClasses = new ArrayList<>();
    for (ClassCallContext classCallCtx : ctx.classCall()) {
      UnresolvedReference superClassRef = visitAtomFieldsAccRef(classCallCtx.atomFieldsAcc());
      if (superClassRef != null) {
        superClasses.add(new Concrete.ReferenceExpression(superClassRef.getData(), superClassRef));
      }
    }

    List<? extends InternalConcreteLocatedReferable> fieldReferables;
    Position pos = tokenPosition(ctx.start);
    String name = ctx.ID().getText();
    Precedence prec = visitPrecedence(ctx.precedence());
    ConcreteClassReferable reference;
    ClassGroup resultGroup = null;
    boolean isRecord = ctx.classKw() instanceof ClassKwRecordContext;
    ClassBodyContext classBodyCtx = ctx.classBody();
    Concrete.ClassDefinition classDefinition = null;
    List<TCReferable> coercingFunctions = null;
    if (classBodyCtx instanceof ClassSynContext) {
      if (isRecord) {
        myErrorReporter.report(new ParserError(tokenPosition(ctx.start), "Records cannot be synonyms"));
      }

      UnresolvedReference classRef = visitAtomFieldsAccRef(((ClassSynContext) classBodyCtx).atomFieldsAcc());
      if (classRef == null) {
        throw new ParseException();
      }
      List<ConcreteClassFieldSynonymReferable> fieldSynonymReferables = new ArrayList<>();
      Concrete.ReferenceExpression refExpr = new Concrete.ReferenceExpression(classRef.getData(), classRef);
      reference = parent instanceof FileGroup
        ? new ConcreteClassSynonymReferable(pos, name, prec, fieldSynonymReferables, superClasses, parent, myModule, refExpr)
        : new ConcreteClassSynonymReferable(pos, name, prec, fieldSynonymReferables, superClasses, parent, (TCReferable) parent.getReferable(), refExpr);
      fieldReferables = fieldSynonymReferables;

      List<FieldTeleContext> fieldTeleCtxs = ctx.fieldTele();
      if (!fieldTeleCtxs.isEmpty()) {
        myErrorReporter.report(new ParserError(tokenPosition(fieldTeleCtxs.get(0).start), "Class synonyms cannot have parameters"));
      }

      for (FieldSynContext fieldSyn : ((ClassSynContext) classBodyCtx).fieldSyn()) {
        Position position = tokenPosition(fieldSyn.start);
        fieldSynonymReferables.add(new ConcreteClassFieldSynonymReferable(tokenPosition(fieldSyn.start), fieldSyn.ID(1).getText(), visitPrecedence(fieldSyn.precedence()), true, reference, new Concrete.ReferenceExpression(position, new NamedUnresolvedReference(position, fieldSyn.ID(0).getText()))));
      }
    } else {
      List<Concrete.ClassField> fields = new ArrayList<>();
      List<ClassStatContext> classStatCtxs = classBodyCtx == null ? Collections.emptyList() : ((ClassImplContext) classBodyCtx).classStat();
      if (!classStatCtxs.isEmpty()) {
        implementations = new ArrayList<>();
      }
      List<Boolean> fieldsExplicitness = new ArrayList<>();

      List<ConcreteClassFieldReferable> fieldReferables1 = new ArrayList<>();
      reference = parent instanceof FileGroup
        ? new ConcreteClassReferable(pos, name, prec, fieldReferables1, superClasses, parent, myModule)
        : new ConcreteClassReferable(pos, name, prec, fieldReferables1, superClasses, parent, (TCReferable) parent.getReferable());

      classDefinition = new Concrete.ClassDefinition(reference, isRecord, new ArrayList<>(superClasses), fields, fieldsExplicitness, implementations);
      reference.setDefinition(classDefinition);
      classDefinition.setCoercingField(visitFieldTeles(ctx.fieldTele(), classDefinition, fields, fieldsExplicitness));
      classDefinition.enclosingClass = enclosingClass;

      if (!classStatCtxs.isEmpty()) {
        List<Group> dynamicSubgroups = new ArrayList<>();
        resultGroup = new ClassGroup(reference, fieldReferables1, dynamicSubgroups, staticSubgroups, namespaceCommands, parent);
        visitInstanceStatements(classStatCtxs, fields, implementations, dynamicSubgroups, classDefinition, resultGroup);
        coercingFunctions = collectCoercingFunctions(dynamicSubgroups, null);
      }

      for (Concrete.ClassField field : fields) {
        fieldReferables1.add((ConcreteClassFieldReferable) field.getData());
      }
      fieldReferables = fieldReferables1;
    }

    if (resultGroup == null) {
      resultGroup = new ClassGroup(reference, fieldReferables, Collections.emptyList(), staticSubgroups, namespaceCommands, parent);
    }
    visitWhere(where, staticSubgroups, namespaceCommands, resultGroup, enclosingClass);

    if (classDefinition != null) {
      coercingFunctions = collectCoercingFunctions(staticSubgroups, coercingFunctions);
      if (coercingFunctions != null) {
        classDefinition.setCoercingFunctions(coercingFunctions);
      }
    }

    return resultGroup;
  }

  @Override
  public Concrete.ClassFieldImpl visitClassImplement(ClassImplementContext ctx) {
    return visitCoClause(ctx.coClause());
  }

  @Override
  public Concrete.ReferenceExpression visitName(NameContext ctx) {
    Position position = tokenPosition(ctx.start);
    return new Concrete.ReferenceExpression(position, new NamedUnresolvedReference(position, ctx.ID().getText()));
  }

  @Override
  public Concrete.HoleExpression visitUnknown(UnknownContext ctx) {
    return new Concrete.HoleExpression(tokenPosition(ctx.start));
  }

  @Override
  public Concrete.GoalExpression visitGoal(GoalContext ctx) {
    TerminalNode id = ctx.ID();
    ExprContext exprCtx = ctx.expr();
    return new Concrete.GoalExpression(tokenPosition(ctx.start), id == null ? null : id.getText(), exprCtx == null ? null : visitExpr(exprCtx));
  }

  @Override
  public Concrete.PiExpression visitArr(ArrContext ctx) {
    Concrete.Expression domain = visitExpr(ctx.expr(0));
    Concrete.Expression codomain = visitExpr(ctx.expr(1));
    List<Concrete.TypeParameter> arguments = new ArrayList<>(1);
    arguments.add(new Concrete.TypeParameter(domain.getData(), true, domain));
    return new Concrete.PiExpression(tokenPosition(ctx.getToken(ARROW, 0).getSymbol()), arguments, codomain);
  }

  @Override
  public Concrete.Expression visitTuple(TupleContext ctx) {
    List<ExprContext> exprs = ctx.expr();
    if (exprs.size() == 1) {
      return visitExpr(exprs.get(0));
    } else {
      List<Concrete.Expression> fields = new ArrayList<>(exprs.size());
      for (ExprContext exprCtx : exprs) {
        fields.add(visitExpr(exprCtx));
      }
      return new Concrete.TupleExpression(tokenPosition(ctx.start), fields);
    }
  }

  private List<Concrete.Parameter> visitLamTele(TeleContext tele) {
    List<Concrete.Parameter> parameters = new ArrayList<>();
    if (tele instanceof ExplicitContext || tele instanceof ImplicitContext) {
      boolean explicit = tele instanceof ExplicitContext;
      TypedExprContext typedExpr = explicit ? ((ExplicitContext) tele).typedExpr() : ((ImplicitContext) tele).typedExpr();
      Concrete.Expression typeExpr = null;
      List<ParsedLocalReferable> vars = new ArrayList<>();
      if (typedExpr instanceof TypedContext) {
        getVarList(((TypedContext) typedExpr).expr(0), vars);
        typeExpr = visitExpr(((TypedContext) typedExpr).expr(1));
      } else if (typedExpr instanceof NotTypedContext) {
        getVarList(((NotTypedContext) typedExpr).expr(), vars);
        for (ParsedLocalReferable var : vars) {
          parameters.add(new Concrete.NameParameter(var.getPosition(), explicit, var));
        }
      } else {
        throw new IllegalStateException();
      }
      if (typeExpr != null) {
        parameters.add(new Concrete.TelescopeParameter(tokenPosition(tele.getStart()), explicit, vars, typeExpr));
      }
    } else {
      boolean ok = tele instanceof TeleLiteralContext;
      if (ok) {
        LiteralContext literalContext = ((TeleLiteralContext) tele).literal();
        if (literalContext instanceof NameContext && ((NameContext) literalContext).ID() != null) {
          TerminalNode id = ((NameContext) literalContext).ID();
          parameters.add(new Concrete.NameParameter(tokenPosition(id.getSymbol()), true, new ParsedLocalReferable(tokenPosition(id.getSymbol()), id.getText())));
        } else if (literalContext instanceof UnknownContext) {
          parameters.add(new Concrete.NameParameter(tokenPosition(literalContext.getStart()), true, null));
        } else {
          ok = false;
        }
      }
      if (!ok) {
        myErrorReporter.report(new ParserError(tokenPosition(tele.start), "Unexpected token, expected an identifier"));
        throw new ParseException();
      }
    }
    return parameters;
  }

  private List<Concrete.Parameter> visitLamTeles(List<TeleContext> tele) {
    List<Concrete.Parameter> arguments = new ArrayList<>();
    for (TeleContext arg : tele) {
      arguments.addAll(visitLamTele(arg));
    }
    return arguments;
  }

  @Override
  public Concrete.LamExpression visitLam(LamContext ctx) {
    return new Concrete.LamExpression(tokenPosition(ctx.start), visitLamTeles(ctx.tele()), visitExpr(ctx.expr()));
  }

  private Concrete.Expression visitAppExpr(AppExprContext ctx) {
    return (Concrete.Expression) visit(ctx);
  }

  @Override
  public Concrete.Expression visitAppArgument(AppArgumentContext ctx) {
    Concrete.Expression expr = visitAtomFieldsAcc(ctx.atomFieldsAcc());
    List<OnlyLevelAtomContext> onlyLevelAtoms = ctx.onlyLevelAtom();
    if (!onlyLevelAtoms.isEmpty()) {
      if (expr instanceof Concrete.ReferenceExpression) {
        Object obj1 = visit(onlyLevelAtoms.get(0));
        Object obj2 = onlyLevelAtoms.size() < 2 ? null : visit(onlyLevelAtoms.get(1));
        if (onlyLevelAtoms.size() > 2 || obj1 instanceof Pair && obj2 != null || obj2 instanceof Pair) {
          myErrorReporter.report(new ParserError(tokenPosition(onlyLevelAtoms.get(0).start), "too many level specifications"));
        }

        Concrete.LevelExpression level1;
        Concrete.LevelExpression level2;
        if (obj1 instanceof Pair) {
          level1 = (Concrete.LevelExpression) ((Pair) obj1).proj1;
          level2 = (Concrete.LevelExpression) ((Pair) obj1).proj2;
        } else {
          level1 = (Concrete.LevelExpression) obj1;
          level2 = obj2 instanceof Concrete.LevelExpression ? (Concrete.LevelExpression) obj2 : null;
        }

        expr = new Concrete.ReferenceExpression(expr.getData(), ((Concrete.ReferenceExpression) expr).getReferent(), level1, level2);
      } else {
        myErrorReporter.report(new ParserError(tokenPosition(onlyLevelAtoms.get(0).start), "Level annotations are allowed only after a reference"));
      }
    }

    List<ArgumentContext> argumentCtxs = ctx.argument();
    if (argumentCtxs.isEmpty()) {
      return expr;
    }

    List<Concrete.BinOpSequenceElem> sequence = new ArrayList<>(argumentCtxs.size());
    sequence.add(new Concrete.BinOpSequenceElem(expr, Fixity.NONFIX, true));
    for (ArgumentContext argumentCtx : argumentCtxs) {
      sequence.add(visitArgument(argumentCtx));
    }

    return new Concrete.BinOpSequenceExpression(expr.getData(), sequence);
  }

  private Concrete.BinOpSequenceElem visitArgument(ArgumentContext ctx) {
    return (Concrete.BinOpSequenceElem) visit(ctx);
  }

  @Override
  public Concrete.BinOpSequenceElem visitArgumentExplicit(ArgumentExplicitContext ctx) {
    AtomFieldsAccContext atomFieldsAcc = ctx.atomFieldsAcc();
    return new Concrete.BinOpSequenceElem(visitAtomFieldsAcc(atomFieldsAcc), atomFieldsAcc.atom() instanceof AtomLiteralContext && ((AtomLiteralContext) atomFieldsAcc.atom()).literal() instanceof NameContext ? Fixity.UNKNOWN : Fixity.NONFIX, true);
  }

  @Override
  public Concrete.BinOpSequenceElem visitArgumentNew(ArgumentNewContext ctx) {
    return new Concrete.BinOpSequenceElem(visitNew(ctx.NEW(), ctx.appExpr(), ctx.implementStatements()), Fixity.NONFIX, true);
  }

  @Override
  public Concrete.BinOpSequenceElem visitArgumentUniverse(ArgumentUniverseContext ctx) {
    return new Concrete.BinOpSequenceElem(visitExpr(ctx.universeAtom()), Fixity.NONFIX, true);
  }

  @Override
  public Concrete.BinOpSequenceElem visitArgumentImplicit(ArgumentImplicitContext ctx) {
    return new Concrete.BinOpSequenceElem(visitExpr(ctx.expr()), Fixity.NONFIX, false);
  }

  @Override
  public Concrete.BinOpSequenceElem visitArgumentInfix(ArgumentInfixContext ctx) {
    Position position = tokenPosition(ctx.start);
    return new Concrete.BinOpSequenceElem(new Concrete.ReferenceExpression(position, new NamedUnresolvedReference(position, getInfixText(ctx.INFIX()))), Fixity.INFIX, true);
  }

  @Override
  public Concrete.BinOpSequenceElem visitArgumentPostfix(ArgumentPostfixContext ctx) {
    Position position = tokenPosition(ctx.start);
    return new Concrete.BinOpSequenceElem(new Concrete.ReferenceExpression(position, new NamedUnresolvedReference(position, getPostfixText(ctx.POSTFIX()))), Fixity.POSTFIX, true);
  }

  @Override
  public Concrete.ClassFieldImpl visitCoClause(CoClauseContext ctx) {
    List<String> path = visitAtomFieldsAccPath(ctx.atomFieldsAcc());
    if (path == null) {
      return null;
    }

    Position position = tokenPosition(ctx.start);
    List<TeleContext> teleCtxs = ctx.tele();
    List<Concrete.Parameter> parameters = visitLamTeles(teleCtxs);
    Concrete.Expression term = null;
    List<Concrete.ClassFieldImpl> subClassFieldImpls = Collections.emptyList();
    ExprContext exprCtx = ctx.expr();
    if (exprCtx != null) {
      term = visitExpr(exprCtx);
      if (!parameters.isEmpty()) {
        term = new Concrete.LamExpression(tokenPosition(teleCtxs.get(0).start), parameters, term);
      }
    } else {
      if (!parameters.isEmpty()) {
        myErrorReporter.report(new ParserError(tokenPosition(teleCtxs.get(0).start), "Parameters are allowed only before '=> <expression>'"));
      }
      subClassFieldImpls = visitCoClauses(ctx.coClause());
    }

    return new Concrete.ClassFieldImpl(position, LongUnresolvedReference.make(position, path), term, subClassFieldImpls);
  }

  private Concrete.LevelExpression parseTruncatedUniverse(TerminalNode terminal) {
    String universe = terminal.getText();
    if (universe.charAt(1) == 'o') {
      return new Concrete.InfLevelExpression(tokenPosition(terminal.getSymbol()));
    }

    return new Concrete.NumberLevelExpression(tokenPosition(terminal.getSymbol()), Integer.parseInt(universe.substring(1, universe.indexOf('-'))));
  }

  @Override
  public Concrete.UniverseExpression visitUniverse(UniverseContext ctx) {
    Position position = tokenPosition(ctx.start);
    Concrete.LevelExpression lp;
    Concrete.LevelExpression lh;

    String text = ctx.UNIVERSE().getText().substring("\\Type".length());
    lp = text.isEmpty() ? null : new Concrete.NumberLevelExpression(position, Integer.parseInt(text));

    List<MaybeLevelAtomContext> maybeLevelAtomCtxs = ctx.maybeLevelAtom();
    if (!maybeLevelAtomCtxs.isEmpty()) {
      if (lp == null) {
        lp = visitLevel(maybeLevelAtomCtxs.get(0));
        lh = null;
      } else {
        lh = visitLevel(maybeLevelAtomCtxs.get(0));
      }

      if (maybeLevelAtomCtxs.size() >= 2) {
        if (lh == null) {
          lh = visitLevel(maybeLevelAtomCtxs.get(1));
        } else {
          myErrorReporter.report(new ParserError(tokenPosition(maybeLevelAtomCtxs.get(1).start), "h-level is already specified"));
        }
      }
    } else {
      lh = null;
    }

    return new Concrete.UniverseExpression(position, lp, lh);
  }

  @Override
  public Concrete.UniverseExpression visitTruncatedUniverse(TruncatedUniverseContext ctx) {
    Position position = tokenPosition(ctx.start);
    String text = ctx.TRUNCATED_UNIVERSE().getText();
    text = text.substring(text.indexOf('-') + "-Type".length());
    return new Concrete.UniverseExpression(position, getLevelExpression(position, text, ctx.maybeLevelAtom()), parseTruncatedUniverse(ctx.TRUNCATED_UNIVERSE()));
  }

  @Override
  public Concrete.UniverseExpression visitSetUniverse(SetUniverseContext ctx) {
    Position position = tokenPosition(ctx.start);
    return new Concrete.UniverseExpression(position, getLevelExpression(position, ctx.SET().getText().substring("\\Set".length()), ctx.maybeLevelAtom()), new Concrete.NumberLevelExpression(position, 0));
  }

  private Concrete.LevelExpression getLevelExpression(Position position, String text, MaybeLevelAtomContext maybeLevelAtomCtx) {
    if (text.isEmpty()) {
      return maybeLevelAtomCtx == null ? null : visitLevel(maybeLevelAtomCtx);
    }

    if (maybeLevelAtomCtx instanceof WithLevelAtomContext) {
      myErrorReporter.report(new ParserError(tokenPosition(maybeLevelAtomCtx.start), "p-level is already specified"));
    }
    return new Concrete.NumberLevelExpression(position, Integer.parseInt(text));
  }

  @Override
  public Concrete.UniverseExpression visitUniTruncatedUniverse(UniTruncatedUniverseContext ctx) {
    Position position = tokenPosition(ctx.start);
    String text = ctx.TRUNCATED_UNIVERSE().getText();
    text = text.substring(text.indexOf('-') + "-Type".length());
    Concrete.LevelExpression pLevel = text.isEmpty() ? null : new Concrete.NumberLevelExpression(position, Integer.parseInt(text));
    return new Concrete.UniverseExpression(position, pLevel, parseTruncatedUniverse(ctx.TRUNCATED_UNIVERSE()));
  }

  @Override
  public Concrete.UniverseExpression visitUniUniverse(UniUniverseContext ctx) {
    Position position = tokenPosition(ctx.start);
    String text = ctx.UNIVERSE().getText().substring("\\Type".length());
    Concrete.LevelExpression lp = text.isEmpty() ? null : new Concrete.NumberLevelExpression(position, Integer.parseInt(text));
    return new Concrete.UniverseExpression(position, lp, null);
  }

  @Override
  public Concrete.UniverseExpression visitUniSetUniverse(UniSetUniverseContext ctx) {
    Position position = tokenPosition(ctx.start);
    String text = ctx.SET().getText().substring("\\Set".length());
    Concrete.LevelExpression pLevel = text.isEmpty() ? null : new Concrete.NumberLevelExpression(position, Integer.parseInt(text));
    return new Concrete.UniverseExpression(position, pLevel, new Concrete.NumberLevelExpression(position, 0));
  }

  @Override
  public Concrete.UniverseExpression visitProp(PropContext ctx) {
    Position pos = tokenPosition(ctx.start);
    return new Concrete.UniverseExpression(pos, new Concrete.NumberLevelExpression(pos, 0), new Concrete.NumberLevelExpression(pos, -1));
  }

  private Concrete.LevelExpression visitLevel(LevelAtomContext ctx) {
    return (Concrete.LevelExpression) visit(ctx);
  }

  private Concrete.LevelExpression visitLevel(MaybeLevelAtomContext ctx) {
    return (Concrete.LevelExpression) visit(ctx);
  }

  @Override
  public Concrete.LevelExpression visitWithLevelAtom(WithLevelAtomContext ctx) {
    return visitLevel(ctx.levelAtom());
  }

  @Override
  public Concrete.LevelExpression visitWithoutLevelAtom(WithoutLevelAtomContext ctx) {
    return null;
  }

  @Override
  public Concrete.PLevelExpression visitPLevel(PLevelContext ctx) {
    return new Concrete.PLevelExpression(tokenPosition(ctx.start));
  }

  @Override
  public Concrete.HLevelExpression visitHLevel(HLevelContext ctx) {
    return new Concrete.HLevelExpression(tokenPosition(ctx.start));
  }

  @Override
  public Concrete.NumberLevelExpression visitNumLevel(NumLevelContext ctx) {
    return new Concrete.NumberLevelExpression(tokenPosition(ctx.start), Integer.parseInt(ctx.NUMBER().getText()));
  }

  @Override
  public Concrete.LevelExpression visitParenLevel(ParenLevelContext ctx) {
    return (Concrete.LevelExpression) visit(ctx.levelExpr());
  }

  @Override
  public Concrete.LevelExpression visitAtomLevel(AtomLevelContext ctx) {
    return visitLevel(ctx.levelAtom());
  }

  @Override
  public Concrete.SucLevelExpression visitSucLevel(SucLevelContext ctx) {
    return new Concrete.SucLevelExpression(tokenPosition(ctx.start), visitLevel(ctx.levelAtom()));
  }

  @Override
  public Concrete.MaxLevelExpression visitMaxLevel(MaxLevelContext ctx) {
    return new Concrete.MaxLevelExpression(tokenPosition(ctx.start), visitLevel(ctx.levelAtom(0)), visitLevel(ctx.levelAtom(1)));
  }

  @Override
  public Concrete.PLevelExpression visitPOnlyLevel(POnlyLevelContext ctx) {
    return new Concrete.PLevelExpression(tokenPosition(ctx.start));
  }

  @Override
  public Concrete.HLevelExpression visitHOnlyLevel(HOnlyLevelContext ctx) {
    return new Concrete.HLevelExpression(tokenPosition(ctx.start));
  }

  @Override
  public Object visitParenOnlyLevel(ParenOnlyLevelContext ctx) {
    return visit(ctx.onlyLevelExpr());
  }

  @Override
  public Object visitAtomOnlyLevel(AtomOnlyLevelContext ctx) {
    return visit(ctx.onlyLevelAtom());
  }

  @Override
  public Pair<Concrete.LevelExpression, Concrete.LevelExpression> visitLevelsOnlyLevel(LevelsOnlyLevelContext ctx) {
    Position position = tokenPosition(ctx.start);
    List<MaybeLevelAtomContext> maybeLevelAtomCtxs = ctx.maybeLevelAtom();
    return maybeLevelAtomCtxs.isEmpty() ? new Pair<>(new Concrete.NumberLevelExpression(position, 0), new Concrete.NumberLevelExpression(position, -1)) : new Pair<>(visitLevel(maybeLevelAtomCtxs.get(0)), visitLevel(maybeLevelAtomCtxs.get(1)));
  }

  @Override
  public Concrete.SucLevelExpression visitSucOnlyLevel(SucOnlyLevelContext ctx) {
    return new Concrete.SucLevelExpression(tokenPosition(ctx.start), visitLevel(ctx.levelAtom()));
  }

  @Override
  public Concrete.MaxLevelExpression visitMaxOnlyLevel(MaxOnlyLevelContext ctx) {
    return new Concrete.MaxLevelExpression(tokenPosition(ctx.start), visitLevel(ctx.levelAtom(0)), visitLevel(ctx.levelAtom(1)));
  }

  private List<Concrete.TypeParameter> visitTeles(List<TeleContext> teles) {
    List<Concrete.TypeParameter> parameters = new ArrayList<>(teles.size());
    for (TeleContext tele : teles) {
      boolean explicit = !(tele instanceof ImplicitContext);
      TypedExprContext typedExpr;
      if (explicit) {
        if (tele instanceof ExplicitContext) {
          typedExpr = ((ExplicitContext) tele).typedExpr();
        } else
        if (tele instanceof TeleLiteralContext) {
          parameters.add(new Concrete.TypeParameter(true, visitExpr(((TeleLiteralContext) tele).literal())));
          continue;
        } else
        if (tele instanceof TeleUniverseContext) {
          parameters.add(new Concrete.TypeParameter(true, visitExpr(((TeleUniverseContext) tele).universeAtom())));
          continue;
        } else {
          throw new IllegalStateException();
        }
      } else {
        typedExpr = ((ImplicitContext) tele).typedExpr();
      }
      if (typedExpr instanceof TypedContext) {
        List<ParsedLocalReferable> vars = new ArrayList<>();
        getVarList(((TypedContext) typedExpr).expr(0), vars);
        parameters.add(new Concrete.TelescopeParameter(tokenPosition(tele.getStart()), explicit, vars, visitExpr(((TypedContext) typedExpr).expr(1))));
      } else
      if (typedExpr instanceof NotTypedContext) {
        parameters.add(new Concrete.TypeParameter(explicit, visitExpr(((NotTypedContext) typedExpr).expr())));
      } else {
        throw new IllegalStateException();
      }
    }
    return parameters;
  }

  private TCFieldReferable visitFieldTeles(List<FieldTeleContext> teles, Concrete.ClassDefinition classDef, List<Concrete.ClassField> fields, List<Boolean> fieldsExplicitness) {
    TCFieldReferable coercingField = null;

    for (FieldTeleContext tele : teles) {
      boolean explicit;
      List<TerminalNode> vars;
      ExprContext exprCtx;
      if (tele instanceof ExplicitFieldTeleContext) {
        explicit = true;
        vars = ((ExplicitFieldTeleContext) tele).ID();
        exprCtx = ((ExplicitFieldTeleContext) tele).expr();
      } else if (tele instanceof ImplicitFieldTeleContext) {
        explicit = false;
        vars = ((ImplicitFieldTeleContext) tele).ID();
        exprCtx = ((ImplicitFieldTeleContext) tele).expr();
      } else {
        throw new IllegalStateException();
      }

      Concrete.Expression type = visitExpr(exprCtx);
      for (TerminalNode var : vars) {
        ConcreteClassFieldReferable fieldRef = new ConcreteClassFieldReferable(tokenPosition(var.getSymbol()), var.getText(), Precedence.DEFAULT, false, explicit, classDef.getData(), LocatedReferableImpl.Kind.FIELD);
        Concrete.ClassField field = new Concrete.ClassField(fieldRef, classDef, explicit, type);
        fieldRef.setDefinition(field);
        fields.add(field);

        if (coercingField == null && explicit) {
          coercingField = fieldRef;
        }

        fieldsExplicitness.add(explicit);
      }
    }

    return coercingField;
  }

  @Override
  public Concrete.Expression visitAtomLiteral(AtomLiteralContext ctx) {
    return visitExpr(ctx.literal());
  }

  @Override
  public Concrete.NumericLiteral visitAtomNumber(AtomNumberContext ctx) {
    return new Concrete.NumericLiteral(tokenPosition(ctx.start), new BigInteger(ctx.NUMBER().getText(), 10));
  }

  @Override
  public Concrete.NumericLiteral visitAtomNegativeNumber(AtomNegativeNumberContext ctx) {
    return new Concrete.NumericLiteral(tokenPosition(ctx.start), new BigInteger(ctx.NEGATIVE_NUMBER().getText(), 10));
  }

  @Override
  public Concrete.SigmaExpression visitSigma(SigmaContext ctx) {
    return new Concrete.SigmaExpression(tokenPosition(ctx.start), visitTeles(ctx.tele()));
  }

  @Override
  public Concrete.PiExpression visitPi(PiContext ctx) {
    return new Concrete.PiExpression(tokenPosition(ctx.start), visitTeles(ctx.tele()), visitExpr(ctx.expr()));
  }

  public Concrete.Expression visitApp(AppContext ctx) {
    Concrete.Expression expr = visitNew(ctx.NEW(), ctx.appExpr(), ctx.implementStatements());
    List<ArgumentContext> argumentCtxs = ctx.argument();
    if (!argumentCtxs.isEmpty()) {
      List<Concrete.BinOpSequenceElem> sequence = new ArrayList<>(argumentCtxs.size() + 1);
      sequence.add(new Concrete.BinOpSequenceElem(expr, Fixity.NONFIX, true));
      for (ArgumentContext argCtx : argumentCtxs) {
        sequence.add(visitArgument(argCtx));
      }
      expr = new Concrete.BinOpSequenceExpression(expr.getData(), sequence);
    }
    return expr;
  }

  private Concrete.Expression visitNew(TerminalNode newNode, AppExprContext appCtx, ImplementStatementsContext implCtx) {
    Concrete.Expression expr = visitAppExpr(appCtx);

    if (implCtx != null) {
      expr = new Concrete.ClassExtExpression(tokenPosition(appCtx.start), expr, visitCoClauses(implCtx.coClause()));
    }

    if (newNode != null) {
      expr = new Concrete.NewExpression(tokenPosition(newNode.getSymbol()), expr);
    }

    return expr;
  }

  private List<String> visitAtomFieldsAccPath(AtomFieldsAccContext ctx) {
    AtomContext atomCtx = ctx.atom();
    if (atomCtx instanceof AtomLiteralContext && ((AtomLiteralContext) atomCtx).literal() instanceof NameContext) {
      List<String> result = new ArrayList<>();
      result.add(((NameContext) ((AtomLiteralContext) atomCtx).literal()).ID().getText());
      boolean ok = true;
      for (FieldAccContext fieldAccCtx : ctx.fieldAcc()) {
        if (fieldAccCtx instanceof ClassFieldAccContext) {
          result.add(((ClassFieldAccContext) fieldAccCtx).ID().getText());
        } else {
          ok = false;
          break;
        }
      }
      if (ok) {
        return result;
      }
    }

    myErrorReporter.report(new ParserError(tokenPosition(ctx.start), "Expected a reference"));
    return null;
  }

  private UnresolvedReference visitAtomFieldsAccRef(AtomFieldsAccContext ctx) {
    List<String> path = visitAtomFieldsAccPath(ctx);
    return path == null ? null : LongUnresolvedReference.make(tokenPosition(ctx.start), path);
  }

  @Override
  public Concrete.Expression visitAtomFieldsAcc(AtomFieldsAccContext ctx) {
    List<FieldAccContext> fieldAccCtxs = ctx.fieldAcc();
    if (fieldAccCtxs.isEmpty()) {
      return visitExpr(ctx.atom());
    }

    AtomContext atomCtx = ctx.atom();
    Concrete.Expression expression = null;
    Token errorToken = null;
    int i = 0;
    if (fieldAccCtxs.get(0) instanceof ClassFieldAccContext) {
      if (atomCtx instanceof AtomLiteralContext && ((AtomLiteralContext) atomCtx).literal() instanceof NameContext) {
        String name = ((NameContext) ((AtomLiteralContext) atomCtx).literal()).ID().getText();
        List<String> path = new ArrayList<>();
        for (; i < fieldAccCtxs.size(); i++) {
          if (!(fieldAccCtxs.get(i) instanceof ClassFieldAccContext)) {
            break;
          }
          path.add(((ClassFieldAccContext) fieldAccCtxs.get(i)).ID().getText());
        }
        Position position = tokenPosition(ctx.start);
        expression = new Concrete.ReferenceExpression(position, new LongUnresolvedReference(position, name, path));
      } else {
        errorToken = ctx.start;
      }
    } else {
      expression = visitExpr(atomCtx);
    }

    if (errorToken == null) {
      for (; i < fieldAccCtxs.size(); i++) {
        FieldAccContext fieldAccCtx = fieldAccCtxs.get(i);
        if (fieldAccCtx instanceof ClassFieldAccContext) {
          errorToken = fieldAccCtx.start;
          break;
        } else if (fieldAccCtx instanceof SigmaFieldAccContext) {
          expression = new Concrete.ProjExpression(tokenPosition(fieldAccCtx.start), expression, Integer.parseInt(((SigmaFieldAccContext) fieldAccCtx).NUMBER().getText()) - 1);
        } else {
          throw new IllegalStateException();
        }
      }
    }

    if (errorToken != null) {
      myErrorReporter.report(new ParserError(tokenPosition(errorToken), "Field accessors can be applied only to identifiers"));
      throw new ParseException();
    }

    return expression;
  }

  private List<Concrete.FunctionClause> visitClauses(ClausesContext ctx) {
    List<ClauseContext> clauses = ctx instanceof ClausesWithBracesContext ? ((ClausesWithBracesContext) ctx).clause() : ((ClausesWithoutBracesContext) ctx).clause();
    List<Concrete.FunctionClause> result = new ArrayList<>(clauses.size());
    for (ClauseContext clause : clauses) {
      result.add(visitClause(clause));
    }
    return result;
  }

  @Override
  public Concrete.FunctionClause visitClause(ClauseContext clauseCtx) {
    List<Concrete.Pattern> patterns = new ArrayList<>(clauseCtx.pattern().size());
    for (PatternContext patternCtx : clauseCtx.pattern()) {
      patterns.add(visitPattern(patternCtx));
    }
    return new Concrete.FunctionClause(tokenPosition(clauseCtx.start), patterns, clauseCtx.expr() == null ? null : visitExpr(clauseCtx.expr()));
  }

  private List<Concrete.ReferenceExpression> checkElimExpressions(List<? extends Concrete.Expression> expressions) {
    List<Concrete.ReferenceExpression> result = new ArrayList<>(expressions.size());
    for (Concrete.Expression elimExpr : expressions) {
      if (!(elimExpr instanceof Concrete.ReferenceExpression)) {
        myErrorReporter.report(new ParserError((Position) elimExpr.getData(), "\\elim can be applied only to a local variable"));
        throw new ParseException();
      }
      result.add((Concrete.ReferenceExpression) elimExpr);
    }
    return result;
  }

  @Override
  public Concrete.Expression visitCase(CaseContext ctx) {
    List<Concrete.Expression> elimExprs = new ArrayList<>();
    for (ExprContext exprCtx : ctx.expr()) {
      elimExprs.add(visitExpr(exprCtx));
    }
    List<Concrete.FunctionClause> clauses = new ArrayList<>();
    for (ClauseContext clauseCtx : ctx.clause()) {
      clauses.add(visitClause(clauseCtx));
    }
    return new Concrete.CaseExpression(tokenPosition(ctx.start), elimExprs, clauses);
  }

  @Override
  public Concrete.LetClause visitLetClause(LetClauseContext ctx) {
    List<Concrete.Parameter> arguments = visitLamTeles(ctx.tele());
    TypeAnnotationContext typeAnnotationCtx = ctx.typeAnnotation();
    Concrete.Expression resultType = typeAnnotationCtx == null ? null : visitExpr(typeAnnotationCtx.expr());
    return new Concrete.LetClause(new ParsedLocalReferable(tokenPosition(ctx.start), ctx.ID().getText()), arguments, resultType, visitExpr(ctx.expr()));
  }

  @Override
  public Concrete.LetExpression visitLet(LetContext ctx) {
    List<Concrete.LetClause> clauses = new ArrayList<>();
    for (LetClauseContext clauseCtx : ctx.letClause()) {
      clauses.add(visitLetClause(clauseCtx));
    }

    return new Concrete.LetExpression(tokenPosition(ctx.start), clauses, visitExpr(ctx.expr()));
  }

  private Position tokenPosition(Token token) {
    return new Position(myModule, token.getLine(), token.getCharPositionInLine());
  }
}
