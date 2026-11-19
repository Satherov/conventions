package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.comment.CommentedCodeRule;
import dev.satherov.conventions.javac.rule.comment.DocFormRule;
import dev.satherov.conventions.javac.rule.comment.DocPresenceRule;
import dev.satherov.conventions.javac.rule.comment.NoinspectionReasonRule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about comments.
///
class CommentRulesTest {

    @Test
    void docFormFlagsJavadocAndUnframedBlocks() {
        Javac.Report report = Javac.main("""
                package dev.example;

                /** Old style. */
                class Sample {

                    /// Not framed.
                    void first() { }

                    ///
                    /// Links {@link Sample} the old way.
                    ///
                    void second() { }

                    ///
                    /// Framed and plain, with a [Sample] link.
                    ///
                    void third() { }
                }
                """);

        assertThat(report.getAllLinesOf(DocFormRule.ID)).containsExactly(3L, 6L, 6L, 10L);
    }

    @Test
    void docFormIgnoresTextBlocks() {
        Javac.Report report = Javac.main("""
                package dev.example;

                ///
                /// Holds a text block.
                ///
                class Sample {

                    ///
                    /// The text.
                    ///
                    static final String TEXT = \"""
                            /** not a comment */
                            /// not one either
                            \""";
                }
                """);

        assertThat(report.getAllOf(DocFormRule.ID)).isEmpty();
    }

    @Test
    void docPresenceFlagsEveryUndocumentedMemberOfMain() {
        Javac.Report report = Javac.main("""
                package dev.example;

                ///
                /// Documented.
                ///
                class Sample {

                    int count;

                    Sample() { }

                    void run() { }

                    ///
                    /// Documented, below its annotation.
                    ///
                    @Deprecated
                    void old() { }

                    class Nested { }
                }
                """);

        assertThat(report.getAllLinesOf(DocPresenceRule.ID)).containsExactly(8L, 10L, 12L, 20L);
    }

    @Test
    void docPresenceLeavesRecordComponentsAndAnnotatedDocsAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                ///
                /// A point.
                ///
                /// @param x The x.
                ///
                @SuppressWarnings("unused")
                record Point(int x) { }
                """);

        assertThat(report.getAllOf(DocPresenceRule.ID)).isEmpty();
    }

    @Test
    void docPresenceAsksTestCodeOnlyForTypeDocs() {
        Javac.Report report = Javac.test("""
                package dev.example;

                class SampleTest {

                    void works() { }
                }
                """);

        assertThat(report.getAllLinesOf(DocPresenceRule.ID)).containsExactly(3L);
    }

    @Test
    void noinspectionNeedsAReason() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    void run(String text) {
                        //noinspection ResultOfMethodCallIgnored
                        text.strip();
                        //noinspection ResultOfMethodCallIgnored the strip only checks the text is readable
                        text.strip();
                    }
                }
                """);

        assertThat(report.getAllLinesOf(NoinspectionReasonRule.ID)).containsExactly(6L);
    }

    @Test
    void noinspectionStaysInsideBodies() {
        Javac.Report report = Javac.main("""
                package dev.example;

                //noinspection UnusedDeclaration the type is loaded by name
                class Sample {

                    //noinspection UnusedDeclaration the field is read by reflection
                    private int count;

                    void run(String text) {
                        Runnable task = () -> {
                            //noinspection ResultOfMethodCallIgnored the strip only checks the text is readable
                            text.strip();
                        };
                        task.run();
                    }
                }
                """);

        assertThat(report.getAllLinesOf(NoinspectionReasonRule.ID)).containsExactly(3L, 6L);
    }

    @Test
    void commentedCodeFlagsStatementsAndBlocks() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    void run() {
                        // int count = 0;
                        // if (ready) {
                        // }
                        // A sentence about the next line.
                        // @formatter:off
                    }
                }
                """);

        assertThat(report.getAllLinesOf(CommentedCodeRule.ID)).containsExactly(6L, 7L, 8L);
    }
}
