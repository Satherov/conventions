package dev.satherov.conventions.javac.source;

import lombok.RequiredArgsConstructor;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;

///
/// The breaches the rules found, held until the compilation ends and then reported as errors.
///
/// An error stops javac before it checks types, so reporting early would hide every finding of the type-aware rules.
///
@RequiredArgsConstructor
public final class Findings {

    ///
    /// The prefix of every message, which holds the plugin name and then the rule id.
    ///
    public static final String PREFIX = "[conventions:";

    ///
    /// The javac trees the messages are reported through.
    ///
    private final Trees trees;

    ///
    /// The findings not reported yet, in the order they were found.
    ///
    private final List<Finding> pending = new ArrayList<>();

    ///
    /// Records a breach at the given tree.
    ///
    /// @param rule    The id of the rule that was broken.
    /// @param file    The file the tree belongs to.
    /// @param tree    The tree the message points at.
    /// @param message What is wrong and what the rule wants instead.
    ///
    public void report(String rule, SourceFile file, Tree tree, String message) {
        this.pending.add(new Finding(rule, file, tree, message));
    }

    ///
    /// Records a breach on the given line, pointing at the innermost tree that holds it.
    ///
    /// @param rule    The id of the rule that was broken.
    /// @param file    The file the line belongs to.
    /// @param line    The line.
    /// @param message What is wrong and what the rule wants instead.
    ///
    public void report(String rule, SourceFile file, Line line, String message) {
        Tree holder = Findings.holderOf(file, line.start());
        this.pending.add(new Finding(rule, file, holder, "line " + line.number() + ": " + message));
    }

    ///
    /// Finds the innermost tree whose range holds the given position, or the unit itself.
    ///
    private static Tree holderOf(SourceFile file, long position) {
        List<Tree> holders = new ArrayList<>(List.of(file.unit()));
        new TreeScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Descends only into trees that hold the position.
            ///
            @Override
            public @Nullable Void scan(@Nullable Tree tree, @Nullable Void unused) {
                if (tree != null && file.start(tree) <= position && position < file.end(tree)) {
                    holders.add(tree);
                    super.scan(tree, unused);
                }

                return null;
            }
        }.scan(file.unit(), null);
        return holders.getLast();
    }

    ///
    /// Reports every held finding as a compiler error and forgets them.
    ///
    public void flush() {
        for (Finding finding : this.pending) {
            this.trees.printMessage(
                    Diagnostic.Kind.ERROR,
                    Findings.PREFIX + finding.rule() + "] " + finding.message(),
                    finding.tree(),
                    finding.file().unit()
            );
        }

        this.pending.clear();
    }

    ///
    /// A breach waiting to be reported.
    ///
    /// @param rule    The id of the rule that was broken.
    /// @param file    The file the tree belongs to.
    /// @param tree    The tree the message points at.
    /// @param message What is wrong.
    ///
    private record Finding(String rule, SourceFile file, Tree tree, String message) { }
}
