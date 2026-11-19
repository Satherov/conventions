package dev.satherov.conventions.javac.rule.lombok;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

///
/// The rule that Lombok is written in its current forms: no `lombok.NonNull`, no `@__(...)`, and a single annotation
/// in an `onX_` element without braces.
///
public class LombokFormRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "lombok-form";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return LombokFormRule.ID;
    }

    ///
    /// The parsed phase, since Lombok removes its annotations once it ran.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks the imports and the Lombok annotations of the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        for (ImportTree importTree : file.unit().getImports()) {
            if (importTree.getQualifiedIdentifier().toString().equals("lombok.NonNull")) {
                findings.report(LombokFormRule.ID, file, importTree, "lombok.NonNull; non-null is the default under @NullMarked");
            }
        }

        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Checks the elements of the annotation for the old forms.
            ///
            @Override
            public @Nullable Void visitAnnotation(AnnotationTree annotation, @Nullable Void unused) {
                if (Annotations.simpleName(annotation).equals("__")) {
                    findings.report(LombokFormRule.ID, file, annotation, "@__(...); write onX_ = @Annotation");
                }

                for (ExpressionTree argument : annotation.getArguments()) {
                    if (argument instanceof AssignmentTree assignment && LombokFormRule.isBracedSingle(assignment)) {
                        String name = assignment.getVariable().toString();
                        findings.report(LombokFormRule.ID, file, argument, name + " with one annotation in braces; write " + name + " = @Annotation");
                    }
                }

                return super.visitAnnotation(annotation, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the given annotation element is an `onX_` element holding one annotation in braces.
    ///
    private static boolean isBracedSingle(AssignmentTree assignment) {
        return assignment.getVariable() instanceof IdentifierTree name &&
                name.getName().toString().endsWith("_") &&
                assignment.getExpression() instanceof NewArrayTree array &&
                array.getInitializers().size() == 1;
    }
}
