package com.github.sommeri.less4j.core.compiler.stages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.github.sommeri.less4j.core.ast.ASTCssNode;
import com.github.sommeri.less4j.core.ast.ASTCssNodeType;
import com.github.sommeri.less4j.core.ast.CssString;
import com.github.sommeri.less4j.core.ast.EscapedSelector;
import com.github.sommeri.less4j.core.ast.EscapedValue;
import com.github.sommeri.less4j.core.ast.Expression;
import com.github.sommeri.less4j.core.ast.FixedNamePart;
import com.github.sommeri.less4j.core.ast.GeneralBody;
import com.github.sommeri.less4j.core.ast.IndirectVariable;
import com.github.sommeri.less4j.core.ast.InterpolableName;
import com.github.sommeri.less4j.core.ast.MixinReference;
import com.github.sommeri.less4j.core.ast.SimpleSelector;
import com.github.sommeri.less4j.core.ast.Variable;
import com.github.sommeri.less4j.core.ast.VariableNamePart;
import com.github.sommeri.less4j.core.compiler.expressions.ExpressionEvaluator;
import com.github.sommeri.less4j.core.compiler.expressions.strings.StringInterpolator;
import com.github.sommeri.less4j.core.compiler.scopes.FullMixinDefinition;
import com.github.sommeri.less4j.core.compiler.scopes.InScopeSnapshotRunner;
import com.github.sommeri.less4j.core.compiler.scopes.InScopeSnapshotRunner.ITask;
import com.github.sommeri.less4j.core.compiler.scopes.IteratedScope;
import com.github.sommeri.less4j.core.compiler.scopes.Scope;
import com.github.sommeri.less4j.core.parser.HiddenTokenAwareTree;
import com.github.sommeri.less4j.core.problems.ProblemsHandler;
import com.github.sommeri.less4j.utils.QuotesKeepingInStringCssPrinter;

public class ReferencesSolver {

  public static final String ALL_ARGUMENTS = "@arguments";
  private ASTManipulator manipulator = new ASTManipulator();
  private final MixinsSolver mixinsSolver;
  private final ProblemsHandler problemsHandler;
  private StringInterpolator stringInterpolator = new StringInterpolator();

  public ReferencesSolver(ProblemsHandler problemsHandler) {
    this.problemsHandler = problemsHandler;
    mixinsSolver = new MixinsSolver(this, problemsHandler);
  }

  public void solveReferences(final ASTCssNode node, final Scope scope) {
    doSolveReferences(node, new IteratedScope(scope));
  }

  protected void doSolveReferences(ASTCssNode node, Scope scope) {
    unsafeDoSolveReferences(node, new IteratedScope(scope));
  }

  private void doSolveReferences(final ASTCssNode node, final IteratedScope scope) {
    // ... and I'm starting to see the point of closures ...
    InScopeSnapshotRunner.runInLocalDataSnapshot(scope, new ITask() {

      @Override
      public void run() {
        unsafeDoSolveReferences(node, scope);
      }

    });
  }

  private void unsafeDoSolveReferences(ASTCssNode node, IteratedScope iteratedScope) {
    List<ASTCssNode> childs = new ArrayList<ASTCssNode>(node.getChilds());
    if (!childs.isEmpty()) {
      Scope scope = iteratedScope.getScope();

      // solve all mixin references and store solutions
      Map<MixinReference, GeneralBody> solvedMixinReferences = solveMixinReferences(childs, scope);

      // solve whatever is not a mixin reference
      solveNonMixinReferences(childs, iteratedScope);

      // replace mixin references by their solutions - we need to do it in the end
      // the scope and ast would get out of sync otherwise
      replaceMixinReferences(solvedMixinReferences);
    }
  }

  private void solveNonMixinReferences(List<ASTCssNode> childs, IteratedScope iteratedScope) {
    for (ASTCssNode kid : childs) {
      if (!isMixinReference(kid)) {
        if (AstLogic.hasOwnScope(kid)) {
          IteratedScope scope = iteratedScope.getNextChild();
          doSolveReferences(kid, scope);
        } else {
          boolean finishedNode = solveIfVariableReference(kid, iteratedScope.getScope());
          if (!finishedNode)
            unsafeDoSolveReferences(kid, iteratedScope);
        }
      }
    }
  }

  protected boolean isMixinReference(ASTCssNode kid) {
    return kid.getType() == ASTCssNodeType.MIXIN_REFERENCE;
  }

  private void replaceMixinReferences(Map<MixinReference, GeneralBody> solvedMixinReferences) {
    for (Entry<MixinReference, GeneralBody> entry : solvedMixinReferences.entrySet()) {
      MixinReference mixinReference = entry.getKey();
      GeneralBody replacement = entry.getValue();
      manipulator.replaceInBody(mixinReference, replacement.getMembers());
    }
  }

