package dev.satherov.conventions.javac.rule.layout;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
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
/// The rule that the members of a type come in the order static fields, instance fields, constructors, methods,
/// nested types.
///
/// The finer order of methods is a judgment the rule leaves alone. Enum constants count as static fields, record
/// components as instance fields and initializer blocks as constructors.
///
public class MemberOrderRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "member-order";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return MemberOrderRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks the member order of every type in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach for the first member that comes after a member of a later kind.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                Kind reached = Kind.STATIC_FIELD;
                for (Tree member : type.getMembers()) {
                    if (MemberOrderRule.isComponent(type, member)) {
                        continue;
                    }

                    Kind kind = Kind.of(member);
                    if (kind.compareTo(reached) < 0) {
                        findings.report(MemberOrderRule.ID, file, member, kind.label + " after " + reached.label + "; members go static fields, instance fields, constructors, methods, nested types");
                        break;
                    }

                    reached = kind;
                }

                return super.visitClass(type, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the given member is a component of the given record, which javac lists first whatever follows it.
    ///
    private static boolean isComponent(ClassTree type, Tree member) {
        return type.getKind() == Tree.Kind.RECORD &&
                member instanceof VariableTree field &&
                !field.getModifiers().getFlags().contains(Modifier.STATIC);
    }

    ///
    /// The kind a member has for its place in the order, earliest first.
    ///
    @RequiredArgsConstructor
    private enum Kind {
        ///
        /// An enum constant or a static field.
        ///
        STATIC_FIELD("a static field"),

        ///
        /// An instance field or record component.
        ///
        FIELD("an instance field"),

        ///
        /// A constructor or initializer block.
        ///
        CONSTRUCTOR("a constructor"),

        ///
        /// A method.
        ///
        METHOD("a method"),

        ///
        /// A nested type.
        ///
        TYPE("a nested type");

        ///
        /// The words a message uses for the kind.
        ///
        private final String label;

        ///
        /// Gets the kind of the given member.
        ///
        private static Kind of(Tree member) {
            return switch (member) {
                case VariableTree field when field.getModifiers().getFlags().contains(Modifier.STATIC) -> Kind.STATIC_FIELD;
                case VariableTree ignored -> Kind.FIELD;
                case BlockTree ignored -> Kind.CONSTRUCTOR;
                case MethodTree method when method.getName().contentEquals("<init>") -> Kind.CONSTRUCTOR;
                case MethodTree ignored -> Kind.METHOD;
                default -> Kind.TYPE;
            };
        }
    }
}
