package dev.satherov.conventions.javac.rule.expression;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;
import dev.satherov.conventions.javac.source.SourceSet;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

///
/// The rule that production code connects with a timeout.
///
/// `HttpClient.newHttpClient()` has no connect timeout. `HttpClient.Builder.build()` and
/// `WebSocket.Builder.buildAsync(..)` are reported when the chain of calls back to `newBuilder()` or
/// `newWebSocketBuilder()` sets no `connectTimeout(..)`. A builder held in a variable is skipped, since its chain is not
/// in one expression.
///
@RequiredArgsConstructor
public class UntimedConnectRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "untimed-connect";

    ///
    /// The HTTP client type of the JDK.
    ///
    private static final String CLIENT = "java.net.http.HttpClient";

    ///
    /// The builder type of the HTTP client.
    ///
    private static final String CLIENT_BUILDER = "java.net.http.HttpClient.Builder";

    ///
    /// The builder type of the WebSocket.
    ///
    private static final String SOCKET_BUILDER = "java.net.http.WebSocket.Builder";

    ///
    /// The javac trees the elements of the calls come from.
    ///
    private final Trees trees;

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return UntimedConnectRule.ID;
    }

    ///
    /// The analyzed phase, since it needs to know which method a call resolves to.
    ///
    @Override
    public Phase phase() {
        return Phase.ANALYZED;
    }

    ///
    /// Checks every call in the declared methods and field initializers of a main source type.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        if (file.options().set() != SourceSet.MAIN) {
            return;
        }

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
            /// Records a breach if the call creates a client or connects without a connect timeout.
            ///
            @Override
            public @Nullable Void visitMethodInvocation(MethodInvocationTree call, @Nullable Void unused) {
                TreePath callPath = this.getCurrentPath();
                if (UntimedConnectRule.this.trees.getElement(callPath) instanceof ExecutableElement method
                        && method.getEnclosingElement() instanceof TypeElement owner) {
                    String type = owner.getQualifiedName().toString();
                    String name = method.getSimpleName().toString();
                    if (type.equals(UntimedConnectRule.CLIENT) && name.equals("newHttpClient")) {
                        findings.report(UntimedConnectRule.ID, file, call, "HttpClient.newHttpClient() has no connect timeout; build the client with connectTimeout(..)");
                    } else if (type.equals(UntimedConnectRule.CLIENT_BUILDER) && name.equals("build") && UntimedConnectRule.this.lacksTimeout(callPath, "newBuilder")) {
                        findings.report(UntimedConnectRule.ID, file, call, "an HttpClient built without a connect timeout; call connectTimeout(..) on the builder");
                    } else if (type.equals(UntimedConnectRule.SOCKET_BUILDER) && name.equals("buildAsync") && UntimedConnectRule.this.lacksTimeout(callPath, "newWebSocketBuilder")) {
                        findings.report(UntimedConnectRule.ID, file, call, "a WebSocket built without a connect timeout; call connectTimeout(..) on the builder");
                    }
                }

                return super.visitMethodInvocation(call, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the chain of calls below the given call reaches the given factory of the HTTP client without a
    /// `connectTimeout(..)` on the way.
    ///
    /// @return `false` if the chain sets a timeout or starts at anything other than the factory, such as a variable.
    ///
    private boolean lacksTimeout(TreePath call, String factory) {
        TreePath current = UntimedConnectRule.receiverOf(call);
        while (current != null) {
            if (!(current.getLeaf() instanceof MethodInvocationTree)) {
                return false;
            }

            if (!(this.trees.getElement(current) instanceof ExecutableElement method)) {
                return false;
            }

            String name = method.getSimpleName().toString();
            if (name.equals("connectTimeout")) {
                return false;
            }

            if (name.equals(factory) && method.getEnclosingElement() instanceof TypeElement owner && owner.getQualifiedName().contentEquals(UntimedConnectRule.CLIENT)) {
                return true;
            }

            current = UntimedConnectRule.receiverOf(current);
        }

        return false;
    }

    ///
    /// Gets the path of the receiver of the given call without its parentheses, or `null` if the call has none.
    ///
    private static @Nullable TreePath receiverOf(TreePath call) {
        if (!(call.getLeaf() instanceof MethodInvocationTree invocation) || !(invocation.getMethodSelect() instanceof MemberSelectTree select)) {
            return null;
        }

        TreePath receiver = new TreePath(new TreePath(call, select), select.getExpression());
        while (receiver.getLeaf() instanceof ParenthesizedTree parenthesized) {
            ExpressionTree inner = parenthesized.getExpression();
            receiver = new TreePath(receiver, inner);
        }

        return receiver;
    }
}
