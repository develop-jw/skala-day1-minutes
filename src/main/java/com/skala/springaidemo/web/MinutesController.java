package com.skala.springaidemo.web;

import com.skala.springaidemo.minutes.MeetingReport;
import com.skala.springaidemo.minutes.MinutesService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/minutes")
public class MinutesController {

    private final com.skala.springaidemo.minutes.MinutesService minutesService;

    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    @GetMapping("/ping")
    public String ping() {
        return "meeting-minutes 준비됨";
    }

    @PostMapping("/summary")
    public String summary(@Valid @RequestBody MinutesRequest req) {
        return minutesService.summarize(req.text());
    }

    @PostMapping("/report")
    public MeetingReport report(@Valid @RequestBody MinutesRequest req) {
        return minutesService.report(req.text());
    }

    @PostMapping(value="/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody MinutesRequest req) {
        return minutesService.streamSummary(req.text())
                .map(chunk -> ServerSentEvent.builder(chunk).event("token").build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder().event("done").data("").build()))
                .onErrorResume(e -> Mono.just(ServerSentEvent.<String>builder().event("error").data(e.getMessage()).build()));
    }
}
