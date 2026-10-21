package com.zhuqiyun.platform;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Configuration
public class JsonConfig {
    @Bean public SimpleModule decimalStrings() {
        SimpleModule module = new SimpleModule("platform-values");
        module.addSerializer(BigDecimal.class,new ToStringSerializer(BigDecimal.class));
        module.addSerializer(Timestamp.class, new JsonSerializer<>() {
            @Override public void serialize(Timestamp value, JsonGenerator out, SerializerProvider provider) throws IOException {
                out.writeString(value.toInstant().toString());
            }
        });
        module.addSerializer(java.sql.Date.class, new JsonSerializer<>() {
            @Override public void serialize(java.sql.Date value, JsonGenerator out, SerializerProvider provider) throws IOException {
                out.writeString(value.toLocalDate().toString());
            }
        });
        // MyBatis maps TIMESTAMP to LocalDateTime using the UTC database session.
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override public void serialize(LocalDateTime value, JsonGenerator out, SerializerProvider provider) throws IOException {
                out.writeString(value.toInstant(ZoneOffset.UTC).toString());
            }
        });
        return module;
    }
}
