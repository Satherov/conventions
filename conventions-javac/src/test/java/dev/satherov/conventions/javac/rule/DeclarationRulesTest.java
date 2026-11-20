package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.declaration.BitConstantRule;
import dev.satherov.conventions.javac.rule.declaration.TypeParameterRule;
import dev.satherov.conventions.javac.rule.declaration.VisibilityRule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about declarations.
///
class DeclarationRulesTest {

    @Test
    void visibilityIsStatedOnEveryMemberOfMain() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import org.jetbrains.annotations.VisibleForTesting;

                class Sample {

                    int count;

                    private int total;

                    Sample() { }

                    void run() { }

                    @VisibleForTesting
                    void probe() { }

                    static class Nested { }
                }
                """);

        assertThat(report.getAllLinesOf(VisibilityRule.ID)).containsExactly(7L, 11L, 13L, 18L);
    }

    @Test
    void visibilityLeavesImplicitMembersAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                interface Shape {

                    int SIDES = 0;

                    int area();

                    class Default { }
                }
                """, """
                package dev.example;

                enum Color {
                    @Deprecated
                    RED,
                    BLUE;

                    Color() { }
                }
                """, """
                package dev.example;

                record Point(int x, int y) {

                    Point {
                        if (x < 0) throw new IllegalArgumentException("negative");
                    }
                }
                """);

        assertThat(report.getAllOf(VisibilityRule.ID)).isEmpty();
    }

    @Test
    void visibilityIsLeftToTestCode() {
        Javac.Report report = Javac.test("""
                package dev.example;

                class SampleTest {

                    void works() { }
                }
                """);

        assertThat(report.getAllOf(VisibilityRule.ID)).isEmpty();
    }

    @Test
    void typeParametersAreCapitalizedWords() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Box<T, VALUE> {

                    <Result> Result map(Result value) {
                        return value;
                    }

                    <KEY_2> void take(KEY_2 key) { }
                }
                """);

        assertThat(report.getAllOf(TypeParameterRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly(
                        "type parameter T; name it with a capitalized word such as VALUE",
                        "type parameter Result; name it with a capitalized word such as VALUE"
                );
    }

    @Test
    void bitConstantsThatAreMaskedAreFlagged() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.Set;

                class Flags {

                    private static final int SILENT = 1 << 2;

                    private static final long PINNED = 1L << 40;

                    private static final int MUTED = 1 << 5;

                    private static final int BOTH = Flags.MUTED | (1 << 3);

                    private static final int SHOWN = 1 << 4;

                    static int of(Set<String> names) {
                        return names.stream()
                                .mapToInt(name -> switch (name) {
                                    case "silent" -> Flags.SILENT;
                                    default -> 0;
                                })
                                .reduce(0, (left, right) -> left | right);
                    }

                    static boolean pinned(long bits) {
                        return (bits & PINNED) != 0L;
                    }

                    static int both(int bits) {
                        bits |= Flags.BOTH;
                        return ~bits;
                    }

                    static int shown() {
                        return Flags.SHOWN;
                    }
                }
                """);

        assertThat(report.getAllLinesOf(BitConstantRule.ID)).containsExactly(7L, 9L, 11L, 13L);
    }

    @Test
    void bitConstantLeavesOtherConstantsAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Buffers {

                    private static final int CAPACITY = 1 << 16;

                    private static final int MASK = 0xFF;

                    private static final int TWO = 2 << 3;

                    private final int instance = 1 << 5;

                    private static int mutable = 1 << 6;

                    private static final int SUM = Buffers.CAPACITY + 1;

                    enum Mode {
                        ON;

                        private static final int BIT = 1 << 1;

                        int masked(int bits) {
                            return bits & Mode.BIT;
                        }
                    }

                    byte[] allocate(int bits) {
                        int masked = bits & Buffers.MASK & Buffers.TWO & this.instance & Buffers.mutable & Buffers.SUM;
                        return new byte[Buffers.CAPACITY + masked];
                    }
                }
                """);

        assertThat(report.getAllOf(BitConstantRule.ID)).isEmpty();
    }
}
