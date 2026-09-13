package com.qy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统用户（qy-im 库 im_user 表）
 * 邮件发送场景仅使用基础信息字段，其余业务字段未映射
 */
@Data
@TableName("im_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（唯一）
     */
    private String userName;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 注册邮箱（唯一）
     */
    private String email;

    /**
     * 是否禁用：0 正常，1 禁用
     */
    private Integer isDisable;

    /**
     * 是否封禁：0 正常，1 封禁
     */
    private Integer isBanned;

    /**
     * 逻辑删除：0 未删除，1 已删除
     */
    @TableLogic
    private Integer isDeleted;
}
