package dev.satherov.conventions.javac.rule.expression;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.jspecify.annotations.Nullable;

import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;

///
/// The rule that every reference to a field or method of the enclosing types or their supertypes is qualified:
/// `this.counter`, `this.name()`, `Counters.MAX`, `Outer.this.name`.
///
/// Members brought in by a static import, enum constants in `case` labels and annotation element names are left out.
///
@RequiredArgsConstructor
public class QualificationRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "qualification";

    ///
    /// The kinds of element a reference has to qualify.
    ///
    private static final Set<ElementKind> MEMBERS = Set.of(
            ElementKind.FIELD,
            ElementKind.ENUM_CONSTANT,
            ElementKind.METHOD
    );

    ///
    /// The keywords javac reads as names, which are qualifiers themselves.
    ///
    private static final Set<String> KEYWORDS = Set.of(
            "this",
            "super"
    );

    ///
    /// The javac trees the elements of the references come from.
    ///
    private final Trees trees;

    ///
    /// The type utilities that decide whether a member belongs to an enclosing type.
    ///
    private final Types types;

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return QualificationRule.ID;
    }

    ///
    /// The analyzed phase, since it needs to know what a name resolves to.
    ///
    @Override
    public Phase phase() {
        return Phase.ANALYZED;
    }

    ///
    /// Checks every simple name in the declared methods and field initializers of the type.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Skips the methods javac and Lombok add.
            ///
            @Override
            public @Nullable Void visitMethod(MethodTree method, @Nullable Void unused) {
                if (!file.isDeclared(method)) {
                    return null;
                }

                return super.visitMethod(method, unused);
            }

            ///
            /// Records a breach if the name is an unqualified member of an enclosing type.
            ///
            @Override
            public @Nullable Void visitIdentifier(IdentifierTree identifier, @Nullable Void unused) {
                if (QualificationRule.KEYWORDS.contains(identifier.getName().toString())) {
                    return null;
                }

                TreePath current = this.getCurrentPath();
                Element element = QualificationRule.this.trees.getElement(current);
                if (element != null && QualificationRule.MEMBERS.contains(element.getKind()) && !QualificationRule.isExempt(current) && QualificationRule.this.isInherited(element, current)) {
                    boolean isStatic = element.getModifiers().contains(Modifier.STATIC);
                    String qualifier = isStatic ? element.getEnclosingElement().getSimpleName() + "." : "this.";
                    findings.report(QualificationRule.ID, file, identifier, identifier.getName() + " is unqualified; write " + qualifier + identifier.getName());
                }

                return super.visitIdentifier(identifier, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the given name sits where Java takes a simple name: a `case` label or an annotation element name.
    ///
    private static boolean isExempt(TreePath path) {
        TreePath parent = path.getParentPath();
        TreePath grandparent = parent.getParentPath();
        if (parent.getLeaf() instanceof CaseTree) {
            return true;
        }

        if (grandparent == null) {
            return false;
        }

        if (grandparent.getLeaf() instanceof CaseTree) {
            return true;
        }

        return parent.getLeaf() instanceof AssignmentTree assignment && assignment.getVariable() == path.getLeaf() && grandparent.getLeaf() instanceof AnnotationTree;
    }

    ///
    /// Checks if the given member is declared by a type that encloses the name, or by one of its supertypes.
    ///
    private boolean isInherited(Element member, TreePath path) {
        if (!(member.getEnclosingElement() instanceof TypeElement owner)) {
            return false;
        }

        for (TreePath step = path; step != null; step = step.getParentPath()) {
            if (step.getLeaf() instanceof ClassTree && this.trees.getElement(step) instanceof TypeElement enclosing) {
                if (this.types.isSubtype(this.types.erasure(enclosing.asType()), this.types.erasure(owner.asType()))) {
                    return true;
                }
            }
        }

        return false;
    }
}
