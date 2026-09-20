package grounded_villages.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small hand-written, dependency-free JSON reader for {@code grounded_villages.json}'s own
 * fixed shape (`decisions/DEC-008-config-file.md`: "no config mechanism is both present and
 * loader-neutral across this mod's six nodes" -- Fabric has no config API, NeoForge's own is
 * TOML/NeoForge-only, so a hand-rolled parser is the only loader-neutral option, the same shape
 * `villager_voices`' own dependency-free {@code MiniJson} already uses). A normal
 * recursive-descent JSON reader (objects, arrays, strings with the standard escapes, numbers,
 * booleans, {@code null}) -- not spec-complete or general-purpose; nothing here ever needs it to
 * be more than that.
 */
final class ConfigJson {

    private ConfigJson() {
    }

    /**
     * Parses {@code text} as one JSON value: a {@link Map}&lt;{@link String}, {@link Object}&gt;
     * for an object (insertion-ordered), a {@link List}&lt;{@link Object}&gt; for an array, a
     * {@link String}, a {@link Double}, a {@link Boolean}, or {@code null}.
     *
     * @throws JsonSyntaxException if {@code text} is not valid JSON, or has trailing content
     *     after its one top-level value
     */
    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    /** Malformed JSON text, reported with the character offset {@link ConfigJson} noticed it at. */
    static final class JsonSyntaxException extends RuntimeException {
        JsonSyntaxException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
            this.pos = 0;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        JsonSyntaxException error(String message) {
            return new JsonSyntaxException(message + " at offset " + pos);
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            return text.charAt(pos);
        }

        void expect(char c) {
            if (atEnd() || text.charAt(pos) != c) {
                throw error("expected '" + c + "'");
            }
            pos++;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (!atEnd() && peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || peek() != '"') {
                    throw error("expected a string key");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                    continue;
                }
                if (next == '}') {
                    pos++;
                    break;
                }
                throw error("expected ',' or '}'");
            }
            return result;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (!atEnd() && peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                Object value = parseValue();
                result.add(value);
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                    continue;
                }
                if (next == ']') {
                    pos++;
                    break;
                }
                throw error("expected ',' or ']'");
            }
            return result;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("unterminated escape");
                    }
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw error("incomplete unicode escape");
                            }
                            String hex = text.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException nfe) {
                                throw error("invalid unicode escape '\\u" + hex + "'");
                            }
                        }
                        default -> throw error("unknown escape '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("expected 'true' or 'false'");
        }

        Object parseNull() {
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("expected 'null'");
        }

        Double parseNumber() {
            int start = pos;
            if (!atEnd() && text.charAt(pos) == '-') {
                pos++;
            }
            while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (!atEnd() && text.charAt(pos) == '.') {
                pos++;
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (!atEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                pos++;
                if (!atEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (pos == start) {
                throw error("expected a value");
            }
            return Double.parseDouble(text.substring(start, pos));
        }
    }
}
