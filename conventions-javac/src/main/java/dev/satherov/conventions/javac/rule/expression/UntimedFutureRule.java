package dev.satherov.conventions.javac.rule.expression;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;
import dev.satherov.conventions.javac.source.SourceSet;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

///
/// The rule that production code waits for a future with a timeout.
///
/// The rule covers `join()` on a `CompletableFuture` or `ForkJoinTask` and `get()` on a `Future`, which wait for as
/// long as the future takes. A wait that is bounded some other way is suppressed with the reason.
///
@RequiredArgsConstructor
public class UntimedFutureRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "untimed-future";

    ///
    /// The javac trees the elements of the calls come from.
    ///
    private final Trees trees;

    ///
    /// The element utilities that find the future types.
    ///
    private final Elements elements;

    ///
    /// The type utilities that decide whether a type is a future.
    ///
    private final Types types;

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return UntimedFutureRule.ID;
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

        Optional<TypeMirror> future = this.erasureOf("java.util.concurrent.Future");
        Optional<TypeMirror> completable = this.erasureOf("java.util.concurrent.CompletableFuture");
        Optional<TypeMirror> task = this.erasureOf("java.util.concurrent.ForkJoinTask");
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
            /// Records a breach if the call waits for a future without a timeout.
            ///
            @Override
            public @Nullable Void visitMethodInvocation(MethodInvocationTree call, @Nullable Void unused) {
                if (UntimedFutureRule.this.trees.getElement(this.getCurrentPath()) instanceof ExecutableElement method
                        && method.getParameters().isEmpty()
                        && method.getEnclosingElement() instanceof TypeElement owner) {
                    TypeMirror type = UntimedFutureRule.this.types.erasure(owner.asType());
                    boolean untimed = switch (method.getSimpleName().toString()) {
                        case "join" -> UntimedFutureRule.this.isSubtypeOfAny(type, Stream.of(completable, task));
                        case "get" -> UntimedFutureRule.this.isSubtypeOfAny(type, Stream.of(future));
                        default -> false;
                    };
                    if (untimed) {
                        findings.report(UntimedFutureRule.ID, file, call, owner.getSimpleName() + "." + method.getSimpleName() + "() waits without a timeout; wait with a timeout, or suppress with the reason the wait is bounded");
                    }
                }

                return super.visitMethodInvocation(call, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Gets the erased type of the class with the given name, or an empty optional if the class is not on the class path.
    ///
    private Optional<TypeMirror> erasureOf(String name) {
        return Optional.ofNullable(this.elements.getTypeElement(name))
                .map(element -> this.types.erasure(element.asType()));
    }

    ///
    /// Checks if the given type is a subtype of any of the given types.
    ///
    private boolean isSubtypeOfAny(TypeMirror type, Stream<Optional<TypeMirror>> supertypes) {
        return supertypes.flatMap(Optional::stream)
                .anyMatch(supertype -> this.types.isSubtype(type, supertype));
    }
}
