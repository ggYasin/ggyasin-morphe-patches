package app.patchlab.extension.otp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Java implementation of OTPHelper's code extraction rules. */
final class OtpExtractorEngine {
    private static final int FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.UNICODE_CASE;
    private static final String DIGITS = "\\d\\u0660-\\u0669\\u06F0-\\u06F9";

    private static final List<String> DEFAULT_SENSITIVE_PHRASES = Collections.unmodifiableList(
            Arrays.asList(
                    "code",
                    "One[-\\s]Time[-\\s]Password",
                    "کد",
                    "رمز",
                    "\\bOTP\\W",
                    "\\b2FA\\W",
                    "Einmalkennwort",
                    "contraseña",
                    "c[oó]digo",
                    "clave",
                    "\\bel siguiente PIN\\W",
                    "验证码",
                    "校验码",
                    "識別碼",
                    "認證",
                    "驗證",
                    "код",
                    "סיסמ",
                    "\\bהקוד\\W",
                    "\\bקוד\\W",
                    "\\bKodu\\W",
                    "\\bKodunuz\\W",
                    "\\b[sş]ifre:\\W",
                    "\\bKodi\\W",
                    "\\bKods\\W",
                    "\\b(?:m|sms)?TAN\\W",
                    "\\bcodice\\W",
                    "コード",
                    "パスワード",
                    "認証番号",
                    "ワンタイム",
                    "\\bvahvistuskoodi",
                    "\\bkertakäyttökoodisi\\W",
                    "\\bkod\\W",
                    "\\bautoryzacji\\W",
                    "Parol\\s+dlya\\s+podtverzhdeniya",
                    "\\bпароль\\W",
                    "인증번호"
            )
    );

    private static final List<String> SKIP_PHRASES = Collections.unmodifiableList(
            Arrays.asList(
                    "مقدار",
                    "مبلغ",
                    "amount",
                    "برای",
                    "-ارز",
                    "[a-zA-Z0-9] [a-zA-Z0-9] [a-zA-Z0-9] [a-zA-Z0-9] ?"
            )
    );

    private static final List<String> CURRENCY_INDICATORS = Collections.unmodifiableList(
            Arrays.asList("USD", "EUR", "GBP", "[$€£]")
    );

    private static final List<String> DEFAULT_IGNORED_PHRASES = Collections.unmodifiableList(
            Arrays.asList(
                    "تخفیف",
                    "takhfif",
                    "off",
                    "اشتباه وارد شده",
                    "RatingCode",
                    "vscode",
                    "versionCode",
                    "unicode",
                    "discount code",
                    "fancode",
                    "encode",
                    "decode",
                    "barcode",
                    "codex"
            )
    );

    private static final List<String> CLEANUP_PHRASES = Collections.unmodifiableList(
            Arrays.asList(
                    "[a-zA-Z0-9][a-zA-Z0-9-]{0,61}\\.[a-zA-Z]{2,}(?:[.a-zA-Z]{0,3}(?=\\s+)|)",
                    "['\"]",
                    "Endziffer-\\d+",
                    "Ending \\d+",
                    "<#>",
                    "share OTP"
            )
    );

    private final Pattern generalCodeMatcher;
    private final Pattern specialCodeMatcher;
    private final Pattern ignoredPhrasesMatcher;
    private final Pattern cleanupPhrasesMatcher;

    OtpExtractorEngine(List<String> additionalSensitivePhrases,
                       List<String> additionalIgnoredPhrases) {
        List<String> sensitivePhrases = withUnicodeBoundaries(append(
                DEFAULT_SENSITIVE_PHRASES, additionalSensitivePhrases));
        List<String> ignoredPhrases = withUnicodeBoundaries(append(
                DEFAULT_IGNORED_PHRASES, additionalIgnoredPhrases));

        String sensitiveAlternation = joinWithPipe(sensitivePhrases);
        String digitClass = "[" + DIGITS + "]";
        String nonDigitClass = "[^" + DIGITS + "]";

        String generalPattern =
                "(" + sensitiveAlternation + ")"
                        + "(?:\\s*(?!" + joinWithPipe(SKIP_PHRASES) + ")"
                        + "(?:[^\\s:：܃︓﹕.'\"" + DIGITS + "]"
                        + "|[" + DIGITS + ",\\s]+(?:"
                        + joinWithPipe(CURRENCY_INDICATORS) + ")"
                        + "|" + digitClass + nonDigitClass + "))*"
                        + "\\s*[:：܃︓﹕]?\\s*([\"'「]?)"
                        + "([" + DIGITS + "a-zA-Z\\-]{4,}"
                        + "|(?: [" + DIGITS + "a-zA-Z]){4,}|)"
                        + "\\1?(?:[^" + DIGITS + "a-zA-Z]|$)";

        String specialPattern =
                "((?:" + digitClass + "-?){4,}(?=\\s)"
                        + "|[" + DIGITS + " ]{4,}(?=\\s)"
                        + "|" + digitClass + "{4,})"
                        + "[^:]*(" + sensitiveAlternation + ")";

        generalCodeMatcher = Pattern.compile(generalPattern, FLAGS);
        specialCodeMatcher = Pattern.compile(specialPattern, FLAGS);
        ignoredPhrasesMatcher = Pattern.compile(
                "(?U:\\b)(" + joinWithPipe(ignoredPhrases) + ")(?U:\\b)", FLAGS);
        cleanupPhrasesMatcher = Pattern.compile(
                "(" + joinWithPipe(CLEANUP_PHRASES) + ")", FLAGS);
    }

    boolean shouldIgnore(String body) {
        return body != null && !body.isEmpty() && ignoredPhrasesMatcher.matcher(body).find();
    }

    String extract(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        String cleanBody = cleanupPhrasesMatcher.matcher(body).replaceAll("");
        Matcher generalMatcher = generalCodeMatcher.matcher(cleanBody);
        boolean foundGeneralContext = false;
        while (generalMatcher.find()) {
            foundGeneralContext = true;
            String code = generalMatcher.group(3);
            if (code != null && !code.isEmpty()) {
                return normalizeCode(code);
            }
        }

        if (foundGeneralContext) {
            Matcher specialMatcher = specialCodeMatcher.matcher(cleanBody);
            if (specialMatcher.find()) {
                String code = specialMatcher.group(1);
                if (code != null && !code.trim().isEmpty()) {
                    return normalizeCode(code);
                }
            }
        }

        return null;
    }

    private static List<String> append(List<String> defaults, List<String> additions) {
        if (additions == null || additions.isEmpty()) {
            return defaults;
        }
        List<String> combined = new ArrayList<>(defaults.size() + additions.size());
        combined.addAll(defaults);
        combined.addAll(additions);
        return combined;
    }

    private static String joinWithPipe(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) {
                joined.append('|');
            }
            joined.append(value);
        }
        return joined.toString();
    }

    private static List<String> withUnicodeBoundaries(List<String> patterns) {
        List<String> normalized = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            normalized.add(pattern.replace("\\b", "(?U:\\b)"));
        }
        return normalized;
    }

    private static String normalizeCode(String code) {
        String compact = code.replace(" ", "").replace("-", "");
        char[] chars = compact.toCharArray();
        for (int index = 0; index < chars.length; index++) {
            char character = chars[index];
            if (character >= '\u0660' && character <= '\u0669') {
                chars[index] = (char) (character - '\u0660' + '0');
            } else if (character >= '\u06F0' && character <= '\u06F9') {
                chars[index] = (char) (character - '\u06F0' + '0');
            }
        }
        return new String(chars);
    }
}
