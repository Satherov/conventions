package dev.satherov.conventions.javac.rule.layout;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import java.util.List;

///
/// The rule that a `switch` uses arrow arms, never classic `case X:` labels.
///
public class ClassicSwitchRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "classic-switch";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return ClassicSwitchRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every `switch` statement and expression in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Checks the arms of the statement.
            ///
            @Override
            public @Nullable Void visitSwitch(SwitchTree tree, @Nullable Void unused) {
                ClassicSwitchRule.checkCases(file, tree, tree.getCases(), findings);
                return super.visitSwitch(tree, unused);
            }

            ///
            /// Checks the arms of the expression.
            ///
            @Override
            public @Nullable Void visitSwitchExpression(SwitchExpressionTree tree, @Nullable Void unused) {
                ClassicSwitchRule.checkCases(file, tree, tree.getCases(), findings);
                return super.visitSwitchExpression(tree, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Records one breach for the given switch if any of its cases is a classic label.
    ///
    private static void checkCases(SourceFile file, Tree tree, List<? extends CaseTree> cases, Findings findings) {
        if (cases.stream().anyMatch(arm -> arm.getCaseKind() == CaseTree.CaseKind.STATEMENT)) {
            findings.report(ClassicSwitchRule.ID, file, tree, "a classic switch; use arrow arms (case X -> ...)");
        }
    }
}
