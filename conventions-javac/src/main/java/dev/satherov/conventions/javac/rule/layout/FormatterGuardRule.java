package dev.satherov.conventions.javac.rule.layout;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import java.util.List;

///
/// The rule that a type declaration with two or more entries in its `extends`, `implements`, or `permits` clause sits
/// between `// @formatter:off` and `// @formatter:on`.
///
/// The guard opens on the line above the declaration, below its annotations, and closes on the line after the `{`
/// that opens the body.
///
public class FormatterGuardRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "formatter-guard";

    ///
    /// The line that opens the guard.
    ///
    public static final String OFF = "// @formatter:off";

    ///
    /// The line that closes the guard.
    ///
    public static final String ON = "// @formatter:on";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return FormatterGuardRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every type declaration in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Checks the guard of the type if one of its clauses holds two or more entries.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                if (type.getImplementsClause().size() >= 2 || type.getPermitsClause().size() >= 2) {
                    FormatterGuardRule.checkGuard(file, type, findings);
                }

                return super.visitClass(type, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Records a breach if the guard lines are not where they belong around the given type.
    ///
    private static void checkGuard(SourceFile file, ClassTree type, Findings findings) {
        List<? extends AnnotationTree> annotations = type.getModifiers().getAnnotations();
        long guard = annotations.isEmpty() ? file.line(file.start(type)) - 1 : file.line(file.end(annotations.getLast())) + 1;
        if (!file.lineText(guard).strip().equals(FormatterGuardRule.OFF)) {
            findings.report(FormatterGuardRule.ID, file, type, type.getSimpleName() + " has two or more supertypes or permitted types; open the declaration with " + FormatterGuardRule.OFF);
        }

        long last = file.line(file.end(type));
        for (long line = guard + 1; line <= last; line++) {
            String content = file.lineText(line).strip();
            if (content.equals("{") || content.equals("{ }")) {
                if (!file.lineText(line + 1).strip().equals(FormatterGuardRule.ON)) {
                    findings.report(FormatterGuardRule.ID, file, type, type.getSimpleName() + " closes its guard with " + FormatterGuardRule.ON + " on the line after the {");
                }

                return;
            }
        }

        findings.report(FormatterGuardRule.ID, file, type, type.getSimpleName() + " opens its body with { on a line of its own, followed by " + FormatterGuardRule.ON);
    }
}
