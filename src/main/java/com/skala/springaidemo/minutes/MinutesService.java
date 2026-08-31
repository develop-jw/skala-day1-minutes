package com.skala.springaidemo.minutes;

import java.util.logging.Logger;

import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
    }


    public MeetingReport report(String minutes) {
        ChatResponse response = chat.prompt()
            .system(reportPrompt)
            .user(minutes)
            .call()
            .entity(MeetingReport.class);
    }


    public Flux<String> streamSummary(String minutes) {

    }
    
}

    