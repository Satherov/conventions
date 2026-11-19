package dev.satherov.conventions.javac.rule.layout;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;

import org.jspecify.annotations.Nullable;

///
/// The rule that every `if` and `else` body is a braced block.
///
/// The one exception is a short guard that throws, with no `else`, written on the line of its `if`.
/// A guard whose message is concatenated is not short.
///
public class IfBracesRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "if-braces";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return IfBracesRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every `if` in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach for an unbraced body that is not a short one-line throw guard.
            ///
            @Override
            public @Nullable Void visitIf(IfTree tree, @Nullable Void unused) {
                StatementTree then = tree.getThenStatement();
                StatementTree otherwise = tree.getElseStatement();
                boolean guard = then instanceof ThrowTree thrown
                        && otherwise == null
                        && file.line(file.start(then)) == file.line(file.start(tree))
                        && !IfBracesRule.isConcatenated(thrown);

                if (!(then instanceof BlockTree) && !guard) {
                    findings.report(IfBracesRule.ID, file, then, "an if body is braced, unless it is a short one-line guard that throws");
                }

                if (otherwise != null && !(otherwise instanceof BlockTree) && !(otherwise instanceof IfTree)) {
                    findings.report(IfBracesRule.ID, file, otherwise, "an else body is braced");
                }

                return super.visitIf(tree, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the expression the given statement throws contains a `+`.
    ///
    private static boolean isConcatenated(ThrowTree thrown) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        Boolean concatenated = new TreeScanner<@Nullable Boolean, @Nullable Void>() {

            ///
            /// Finds a `+` in the given expression or in its operands.
            ///
            @Override
            public @Nullable Boolean visitBinary(BinaryTree binary, @Nullable Void unused) {
                if (binary.getKind() == Tree.Kind.PLUS) {
                    return true;
                }

                return super.visitBinary(binary, unused);
            }

            ///
            /// Combines the findings of two subtrees.
            ///
            @Override
            public Boolean reduce(@Nullable Boolean first, @Nullable Boolean second) {
                return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second);
            }
        }.scan(thrown.getExpression(), null);
        return Boolean.TRUE.equals(concatenated);
    }
}
