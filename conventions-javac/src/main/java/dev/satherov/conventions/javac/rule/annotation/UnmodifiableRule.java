package dev.satherov.conventions.javac.rule.annotation;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.Hierarchy;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

///
/// The rule that every method returning a collection or a map marks it `@Unmodifiable` or `@UnmodifiableView`,
/// private ones included, and so does every collection component of a record.
///
/// The marks are type-use annotations, which the class file keeps where no bytecode check reads them, so javac checks
/// them. Left out are the methods javac or Lombok add, and overrides of a method declared outside the project, whose
/// signature the project does not own.
///
@RequiredArgsConstructor
public class UnmodifiableRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "unmodifiable";

    ///
    /// The simple names of the annotations that mark a collection as one the caller cannot change.
    ///
    public static final Set<String> MARKS = Set.of(
            "Unmodifiable",
            "UnmodifiableView"
    );

    ///
    /// The types whose subtypes count as collections.
    ///
    private static final List<String> COLLECTIONS = List.of(
            "java.util.Collection",
            "java.util.Map"
    );

    ///
    /// The javac trees the elements of the methods come from.
    ///
    private final Trees trees;

    ///
    /// The element utilities the collection types are looked up with.
    ///
    private final Elements elements;

    ///
    /// The type utilities that decide whether a type is a collection.
    ///
    private final Types types;

    ///
    /// The supertypes the overridden methods are looked up in.
    ///
    private final Hierarchy hierarchy;

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return UnmodifiableRule.ID;
    }

    ///
    /// The analyzed phase, since it needs to know what a return type resolves to.
    ///
    @Override
    public Phase phase() {
        return Phase.ANALYZED;
    }

    ///
    /// Checks every declared method and every record component of the type and its nested types.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Checks the components of a record.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                if (type.getKind() == Tree.Kind.RECORD) {
                    UnmodifiableRule.this.checkComponents(file, type, findings);
                }

                return super.visitClass(type, unused);
            }

            ///
            /// Checks a declared method.
            ///
            @Override
            public @Nullable Void visitMethod(MethodTree method, @Nullable Void unused) {
                if (!file.isDeclared(method)) {
                    return null;
                }

                Element element = UnmodifiableRule.this.trees.getElement(this.getCurrentPath());
                if (element instanceof ExecutableElement executable && UnmodifiableRule.this.isUnmarked(file, method, executable)) {
                    findings.report(UnmodifiableRule.ID, file, method, "method " + method.getName() + " returns a collection without @Unmodifiable or @UnmodifiableView");
                }

                return super.visitMethod(method, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Records a breach for every collection component of the given record that carries no mark.
    ///
    private void checkComponents(SourceFile file, ClassTree type, Findings findings) {
        for (Tree member : type.getMembers()) {
            if (member instanceof VariableTree component && !component.getModifiers().getFlags().contains(Modifier.STATIC)) {
                Element element = this.trees.getElement(this.trees.getPath(file.unit(), component));
                if (element != null && this.isCollection(element.asType()) && !UnmodifiableRule.isMarked(component.getModifiers(), component.getType())) {
                    findings.report(UnmodifiableRule.ID, file, component, "component " + component.getName() + " is a collection without @Unmodifiable or @UnmodifiableView");
                }
            }
        }
    }

    ///
    /// Checks if the given method returns a collection, carries no mark,
    /// and does not override a method from outside the project.
    ///
    private boolean isUnmarked(SourceFile file, MethodTree method, ExecutableElement executable) {
        if (!this.isCollection(executable.getReturnType()) || UnmodifiableRule.isMarked(method.getModifiers(), method.getReturnType())) {
            return false;
        }

        return this.hierarchy.getAllOverriddenBy(executable)
                .stream()
                .noneMatch(overridden -> UnmodifiableRule.isForeign(file, overridden));
    }

    ///
    /// Checks if the given type is a subtype of `Collection` or `Map`.
    ///
    private boolean isCollection(TypeMirror type) {
        TypeMirror erased = this.types.erasure(type);
        for (String name : UnmodifiableRule.COLLECTIONS) {
            TypeElement collection = this.elements.getTypeElement(name);
            if (collection != null && this.types.isSubtype(erased, this.types.erasure(collection.asType()))) {
                return true;
            }
        }

        return false;
    }

    ///
    /// Checks if a mark sits among the given modifiers or on the given type.
    ///
    private static boolean isMarked(ModifiersTree modifiers, @Nullable Tree type) {
        List<AnnotationTree> annotations = new ArrayList<>(modifiers.getAnnotations());
        if (type instanceof AnnotatedTypeTree annotated) {
            annotations.addAll(annotated.getAnnotations());
        }

        return annotations.stream().anyMatch(annotation -> UnmodifiableRule.MARKS.contains(Annotations.simpleName(annotation)));
    }

    ///
    /// Checks if the given method is declared outside the project: outside its root package, or by another vendor
    /// where no root package was given.
    ///
    private static boolean isForeign(SourceFile file, ExecutableElement method) {
        String owner = ((TypeElement) method.getEnclosingElement()).getQualifiedName().toString();
        String root = file.options().root();
        if (!root.isEmpty()) {
            return !owner.startsWith(root + ".");
        }

        String own = Objects.toString(file.unit().getPackageName(), "");
        return !UnmodifiableRule.vendorOf(owner).equals(UnmodifiableRule.vendorOf(own));
    }

    ///
    /// Gets the first two parts of the given name.
    ///
    private static String vendorOf(String name) {
        String[] parts = name.split("\\.");
        if (parts.length < 2) {
            return name;
        }

        return parts[0] + "." + parts[1];
    }
}
