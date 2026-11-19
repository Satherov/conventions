package dev.satherov.conventions.javac.rule.layout;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

///
/// The rule that an empty body is written `{ }` on one line, with one space inside, the empty arm of an arrow `switch`
/// included.
///
/// A body that holds only a comment is not empty.
///
public class EmptyBodyRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "empty-body";

    ///
    /// The one form an empty body takes.
    ///
    private static final String EMPTY = "{ }";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return EmptyBodyRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every empty block and every type without members in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Checks the block if it holds no statement.
            ///
            @Override
            public @Nullable Void visitBlock(BlockTree block, @Nullable Void unused) {
                if (block.getStatements().isEmpty()) {
                    EmptyBodyRule.checkBraces(file, block, findings);
                }

                return super.visitBlock(block, unused);
            }

            ///
            /// Checks the body of the type if it has no members.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                if (type.getMembers().isEmpty()) {
                    EmptyBodyRule.checkBraces(file, type, findings);
                }

                return super.visitClass(type, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Records a breach if the braces that close the given tree are not exactly `{ }`.
    ///
    private static void checkBraces(SourceFile file, Tree tree, Findings findings) {
        String text = file.text(tree);
        if (!text.endsWith("}")) {
            return;
        }

        int close = text.length() - 1;
        int open = close - 1;
        while (open >= 0 && Character.isWhitespace(text.charAt(open))) {
            open--;
        }

        if (open < 0 || text.charAt(open) != '{') {
            return;
        }

        if (!text.substring(open, close + 1).equals(EmptyBodyRule.EMPTY)) {
            findings.report(EmptyBodyRule.ID, file, tree, "an empty body is written { } on one line");
        }
    }
}
