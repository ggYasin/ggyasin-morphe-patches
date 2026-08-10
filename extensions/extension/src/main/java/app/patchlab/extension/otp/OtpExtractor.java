package app.patchlab.extension.otp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Body-only runtime entry point used by the ZenSMS OTP bytecode patch. */
public final class OtpExtractor {
    private static final String SENSITIVE_MARKER =
            "__PATCHLAB_ADDITIONAL_SENSITIVE_PHRASES__";
    private static final String IGNORED_MARKER =
            "__PATCHLAB_ADDITIONAL_IGNORED_PHRASES__";

    private static final OtpExtractorEngine ENGINE = new OtpExtractorEngine(
            decodeAdditionalPhrases(additionalSensitivePhrases()),
            decodeAdditionalPhrases(additionalIgnoredPhrases())
    );

    private OtpExtractor() {
    }

    /** Returns true when this message must not fall through to ZenSMS detection. */
    public static boolean shouldIgnore(String body) {
        return ENGINE.shouldIgnore(body);
    }

    /** Returns OTPHelper's preferred code, or null so the patch can call ZenSMS. */
    public static String extract(String body) {
        return ENGINE.extract(body);
    }

    /* Replaced by the Morphe patch when its corresponding list option is set. */
    private static String additionalSensitivePhrases() {
        return "__PATCHLAB_ADDITIONAL_SENSITIVE_PHRASES__";
    }

    /* Replaced by the Morphe patch when its corresponding list option is set. */
    private static String additionalIgnoredPhrases() {
        return "__PATCHLAB_ADDITIONAL_IGNORED_PHRASES__";
    }

    private static List<String> decodeAdditionalPhrases(String encoded) {
        if (encoded == null || encoded.isEmpty()
                || SENSITIVE_MARKER.equals(encoded) || IGNORED_MARKER.equals(encoded)) {
            return Collections.emptyList();
        }

        String[] lines = encoded.split("\\n", -1);
        List<String> phrases = new ArrayList<>(lines.length);
        for (String line : lines) {
            if (!line.isEmpty()) {
                phrases.add(line);
            }
        }
        return phrases;
    }
}
