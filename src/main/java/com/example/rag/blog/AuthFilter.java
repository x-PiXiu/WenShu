package com.example.rag.blog;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;

/**
 * 管理员鉴权过滤器：验证 X-Admin-Token 请求头
 */
public class AuthFilter implements Handler {

    private final String adminPassword;

    public AuthFilter(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    /**
     * 验证管理员 Token
     * Token 格式: base64(password + ":" + timestamp)
     */
    public boolean isAuthenticated(Context ctx) {
        String token = ctx.header("X-Admin-Token");
        if (token == null || token.isBlank()) return false;

        try {
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":", 2);
            if (parts.length < 1) return false;
            return adminPassword.equals(parts[0]);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成 Token
     */
    public static String generateToken(String password) {
        String raw = password + ":" + System.currentTimeMillis();
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        if (!isAuthenticated(ctx)) {
            ctx.status(401).json(java.util.Map.of("error", "Unauthorized: invalid or missing admin token"));
        }
    }
}
