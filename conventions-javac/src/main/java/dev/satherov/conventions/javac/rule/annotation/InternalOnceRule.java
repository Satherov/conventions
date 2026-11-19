package dev.satherov.conventions.javac.rule.annotation;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

///
/// The rule that an internal type carries `@ApiStatus.Internal` once on itself and never on its members.
///
public class InternalOnceRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "internal-once";

    ///
    /// The annotation that marks a type as internal.
    ///
    private static final String INTERNAL = "ApiStatus.Internal";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return InternalOnceRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks the members of every internal type in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Checks the members of an internal type, and goes on into its nested types.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                if (Annotations.has(type.getModifiers(), InternalOnceRule.INTERNAL)) {
                    InternalOnceRule.checkMembers(file, type, findings);
                    return null;
                }

                return super.visitClass(type, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Records a breach for every member of the given internal type that repeats the mark, nested types included.
    ///
    private static void checkMembers(SourceFile file, ClassTree type, Findings findings) {
        for (Tree member : type.getMembers()) {
            ModifiersTree modifiers = switch (member) {
                case ClassTree nested -> nested.getModifiers();
                case MethodTree method -> method.getModifiers();
                case VariableTree field -> field.getModifiers();
                default -> null;
            };
            if (modifiers != null && Annotations.has(modifiers, InternalOnceRule.INTERNAL)) {
                findings.report(InternalOnceRule.ID, file, member, "@ApiStatus.Internal on a member of an internal type; the mark on " + type.getSimpleName() + " covers it");
            }

            if (member instanceof ClassTree nested) {
                InternalOnceRule.checkMembers(file, nested, findings);
            }
        }
    }
}
