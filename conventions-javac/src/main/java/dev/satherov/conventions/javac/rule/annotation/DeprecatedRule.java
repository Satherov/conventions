package dev.satherov.conventions.javac.rule.annotation;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;
import dev.satherov.conventions.javac.source.SourceSet;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

///
/// The rule that main code holds nothing deprecated: a breaking change deletes the old member instead.
///
public class DeprecatedRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "deprecated";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return DeprecatedRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Records a breach for every `@Deprecated` in a main source file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        if (file.options().set() != SourceSet.MAIN) {
            return;
        }

        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach if the annotation is `@Deprecated`.
            ///
            @Override
            public @Nullable Void visitAnnotation(AnnotationTree annotation, @Nullable Void unused) {
                if (Annotations.simpleName(annotation).equals("Deprecated")) {
                    findings.report(DeprecatedRule.ID, file, annotation, "@Deprecated; delete the old member and update its callers");
                }

                return super.visitAnnotation(annotation, unused);
            }
        }.scan(path, null);
    }
}
