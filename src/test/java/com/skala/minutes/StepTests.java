package com.skala.minutes;

import com.skala.minutes.minutes.MeetingReport;
import com.skala.minutes.minutes.MinutesService;
import com.skala.minutes.support.FakeChatModel;
import com.skala.minutes.web.MinutesController;
import com.skala.minutes.web.MinutesRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STEP 을 하나 끝낼 때마다 여기가 하나씩 초록으로 바뀐다.
 *
 * 모델도 키도 네트워크도 필요 없다 — 가짜 모델을 끼우고
 * ChatClient 체인은 진짜 그대로 돌린다.
 *
 *   ./gradlew test        전체 확인
 *   ./gradlew test --tests '*StepTests$STEP1*'      한 단계만 확인
 */
class StepTests {

    private static final String 회의록 = """
            김지훈: 응답이 느리다는 문의가 늘었다. 타임아웃을 10초로 줄이자.
            이도현: 수요일까지 반영하겠다.
            """;

    private static MinutesService service(FakeChatModel fake) {
        MinutesService s = new MinutesService(ChatClient.create(fake), 3);
        // @Value 로 주입되는 프롬프트 파일을 테스트에서 직접 넣어 준다.
        ReflectionTestUtils.setField(s, "summaryPrompt",
                new org.springframework.core.io.ClassPathResource("prompts/summary.st"));
        ReflectionTestUtils.setField(s, "reportPrompt",
                new org.springframework.core.io.ClassPathResource("prompts/report.st"));
        return s;
    }

    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("STEP 1 · 요약을 만든다")
    class STEP1 {

        @Test
        void 모델_응답을_그대로_돌려준다() {
            var fake = new FakeChatModel("타임아웃을 10초로 줄이기로 했다.");
            assertThat(service(fake).summarize(회의록))
                    .isEqualTo("타임아웃을 10초로 줄이기로 했다.");
        }

        @Test
        void 프롬프트_파일을_system_으로_넘긴다() {
            var fake = new FakeChatModel("요약");
            service(fake).summarize(회의록);
            assertThat(fake.systemText()).contains("회의록을 정리하는 담당자");
        }

        @Test
        void maxSentences_자리를_설정값으로_채운다() {
            var fake = new FakeChatModel("요약");
            service(fake).summarize(회의록);
            assertThat(fake.systemText()).contains("3 문장");
            assertThat(fake.systemText()).doesNotContain("{maxSentences}");
        }

        @Test
        void 회의록_본문을_user_로_넘긴다() {
            var fake = new FakeChatModel("요약");
            service(fake).summarize(회의록);
            assertThat(fake.userText()).contains("타임아웃을 10초로 줄이자");
        }
    }

    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("STEP 2 · 구조화 출력으로 받는다")
    class STEP2 {

        private static final String 모델_응답 = """
                {
                  "title": "주간 개발 회의",
                  "summary": "타임아웃을 10초로 줄이기로 했다.",
                  "decisions": ["타임아웃 10초로 단축"],
                  "actionItems": [
                    {"owner": "이도현", "task": "타임아웃 설정 반영", "dueDate": "수요일"}
                  ]
                }
                """;

        @Test
        void JSON_을_객체로_받는다() {
            MeetingReport r = service(new FakeChatModel(모델_응답)).report(회의록);
            assertThat(r.title()).isEqualTo("주간 개발 회의");
            assertThat(r.decisions()).containsExactly("타임아웃 10초로 단축");
        }

        @Test
        void 담당자별_할_일이_채워진다() {
            MeetingReport r = service(new FakeChatModel(모델_응답)).report(회의록);
            assertThat(r.actionItems()).hasSize(1);
            assertThat(r.actionItems().get(0).owner()).isEqualTo("이도현");
            assertThat(r.actionItems().get(0).dueDate()).isEqualTo("수요일");
        }

        @Test
        void 정리용_프롬프트를_쓴다() {
            var fake = new FakeChatModel(모델_응답);
            service(fake).report(회의록);
            assertThat(fake.systemText()).contains("결정 사항");
        }
    }

    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("STEP 3 · 스트리밍으로 내보낸다")
    class STEP3 {

        @Test
        void 조각으로_나뉘어_도착한다() {
            var fake = new FakeChatModel("타임아웃을 10초로 줄이기로 했다");
            List<String> 조각 = service(fake).streamSummary(회의록).collectList().block();
            assertThat(조각).hasSizeGreaterThan(1);
            assertThat(String.join("", 조각)).isEqualTo("타임아웃을 10초로 줄이기로 했다");
        }

        @Test
        void 스트리밍도_같은_프롬프트를_쓴다() {
            var fake = new FakeChatModel("요약 결과");
            service(fake).streamSummary(회의록).blockLast();
            assertThat(fake.systemText()).contains("3 문장");
        }
    }

    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("STEP 4 · 완료와 오류를 이벤트로 구분한다")
    class STEP4 {

        private static List<String> 이벤트이름(Flux<ServerSentEvent<String>> flux) {
            return flux.map(ServerSentEvent::event).collectList().block();
        }

        @Test
        void 마지막에_done_이벤트가_한_번_나간다() {
            var controller = new MinutesController(new MinutesService(
                    ChatClient.create(new FakeChatModel("무시")), 3) {
                @Override
                public Flux<String> streamSummary(String minutes) {
                    return Flux.just("가", "나");
                }
            });

            assertThat(이벤트이름(controller.stream(new MinutesRequest(회의록))))
                    .containsExactly("token", "token", "done");
        }

        @Test
        void 도중에_끊기면_error_이벤트로_알린다() {
            var controller = new MinutesController(new MinutesService(
                    ChatClient.create(new FakeChatModel("무시")), 3) {
                @Override
                public Flux<String> streamSummary(String minutes) {
                    return Flux.just("가").concatWith(Flux.error(new IllegalStateException("끊김")));
                }
            });

            List<ServerSentEvent<String>> events =
                    controller.stream(new MinutesRequest(회의록)).collectList().block();

            assertThat(events).extracting(ServerSentEvent::event)
                    .containsExactly("token", "error");
            assertThat(events.get(1).data()).contains("끊김");
        }

        @Test
        void 조각_내용은_그대로_전달되고_done_으로_끝난다() {
            var controller = new MinutesController(new MinutesService(
                    ChatClient.create(new FakeChatModel("무시")), 3) {
                @Override
                public Flux<String> streamSummary(String minutes) {
                    return Flux.just("타임아웃을 ", "10초로 ", "줄인다");
                }
            });

            List<ServerSentEvent<String>> events =
                    controller.stream(new MinutesRequest(회의록)).collectList().block();

            String 본문 = events.stream()
                    .filter(e -> "token".equals(e.event()))
                    .map(ServerSentEvent::data)
                    .reduce("", String::concat);

            assertThat(본문).isEqualTo("타임아웃을 10초로 줄인다");
            assertThat(events.get(events.size() - 1).event()).isEqualTo("done");
        }
    }
}
