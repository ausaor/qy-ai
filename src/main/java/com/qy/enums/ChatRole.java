package com.qy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatRole {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system");

    private final String role;
}
