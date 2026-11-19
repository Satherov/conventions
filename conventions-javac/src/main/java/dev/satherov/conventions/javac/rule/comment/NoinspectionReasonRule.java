package dev.satherov.conventions.javac.rule.comment;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.Line;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.BlockTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

///
/// The rule that every `//noinspection` sits on a statement inside a body and
/// lists its inspection ids and then the reason.
///
/// A body is that of a method, a constructor, an initializer, or a lambda.
/// Above a type, a method, or a field, an inspection is suppressed with `@SuppressWarnings` instead.
///
public class NoinspectionReasonRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "noinspection-reason";

    ///
    /// A suppression comment, with the ids and any text after them as groups.
    ///
    private static final Pattern SUPPRESSION = Pattern.compile("^//\\s*noinspection\\s+(\\S+)\\s*(.*)$");

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return NoinspectionReasonRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every suppression comment of the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        List<BlockTree> blocks = NoinspectionReasonRule.blocksOf(path);
        for (Line line : file.getAllCommentLines()) {
            Matcher matcher = NoinspectionReasonRule.SUPPRESSION.matcher(line.content());
            if (!matcher.matches()) {
                continue;
            }

            if (matcher.group(2).isBlank()) {
                findings.report(NoinspectionReasonRule.ID, file, line, "//noinspection without a reason; write //noinspection <id> <reason>");
            }

            if (blocks.stream().noneMatch(block -> file.start(block) < line.start() && line.start() < file.end(block))) {
                findings.report(NoinspectionReasonRule.ID, file, line, "//noinspection outside a body; suppress it with @SuppressWarnings on the type or method");
            }
        }
    }

    ///
    /// Gets every block under the given path.
    ///
    private static @Unmodifiable List<BlockTree> blocksOf(TreePath path) {
        List<BlockTree> blocks = new ArrayList<>();
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreeScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Collects the block and then the blocks inside it.
            ///
            @Override
            public @Nullable Void visitBlock(BlockTree block, @Nullable Void unused) {
                blocks.add(block);
                return super.visitBlock(block, unused);
            }
        }.scan(path.getLeaf(), null);
        return List.copyOf(blocks);
    }
}
