package onlexnet.shared;

import java.util.Collection;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Utility methods for handling nullable empty values consistently.
 */
public final class Guards {

    private Guards() {
    }

    /**
     * Returns the provided value when it is non-null and non-empty; otherwise returns default value.
     *
     * @param value nullable collection value
     * @param defaultValue value returned when input is null or empty
     * @param <T> collection type
     * @return non-empty value or defaultValue when value is null/empty
     */
    public static <T extends Collection<?>> T orDefaultIfNullOrEmpty(@Nullable T value, T defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    /**
     * Returns the provided string when it is non-null and non-empty; otherwise returns default value.
     *
     * @param value nullable string
     * @param defaultValue value returned when input is null or empty
     * @return non-empty input string or defaultValue
     */
    public static String orDefaultIfNullOrEmpty(@Nullable String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    /**
     * Returns the provided value when it is non-null and non-empty; otherwise throws supplied exception.
     *
     * @param value nullable collection value
     * @param exceptionSupplier supplier for exception when input is null/empty
     * @param <T> collection type
     * @param <X> exception type
     * @return non-empty collection value
     * @throws X when value is null or empty
     */
    public static <T extends Collection<?>, X extends RuntimeException> T requireNonEmpty(
            @Nullable T value,
            Supplier<X> exceptionSupplier) throws X {
        if (value == null || value.isEmpty()) {
            throw exceptionSupplier.get();
        }
        return value;
    }

    /**
     * Returns the provided string when it is non-null and non-empty; otherwise throws supplied exception.
     *
     * @param value nullable string
     * @param exceptionSupplier supplier for exception when input is null/empty
     * @param <X> exception type
     * @return non-empty string value
     * @throws X when value is null or empty
     */
    public static <X extends RuntimeException> String requireNonEmpty(
            @Nullable String value,
            Supplier<X> exceptionSupplier) throws X {
        if (value == null || value.isEmpty()) {
            throw exceptionSupplier.get();
        }
        return value;
    }
}
