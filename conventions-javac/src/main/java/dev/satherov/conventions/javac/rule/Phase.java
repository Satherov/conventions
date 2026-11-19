package dev.satherov.conventions.javac.rule;

///
/// The point of the compilation that a rule runs at.
///
public enum Phase {
    ///
    /// The unit is parsed and nothing else has run: the trees are exactly the source, Lombok annotations included.
    ///
    PARSED,

    ///
    /// A top-level type is attributed: every name is resolved, and Lombok has added and removed members.
    ///
    ANALYZED
}
