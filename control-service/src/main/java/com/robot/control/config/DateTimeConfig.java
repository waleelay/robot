package com.robot.control.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DateTimeConfig implements WebMvcConfigurer {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeFormatCustomizer() {
        return builder -> builder
                .serializers(new OffsetDateTimeSerializer(), new LocalDateTimeSerializer(), new InstantSerializer(), new DateSerializer())
                .deserializers(new OffsetDateTimeDeserializer(), new LocalDateTimeDeserializer(), new InstantDeserializer(), new DateDeserializer());
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, OffsetDateTime.class, DateTimeConfig::parseOffsetDateTime);
        registry.addConverter(String.class, LocalDateTime.class, DateTimeConfig::parseLocalDateTime);
        registry.addConverter(String.class, Instant.class, DateTimeConfig::parseInstant);
        registry.addConverter(String.class, Date.class, DateTimeConfig::parseDate);
    }

    public static String format(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).format(DATE_TIME_FORMATTER);
    }

    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    public static String format(Instant value) {
        return value == null ? null : value.atZone(DISPLAY_ZONE).format(DATE_TIME_FORMATTER);
    }

    public static String format(Date value) {
        return value == null ? null : format(value.toInstant());
    }

    public static Object normalize(Object value) {
        if (value instanceof OffsetDateTime time) {
            return format(time);
        }
        if (value instanceof LocalDateTime time) {
            return format(time);
        }
        if (value instanceof Instant time) {
            return format(time);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return format(OffsetDateTime.parse(text));
            } catch (DateTimeParseException ignored) {
                return value;
            }
        }
        return value;
    }

    public static OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER)
                    .atZone(DISPLAY_ZONE)
                    .toOffsetDateTime();
        }
    }

    public static LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        }
    }

    public static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return parseOffsetDateTime(value).toInstant();
        }
    }

    public static Date parseDate(String value) {
        Instant instant = parseInstant(value);
        return instant == null ? null : Date.from(instant);
    }

    private static final class OffsetDateTimeSerializer extends StdSerializer<OffsetDateTime> {
        private OffsetDateTimeSerializer() {
            super(OffsetDateTime.class);
        }

        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    private static final class OffsetDateTimeDeserializer extends StdDeserializer<OffsetDateTime> {
        private OffsetDateTimeDeserializer() {
            super(OffsetDateTime.class);
        }

        @Override
        public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseOffsetDateTime(parser.getValueAsString());
        }
    }

    private static final class LocalDateTimeSerializer extends StdSerializer<LocalDateTime> {
        private LocalDateTimeSerializer() {
            super(LocalDateTime.class);
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    private static final class LocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {
        private LocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseLocalDateTime(parser.getValueAsString());
        }
    }

    private static final class InstantSerializer extends StdSerializer<Instant> {
        private InstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    private static final class InstantDeserializer extends StdDeserializer<Instant> {
        private InstantDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseInstant(parser.getValueAsString());
        }
    }

    private static final class DateSerializer extends StdSerializer<Date> {
        private DateSerializer() {
            super(Date.class);
        }

        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    private static final class DateDeserializer extends StdDeserializer<Date> {
        private DateDeserializer() {
            super(Date.class);
        }

        @Override
        public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseDate(parser.getValueAsString());
        }
    }
}
