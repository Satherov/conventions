package dev.satherov.conventions.javac.rule.expression;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

///
/// The rule that the function a map computes an entry with writes to no field other than that map.
///
/// A concurrent map holds a lock while the function of `compute`, `computeIfAbsent`, `computeIfPresent`, or `merge` runs,
/// and a write to another map, cache, or store in it can take that one's lock in the opposite order, or run a listener
/// that comes back to the first map. A write is a call named `put`, `putIfAbsent`, `remove`, `replace`, `compute*`,
/// `merge`, `clear`, `putAll`, `invalidate`, or `invalidateAll` on a field, whatever the type of the field. A private
/// method of the same file that the function calls is checked as well, one call deep. A variable the function declares
/// or takes as a parameter is its own, and writes to it are left alone.
///
@RequiredArgsConstructor
public class NestedComputeRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "nested-compute";

    ///
    /// The methods of a map that run a function under the lock of the entry.
    ///
    private static final Set<String> COMPUTES = Set.of(
            "compute",
            "computeIfAbsent",
            "computeIfPresent",
            "merge"
    );

    ///
    /// The names of the methods that write to a map or a cache.
    ///
    private static final Set<String> WRITES = Set.of(
            "put",
            "putIfAbsent",
            "remove",
            "replace",
            "compute",
            "computeIfAbsent",
            "computeIfPresent",
            "merge",
            "clear",
            "putAll",
            "invalidate",
            "invalidateAll"
    );

    ///
    /// The javac trees the elements and types of the calls come from.
    ///
    private final Trees trees;

    ///
    /// The element utilities that find `java.util.Map`.
    ///
    private final Elements elements;

    ///
    /// The type utilities that decide whether the owner of a compute method is a map.
    ///
    private final Types types;

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return NestedComputeRule.ID;
    }

    ///
    /// The analyzed phase, since it needs to know what the receivers resolve to.
    ///
    @Override
    public Phase phase() {
        return Phase.ANALYZED;
    }

    ///
    /// Checks the function of every compute call in the declared methods and field initializers of the type.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        TypeElement map = this.elements.getTypeElement("java.util.Map");
        if (map == null) {
            return;
        }

        TypeMirror mapType = this.types.erasure(map.asType());
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
            /// Checks the lambda arguments of the call if it computes an entry of a map.
            ///
            @Override
            public @Nullable Void visitMethodInvocation(MethodInvocationTree call, @Nullable Void unused) {
                TreePath callPath = this.getCurrentPath();
                if (call.getMethodSelect() instanceof MemberSelectTree select
                        && NestedComputeRule.COMPUTES.contains(select.getIdentifier().toString())
                        && NestedComputeRule.this.isMapMethod(callPath, mapType)) {
                    TreePath outer = new TreePath(new TreePath(callPath, select), select.getExpression());
                    for (ExpressionTree argument : call.getArguments()) {
                        if (argument instanceof LambdaExpressionTree lambda) {
                            NestedComputeRule.this.checkFunction(file, new TreePath(callPath, lambda), outer, select.getIdentifier().toString(), findings);
                        }
                    }
                }

                return super.visitMethodInvocation(call, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Records a breach for every write in the given function to a field other than the outer map, following the
    /// private methods of the file it calls one level deep.
    ///
    private void checkFunction(SourceFile file, TreePath function, TreePath outer, String compute, Findings findings) {
        String outerText = file.text(outer.getLeaf());
        this.writesIn(function, outer, true).forEach(write -> findings.report(
                NestedComputeRule.ID,
                file,
                write.call(),
                write.describe(file) + " inside the " + compute + " of " + outerText + "; write after the " + compute + " returns, since the lock of the entry is held"
        ));
    }

    ///
    /// Collects the writes in the given body to a field other than the outer map, and the calls to private
    /// methods of the file that write if the given flag is set.
    ///
    private @Unmodifiable List<Write> writesIn(TreePath body, TreePath outer, boolean followPrivate) {
        Set<Element> own = this.declaredIn(body);
        Optional<Element> outerElement = Optional.ofNullable(this.trees.getElement(outer));
        List<Write> writes = new ArrayList<>();
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Notes the call if it writes to another field, or calls a private method that does.
            ///
            @Override
            public @Nullable Void visitMethodInvocation(MethodInvocationTree call, @Nullable Void unused) {
                TreePath callPath = this.getCurrentPath();
                if (NestedComputeRule.this.trees.getElement(callPath) instanceof ExecutableElement method) {
                    if (call.getMethodSelect() instanceof MemberSelectTree select) {
                        TreePath receiver = new TreePath(new TreePath(callPath, select), select.getExpression());
                        if (NestedComputeRule.this.isWrite(method, receiver)
                                && !NestedComputeRule.this.isSameMap(receiver, outer, outerElement)
                                && !NestedComputeRule.this.isOwn(receiver, own)) {
                            writes.add(new Write(call, method, null));
                        }
                    }

                    if (followPrivate && method.getModifiers().contains(Modifier.PRIVATE)) {
                        TreePath helper = NestedComputeRule.this.trees.getPath(method);
                        if (helper != null && helper.getCompilationUnit() == callPath.getCompilationUnit()
                                && helper.getLeaf() instanceof MethodTree helperTree && helperTree.getBody() != null) {
                            NestedComputeRule.this.writesIn(new TreePath(helper, helperTree.getBody()), outer, false)
                                    .stream()
                                    .findFirst()
                                    .ifPresent(inner -> writes.add(new Write(call, method, inner)));
                        }
                    }
                }

                return super.visitMethodInvocation(call, unused);
            }
        }.scan(body, null);
        return List.copyOf(writes);
    }

    ///
    /// Checks if the method of the given call is a compute method of a map.
    ///
    private boolean isMapMethod(TreePath call, TypeMirror mapType) {
        return this.trees.getElement(call) instanceof ExecutableElement method
                && method.getEnclosingElement() instanceof TypeElement owner
                && this.types.isSubtype(this.types.erasure(owner.asType()), mapType);
    }

    ///
    /// Checks if the given method is named like a write and the given receiver is a field.
    ///
    private boolean isWrite(ExecutableElement method, TreePath receiver) {
        return NestedComputeRule.WRITES.contains(method.getSimpleName().toString())
                && this.trees.getElement(receiver) instanceof Element element
                && element.getKind() == ElementKind.FIELD;
    }

    ///
    /// Checks if the given receiver is the map the entry is computed in, by its element or else by its text.
    ///
    private boolean isSameMap(TreePath receiver, TreePath outer, Optional<Element> outerElement) {
        Element element = this.trees.getElement(receiver);
        if (element != null && outerElement.isPresent()) {
            return element.equals(outerElement.get());
        }

        return receiver.getLeaf().toString().equals(outer.getLeaf().toString());
    }

    ///
    /// Checks if the given receiver starts at a variable the body declares or takes as a parameter.
    ///
    private boolean isOwn(TreePath receiver, Set<Element> own) {
        TreePath root = receiver;
        while (true) {
            Tree leaf = root.getLeaf();
            ExpressionTree next = switch (leaf) {
                case MemberSelectTree select -> select.getExpression();
                case MethodInvocationTree call -> call.getMethodSelect();
                case ParenthesizedTree parenthesized -> parenthesized.getExpression();
                case TypeCastTree cast -> cast.getExpression();
                default -> null;
            };
            if (next == null) {
                break;
            }

            root = new TreePath(root, next);
        }

        return root.getLeaf() instanceof IdentifierTree && own.contains(this.trees.getElement(root));
    }

    ///
    /// Collects the variables the given body declares, the parameters of a lambda and its enclosing method included.
    ///
    private @Unmodifiable Set<Element> declaredIn(TreePath body) {
        Set<Element> declared = new HashSet<>();
        TreePath scope = body.getLeaf() instanceof LambdaExpressionTree ? body : body.getParentPath();
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Notes a local variable or parameter.
            ///
            @Override
            public @Nullable Void visitVariable(VariableTree variable, @Nullable Void unused) {
                Element element = NestedComputeRule.this.trees.getElement(this.getCurrentPath());
                if (element != null && element.getKind() != ElementKind.FIELD) {
                    declared.add(element);
                }

                return super.visitVariable(variable, unused);
            }
        }.scan(scope, null);
        return Set.copyOf(declared);
    }

    ///
    /// A write inside the function of a compute call.
    ///
    /// @param call   The call in the function, which is the write itself or a call to a private method.
    /// @param method The method the call resolves to.
    /// @param inner  The write inside the private method, or `null` if the call writes itself.
    ///
    private record Write(MethodInvocationTree call, ExecutableElement method, @Nullable Write inner) {

        ///
        /// Describes the write for the message of a finding.
        ///
        private String describe(SourceFile file) {
            if (this.inner == null) {
                return this.method.getSimpleName() + " on " + Write.receiverOf(file, this.call);
            }

            return this.method.getSimpleName() + "(..), which calls " + this.inner.describe(file) + ",";
        }

        ///
        /// Gets the source text of the receiver of the given call.
        ///
        private static String receiverOf(SourceFile file, MethodInvocationTree call) {
            if (call.getMethodSelect() instanceof MemberSelectTree select) {
                return file.text(select.getExpression());
            }

            return "this";
        }
    }
}
