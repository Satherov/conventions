package dev.satherov.conventions.javac.source;

import lombok.Getter;
import lombok.experimental.Accessors;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.tools.JavaFileObject;

///
/// A compilation unit together with its source text, the way every rule reads it.
///
@Accessors(fluent = true)
public final class SourceFile {

    ///
    /// The compilation unit as javac parsed it.
    ///
    @Getter
    private final CompilationUnitTree unit;

    ///
    /// The javac trees the unit belongs to.
    ///
    @Getter
    private final Trees trees;

    ///
    /// The options the plugin runs with.
    ///
    @Getter
    private final Options options;

    ///
    /// Who wrote the unit, which decides the rules it is held to.
    ///
    @Getter
    private final Origin origin;

    ///
    /// The source text of the unit.
    ///
    private final String text;

    ///
    /// The lines of the source text, in order.
    ///
    private final List<Line> lines;

    ///
    /// The start and end positions of every string literal and text block, in pairs.
    ///
    private final List<Long> literals;

    ///
    /// The methods and constructors the source declares, before javac or Lombok add their own.
    ///
    private final Set<Tree> declared = Collections.newSetFromMap(new IdentityHashMap<>());

    ///
    /// Reads the source text of the given unit and finds its literals and declared methods.
    ///
    /// Must be called once the unit is parsed and before annotation processing, so that the methods Lombok adds
    /// are not counted as declared.
    ///
    /// @param unit    The compilation unit.
    /// @param trees   The javac trees the unit belongs to.
    /// @param options The options the plugin runs with.
    ///
    /// @throws UncheckedIOException If the source text cannot be read.
    ///
    public SourceFile(CompilationUnitTree unit, Trees trees, Options options) {
        this.unit = unit;
        this.trees = trees;
        this.options = options;

        this.origin = SourceFile.originOf(unit, options);
        this.text = SourceFile.read(unit);
        this.lines = SourceFile.split(this.text);
        this.literals = new ArrayList<>();
        new TreeScanner<@Nullable Void, @Nullable Void>() {

            ///
            /// Records the range of a string literal or text block.
            ///
            @Override
            public @Nullable Void visitLiteral(LiteralTree node, @Nullable Void unused) {
                if (node.getKind() == Tree.Kind.STRING_LITERAL) {
                    SourceFile.this.literals.add(SourceFile.this.start(node));
                    SourceFile.this.literals.add(SourceFile.this.end(node));
                }

                return null;
            }

            ///
            /// Records a method or constructor the source declares.
            ///
            @Override
            public @Nullable Void visitMethod(MethodTree node, @Nullable Void unused) {
                SourceFile.this.declared.add(node);
                return super.visitMethod(node, unused);
            }
        }.scan(unit, null);
    }

    ///
    /// Decides who wrote the given unit from the directories the options name.
    ///
    private static Origin originOf(CompilationUnitTree unit, Options options) {
        Optional<Path> path = SourceFile.pathOf(unit);
        if (path.isEmpty()) {
            return Origin.WRITTEN;
        }

        if (options.skipped().stream().anyMatch(path.get()::startsWith)) {
            return Origin.SKIPPED;
        }

        if (options.generated().stream().anyMatch(path.get()::startsWith)) {
            return Origin.GENERATED;
        }

        return Origin.WRITTEN;
    }

    ///
    /// Gets the file of the given unit, or an empty optional if the unit does not live on disk.
    ///
    private static Optional<Path> pathOf(CompilationUnitTree unit) {
        URI uri = unit.getSourceFile().toUri();
        if (!"file".equals(uri.getScheme())) {
            return Optional.empty();
        }

        return Optional.of(Path.of(uri).toAbsolutePath().normalize());
    }

