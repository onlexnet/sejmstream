package onlexnet.shared;

import java.time.DateTimeException;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * Conversion helpers for JSON wire contracts that encode LocalDate as yyyyMMdd numbers.
 */
public final class JsonDateNumbers {

    private static final int MIN_YYYY_MM_DD = 10_000_101;
    private static final int MAX_YYYY_MM_DD = 99_991_231;

    private JsonDateNumbers() {
    }

    public static int toYyyyMmDd(LocalDate value) {
        return value.getYear() * 10_000 + value.getMonthValue() * 100 + value.getDayOfMonth();
    }

    public static @Nullable Integer toYyyyMmDdOrNull(@Nullable LocalDate value) {
        return value == null ? null : toYyyyMmDd(value);
    }

    public static LocalDate fromYyyyMmDd(int value) {
        if (value < MIN_YYYY_MM_DD || value > MAX_YYYY_MM_DD) {
            throw new IllegalArgumentException("Date number must use yyyyMMdd format: " + value);
        }
        var year = value / 10_000;
        var month = (value / 100) % 100;
        var day = value % 100;
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Date number is not a valid calendar date (yyyyMMdd): " + value, exception);
        }
    }

    public static @Nullable LocalDate fromYyyyMmDdOrNull(@Nullable Integer value) {
        return value == null ? null : fromYyyyMmDd(value);
    }
}