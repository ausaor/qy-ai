package com.qy.contant;

public interface RedisKey {
    // 缓存前缀
    public final static String  IM_CACHE = "im:cache:";

    // 用户账号封禁key
    public final static String IM_USER_BAN_ACCOUNT = IM_CACHE + "user:ban-account:";
}
