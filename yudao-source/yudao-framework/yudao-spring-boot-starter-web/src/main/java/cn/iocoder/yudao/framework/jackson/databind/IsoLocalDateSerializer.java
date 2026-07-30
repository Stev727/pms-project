package cn.iocoder.yudao.framework.jackson.databind;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 自定义 LocalDate 序列化器：输出 ISO-8601 字符串格式 "yyyy-MM-dd"
 * 解决 yudao 默认配置下 el-date-picker 无法显示数组格式 [yyyy, MM, dd] 的问题
 *
 * 用法：在 JacksonAutoConfiguration 中替换默认的 LocalDateSerializer.INSTANCE
 */
public class IsoLocalDateSerializer extends JsonSerializer<LocalDate> {

    public static final IsoLocalDateSerializer INSTANCE = new IsoLocalDateSerializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value.format(FORMATTER));
        }
    }

    @Override
    public Class<LocalDate> handledType() {
        return LocalDate.class;
    }
}