package dev.satherov.conventions.javac.rule.layout;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import java.util.List;

///
/// The rule that a blank line follows every statement that ends in the closing `}` of a nested block, before the next
/// statement of the same block.
///
/// A statement that ends in `;` is not a block statement, such as a `do` loop or a call that takes a lambda.
///
public class BlockSpacingRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "block-spacing";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return BlockSpacingRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every block in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach for every statement that directly follows a block statement.
            ///
            @Override
            public @Nullable Void visitBlock(BlockTree block, @Nullable Void unused) {
                List<? extends StatementTree> statements = block.getStatements();
                for (int i = 1; i < statements.size(); i++) {
                    StatementTree previous = statements.get(i - 1);
                    StatementTree next = statements.get(i);
                    if (BlockSpacingRule.isBlock(file, previous) && !BlockSpacingRule.isSpaced(file, previous, next)) {
                        findings.report(BlockSpacingRule.ID, file, next, "a blank line follows the closing } of a nested block");
                    }
                }

                return super.visitBlock(block, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the given statement ends in the closing `}` of a block.
    ///
    private static boolean isBlock(SourceFile file, StatementTree statement) {
        return file.text(statement).endsWith("}");
    }

    ///
    /// Checks if a blank line sits between the given statements.
    ///
    private static boolean isSpaced(SourceFile file, StatementTree previous, StatementTree next) {
        long first = file.line(file.end(previous));
        long last = file.line(file.start(next));
        for (long number = first + 1; number < last; number++) {
            if (file.lineText(number).isBlank()) {
                return true;
            }
        }

        return false;
    }
}
