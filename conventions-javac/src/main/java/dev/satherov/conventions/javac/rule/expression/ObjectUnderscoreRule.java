package dev.satherov.conventions.javac.rule.expression;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

///
/// The rule that a dropped result is never parked in `Object _`.
///
/// A method whose result callers may drop is marked `@CanIgnoreReturnValue`; a call site where it matters takes a
/// `//noinspection ResultOfMethodCallIgnored <reason>` line.
///
public class ObjectUnderscoreRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "object-underscore";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return ObjectUnderscoreRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every variable in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach if the variable is an unnamed `Object` that holds a value.
            ///
            @Override
            public @Nullable Void visitVariable(VariableTree variable, @Nullable Void unused) {
                if (
                        variable.getName().contentEquals("_") &&
                        variable.getType() instanceof IdentifierTree type &&
                        type.getName().contentEquals("Object") &&
                        variable.getInitializer() != null
                ) {
                    findings.report(ObjectUnderscoreRule.ID, file, variable, "Object _ = ...; mark the method @CanIgnoreReturnValue or add //noinspection ResultOfMethodCallIgnored <reason>");
                }

                return super.visitVariable(variable, unused);
            }
        }.scan(path, null);
    }
}
