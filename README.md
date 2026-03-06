# lipam-board

대규모 트래픽을 고려한 **MSA 기반 게시판 백엔드 프로젝트**입니다.  
Spring Boot 3, Kafka, Redis, MySQL을 활용하여 확장 가능한 구조로 설계되었습니다.

---

## 📐 아키텍처 개요

이 프로젝트는 **CQRS (Command Query Responsibility Segregation)** 패턴을 적용한 멀티 모듈 MSA 구조입니다.  
각 서비스는 독립적으로 배포 가능하며, Kafka를 통한 이벤트 기반 통신으로 서비스 간 결합도를 낮췄습니다.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client                                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────────┐
        │ Command Services  │                       │ Query Service
        │                   │                       │
   ┌────▼─────┐  ┌──────────▼──┐  ┌────────┐   ┌───▼──────────┐
   │ article  │  │   comment   │  │  like  │   │ article-read │
   │ :9000    │  │   :9001     │  │ :9002  │   │   :9005      │
   └────┬─────┘  └──────┬──────┘  └───┬────┘   └───────┬──────┘
        │               │             │                │
        └───────────────┴─────────────┘                │
                        │ Kafka (Outbox Pattern)       │
                        ▼                              │
              ┌─────────────────┐             ┌────────▼───────┐
              │   view :9003    │             │  hot-article   │
              │  (조회수 집계)   │             │    :9004       │
              └─────────────────┘             └────────────────┘
```

---

## 🗂️ 프로젝트 구조

```
lipam-board/
├── common/
│   ├── snowflake            # 분산 ID 생성기
│   ├── data-serializer      # 직렬화/역직렬화 공통 모듈
│   ├── event                # 공통 이벤트 정의 (EventType, Payload)
│   └── outbox-message-relay # Transactional Outbox 패턴 구현
└── service/
    ├── article              # 게시글 CRUD (Command)
    ├── comment              # 댓글 CRUD (Command)
    ├── like                 # 좋아요 (Command)
    ├── view                 # 조회수 집계
    ├── hot-article          # 인기글 산정
    └── article-read         # 게시글 통합 조회 (Query)
