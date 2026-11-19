package dev.satherov.conventions.javac.rule.annotation;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.Hierarchy;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

///
/// The rule that every method which overrides or redeclares a method of a supertype carries `@Override`.
///
/// Only methods written in the source count; the ones javac or Lombok add are left out.
///
@RequiredArgsConstructor
public class OverrideRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "override";

    ///
    /// The javac trees the elements of the methods come from.
    ///
    private final Trees trees;

    ///
    /// The supertypes the overridden methods are looked up in.
    ///
    private final Hierarchy hierarchy;

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return OverrideRule.ID;
    }

    ///
    /// The analyzed phase, since overriding needs resolved supertypes.
    ///
    @Override
    public Phase phase() {
        return Phase.ANALYZED;
    }

    ///
    /// Checks every declared method of the type, its nested, local, and anonymous classes included.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records a breach if the method overrides something without saying so.
            ///
            @Override
            public @Nullable Void visitMethod(MethodTree method, @Nullable Void unused) {
                boolean annotated = Annotations.has(method.getModifiers(), "Override");
                if (!annotated && file.isDeclared(method)) {
                    Element element = OverrideRule.this.trees.getElement(this.getCurrentPath());
                    if (element instanceof ExecutableElement executable && !OverrideRule.this.hierarchy.getAllOverriddenBy(executable).isEmpty()) {
                        findings.report(OverrideRule.ID, file, method, "method " + method.getName() + " overrides a supertype method; add @Override");
                    }
                }

                return super.visitMethod(method, unused);
            }
        }.scan(path, null);
    }
}
