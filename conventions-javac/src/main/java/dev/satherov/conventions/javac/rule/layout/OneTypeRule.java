package dev.satherov.conventions.javac.rule.layout;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

import java.util.List;

///
/// The rule that a file declares exactly one top-level type.
///
public class OneTypeRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "one-type";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return OneTypeRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Records a breach for every top-level type after the first.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        List<? extends Tree> types = file.unit()
                .getTypeDecls()
                .stream()
                .filter(ClassTree.class::isInstance)
                .toList();
        for (Tree type : types.subList(Math.min(1, types.size()), types.size())) {
            findings.report(OneTypeRule.ID, file, type, "a second top-level type in one file; give " + ((ClassTree) type).getSimpleName() + " its own file");
        }
    }
}