```

---

## 🛠️ 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.2 |
| Build | Gradle (멀티 모듈) |
| Database | MySQL |
| Cache | Redis |
| Message Broker | Apache Kafka |
| ORM | Spring Data JPA |
| ID 생성 | Snowflake Algorithm |

---

## 📦 서비스별 설계 패턴

### article (`:9000`) — 게시글 서비스

**DB Sharding 설계**

게시글 서비스의 shard key는 `board_id`입니다. 트래픽이 증가하면 `board_id % shard_count`로 해시 기반 샤딩을 적용해 데이터를 여러 DB에 분산할 수 있도록 미리 설계되어 있습니다. Outbox 이벤트 발행 시에도 `boardId`를 shard key로 사용하여 Kafka 파티션을 결정합니다.

**페이지 번호 방식 — 커버링 인덱스 최적화**

단순 `LIMIT … OFFSET …` 쿼리는 offset이 커질수록 Secondary Index → Clustered Index를 offset 수만큼 반복 탐색하여 느려집니다. `(board_id, article_id)` 인덱스를 커버링 인덱스로 활용해 article_id만 먼저 추리고, 그 결과에만 Clustered Index를 조회하는 2단계 서브쿼리 방식으로 개선했습니다.

```sql
SELECT * FROM (
    SELECT article_id FROM article
    WHERE board_id = :boardId
    ORDER BY article_id DESC
    LIMIT :limit OFFSET :offset
) t LEFT JOIN article ON t.article_id = article.article_id
```

**무한 스크롤 방식 — 커서 기반 페이지네이션**

`OFFSET` 방식은 깊은 페이지일수록 인덱스 스캔 비용이 누적됩니다. 마지막으로 받은 `article_id`를 커서로 사용하여 항상 일정한 속도를 유지합니다.

```sql
WHERE board_id = :boardId AND article_id < :lastArticleId
ORDER BY article_id DESC LIMIT :limit
```

**COUNT 쿼리 최적화**

전체 게시글 수를 `COUNT(*)`로 매번 집계하면 대규모 테이블에서 성능 문제가 생깁니다. `board_article_count` 테이블에 게시글 수를 별도로 관리하고, 게시글 생성/삭제 시 `UPDATE … SET article_count = article_count + 1`로 즉시 반영합니다. 카운트 조회 시에도 `LIMIT`으로 최대 스캔 건수를 제한해 불필요한 풀스캔을 방지합니다.

---

### comment (`:9001`) — 댓글 서비스

**계층형 댓글 설계 1 — 최대 2 depth (Adjacency List)**

댓글과 대댓글 2단계만 지원하는 구조입니다. `parent_comment_id`로 부모를 참조하고, 루트 댓글은 자기 자신의 `comment_id`를 `parent_comment_id`로 가집니다. 목록 조회 시 `(parent_comment_id ASC, comment_id ASC)` 정렬로 계층 순서를 유지합니다. 커서 기반 무한 스크롤은 `(parent_comment_id, comment_id)` 복합 커서를 사용합니다.

**계층형 댓글 설계 2 — 무한 depth (Path Enumeration)**

무한 depth 지원을 위해 경로 열거형 구조를 사용합니다. 각 댓글은 루트부터 자신까지의 전체 경로를 `path` 컬럼에 저장합니다. 경로는 62진수(`0-9A-Za-z`) 5자리 청크의 연속으로 구성되며, depth당 최대 62^5(약 9억) 개의 형제 댓글을 수용합니다.

```
댓글1:  00000
댓글2:  0000100000   ← 댓글1의 첫 번째 자식
댓글3:  0000100001   ← 댓글1의 두 번째 자식
댓글4:  000010000100000  ← 댓글2의 첫 번째 자식
```

`ORDER BY path ASC` 한 줄로 전체 계층 순서가 보장됩니다. `path LIKE 'prefix%'` 조건으로 특정 댓글의 모든 하위 댓글을 범위 조회할 수 있고, `path` 컬럼 인덱스를 통해 인덱스 레인지 스캔으로 처리됩니다.

---

### like (`:9002`) — 좋아요 서비스

**좋아요 수 동시성 제어**

게시글에 동시에 좋아요 요청이 몰리면 `article_like_count` 테이블에 경쟁 조건이 발생합니다. 이 서비스는 세 가지 방식을 모두 구현해 비교합니다.

비관적 락 방법 1은 DB UPDATE 쿼리로 직접 증감합니다. DB가 행 수준 잠금을 처리하므로 가장 단순하고 성능도 좋습니다.

```sql
UPDATE article_like_count SET like_count = like_count + 1 WHERE article_id = :articleId
```

비관적 락 방법 2는 `SELECT … FOR UPDATE`로 행을 먼저 잠근 뒤 Java 객체에서 값을 증감하고 저장합니다. 복잡한 계산 로직이 필요한 경우에 적합합니다.

낙관적 락은 `@Version` 컬럼으로 충돌 여부를 감지합니다. 충돌이 적은 환경에서 성능 이점이 있고, 충돌 시 JPA가 `OptimisticLockException`을 던지면 재시도합니다.

---

### view (`:9003`) — 조회수 서비스

**Redis 기반 실시간 카운팅 + MySQL 백업**

조회수는 특성상 단일 카운트 값만 관리하면 되므로 Redis에 실시간 집계합니다. 모든 조회마다 MySQL에 쓰면 DB 부하가 너무 크기 때문에, Redis에서 `INCR`로 카운트를 올리고 100 단위마다 MySQL에 백업합니다. MySQL 백업 쿼리에는 `WHERE view_count < :viewCount` 조건을 달아 이미 더 큰 값이 기록된 경우 덮어쓰지 않도록 보호합니다.

**분산 락으로 중복 조회 방지**

동일 사용자가 게시글을 반복 새로고침할 때마다 조회수가 올라가면 어뷰징이 됩니다. 분산 시스템에서는 서버가 여러 대이므로 로컬 상태로 중복 여부를 판단할 수 없습니다. Redis `SETNX`(SetIfAbsent)로 `article_id + user_id` 조합의 키에 TTL 10분짜리 분산 락을 걸어, 같은 사용자가 같은 게시글을 10분 내에 재조회해도 카운트가 오르지 않도록 합니다.

```
Redis Key: view::article::{articleId}::user::{userId}::lock  TTL: 10분
락 획득 성공 → Redis INCR → 100 단위마다 MySQL 백업
락 획득 실패 → 현재 조회수 그대로 반환
```

---

### hot-article (`:9004`) — 인기글 서비스

**배치 처리 대신 이벤트 기반 실시간 집계**

매일 오전 1시에 하루치 게시글 전체를 순회하며 점수를 계산하는 배치 방식은, 수백만 건 이상의 대규모 데이터를 1시간 내에 처리해야 하는 부담이 있습니다. 오전 0시에 즉시 업데이트해야 하는 요구사항이 생기면 처리 시간이 더욱 촉박해집니다.

대신 게시글/댓글/좋아요/조회수 서비스에서 데이터가 변경될 때마다 Kafka로 이벤트를 발행하고, 인기글 서비스가 이벤트를 받을 때마다 점수를 즉시 계산해 Redis에 반영합니다. 배치 처리 없이도 항상 최신 인기글 순위를 유지할 수 있습니다. 조회수 이벤트는 트래픽이 과도하게 많을 수 있으므로 100 단위 백업 시점에만 발행합니다.

**점수 계산**

```
score = (좋아요 수 × 3) + (댓글 수 × 2) + (조회수 × 1)
```

**Redis Sorted Set으로 실시간 랭킹 관리**

인기글 데이터는 7일치만 유지하는 휘발성 데이터이므로 MySQL이 아닌 Redis에 저장합니다. Redis Sorted Set은 score 기반으로 자동 정렬된 집합을 유지할 수 있어 상위 N건 조회가 O(log N)에 가능합니다.

이벤트가 들어올 때마다 `ZADD`, `ZREMRANGE`, `EXPIRE` 세 명령을 Pipeline으로 묶어 한 번의 네트워크 왕복으로 처리합니다. 날짜가 지나면 키가 자동 만료됩니다.

```
Key:    hot-article::list::{yyyyMMdd}
Member: articleId
Score:  인기 점수
```

인기글 점수 계산에 필요한 댓글 수, 좋아요 수, 조회수, 게시글 생성 시각을 인기글 서비스가 Redis에 자체 캐시로 관리합니다. 이벤트 처리마다 외부 서비스를 호출하지 않아도 되므로 서비스 간 결합도를 낮춥니다.

---

### article-read (`:9005`) — 게시글 조회 서비스

**CQRS — Command와 Query 분리**

게시글 단건 조회는 게시글 정보 외에도 댓글 수, 좋아요 수가 함께 필요합니다. Command Side 서비스들에 각각 API를 호출하면 네트워크 비용이 발생하고, 서비스 간 결합도가 높아집니다.

CQRS(Command Query Responsibility Segregation) 패턴으로 조회 전용 서비스를 분리했습니다. 게시글 조회 서비스는 article/comment/like 서비스에서 Kafka로 발행한 이벤트를 수신해 `article + comment_count + like_count`를 비정규화한 QueryModel을 Redis에 미리 만들어 둡니다. 클라이언트는 조회 서비스 하나에만 요청하면 필요한 데이터를 모두 가져올 수 있습니다.

조회수는 읽기 트래픽 자체가 쓰기를 유발하는 특성이 있어 QueryModel에 포함하지 않고, 조회수 서비스에 직접 요청해서 가져옵니다.

**게시글 목록 — Redis ZSET 캐시**

게시글 목록은 실시간으로 게시글이 추가/삭제되어 캐시 적용이 까다롭습니다. 게시글 이벤트를 받을 때마다 `boardId` 기준의 Redis ZSET에 최신 1,000건을 유지합니다. 클라이언트 요청이 최신 1,000건 범위 안이면 Redis에서 바로 응답하고, 그 이후는 게시글 서비스에서 MySQL로 조회합니다.

ZSET의 score는 `double`이라 Long 타입의 Snowflake ID를 그대로 쓰면 2^53 초과 시 정밀도가 손실됩니다. score를 0으로 고정하고 value를 19자리 zero-padded 문자열로 저장하여 사전순 정렬 = 숫자 정렬이 되도록 처리했습니다.

**Request Collapsing — 캐시 스탬피드 방지**

캐시가 만료되는 순간 대량의 요청이 동시에 몰리면, 캐시가 없음을 확인한 모든 요청이 원본 서버(조회수 서비스 등)에 동시에 쿼리를 날립니다. 이를 Cache Stampede라고 합니다.

Logical TTL과 Physical TTL을 분리하고 분산 락을 조합해 해결합니다. 캐시 데이터에 논리 만료 시각(`expireAt`)을 포함시켜 저장하고, Redis 키 자체의 TTL(Physical)은 논리 만료보다 5초 더 길게 설정합니다. 논리 TTL이 만료되어도 데이터는 Redis에 남아 있는 것입니다.

논리 만료가 감지되면 분산 락 획득을 시도합니다. 락 획득에 성공한 1개의 요청만 원본 서버에 가서 캐시를 갱신하고, 나머지는 대기 없이 아직 살아있는 기존 캐시를 즉시 반환합니다. 원본 서버로 향하는 요청이 1개로 합쳐지기 때문에 Request Collapsing이라고 부릅니다.

---

## 🔑 핵심 설계 패턴

### 1. Transactional Outbox Pattern

DB 트랜잭션과 Kafka 메시지 발행의 원자성을 보장합니다.  
비즈니스 데이터 저장과 Outbox 저장을 **단일 트랜잭션**으로 묶고, `MessageRelay`가 Kafka로 중계합니다.  
`MessageRelayCoordinator`는 Redis ZSET으로 살아있는 인스턴스를 추적하여 샤드를 균등 분배합니다.

**클래스 구조**

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        common:outbox-message-relay                       │
│                                                                          │
│  ┌────────────────────────┐      ┌──────────────────────────────────┐    │
│  │   OutboxEventPublisher │      │           MessageRelay           │    │
│  │ ─────────────────────  │      │ ──────────────────────────────── │    │
│  │ - outboxIdSnowflake    │      │ - outboxRepository               │    │
│  │ - eventIdSnowflake     │      │ - messageRelayCoordinator        │    │
│  │ - appEventPublisher    │      │ - kafkaTemplate                  │    │
│  │ ─────────────────────  │      │ ──────────────────────────────── │    │
│  │ + publish(type,        │─────▶│ @TransactionalEventListener      │    │
│  │     payload, shardKey) │      │   BEFORE_COMMIT                  │    │
│  └────────────────────────┘      │   + createOutbox(OutboxEvent)    │    │
│           │ publish               │                                 │    │
│           ▼                      │ @TransactionalEventListener      │    │
│  ┌────────────────────┐          │   AFTER_COMMIT (Async)           │    │
│  │     OutboxEvent    │          │   + publishEvent(OutboxEvent)    │    │
│  │ ───────────────    │          │                                  │    │
│  │ - outbox: Outbox   │          │ @Scheduled(fixedDelay=10s)       │    │
│  └────────────────────┘          │   + publishPendingEvent()        │    │
│           │ wraps                │     → 미전송 이벤트 재처리 (폴링)  │    │
│           ▼                      └──────────────────────────────────┘    │
│  ┌──────────────────────────┐    ┌──────────────────────────────────┐    │
│  │         Outbox           │    │    MessageRelayCoordinator       │    │
│  │ ────────────────────     │    │ ──────────────────────────────── │    │
│  │ - outboxId: Long         │    │ - redisTemplate                  │    │
│  │ - eventType: EventType   │    │ - APP_ID: UUID (인스턴스 식별자)  │    │
│  │ - payload: String (JSON) │    │ ──────────────────────────────── │    │
│  │ - shardKey: Long         │    │ + assignedShard(): AssignedShard │    │
│  │   (articleId % 32)       │    │ @Scheduled + ping()              │    │
│  │ - createdAt              │    │   → Redis ZSET에 heartbeat 갱신   │    │
│  └──────────────────────────┘    │ @PreDestroy + leave()            │    │
│                                  │   → 종료 시 Redis에서 자신 제거    │    │
│                                  └──────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
```

