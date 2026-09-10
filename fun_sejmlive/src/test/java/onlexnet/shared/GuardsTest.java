package onlexnet.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
class GuardsTest {

    @Test
    void orDefaultIfNullOrEmpty_collection_givenNull_returnsDefault() {
        var result = Guards.orDefaultIfNullOrEmpty(null, List.of("default"));
        assertThat(result).isEqualTo(List.of("default"));
    }

    @Test
    void orDefaultIfNullOrEmpty_collection_givenEmpty_returnsDefault() {
        var result = Guards.orDefaultIfNullOrEmpty(List.of(), List.of("default"));
        assertThat(result).isEqualTo(List.of("default"));
    }

    @Test
    void orDefaultIfNullOrEmpty_collection_givenNonEmpty_returnsValue() {
        var result = Guards.orDefaultIfNullOrEmpty(List.of("a"), List.of("default"));
        assertThat(result).isEqualTo(List.of("a"));
    }

    @Test
    void orDefaultIfNullOrEmpty_collection_givenNullDefault_returnsNull() {
        assertThat(Guards.orDefaultIfNullOrEmpty(List.of(), null)).isNull();
    }

    @Test
    void orDefaultIfNullOrEmpty_string_givenNull_returnsDefault() {
        assertThat(Guards.orDefaultIfNullOrEmpty(null, "default")).isEqualTo("default");
    }

    @Test
    void orDefaultIfNullOrEmpty_string_givenEmpty_returnsDefault() {
        assertThat(Guards.orDefaultIfNullOrEmpty("", "default")).isEqualTo("default");
    }

    @Test
    void orDefaultIfNullOrEmpty_string_givenNonEmpty_returnsValue() {
        assertThat(Guards.orDefaultIfNullOrEmpty("value", "default")).isEqualTo("value");
    }

    @Test
    void orDefaultIfNullOrEmpty_string_givenNullDefault_returnsNull() {
        assertThat(Guards.orDefaultIfNullOrEmpty("", null)).isNull();
    }

    @Test
    void requireNonEmpty_collection_givenNonEmpty_returnsValue() {
        var input = List.of("x");
        var result = Guards.requireNonEmpty(input, IllegalStateException::new);
        assertThat(result).isSameAs(input);
    }

    @Test
    void requireNonEmpty_collection_givenNull_throwsSupplied() {
        List<String> nullList = null;
        assertThatThrownBy(() -> Guards.requireNonEmpty(nullList, () -> new IllegalStateException("empty")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("empty");
    }

    @Test
    void requireNonEmpty_collection_givenEmpty_throwsSupplied() {
        assertThatThrownBy(() -> Guards.requireNonEmpty(List.of(), () -> new IllegalArgumentException("empty")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNonEmpty_collection_givenNullSupplier_throws() {
        assertThatThrownBy(() -> Guards.requireNonEmpty(List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requireNonEmpty_string_givenNonEmpty_returnsValue() {
        assertThat(Guards.requireNonEmpty("abc", IllegalStateException::new)).isEqualTo("abc");
    }

    @Test
    void requireNonEmpty_string_givenNull_throwsSupplied() {
        String nullStr = null;
        assertThatThrownBy(() -> Guards.requireNonEmpty(nullStr, () -> new IllegalStateException("empty")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("empty");
    }

    @Test
    void requireNonEmpty_string_givenEmpty_throwsSupplied() {
        assertThatThrownBy(() -> Guards.requireNonEmpty("", () -> new IllegalArgumentException("empty")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNonEmpty_string_givenNullSupplierAndNonEmptyValue_returnsValue() {
        assertThat(Guards.requireNonEmpty("x", (java.util.function.Supplier<RuntimeException>) null)).isEqualTo("x");
    }

    @Test
    void checkState_givenTrue_doesNotThrow() {
        Guards.checkState(true, () -> "should not throw");
    }

    @Test
    void checkState_givenFalse_throwsIllegalStateExceptionWithSuppliedMessage() {
        assertThatThrownBy(() -> Guards.checkState(false, () -> "state-invalid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("state-invalid");
    }

    @Test
    void requireState_givenNonNull_returnsValue() {
        var value = "ok";
        assertThat(Guards.requireState(value, () -> "missing")).isSameAs(value);
    }

    @Test
    void requireState_givenNull_throwsIllegalStateExceptionWithSuppliedMessage() {
        String value = null;
        assertThatThrownBy(() -> Guards.requireState(value, () -> "missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("missing");
    }
}
