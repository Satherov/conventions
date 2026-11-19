package dev.satherov.conventions.javac.source;

import lombok.RequiredArgsConstructor;

import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

///
/// The supertypes of a type and the methods a method overrides, as javac resolved them.
///
@RequiredArgsConstructor
public final class Hierarchy {

    ///
    /// The element utilities that decide whether one method overrides another.
    ///
    private final Elements elements;

    ///
    /// The type utilities that walk the supertypes.
    ///
    private final Types types;

    ///
    /// Gets every method of a supertype that the given method overrides.
    ///
    /// @param method A method.
    ///
    /// @return Any overridden methods, nearest supertype first; none for a constructor or a static method.
    ///
    public @Unmodifiable List<ExecutableElement> getAllOverriddenBy(ExecutableElement method) {
        if (method.getKind() != ElementKind.METHOD || method.getModifiers().contains(Modifier.STATIC)) {
            return List.of();
        }

        TypeElement owner = (TypeElement) method.getEnclosingElement();
        List<ExecutableElement> overridden = new ArrayList<>();
        for (TypeElement supertype : this.getAllSupertypesOf(owner)) {
            for (ExecutableElement candidate : ElementFilter.methodsIn(supertype.getEnclosedElements())) {
                if (this.elements.overrides(method, candidate, owner)) {
                    overridden.add(candidate);
                }
            }
        }

        return List.copyOf(overridden);
    }

    ///
    /// Gets every class and interface above the given type, `Object` included.
    ///
    /// @param type A type.
    ///
    /// @return The supertypes, nearest first.
    ///
    public @Unmodifiable Set<TypeElement> getAllSupertypesOf(TypeElement type) {
        SequencedSet<TypeElement> found = new LinkedHashSet<>();
        Deque<TypeMirror> queue = new ArrayDeque<>(this.types.directSupertypes(type.asType()));
        while (!queue.isEmpty()) {
            if (this.types.asElement(queue.pop()) instanceof TypeElement supertype && found.add(supertype)) {
                queue.addAll(this.types.directSupertypes(supertype.asType()));
            }
        }

        return Collections.unmodifiableSequencedSet(found);
    }
}