**처리 흐름**

```
Service.create()
    │
    ├─ [DB 트랜잭션 시작]
    │    ├─ MySQL: 비즈니스 데이터 저장
    │    └─ OutboxEventPublisher.publish()
    │            └─ ApplicationEventPublisher → OutboxEvent 발행
    │
    │    @TransactionalEventListener(BEFORE_COMMIT)
    │    └─ MessageRelay.createOutbox()
    │            └─ MySQL: outbox 테이블에 저장 (같은 트랜잭션)
    │
    ├─ [트랜잭션 커밋]
    │
    │    @TransactionalEventListener(AFTER_COMMIT) + @Async
    │    └─ MessageRelay.publishEvent()
    │            ├─ KafkaTemplate.send() 성공 → outbox 레코드 삭제
    │            └─ 실패 시 outbox 유지 → 스케줄러가 10초 후 재전송
    │
    └─ [완료]
```

---

### 2. CQRS (Command Query Responsibility Segregation)

쓰기(Command)와 읽기(Query) 책임을 서비스 레벨에서 완전히 분리합니다.

**클래스 구조 (article-read 서비스 중심)**

```
┌─────────────────────────────────────────────────────────────────────────┐
│                  service:article-read  (Query Side)                     │
│                                                                         │
│  ┌───────────────────────┐     ┌──────────────────────────────────────┐ │
│  │ ArticleReadController │────▶│         ArticleReadService          │ │
│  └───────────────────────┘     │ ──────────────────────────────────── │ │
│                                │ - articleQueryModelRepository        │ │
│  ┌───────────────────────┐     │ - articleIdListRepository            │ │
│  │ArticleReadEventConsumer│    │ - boardArticleCountRepository        │ │
│  │ ───────────────────── │     │ - eventHandlers: List<EventHandler>  │ │
│  │ @KafkaListener(        │    │ - articleClient / commentClient /    │ │
│  │   article, comment,   │     │   likeClient / viewClient (Fallback) │ │
│  │   like topics)        │────▶│ ──────────────────────────────────── │ │
│  │ + listen(message, ack)│     │ + read(articleId)                    │ │
│  └───────────────────────┘     │ + readAll(boardId, page, pageSize)   │ │
│                                │ + handleEvent(event)                 │ │
│                                └──────────────────┬───────────────────┘ │
│                                                   │ delegates           │
│                                    ┌──────────────▼──────────────────┐  │
│                                    │  EventHandler (interface)       │  │
│                                    │  + supports(event): boolean     │  │
│                                    │  + handle(event)                │  │
│                                    └──────────────┬──────────────────┘  │
│                              ┌───────────┬────────┴──────┬───────────┐  │
│                    ArticleCreated  ArticleUpdated  CommentCreated  ...  │
│                    EventHandler    EventHandler    EventHandler         │
│                                                                         │
│  ┌──────────────────────────────────┐                                   │
│  │     ArticleQueryModelRepository  │  ← Redis Read Model               │
│  │ ──────────────────────────────── │                                   │
│  │  key: "article-read::article::{id}"                                  │
│  │ + create(model, ttl)             │                                   │
│  │ + update(model)  ← setIfPresent  │  (TTL 리셋 방지)                   │
│  │ + delete(articleId)              │                                   │
│  │ + read(articleId): Optional      │                                   │
│  │ + readAll(ids): Map              │                                   │
│  └──────────────────────────────────┘                                   │
└─────────────────────────────────────────────────────────────────────────┘
```

