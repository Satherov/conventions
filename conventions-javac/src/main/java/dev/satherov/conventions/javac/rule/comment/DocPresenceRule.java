package dev.satherov.conventions.javac.rule.comment;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;
import dev.satherov.conventions.javac.source.SourceSet;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

import javax.lang.model.element.Modifier;

///
/// The rule that every declaration of main code has a doc comment, and every top-level type of test code.
///
/// Types, methods, constructors, fields, and enum constants count. Record components are described by the
/// `@param` lines of their record, and members of local and anonymous classes are left out.
///
public class DocPresenceRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "doc-presence";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return DocPresenceRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every top-level type of the file and, in main code, its members.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        if (file.isPackageInfo()) {
            return;
        }

        boolean members = file.options().set() == SourceSet.MAIN;
        for (Tree declaration : file.unit().getTypeDecls()) {
            if (declaration instanceof ClassTree type) {
                DocPresenceRule.checkType(file, type, members, findings);
            }
        }
    }

    ///
    /// Checks the given type and, if asked to, each of its members.
    ///
    private static void checkType(SourceFile file, ClassTree type, boolean members, Findings findings) {
        DocPresenceRule.checkDeclaration(file, type, "type " + type.getSimpleName(), findings);
        if (!members) {
            return;
        }

        boolean record = type.getKind() == Tree.Kind.RECORD;
        for (Tree member : type.getMembers()) {
            switch (member) {
                case ClassTree nested -> DocPresenceRule.checkType(file, nested, true, findings);
                case VariableTree field when record && !field.getModifiers().getFlags().contains(Modifier.STATIC) -> { }
                case VariableTree field -> DocPresenceRule.checkDeclaration(file, field, "field " + field.getName(), findings);
                case MethodTree method -> DocPresenceRule.checkDeclaration(file, method, DocPresenceRule.describe(type, method), findings);
                default -> { }
            }
        }
    }

    ///
    /// Describes the given method the way a message points at it.
    ///
    private static String describe(ClassTree type, MethodTree method) {
        if (method.getName().contentEquals("<init>")) {
            return "constructor of " + type.getSimpleName();
        }

        return "method " + method.getName();
    }

    ///
    /// Records a breach if no doc comment ends on the line above the given declaration.
    ///
    /// A `// @formatter:off` guard between the comment and the declaration is skipped.
    ///
    private static void checkDeclaration(SourceFile file, Tree declaration, String name, Findings findings) {
        long line = file.line(file.start(declaration));
        String above = file.lineText(line - 1).strip();
        if (above.equals("// @formatter:off")) {
            above = file.lineText(line - 2).strip();
        }

        if (!above.equals("///")) {
            findings.report(DocPresenceRule.ID, file, declaration, name + " has no doc comment");
        }
    }
}
