import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ArgumentHandlerTest {

    @Test
    void noArgumentTest() {
        String[] args = {};
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ArgumentHandler.parseArgs(args),
                "parseArgs method should not allow no arguments."
                );

        assertTrue(thrown.getMessage().contains("Invalid number of arguments"));
    }

    @Test
    void rejectNoValueTest() {
        String[] args = {"-local"};

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ArgumentHandler.parseArgs(args),
                "parseArgs method should require values for every option"
        );

        assertTrue(thrown.getMessage().contains("Invalid number of arguments"));
    }

    @Test
    void rejectImproperOptionTest() {
        String[] args = {"local", "/a/path"};

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ArgumentHandler.parseArgs(args),
                "parseArgs method should require a dash for every option"
        );

        assertTrue(thrown.getMessage().contains("Malformed"));
    }

    @Test
    void acceptParseTest() {
        String[] args = {"-local", "/", "-project", "rust"};
        Map<String, String> expected = Map.of("local", "/", "project", "rust");

        assertEquals(expected, ArgumentHandler.parseArgs(args));
    }

    @Test
    void rejectUnknownOptionTest() {
        Map<String, String> args = Map.of("somethingrandom", "a_value");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ArgumentHandler.validateOptions(args),
                "Validation should reject unknown options"
        );

        assertTrue(thrown.getMessage().contains("Unknown"));
    }

    //@Test
    void rejectMissingSource() {
        Map<String, String> args = Map.of("project", "rust");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ArgumentHandler.validateOptions(args),
                "Validation should require one source"
        );

        assertTrue(thrown.getMessage().contains("Missing"));
    }

    @Test
    void rejectInvalidProjectType() {
        Map<String, String> args = Map.of("local", "/", "project", "magic");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ArgumentHandler.validateOptions(args)
        );

        assertTrue(thrown.getMessage().contains("Unsupported"));
    }

    //@Test
    void rejectInvalidLocalPath() {
        Map<String, String> args = Map.of("local", "/thisdefinitelyshouldnotexists", "project", "magic");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ArgumentHandler.validateOptions(args)
        );

        assertTrue(thrown.getMessage().contains("directory"));
    }

    @Test
    void acceptValidConfig() {
        Map<String, String> args = Map.of("local", "/", "project", "rust");

        assertDoesNotThrow(() -> ArgumentHandler.validateOptions(args));
    }
}
