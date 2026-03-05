# 개선 고려 사항 (Technical Considerations)

본 프로젝트를 구현하면서 발견된 기술적인 고려사항 및 향후 개선 가능 포인트를 정리한다.

---

## 1. Long → Double 변환 시 정밀도 손실 문제

게시글 ID는 Snowflake 기반 **Long 타입**으로 생성된다.  
하지만 클라이언트(JavaScript)에서 Long 값을 처리할 때 **정밀도 손실(Precision Loss)** 문제가 발생할 수 있다.

### 문제 원인

JavaScript의 Number 타입은 내부적으로 **IEEE-754 Double**을 사용한다.

따라서 **2^53 이상의 정수 값은 정확하게 표현할 수 없다.**

예시

- Java Long: 1180000000000000000
- JavaScript Number: 정밀도 손실 발생 가능

이 문제는 Java뿐 아니라 **브라우저(JavaScript) 환경에서도 동일하게 발생한다.**

### 해결 방식

서버 내부에서는 Long 타입을 유지하되  
**클라이언트 응답 시에는 문자열(String) 형태로 변환하여 전달한다.**

예시

Server 내부  
Long articleId

Client 응답  
"articleId": "287640846127181824"

이 방식으로 클라이언트에서의 정밀도 손실 문제를 방지할 수 있다.

---

## 2. Kafka Consumer 처리 구조 고려

현재 구조에서는 일부 서비스가 **쓰기 트래픽**과 **조회 트래픽**을 동시에 처리한다.

예시

HotArticle
- Kafka Consumer (쓰기 트래픽)
- API Controller (조회 트래픽)

하지만 두 트래픽의 특성이 다르기 때문에 **애플리케이션을 분리하는 것이 더 효율적일 수 있다.**

### 문제 상황

예를 들어 다음과 같은 상황이 있다고 가정한다.

- 애플리케이션 인스턴스: 20개
- Kafka Partition: 5개

이 경우

- Consumer는 최대 5개 인스턴스만 실제로 파티션을 처리
- 나머지 15개 인스턴스는 유휴 상태

즉 **리소스 활용이 비효율적일 수 있다.**

### 개선 방향

Kafka Consumer를 별도 애플리케이션으로 분리

예시

API Application
- REST API 처리
- 조회 트래픽 처리

Consumer Application
- Kafka 메시지 처리
- 이벤트 기반 데이터 갱신

이렇게 분리하면

- 리소스 활용도 개선
- 확장 전략 분리 가능
- 트래픽 특성에 맞는 스케일링 가능

---

## 3. Kafka 설정 고려사항

Kafka Consumer 설정 시 다음 요소들을 함께 고려해야 한다.

- Consumer Group 구성
- Partition 수
- Application Instance 수

예시

- Application Instance : 20
- Kafka Partition : 5

이 경우 실제 메시지를 처리하는 Consumer는 최대 **5개**만 동작한다.

따라서 다음과 같은 전략을 고려할 수 있다.

- Partition 수 증가
- Consumer 전용 애플리케이션 분리
- 트래픽 특성에 따른 Consumer 그룹 분리

---

## 4. Kafka 설정 학습 필요

현재 Kafka 설정은 **기본적인 수준으로 구성되어 있다.**

하지만 실제 운영 환경에서는 다음 요소들에 대한 추가적인 고려가 필요하다.

- Offset 관리
- Consumer Rebalance 전략
- Retry / Dead Letter Queue
- 메시지 순서 보장 전략
- 메시지 중복 처리(Idempotency)

Kafka 기반 시스템에서는 이러한 요소들이 **시스템 안정성에 큰 영향을 줄 수 있다.**

---

## 5. Sharding 전략 고려

현재 시스템에서는 **DB Sharding 전략을 적용하지 않았다.**

하지만 트래픽 증가 시 다음과 같은 전략을 고려할 수 있다.

- Shard Key 기반 데이터 분산
- Snowflake ID 기반 샤딩
- Kafka Partition Key와 Shard Key 정렬

이를 통해

- 데이터 저장 부하 분산
- 이벤트 처리 순서 보장
- 시스템 확장성 확보

가 가능하다.

---

# 정리

본 프로젝트에서는 다음과 같은 기술적 고려사항을 확인하였다.

- Snowflake ID(Long) 사용 시 JavaScript 정밀도 문제
- Kafka Consumer와 API 처리 구조 분리 필요성
- Kafka Partition과 인스턴스 수 관계
- Kafka 운영 설정에 대한 추가 학습 필요
- 향후 Sharding 전략 적용 가능성

이러한 부분들은 **서비스 규모가 확장될 경우 중요한 설계 요소가 될 수 있다.**