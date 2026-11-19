package dev.satherov.conventions.javac;

import lombok.experimental.UtilityClass;

import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

///
/// Compiles fixture sources with the plugin and collects what it reports.
///
@UtilityClass
public class Javac {

    ///
    /// The package declaration of a source, which decides its directory.
    ///
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);

    ///
    /// The first top-level type declaration of a source, which decides its file name.
    ///
    private static final Pattern TYPE = Pattern.compile("^(?:[\\w@]+\\s+)*(?:class|interface|enum|record|@interface)\\s+(\\w+)", Pattern.MULTILINE);

    ///
    /// The message javac prints for a conventions finding.
    ///
    private static final Pattern FINDING = Pattern.compile("^\\[conventions:([a-z-]+)] (?:line (\\d+): )?(.*)$", Pattern.DOTALL);

    ///
    /// Compiles the given sources of the main source set.
    ///
    @SuppressWarnings("ConfusingMainMethod")
    public static Report main(String... sources) {
        return Javac.compile(List.of("set=main"), sources);
    }

    ///
    /// Compiles the given sources of the test source set.
    ///
    public static Report test(String... sources) {
        return Javac.compile(List.of("set=test"), sources);
    }

    ///
    /// Compiles the given sources in a fresh directory with the given plugin options and Lombok on the processor path.
    ///
    public static Report compile(List<String> options, String... sources) {
        Path root = Javac.temporary();
        try {
            return Javac.compile(root, options, Javac.write(root, sources));
        } finally {
            Javac.delete(root);
        }
    }

    ///
    /// Compiles the given files, which lie below the given root.
    ///
    public static Report compile(Path root, List<String> options, List<Path> files) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<String> arguments = List.of(
                    "-d", root.resolve("classes").toString(),
                    "-classpath", System.getProperty("java.class.path"),
                    "-Xplugin:" + Conventions.NAME + " root=dev.example " + String.join(" ", options)
            );
            Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromPaths(files);
            boolean success = compiler.getTask(null, manager, diagnostics, arguments, null, units).call();
            return Javac.report(root, success, diagnostics.getDiagnostics());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    ///
    /// Writes every source to the file its package and first type name, below the given root.
    ///
    public static @Unmodifiable List<Path> write(Path root, String... sources) {
        List<Path> files = new ArrayList<>();
        for (String source : sources) {
            Matcher packageMatcher = Javac.PACKAGE.matcher(source);
            Matcher typeMatcher = Javac.TYPE.matcher(source);
            String directory = packageMatcher.find() ? packageMatcher.group(1).replace('.', '/') : "";
            String name = typeMatcher.find() ? typeMatcher.group(1) : "package-info";
            Path file = root.resolve("src").resolve(directory).resolve(name + ".java");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, source);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }

            files.add(file);
        }

        return List.copyOf(files);
    }

    ///
    /// Creates a fresh directory to compile in.
    ///
    public static Path temporary() {
        try {
            return Files.createTempDirectory("conventions");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    ///
    /// Deletes the given directory and everything below it.
    ///
    public static void delete(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    ///
    /// Splits the diagnostics into the findings of the plugin and every other error.
    ///
    private static Report report(Path root, boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        List<Finding> findings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            String message = diagnostic.getMessage(Locale.ROOT);
            Matcher matcher = Javac.FINDING.matcher(message);
            if (matcher.matches()) {
                findings.add(new Finding(matcher.group(1), Javac.fileOf(root, diagnostic), Javac.lineOf(matcher, diagnostic), matcher.group(3)));
            } else if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(diagnostic.toString());
            }
        }

        return new Report(success, findings, errors);
    }

    ///
    /// Gets the line a finding names in its message, or else the line of its diagnostic.
    ///
    private static long lineOf(Matcher matcher, Diagnostic<? extends JavaFileObject> diagnostic) {
        if (matcher.group(2) == null) {
            return diagnostic.getLineNumber();
        }

        return Long.parseLong(matcher.group(2));
    }

    ///
    /// Gets the file of the given diagnostic, relative to the source root, or an empty string if it has none.
    ///
    private static String fileOf(Path root, Diagnostic<? extends JavaFileObject> diagnostic) {
        //noinspection ConstantValue javac reports some diagnostics without a source file.
        if (diagnostic.getSource() == null) {
            return "";
        }

        return root.resolve("src")
                .relativize(Path.of(diagnostic.getSource().toUri()))
                .toString();
    }

    ///
    /// What one compilation reported.
    ///
    /// @param success  Whether javac succeeded.
    /// @param findings Any findings of the plugin, in order.
    /// @param errors   Any other errors, which a fixture must not cause.
    ///
    public record Report(boolean success, @Unmodifiable List<Finding> findings, @Unmodifiable List<String> errors) {

        ///
        /// Copies the lists and fails on errors that are not findings, since they mean the fixture is broken.
        ///
        public Report {
            findings = List.copyOf(findings);
            errors = List.copyOf(errors);
            if (!errors.isEmpty()) {
                throw new IllegalStateException("The fixture does not compile:\n" + String.join("\n", errors));
            }
        }

        ///
        /// Gets the findings of the given rule.
        ///
        public @Unmodifiable List<Finding> getAllOf(String rule) {
            return this.findings.stream()
                    .filter(finding -> finding.rule().equals(rule))
                    .toList();
        }

        ///
        /// Gets the lines the given rule reported on, in order.
        ///
        public @Unmodifiable List<Long> getAllLinesOf(String rule) {
            return this.getAllOf(rule)
                    .stream()
                    .map(Finding::line)
                    .toList();
        }
    }

    ///
    /// One finding of the plugin.
    ///
    /// @param rule    The id of the rule.
    /// @param file    The file, relative to the source root.
    /// @param line    The line, counted from 1.
    /// @param message The message without its prefix.
    ///
    public record Finding(String rule, String file, long line, String message) { }
}
