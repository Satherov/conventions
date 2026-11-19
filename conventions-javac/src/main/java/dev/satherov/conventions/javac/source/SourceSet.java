package dev.satherov.conventions.javac.source;

import java.util.Locale;

///
/// The source set that a compilation belongs to.
///
public enum SourceSet {
    ///
    /// The production sources, held to every rule.
    ///
    MAIN,

    ///
    /// The test sources, which may use static imports, package-private members, and undocumented test methods.
    ///
    TEST;

    ///
    /// Reads a source set from its lowercase name.
    ///
    /// @param name The name, `main` or `test`.
    ///
    /// @return The source set.
    ///
    /// @throws IllegalArgumentException If the name is neither.
    ///
    public static SourceSet from(String name) {
        return SourceSet.valueOf(name.toUpperCase(Locale.ROOT));
    }
}
