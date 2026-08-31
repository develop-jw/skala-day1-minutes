package com.skala.springaidemo.web;

import com.skala.springaidemo.minutes.MeetingReport;
import com.skala.springaidemo.minutes.MinutesService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 엔드포인트는 이미 뚫려 있다. 서비스가 채워지면 그대로 동작한다.
 * 이 파일에서 손댈 곳은 맨 아래 TODO ④ 하나뿐이다.
 */
@RestController
@RequestMapping("/api/minutes")
public class MinutesController {

    private final MinutesService minutesService;

    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    /** 모델을 부르지 않는다. 뼈대가 떴는지만 본다. */
    @GetMapping("/ping")
    public String ping() {
        return "meeting-minutes 준비됨";
    }

    @PostMapping("/summary")
    public String summary(@RequestBody MinutesRequest req) {
        return minutesService.summarize(req.text());
    }

    @PostMapping("/report")
    public MeetingReport report(@RequestBody MinutesRequest req) {
        return minutesService.report(req.text());
    }

    /**
     * TODO ④ 완료와 오류를 각각 이벤트로 내보낸다.
     *
     * 지금은 글자 조각만 event("token") 으로 나간다. 두 가지를 덧붙인다.
     *   - 다 끝나면 event("done") 을 한 번 더 보낸다.        → concatWith(Mono.just(...))
     *   - 도중에 실패하면 event("error") 로 메시지를 보낸다.   → onErrorResume(e -> Mono.just(...))
     *
     * 데이터 없는 이벤트를 만들 때는 타입을 알려 줘야 한다.
     *   ServerSentEvent.<String>builder().event("done").data("").build()
     *
     * 이걸 안 하면 화면에서 "끝난 것"과 "끊긴 것"을 구분할 수 없다.
     * 사용자는 답이 끝난 건지 서버가 죽은 건지 모른 채 기다리게 된다.
     *
     * 통과 기준 : STEP 4 테스트 3개가 초록이 된다.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody MinutesRequest req) {
        return minutesService.streamSummary(req.text())
                .map(chunk -> ServerSentEvent.builder(chunk).event("token").build());
        // TODO ④ 여기에 done 이벤트와 error 이벤트를 붙인다
    }
}
