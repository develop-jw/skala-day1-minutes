# skala-day1-minutes

회의록을 넣으면 **요약**과 **할 일**을 뽑아 주는 REST API.
SKALA 8주차 Spring AI 과정 **Day1 미니실습(회의록 요약봇)** 을 직접 구현한 것이다.

`Java 21` · `Spring Boot 3.4.5` · `Spring AI 1.1.8` · `OpenAI gpt-4o-mini` · 테스트 16/16 ✅

> **`minutes` 는 "분"이 아니라 "회의록"이다.**
> 영어에서 `meeting minutes` = 회의록. 라틴어 *minuta scriptura*(작게 쓴 글)에서 왔다.
> 그래서 `summarize(String minutes)` 의 인자는 시간이 아니라 **회의록 본문**이다.

---

## 무엇을 하는가

입력은 이런 날것의 회의록이다. 인사말·잡담·결론 안 난 논의가 섞여 있다.

```
김지훈: 다들 모였나. 지난주 배포부터 정리하자.
박서연: 금요일 배포는 나갔고, 결제 취소 API 응답 지연이 두 건 보고됐다.
이도현: 원인은 외부 PG 타임아웃이다. 우리 쪽 타임아웃이 30초로 잡혀 있어서 스레드가 물린다.
김지훈: 그러면 타임아웃을 10초로 줄이고 재시도 두 번으로 가자. 이건 그대로 간다.
이도현: 알겠다. 이번 주 수요일까지 반영하겠다.
박서연: 로그 저장 비용이 지난달 대비 40퍼센트 늘었다.
김지훈: 그건 확인이 더 필요하다. 다음 회의에서 다시 이야기하자.
```

이걸 넣으면 이렇게 나온다.

```json
{
  "title": "주간 개발 회의",
  "summary": "결제 API 타임아웃을 줄이고 온보딩 화면 개발을 선행하기로 했다.",
  "decisions": ["결제 타임아웃 30초 → 10초, 재시도 2회"],
  "actionItems": [
    { "owner": "이도현", "task": "타임아웃 설정 반영", "dueDate": "수요일" }
  ]
}
```

**결정된 것만** `decisions` 에 들어간다. "확인이 더 필요하다"로 끝난 로그 비용 얘기는 빠진다.
이 판단 기준은 자바 코드가 아니라 `prompts/report.st` 에 적혀 있다.

### 엔드포인트

| 메서드 | 경로 | 하는 일 | 반환 |
| --- | --- | --- | --- |
| GET | `/api/minutes/ping` | 기동 확인 (모델 호출 안 함) | `String` |
| POST | `/api/minutes/summary` | 3문장 이내 한 문단 요약 | `String` |
| POST | `/api/minutes/report` | 제목·요약·결정사항·할일을 JSON으로 | `MeetingReport` |
| POST | `/api/minutes/stream` | 요약을 SSE로 한 조각씩 | `Flux<ServerSentEvent<String>>` |

브라우저로 `http://localhost:8080` 을 열면 붙여 넣고 눌러 보는 화면이 있다.

---

## 빠른 시작

```bash
export OPENAI_API_KEY="sk-..."     # 키는 환경변수로만
./gradlew bootRun
curl localhost:8080/api/minutes/ping        # → meeting-minutes 준비됨
```

<details>
<summary><b>환경변수 설정 자세히 (윈도우 포함) · IDE 에서 안 읽힐 때</b></summary>

<br>

키를 파일에 적지 않는다. `application.yml` 은 환경변수만 참조한다.

```yaml
api-key: ${OPENAI_API_KEY:dummy-key-for-local-build}
```

기본값 `dummy-...` 는 **키가 없어도 앱과 테스트가 기동되도록** 하기 위한 것이다.
실제 모델을 부르려면 진짜 키가 필요하다.

**macOS / Linux** — `~/.zshrc` 에 추가한 뒤 `source ~/.zshrc`

```bash
export OPENAI_API_KEY="sk-..."
```

**Windows (PowerShell)** — 설정한 뒤 터미널을 새로 연다

```powershell
[Environment]::SetEnvironmentVariable("OPENAI_API_KEY", "sk-...", "User")
```

