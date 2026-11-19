package dev.satherov.conventions.javac.rule.layout;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import java.util.Set;

///
/// The rule that the type suffix of a numeric literal is uppercase: `5_000L`, `1.0F`, `1.0D`.
///
public class NumericSuffixRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "numeric-suffix";

    ///
    /// The literal kinds that may carry a suffix.
    ///
    private static final Set<Tree.Kind> SUFFIXED = Set.of(
            Tree.Kind.LONG_LITERAL,
            Tree.Kind.FLOAT_LITERAL,
            Tree.Kind.DOUBLE_LITERAL
    );

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return NumericSuffixRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every numeric literal in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach if the literal ends in a lowercase suffix.
            ///
            @Override
            public @Nullable Void visitLiteral(LiteralTree literal, @Nullable Void unused) {
                String text = file.text(literal);
                if (NumericSuffixRule.SUFFIXED.contains(literal.getKind()) && !text.isEmpty() && "lfd".indexOf(text.charAt(text.length() - 1)) >= 0) {
                    findings.report(NumericSuffixRule.ID, file, literal, text + " has a lowercase suffix; write it uppercase");
                }

                return super.visitLiteral(literal, unused);
            }
        }.scan(path, null);
    }
}
