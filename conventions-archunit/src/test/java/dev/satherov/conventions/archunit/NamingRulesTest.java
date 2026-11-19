package dev.satherov.conventions.archunit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about type names.
///
class NamingRulesTest {

    @Test
    void noKindNames() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.api.user;

                        public class UserManager { }
                        """, """
                        package dev.example.internal.text;

                        public class Utils { }
                        """, """
                        package dev.example.internal.text;

                        public class StringUtils { }
                        """)
                .violationsOf(NamingRules.NO_KIND_NAMES);

        assertThat(violations)
                .hasSize(2)
                .anyMatch(violation -> violation.contains("UserManager"))
                .anyMatch(violation -> violation.contains("text.Utils"));
    }

    @Test
    void noInterfaceOrAbstractPrefix() {
        Fixtures fixtures = new Fixtures().main("""
                package dev.example.api.user;

                public interface IUser { }
                """, """
                package dev.example.api.user;

                public interface Identity { }
                """, """
                package dev.example.api.user;

                public abstract class AbstractUser { }
                """);

        assertThat(fixtures.violationsOf(NamingRules.NO_INTERFACE_PREFIX))
                .hasSize(1)
                .allMatch(violation -> violation.contains("IUser"));
        assertThat(fixtures.violationsOf(NamingRules.NO_ABSTRACT_PREFIX))
                .hasSize(1)
                .allMatch(violation -> violation.contains("AbstractUser"));
    }

    @Test
    void noVersionNames() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.api.user;

                        public class NewUser { }
                        """, """
                        package dev.example.api.user;

                        public class UserV2 { }
                        """, """
                        package dev.example.api.user;

                        public class NewsFeed { }
                        """, """
                        package dev.example.api.user;

                        public class Threshold { }
                        """)
                .violationsOf(NamingRules.NO_VERSION_NAMES);

        assertThat(violations)
                .hasSize(2)
                .anyMatch(violation -> violation.contains("NewUser"))
                .anyMatch(violation -> violation.contains("UserV2"));
    }

    @Test
    void exceptionsEndInException() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.api.user;

                        public class UserMissing extends RuntimeException { }
                        """, """
                        package dev.example.api.user;

                        public class UserMissingException extends RuntimeException { }
                        """)
                .violationsOf(NamingRules.EXCEPTIONS_END_IN_EXCEPTION);

        assertThat(violations)
                .hasSize(1)
                .allMatch(violation -> violation.contains("user.UserMissing>"));
    }

    @Test
    void noMockNames() {
        List<String> violations = new Fixtures()
                .test("""
                        package dev.example.api.user;

                        class MockUser { }
                        """, """
                        package dev.example.api.user;

                        class RecordingUser { }
                        """)
                .violationsOf(NamingRules.NO_MOCK_NAMES);

        assertThat(violations)
                .hasSize(1)
                .allMatch(violation -> violation.contains("MockUser"));
    }
}