**⚠️ IDE 를 아이콘으로 실행하면 환경변수를 못 읽는다.**
GUI 앱은 로그인 셸을 거치지 않아 `~/.zshrc` 를 읽지 않는다. 둘 중 하나로 해결한다.

- 터미널에서 `code .` / `idea .` 로 연다 (셸 환경을 물려받음)
- 실행 구성의 `Environment variables` 칸에 직접 넣는다

</details>

<details>
<summary><b>curl 로 세 기능 불러 보기</b></summary>

<br>

```bash
# 요약
curl -X POST localhost:8080/api/minutes/summary \
     -H 'Content-Type: application/json' \
     -d "{\"text\": \"$(cat src/main/resources/samples/minutes-sample.txt | tr '\n' ' ')\"}"

# 구조화 출력 — 필드가 비어 오면 prompts/report.st 를 고친다
curl -X POST localhost:8080/api/minutes/report \
     -H 'Content-Type: application/json' \
     -d '{"text":"김지훈: 타임아웃을 10초로 줄인다. 이도현이 수요일까지 반영한다."}'

# 스트리밍 — -N 을 빠뜨리면 다 모아서 한 번에 찍힌다
curl -N -X POST localhost:8080/api/minutes/stream \
     -H 'Content-Type: application/json' \
     -d '{"text":"김지훈: 타임아웃을 10초로 줄인다."}'
```

</details>

---

## 구조

```
com.skala.minutes
├── MinutesApplication
├── config/
│   └── AiConfig              ChatClient 를 조립해 컨테이너에 등록
├── minutes/                  회의록이라는 업무  (AI 는 알고 HTTP 는 모름)
│   ├── MinutesService        프롬프트 조립 · 모델 호출 · 토큰 기록
│   └── MeetingReport         구조화 출력 형태 (= 모델에게 주는 스키마)
└── web/                      바깥세상과의 접점  (HTTP 는 알고 AI 는 모름)
    ├── MinutesController
    ├── MinutesRequest        입력 + @NotBlank·@Size 검증
    ├── ErrorResponse
    └── ApiExceptionHandler   예외 → HTTP 상태 번역

resources/
├── prompts/summary.st        ← 사실상의 로직
├── prompts/report.st         ← 사실상의 로직
├── samples/minutes-sample.txt
└── static/index.html
```

프롬프트를 자바 코드에 문자열로 박지 않는다. `.st` 파일이 로직을 들고 있다.

---

## 개념 정리

> 처음 볼 때 걸렸던 것들. 나중에 다시 봐도 헷갈릴 만한 순서로 놓았다.

<details>
<summary><b>1. 예전 CRUD 프로젝트와 무엇이 다른가</b></summary>

<br>

계층 구조는 그대로다. **`Repository` 자리에 `ChatClient` 가 들어왔을 뿐이다.**

| 역할 | 예전 | 이번 |
| --- | --- | --- |
| 데이터 출처 | `Repository` → DB | **`ChatClient` → LLM** |
| 로직 | Service | Service + **프롬프트 파일** |
| 입력 DTO | `XxxRequest` | `MinutesRequest` |
| 출력 DTO | `XxxResponse` | `MeetingReport` |
| 응답 | Controller → JSON | 동일 |

`AiConfig` 는 예전에 `DataSource` 를 설정하던 자리와 비슷한 위치다.

**결정적으로 다른 점:**

```
DB  : 같은 쿼리 → 항상 같은 결과
LLM : 같은 입력 → 매번 다른 결과
```

그래서 예전엔 없던 것들이 생겼다.

- `temperature: 0.2` — 편차를 줄이는 손잡이 (요약은 흔들리면 안 되니까)
- `prompts/*.st` — 로직이 자바 코드가 아니라 텍스트 파일에 있음
- `TransientAiException` / `NonTransientAiException` — 재시도로 될 실패와 안 될 실패의 구분

특히 두 번째가 크다. `report()` 가 `decisions` 를 비워서 돌려주면, 예전 같으면 쿼리를
고쳤을 자리에서 **프롬프트를 고친다.**

</details>

<details>
<summary><b>2. AiConfig → Service → Controller 주입 흐름</b></summary>

<br>

