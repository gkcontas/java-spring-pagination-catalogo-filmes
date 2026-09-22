package com.gkcontas.pagination.cursor;

import com.gkcontas.pagination.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Encodes and decodes the cursor as an opaque, URL-safe token.
 *
 * <p>Base64 here is not obfuscation — anyone can decode it. It is a contract decision:
 * an opaque token tells clients not to build cursors by hand or depend on the internal
 * sort key, which leaves us free to change that key later without breaking them.
 */
@Component
public class CursorCodec {

    private static final char SEPARATOR = '|';

    public String encode(Cursor cursor) {
        String raw = cursor.releaseDate() + String.valueOf(SEPARATOR) + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separatorIndex = raw.indexOf(SEPARATOR);
            if (separatorIndex < 0) {
                throw new InvalidCursorException(encoded);
            }
            LocalDate releaseDate = LocalDate.parse(raw.substring(0, separatorIndex));
            long id = Long.parseLong(raw.substring(separatorIndex + 1));
            return new Cursor(releaseDate, id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // A malformed cursor is client input, not a server fault: it must surface
            // as 400, never as a 500 stack trace.
            throw new InvalidCursorException(encoded);
        }
    }
}
