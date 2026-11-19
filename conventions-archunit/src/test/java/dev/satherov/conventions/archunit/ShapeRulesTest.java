package dev.satherov.conventions.archunit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about type and member shapes.
///
class ShapeRulesTest {

    @Test
    void singleInstanceIsEnum() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.internal.glow;

                        public final class Glow {

                            public static final Glow INSTANCE = new Glow();
                        }
                        """, """
                        package dev.example.internal.glow;

                        public enum Outline {
                            INSTANCE
                        }
                        """)
                .violationsOf(ShapeRules.SINGLE_INSTANCE_IS_ENUM);

        assertThat(violations)
                .hasSize(1)
                .allMatch(violation -> violation.contains("glow.Glow has a single INSTANCE"));
    }

    @Test
    void noEmptyRecords() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.internal.format;

                        public record Empty() {

                            public static final int SIZE = 0;
                        }
                        """, """
                        package dev.example.internal.format;

                        public record Point(int x) { }
                        """)
                .violationsOf(ShapeRules.NO_EMPTY_RECORDS);

        assertThat(violations)
                .hasSize(1)
                .allMatch(violation -> violation.contains("Empty is an empty record"));
    }

    @Test
    void repeatableContainerIsNested() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.api.tag;

                        import java.lang.annotation.Repeatable;

                        @Repeatable(Tag.Container.class)
                        public @interface Tag {

                            String value();

                            @interface Container {

                                Tag[] value();
                            }
                        }
                        """, """
                        package dev.example.api.tag;

                        import java.lang.annotation.Repeatable;

                        @Repeatable(Labels.class)
                        public @interface Label {

                            String value();
                        }
                        """, """
                        package dev.example.api.tag;

                        public @interface Labels {

                            Label[] value();
                        }
                        """)
                .violationsOf(ShapeRules.REPEATABLE_CONTAINER);

        assertThat(violations)
                .hasSize(1)
                .allMatch(violation -> violation.contains("tag.Label has its repeatable container outside"));
    }

    @Test
    void lombokLoggers() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.internal.audit;

                        import java.util.logging.Logger;

                        public class Audit {

                            private static final Logger LOGGER = Logger.getLogger("audit");
                        }
                        """, """
                        package dev.example.internal.audit;

                        import java.util.logging.Logger;

                        public class Trail {

                            private static final Logger log = Logger.getLogger("trail");
                        }
                        """)
                .violationsOf(ShapeRules.LOMBOK_LOGGERS);

        assertThat(violations)
                .isNotEmpty()
                .allMatch(violation -> violation.contains("Audit.LOGGER"));
    }

    @Test
    void noReflection() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.internal.load;

                        import java.lang.reflect.Field;

                        public class Loader {

                            public Object load(String name) throws ReflectiveOperationException {
                                Class<?> type = Class.forName(name);
                                Field field = type.getDeclaredField("value");
                                field.setAccessible(true);
                                return field.get(null);
                            }
                        }
                        """, """
                        package dev.example.internal.load;

                        public class Plain {

                            public Class<?> type() {
                                return String.class;
                            }
                        }
                        """)
                .violationsOf(ShapeRules.NO_REFLECTION);

        assertThat(violations)
                .hasSize(2)
                .allMatch(violation -> violation.contains("Loader.load"));
    }

    @Test
    void noBitfieldsInApi() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.api.user;

                        import java.util.Set;

                        public interface Account {

                            int flags();

                            long permissionBits();

                            Set<String> badgeFlags();

                            int age();
                        }
                        """)
                .violationsOf(ShapeRules.NO_BITFIELDS_IN_API);

        assertThat(violations)
                .hasSize(2)
                .anyMatch(violation -> violation.contains("Account.flags()"))
                .anyMatch(violation -> violation.contains("Account.permissionBits()"));
    }

    @Test
    void describedThroughTheDescriber() {
        String describer = """
                package dev.example.internal.text;

                public final class Describer {

                    public static String of(Object value) {
                        return value.getClass().getSimpleName();
                    }
                }
                """;
        String user = """
                package dev.example.api.user;

                public interface User { }
                """;
        List<String> violations = new Fixtures()
                .main(describer, user, """
                        package dev.example.impl.user;

                        import dev.example.api.user.User;
                        import dev.example.internal.text.Describer;

                        public class DescribedUser implements User {

                            @Override
                            public String toString() {
                                return Describer.of(this);
                            }
                        }
                        """, """
                        package dev.example.impl.user;

                        import dev.example.api.user.User;

                        public class PlainUser implements User {

                            @Override
                            public String toString() {
                                return "PlainUser";
                            }
                        }
                        """)
                .violationsOf(ShapeRules.describedThrough("dev.example.internal.text.Describer"));

        assertThat(violations)
                .hasSize(1)
                .allMatch(violation -> violation.contains("PlainUser.toString()"));
    }
}