**데이터 흐름**

```
[Command Side]                              [Query Side]
article / comment / like 서비스
        │
        │ Outbox → Kafka 이벤트 발행
        ▼
   Kafka Topics
        │
        ▼
ArticleReadEventConsumer
        │
        ▼
ArticleReadService.handleEvent()
        │
        ├─ ArticleCreatedEventHandler  → ArticleQueryModelRepository.create()
        ├─ ArticleUpdatedEventHandler  → ArticleQueryModelRepository.update()
        ├─ ArticleDeletedEventHandler  → ArticleQueryModelRepository.delete()
        ├─ CommentCreatedEventHandler  → queryModel.commentCount + 1
        ├─ ArticleLikedEventHandler    → queryModel.likeCount + 1
        └─ ...

[조회 요청]
Client → ArticleReadController → ArticleReadService.read(articleId)
                                        │
                                        ├─ Redis에 QueryModel 있음 → 바로 반환
                                        └─ 없으면 fetch() → Command 서버 직접 호출
                                                            → Redis에 캐시 저장 (TTL 1일)
```

---

### 3. Request Collapsing 기법이 적용된 캐시 전략 (`@OptimizedCacheable`)

#### 해결하려는 문제: Cache Stampede

일반적인 캐시는 TTL이 만료되는 순간 키가 Redis에서 사라집니다.  
이때 대량의 요청이 동시에 들어오면, 캐시가 없는 것을 확인한 **모든 요청이 동시에 DB/원본 서버로 쿼리를 날립니다.**  
이것이 **Cache Stampede(캐시 스탬피드)** 이며, 원본 서버에 순간적인 폭발적 부하를 줍니다.

