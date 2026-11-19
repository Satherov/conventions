package dev.satherov.conventions.javac.rule.expression;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

///
/// The rule that a lambda which returns its parameter is written `Function.identity()`.
///
public class IdentityLambdaRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "identity-lambda";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return IdentityLambdaRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every lambda in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach if the lambda takes one parameter and returns it as it is.
            ///
            @Override
            public @Nullable Void visitLambdaExpression(LambdaExpressionTree lambda, @Nullable Void unused) {
                if (
                        lambda.getParameters().size() == 1 &&
                        lambda.getBody() instanceof IdentifierTree body &&
                        body.getName().contentEquals(lambda.getParameters().getFirst().getName())
                ) {
                    findings.report(IdentityLambdaRule.ID, file, lambda, "a lambda that returns its parameter; use Function.identity()");
                }

                return super.visitLambdaExpression(lambda, unused);
            }
        }.scan(path, null);
    }
}
