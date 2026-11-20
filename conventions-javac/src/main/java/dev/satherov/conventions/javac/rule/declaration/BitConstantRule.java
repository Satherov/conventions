package dev.satherov.conventions.javac.rule.declaration;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;

///
/// The rule that a set of flags is a typed set of an enum, and its bits are no `int` or `long` constants.
///
/// A constant is a bit if it is a `static final` `int` or `long` outside an enum and its initializer is `1 << n`,
/// `1L << n`, or an `|` of such shifts and bit constants. It is reported once the top-level type uses it in a statement
/// that applies `&`, `|`, `^`, `~`, `&=`, `|=` or `^=`.
///
@RequiredArgsConstructor
public class BitConstantRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "bit-constant";

    ///
    /// The operators that combine or mask bits.
    ///
    private static final Set<Tree.Kind> BITWISE = Set.of(
            Tree.Kind.AND,
            Tree.Kind.OR,
            Tree.Kind.XOR,
            Tree.Kind.BITWISE_COMPLEMENT,
            Tree.Kind.AND_ASSIGNMENT,
            Tree.Kind.OR_ASSIGNMENT,
            Tree.Kind.XOR_ASSIGNMENT
    );

    ///
    /// The javac trees the elements of the fields and their uses come from.
    ///
    private final Trees trees;

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return BitConstantRule.ID;
    }

    ///
    /// The analyzed phase, since it needs to know which field a name resolves to.
    ///
    @Override
    public Phase phase() {
        return Phase.ANALYZED;
    }

    ///
    /// Checks every bit constant of the type and its nested types against the statements that use it.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        Map<Element, TreePath> constants = this.collectConstants(path);
        Map<Element, Boolean> bits = new HashMap<>();
        Set<Element> masked = new HashSet<>();
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Notes the constant a name resolves to if the statement around it applies a bitwise operator.
            ///
            @Override
            public @Nullable Void visitIdentifier(IdentifierTree identifier, @Nullable Void unused) {
                this.note();
                return super.visitIdentifier(identifier, unused);
            }

            ///
            /// Notes the constant a qualified name resolves to if the statement around it applies a bitwise operator.
            ///
            @Override
            public @Nullable Void visitMemberSelect(MemberSelectTree select, @Nullable Void unused) {
                this.note();
                return super.visitMemberSelect(select, unused);
            }

            ///
            /// Notes the constant of the current name if the statement around it applies a bitwise operator.
            ///
            private void note() {
                Element element = BitConstantRule.this.trees.getElement(this.getCurrentPath());
                if (element != null && constants.containsKey(element) && BitConstantRule.isInBitwiseStatement(this.getCurrentPath())) {
                    masked.add(element);
                }
            }
        }.scan(path, null);
        for (Map.Entry<Element, TreePath> constant : constants.entrySet()) {
            if (masked.contains(constant.getKey()) && this.isBit(constant.getKey(), constants, bits, new HashSet<>())) {
                findings.report(BitConstantRule.ID, file, constant.getValue().getLeaf(), constant.getKey().getSimpleName() + " is a bit flag; model the flags as a typed set of an enum, such as an EnumSet");
            }
        }
    }

    ///
    /// Checks if the statement or field around the given name applies a bitwise operator anywhere in it.
    ///
    /// A lambda or a switch expression belongs to the statement it is written in, so the statements inside it are
    /// passed over.
    ///
    private static boolean isInBitwiseStatement(TreePath path) {
        Tree statement = null;
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            Tree tree = current.getLeaf();
            if (tree instanceof ClassTree || tree instanceof MethodTree) {
                break;
            }

            if (tree instanceof ExpressionTree) {
                statement = null;
            } else if (statement == null && tree instanceof StatementTree && !(tree instanceof CaseTree)) {
                statement = tree;
            }
        }

        return statement != null && BitConstantRule.appliesBitwise(statement);
    }

    ///
    /// Checks if the given tree holds a bitwise operator.
    ///
    private static boolean appliesBitwise(Tree tree) {
        Boolean found = new TreeScanner<Boolean, @Nullable Void>() {

            ///
            /// Checks the node itself before its children.
            ///
            @Override
            public Boolean scan(@Nullable Tree node, @Nullable Void unused) {
                if (node != null && BitConstantRule.BITWISE.contains(node.getKind())) {
                    return true;
                }

                return Boolean.TRUE.equals(super.scan(node, unused));
            }

            ///
            /// Joins the answers of the children.
            ///
            @Override
            public Boolean reduce(@Nullable Boolean first, @Nullable Boolean second) {
                return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second);
            }
        }.scan(tree, null);
        return Boolean.TRUE.equals(found);
    }

    ///
    /// Collects every `static final` `int` or `long` field with an initializer that the type and its nested types outside
    /// an enum declare, with its path.
    ///
    private @UnmodifiableView Map<Element, TreePath> collectConstants(TreePath path) {
        Map<Element, TreePath> constants = new LinkedHashMap<>();
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Notes the fields of the type that are candidates.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                if (type.getKind() != Tree.Kind.ENUM) {
                    for (Tree member : type.getMembers()) {
                        if (member instanceof VariableTree field && field.getInitializer() != null) {
                            TreePath fieldPath = new TreePath(this.getCurrentPath(), field);
                            Element element = BitConstantRule.this.trees.getElement(fieldPath);
                            if (element != null && BitConstantRule.isIntegralConstant(element)) {
                                constants.put(element, fieldPath);
                            }
                        }
                    }
                }

                return super.visitClass(type, unused);
            }
        }.scan(path, null);
        return Collections.unmodifiableMap(constants);
    }

    ///
    /// Checks if the given field is `static final` and an `int` or a `long`.
    ///
    private static boolean isIntegralConstant(Element element) {
        TypeKind kind = element.asType().getKind();
        return element.getModifiers().contains(Modifier.STATIC)
                && element.getModifiers().contains(Modifier.FINAL)
                && (kind == TypeKind.INT || kind == TypeKind.LONG);
    }

    ///
    /// Checks if the given constant is initialized with a bit, remembering every answer and refusing a cycle.
    ///
    private boolean isBit(Element constant, Map<Element, TreePath> constants, Map<Element, Boolean> bits, Set<Element> visiting) {
        Boolean known = bits.get(constant);
        if (known != null) {
            return known;
        }

        if (!visiting.add(constant)) {
            return false;
        }

        TreePath field = constants.get(constant);
        ExpressionTree initializer = ((VariableTree) field.getLeaf()).getInitializer();
        boolean bit = initializer != null && this.isBitExpression(new TreePath(field, initializer), constants, bits, visiting);
        bits.put(constant, bit);
        return bit;
    }

    ///
    /// Checks if the given expression is `1 << n`, `1L << n`, a bit constant, or an `|` of those.
    ///
    private boolean isBitExpression(TreePath path, Map<Element, TreePath> constants, Map<Element, Boolean> bits, Set<Element> visiting) {
        return switch (path.getLeaf()) {
            case ParenthesizedTree parenthesized -> this.isBitExpression(new TreePath(path, parenthesized.getExpression()), constants, bits, visiting);
            case BinaryTree binary when binary.getKind() == Tree.Kind.LEFT_SHIFT -> BitConstantRule.isOne(binary.getLeftOperand());
            case BinaryTree binary when binary.getKind() == Tree.Kind.OR -> this.isBitExpression(new TreePath(path, binary.getLeftOperand()), constants, bits, visiting)
                    && this.isBitExpression(new TreePath(path, binary.getRightOperand()), constants, bits, visiting);
            case IdentifierTree identifier -> this.isBitReference(path, constants, bits, visiting);
            case MemberSelectTree select -> this.isBitReference(path, constants, bits, visiting);
            default -> false;
        };
    }

    ///
    /// Checks if the given name resolves to a bit constant of the same top-level type.
    ///
    private boolean isBitReference(TreePath name, Map<Element, TreePath> constants, Map<Element, Boolean> bits, Set<Element> visiting) {
        Element element = this.trees.getElement(name);
        return element != null && constants.containsKey(element) && this.isBit(element, constants, bits, visiting);
    }

    ///
    /// Checks if the given operand is the literal `1` or `1L`.
    ///
    private static boolean isOne(ExpressionTree operand) {
        return operand instanceof LiteralTree literal && literal.getValue() instanceof Number number && number.longValue() == 1L;
    }
}
