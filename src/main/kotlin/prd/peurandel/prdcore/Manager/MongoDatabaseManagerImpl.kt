package prd.peurandel.prdcore.Manager

import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.TransactionOptions
import com.mongodb.WriteConcern
import com.mongodb.client.ClientSession
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import java.util.logging.Logger

class MongoDatabaseManagerImpl(
    private val client: MongoClient,
    private val database: MongoDatabase,
    private val logger: Logger
) : DatabaseManager {

    override suspend fun <T> executeTransaction(action: suspend (ClientSession) -> T): T {
        return withContext(Dispatchers.IO) {
            val session = client.startSession()

            try {
                // 트랜잭션 옵션 설정
                val txOptions = TransactionOptions.builder()
                    .readPreference(ReadPreference.primary())
                    .readConcern(ReadConcern.MAJORITY)
                    .writeConcern(WriteConcern.MAJORITY)
                    .build()

                // 트랜잭션 시작
                session.startTransaction(txOptions)

                val result = action(session) // 사용자가 전달한 DB 작업 실행

                // 트랜잭션 커밋
                session.commitTransaction()
                logger.info("트랜잭션 성공적으로 커밋됨.")
                result
            } catch (e: Exception) {
                logger.warning("트랜잭션 실패, 롤백 시도: ${e.message}")
                try {
                    session.abortTransaction()
                    logger.info("트랜잭션 롤백됨.")
                } catch (rollbackEx: Exception) {
                    logger.severe("트랜잭션 롤백 실패: ${rollbackEx.message}")
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
    }

    override suspend fun startSession(): ClientSession {
        return withContext(Dispatchers.IO) {
            client.startSession()
        }
    }

    override suspend fun isConnected(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = database.runCommand(Document("ping", 1))
                result != null
            } catch (e: Exception) {
                logger.warning("데이터베이스 연결 확인 실패: ${e.message}")
                false
            }
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            try {
                client.close()
                logger.info("MongoDB 연결이 정상적으로 종료되었습니다.")
            } catch (e: Exception) {
                logger.severe("MongoDB 연결 종료 중 오류 발생: ${e.message}")
            }
        }
    }
}