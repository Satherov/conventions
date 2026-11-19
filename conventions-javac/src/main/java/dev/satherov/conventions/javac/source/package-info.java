///
/// The source files the rules read and the findings they report.
///
/// - [SourceFile] is one compilation unit with its text and positions.
/// - [Findings] collects what the rules report until the compilation ends.
/// - [Options] holds what the project passes after `-Xplugin:Conventions`.
///
@NullMarked
@CheckReturnValue
package dev.satherov.conventions.javac.source;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
