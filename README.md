# 재고 관리 API

Java / Spring Boot / PostgreSQL 기반 재고 관리 사전 과제입니다.
상품 재고 조회, 미등록 상품 자동 등록을 포함한 입고, 재고 부족을 방지하는 출고를 구현했습니다.

## 기술 구성

- Java 17, Spring Boot 4.1.1, Gradle Wrapper 9.7.1
- Spring MVC, Bean Validation, Spring Data JPA
- PostgreSQL 17, Flyway
- JUnit, Testcontainers: H2 대체 DB 없이 실제 PostgreSQL 통합 테스트

Spring Data JPA의 `@Lock(PESSIMISTIC_WRITE)`로 변경할 상품을 잠근 뒤 엔티티에서 재고를 변경합니다.
컨트롤러는 HTTP 입력 검증, 서비스는 트랜잭션과 흐름 제어, 엔티티는 수량 규칙, 저장소는 조회와 잠금을 담당합니다.
Flyway가 DDL을 관리하고 Hibernate는 `ddl-auto: validate`로 스키마만 검증합니다. OSIV는 비활성화했습니다.

## 실행

Java 17과 Docker Compose가 필요합니다. 애플리케이션과 테스트 모두 Gradle Wrapper로 실행합니다.

```bash
docker compose up -d --wait
./gradlew bootRun
```

애플리케이션은 `http://localhost:8080`에서 실행되며, 시작 시 Flyway가 DDL을 적용합니다.
DB는 `localhost:5432/inventory`, 로컬 개발용 계정과 비밀번호는 `inventory`입니다.
이미 5432 포트를 사용 중이면 Compose의 호스트 포트와 `DB_URL`을 함께 변경해 주세요.

| 환경 변수 | 기본값 |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/inventory` |
| `DB_USERNAME` | `inventory` |
| `DB_PASSWORD` | `inventory` |
| `PORT` | `8080` |

```bash
# PostgreSQL 컨테이너 정지 (데이터 유지)
docker compose down

# 테스트 및 실행 JAR 생성
./gradlew clean test bootJar
java -jar build/libs/deepFine-0.0.1-SNAPSHOT.jar
```

테스트는 Docker가 실행 중이면 PostgreSQL 컨테이너를 자동 생성·정리합니다.
Compose DB 또는 외부 DB는 사용하지 않습니다. Docker가 없으면 테스트는 실패하며 자동으로 건너뛰지 않습니다.

## DB DDL

제출용 DDL이자 실제 실행되는 마이그레이션 파일:
[`src/main/resources/db/migration/V1__create_product.sql`](src/main/resources/db/migration/V1__create_product.sql)

| 컬럼 | 타입 | 규칙 |
| --- | --- | --- |
| `id` | BIGINT IDENTITY | PK, 서버에서 생성 |
| `name` | VARCHAR(100) | NOT NULL, UNIQUE, 공백 이름 금지 |
| `quantity` | BIGINT | NOT NULL, 0 이상 CHECK |

DDL을 수동 실행할 필요가 없습니다. Flyway가 스키마 버전을 관리하므로 후속 변경은 새로운 마이그레이션 파일로 추가합니다.

## API

### 입고: `POST /api/products/receipts`

상품명으로 기존 상품을 찾습니다. 없으면 신규 상품 등록과 입고를 한 번에 처리합니다.
신규·기존 상품 모두 입고 완료 후 상품 정보와 `200 OK`를 반환합니다.

```bash
curl -i -X POST http://localhost:8080/api/products/receipts \
  -H 'Content-Type: application/json' \
  -d '{"name":"상품 A","quantity":10}'
```

```json
{"id":1,"name":"상품 A","quantity":10}
```

### 출고: `POST /api/products/{id}/shipments`

아래 예시의 ID는 입고 응답의 `id`로 바꿔 사용합니다.

```bash
curl -i -X POST http://localhost:8080/api/products/1/shipments \
  -H 'Content-Type: application/json' \
  -d '{"quantity":3}'
