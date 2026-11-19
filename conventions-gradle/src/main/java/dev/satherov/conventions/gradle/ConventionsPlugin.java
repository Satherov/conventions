package dev.satherov.conventions.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

///
/// The Gradle plugin `dev.satherov.conventions`, which puts the code conventions on every Java source set of a
/// project.
///
/// It adds the javac plugin to the annotation processor path of every source set and starts it in each compile task,
/// with `set=main` for the main source set and `set=test` for every other one. It adds the ArchUnit rules to the test
/// dependencies, for the project's architecture test to pull in.
///
public class ConventionsPlugin implements Plugin<Project> {

    ///
    /// The group the javac plugin and the ArchUnit rules are published under.
    ///
    public static final String GROUP = "dev.satherov.conventions";

    ///
    /// The resource that holds the version this plugin was built as.
    ///
    private static final String PROPERTIES = "conventions.properties";

    ///
    /// Registers the extension and configures the source sets once the project is a Java project.
    ///
    @Override
    public void apply(Project project) {
        ConventionsExtension extension = project.getExtensions().create(ConventionsExtension.NAME, ConventionsExtension.class);
        extension.getRootPackage().convention("");
        project.getPluginManager().withPlugin("java", applied -> ConventionsPlugin.configure(project, extension));
    }

    ///
    /// Adds the dependencies and starts the javac plugin in the compile task of every source set.
    ///
    private static void configure(Project project, ConventionsExtension extension) {
        String version = ConventionsPlugin.version();
        project.getDependencies().add("testImplementation", ConventionsPlugin.GROUP + ":conventions-archunit:" + version);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        FileCollection generated = ConventionsPlugin.generatedDirectories(project, sourceSets).plus(extension.getGenerated());
        sourceSets.configureEach(sourceSet -> {
            project.getDependencies().add(sourceSet.getAnnotationProcessorConfigurationName(), ConventionsPlugin.GROUP + ":conventions-javac:" + version);
            project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(task -> {
                PluginArguments arguments = project.getObjects().newInstance(PluginArguments.class);
                arguments.getRootPackage().set(extension.getRootPackage());
                arguments.getSourceSet().set(SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) ? "main" : "test");
                arguments.getDisabled().set(extension.getDisabled());
                arguments.getGenerated().from(generated);
                arguments.getSkipped().set(task.getOptions().getGeneratedSourceOutputDirectory().getLocationOnly());
                task.getOptions().getCompilerArgumentProviders().add(arguments);
            });
        });
    }

    ///
    /// Gets the Java source directories of every source set that lie below the build directory, which a code
    /// generator of the project wrote.
    ///
    private static FileCollection generatedDirectories(Project project, SourceSetContainer sourceSets) {
        Directory build = project.getLayout().getBuildDirectory().get();
        return project.files(project.provider(() -> sourceSets.stream()
                .flatMap(sourceSet -> sourceSet.getJava().getSrcDirs().stream())
                .filter(directory -> directory.toPath().startsWith(build.getAsFile().toPath()))
                .toList()));
    }

    ///
    /// Reads the version this plugin was built as, which the javac plugin and the ArchUnit rules share.
    ///
    private static String version() {
        try (InputStream input = ConventionsPlugin.class.getResourceAsStream(ConventionsPlugin.PROPERTIES)) {
            if (input == null) {
                throw new GradleException("The conventions plugin is missing its " + ConventionsPlugin.PROPERTIES);
            }

            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("version");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
