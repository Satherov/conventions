package dev.satherov.conventions.javac.source;

///
/// A line of a source file.
///
/// @param number The line number, counted from 1.
/// @param start  The position of the first character of the line.
/// @param text   The text of the line, without its line break.
///
public record Line(long number, long start, String text) {

    ///
    /// The text of the line without the whitespace around it.
    ///
    /// @return The stripped text.
    ///
    public String content() {
        return this.text.strip();
    }

    ///
    /// The position of the first character of the line that is not whitespace.
    ///
    /// @return The position, or the position of the line break if the line is blank.
    ///
    public long contentStart() {
        return this.start + this.text.length() - this.text.stripLeading().length();
    }
}
