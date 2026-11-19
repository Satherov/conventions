package dev.satherov.conventions.javac.rule.comment;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.Line;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.util.TreePath;

///
/// The rule that no code is left in a comment.
///
/// A `//` line that ends like a statement or a block, with `;`, `{`, or `}`, counts as code.
///
public class CommentedCodeRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "commented-code";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return CommentedCodeRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every `//` line of the file that is not a doc comment, a suppression, or a formatter switch.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        for (Line line : file.getAllCommentLines()) {
            String content = line.content();
            if (content.startsWith("///")) {
                continue;
            }

            String body = content.substring(2).strip();
            if (body.startsWith("noinspection") || body.startsWith("@formatter")) {
                continue;
            }

            if (body.endsWith(";") || body.endsWith("{") || body.endsWith("}")) {
                findings.report(CommentedCodeRule.ID, file, line, "commented-out code; delete it");
            }
        }
    }
}
