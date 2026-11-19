package dev.satherov.conventions.javac.rule.expression;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

///
/// The rule that a null check which maps a value goes through the mapping utilities, never a ternary.
///
/// `value == null ? fallback : map(value)` becomes `ObjectMapping.mapNonNullOrElse(value, ..., fallback)`, and a value
/// passed through unmapped becomes `Objects.requireNonNullElse(value, fallback)`. A null check whose branches do not
/// use the value, such as `name == null ? "none" : "some"`, picks between two values and stays a ternary.
///
public class NullTernaryRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "null-ternary";

    ///
    /// The comparisons a null check is written with.
    ///
    private static final Set<Tree.Kind> COMPARISONS = Set.of(
            Tree.Kind.EQUAL_TO,
            Tree.Kind.NOT_EQUAL_TO
    );

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return NullTernaryRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every ternary in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach if the ternary maps the value its condition checks for `null`.
            ///
            @Override
            public @Nullable Void visitConditionalExpression(ConditionalExpressionTree ternary, @Nullable Void unused) {
                NullTernaryRule.mappedValue(file, ternary).ifPresent(value -> findings.report(NullTernaryRule.ID, file, ternary, "a ternary that maps " + value + " when it is not null; use ObjectMapping.mapNonNullOrElse or Objects.requireNonNullElse"));
                return super.visitConditionalExpression(ternary, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Gets the value the ternary checks for `null`, if its condition is `x == null` or `x != null` and the branch taken
    /// when `x` is not null uses `x`.
    ///
    private static Optional<String> mappedValue(SourceFile file, ConditionalExpressionTree ternary) {
        ExpressionTree condition = ternary.getCondition();
        while (condition instanceof ParenthesizedTree parenthesized) {
            condition = parenthesized.getExpression();
        }

        if (!(condition instanceof BinaryTree binary) || !NullTernaryRule.COMPARISONS.contains(binary.getKind())) {
            return Optional.empty();
        }

        Optional<ExpressionTree> checked = NullTernaryRule.checkedOperand(binary);
        ExpressionTree present = binary.getKind() == Tree.Kind.EQUAL_TO ? ternary.getFalseExpression() : ternary.getTrueExpression();
        return checked.map(file::text)
                .filter(value -> NullTernaryRule.mentions(file.text(present), value));
    }

    ///
    /// Gets the operand compared with `null`, or an empty optional if neither side is the `null` literal.
    ///
    private static Optional<ExpressionTree> checkedOperand(BinaryTree binary) {
        if (binary.getRightOperand().getKind() == Tree.Kind.NULL_LITERAL) {
            return Optional.of(binary.getLeftOperand());
        }

        if (binary.getLeftOperand().getKind() == Tree.Kind.NULL_LITERAL) {
            return Optional.of(binary.getRightOperand());
        }

        return Optional.empty();
    }

    ///
    /// Checks if the given text uses the given value as a whole name, not as the tail of a longer one.
    ///
    private static boolean mentions(String text, String value) {
        return Pattern.compile("(?<![\\w.$])" + Pattern.quote(value) + "(?![\\w$])")
                .matcher(text)
                .find();
    }
}