이 프로젝트는 두 가지 기법을 조합하여 이 문제를 해결합니다.

---

#### 핵심 아이디어 1: Logical TTL + Physical TTL 분리

Redis에 캐시를 저장할 때, 단순히 TTL만 설정하는 게 아니라 **만료 시각(`expireAt`)을 데이터 안에 함께 포함**시켜 저장합니다.

```
Redis에 저장되는 값 (OptimizedCache)
┌──────────────────────────────────────────────────┐
│  {                                               │
│    "data": "{ ...게시글 JSON... }",               │
│    "expireAt": "2025-03-06T12:05:00"  ← 논리 만료 시각
│  }                                               │
└──────────────────────────────────────────────────┘
  Redis 키 자체의 TTL (물리 만료): 12:05:05 에 삭제
```

- **logicalTTL** (논리 TTL): 데이터 안의 `expireAt`. 애플리케이션이 "이 캐시를 갱신해야 하는가?"를 판단하는 기준입니다.
- **physicalTTL** (물리 TTL): Redis 키 자체의 실제 만료 시각. `logicalTTL + 5초`로 항상 더 깁니다.

```
ttlSeconds = 300 으로 설정했을 때
─────────────────────────────────────────────────────
  t=0           t=300          t=305
  │             │              │
  ▼             ▼              ▼
  [캐시 저장]   [논리 만료]    [물리 만료 → Redis 키 삭제]
               ↑
               여기서 갱신 요청 발생
               BUT 데이터는 t=305까지 Redis에 남아있음
```

이 구조 덕분에 **논리 만료 시점에도 Redis 키는 살아있고 데이터를 읽을 수 있습니다.**  
갱신이 필요한 상태임에도 기존 데이터를 즉시 반환할 수 있는 여지가 생깁니다.

---

#### 핵심 아이디어 2: Request Collapsing (분산 락으로 갱신 요청을 1개로 합치기)

논리 TTL이 만료된 시점에 100개의 요청이 동시에 들어온다고 가정합니다.

```
기존 방식 (일반 캐시):
  요청 1 → 캐시 없음 → DB 조회
  요청 2 → 캐시 없음 → DB 조회   ← 100개 동시에 DB로 폭발
  ...
  요청 100 → 캐시 없음 → DB 조회

이 프로젝트 방식 (Request Collapsing):
  요청 1  → 논리 만료 감지 → 분산 락 획득 성공 → DB 조회 후 캐시 갱신
  요청 2  → 논리 만료 감지 → 분산 락 획득 실패 → 만료된 캐시 즉시 반환 ✅
  ...
  요청 100 → 논리 만료 감지 → 분산 락 획득 실패 → 만료된 캐시 즉시 반환 ✅
               ↑
               DB로 가는 요청을 1개로 collapse
```

분산 락(Redis SETNX, TTL 3초)으로 **단 1개의 요청만 원본 서버로 향하고**, 나머지 99개는 물리적으로 아직 살아있는 기존 캐시를 그대로 반환합니다. 응답 지연 없이 처리됩니다.

---

#### 클래스 구조

