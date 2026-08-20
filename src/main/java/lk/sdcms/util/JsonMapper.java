package lk.sdcms.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Shared Gson instance.
 *
 * <p>Gson does not handle java.time out of the box — it falls back on
 * reflection over the internal fields of LocalDate and produces unusable JSON.
 * BigDecimal is likewise emitted in scientific notation for some values, which
 * is wrong for currency. Both need explicit adapters, registered once here
 * rather than rediscovered at each call site.
 */
public final class JsonMapper {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class,     new LocalDateAdapter())
            .registerTypeAdapter(LocalTime.class,     new LocalTimeAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(BigDecimal.class,    new BigDecimalAdapter())
            .create();

    private JsonMapper() {
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    // -----------------------------------------------------------------

    private static class LocalDateAdapter
            implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {

        @Override
        public JsonElement serialize(LocalDate src, Type t, JsonSerializationContext c) {
            return new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        @Override
        public LocalDate deserialize(JsonElement e, Type t, JsonDeserializationContext c) {
            return LocalDate.parse(e.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    private static class LocalTimeAdapter
            implements JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {

        private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

        @Override
        public JsonElement serialize(LocalTime src, Type t, JsonSerializationContext c) {
            return new JsonPrimitive(src.format(HH_MM));
        }

        @Override
        public LocalTime deserialize(JsonElement e, Type t, JsonDeserializationContext c) {
            return LocalTime.parse(e.getAsString(), HH_MM);
        }
    }

    private static class LocalDateTimeAdapter
            implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

        @Override
        public JsonElement serialize(LocalDateTime src, Type t, JsonSerializationContext c) {
            return new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        @Override
        public LocalDateTime deserialize(JsonElement e, Type t, JsonDeserializationContext c) {
            return LocalDateTime.parse(e.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    /** Emits a plain decimal string so currency never appears as 2.85E+4. */
    private static class BigDecimalAdapter
            implements JsonSerializer<BigDecimal>, JsonDeserializer<BigDecimal> {

        @Override
        public JsonElement serialize(BigDecimal src, Type t, JsonSerializationContext c) {
            return new JsonPrimitive(src.toPlainString());
        }

        @Override
        public BigDecimal deserialize(JsonElement e, Type t, JsonDeserializationContext c) {
            return new BigDecimal(e.getAsString());
        }
    }
}
