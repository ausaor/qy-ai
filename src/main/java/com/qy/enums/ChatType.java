package com.qy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatType {
    CHAT("chat", "智能问答"),

    AGENT("agent", "智能体");

    private final String code;

    private final String desc;
}
