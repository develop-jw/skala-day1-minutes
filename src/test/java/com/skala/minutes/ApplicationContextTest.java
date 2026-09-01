package com.skala.minutes;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 키가 없어도 애플리케이션이 뜨는지 본다.
 * 공급자를 바꿔도 이 테스트는 그대로 통과해야 한다 — 바뀌는 것은 설정뿐이기 때문이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextTest {

    @Autowired ChatClient chatClient;

    @Test
    void 키가_없어도_기동된다() {
        assertThat(chatClient).isNotNull();
    }
}
