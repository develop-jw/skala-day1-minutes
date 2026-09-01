package com.skala.minutes.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 진짜 모델 대신 끼우는 가짜 모델.
 *
 * 정해진 답을 그대로 돌려주면서, 우리가 보낸 프롬프트를 붙잡아 둔다.
 * 덕분에 키도 네트워크도 없이 "프롬프트를 제대로 조립했는가" 까지 확인할 수 있다.
 * ChatClient 의 체인(.system().user().call().entity())은 진짜 그대로 돌아간다.
 */
public class FakeChatModel implements ChatModel {

    private final String reply;
    private volatile Prompt lastPrompt;

    public FakeChatModel(String reply) {
        this.reply = reply;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        this.lastPrompt = prompt;
        return response(reply);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        this.lastPrompt = prompt;
        return Flux.fromIterable(chunks(reply)).map(FakeChatModel::response);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** 스트리밍처럼 보이도록 공백 단위로 쪼갠다. */
    private static List<String> chunks(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.split(" ")) {
            out.add(out.isEmpty() ? w : " " + w);
        }
        return out;
    }

    // ── 우리가 보낸 프롬프트 들여다보기 ────────────────────────────

    public String systemText() {
        return textOf(MessageType.SYSTEM);
    }

    public String userText() {
        return textOf(MessageType.USER);
    }

    private String textOf(MessageType type) {
        if (lastPrompt == null) {
            return "";
        }
        for (Message m : lastPrompt.getInstructions()) {
            if (m.getMessageType() == type) {
                return m.getText();
            }
        }
        return "";
    }
}
