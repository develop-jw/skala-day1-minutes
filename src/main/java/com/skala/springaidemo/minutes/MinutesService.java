package com.skala.springaidemo.minutes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;


@Service
public class MinutesService {

    private static final Logger log = LoggerFactory.getLogger(MinutesService.class);
    
    private final ChatClient chat;
    private final int maxSentences;

    @Value("classpath:/prompts/summary.st")
    private Resource summaryPrompt;

    @Value("classpath:/prompts/report.st")
    private Resource reportPrompt;

    public MinutesService(ChatClient chat, @Value("${app.max-sentences}") int maxSentences) {
        this.chat = chat;
        this.maxSentences = maxSentences;
    }
        

    public String summarize(String minutes) {
        ChatResponse response = chat.prompt()
            .system(s -> s.text(summaryPrompt).param("maxSentences", maxSentences))
            .user(minutes)
            .call()
            .chatResponse();

        logUsage("summary", response);
        return response.getResult().getOutput().getText();
    }


    public MeetingReport report(String minutes) {
        return chat.prompt()
            .system(reportPrompt)
            .user(minutes)
            .call()
            .entity(MeetingReport.class);
    }


    public Flux<String> streamSummary(String minutes) {
        return chat.prompt()
            .system(s -> s.text(summaryPrompt).param("maxSentences", maxSentences))
            .user(minutes)
            .stream()
            .content();
    }

    void logUsage(String kind, ChatResponse response) {
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            log.debug("[{}] 토큰 정보 없음", kind);
            return;
        }
        log.info("[{}] 토큰 입력 {} 출력 {} 합계 {}", kind, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }
    
}

    