```

```json
{"id":1,"name":"상품 A","quantity":7}
```

### 재고 조회: `GET /api/products/{id}`

```bash
curl -i http://localhost:8080/api/products/1
```

```json
{"id":1,"name":"상품 A","quantity":7}
```

### 입력 규칙 및 오류

- 상품명 앞뒤 공백은 제거합니다. 제거 후 1~100자이며 공백만 있는 이름은 허용하지 않습니다.
- MVP에서는 상품명을 고유 식별 기준으로 사용합니다. 대소문자와 내부 공백은 구분합니다.
- 입출고 수량은 `1`부터 `9223372036854775807`까지의 정수입니다. 현재 재고는 `0`도 허용합니다.
- ID는 양수여야 합니다. 잘못된 JSON, 누락된 필수 값, 소수 수량, 알 수 없는 필드는 거부합니다.
- 출고 요청량이 재고와 같으면 성공하며 재고는 `0`이 됩니다.

| HTTP 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 입력 검증 실패, 잘못된 JSON 또는 ID |
| 404 | `PRODUCT_NOT_FOUND` | 조회·출고 대상 상품 없음 |
| 409 | `INSUFFICIENT_STOCK` | 출고 재고 부족 |
| 409 | `STOCK_LIMIT_EXCEEDED` | 입고 후 BIGINT 범위 초과 |
| 503 | `DATABASE_UNAVAILABLE` | DB 요청 실패 또는 제한 시간 초과 |

오류는 `application/problem+json`으로 반환합니다. 예:

```json
{
  "type":"about:blank",
  "title":"Conflict",
  "status":409,
  "detail":"출고 가능한 재고가 부족합니다.",
  "instance":"/api/products/1/shipments",
  "code":"INSUFFICIENT_STOCK"
}
```

## 동시성 및 정합성

### 입고

상품명으로 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 조회 후 잠긴 엔티티에서 재고를 증가시킵니다.
엔티티가 변경되면 JPA 변경 감지가 트랜잭션 커밋 시 UPDATE를 수행합니다.
덧셈 전에 상한을 검사하여 BIGINT 오버플로를 방지합니다.

미등록 상품에는 잠글 행이 없으므로 신규 등록만 네이티브 SQL
`INSERT ... ON CONFLICT (name) DO NOTHING`으로 처리합니다. 초기 재고 0으로 생성한 뒤
다시 비관적 락으로 조회하여 입고합니다. UNIQUE 제약과 충돌 무시로 동시 최초 입고의 중복 등록을 방지합니다.
모든 단계는 같은 트랜잭션 안에서 실행되므로 입고에 실패하면 신규 등록도 롤백됩니다.
PostgreSQL 기본 READ COMMITTED에서 충돌 대기 후 다음 조회는 커밋된 상품을 볼 수 있습니다.

### 출고

ID로 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 조회 후 엔티티에서 재고를 확인하고 차감합니다.
다른 입출고 요청은 같은 행의 잠금이 해제될 때까지 기다린 뒤 최신 재고를 읽습니다.
상품이 없으면 404, 잠긴 상품의 재고가 부족하면 409를 반환하며 변경 사항은 롤백됩니다.

### 트랜잭션 범위와 선택 이유

서비스의 `@Transactional`이 조회 잠금 → 업무 검증 → 변경 감지 UPDATE → 커밋까지 묶습니다.
잠금은 트랜잭션 종료까지 유지됩니다. `@Transactional`만으로 갱신 유실을 막는 것은 아닙니다.
같은 상품을 수정하는 모든 입출고 경로에서 비관적 락을 사용해야 합니다.

동일 상품의 동시 변경을 기다린 뒤 처리하도록 비관적 락을 선택했습니다.
낙관적 락은 충돌 시 실패와 재시도 정책이 필요합니다. 비관적 락은 대기와 처리량 저하가 발생할 수 있어
트랜잭션을 짧게 유지하고 잠금 중 외부 API를 호출하지 않습니다.
현재는 상품 한 개만 변경하며, 여러 상품을 한 번에 잠그는 기능을 추가한다면 ID 순서 등 잠금 순서를 통일해야 합니다.

재고에는 DB CHECK 제약도 적용했습니다. 같은 PostgreSQL에 연결하면 여러 애플리케이션 인스턴스에서도 보호됩니다.
DB statement timeout은 5초, 연결 획득 제한도 5초입니다.
응답의 재고는 해당 연산의 결과이며 이후 다른 요청으로 변경될 수 있습니다.

공식 근거:
- [Spring Data JPA Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
- [PostgreSQL 행 잠금](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS)
- [PostgreSQL INSERT / ON CONFLICT](https://www.postgresql.org/docs/17/sql-insert.html)

## 테스트

`./gradlew test`로 다음을 검증합니다.

- 실제 HTTP를 통한 신규·기존 입고 → 전량 출고 → 조회
- 미등록 상품 404, 재고 부족 409, 잘못된 입력 400
- 수량 상한 초과 시 재고 유지
- 동시 신규 입고 80건 → 상품 1행, 재고 80
- 재고 20개에 동시 출고 80건 → 성공 20건, 재고 부족 60건, 최종 재고 0
- 초기 재고 100개에 입고 50건(각 2개)·출고 50건(각 1개) → 최종 재고 150
- DB 자체의 음수 재고·중복 상품명 차단

동시성 테스트는 16개 워커를 시작 장벽으로 동시에 출발시키며, 각 요청이 별도 서비스 트랜잭션을 사용합니다.
모든 Future의 결과를 수집해 스레드 안의 실패가 테스트에서 누락되지 않게 했습니다.
테스트 보고서: `build/reports/tests/test/index.html`.

## MVP 범위 및 확장 방향

상품 코드가 주어지지 않아 상품명을 고유 키로 정의했습니다. 상품명 변경이나 동명 상품이 필요하면
SKU를 별도 UNIQUE 키로 도입하고 표시 이름과 분리할 수 있습니다.
인증, 재고 변경 이력, 목록·검색, 요청 멱등성은 현재 범위에 포함하지 않았습니다.
입출고 POST를 반복하면 매번 반영되므로 네트워크 응답 유실 시 자동 재시도는 중복 반영을 유발할 수 있습니다.
실서비스에서는 요청 키와 처리 결과를 같은 트랜잭션으로 저장하는 멱등성 처리 및 변경 이력을 추가할 수 있습니다.