```
┌────────────────────────────────────────────────────────────────────┐
│              service:article-read  /  cache 패키지                  │
│                                                                    │
│  @OptimizedCacheable(type="article", ttlSeconds=300)               │
│  ─────────────────────────────────────────────────                 │
│  메서드에 부착하는 어노테이션. type은 캐시 키 prefix,                 │
│  ttlSeconds는 논리 TTL 기준값.                                      │
│              │                                                     │
│              │ AOP @Around                                         │
│              ▼                                                     │
│  OptimizedCacheAspect                                              │
│  ─────────────────────                                             │
│  @OptimizedCacheable이 붙은 메서드를 가로채서                        │
│  OptimizedCacheManager.process()에 위임.                            │
│  실제 비즈니스 로직(joinPoint.proceed)을 supplier 람다로 전달.        │
│              │                                                     │
│              ▼                                                     │
│  OptimizedCacheManager                                             │
│  ─────────────────────────────────────────────────────────────     │
│  캐시 조회 → 논리 TTL 판단 → 갱신 여부 결정의 핵심 로직.               │
│                                                                     │
│  process() 흐름:                                                    │
│    1. key = "article::1234" 생성 (type + args)                      │
│    2. Redis에서 OptimizedCache 조회                                 │
│    3. 없음 → refresh() (원본 조회 + 캐시 저장)                       │
│    4. 있음 + 논리 TTL 유효 → parseData() 반환                        │
│    5. 있음 + 논리 TTL 만료                                          │
│         └─ 분산 락 시도 (OptimizedCacheLockProvider)                │
│              실패 → 만료 캐시 반환 (Request Collapsing)             │
│              성공 → refresh() + unlock()                           │
│              │                    │                                │
│              ▼                    ▼                                │
│  OptimizedCacheLockProvider   OptimizedCacheTTL                    │
│  ──────────────────────────   ────────────────────────────────     │
│  Redis SETNX 기반 분산 락.    logicalTTL  = ttlSeconds              │
│  key: "optimized-cache-       physicalTTL = ttlSeconds + 5초       │
│        lock::{캐시키}"                                              │
│  LOCK_TTL: 3초                OptimizedCache                       │
│                               ────────────────────────────────     │
│  lock()   → SETNX             data: String (직렬화된 응답값)        │ 
│  unlock() → DELETE            expireAt: LocalDateTime (논리 만료)   │
│                               isExpired() / parseData()            │
└────────────────────────────────────────────────────────────────────┘
```

**캐시 처리 전체 흐름**

```
Client 요청 (동시에 100개 유입, 논리 TTL 막 만료된 상황)
          │
          ▼
@OptimizedCacheable AOP 인터셉트
          │
          ▼
Redis 조회 → OptimizedCache{ data, expireAt } 존재함
          │
          ▼
isExpired() == true  (논리 TTL 만료)
          │
    ┌─────┴──────────────────────────────────────┐
    │ 요청 #1: 분산 락 획득 성공                   │ 요청 #2~100: 락 획득 실패
    │   └─ 원본 서버 호출 (DB / Command 서비스)    │   └─ 만료된 캐시 즉시 반환 ✅
    │   └─ OptimizedCache 재구성                  │      (응답 지연 없음)
    │        data = 새 데이터                     │
    │        expireAt = now + logicalTTL         │
    │   └─ Redis 저장 (physicalTTL로)             │
    │   └─ 락 해제                                │
    │   └─ 새 데이터 반환 ✅                      │
    └─────────────────────────────────────────────┘
```

---

### 4. Snowflake ID
분산 환경에서 전역적으로 유니크한 Long 타입 ID를 생성합니다.  
클라이언트(JavaScript) 전달 시 정밀도 손실 방지를 위해 **String으로 직렬화**하여 응답합니다.

---

### 5. Kafka Event-Driven

| 토픽 | 발행 서비스 | 구독 서비스 |
|---|---|---|
| `lipam-board-article` | article | article-read, hot-article |
| `lipam-board-comment` | comment | article-read, hot-article |
| `lipam-board-like` | like | article-read, hot-article |
| `lipam-board-view` | view | article-read |

---

## ⚙️ 실행 환경

### 사전 요구사항
- Java 21
- MySQL (port: 3307)
- Redis (port: 6379)
- Apache Kafka (port: 9092)

### 데이터베이스 목록
| DB 이름 | 사용 서비스 |
|---|---|
| `article` | article |
| `comment` | comment |
| `article_like` | like |
| `article_view` | view |

### 빌드 및 실행
```bash
# 전체 빌드
./gradlew build

# 개별 서비스 실행 예시
./gradlew :service:article:bootRun
./gradlew :service:comment:bootRun
./gradlew :service:like:bootRun
./gradlew :service:view:bootRun
./gradlew :service:hot-article:bootRun
./gradlew :service:article-read:bootRun
```

---

## 🔬 사용 기술 상세

### DB 인덱스 전략

#### 커버링 인덱스를 활용한 페이징 최적화

대용량 페이징 조회 시 `LIMIT ... OFFSET ...`을 테이블 풀스캔 없이 처리하기 위해 **커버링 인덱스(Covering Index)** 패턴을 적용했습니다.

```sql
-- ❌ 일반적인 방법: 전체 컬럼을 읽으면서 offset까지 스캔
SELECT * FROM article WHERE board_id = 1 ORDER BY article_id DESC LIMIT 10 OFFSET 10000;

-- ✅ 이 프로젝트 방법: 인덱스만으로 article_id를 먼저 추려낸 뒤, 해당 행만 JOIN
SELECT article.*
FROM (
    SELECT article_id FROM article
    WHERE board_id = :boardId
    ORDER BY article_id DESC
    LIMIT :limit OFFSET :offset
) t LEFT JOIN article ON t.article_id = article.article_id
```

