package prd.peurandel.prdcore.Manager
import org.litote.kmongo.coroutine.* // KMongo Coroutine Extension
import org.litote.kmongo.eq
import org.litote.kmongo.* // 기타 KMongo import
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.reactivestreams.client.ClientSession // 또는 다른 드라이버의 세션 타입
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID

// --- OrderRepository 인터페이스 구현 ---
class MongoOrderRepositoryImpl(
    // bazaar 데이터베이스 객체를 주입받음
    private val database: CoroutineDatabase
) : OrderRepository {

    // 각 메소드에서 사용할 컬렉션을 명시적으로 지정
    private val buyOrderCollection = database.getCollection<BuyOrder>("buy_orders")
    private val sellOfferCollection = database.getCollection<SellOffer>("sell_offers")

    override suspend fun findActiveBuyOrdersForItem(itemId: String, limit: Int): List<BuyOrder> {
        // 세션 파라미터 제거하고 구현
        val filter = Filters.and(
            BuyOrder::itemId eq itemId,
            Filters.`in`(BuyOrder::status.name, OrderStatus.ACTIVE, OrderStatus.PARTIALLY_FILLED)
        )
        val sort = Sorts.orderBy(Sorts.descending(BuyOrder::pricePerUnit.name), Sorts.ascending(BuyOrder::timestampPlaced.name))

        // 세션 없이 컬렉션 직접 사용
        return buyOrderCollection.find(filter).sort(sort).limit(limit).toList()
    }

    // session 파라미터 제거
    override suspend fun findActiveSellOffersForItem(itemId: String, limit: Int): List<SellOffer> {
        // 활성 상태이고, itemId가 일치하는 문서를 찾음
        // 가격 오름차순, 시간 오름차순 정렬
        val filter = Filters.and(
            SellOffer::itemId eq itemId,
            Filters.`in`(SellOffer::status.name, OrderStatus.ACTIVE, OrderStatus.PARTIALLY_FILLED)
        )
        val sort = Sorts.orderBy(Sorts.ascending(SellOffer::pricePerUnit.name), Sorts.ascending(SellOffer::timestampPlaced.name))

        // session 관련 분기 제거, 직접 컬렉션 사용
        return sellOfferCollection.find(filter).sort(sort).limit(limit).toList()
    }

    // session 파라미터 제거
    override suspend fun findBuyOrderById(orderId: String): BuyOrder? {
        // session 관련 분기 제거, 직접 컬렉션 사용
        return buyOrderCollection.findOneById(orderId)
    }

    // session 파라미터 제거
    override suspend fun findSellOfferById(orderId: String): SellOffer? {
        // session 관련 분기 제거, 직접 컬렉션 사용
        return sellOfferCollection.findOneById(orderId)
    }

    override suspend fun findActiveOrdersByPlayer(playerUUID: String): List<Pair<BuyOrder?, SellOffer?>> {
        // CoroutineScope를 사용하여 두 컬렉션 조회를 병렬로 실행 (성능 향상)
        return coroutineScope {
            // 1. 해당 플레이어의 활성 구매 주문 조회 (비동기)
            val buyOrdersDeferred = async {
                val filter = Filters.and(
                    BuyOrder::playerUUID eq playerUUID, // 플레이어 UUID 일치
                    Filters.`in`(BuyOrder::status.name, OrderStatus.ACTIVE, OrderStatus.PARTIALLY_FILLED) // 활성 상태
                )
                // buyOrderCollection 사용 (클래스 내부에 선언된 변수)
                buyOrderCollection.find(filter).toList()
            }

            // 2. 해당 플레이어의 활성 판매 제안 조회 (비동기)
            val sellOffersDeferred = async {
                val filter = Filters.and(
                    SellOffer::playerUUID eq playerUUID, // 플레이어 UUID 일치
                    Filters.`in`(SellOffer::status.name, OrderStatus.ACTIVE, OrderStatus.PARTIALLY_FILLED) // 활성 상태
                )
                // sellOfferCollection 사용 (클래스 내부에 선언된 변수)
                sellOfferCollection.find(filter).toList()
            }

            // 3. 두 비동기 작업이 완료될 때까지 기다림
            val buyOrders = buyOrdersDeferred.await()
            val sellOffers = sellOffersDeferred.await()

            // 4. 조회된 결과를 List<Pair<BuyOrder?, SellOffer?>> 형태로 조합
            val combinedList = mutableListOf<Pair<BuyOrder?, SellOffer?>>()
            buyOrders.forEach { buyOrder ->
                combinedList.add(Pair(buyOrder, null)) // 구매 주문은 첫 번째 요소, 판매 제안은 null
            }
            sellOffers.forEach { sellOffer ->
                combinedList.add(Pair(null, sellOffer)) // 구매 주문은 null, 판매 제안은 두 번째 요소
            }

            // 5. (선택) 필요하다면 리스트를 정렬 (예: 최신 주문/제안 순)
            combinedList.sortByDescending {
                // Pair의 첫 번째(BuyOrder) 또는 두 번째(SellOffer) 요소의 timestamp를 기준으로 정렬
                (it.first?.timestampPlaced ?: it.second?.timestampPlaced)
            }

            // 6. 조합된 리스트 반환
            combinedList
        } // coroutineScope 종료
    } // findActiveOrdersByPlayer 메소드 종료
    // session 파라미터 제거
    override suspend fun saveBuyOrder(order: BuyOrder): Boolean {
        return try {
            // session 관련 분기 제거, 직접 컬렉션 사용
            buyOrderCollection.insertOne(order).wasAcknowledged()
        } catch (e: Exception) {
            println("Error saving BuyOrder: ${e.message}") // 오류 로깅 추가 권장
            false
        }
    }

    // session 파라미터 제거
    override suspend fun saveSellOffer(offer: SellOffer): Boolean {
        return try {
            // session 관련 분기 제거, 직접 컬렉션 사용
            sellOfferCollection.insertOne(offer).wasAcknowledged()
        } catch (e: Exception) {
            println("Error saving SellOffer: ${e.message}") // 오류 로깅 추가 권장
            false
        }
    }

    // session 파라미터 제거
    override suspend fun updateBuyOrder(order: BuyOrder): Boolean {
        return try {
            val filter = BuyOrder::id eq order.id
            // session 관련 분기 제거, 직접 컬렉션 사용
            buyOrderCollection.replaceOne(filter, order).modifiedCount == 1L
        } catch (e: Exception) {
            println("Error updating BuyOrder: ${e.message}") // 오류 로깅 추가 권장
            false
        }
    }

    // session 파라미터 제거
    override suspend fun updateSellOffer(offer: SellOffer): Boolean {
        return try {
            val filter = SellOffer::id eq offer.id
            // session 관련 분기 제거, 직접 컬렉션 사용
            sellOfferCollection.replaceOne(filter, offer).modifiedCount == 1L
        } catch (e: Exception) {
            println("Error updating SellOffer: ${e.message}") // 오류 로깅 추가 권장
            false
        }
    }
}

// --- 다른 Repository 구현 (MongoCategoryRepositoryImpl, MongoProductRepositoryImpl 등)도 유사하게 작성 ---
// 각 구현체는 database.getCollection<해당 데이터 클래스>("해당 컬렉션 이름") 을 사용