package com.qy.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qy.config.JwtProperties;
import com.qy.contant.RedisKey;
import com.qy.enums.ResultCode;
import com.qy.exception.GlobalException;
import com.qy.session.UserSession;
import com.qy.util.JwtUtil;
import com.qy.util.RedisCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@AllArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    private final RedisCache redisCache;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        //如果不是映射到方法直接通过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        //从 http 请求头中取出 token
        String token = request.getHeader("accessToken");
        if (StrUtil.isEmpty(token)) {
            log.error("未登陆，url:{}", request.getRequestURI());
            throw new GlobalException(ResultCode.NO_LOGIN);
        }
        String strJson = JwtUtil.getInfo(token);
        UserSession userSession = JSONUtil.toBean(strJson, UserSession.class);
        if (redisCache.hasKey(RedisKey.IM_USER_BAN_ACCOUNT + userSession.getUserId())) {
            throw new GlobalException("账号已被封禁");
        }

        //验证 token
        if (!JwtUtil.checkSign(token, jwtProperties.getAccessTokenSecret())) {
            log.error("token已失效，用户:{}", userSession.getUserName());
            log.error("token:{}", token);
            throw new GlobalException(ResultCode.INVALID_TOKEN);
        }
        // 存放session
        request.setAttribute("session", userSession);
        return true;
    }
}
