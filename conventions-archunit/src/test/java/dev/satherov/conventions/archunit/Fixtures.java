package dev.satherov.conventions.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

///
/// Compiles fixture sources into main and test output directories laid out like Gradle's, and imports them.
///
final class Fixtures {

    ///
    /// The package declaration of a source, which decides its directory.
    ///
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);

    ///
    /// The first top-level type declaration of a source, which decides its file name.
    ///
    private static final Pattern TYPE = Pattern.compile("^(?:[\\w@]+\\s+)*(?:class|interface|enum|record|@interface)\\s+(\\w+)", Pattern.MULTILINE);

    ///
    /// The main sources.
    ///
    private final List<String> main = new ArrayList<>();

    ///
    /// The test sources.
    ///
    private final List<String> test = new ArrayList<>();

    ///
    /// Adds main sources.
    ///
    @SuppressWarnings("ConfusingMainMethod")
    Fixtures main(String... sources) {
        this.main.addAll(List.of(sources));
        return this;
    }

    ///
    /// Adds test sources, compiled against the main ones.
    ///
    Fixtures test(String... sources) {
        this.test.addAll(List.of(sources));
        return this;
    }

    ///
    /// Compiles every source, evaluates the rule on the classes and gets its violations.
    ///
    @Unmodifiable List<String> violationsOf(ArchRule rule) {
        Path root = Fixtures.temporary();
        try {
            Path mainOutput = root.resolve("build/classes/java/main");
            Path testOutput = root.resolve("build/classes/java/test");
            Fixtures.compile(root.resolve("src/main"), this.main, mainOutput, System.getProperty("java.class.path"));
            Fixtures.compile(root.resolve("src/test"), this.test, testOutput, System.getProperty("java.class.path") + File.pathSeparator + mainOutput);
            JavaClasses classes = new ClassFileImporter().importPath(root.resolve("build"));
            EvaluationResult result = rule.evaluate(classes);
            return List.copyOf(result.getFailureReport().getDetails());
        } finally {
            Fixtures.delete(root);
        }
    }

    ///
    /// Writes the given sources below the given directory and compiles them into the given output.
    ///
    private static void compile(Path sources, List<String> texts, Path output, String classpath) {
        if (texts.isEmpty()) {
            return;
        }

        try {
            Files.createDirectories(output);
            List<String> arguments = new ArrayList<>(List.of("-d", output.toString(), "-classpath", classpath, "-proc:none"));
            for (String text : texts) {
                arguments.add(Fixtures.write(sources, text).toString());
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int status = compiler.run(null, null, null, arguments.toArray(String[]::new));
            if (status != 0) {
                throw new IllegalStateException("The fixture does not compile");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    ///
    /// Writes one source to the file its package and first type name, below the given directory.
    ///
    private static Path write(Path sources, String text) throws IOException {
        Matcher packageMatcher = Fixtures.PACKAGE.matcher(text);
        Matcher typeMatcher = Fixtures.TYPE.matcher(text);
        String directory = packageMatcher.find() ? packageMatcher.group(1).replace('.', '/') : "";
        String name = typeMatcher.find() ? typeMatcher.group(1) : "package-info";
        Path file = sources.resolve(directory).resolve(name + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    ///
    /// Creates a fresh directory to compile in.
    ///
    private static Path temporary() {
        try {
            return Files.createTempDirectory("conventions");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    ///
    /// Deletes the given directory and everything below it.
    ///
    private static void delete(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
