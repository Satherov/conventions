package dev.satherov.conventions.javac.rule.lombok;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;

///
/// The rule that a class of only static members is a Lombok `@UtilityClass` instead of hiding a constructor by hand.
///
public class UtilityClassRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "utility-class";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return UtilityClassRule.ID;
    }

    ///
    /// The parsed phase, since Lombok rewrites a utility class once it ran.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every class in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach if the class hides a constructor in front of nothing but static members.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                if (type.getKind() == Tree.Kind.CLASS && !Annotations.has(type.getModifiers(), "UtilityClass") && UtilityClassRule.isHandWrittenUtility(type)) {
                    findings.report(UtilityClassRule.ID, file, type, type.getSimpleName() + " holds only static members behind a private constructor; use @UtilityClass");
                }

                return super.visitClass(type, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the given class has a private constructor and, besides constructors and nested types, only static
    /// members, at least one of them.
    ///
    private static boolean isHandWrittenUtility(ClassTree type) {
        boolean privateConstructor = false;
        boolean statics = false;
        for (Tree member : type.getMembers()) {
            switch (member) {
                case MethodTree method when method.getName().contentEquals("<init>") -> {
                    if (!method.getModifiers().getFlags().contains(Modifier.PRIVATE)) {
                        return false;
                    }

                    privateConstructor = true;
                }
                case MethodTree method -> {
                    if (!method.getModifiers().getFlags().contains(Modifier.STATIC)) {
                        return false;
                    }

                    statics = true;
                }
                case VariableTree field -> {
                    if (!field.getModifiers().getFlags().contains(Modifier.STATIC)) {
                        return false;
                    }

                    statics = true;
                }
                case BlockTree block -> {
                    if (!block.isStatic()) {
                        return false;
                    }
                }
                default -> { }
            }
        }

        return privateConstructor && statics;
    }
}