내부 서브쿼리는 `(board_id, article_id)` 인덱스만 읽어 필요한 PK를 추려냅니다. 실제 행 데이터는 그 PK들에 대해서만 접근하므로 불필요한 I/O가 크게 줄어듭니다. 같은 패턴이 `comment`, `comment_v2` 조회에도 동일하게 적용되어 있습니다.

#### COUNT 쿼리 상한 제한

전체 게시글 수를 매번 `COUNT(*)`로 집계하면 테이블이 커질수록 비용이 폭증합니다. 이 프로젝트는 두 가지로 대응합니다.

첫째, `board_article_count`, `article_comment_count`, `article_like_count` 같은 **별도 카운트 테이블**을 두고 INSERT/DELETE 시점에 `UPDATE ... SET count = count + 1`로 즉시 갱신합니다. COUNT 풀스캔이 발생하지 않습니다.

둘째, 카운트가 필요한 곳에서는 조회 상한(limit)을 두어 쿼리 비용을 일정 수준으로 제한합니다.

```sql
-- 최대 limit 건까지만 세고 멈춤 → 풀스캔 방지
SELECT count(*) FROM (
    SELECT comment_id FROM comment WHERE article_id = :articleId LIMIT :limit
) t
```

`PageLimitCalculator`는 현재 페이지와 이동 가능 페이지 수를 바탕으로 이 limit 값을 동적으로 계산해 필요 이상의 스캔을 막습니다.

---

### 무한 스크롤 (Cursor-based Pagination)

페이지 번호 방식(OFFSET)은 깊은 페이지일수록 성능이 저하됩니다. 무한 스크롤에는 **커서 기반 페이지네이션**을 사용합니다.

```sql
-- 마지막으로 받은 article_id보다 작은 것만 조회 → OFFSET 없이 항상 빠름
SELECT * FROM article
WHERE board_id = :boardId AND article_id < :lastArticleId
ORDER BY article_id DESC LIMIT :limit
```

댓글은 정렬 기준이 `(parent_comment_id, comment_id)` 복합키이므로 커서도 두 값의 조합으로 처리합니다.

```sql
WHERE article_id = :articleId AND (
    parent_comment_id > :lastParentCommentId OR
    (parent_comment_id = :lastParentCommentId AND comment_id > :lastCommentId)
)
```

`comment_v2`(계층형 댓글)는 `path` 문자열을 커서로 사용합니다. `path`는 62진수 인코딩된 고정폭 문자열이므로 사전순 정렬이 곧 계층 정렬이 됩니다.

---

### Shard Key 설계

DB Sharding을 고려한 설계를 미리 반영했습니다. 각 테이블에는 미래의 샤드 분배 기준이 될 컬럼을 명시적으로 주석 처리해 두었습니다.

| 서비스 | 테이블 | Shard Key |
|---|---|---|
| article | `article` | `board_id` |
| comment | `comment`, `comment_v2` | `article_id` |
| like | `article_like`, `article_like_count` | `article_id` |
| view | `article_view_count` | `article_id` |

Outbox 메시지 릴레이에서도 동일한 shard key를 사용합니다. `shardKey % SHARD_COUNT(4)`로 파티션을 결정하여 **같은 게시글에 대한 이벤트는 항상 같은 Kafka 파티션으로 전송**되고, 순서가 보장됩니다.

```java
// OutboxEventPublisher.java
Outbox.create(outboxId, type, payload, shardKey % MessageRelayConstants.SHARD_COUNT)

// KafkaTemplate.send()에서 shardKey를 파티션 키로 사용
kafkaTemplate.send(topic, String.valueOf(outbox.getShardKey()), payload)
```

---

### 좋아요 동시성 제어 (낙관적 락 / 비관적 락)

좋아요 수(`article_like_count`)는 동시에 여러 요청이 증가/감소시킬 수 있는 경쟁 조건이 발생합니다. 이 프로젝트는 세 가지 방식을 모두 구현해 비교합니다.

**비관적 락 방법 1 — UPDATE 직접 실행**

```sql
UPDATE article_like_count SET like_count = like_count + 1 WHERE article_id = :articleId
```

DB가 행 수준 잠금을 처리하므로 애플리케이션 레벨에서 별도 락 처리가 필요 없습니다. 가장 단순하고 성능도 좋습니다.

