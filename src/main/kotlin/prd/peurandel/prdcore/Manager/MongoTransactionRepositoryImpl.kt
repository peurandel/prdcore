package prd.peurandel.prdcore.Manager

import com.mongodb.client.ClientSession
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * MongoDB를 사용한 트랜잭션 저장소 구현체
 */
class MongoTransactionRepositoryImpl(
    private val database: MongoDatabase,
    private val logger: Logger? = null
) : TransactionRepository {

    private val transactionCollection: MongoCollection<Document> = database.getCollection("transactions")

    init {
        // 인덱스 생성 - 플레이어 UUID, 아이템 ID, 완료 시간에 대한 검색 최적화
        try {
            transactionCollection.createIndex(Document("buyerUUID", 1))
            transactionCollection.createIndex(Document("sellerUUID", 1))
            transactionCollection.createIndex(Document("itemId", 1))
            transactionCollection.createIndex(Document("timestampCompleted", -1))
            logger?.info("Transaction 컬렉션 인덱스가 성공적으로 생성되었습니다.")
        } catch (e: Exception) {
            logger?.warning("Transaction 컬렉션 인덱스 생성 중 오류 발생: ${e.message}")
        }
    }

    /**
     * 새로운 거래 내역을 저장합니다.
     */
    override suspend fun saveTransaction(transaction: Transaction): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = transactionToDocument(transaction)
                val result = transactionCollection.insertOne(doc)
                val success = result.wasAcknowledged()
                if (success) {
                    logger?.info("거래 내역이 성공적으로 저장되었습니다: ${transaction.id}")
                }
                success
            } catch (e: Exception) {
                logger?.warning("거래 내역 저장 중 오류 발생: ${e.message}")
                false
            }
        }
    }

    /**
     * 특정 플레이어의 거래 내역을 조회합니다.
     */
    override suspend fun findTransactionsByPlayer(playerUUID: String, limit: Int): List<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                // 구매자 또는 판매자가 해당 플레이어인 거래 내역 조회
                val filter = Filters.or(
                    Filters.eq("buyerUUID", playerUUID),
                    Filters.eq("sellerUUID", playerUUID)
                )

                // 최신 거래부터 조회
                val transactions = mutableListOf<Transaction>()
                transactionCollection.find(filter)
                    .sort(Sorts.descending("timestampCompleted"))
                    .limit(limit)
                    .forEach { doc ->
                        val transaction = documentToTransaction(doc)
                        if (transaction != null) {
                            transactions.add(transaction)
                        }
                    }
                
                logger?.info("플레이어($playerUUID)의 거래 내역 ${transactions.size}개를 조회했습니다.")
                transactions
            } catch (e: Exception) {
                logger?.warning("플레이어 거래 내역 조회 중 오류 발생: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * 세션을 사용하여 새로운 거래 내역을 저장합니다 (트랜잭션 지원).
     */
    suspend fun saveTransaction(transaction: Transaction, session: ClientSession): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = transactionToDocument(transaction)
                val result = transactionCollection.insertOne(session, doc)
                val success = result.wasAcknowledged()
                if (success) {
                    logger?.info("트랜잭션 내에서 거래 내역이 성공적으로 저장되었습니다: ${transaction.id}")
                }
                success
            } catch (e: Exception) {
                logger?.warning("세션 내 거래 내역 저장 중 오류 발생: ${e.message}")
                false
            }
        }
    }

    /**
     * 특정 아이템에 대한 거래 내역을 조회합니다.
     */
    suspend fun findTransactionsByItem(itemId: String, limit: Int): List<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("itemId", itemId)
                
                val transactions = mutableListOf<Transaction>()
                transactionCollection.find(filter)
                    .sort(Sorts.descending("timestampCompleted"))
                    .limit(limit)
                    .forEach { doc ->
                        val transaction = documentToTransaction(doc)
                        if (transaction != null) {
                            transactions.add(transaction)
                        }
                    }
                
                logger?.info("아이템($itemId)의 거래 내역 ${transactions.size}개를 조회했습니다.")
                transactions
            } catch (e: Exception) {
                logger?.warning("아이템별 거래 내역 조회 중 오류 발생: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * 특정 기간 내의 거래 내역을 조회합니다.
     */
    suspend fun findTransactionsByTimeRange(startTime: Long, endTime: Long): List<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.and(
                    Filters.gte("timestampCompleted", startTime),
                    Filters.lte("timestampCompleted", endTime)
                )

                val transactions = mutableListOf<Transaction>()
                transactionCollection.find(filter)
                    .sort(Sorts.descending("timestampCompleted"))
                    .forEach { doc ->
                        val transaction = documentToTransaction(doc)
                        if (transaction != null) {
                            transactions.add(transaction)
                        }
                    }
                
                logger?.info("기간 내($startTime ~ $endTime) 거래 내역 ${transactions.size}개를 조회했습니다.")
                transactions
            } catch (e: Exception) {
                logger?.warning("기간별 거래 내역 조회 중 오류 발생: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Transaction 객체를 MongoDB Document로 변환합니다.
     */
    private fun transactionToDocument(transaction: Transaction): Document {
        // 거래 총액 계산 (DB에는 저장할 수 있도록)
        val totalPrice = transaction.quantity * transaction.pricePerUnit

        return Document()
            .append("_id", transaction.id)
            .append("buyerUUID", transaction.buyerUUID)
            .append("sellerUUID", transaction.sellerUUID)
            .append("itemId", transaction.itemId)
            // 아이템 이름은 클라이언트에서 가져와 표시하도록 함
            .append("quantity", transaction.quantity)
            .append("pricePerUnit", transaction.pricePerUnit)
            .append("totalPrice", totalPrice) // 계산된 총액
            .append("buyOrderIdRef", transaction.buyOrderIdRef) // 필드명 수정: buyOrderIdRef
            .append("sellOfferIdRef", transaction.sellOfferIdRef) // 필드명 수정: sellOfferIdRef
            .append("timestampCompleted", transaction.timestampCompleted)
    }

    /**
     * MongoDB Document를 Transaction 객체로 변환합니다.
     */
    private fun documentToTransaction(doc: Document): Transaction? {
        return try {
            val id = if (doc.get("_id") is String) {
                doc.getString("_id")
            } else {
                doc.getObjectId("_id").toString()
            }
            
            Transaction(
                id = id,
                buyerUUID = doc.getString("buyerUUID"),
                sellerUUID = doc.getString("sellerUUID"),
                itemId = doc.getString("itemId"),
                quantity = doc.getInteger("quantity"),
                pricePerUnit = doc.getDouble("pricePerUnit"),
                timestampCompleted = doc.getLong("timestampCompleted"),
                buyOrderIdRef = doc.getString("buyOrderId"), // 여기서는 DB 필드명 그대로 사용
                sellOfferIdRef = doc.getString("sellOfferId") // 여기서는 DB 필드명 그대로 사용
            )
        } catch (e: Exception) {
            logger?.warning("Transaction 문서 변환 중 오류 발생: ${e.message}, 문서: $doc")
            null
        }
    }
}