  private Map<MixinReference, GeneralBody> solveMixinReferences(List<ASTCssNode> childs, Scope mixinReferenceScope) {
    Map<MixinReference, GeneralBody> solvedMixinReferences = new HashMap<MixinReference, GeneralBody>();
    for (ASTCssNode kid : childs) {
      if (isMixinReference(kid)) {
        MixinReference mixinReference = (MixinReference) kid;
        List<FullMixinDefinition> foundMixins = findReferencedMixins(mixinReference, mixinReferenceScope);
        GeneralBody replacement = mixinsSolver.buildMixinReferenceReplacement(mixinReference, mixinReferenceScope, foundMixins);
        
        AstLogic.validateLessBodyCompatibility(mixinReference, replacement.getMembers(), problemsHandler);
        solvedMixinReferences.put(mixinReference, replacement);
      }
    }
    return solvedMixinReferences;
  }

  protected List<FullMixinDefinition> findReferencedMixins(MixinReference mixinReference, Scope scope) {
    List<FullMixinDefinition> sameNameMixins = scope.getNearestMixins(mixinReference, problemsHandler);
    if (sameNameMixins.isEmpty()) {
      problemsHandler.undefinedMixin(mixinReference);
      return new ArrayList<FullMixinDefinition>();
    }

    List<FullMixinDefinition> mixins = (new MixinsReferenceMatcher(scope, problemsHandler)).filter(mixinReference, sameNameMixins);
    if (mixins.isEmpty())
      problemsHandler.unmatchedMixin(mixinReference);

    return mixins;
  }

  private boolean solveIfVariableReference(ASTCssNode node, Scope scope) {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(scope, problemsHandler);
    switch (node.getType()) {
    case VARIABLE: {
      Expression replacement = expressionEvaluator.evaluate((Variable) node);
      manipulator.replace(node, replacement);
      return true;
    }
    case INDIRECT_VARIABLE: {
      Expression replacement = expressionEvaluator.evaluate((IndirectVariable) node);
      manipulator.replace(node, replacement);
      return true;
    }
    case STRING_EXPRESSION: {
      Expression replacement = expressionEvaluator.evaluate((CssString) node);
      manipulator.replace(node, replacement);
      return true;
    }
    case ESCAPED_VALUE: {
      Expression replacement = expressionEvaluator.evaluate((EscapedValue) node);
      manipulator.replace(node, replacement);
      return true;
    }
    case ESCAPED_SELECTOR: {
      SimpleSelector replacement = interpolateEscapedSelector((EscapedSelector) node, expressionEvaluator);
      manipulator.replace(node, replacement);
      return true;
    }
    case FIXED_NAME_PART: {
      FixedNamePart part = (FixedNamePart) node;
      FixedNamePart replacement = interpolateFixedNamePart(part, expressionEvaluator);
      part.getParent().replaceMember(part, replacement);
      return true;
    }
    case VARIABLE_NAME_PART: {
      VariableNamePart part = (VariableNamePart) node;
      Expression value = expressionEvaluator.evaluate(part.getVariable());
      FixedNamePart fixedName = toFixedName(value, node.getUnderlyingStructure());
      part.getParent().replaceMember(part, interpolateFixedNamePart(fixedName, expressionEvaluator));
      return true;
    }
    default: // nothing
    }
    return false;
  }

  private FixedNamePart toFixedName(Expression value, HiddenTokenAwareTree parent) {
    QuotesKeepingInStringCssPrinter printer = new QuotesKeepingInStringCssPrinter();
    printer.append(value);
    // property based alternative would be nice, but does not seem to be needed
    FixedNamePart fixedName = new FixedNamePart(parent, printer.toString());
    return fixedName;
  }

  private SimpleSelector interpolateEscapedSelector(EscapedSelector input, ExpressionEvaluator expressionEvaluator) {
    HiddenTokenAwareTree underlying = input.getUnderlyingStructure();
    String value = stringInterpolator.replaceIn(input.getValue(), expressionEvaluator, input.getUnderlyingStructure());
    InterpolableName interpolableName = new InterpolableName(underlying, new FixedNamePart(underlying, value));
    return new SimpleSelector(input.getUnderlyingStructure(), interpolableName, false);
  }

  private FixedNamePart interpolateFixedNamePart(FixedNamePart input, ExpressionEvaluator expressionEvaluator) {
    String value = stringInterpolator.replaceIn(input.getName(), expressionEvaluator, input.getUnderlyingStructure());
    return new FixedNamePart(input.getUnderlyingStructure(), value);
  }

}
