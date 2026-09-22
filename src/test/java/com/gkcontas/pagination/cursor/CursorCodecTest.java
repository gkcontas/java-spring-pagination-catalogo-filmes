package com.gkcontas.pagination.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.pagination.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

    private final CursorCodec cursorCodec = new CursorCodec();

    @Test
    void shouldSurviveARoundTrip() {
        Cursor original = new Cursor(LocalDate.of(2024, 10, 3), 159_999L);

        Cursor decoded = cursorCodec.decode(cursorCodec.encode(original));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void shouldProduceAnUrlSafeTokenWithoutPadding() {
        String encoded = cursorCodec.encode(new Cursor(LocalDate.of(1999, 1, 1), 7L));

        // A cursor travels in a query string, so '+', '/' and '=' would need escaping.
        assertThat(encoded).doesNotContain("+", "/", "=");
    }

    @Test
    void shouldRejectATokenThatIsNotValidBase64() {
        assertThatThrownBy(() -> cursorCodec.decode("not-a-valid-cursor"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void shouldRejectATokenWithoutTheSeparator() {
        String encoded = encodeRaw("2024-10-03");

        assertThatThrownBy(() -> cursorCodec.decode(encoded))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void shouldRejectATokenWithANonNumericId() {
        String encoded = encodeRaw("2024-10-03|abc");

        assertThatThrownBy(() -> cursorCodec.decode(encoded))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void shouldRejectATokenWithAnUnparseableDate() {
        String encoded = encodeRaw("03/10/2024|12");

        assertThatThrownBy(() -> cursorCodec.decode(encoded))
                .isInstanceOf(InvalidCursorException.class);
    }

    private static String encodeRaw(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
