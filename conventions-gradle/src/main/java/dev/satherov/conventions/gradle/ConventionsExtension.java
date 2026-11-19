package dev.satherov.conventions.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

///
/// The `conventions { }` block a project configures the checks with.
///
/// ```kotlin
/// conventions {
///     rootPackage = "dev.satherov.example"
///     disabled.add("member-order")
///     generated.from(layout.buildDirectory.dir("generated/sources/codegen"))
/// }
/// ```
///
public abstract class ConventionsExtension {

    ///
    /// The name the extension is registered under.
    ///
    public static final String NAME = "conventions";

    ///
    /// The root package of the project, whose imports form their own group. Empty by default, which puts the project's
    /// imports among the other libraries.
    ///
    /// @return The root package.
    ///
    public abstract Property<String> getRootPackage();

    ///
    /// The ids of the javac rules that do not run.
    ///
    /// @return Any disabled rule ids.
    ///
    public abstract SetProperty<String> getDisabled();

    ///
    /// The directories of sources the project generates itself, held to the rules for generated code. Every source
    /// directory below the build directory is added on its own.
    ///
    /// @return The generated source directories.
    ///
    public abstract ConfigurableFileCollection getGenerated();
}
