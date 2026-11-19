package dev.satherov.conventions.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests what the plugin adds to a Java project.
///
class ConventionsPluginTest {

    @Test
    void addsTheCheckersAsDependencies() {
        Project project = ConventionsPluginTest.project();

        assertThat(ConventionsPluginTest.namesIn(project, "annotationProcessor")).contains("conventions-javac");
        assertThat(ConventionsPluginTest.namesIn(project, "testAnnotationProcessor")).contains("conventions-javac");
        assertThat(ConventionsPluginTest.namesIn(project, "testImplementation")).contains("conventions-archunit");
    }

    @Test
    void startsThePluginWithTheProjectOptions() {
        Project project = ConventionsPluginTest.project();
        ConventionsExtension extension = project.getExtensions().getByType(ConventionsExtension.class);
        extension.getRootPackage().set("dev.example");
        extension.getDisabled().add("member-order");

        assertThat(ConventionsPluginTest.argumentsOf(project, "compileJava"))
                .singleElement()
                .asString()
                .startsWith("-Xplugin:Conventions set=main root=dev.example disable=member-order")
                .contains(" skip=");
        assertThat(ConventionsPluginTest.argumentsOf(project, "compileTestJava"))
                .singleElement()
                .asString()
                .startsWith("-Xplugin:Conventions set=test");
    }

    @Test
    void treatsSourcesBelowTheBuildDirectoryAsGenerated() {
        Project project = ConventionsPluginTest.project();
        project.getExtensions()
                .getByType(SourceSetContainer.class)
                .getByName("main")
                .getJava()
                .srcDir(project.getLayout().getBuildDirectory().dir("generated/sources/codegen"));

        assertThat(ConventionsPluginTest.argumentsOf(project, "compileJava"))
                .singleElement()
                .asString()
                .contains(" generated=" + project.getLayout().getBuildDirectory().dir("generated/sources/codegen").get().getAsFile().getAbsolutePath());
    }

    ///
    /// Creates a Java project with the plugin applied.
    ///
    private static Project project() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(ConventionsPlugin.class);
        return project;
    }

    ///
    /// Gets the artifact names of the dependencies in the given configuration.
    ///
    private static @Unmodifiable List<String> namesIn(Project project, String configuration) {
        return project.getConfigurations()
                .getByName(configuration)
                .getDependencies()
                .stream()
                .map(Dependency::getName)
                .toList();
    }

    ///
    /// Gets the arguments the providers of the given compile task add.
    ///
    private static @Unmodifiable List<String> argumentsOf(Project project, String task) {
        List<String> arguments = new ArrayList<>();
        for (CommandLineArgumentProvider provider : project.getTasks().named(task, JavaCompile.class).get().getOptions().getCompilerArgumentProviders()) {
            provider.asArguments().forEach(arguments::add);
        }

        return List.copyOf(arguments);
    }
}
