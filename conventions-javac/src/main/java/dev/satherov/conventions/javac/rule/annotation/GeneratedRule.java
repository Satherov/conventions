package dev.satherov.conventions.javac.rule.annotation;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.Origin;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

///
/// The rule that every top-level type the project generates carries `@Generated`.
///
public class GeneratedRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "generated";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return GeneratedRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Only the project's own generated files.
    ///
    @Override
    public Origin origin() {
        return Origin.GENERATED;
    }

    ///
    /// Records a breach for every top-level type of the file without `@Generated`.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        for (Tree declaration : file.unit().getTypeDecls()) {
            if (declaration instanceof ClassTree type && !Annotations.has(type.getModifiers(), "Generated")) {
                findings.report(GeneratedRule.ID, file, type, "generated type " + type.getSimpleName() + " has no @Generated");
            }
        }
    }
}
