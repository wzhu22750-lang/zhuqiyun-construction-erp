package com.zhuqiyun.platform;

import com.zaxxer.hikari.HikariDataSource;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.TimeZone;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Keep TIMESTAMP instants stable across JVM, database and external DB_URL timezones. */
@Configuration
public class JdbcTimeConfig {
    private static final Set<String> ZONE_OPTIONS = Set.of("servertimezone", "timezone", "connectiontimezone",
        "forceconnectiontimezonetosession", "preserveinstants");

    static String utcUrl(String url) {
        int query = url.indexOf('?');
        String base = query < 0 ? url : url.substring(0, query);
        List<String> options = new ArrayList<>();
        if (query >= 0) for (String option : url.substring(query + 1).split("&")) {
            String key = URLDecoder.decode(option.split("=", 2)[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (!option.isEmpty() && !ZONE_OPTIONS.contains(key)) options.add(option);
        }
        options.add("connectionTimeZone=UTC");
        options.add("forceConnectionTimeZoneToSession=true");
        options.add("preserveInstants=true");
        return base + "?" + String.join("&", options);
    }

    @Bean public ConfigurationCustomizer utcMybatisTimestamps() {
        return configuration -> configuration.getTypeHandlerRegistry()
            .register(LocalDateTime.class, new UtcLocalDateTimeHandler());
    }

    /** LocalDateTime represents UTC wall time at our SQL/JSON boundary. */
    static class UtcLocalDateTimeHandler extends BaseTypeHandler<LocalDateTime> {
        private Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
        private LocalDateTime local(Timestamp timestamp) {
            return timestamp == null ? null : LocalDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        }
        @Override public void setNonNullParameter(PreparedStatement ps, int index, LocalDateTime value, JdbcType type) throws SQLException {
            ps.setTimestamp(index, Timestamp.from(value.toInstant(ZoneOffset.UTC)), utc());
        }
        @Override public LocalDateTime getNullableResult(ResultSet rs, String column) throws SQLException {
            return local(rs.getTimestamp(column, utc()));
        }
        @Override public LocalDateTime getNullableResult(ResultSet rs, int column) throws SQLException {
            return local(rs.getTimestamp(column, utc()));
        }
        @Override public LocalDateTime getNullableResult(CallableStatement cs, int column) throws SQLException {
            return local(cs.getTimestamp(column, utc()));
        }
    }

    @Bean public static BeanPostProcessor utcDatabaseSessions() {
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                // Apply after configuration binding, including an externally supplied legacy DB_URL.
                // H2 and other test drivers must not receive MariaDB-specific options.
                if (bean instanceof HikariDataSource pool
                        && "org.mariadb.jdbc.Driver".equals(pool.getDriverClassName()) && pool.getJdbcUrl() != null) {
                    pool.setJdbcUrl(utcUrl(pool.getJdbcUrl()));
                }
                return bean;
            }
        };
    }
}
