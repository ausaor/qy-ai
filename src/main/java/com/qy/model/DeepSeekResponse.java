package com.qy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepSeekResponse {
    private String id;

    private String object;

    private long created;

    private String model;

    private Choice[] choices;

    private Usage usage;

    @Data
    public static class Choice {
        private Delta delta;

        private int index;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Delta {
        private String content;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("completion_tokens")
        private int completionTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}
