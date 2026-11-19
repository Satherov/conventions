package dev.satherov.conventions.javac.rule.declaration;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;
import dev.satherov.conventions.javac.source.SourceSet;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

///
/// The rule that every member of a class, enum, or record in main code states its visibility.
///
/// Left out are what Java makes visible on its own (members of interfaces and annotation types, enum constants and
/// constructors), record components, the canonical constructor of a record that states no visibility itself, members
/// marked `@VisibleForTesting` or `@TestOnly`, and top-level types, whose package-private form has no keyword.
///
public class VisibilityRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "visibility";

    ///
    /// The modifiers that state a visibility.
    ///
    private static final Set<Modifier> VISIBILITIES = Set.of(
            Modifier.PUBLIC,
            Modifier.PROTECTED,
            Modifier.PRIVATE
    );

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return VisibilityRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks the members of every type in a main source file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        if (file.options().set() != SourceSet.MAIN) {
            return;
        }

        for (Tree declaration : file.unit().getTypeDecls()) {
            if (declaration instanceof ClassTree type) {
                VisibilityRule.checkMembers(file, type, findings);
            }
        }
    }

    ///
    /// Records a breach for every member of the given type that states no visibility, nested types included.
    ///
    private static void checkMembers(SourceFile file, ClassTree type, Findings findings) {
        Tree.Kind kind = type.getKind();
        boolean implicit = kind == Tree.Kind.INTERFACE || kind == Tree.Kind.ANNOTATION_TYPE;
        for (Tree member : type.getMembers()) {
            switch (member) {
                case ClassTree nested -> {
                    if (!implicit) {
                        VisibilityRule.checkModifiers(file, nested, nested.getModifiers(), "type " + nested.getSimpleName(), findings);
                    }

                    VisibilityRule.checkMembers(file, nested, findings);
                }
                case MethodTree method when !implicit && !VisibilityRule.isImplicitConstructor(type, method) -> VisibilityRule.checkModifiers(file, method, method.getModifiers(), "method " + method.getName(), findings);
                case VariableTree field when !implicit && !VisibilityRule.isComponentOrConstant(file, type, field) -> VisibilityRule.checkModifiers(file, field, field.getModifiers(), "field " + field.getName(), findings);
                default -> { }
            }
        }
    }

    ///
    /// Checks if the given method is a constructor whose visibility Java fixes: any enum constructor, and the
    /// canonical constructor of a record that states no visibility itself.
    ///
    private static boolean isImplicitConstructor(ClassTree type, MethodTree method) {
        if (!method.getName().contentEquals("<init>")) {
            return false;
        }

        if (type.getKind() == Tree.Kind.ENUM) {
            return true;
        }

        boolean visible = type.getModifiers().getFlags().stream().anyMatch(VisibilityRule.VISIBILITIES::contains);
        return type.getKind() == Tree.Kind.RECORD && !visible;
    }

    ///
    /// Checks if the given field is a record component or an enum constant, neither of which takes a visibility.
    ///
    /// An enum constant is told apart from a field by its source, which starts with its name once its annotations
    /// are skipped.
    ///
    private static boolean isComponentOrConstant(SourceFile file, ClassTree type, VariableTree field) {
        boolean isStatic = field.getModifiers().getFlags().contains(Modifier.STATIC);
        if (type.getKind() == Tree.Kind.RECORD) {
            return !isStatic;
        }

        if (type.getKind() != Tree.Kind.ENUM) {
            return false;
        }

        List<? extends AnnotationTree> annotations = field.getModifiers().getAnnotations();
        long start = annotations.isEmpty() ? file.start(field) : file.end(annotations.getLast());
        return file.text(start, file.end(field))
                .stripLeading()
                .startsWith(field.getName().toString());
    }

    ///
    /// Records a breach if the given modifiers state no visibility and carry no mark that allows that.
    ///
    private static void checkModifiers(SourceFile file, Tree member, ModifiersTree modifiers, String name, Findings findings) {
        boolean visible = modifiers.getFlags().stream().anyMatch(VisibilityRule.VISIBILITIES::contains);
        boolean marked = Annotations.has(modifiers, "VisibleForTesting") || Annotations.has(modifiers, "TestOnly");
        if (!visible && !marked) {
            findings.report(VisibilityRule.ID, file, member, name + " states no visibility; package-private is only for a member marked @VisibleForTesting");
        }
    }
}
