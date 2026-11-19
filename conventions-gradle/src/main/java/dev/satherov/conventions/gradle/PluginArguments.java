package dev.satherov.conventions.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.process.CommandLineArgumentProvider;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.util.List;

///
/// The `-Xplugin:Conventions` argument of one compile task, built when the task runs.
///
public abstract class PluginArguments implements CommandLineArgumentProvider {

    ///
    /// The name javac starts the plugin by.
    ///
    public static final String PLUGIN = "Conventions";

    ///
    /// The root package of the project.
    ///
    /// @return The root package, empty for none.
    ///
    @Input
    public abstract Property<String> getRootPackage();

    ///
    /// The source set the task compiles, `main` or `test`.
    ///
    /// @return The source set.
    ///
    @Input
    public abstract Property<String> getSourceSet();

    ///
    /// The ids of the rules that do not run.
    ///
    /// @return Any disabled rule ids.
    ///
    @Input
    public abstract SetProperty<String> getDisabled();

    ///
    /// The directories of sources the project generates itself.
    ///
    /// @return The generated source directories.
    ///
    @Internal
    public abstract ConfigurableFileCollection getGenerated();

    ///
    /// The directory annotation processors write their sources to, which no rule looks at.
    ///
    /// @return The output directory of annotation processing.
    ///
    @Internal
    public abstract DirectoryProperty getSkipped();

    ///
    /// The paths of the generated source directories, the input that decides whether the task is up to date.
    ///
    /// @return The absolute paths.
    ///
    @Input
    public @Unmodifiable List<String> getGeneratedPaths() {
        return this.getGenerated()
                .getFiles()
                .stream()
                .map(File::getAbsolutePath)
                .sorted()
                .toList();
    }

    ///
    /// The path of the annotation processor output, the input that decides whether the task is up to date.
    ///
    /// @return The absolute path, or an empty string if the task has none.
    ///
    @Input
    public String getSkippedPath() {
        return this.getSkipped()
                .map(directory -> directory.getAsFile().getAbsolutePath())
                .getOrElse("");
    }

    ///
    /// Builds the one argument javac reads the plugin and its options from.
    ///
    @Override
    public Iterable<String> asArguments() {
        StringBuilder argument = new StringBuilder("-Xplugin:")
                .append(PluginArguments.PLUGIN)
                .append(" set=")
                .append(this.getSourceSet().get());
        String root = this.getRootPackage().getOrElse("");
        if (!root.isEmpty()) {
            argument.append(" root=").append(root);
        }

        if (!this.getDisabled().get().isEmpty()) {
            argument.append(" disable=").append(String.join(",", this.getDisabled().get().stream().sorted().toList()));
        }

        if (!this.getGeneratedPaths().isEmpty()) {
            argument.append(" generated=").append(String.join(File.pathSeparator, this.getGeneratedPaths()));
        }

        if (!this.getSkippedPath().isEmpty()) {
            argument.append(" skip=").append(this.getSkippedPath());
        }

        return List.of(argument.toString());
    }
}
