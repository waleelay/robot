package com.robot.mediaserver.config;

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

/** 统一媒体接口的时间读写格式和请求参数转换，展示时区为上海。 */
@Configuration
public class DateTimeConfig implements WebMvcConfigurer {

    /**
     * 接口时间展示和无偏移输入所使用的上海时区。
     */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    /**
     * 不带时区后缀、精确到秒的接口时间格式。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 注册统一的上海时区时间序列化与反序列化规则。
     *
     * @return 供 Jackson 使用的时间模块定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeFormatCustomizer() {
        return builder -> builder
                .serializers(
                        new OffsetDateTimeSerializer(),
                        new LocalDateTimeSerializer(),
                        new InstantSerializer(),
                        new DateSerializer())
                .deserializers(
                        new OffsetDateTimeDeserializer(),
                        new LocalDateTimeDeserializer(),
                        new InstantDeserializer(),
                        new DateDeserializer());
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, OffsetDateTime.class, DateTimeConfig::parseOffsetDateTime);
        registry.addConverter(String.class, LocalDateTime.class, DateTimeConfig::parseLocalDateTime);
        registry.addConverter(String.class, Instant.class, DateTimeConfig::parseInstant);
        registry.addConverter(String.class, Date.class, DateTimeConfig::parseDate);
    }

    /**
     * 将给定时间归一为项目约定的上海时区字符串；空输入保持为空。
     *
     * @param value 待格式化或归一化的时间值；不额外推断业务时间
     * @return 格式化时间；输入为空时返回 null
     */
    public static String format(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).format(DATE_TIME_FORMATTER);
    }

    /**
     * 按原本地日期时间输出项目格式；输入不含时区，因此不进行时区转换，空输入保持为空。
     * @param value 待格式化或归一化的时间值；不额外推断业务时间
     * @return 格式化时间；输入为空时返回 null
     */
    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    /**
     * 将给定时间归一为项目约定的上海时区字符串；空输入保持为空。
     *
     * @param value 待格式化或归一化的时间值；不额外推断业务时间
     * @return 格式化时间；输入为空时返回 null
     */
    public static String format(Instant value) {
        return value == null ? null : value.atZone(DISPLAY_ZONE).format(DATE_TIME_FORMATTER);
    }

    /**
     * 将给定时间归一为项目约定的上海时区字符串；空输入保持为空。
     *
     * @param value 待格式化或归一化的时间值；不额外推断业务时间
     * @return 格式化时间；输入为空时返回 null
     */
    public static String format(Date value) {
        return value == null ? null : format(value.toInstant());
    }

    /**
     * 将可识别的时间值转换为项目时间字符串；不支持的类型或无法解析的字符串保持原值。
     *
     * @param value 待格式化或归一化的时间值；不额外推断业务时间
     * @return 格式化后的字符串或未转换的原值
     */
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

    /**
     * 解析项目时间字符串并补齐上海时区语义。
     *
     * @param value 待解析的时间字符串；空白按方法约定返回 null
     * @return 解析后的带偏移时间
     */
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

    /**
     * 解析接口中的本地日期时间。
     *
     * @param value 待解析的时间字符串；空白按方法约定返回 null
     * @return 解析后的本地时间
     */
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

    /**
     * 将项目时间字符串转换为绝对时间点。
     *
     * @param value 待解析的时间字符串；空白按方法约定返回 null
     * @return 解析后的时间点
     */
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

    /**
     * 将项目时间字符串转换为传统 Date。
     *
     * @param value 待解析的时间字符串；空白按方法约定返回 null
     * @return 解析后的日期对象
     */
    public static Date parseDate(String value) {
        Instant instant = parseInstant(value);
        return instant == null ? null : Date.from(instant);
    }

    /** 将带偏移量的时间转换为上海时区展示字符串。 */
    private static final class OffsetDateTimeSerializer extends StdSerializer<OffsetDateTime> {
        private static final long serialVersionUID = 1L;

        private OffsetDateTimeSerializer() {
            super(OffsetDateTime.class);
        }

        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    /** 将本地日期时间按接口约定格式输出，不附加时区后缀。 */
    private static final class LocalDateTimeSerializer extends StdSerializer<LocalDateTime> {
        private static final long serialVersionUID = 1L;

        private LocalDateTimeSerializer() {
            super(LocalDateTime.class);
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    /** 将时间点转换为上海时区展示字符串。 */
    private static final class InstantSerializer extends StdSerializer<Instant> {
        private static final long serialVersionUID = 1L;

        private InstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    /** 将旧版 Date 转换为统一的上海时区展示字符串。 */
    private static final class DateSerializer extends StdSerializer<Date> {
        private static final long serialVersionUID = 1L;

        private DateSerializer() {
            super(Date.class);
        }

        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    /** 解析带偏移量或约定本地格式的日期时间输入。 */
    private static final class OffsetDateTimeDeserializer extends StdDeserializer<OffsetDateTime> {
        private static final long serialVersionUID = 1L;

        private OffsetDateTimeDeserializer() {
            super(OffsetDateTime.class);
        }

        @Override
        public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseOffsetDateTime(parser.getValueAsString());
        }
    }

    /** 将受支持的日期时间输入解析为本地日期时间。 */
    private static final class LocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {
        private static final long serialVersionUID = 1L;

        private LocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseLocalDateTime(parser.getValueAsString());
        }
    }

    /** 将受支持的时间输入解析为绝对时间点。 */
    private static final class InstantDeserializer extends StdDeserializer<Instant> {
        private static final long serialVersionUID = 1L;

        private InstantDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseInstant(parser.getValueAsString());
        }
    }

    /** 将受支持的时间输入适配为旧版 Date。 */
    private static final class DateDeserializer extends StdDeserializer<Date> {
        private static final long serialVersionUID = 1L;

        private DateDeserializer() {
            super(Date.class);
        }

        @Override
        public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseDate(parser.getValueAsString());
        }
    }
}