    ///
    /// Reads the source text of the given unit.
    ///
    private static String read(CompilationUnitTree unit) {
        try {
            return unit.getSourceFile().getCharContent(true).toString();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + unit.getSourceFile().getName(), exception);
        }
    }

    ///
    /// Splits the given text into its lines, keeping the position each one starts at.
    ///
    private static @Unmodifiable List<Line> split(String text) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf('\n', start);
            int stop = end < 0 ? text.length() : end;
            String content = text.substring(start, stop);
            lines.add(new Line(lines.size() + 1, start, content.endsWith("\r") ? content.substring(0, content.length() - 1) : content));
            if (end < 0) {
                break;
            }

            start = end + 1;
        }

        return List.copyOf(lines);
    }

    ///
    /// Gets the file of this unit, or an empty optional if it does not live on disk.
    ///
    /// @return The absolute path of the file.
    ///
    public Optional<Path> path() {
        return SourceFile.pathOf(this.unit);
    }

    ///
    /// Checks if this unit is a `package-info.java`.
    ///
    /// @return `true` if the unit is the package declaration file of its package.
    ///
    public boolean isPackageInfo() {
        return this.unit.getSourceFile()
                .isNameCompatible("package-info", JavaFileObject.Kind.SOURCE);
    }

    ///
    /// The position the given tree starts at, its annotations included.
    ///
    /// @param tree A tree of this unit.
    ///
    /// @return The position.
    ///
    public long start(Tree tree) {
        return this.trees.getSourcePositions().getStartPosition(this.unit, tree);
    }

    ///
    /// The position right after the end of the given tree.
    ///
    /// @param tree A tree of this unit.
    ///
    /// @return The position.
    ///
    public long end(Tree tree) {
        return this.trees.getSourcePositions().getEndPosition(this.unit, tree);
    }

    ///
    /// The number of the line the given position is on, counted from 1.
    ///
    /// @param position A position in this unit.
    ///
    /// @return The line number.
    ///
    public long line(long position) {
        return this.unit.getLineMap().getLineNumber(position);
    }

    ///
    /// The text of the given line, without its line break.
    ///
    /// @param number The line number, counted from 1.
    ///
    /// @return The text, or an empty string if the unit has no such line.
    ///
    public String lineText(long number) {
        if (number < 1 || number > this.lines.size()) {
            return "";
        }

        return this.lines.get((int) number - 1).text();
    }

    ///
    /// The source text of the given tree.
    ///
    /// @param tree A tree of this unit.
    ///
    /// @return The text from its start to its end, or an empty string if javac has no positions for it.
    ///
    public String text(Tree tree) {
        long start = this.start(tree);
        long end = this.end(tree);
        if (start < 0 || end < start) {
            return "";
        }

        return this.text.substring((int) start, (int) end);
    }

    ///
    /// The source text between the given positions.
    ///
    /// @param start The first position.
    /// @param end   The position after the last one.
    ///
    /// @return The text.
    ///
    public String text(long start, long end) {
        return this.text.substring((int) start, (int) end);
    }

    ///
    /// Checks if the given position lies inside a string literal or text block.
    ///
    /// @param position A position in this unit.
    ///
    /// @return `true` if a literal covers the position.
    ///
    public boolean isInLiteral(long position) {
        for (int i = 0; i < this.literals.size(); i += 2) {
            if (this.literals.get(i) <= position && position < this.literals.get(i + 1)) {
                return true;
            }
        }

        return false;
    }

    ///
    /// Checks if the source declares the given method, as opposed to javac or Lombok adding it.
    ///
    /// @param method A method or constructor of this unit.
    ///
    /// @return `true` if the method is written in the source.
    ///
    public boolean isDeclared(MethodTree method) {
        return this.declared.contains(method);
    }

    ///
    /// Gets every line of this unit, in order.
    ///
    /// @return The lines.
    ///
    public @Unmodifiable List<Line> getAllLines() {
        return this.lines;
    }

    ///
    /// Gets every line that holds nothing but a `//` comment outside a literal, `///` doc comments included, in order.
    ///
    /// @return Any comment lines.
    ///
    public @Unmodifiable List<Line> getAllCommentLines() {
        return this.lines.stream()
                .filter(line -> line.text().stripLeading().startsWith("//"))
                .filter(line -> !this.isInLiteral(line.start() + line.text().indexOf("//")))
                .toList();
    }
}