`AiConfig` 는 남에게 주입하지 않는다. **부품을 만들어 내놓으면 스프링이 필요한 곳에 꽂아 준다.**

```
[앱 기동 시]

1. Spring AI 자동설정
   └▶ OpenAiChatModel 빈 생성          (yml 의 api-key, model 을 읽어서)
              │
              ▼  @Qualifier("openAiChatModel")
2. AiConfig.chatClient()
   └▶ ChatClient 빈 생성                (+ temperature 0.2 를 기본 옵션으로)
              │
              ▼  생성자 주입
3. MinutesService(ChatClient chat, @Value("${app.max-sentences}") int maxSentences)
              │
              ▼  생성자 주입
4. MinutesController(MinutesService minutesService)
```

`MinutesService` 는 `AiConfig` 의 존재조차 모른다. 그냥 "생성자로 `ChatClient` 하나 주세요"
라고 선언할 뿐이다.

**`defaultOptions` 가 여기서 걸린다:**

```java
ChatClient.builder(openaiModel)
        .defaultOptions(ChatOptions.builder().temperature(temperature).build())
        .build();
```

이 `ChatClient` 로 나가는 **모든** 호출에 적용되므로, `summarize()` · `report()` ·
`streamSummary()` 어디서도 temperature 를 신경 쓸 필요가 없다.
yml 한 줄만 바꾸면 전부 반영되는 이유다.

</details>

<details>
<summary><b>3. <code>.entity()</code> 는 JPA 엔티티가 아니다 ⭐</b></summary>

<br>

가장 헷갈리는 지점. **`MeetingReport` 는 DB 와 아무 관계 없는 그냥 `record`(DTO)다.**

`entity` 는 Spring AI 의 메서드 이름일 뿐이고, 뜻은 **"응답을 이 타입으로 만들어 줘"** 다.

#### `content()` 와 비교하면 명확하다

```java
.call().content()                    → String
.call().entity(MeetingReport.class)  → MeetingReport
```

`content()` 를 쓰면 이런 **문자열**이 그대로 온다.

```
{"title":"주간 개발 회의","decisions":["타임아웃 10초"], ...}
```

쓸모 있는 데이터인데 문자열이라 `report.title()` 을 못 한다.
`entity()` 는 이걸 파싱해서 객체로 만들어 준다.

#### 그런데 모델은 왜 JSON 으로 답했을까

우리가 쓴 `report.st` 에는 "JSON 으로 답해라"는 말이 한 줄도 없다.
**`.entity()` 가 프롬프트 뒤에 몰래 덧붙인다.**

```
Your response should be in JSON format.
Do not include any explanations, only provide a RFC8259 compliant JSON response
following this format without deviation.
Here is the JSON Schema instance your output must adhere to:
{ "title": {"type":"string"}, "decisions": {"type":"array"}, ... }
```

이 스키마는 `MeetingReport` record 의 필드를 읽어 **자동 생성**한 것이다.
(`BeanOutputConverter` 가 `victools` 라이브러리로 만든다)

#### `entity()` 만의 일 vs Jackson 이 하는 일

파싱 자체는 Jackson 이 한다 — `BeanOutputConverter` 생성자가 `ObjectMapper` 를 받는다.
**`entity()` 의 고유한 역할은 프롬프트를 건드리는 쪽이다.**

| | 하는 일 |
| --- | --- |
| `entity()` | ① record → JSON Schema 생성 ② 프롬프트에 지시 덧붙임 ③ Jackson 호출 |
| Jackson | 문자열 ↔ 객체 변환만 |

Jackson 은 **이미 JSON 인 문자열**만 다룰 수 있다. 모델이 "회의 내용을 정리하면
다음과 같습니다…" 라고 답하면 손도 못 댄다. 그래서 **모델이 JSON 을 뱉게 만드는 것**이
먼저이고, 그게 `entity()` 의 진짜 역할이다.

</details>

<details>
<summary><b>4. JSON 변환이 두 번 일어난다</b></summary>

<br>

`/report` 응답이 JSON 인 이유는 **서로 다른 두 단계**가 겹쳐 있기 때문이다.