**비관적 락 방법 2 — SELECT FOR UPDATE**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<ArticleLikeCount> findLockedByArticleId(Long articleId);
```

`SELECT ... FOR UPDATE`로 행을 먼저 잠근 뒤 Java 객체에서 값을 올리고 저장합니다. 복잡한 계산이 필요한 경우에 적합합니다.

**낙관적 락 — @Version**

```java
@Version
private Long version; // ArticleLikeCount 엔티티
```

충돌이 적은 환경에서 성능이 좋습니다. 버전이 맞지 않으면 JPA가 `OptimisticLockException`을 던지고 재시도합니다.

---

### 조회수 중복 방지 (Redis 분산 락)

같은 사용자가 같은 게시글을 새로고침할 때마다 조회수가 올라가면 안 됩니다. Redis `SETNX`(SetIfAbsent)를 분산 락으로 사용해 **동일 사용자 + 게시글 조합의 TTL(10분) 내 중복 증가를 차단**합니다.

```
Redis Key: view::article::{articleId}::user::{userId}::lock
TTL: 10분

lock 획득 성공(최초 요청)  → Redis 카운터 increment → 100 단위마다 MySQL 백업
lock 획득 실패(재방문)     → 현재 조회수 그대로 반환 (증가 없음)
```

조회수 자체는 Redis에서 실시간으로 `INCR`로 처리하고, 100 단위로만 MySQL에 백업하여 DB 부하를 최소화합니다.

---

### Redis Sorted Set을 활용한 인기글 랭킹

인기글 목록은 MySQL에 저장하지 않고 **Redis Sorted Set(ZSET)**으로 실시간 관리합니다.

```
Key:    hot-article::list::{yyyyMMdd}
Member: articleId
Score:  인기 점수 (좋아요·댓글·조회수 가중합)
```

게시글 관련 이벤트(좋아요, 댓글, 조회)가 Kafka를 통해 들어올 때마다 점수를 갱신하고, 상위 N개만 유지합니다.

```java
conn.zAdd(key, score, articleId);       // 점수 갱신
conn.zRemRange(key, 0, -limit - 1);     // 하위 항목 제거, 상위 N개만 유지
conn.expire(key, ttl);                  // 날짜가 지나면 자동 만료
```

3개 Redis 명령을 **Pipeline으로 묶어 한 번의 네트워크 왕복**으로 처리합니다.

또한 `hot-article` 서비스는 인기글 점수 계산에 필요한 댓글 수, 좋아요 수, 조회수, 게시글 생성 시각을 Redis에 자체 캐시로 보관합니다. 이벤트 처리 시마다 외부 서비스를 호출하지 않아도 되므로 서비스 간 의존성이 줄어듭니다.

---

### CommentPath — 경로 열거형 계층 댓글

계층형 댓글(`comment_v2`)은 부모 ID를 재귀 조회하는 방식 대신 **경로 열거형(Path Enumeration)** 구조를 사용합니다.

```
depth 1:  00001                 (루트 댓글 A)
depth 2:  0000100001            (A의 첫 번째 대댓글)
depth 2:  0000100002            (A의 두 번째 대댓글)
depth 3:  000010000100001       (첫 번째 대댓글의 대대댓글)
```

- 각 depth는 62진수 5자리 청크로 표현합니다 (`0-9A-Za-z`, 62^5 ≈ 9억 개 경우의 수).
- 최대 depth는 5단계로 제한합니다.
- `path` 컬럼을 기준으로 `ORDER BY path ASC`만 하면 계층 순서대로 정렬됩니다.
- 특정 댓글의 모든 하위 댓글을 구할 때 `WHERE path LIKE 'prefix%'`로 단순 범위 조회가 가능합니다.
- `path` 컬럼에 인덱스를 걸면 계층 조회·페이징 모두 인덱스 레인지 스캔으로 처리됩니다.

---

### ZSET에서 articleId 정렬 정확도 보장

Redis ZSET의 score는 `double` 타입입니다. Snowflake로 생성된 `articleId`(Long, 약 19자리)를 score로 그대로 사용하면 `2^53` 한계를 초과해 **정밀도 손실로 인한 순서 오류**가 발생할 수 있습니다.

이를 해결하기 위해 score는 `0`으로 고정하고 value에 19자리 zero-padding 문자열을 저장합니다.

```java
// ArticleIdListRepository.java
conn.zAdd(key, 0, "%019d".formatted(articleId));
// 예: articleId=1234 → "0000000000000001234"
```

score가 모두 같으면 Redis는 value의 사전순(lexicographic)으로 순서를 결정합니다. 고정폭 숫자 문자열은 사전순 = 숫자순이 보장되므로, **정밀도 손실 없이 최신 글 우선 정렬**이 유지됩니다.

---

## 📋 기술적 고려사항

자세한 내용은 [`docs/technical-considerations.md`](docs/technical-considerations.md)를 참고하세요.

- **Snowflake ID 정밀도 문제**: JavaScript의 IEEE-754 Double 한계로 인해 클라이언트 응답 시 String 변환 처리
- **Kafka Consumer 분리 필요성**: 쓰기/조회 트래픽 특성 차이로 Consumer 전용 애플리케이션 분리 고려
- **Kafka Partition과 인스턴스 수 정합성**: 파티션 수보다 인스턴스가 많으면 유휴 Consumer 발생
- **DB Sharding 전략**: 트래픽 증가 시 Snowflake ID 기반 샤딩 및 Kafka Partition Key 정렬 고려
