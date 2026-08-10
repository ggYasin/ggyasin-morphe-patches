package app.patchlab.extension.otp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public final class OtpExtractorTest {
    @Test
    public void matchesOtpHelperCorpus() {
        InputStream stream = checkNotNull(
                getClass().getResourceAsStream("/code_detection_tests.yaml"));
        List<Map<String, Object>> cases = new Yaml().load(stream);
        OtpExtractorEngine extractor = new OtpExtractorEngine(
                Collections.emptyList(), Collections.emptyList());

        for (Map<String, Object> testCase : cases) {
            String name = (String) testCase.get("name");
            String message = (String) testCase.get("message");
            boolean shouldIgnore = (Boolean) testCase.get("shouldIgnore");
            assertEquals("[" + name + "] shouldIgnore", shouldIgnore,
                    extractor.shouldIgnore(message));

            if (!Boolean.TRUE.equals(testCase.get("skipCodeCheck"))) {
                String expectedCode = (String) testCase.get("expectedCode");
                assertEquals("[" + name + "] extract", expectedCode,
                        extractor.extract(message));
            }
        }
    }

    @Test
    public void customPhrasesExtendRatherThanReplaceDefaults() {
        OtpExtractorEngine extractor = new OtpExtractorEngine(
                Collections.singletonList("login token"),
                Collections.singletonList("test-only"));

        assertEquals("AB12", extractor.extract("Your login token: AB-12."));
        assertEquals("123456", extractor.extract("Your OTP is 123456."));
        assertEquals(true, extractor.shouldIgnore("test-only code 123456"));
    }

    @Test
    public void nullAndEmptyBodiesAreSafe() {
        OtpExtractorEngine extractor = new OtpExtractorEngine(
                Collections.emptyList(), Collections.emptyList());

        assertFalse(extractor.shouldIgnore(null));
        assertFalse(extractor.shouldIgnore(""));
        assertNull(extractor.extract(null));
        assertNull(extractor.extract(""));
    }

    @Test
    public void extractionIsSafeAcrossConcurrentCalls() throws Exception {
        OtpExtractorEngine extractor = new OtpExtractorEngine(
                Collections.emptyList(), Collections.emptyList());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = Collections.nCopies(
                    64, () -> extractor.extract("Your verification code is 123456."));
            for (Future<String> result : executor.invokeAll(calls)) {
                assertEquals("123456", result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> T checkNotNull(T value) {
        if (value == null) {
            throw new AssertionError("Missing code_detection_tests.yaml");
        }
        return value;
    }
}