```
"{\"title\":\"주간 개발 회의\", ...}"     모델이 뱉은 JSON 문자열
              │
              ▼  ① Spring AI — BeanOutputConverter (Jackson 역직렬화)
      MeetingReport 객체
              │
              ▼  Service → Controller
              ▼  ② Spring MVC — Jackson 직렬화
{"title":"주간 개발 회의", ...}            HTTP 응답
```

- **①** 은 `.entity()` 가 하는 일. AI 때문에 생긴 단계다.
- **②** 는 `@RestController` 의 기본 동작. **예전에 DTO 반환하던 것과 똑같고 AI 와 무관하다.**

같은 모양으로 돌아오는데 왜 굳이 객체를 거치느냐 — **가운데에서 뭔가 할 수 있기 때문**이다.
검증하거나, 필드를 고치거나, DB 에 저장하거나, Slack 으로 보내거나.
문자열 그대로 흘려보내면 그게 안 된다.

</details>

<details>
<summary><b>5. <code>MeetingReport</code> 가 두 번 일하는 이유</b></summary>

<br>

```java
public record MeetingReport(
        String title, String summary,
        List<String> decisions, List<ActionItem> actionItems) {
    public record ActionItem(String owner, String task, String dueDate) {}
}
```

| 역할 | 설명 |
| --- | --- |
| **모델에게 주는 지시** | 이 구조가 JSON Schema 가 되어 프롬프트에 붙는다 |
| **응답 DTO** | Jackson 이 직렬화해 클라이언트에게 보낸다 |

그래서 이 record 를 고치면 **프롬프트도 같이 바뀐다.** 필드를 추가하면 모델이 그것도
채워서 답하게 된다.

#### 실용적인 결론 두 가지

**필드 이름을 잘 지어야 한다.** 이름 자체가 모델에게 주는 지시다.
`decisions`, `actionItems`, `owner`, `dueDate` — 모델이 이 단어를 보고 무엇을 채울지
판단한다. `list1`, `data2` 였다면 품질이 떨어진다.

**스키마는 "모양"만 강제한다.** `decisions` 가 문자열 배열이라는 건 보장되지만,
거기에 **무엇을 넣을지**는 정하지 못한다.

```
- 결정된 것만 decisions 에 넣는다. 논의만 하고 끝난 것은 넣지 않는다.
- 담당자를 못 찾으면 owner 를 "미정" 으로 둔다.
```

이런 판단 기준은 `report.st` 에만 있다. **필드가 비어 오면 record 가 아니라 프롬프트를
고친다.** Day1 이 "제일 중요한 경험"이라고 한 지점이다.

#### `dueDate` 가 `LocalDate` 가 아닌 이유

모델은 "수요일까지", "다음 주 금요일" 같은 말을 그대로 돌려준다.
`LocalDate` 로 두면 변환이 깨져 **응답 전체가 실패한다.**
날짜로 바꾸는 일은 받아 온 뒤에 우리 코드가 한다.

</details>

<details>
<summary><b>6. 왜 <code>minutes/</code> 와 <code>web/</code> 으로 나눴나</b></summary>

<br>

패키지를 나누는 기준이 두 가지 있다.

| | 묶는 기준 | 예 |
| --- | --- | --- |
| **계층 기준** | 역할 단위 | `controller/` `service/` `dto/` |
| **도메인 기준** | 업무 단위 | `minutes/` `order/` `payment/` |

이 프로젝트는 도메인 기준이다. 그래서 `MeetingReport`(데이터 모델)가 `dto/` 가 아니라
`MinutesService` 와 **같은 폴더**에 있다.

```
web/      "HTTP 는 알지만 AI 는 모른다"
minutes/  "AI 는 알지만 HTTP 는 모른다"
```

**서로를 모르게 만드는 게 목적이다.**

- `MinutesController` 에는 `ChatClient`, `Prompt` 가 한 번도 안 나온다
- `MinutesService` 에는 `@RestController`, `HttpStatus` 가 없다

덕분에 `MinutesControllerTest` 가 `@MockitoBean MinutesService` 로 서비스를 통째로
가짜로 바꿔 끼울 수 있다. 나중에 웹이 아닌 배치에서 요약이 필요해도 서비스를 그대로 쓴다.

</details>

