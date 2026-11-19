package dev.satherov.conventions.javac.source;

import lombok.RequiredArgsConstructor;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
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
/// A finding is dropped when the tree it points at, or a method, constructor, or type around it, carries
/// `@SuppressWarnings("conventions:<id>")` with the id of its rule.
///
@RequiredArgsConstructor
public final class Findings {

    ///
    /// The prefix of every message, which holds the plugin name and then the rule id.
    ///
    public static final String PREFIX = "[conventions:";

    ///
    /// The prefix of a `@SuppressWarnings` key that suppresses a rule, followed by the rule's id.
    ///
    private static final String SUPPRESSION = "conventions:";

    ///
    /// The simple name of the annotation that suppresses a rule.
    ///
    private static final String SUPPRESS_WARNINGS = "SuppressWarnings";

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
        if (!this.isSuppressed(rule, file, tree)) {
            this.pending.add(new Finding(rule, file, tree, message));
        }
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
        if (!this.isSuppressed(rule, file, holder)) {
            this.pending.add(new Finding(rule, file, holder, "line " + line.number() + ": " + message));
        }
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
    /// Checks if the given tree, or a method or type around it, suppresses the given rule.
    ///
    /// This runs when the finding is reported, since javac rewrites the trees of a class once it has analyzed it.
    ///
    private boolean isSuppressed(String rule, SourceFile file, Tree tree) {
        String key = Findings.SUPPRESSION + rule;
        for (TreePath path = this.trees.getPath(file.unit(), tree); path != null; path = path.getParentPath()) {
            ModifiersTree modifiers = switch (path.getLeaf()) {
                case MethodTree method -> method.getModifiers();
                case ClassTree type -> type.getModifiers();
                default -> null;
            };
            if (modifiers != null && Findings.suppresses(modifiers, key)) {
                return true;
            }
        }

        return false;
    }

    ///
    /// Checks if a `@SuppressWarnings` among the given modifiers holds the given key.
    ///
    private static boolean suppresses(ModifiersTree modifiers, String key) {
        return Annotations.find(modifiers, Findings.SUPPRESS_WARNINGS)
                .map(Annotations::getAllStringsOf)
                .orElse(List.of())
                .stream()
                .anyMatch(value -> key.equals(value.getValue()));
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
