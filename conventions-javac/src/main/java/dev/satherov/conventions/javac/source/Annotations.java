package dev.satherov.conventions.javac.source;

import lombok.experimental.UtilityClass;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;

///
/// Reads annotations as they are written in the source, before any type is known.
///
@UtilityClass
public class Annotations {

    ///
    /// The simple name an annotation is written with: `Getter` for both `@Getter` and `@lombok.Getter`.
    ///
    /// @param annotation The annotation.
    ///
    /// @return The simple name.
    ///
    public static String simpleName(AnnotationTree annotation) {
        Tree type = annotation.getAnnotationType();
        return switch (type) {
            case IdentifierTree identifier -> identifier.getName().toString();
            case MemberSelectTree select -> select.getIdentifier().toString();
            default -> type.toString();
        };
    }

    ///
    /// Finds the first annotation among the given modifiers that is written with the given name.
    ///
    /// @param modifiers The modifiers of a declaration.
    /// @param name      The simple name, or a qualified tail such as `ApiStatus.Internal`.
    ///
    /// @return The annotation, or an empty optional if none is written with that name.
    ///
    public static Optional<AnnotationTree> find(ModifiersTree modifiers, String name) {
        return modifiers.getAnnotations()
                .stream()
                .filter(annotation -> Annotations.isNamed(annotation, name))
                .map(AnnotationTree.class::cast)
                .findFirst();
    }

    ///
    /// Checks if an annotation with the given name is among the given modifiers.
    ///
    /// @param modifiers The modifiers of a declaration.
    /// @param name      The simple name, or a qualified tail such as `ApiStatus.Internal`.
    ///
    /// @return `true` if such an annotation is written there.
    ///
    public static boolean has(ModifiersTree modifiers, String name) {
        return Annotations.find(modifiers, name).isPresent();
    }

    ///
    /// Gets the string literals the arguments of the given annotation hold, alone or in an array.
    ///
    /// @param annotation The annotation, such as a `@SuppressWarnings`.
    ///
    /// @return Any string literals, in the order they are written.
    ///
    public static @Unmodifiable List<LiteralTree> getAllStringsOf(AnnotationTree annotation) {
        return annotation.getArguments()
                .stream()
                .flatMap(argument -> Annotations.stringsOf(argument).stream())
                .toList();
    }

    ///
    /// Gets the string literals an annotation argument holds, alone or in an array.
    ///
    private static @Unmodifiable List<LiteralTree> stringsOf(ExpressionTree argument) {
        return switch (argument) {
            case AssignmentTree assignment -> Annotations.stringsOf(assignment.getExpression());
            case LiteralTree literal -> List.of(literal);
            case NewArrayTree array -> array.getInitializers()
                    .stream()
                    .filter(LiteralTree.class::isInstance)
                    .map(LiteralTree.class::cast)
                    .toList();
            default -> List.of();
        };
    }

    ///
    /// Checks if the given annotation is written with the given name.
    ///
    /// A name with a dot matches the written type or its tail, so `ApiStatus.Internal` matches
    /// `@ApiStatus.Internal` and `@org.jetbrains.annotations.ApiStatus.Internal`, but not `@Internal`.
    ///
    /// @param annotation The annotation.
    /// @param name       The simple name, or a qualified tail.
    ///
    /// @return `true` if the names match.
    ///
    public static boolean isNamed(AnnotationTree annotation, String name) {
        if (name.indexOf('.') < 0) {
            return Annotations.simpleName(annotation).equals(name);
        }

        String written = annotation.getAnnotationType().toString();
        return written.equals(name) || written.endsWith("." + name);
    }
}
