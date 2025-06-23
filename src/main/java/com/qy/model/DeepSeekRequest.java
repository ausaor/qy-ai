package com.qy.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeepSeekRequest {
    private String model;
    private List<Message> messages;
    private boolean stream;
    private double temperature;
    private int max_tokens;

    public DeepSeekRequest(String model, String prompt, boolean stream, double temperature, int max_tokens) {
        this.model = model;
        this.stream = stream;
        this.temperature = temperature;
        this.max_tokens = max_tokens;
        this.messages = new ArrayList<>();
        this.messages.add(new Message("user", prompt));
    }

    @Data
    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
