package dev.satherov.conventions.javac.rule.annotation;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

///
/// The rule that `@SuppressWarnings` sits on a type, a method, or a constructor and names javac lint keys or IntelliJ
/// inspection ids.
///
/// A finding on a field, a parameter, a record component, or a local is suppressed on the enclosing method or type. An
/// IntelliJ finding inside a body may also be silenced on its statement with a `//noinspection <id> <reason>` line.
///
public class SuppressWarningsRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "suppress-warnings";

    ///
    /// The lint keys javac knows, which are the only values `@SuppressWarnings` may name.
    ///
    public static final Set<String> LINT_KEYS = Set.of(
            "all",
            "auxiliaryclass",
            "cast",
            "classfile",
            "dangling-doc-comments",
            "dep-ann",
            "deprecation",
            "divzero",
            "empty",
            "exports",
            "fallthrough",
            "finally",
            "identity",
            "incubating",
            "lossy-conversions",
            "missing-explicit-ctor",
            "module",
            "opens",
            "options",
            "output-file-clash",
            "overloads",
            "overrides",
            "path",
            "preview",
            "processing",
            "rawtypes",
            "removal",
            "requires-automatic",
            "requires-transitive-automatic",
            "restricted",
            "serial",
            "static",
            "strictfp",
            "synchronization",
            "text-blocks",
            "this-escape",
            "try",
            "unchecked",
            "varargs"
    );

    ///
    /// The pattern of an IntelliJ inspection id.
    ///
    private static final Pattern INSPECTION = Pattern.compile("[A-Z][A-Za-z0-9]*");

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return SuppressWarningsRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks the values of every `@SuppressWarnings` in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach for an annotation outside a type, a method, or a constructor,
            /// and for every value that is neither a javac lint key nor an inspection id.
            ///
            @Override
            public @Nullable Void visitAnnotation(AnnotationTree annotation, @Nullable Void unused) {
                if (!Annotations.simpleName(annotation).equals("SuppressWarnings")) {
                    return super.visitAnnotation(annotation, unused);
                }

                Tree owner = this.getCurrentPath().getParentPath().getParentPath().getLeaf();
                if (!(owner instanceof ClassTree) && !(owner instanceof MethodTree)) {
                    findings.report(SuppressWarningsRule.ID, file, annotation, "@SuppressWarnings goes on the enclosing method or type");
                }

                for (ExpressionTree argument : annotation.getArguments()) {
                    for (LiteralTree value : SuppressWarningsRule.valuesOf(argument)) {
                        String key = String.valueOf(value.getValue());
                        if (!SuppressWarningsRule.LINT_KEYS.contains(key) && !SuppressWarningsRule.INSPECTION.matcher(key).matches()) {
                            findings.report(SuppressWarningsRule.ID, file, value, key + " is neither a javac lint key nor an IntelliJ inspection id");
                        }
                    }
                }

                return super.visitAnnotation(annotation, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Gets the string literals an annotation argument holds, alone or in an array.
    ///
    private static @Unmodifiable List<LiteralTree> valuesOf(ExpressionTree argument) {
        return switch (argument) {
            case AssignmentTree assignment -> SuppressWarningsRule.valuesOf(assignment.getExpression());
            case LiteralTree literal -> List.of(literal);
            case NewArrayTree array -> array.getInitializers()
                    .stream()
                    .filter(LiteralTree.class::isInstance)
                    .map(LiteralTree.class::cast)
                    .toList();
            default -> List.of();
        };
    }
}
