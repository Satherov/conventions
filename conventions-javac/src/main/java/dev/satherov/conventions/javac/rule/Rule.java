package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.Origin;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.util.TreePath;

///
/// A rule of the code conventions that javac checks.
///
/// A rule is created once per compilation and may keep state across the files it sees.
///
public interface Rule {

    ///
    /// The id the rule is reported and disabled by, in lowercase words joined by `-`.
    ///
    /// @return The id.
    ///
    String id();

    ///
    /// The point of the compilation the rule runs at.
    ///
    /// @return The phase.
    ///
    Phase phase();

    ///
    /// The kind of file the rule looks at.
    ///
    /// @return [Origin#WRITTEN], unless the rule is about generated files.
    ///
    default Origin origin() {
        return Origin.WRITTEN;
    }

    ///
    /// Checks one part of a file and records every breach.
    ///
    /// @param file     The file.
    /// @param path     The whole unit in [Phase#PARSED], one top-level type in [Phase#ANALYZED].
    /// @param findings The findings the breaches are recorded in.
    ///
    void check(SourceFile file, TreePath path, Findings findings);

    ///
    /// Records the breaches that only show once every file was seen.
    ///
    /// @param findings The findings the breaches are recorded in.
    ///
    default void finish(Findings findings) { }
}
