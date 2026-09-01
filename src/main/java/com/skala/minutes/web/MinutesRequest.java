package com.skala.minutes.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회의록 한 건. 너무 긴 입력은 컨텍스트를 넘기므로 여기서 먼저 막는다.
 */
public record MinutesRequest(
        @NotBlank(message = "회의록 내용이 비어 있다")
        @Size(max = 12000, message = "회의록이 너무 길다. 12000자 이내로 잘라서 보낸다")
        String text) {
}
