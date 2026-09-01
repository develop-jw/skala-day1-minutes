# skala-day1-minutes

회의록을 넣으면 **요약**과 **할 일**을 뽑아 주는 REST API.

SKALA 8주차 Spring AI 과정 **Day1 미니실습(회의록 요약봇)** 을 직접 구현한 것이다.
배포된 `starter/` 를 그대로 쓰지 않고, 빈 프로젝트에서 시작해 한 줄씩 옮겨 적으며 만들었다.

- 실습 원본: `~/skala/8주차/Day1_미니실습_회의록요약봇/`
- 강의 실습: [springai-practice](https://github.com/develop-jw/springai-practice) (PART 1~2, ChatMemory·RAG·Tool 포함)

RAG와 Tool은 Day1 범위 밖이라 여기엔 없다.

---

## 무엇을 하는가

`src/main/resources/samples/minutes-sample.txt` 같은 날것의 회의록이 입력이다.
인사말·잡담·결론 안 난 논의가 섞여 있다.

```
김지훈: 다들 모였나. 지난주 배포부터 정리하자.
박서연: 금요일 배포는 나갔고, 결제 취소 API 응답 지연이 두 건 보고됐다.
이도현: 원인은 외부 PG 타임아웃이다. 우리 쪽 타임아웃이 30초로 잡혀 있어서 스레드가 물린다.
김지훈: 그러면 타임아웃을 10초로 줄이고 재시도 두 번으로 가자. 이건 그대로 간다.
...
```

### 엔드포인트

| 메서드 | 경로 | 하는 일 | 반환 |
| --- | --- | --- | --- |
| GET | `/api/minutes/ping` | 기동 확인 (모델 호출 안 함) | `String` |
| POST | `/api/minutes/summary` | 3문장 이내 한 문단 요약 | `String` |
| POST | `/api/minutes/report` | 제목·요약·결정사항·할일을 JSON으로 | `MeetingReport` |
| POST | `/api/minutes/stream` | 요약을 SSE로 한 조각씩 | `Flux<ServerSentEvent<String>>` |

`/report` 응답 예시:

```json
{
  "title": "주간 개발 회의",
  "summary": "결제 API 타임아웃을 줄이고 온보딩 화면 개발을 선행하기로 했다.",
  "decisions": [
    "결제 타임아웃 30초 → 10초, 재시도 2회",
    "온보딩 화면은 문구 없이 개발 선행"
  ],
  "actionItems": [
    { "owner": "이도현", "task": "타임아웃 설정 반영",  "dueDate": "수요일" },
    { "owner": "최민수", "task": "온보딩 1차 화면 개발", "dueDate": "8월 29일" }
  ]
}
```

결정된 것만 `decisions` 에 들어간다. "확인이 더 필요하다"로 끝난 로그 비용 얘기는 빠진다.
이 판단 기준은 자바 코드가 아니라 `prompts/report.st` 에 적혀 있다.

브라우저로 `http://localhost:8080` 을 열면 붙여 넣고 눌러 보는 화면이 있다.

---

## 실행

### 1. API 키를 환경변수로 넣는다

키를 파일에 적지 않는다. `application.yml` 은 환경변수만 참조한다.

```yaml
api-key: ${OPENAI_API_KEY:dummy-key-for-local-build}
```

기본값 `dummy-...` 는 **키가 없어도 앱과 테스트가 기동되도록** 하기 위한 것이다.
실제 모델을 부르려면 진짜 키가 필요하다.

**macOS / Linux** — `~/.zshrc` 에 추가 후 `source ~/.zshrc`

```bash
export OPENAI_API_KEY="sk-..."
```

**Windows (PowerShell)** — 설정 후 터미널을 새로 연다

```powershell
[Environment]::SetEnvironmentVariable("OPENAI_API_KEY", "sk-...", "User")
```

> VS Code / IntelliJ 를 **아이콘으로 실행하면 환경변수를 못 읽는다.**
> 터미널에서 `code .` 로 열거나, 실행 구성의 Environment variables 에 직접 넣는다.

### 2. 띄운다

```bash
./gradlew bootRun          # Windows: .\gradlew.bat bootRun
curl localhost:8080/api/minutes/ping
# meeting-minutes 준비됨
```

### 3. 불러 본다

```bash
# 요약
curl -X POST localhost:8080/api/minutes/summary \
     -H 'Content-Type: application/json' \
     -d "{\"text\": \"$(cat src/main/resources/samples/minutes-sample.txt | tr '\n' ' ')\"}"

# 구조화 출력
curl -X POST localhost:8080/api/minutes/report \
     -H 'Content-Type: application/json' \
     -d '{"text":"김지훈: 타임아웃을 10초로 줄인다. 이도현이 수요일까지 반영한다."}'

# 스트리밍 (-N 을 빠뜨리면 다 모아서 한 번에 찍힌다)
curl -N -X POST localhost:8080/api/minutes/stream \
     -H 'Content-Type: application/json' \
     -d '{"text":"김지훈: 타임아웃을 10초로 줄인다."}'
```

---

## 테스트

```bash
./gradlew test
./gradlew test --tests '*StepTests$STEP1*'   # 한 단계만
```

**키도 네트워크도 쓰지 않는다.** `FakeChatModel` 을 끼우고 `ChatClient` 체인은 진짜 그대로
돌리기 때문에, 프롬프트를 제대로 조립했는지까지 확인된다.
예를 들어 `{maxSentences}` 자리가 설정값 `3` 으로 치환됐는지를 본다.

| 파일 | 확인하는 것 |
| --- | --- |
| `StepTests` | STEP 1~4, 12개 |
| `MinutesControllerTest` | `@WebMvcTest` — 웹 계층만, 서비스는 목 |
| `ApplicationContextTest` | 키 없이 기동, `ChatClient` 빈 생성 |
| `support/FakeChatModel` | 정해진 답 반환 + 보낸 프롬프트 캡처 |

현재 **16개 전부 통과**.

---

## 구조

```
com.skala.minutes
├── MinutesApplication
├── config/
│   └── AiConfig              app.provider·app.temperature 로 ChatClient 조립
├── minutes/                  회의록이라는 업무 (AI 는 알고 HTTP 는 모름)
│   ├── MinutesService        프롬프트 조립 · 모델 호출 · 토큰 기록
│   └── MeetingReport         구조화 출력 형태 (= 모델에게 주는 스키마)
└── web/                      바깥세상과의 접점 (HTTP 는 알고 AI 는 모름)
    ├── MinutesController
    ├── MinutesRequest        입력 + @NotBlank·@Size 검증
    ├── ErrorResponse
    └── ApiExceptionHandler   예외 → HTTP 상태 번역
```

`resources/prompts/*.st` 가 사실상의 로직이다. 자바 코드에 프롬프트를 문자열로 박지 않는다.

### 알아둘 것 몇 가지

**`MeetingReport` 는 JPA 엔티티가 아니다.** `.entity(MeetingReport.class)` 를 부르면
Spring AI 가 이 record 의 필드로 JSON Schema 를 만들어 프롬프트 뒤에 덧붙이고,
모델이 뱉은 JSON 문자열을 Jackson 으로 파싱해 객체로 돌려준다.
필드 이름이 그대로 모델에게 주는 지시가 된다.

**`dueDate` 가 `LocalDate` 가 아닌 이유.** 모델은 "수요일까지", "다음 주 금요일" 같은 말을
그대로 돌려준다. `LocalDate` 로 두면 변환이 깨져 응답 전체가 실패한다.

**스키마는 모양만 강제한다.** `decisions` 가 문자열 배열이라는 건 보장되지만 무엇을 넣을지는
정하지 못한다. 필드가 비어서 오면 고칠 곳은 자바 코드가 아니라 `prompts/report.st` 다.

**실패를 두 갈래로 나눈다.** `TransientAiException`(Rate Limit·모델 장애) → 503,
`NonTransientAiException`(키 오류·잘못된 요청) → 500. 다시 시도해서 될 실패와
안 될 실패를 구분해야 사용자도 개발자도 다음 행동을 안다.

---

## 기술 스택

| | |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring AI | 1.1.8 |
| Gradle | 8.14.4 (wrapper 포함) |
| 모델 | OpenAI `gpt-4o-mini` |

> Spring Initializr 기본값(최신)을 쓰면 수업 자료와 API 가 어긋난다.
> 같은 주차의 기존 프로젝트 `build.gradle` 에 버전을 맞출 것.

---

## 설정

실습에서 손대는 값은 `application.yml` 의 이 세 줄이 전부다.

```yaml
app:
  provider: openai      # openai | ollama — 공급자를 바꾸는 유일한 자리
  temperature: 0.2      # 요약은 편차가 작아야 하므로 낮게
  max-sentences: 3      # 요약 길이
```

`temperature` 를 `0.2` 와 `1.0` 으로 두고 같은 회의록을 여러 번 돌리면 요약이 얼마나
흔들리는지 볼 수 있다. **자바 코드는 한 줄도 고치지 않는다.**

---

## 남은 것

- [ ] 실제 모델 호출 검증 — 테스트는 전부 `FakeChatModel` 기반이라 진짜 OpenAI 를 아직 안 불렀다
- [ ] STEP 5 (선택) — `@Retryable` + `@Recover` 재시도
- [ ] Ollama 전환 — `build.gradle` 의 스타터가 주석 처리돼 있다.
      Maven Central 이 429(요청 제한)를 걸어 받지 못한 상태로 커밋했다.
      주석을 풀고 `AiConfig` 에 `@Qualifier("ollamaChatModel")` 분기를 되살리면 된다.
      `application.yml` 의 ollama 블록과 `local` 프로파일은 이미 들어 있다.
