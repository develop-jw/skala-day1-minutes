package com.skala.minutes.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /* 회의록 요약 봇은 이전 대화를 기억할 필요가 없음 */
    @Bean
    public ChatClient chatClient(
        @Value("${app.provider}") String provider,
        @Value("${app.temperature}") double temperature,
        @Qualifier("openAiChatModel") ChatModel openaiModel
    ) {
        return ChatClient.builder(openaiModel)
            .defaultOptions(ChatOptions.builder()
                    .temperature(temperature)
                    .build())
            .build();
    }
}