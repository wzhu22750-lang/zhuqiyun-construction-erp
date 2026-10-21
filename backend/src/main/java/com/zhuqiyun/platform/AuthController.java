package com.zhuqiyun.platform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Db db; private final Auth auth; private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    private final Map<String,Attempt> attempts = new ConcurrentHashMap<>();
    private final String dummyHash;
    private record Attempt(long window,int count) {}
    public AuthController(Db db,Auth auth,PasswordEncoder encoder) { this.db=db; this.auth=auth; this.encoder=encoder; this.dummyHash=encoder.encode("invalid-account-placeholder"); }
    @PostMapping("/login") @Transactional
    public CommonResult<?> login(@RequestBody Map<String,Object> body,HttpServletRequest request) {
        Fields.only(body,"username password");
        String username=Fields.text(body.get("username"),60,true);
        Fields.check(body.get("password") instanceof String,"密码不能为空");
        String password=(String)body.get("password");
        Fields.check(!password.isEmpty(),"密码不能为空");
        Fields.check(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"密码过长");
        String key=request.getRemoteAddr()+":"+username;
        long now=System.currentTimeMillis();
        if (attempts.size()>10000) attempts.entrySet().removeIf(e -> now-e.getValue().window()>60000);
        if (attempts.size()>10000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"登录请求过多");
        Attempt attempt=attempts.compute(key,(k,old) -> old==null || now-old.window()>60000 ? new Attempt(now,1) : new Attempt(old.window(),old.count()+1));
        if (attempt.count()>10) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"登录尝试过多，请稍后重试");
        var rows=db.list("SELECT id,password_hash,enabled FROM users WHERE username=? AND tenant_id=1",username);
        boolean matches=encoder.matches(password,rows.isEmpty()?dummyHash:String.valueOf(rows.get(0).get("password_hash")));
        if (rows.isEmpty() || !matches || !Boolean.TRUE.equals(rows.get(0).get("enabled"))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户名或密码错误");
        long userId=((Number)rows.get(0).get("id")).longValue();
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.jdbc().update("DELETE FROM sessions WHERE expires_at<=CURRENT_TIMESTAMP");
        db.jdbc().update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",SecurityConfig.tokenHash(token),userId,Timestamp.from(Instant.now().plus(24,ChronoUnit.HOURS)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId,null,List.of()));
        auth.audit("LOGIN","user",userId,"用户登录");
        attempts.remove(key);
        return CommonResult.success(Map.of("token",token,"user",auth.user()));
    }
    @GetMapping("/me") public CommonResult<?> me() { return CommonResult.success(auth.user()); }
    @PostMapping("/logout") @Transactional public CommonResult<?> logout(HttpServletRequest request) {
        auth.audit("LOGOUT","user",auth.id(),"用户退出");
        db.jdbc().update("DELETE FROM sessions WHERE token_hash=? AND user_id=?",SecurityConfig.tokenHash(request.getHeader("Authorization").substring(7)),auth.id());
        return CommonResult.success(true);
    }
}
