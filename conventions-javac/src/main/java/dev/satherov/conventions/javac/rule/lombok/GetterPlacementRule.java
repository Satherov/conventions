package dev.satherov.conventions.javac.rule.lombok;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Modifier;

///
/// The rule that Lombok's `@Getter` sits on the type when every field gets one and on each field otherwise, and that
/// a type with getters carries `@Accessors(fluent = true)`.
///
/// Applies to classes and enums; records have accessors of their own.
///
public class GetterPlacementRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "getter-placement";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return GetterPlacementRule.ID;
    }

    ///
    /// The parsed phase, since Lombok removes its annotations once it ran.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every class and enum in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Checks where the getters of the type are declared.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                if (type.getKind() == Tree.Kind.CLASS || type.getKind() == Tree.Kind.ENUM) {
                    GetterPlacementRule.checkType(file, type, findings);
                }

                return super.visitClass(type, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Records a breach for a misplaced `@Getter` or a missing `@Accessors(fluent = true)` on the given type.
    ///
    private static void checkType(SourceFile file, ClassTree type, Findings findings) {
        List<VariableTree> fields = type.getMembers()
                .stream()
                .filter(VariableTree.class::isInstance)
                .map(VariableTree.class::cast)
                .filter(field -> !field.getModifiers().getFlags().contains(Modifier.STATIC))
                .toList();
        List<VariableTree> withGetter = fields.stream()
                .filter(field -> Annotations.has(field.getModifiers(), "Getter"))
                .toList();
        List<VariableTree> withPlainGetter = withGetter.stream()
                .filter(GetterPlacementRule::hasPlainGetter)
                .toList();
        boolean onType = Annotations.has(type.getModifiers(), "Getter");
        if (onType) {
            for (VariableTree field : withPlainGetter) {
                findings.report(GetterPlacementRule.ID, file, field, "@Getter on field " + field.getName() + " repeats the one on " + type.getSimpleName() + "; when a field gets none, put @Getter on each field that does");
            }
        } else if (!fields.isEmpty() && withPlainGetter.size() == fields.size()) {
            findings.report(GetterPlacementRule.ID, file, type, "every field of " + type.getSimpleName() + " has @Getter; put one @Getter on the type");
        }

        if ((onType || !withGetter.isEmpty()) && !GetterPlacementRule.isFluent(type)) {
            findings.report(GetterPlacementRule.ID, file, type, type.getSimpleName() + " has getters but no @Accessors(fluent = true)");
        }
    }

    ///
    /// Checks if the given field carries a `@Getter` without arguments, one that `@Getter(lazy = true)` or an access
    /// level does not set apart.
    ///
    private static boolean hasPlainGetter(VariableTree field) {
        return Annotations.find(field.getModifiers(), "Getter")
                .map(annotation -> annotation.getArguments().isEmpty())
                .orElse(false);
    }

    ///
    /// Checks if the given type carries `@Accessors(fluent = true)`.
    ///
    private static boolean isFluent(ClassTree type) {
        Optional<AnnotationTree> accessors = Annotations.find(type.getModifiers(), "Accessors");
        return accessors.map(annotation -> annotation.getArguments()
                        .stream()
                        .anyMatch(argument -> argument.toString().replace(" ", "").equals("fluent=true")))
                .orElse(false);
    }
}
