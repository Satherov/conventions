package dev.satherov.conventions.javac.rule.declaration;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.TypeParameterTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

///
/// The rule that a type parameter is a capitalized word, such as `VALUE` or `SELF`, never a single letter.
///
public class TypeParameterRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "type-parameter";

    ///
    /// The form a type parameter name takes.
    ///
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Z0-9_]+");

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return TypeParameterRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every type parameter of a type or method in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach if the name is not a capitalized word.
            ///
            @Override
            public @Nullable Void visitTypeParameter(TypeParameterTree parameter, @Nullable Void unused) {
                if (!TypeParameterRule.NAME.matcher(parameter.getName()).matches()) {
                    findings.report(TypeParameterRule.ID, file, parameter, "type parameter " + parameter.getName() + "; name it with a capitalized word such as VALUE");
                }

                return super.visitTypeParameter(parameter, unused);
            }
        }.scan(path, null);
    }
}
