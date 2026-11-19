package dev.satherov.conventions.javac.source;

///
/// The state of authorship that a source file may have.
///
public enum Origin {
    ///
    /// The file is written by hand and held to every rule.
    ///
    WRITTEN,

    ///
    /// The file is written by one of the project's own generators and only has to carry `@Generated`.
    ///
    GENERATED,

    ///
    /// The file is written by a tool outside the project, such as an annotation processor, and no rule looks at it.
    ///
    SKIPPED
}
