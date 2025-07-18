package com.qy.session;

import lombok.Data;

@Data
public class UserSession {
    /**
     * 用户id
     */
    private Long userId;

    /**
     * 终端类型
     */
    private Integer terminal;

    /**
     * 用户名称
     */
    private String userName;

    /**
     * 用户昵称
     */
    private String nickName;
}
