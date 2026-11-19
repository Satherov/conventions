package dev.satherov.conventions.javac.rule.expression;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

///
/// The rule that text is formatted and case-converted with an explicit locale.
///
/// `String.format(String, Object...)`, `String#formatted`, and the no-argument `toUpperCase` and `toLowerCase` use the
/// default locale of the machine, so their output changes with it.
///
@RequiredArgsConstructor
public class LocaleRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "locale";

    ///
    /// The javac trees the elements of the calls come from.
    ///
    private final Trees trees;

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return LocaleRule.ID;
    }

    ///
    /// The analyzed phase, since it needs to know which method a call resolves to.
    ///
    @Override
    public Phase phase() {
        return Phase.ANALYZED;
    }

    ///
    /// Checks every call in the declared methods and field initializers of the type.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
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
            /// Records a breach if the call formats or converts text with the default locale.
            ///
            @Override
            public @Nullable Void visitMethodInvocation(MethodInvocationTree call, @Nullable Void unused) {
                Element element = LocaleRule.this.trees.getElement(this.getCurrentPath());
                if (element instanceof ExecutableElement method && LocaleRule.usesDefaultLocale(method)) {
                    findings.report(LocaleRule.ID, file, call, "String." + method.getSimpleName() + " with the default locale; pass a Locale, such as Locale.ROOT");
                }

                return super.visitMethodInvocation(call, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the given method is one of the `String` methods that read the default locale.
    ///
    private static boolean usesDefaultLocale(ExecutableElement method) {
        if (!(method.getEnclosingElement() instanceof TypeElement owner) || !owner.getQualifiedName().contentEquals("java.lang.String")) {
            return false;
        }

        return switch (method.getSimpleName().toString()) {
            case "format" -> method.getParameters()
                    .getFirst()
                    .asType()
                    .toString()
                    .equals("java.lang.String");
            case "formatted" -> true;
            case "toUpperCase", "toLowerCase" -> method.getParameters().isEmpty();
            default -> false;
        };
    }
}
