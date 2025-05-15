package prd.peurandel.prdcore.Manager // 사용자의 패키지

import com.mongodb.kotlin.client.coroutine.MongoClient // 혹은 CoroutineClient
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.TransactionOptions
import com.mongodb.WriteConcern
import com.mongodb.reactivestreams.client.ClientSession
import org.litote.kmongo.coroutine.CoroutineDatabase // KMongo 사용 시, 아니면 MongoDatabase 타입 사용
import org.litote.kmongo.coroutine.CoroutineClient // KMongo 사용 시
import java.util.logging.Logger

// 필요한 await 확장 함수 import !!!
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.litote.kmongo.coroutine.abortTransactionAndAwait

class MongoDatabaseManagerImpl(
    // CoroutineClient (KMongo) 또는 MongoClient (Kotlin Driver) 타입 확인 필요
    private val client: CoroutineClient, // 또는 MongoClient 타입
    private val database: CoroutineDatabase, // 또는 MongoDatabase 타입
    private val logger: Logger
) : DatabaseManager {

    override suspend fun <T> executeTransaction(action: suspend (ClientSession) -> T): T {
        // startSession()이 ClientSession을 반환하도록 수정되어야 함
        val session = startSession()

        try {
            // 트랜잭션 옵션 설정 (예시)
            val txOptions = TransactionOptions.builder()
                .readPreference(ReadPreference.primary())
                .readConcern(ReadConcern.MAJORITY)
                .writeConcern(WriteConcern.MAJORITY)
                .build()

            // session.withTransaction 사용이 더 간결할 수 있음 (드라이버/라이브러리 지원 시)
            // 아래는 수동 관리 예시
            session.startTransaction(txOptions) // 트랜잭션 시작

            val result = action(session) // 사용자가 전달한 DB 작업 실행

            // 커밋하고 결과 기다림 (결과 없으면 null)
            session.commitTransaction()
            logger.info("트랜잭션 성공적으로 커밋됨.")
            return result
        } catch (e: Exception) {
            logger.warning("트랜잭션 실패, 롤백 시도: ${e.message}")
            try {
                // 롤백하고 결과 기다림 (결과 없으면 null)
                session.abortTransaction()
                logger.info("트랜잭션 롤백됨.")
            } catch (rollbackEx: Exception) {
                logger.severe("트랜잭션 롤백 실패: ${rollbackEx.message}")
                // 롤백 실패는 심각한 문제일 수 있음
            }
            throw e // 원본 예외 다시 던지기
        } finally {
            try {
                session.close() // 세션 리소스 해제
            } catch (e: Exception) {
                logger.warning("세션 종료 중 오류: ${e.message}")
            }
        }
    }

    override suspend fun startSession(): ClientSession {
        // client 타입에 따라 실제 세션 시작 방법이 다를 수 있음
        // KMongo CoroutineClient 사용 시: client.startSession() 바로 ClientSession 반환 가능성 있음
        // Kotlin Driver MongoClient 사용 시: client.startSession() 이 Publisher<ClientSession> 반환

        // Kotlin Driver 사용 가정 시 .awaitFirst() 사용
        return client.startSession() // 첫 번째 ClientSession 객체 반환 (없으면 예외)
    }

    // isConnected 구현 예시 (KMongo 사용 시 더 간결할 수 있음)
    override suspend fun isConnected(): Boolean {
        return try {
            // database 타입이 CoroutineDatabase(KMongo) 인지 MongoDatabase(Kotlin Driver)인지 확인 필요
            // 아래는 Kotlin Driver의 MongoDatabase 사용 예시
            database.runCommand<org.bson.Document>("{ ping: 1 }".toBsonDocument()) != null
        } catch (e: Exception) {
            logger.warning("데이터베이스 연결 확인 실패: ${e.message}")
            false
        }
    }

    override suspend fun close() {
        try {
            // client 타입에 따라 close()가 suspend 함수인지 확인 필요
            // KMongo/Kotlin Driver 모두 동기 함수일 가능성 높음
            client.close()
            logger.info("MongoDB 연결이 정상적으로 종료되었습니다.")
        } catch (e: Exception) {
            logger.severe("MongoDB 연결 종료 중 오류 발생: ${e.message}")
        }
    }

    // BSON Document 파싱 확장 함수 (필요시 사용)
    private fun String.toBsonDocument(): org.bson.Document {
        return org.bson.Document.parse(this)
    }
}