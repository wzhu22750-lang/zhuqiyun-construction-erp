package com.zhuqiyun.platform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    public static String tokenHash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static void error(HttpServletResponse response, ObjectMapper json, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), CommonResult.error(status, message));
    }
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http, Db db, ObjectMapper json) throws Exception {
        var bearer = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                if (header != null) {
                    if (!header.startsWith("Bearer ") || header.length() > 512) { error(response,json,401,"登录令牌无效"); return; }
                    String token = header.substring(7);
                    var rows = db.list("SELECT u.id FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND u.enabled=TRUE AND u.tenant_id=1", tokenHash(token));
                    if (rows.isEmpty()) { error(response,json,401,"登录已失效，请重新登录"); return; }
                    long id = ((Number)rows.get(0).get("id")).longValue();
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of()));
                }
                chain.doFilter(request,response);
            }
        };
        return http.csrf(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(c -> c.requestMatchers(HttpMethod.POST,"/api/auth/login").permitAll().requestMatchers("/error").permitAll().anyRequest().authenticated())
            .exceptionHandling(c -> c.authenticationEntryPoint((q,r,e) -> error(r,json,401,"请先登录"))
                .accessDeniedHandler((q,r,e) -> error(r,json,403,"无权访问")))
            .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class).build();
    }
}
