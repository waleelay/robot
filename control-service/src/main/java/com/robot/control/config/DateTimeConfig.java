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

/**
 * 统一 JSON 与 MVC 时间格式的配置。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Configuration
public class DateTimeConfig implements WebMvcConfigurer {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 注册 Jackson 时间序列化与反序列化规则。
     *
     * @return Jackson 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeFormatCustomizer() {
        return builder -> builder
                .serializers(new OffsetDateTimeSerializer(), new LocalDateTimeSerializer(), new InstantSerializer(), new DateSerializer())
                .deserializers(new OffsetDateTimeDeserializer(), new LocalDateTimeDeserializer(), new InstantDeserializer(), new DateDeserializer());
    }

    /**
     * 注册 MVC 请求参数时间转换器。
     *
     * @param registry 格式化注册表
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, OffsetDateTime.class, DateTimeConfig::parseOffsetDateTime);
        registry.addConverter(String.class, LocalDateTime.class, DateTimeConfig::parseLocalDateTime);
        registry.addConverter(String.class, Instant.class, DateTimeConfig::parseInstant);
        registry.addConverter(String.class, Date.class, DateTimeConfig::parseDate);
    }

    /**
     * 将时间值格式化为前端展示字符串。
     *
     * @param value 待处理值
     * @return 格式化后的时间字符串
     */
    public static String format(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).format(DATE_TIME_FORMATTER);
    }

    /**
     * 将时间值格式化为前端展示字符串。
     *
     * @param value 待处理值
     * @return 格式化后的时间字符串
     */
    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    /**
     * 将时间值格式化为前端展示字符串。
     *
     * @param value 待处理值
     * @return 格式化后的时间字符串
     */
    public static String format(Instant value) {
        return value == null ? null : value.atZone(DISPLAY_ZONE).format(DATE_TIME_FORMATTER);
    }

    /**
     * 将时间值格式化为前端展示字符串。
     *
     * @param value 待处理值
     * @return 格式化后的时间字符串
     */
    public static String format(Date value) {
        return value == null ? null : format(value.toInstant());
    }

    /**
     * 将事件载荷中的时间值规范化为字符串。
     *
     * @param value 待处理值
     * @return 规范化后的值
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
     * 解析 OffsetDateTime 字符串，兼容本地展示格式。
     *
     * @param value 待处理值
     * @return 解析后的 OffsetDateTime
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
     * 解析 LocalDateTime 字符串，兼容本地展示格式。
     *
     * @param value 待处理值
     * @return 解析后的 LocalDateTime
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
     * 解析 Instant 字符串，兼容带时区和本地展示格式。
     *
     * @param value 待处理值
     * @return 解析后的 Instant
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
     * 解析 Date 字符串。
     *
     * @param value 待处理值
     * @return 解析后的 Date
     */
    public static Date parseDate(String value) {
        Instant instant = parseInstant(value);
        return instant == null ? null : Date.from(instant);
    }

    /**
     * OffsetDateTime 的上海时区字符串序列化器。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static final class OffsetDateTimeSerializer extends StdSerializer<OffsetDateTime> {
        /**
         * 创建 OffsetDateTimeSerializer 实例。
         */
        private OffsetDateTimeSerializer() {
            super(OffsetDateTime.class);
        }

        /**
         * 将时间值序列化为前端展示字符串。
         *
         * @param value 待处理值
         * @param gen gen
         * @param provider provider
         * @throws IOException IOException 处理失败时抛出
         */
        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    /**
     * OffsetDateTime 的字符串反序列化器。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static final class OffsetDateTimeDeserializer extends StdDeserializer<OffsetDateTime> {
        /**
         * 创建 OffsetDateTimeDeserializer 实例。
         */
        private OffsetDateTimeDeserializer() {
            super(OffsetDateTime.class);
        }

        /**
         * 将字符串反序列化为时间值。
         *
         * @param parser parser
         * @param context context
         * @return 反序列化后的时间值
         * @throws IOException IOException 处理失败时抛出
         */
        @Override
        public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseOffsetDateTime(parser.getValueAsString());
        }
    }

    /**
     * LocalDateTime 的字符串序列化器。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static final class LocalDateTimeSerializer extends StdSerializer<LocalDateTime> {
        /**
         * 创建 LocalDateTimeSerializer 实例。
         */
        private LocalDateTimeSerializer() {
            super(LocalDateTime.class);
        }

        /**
         * 将时间值序列化为前端展示字符串。
         *
         * @param value 待处理值
         * @param gen gen
         * @param provider provider
         * @throws IOException IOException 处理失败时抛出
         */
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    /**
     * LocalDateTime 的字符串反序列化器。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static final class LocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {
        /**
         * 创建 LocalDateTimeDeserializer 实例。
         */
        private LocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        /**
         * 将字符串反序列化为时间值。
         *
         * @param parser parser
         * @param context context
         * @return 反序列化后的时间值
         * @throws IOException IOException 处理失败时抛出
         */
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseLocalDateTime(parser.getValueAsString());
        }
    }

    /**
     * Instant 的上海时区字符串序列化器。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static final class InstantSerializer extends StdSerializer<Instant> {
        /**
         * 创建 InstantSerializer 实例。
         */
        private InstantSerializer() {
            super(Instant.class);
        }

        /**
         * 将时间值序列化为前端展示字符串。
         *
         * @param value 待处理值
         * @param gen gen
         * @param provider provider
         * @throws IOException IOException 处理失败时抛出
         */
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    /**
     * Instant 的字符串反序列化器。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static final class InstantDeserializer extends StdDeserializer<Instant> {
        /**
         * 创建 InstantDeserializer 实例。
         */
        private InstantDeserializer() {
            super(Instant.class);
        }

        /**
         * 将字符串反序列化为时间值。
         *
         * @param parser parser
         * @param context context
         * @return 反序列化后的时间值
         * @throws IOException IOException 处理失败时抛出
         */
        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseInstant(parser.getValueAsString());
        }
    }

    /**
     * Date 的上海时区字符串序列化器。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static final class DateSerializer extends StdSerializer<Date> {
        /**
         * 创建 DateSerializer 实例。
         */
        private DateSerializer() {
            super(Date.class);
        }

        /**
         * 将时间值序列化为前端展示字符串。
         *
         * @param value 待处理值
         * @param gen gen
         * @param provider provider
         * @throws IOException IOException 处理失败时抛出
         */
        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(format(value));
        }
    }

    /**
     * Date 的字符串反序列化器。
     *
     * @author leelay
     * @date 2026-07-05
     */
    private static final class DateDeserializer extends StdDeserializer<Date> {
        /**
         * 创建 DateDeserializer 实例。
         */
        private DateDeserializer() {
            super(Date.class);
        }

        /**
         * 将字符串反序列化为时间值。
         *
         * @param parser parser
         * @param context context
         * @return 反序列化后的时间值
         * @throws IOException IOException 处理失败时抛出
         */
        @Override
        public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parseDate(parser.getValueAsString());
        }
    }
}