<details>
<summary><b>7. <code>ApiExceptionHandler</code> = GlobalExceptionHandler</b></summary>

<br>

이름만 다를 뿐 흔히 쓰는 전역 예외 처리기와 **같은 것**이다.
동작을 만드는 건 클래스 이름이 아니라 `@RestControllerAdvice` 애노테이션이다.

**방향에 주의:** 이 클래스로 예외를 "던지는" 게 아니다.

```
Service 에서 예외 발생
   ↓ (아무도 안 잡음)
Controller 밖으로 전파
   ↓
스프링이 가로챔 → 타입이 맞는 @ExceptionHandler 를 찾아 호출
```

코드 어디에도 `ApiExceptionHandler` 를 부르는 곳이 없다. 서비스는 그냥 예외를 터뜨리고
놔두면 된다. 예외 타입이 여러 핸들러에 걸리면 **가장 구체적인 것**이 이긴다.

| 예외 | 상태 | 뜻 |
| --- | --- | --- |
| `MethodArgumentNotValidException` | **400** | 요청이 잘못됨 (`@NotBlank` 등) |
| `TransientAiException` | **503** | 일시적 — Rate Limit·모델 장애, **다시 하면 될 수도** |
| `NonTransientAiException` | **500** | 영구적 — 키 오류·잘못된 요청, **다시 해도 안 됨** |

503 과 500 의 구분이 핵심이다. 전부 500 으로 나가면 사용자도 개발자도 "재시도해야 하나
설정을 고쳐야 하나"를 모른다. DB 에는 없던 개념인데, LLM 은 외부 API 라 이런 실패가 흔하다.

> `@ControllerAdvice` 는 반환값을 뷰 이름으로 해석하고,
> `@RestControllerAdvice` 는 `@ResponseBody` 를 포함해 JSON 으로 직렬화한다. REST API 라 후자.

</details>

<details>
<summary><b>8. <code>ChatMemory</code> 가 없는 이유</b></summary>

<br>

이 프로젝트에는 `ChatMemory`, `Advisor`, `conversationId` 가 한 번도 등장하지 않는다.

**회의록 요약봇은 상태가 없는(stateless) API 이기 때문이다.**

```
요청 1: 회의록 A → 요약 A.  끝.
요청 2: 회의록 B → 요약 B.  끝.
```

요청끼리 아무 관계가 없다. `ChatMemory` 는 멀티턴 대화에서 필요한 물건이다.

```
사용자: 파이썬 알려줘
  AI: (설명)
사용자: 그거 예제 보여줘     ← "그거"가 뭔지 알려면 앞 대화를 기억해야 함
```

회의록 요약은 매 요청이 완결이라, 기억이 오히려 방해가 된다.
앞 회의록 내용이 다음 요약에 섞이면 안 되니까.

