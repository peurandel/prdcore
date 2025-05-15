package prd.peurandel.prdcore.Manager

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.reactivestreams.client.ClientSession
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import java.util.UUID
import java.util.logging.Logger

/**
 * MongoDB를 사용한 트랜잭션 저장소 구현체
 */
class MongoTransactionRepositoryImpl(
    private val database: CoroutineDatabase,
    private val logger: Logger? = null
) : TransactionRepository {

    private val transactionCollection: CoroutineCollection<Transaction> = database.getCollection("transactions")

    /**
     * 새로운 거래 내역을 저장합니다.
     */
    override suspend fun saveTransaction(transaction: Transaction): Boolean {
        return try {
            transactionCollection.save(transaction)!!.wasAcknowledged()
        } catch (e: Exception) {
            logger?.warning("거래 내역 저장 중 오류 발생: ${e.message}")
            false
        }
    }

    /**
     * 특정 플레이어의 거래 내역을 조회합니다.
     */
    override suspend fun findTransactionsByPlayer(playerUUID: String, limit: Int): List<Transaction> {
        return try {
            // 구매자 또는 판매자가 해당 플레이어인 거래 내역 조회
            val filter = Filters.or(
                Transaction::buyerUUID eq playerUUID,
                Transaction::sellerUUID eq playerUUID
            )

            // 최신 거래부터 조회
            transactionCollection.find(filter)
                .sort(Sorts.descending(Transaction::timestampCompleted.name))
                .limit(limit)
                .toList()
        } catch (e: Exception) {
            logger?.warning("플레이어 거래 내역 조회 중 오류 발생: ${e.message}")
            emptyList()
        }
    }

    /**
     * 세션을 사용하여 새로운 거래 내역을 저장합니다 (트랜잭션 지원).
     */
    suspend fun saveTransaction(transaction: Transaction, session: ClientSession): Boolean {
        return try {
            transactionCollection.save(session, transaction)!!.wasAcknowledged()
        } catch (e: Exception) {
            logger?.warning("세션 내 거래 내역 저장 중 오류 발생: ${e.message}")
            false
        }
    }

    /**
     * 특정 아이템에 대한 거래 내역을 조회합니다.
     */
    suspend fun findTransactionsByItem(itemId: String, limit: Int): List<Transaction> {
        return try {
            transactionCollection.find(Transaction::itemId eq itemId)
                .sort(Sorts.descending(Transaction::timestampCompleted.name))
                .limit(limit)
                .toList()
        } catch (e: Exception) {
            logger?.warning("아이템별 거래 내역 조회 중 오류 발생: ${e.message}")
            emptyList()
        }
    }

    /**
     * 특정 기간 내의 거래 내역을 조회합니다.
     */
    suspend fun findTransactionsByTimeRange(startTime: Long, endTime: Long): List<Transaction> {
        return try {
            val filter = Filters.and(
                Filters.gte(Transaction::timestampCompleted.name, startTime),
                Filters.lte(Transaction::timestampCompleted.name, endTime)
            )

            transactionCollection.find(filter)
                .sort(Sorts.descending(Transaction::timestampCompleted.name))
                .toList()
        } catch (e: Exception) {
            logger?.warning("기간별 거래 내역 조회 중 오류 발생: ${e.message}")
            emptyList()
        }
    }
}