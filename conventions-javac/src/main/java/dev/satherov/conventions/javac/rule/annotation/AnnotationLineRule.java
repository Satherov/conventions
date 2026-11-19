package dev.satherov.conventions.javac.rule.annotation;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;

///
/// The rule that every annotation on a type, method, constructor, or field sits on a line of its own.
///
/// Type-use annotations such as `@Nullable` and `@Unmodifiable` sit inline instead.
/// On a method or a field, they go after the last modifier, directly before the type.
/// The annotations of parameters, record components, and locals are left out.
///
public class AnnotationLineRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "annotation-line";

    ///
    /// The simple names of the type-use annotations that sit inline.
    ///
    public static final Set<String> INLINE = Set.of(
            "Nullable",
            "NonNull",
            "UnknownNullability",
            "Range",
            "MagicConstant",
            "Unmodifiable",
            "UnmodifiableView"
    );

    ///
    /// The pattern of a modifier keyword.
    ///
    private static final Pattern MODIFIER = Pattern.compile(
            "\\b(public|protected|private|static|final|abstract|default|synchronized|native|strictfp|transient|volatile|sealed|non-sealed)\\b"
    );

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return AnnotationLineRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks the annotations of every type and member declaration in the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        //noinspection ParameterNameDiffersFromOverriddenParameter Full words replace the parameter names of the supertype.
        new TreePathScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Checks the annotations of the type and of its methods and fields.
            ///
            @Override
            public @Nullable Void visitClass(ClassTree type, @Nullable Void unused) {
                AnnotationLineRule.checkModifiers(file, type.getModifiers(), null, findings);
                for (Tree member : type.getMembers()) {
                    switch (member) {
                        case MethodTree method -> AnnotationLineRule.checkModifiers(file, method.getModifiers(), method.getReturnType(), findings);
                        case VariableTree field when !AnnotationLineRule.isComponent(type, field) -> AnnotationLineRule.checkModifiers(file, field.getModifiers(), field.getType(), findings);
                        default -> { }
                    }
                }

                return super.visitClass(type, unused);
            }
        }.scan(path, null);
    }

    ///
    /// Checks if the given field is a component of the given record, whose annotations sit inline in the header.
    ///
    private static boolean isComponent(ClassTree type, VariableTree field) {
        return type.getKind() == Tree.Kind.RECORD && !field.getModifiers().getFlags().contains(Modifier.STATIC);
    }

    ///
    /// Records a breach for every annotation among the given modifiers that shares its line with other code,
    /// and for every type-use annotation that does not sit directly before the given type.
    ///
    private static void checkModifiers(SourceFile file, ModifiersTree modifiers, @Nullable Tree type, Findings findings) {
        for (AnnotationTree annotation : modifiers.getAnnotations()) {
            if (AnnotationLineRule.INLINE.contains(Annotations.simpleName(annotation))) {
                AnnotationLineRule.checkPlacement(file, annotation, type, findings);
                continue;
            }

            long start = file.start(annotation);
            long end = file.end(annotation);
            if (start < 0 || end < 0) {
                continue;
            }

            long number = file.line(start);
            String line = file.lineText(number);
            long lineStart = file.unit().getLineMap().getStartPosition(number);
            String before = line.substring(0, (int) (start - lineStart));
            String after = line.substring((int) Math.min(line.length(), end - lineStart));
            if (!before.isBlank() || !(after.isBlank() || after.strip().startsWith("//"))) {
                findings.report(AnnotationLineRule.ID, file, annotation, "@" + Annotations.simpleName(annotation) + " shares its line; every annotation goes on its own line");
            }
        }
    }

    ///
    /// Records a breach if the given type-use annotation is followed by a modifier or
    /// a line break before the given type.
    ///
    private static void checkPlacement(SourceFile file, AnnotationTree annotation, @Nullable Tree type, Findings findings) {
        if (type == null) {
            return;
        }

        long end = file.end(annotation);
        long start = file.start(type);
        if (end < 0 || start < end) {
            return;
        }

        String between = file.text(end, start);
        if (between.contains("\n") || AnnotationLineRule.MODIFIER.matcher(between).find()) {
            findings.report(AnnotationLineRule.ID, file, annotation, "@" + Annotations.simpleName(annotation) + " goes after the last modifier, directly before the type");
        }
    }
}