RAG(`QuestionAnswerAdvisor`)와 Tool(`@Tool`)도 Day1 범위 밖이다.
그쪽은 [springai-practice](https://github.com/develop-jw/springai-practice) 에 있다.

</details>

<details>
<summary><b>9. 교재와 클래스 이름이 다를 때</b></summary>

<br>

Spring AI 는 1.0 GA 전후로 API 가 꽤 바뀌었다. 교재 코드가 안 붙으면 대체로 이 패턴이다.

| 교재 (구버전) | 1.1.x |
| --- | --- |
| `InMemoryChatMemory` | `MessageWindowChatMemory` |
| `ChatMemory` 에 저장 로직 포함 | `ChatMemoryRepository` 로 분리 |

`InMemoryChatMemory` 하나가 하던 일이 **두 개념으로 쪼개졌다.**

- `ChatMemory` — 대화를 *어떻게 관리할지* (몇 개까지 유지할지)
- `ChatMemoryRepository` — 대화를 *어디에 저장할지* (메모리 / JDBC / Cassandra …)

저장소를 갈아끼울 수 있게 하려는 분리다. `MessageWindowChatMemory.builder()` 는
repository 를 생략하면 `InMemoryChatMemoryRepository` 를 기본으로 넣는다.

**확인하는 법:** VS Code 에서 `org.springframework.ai.chat.memory.` 까지 치고
`Ctrl+Space` 를 누르면 그 버전에 **실제로 있는** 클래스 목록이 뜬다. 교재와 다르면
이걸 기준으로 삼는다.

</details>

---

## 테스트

```bash
./gradlew test
./gradlew test --tests '*StepTests$STEP1*'   # 한 단계만
```

**키도 네트워크도 쓰지 않는다.** `FakeChatModel` 을 끼우고 `ChatClient` 체인은 진짜 그대로
돌리기 때문에, 프롬프트를 제대로 조립했는지까지 확인된다.

<details>
<summary><b>테스트 파일별 역할과 FakeChatModel 원리</b></summary>

<br>

| 파일 | 확인하는 것 |
| --- | --- |
| `StepTests` | STEP 1~4, 12개. 스프링 컨텍스트 없이 `new` 로 직접 생성해 빠르다 |
| `MinutesControllerTest` | `@WebMvcTest` — 웹 계층만, 서비스는 `@MockitoBean` |
| `ApplicationContextTest` | 키 없이 기동되는지, `ChatClient` 빈이 만들어지는지 |
| `support/FakeChatModel` | 정해진 답 반환 + **보낸 프롬프트 캡처** |

#### FakeChatModel 이 이 실습의 심장

`ChatModel` 인터페이스를 직접 구현해 두 가지 일을 한다.

| 역할 | 효과 |
| --- | --- |
| 정해진 답을 돌려준다 | 키·네트워크·요금 없이 테스트 가능 |
| 받은 프롬프트를 저장한다 | **프롬프트를 제대로 조립했는지 검사 가능** |

두 번째가 핵심이다. "요약이 나왔나"가 아니라 **"모델에게 무엇을 보냈나"** 를 검증한다.

```java
assertThat(fake.systemText()).contains("3 문장");
assertThat(fake.systemText()).doesNotContain("{maxSentences}");
```

치환이 **됐는지**와 자리표시자가 **안 남았는지**를 둘 다 본다. `.param()` 을 빠뜨리면 걸린다.

가짜인 건 **모델뿐**이다. `ChatClient` 의 체인(`.system().user().call().entity()`)은
진짜 그대로 돌아간다. 그래서 템플릿 치환, JSON→객체 변환이 실제와 동일하게 검증된다.

`stream()` 도 구현해서 답을 공백 단위로 쪼개 흘려보낸다. STEP 3 에서 "조각이 2개 이상인가"
를 검사할 수 있는 이유다.

</details>

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

> ⚠️ Spring Initializr 기본값(최신)을 쓰면 수업 자료와 API 가 어긋난다.
> 같은 주차의 기존 프로젝트 `build.gradle` 에 버전을 맞출 것.

---

## 남은 것

- [ ] **실제 모델 호출 검증** — 테스트는 전부 `FakeChatModel` 기반이라 진짜 OpenAI 를 아직 안 불렀다.
      `/report` 를 돌려서 `decisions`·`actionItems` 가 채워지는지 보고, 비면 `report.st` 를 고친다
- [ ] **STEP 5 (선택)** — `@Retryable` + `@Recover` 재시도.
      스트리밍에는 걸지 않는다. 이미 흘려보낸 글자는 되돌릴 수 없기 때문
- [ ] **Ollama 전환** — `build.gradle` 의 스타터가 주석 처리돼 있다.
      Maven Central 이 429(요청 제한)를 걸어 받지 못한 상태로 커밋했다.
      주석을 풀고 `AiConfig` 에 `@Qualifier("ollamaChatModel")` 분기를 되살리면 된다.
      `application.yml` 의 ollama 블록과 `local` 프로파일은 이미 들어 있다

---

## 관련 저장소

| | 내용 |
| --- | --- |
| **[springai-practice](https://github.com/develop-jw/springai-practice)** | PART 1~2 강의 실습. ChatMemory · RAG(VectorStore) · Tool 포함. 교수님 배포본 (`com.lecture.springai`) |
| **skala-day1-minutes** (여기) | Day1 미니실습. 회의록 요약봇을 빈 프로젝트에서 직접 구현 (`com.skala.minutes`) |

실습 원본: `~/skala/8주차/Day1_미니실습_회의록요약봇/`
