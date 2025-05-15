package prd.peurandel.prdcore.Manager
import com.mongodb.reactivestreams.client.ClientSession
import java.util.UUID
import java.time.Instant
// kotlinx-coroutines-core 의존성이 필요합니다.
import kotlinx.coroutines.* // --- Enums ---
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

enum class OrderStatus {
    ACTIVE,
    PARTIALLY_FILLED,
    FILLED, // Buy Order 전용
    SOLD,   // Sell Offer 전용
    CANCELLED
}

enum class OrderType {
    BUY, SELL
}

// --- 데이터 클래스 (Database Entities / Domain Models) ---

data class Category(
    val id: String,
    val name: String,
    val parentCategoryId: String? = null // 최상위 카테고리는 null
)

@Serializable
data class Product(
    @SerialName("_id")
    val _id: String, // 예: "DIAMOND", "ENCHANTED_LAPIS_BLOCK"
    val name: String, // 예: "다이아몬드"
    val categoryId: String,
    val description: String? = null,
    val isTradable: Boolean = true,
    // 마인크래프트 아이템 메타데이터 등 추가 정보 포함 가능
    val itemMeta: String? = null // 예: JSON 형태의 NBT 데이터
)

data class BuyOrder(
    val id: String = UUID.randomUUID().toString(), // 고유 ID 자동 생성
    val playerUUID: String,
    val itemId: String,
    val quantityOrdered: Int,
    var quantityFilled: Int = 0,
    val pricePerUnit: Double,
    val timestampPlaced: Instant = Instant.now(),
    var status: OrderStatus = OrderStatus.ACTIVE
)

data class SellOffer(
    val id: String = UUID.randomUUID().toString(), // 고유 ID 자동 생성
    val playerUUID: String,
    val itemId: String,
    val quantityOffered: Int,
    var quantitySold: Int = 0,
    val pricePerUnit: Double,
    val timestampPlaced: Instant = Instant.now(),
    var status: OrderStatus = OrderStatus.ACTIVE
)

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val buyerUUID: String,
    val sellerUUID: String,
    val timestampCompleted: Instant = Instant.now(),
    val buyOrderIdRef: String? = null, // 연관된 주문 ID (선택적)
    val sellOfferIdRef: String? = null // 연관된 제안 ID (선택적)
)

// --- DTOs (Data Transfer Objects - API가 외부에 노출하는 데이터 형태) ---

data class CategoryInfo(
    val id: String,
    val name: String
)

data class ProductInfo(
    val id: String,
    val name: String,
    val description: String?
)

data class OrderBookEntry(
    val price: Double,
    val totalQuantity: Int // 해당 가격에 쌓인 총 수량
)

data class OrderBookSnapshot(
    val itemId: String,
    val highestBuyPrice: Double?,
    val lowestSellPrice: Double?,
    val buyOrders: List<OrderBookEntry>, // 가격 내림차순 정렬
    val sellOrders: List<OrderBookEntry> // 가격 오름차순 정렬
)

data class PlaceOrderResult(
    val success: Boolean,
    val message: String,
    val orderId: String? = null
)

data class TransactionResult(
    val success: Boolean,
    val message: String,
    val transactionId: String? = null,
    val quantityTransacted: Int? = null,
    val averagePrice: Double? = null // 여러 주문과 체결 시 평균가
)

data class PlayerOrderInfo(
    val orderId: String,
    val itemId: String,
    val type: OrderType, // BUY 또는 SELL
    val quantityTotal: Int, // 주문/제안 총 수량
    val quantityFulfilled: Int, // 채워진/판매된 수량
    val pricePerUnit: Double,
    val status: OrderStatus,
    val timestampPlaced: Instant
)

// --- Repository Interfaces (Data Access Layer - DB와 통신) ---
// 실제 구현은 DB 종류(MongoDB, SQL 등)에 따라 달라짐
interface CategoryRepository {
    suspend fun findRootCategories(): List<Category>
    suspend fun findById(id: String): Category?
    // ... 기타 필요한 메소드 (save, update 등)
}

interface ProductRepository {
    suspend fun findByCategory(categoryId: String): List<Product>
    suspend fun findByNameQuery(query: String): List<Product>
    suspend fun findById(id: String): Product?
    // ... 기타 필요한 메소드 (save, update 등)
}

interface OrderRepository {
    // 특정 상품의 활성 구매 주문 조회 (가격 내림차순, 시간 오름차순)
    suspend fun findActiveBuyOrdersForItem(itemId: String, limit: Int = 50): List<BuyOrder>
    // 특정 상품의 활성 판매 제안 조회 (가격 오름차순, 시간 오름차순)
    suspend fun findActiveSellOffersForItem(itemId: String, limit: Int = 50): List<SellOffer>
    suspend fun findBuyOrderById(orderId: String): BuyOrder?
    suspend fun findSellOfferById(orderId: String): SellOffer?
    suspend fun findActiveOrdersByPlayer(playerUUID: String): List<Pair<BuyOrder?, SellOffer?>> // Buy 또는 Sell 반환
    suspend fun saveBuyOrder(order: BuyOrder): Boolean
    suspend fun saveSellOffer(offer: SellOffer): Boolean
    suspend fun updateBuyOrder(order: BuyOrder): Boolean // 상태, 채워진 수량 업데이트
    suspend fun updateSellOffer(offer: SellOffer): Boolean // 상태, 판매된 수량 업데이트
}

interface TransactionRepository {
    suspend fun saveTransaction(transaction: Transaction): Boolean
    suspend fun findTransactionsByPlayer(playerUUID: String, limit: Int = 100): List<Transaction>
}


// --- 서비스 인터페이스 ---
interface EconomyService {
    suspend fun hasEnoughFunds(playerUUID: String, amount: Double): Boolean
    suspend fun withdraw(playerUUID: String, amount: Double): Boolean
    suspend fun deposit(playerUUID: String, amount: Double): Boolean
}

interface InventoryService {
    // 특정 아이템을 특정 수량만큼 가지고 있는지 확인
    suspend fun hasItems(playerUUID: String, itemId: String, quantity: Int): Boolean
    // 아이템 제거 (실제로는 마인크래프트 ItemStack 비교 필요)
    suspend fun removeItems(playerUUID: String, itemId: String, quantity: Int): Boolean
    // 아이템 추가
    suspend fun addItems(playerUUID: String, itemId: String, quantity: Int): Boolean
}

// --- 데이터베이스 관리자 인터페이스 ---
interface DatabaseManager {
    /**
     * 트랜잭션을 시작하고 실행합니다.
     * @param action 트랜잭션 내에서 실행할 작업
     * @return 작업의 결과
     */
    suspend fun <T> executeTransaction(action: suspend (ClientSession) -> T): T
    
    /**
     * 새로운 데이터베이스 세션을 생성합니다.
     * @return 생성된 세션
     */
    suspend fun startSession(): ClientSession
    
    /**
     * 현재 데이터베이스 연결이 활성 상태인지 확인합니다.
     * @return 연결 상태
     */
    suspend fun isConnected(): Boolean
    
    /**
     * 데이터베이스 연결을 닫습니다.
     */
    suspend fun close()
}

// --- API 인터페이스 ---
interface BazaarAPI {
    suspend fun getRootCategories(): List<CategoryInfo>
    suspend fun getProductsByCategory(categoryId: String): List<ProductInfo>
    suspend fun searchProductsByName(query: String): List<ProductInfo>
    suspend fun getProductDetailsAndOrderBook(itemId: String): Pair<ProductInfo, OrderBookSnapshot>?
    suspend fun placeBuyOrder(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult
    suspend fun placeSellOffer(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult
    suspend fun instantBuy(playerUUID: String, itemId: String, maxQuantity: Int): TransactionResult
    suspend fun instantSell(playerUUID: String, itemId: String, quantity: Int): TransactionResult
    suspend fun getPlayerActiveOrders(playerUUID: String): List<PlayerOrderInfo>
    suspend fun cancelOrder(playerUUID: String, orderId: String): PlaceOrderResult
    suspend fun getPlayerTransactionHistory(playerUUID: String): List<Transaction>
}


// --- API 구현체 ---

class BazaarAPIImpl(
    // 의존성 주입 (Dependency Injection)
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val transactionRepository: TransactionRepository,
    private val economyService: EconomyService,
    private val inventoryService: InventoryService,
    // 주문 매칭 등 핵심 로직을 담당할 서비스 (별도 클래스로 분리하는 것이 좋음)
    private val bazaarCoreService: BazaarCoreService, // 아래에 인터페이스 정의
    private val databaseManager: DatabaseManager // 데이터베이스 관리자
) : BazaarAPI {

    // --- 조회 관련 API ---

    override suspend fun getRootCategories(): List<CategoryInfo> {
        return categoryRepository.findRootCategories().map { CategoryInfo(it.id, it.name) }
    }

    override suspend fun getProductsByCategory(categoryId: String): List<ProductInfo> {
        // 카테고리 존재 여부 확인 등 추가 로직 가능
        return productRepository.findByCategory(categoryId)
            .filter { it.isTradable } // 거래 가능한 상품만 필터링
            .map { ProductInfo(it._id, it.name, it.description) }
    }

    override suspend fun searchProductsByName(query: String): List<ProductInfo> {
        if (query.isBlank() || query.length < 2) { // 너무 짧은 검색어 방지
            return emptyList()
        }
        return productRepository.findByNameQuery(query)
            .filter { it.isTradable }
            .map { ProductInfo(it._id, it.name, it.description) }
    }

    override suspend fun getProductDetailsAndOrderBook(itemId: String): Pair<ProductInfo, OrderBookSnapshot>? {
        val product = productRepository.findById(itemId) ?: return null // 상품 없으면 null 반환
        if (!product.isTradable) return null // 거래 불가능 상품이면 null 반환

        val productInfo = ProductInfo(product._id, product.name, product.description)

        // 주문서 데이터 가져오기 (DB 조회 최적화 필요 - 예: 집계 쿼리 사용)
        coroutineScope {
            val buyOrdersDeferred = async { orderRepository.findActiveBuyOrdersForItem(itemId) }
            val sellOffersDeferred = async { orderRepository.findActiveSellOffersForItem(itemId) }

            val buyOrders = buyOrdersDeferred.await()
            val sellOffers = sellOffersDeferred.await()

            // 주문서 스냅샷 생성 (가격별 수량 집계)
            val buyOrderEntries = buyOrders.groupBy { it.pricePerUnit }
                .map { (price, orders) -> OrderBookEntry(price, orders.sumOf { it.quantityOrdered - it.quantityFilled }) }
                .sortedByDescending { it.price }

            val sellOfferEntries = sellOffers.groupBy { it.pricePerUnit }
                .map { (price, offers) -> OrderBookEntry(price, offers.sumOf { it.quantityOffered - it.quantitySold }) }
                .sortedBy { it.price }

            val snapshot = OrderBookSnapshot(
                itemId = itemId,
                highestBuyPrice = buyOrderEntries.firstOrNull()?.price,
                lowestSellPrice = sellOfferEntries.firstOrNull()?.price,
                buyOrders = buyOrderEntries,
                sellOrders = sellOfferEntries
            )
            return@coroutineScope Pair(productInfo, snapshot)
        }
        // 이곳에 도달하면 안 됨 (coroutineScope가 결과를 반환하므로)
        return null
    }

    // --- 주문 생성/취소 관련 API ---

    override suspend fun placeBuyOrder(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult {
        // 1. 입력값 유효성 검사
        if (quantity <= 0 || pricePerUnit < 0) {
            return PlaceOrderResult(false, "잘못된 수량 또는 가격입니다.")
        }
        val product = productRepository.findById(itemId)
        if (product == null || !product.isTradable) {
            return PlaceOrderResult(false, "거래할 수 없는 상품입니다.")
        }

        // 2. 총 필요 금액 계산 (세금 포함은 BazaarCoreService에서 처리)
        val totalCost = quantity * pricePerUnit // 실제로는 세금 고려 필요

        // 3. 플레이어 재화 확인
        if (!economyService.hasEnoughFunds(playerUUID, totalCost)) { // 세금 포함 금액으로 확인해야 함
            return PlaceOrderResult(false, "소지금이 부족합니다.")
        }

        // 4. 핵심 서비스 호출 (주문 매칭 시도 및 주문 생성)
        return bazaarCoreService.createBuyOrder(playerUUID, itemId, quantity, pricePerUnit)
    }

    override suspend fun placeSellOffer(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult {
        // 1. 입력값 유효성 검사
        if (quantity <= 0 || pricePerUnit < 0) {
            return PlaceOrderResult(false, "잘못된 수량 또는 가격입니다.")
        }
        val product = productRepository.findById(itemId)
        if (product == null || !product.isTradable) {
            return PlaceOrderResult(false, "거래할 수 없는 상품입니다.")
        }

        // 2. 플레이어 인벤토리 확인
        if (!inventoryService.hasItems(playerUUID, itemId, quantity)) {
            return PlaceOrderResult(false, "판매할 아이템이 부족합니다.")
        }

        // 3. 핵심 서비스 호출 (아이템 임시 보관, 주문 매칭 시도 및 제안 생성)
        return bazaarCoreService.createSellOffer(playerUUID, itemId, quantity, pricePerUnit)
    }

    override suspend fun cancelOrder(playerUUID: String, orderId: String): PlaceOrderResult {
        // 핵심 서비스 호출 (주문/제안 찾기, 소유권 확인, 상태 변경, 재화/아이템 반환)
        return bazaarCoreService.cancelOrderOrOffer(playerUUID, orderId)
    }

    // --- 즉시 거래 관련 API ---

    override suspend fun instantBuy(playerUUID: String, itemId: String, maxQuantity: Int): TransactionResult {
        // 1. 입력값 유효성 검사
        if (maxQuantity <= 0) {
            return TransactionResult(false, "구매 수량은 0보다 커야 합니다.")
        }
        val product = productRepository.findById(itemId)
        if (product == null || !product.isTradable) {
            return TransactionResult(false, "거래할 수 없는 상품입니다.")
        }

        // 2. 핵심 서비스 호출 (매칭, 재화 확인, 거래 처리, 아이템 지급)
        return bazaarCoreService.executeInstantBuy(playerUUID, itemId, maxQuantity)
    }

    override suspend fun instantSell(playerUUID: String, itemId: String, quantity: Int): TransactionResult {
        // 1. 입력값 유효성 검사
        if (quantity <= 0) {
            return TransactionResult(false, "판매 수량은 0보다 커야 합니다.")
        }
        val product = productRepository.findById(itemId)
        if (product == null || !product.isTradable) {
            return TransactionResult(false, "거래할 수 없는 상품입니다.")
        }

        // 2. 플레이어 인벤토리 확인
        if (!inventoryService.hasItems(playerUUID, itemId, quantity)) {
            return TransactionResult(false, "판매할 아이템이 부족합니다.")
        }

        // 3. 핵심 서비스 호출 (매칭, 아이템 제거, 거래 처리, 재화 지급)
        // 판매 전 아이템을 먼저 제거하거나 '에스크로' 계정으로 옮기는 것이 안전함
        return bazaarCoreService.executeInstantSell(playerUUID, itemId, quantity)
    }

    // --- 플레이어 정보 조회 API ---

    override suspend fun getPlayerActiveOrders(playerUUID: String): List<PlayerOrderInfo> {
        val orders = orderRepository.findActiveOrdersByPlayer(playerUUID)
        return orders.mapNotNull { (buyOrder, sellOffer) ->
            when {
                buyOrder != null -> PlayerOrderInfo(
                    orderId = buyOrder.id, itemId = buyOrder.itemId, type = OrderType.BUY,
                    quantityTotal = buyOrder.quantityOrdered, quantityFulfilled = buyOrder.quantityFilled,
                    pricePerUnit = buyOrder.pricePerUnit, status = buyOrder.status, timestampPlaced = buyOrder.timestampPlaced
                )
                sellOffer != null -> PlayerOrderInfo(
                    orderId = sellOffer.id, itemId = sellOffer.itemId, type = OrderType.SELL,
                    quantityTotal = sellOffer.quantityOffered, quantityFulfilled = sellOffer.quantitySold,
                    pricePerUnit = sellOffer.pricePerUnit, status = sellOffer.status, timestampPlaced = sellOffer.timestampPlaced
                )
                else -> null // 둘 다 null인 경우는 없어야 함
            }
        }.sortedByDescending { it.timestampPlaced } // 최신 순 정렬
    }

    override suspend fun getPlayerTransactionHistory(playerUUID: String): List<Transaction> {
        // 페이지네이션 추가 고려 (예: offset, limit 파라미터)
        return transactionRepository.findTransactionsByPlayer(playerUUID)
    }
}


// --- Bazaar Core Service Interface (주문 매칭 등 핵심 로직 담당) ---
// 이 부분의 구현이 실제 바자 시스템의 핵심입니다.
interface BazaarCoreService {
    suspend fun createBuyOrder(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult
    suspend fun createSellOffer(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult
    suspend fun executeInstantBuy(playerUUID: String, itemId: String, maxQuantity: Int): TransactionResult
    suspend fun executeInstantSell(playerUUID: String, itemId: String, quantity: Int): TransactionResult
    suspend fun cancelOrderOrOffer(playerUUID: String, orderId: String): PlaceOrderResult
    // 내부적으로 주문 매칭, 세금 계산, 재화/아이템 이동, 상태 업데이트, 트랜잭션 기록 등을 수행
}

// --- Dummy BazaarCoreService Implementation (예시) ---
// 실제로는 훨씬 복잡한 로직과 동시성 제어가 필요합니다.
class DummyBazaarCoreServiceImpl(
    private val orderRepository: OrderRepository,
    private val transactionRepository: TransactionRepository,
    private val economyService: EconomyService,
    private val inventoryService: InventoryService,
    private val taxRate: Double = 0.01 // 예시 세율 1%
) : BazaarCoreService {
    // 동시성 제어를 위한 mutex 맵
    private val itemMutexes = ConcurrentHashMap<String, Mutex>()
    private val orderMutexes = ConcurrentHashMap<String, Mutex>()
    
    // 특정 아이템에 대한 mutex 획득
    private fun getMutexForItem(itemId: String): Mutex {
        return itemMutexes.computeIfAbsent(itemId) { Mutex() }
    }
    
    // 특정 주문에 대한 mutex 획득
    private fun getMutexForOrder(orderId: String): Mutex {
        return orderMutexes.computeIfAbsent(orderId) { Mutex() }
    }

    override suspend fun createBuyOrder(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult {
        // TODO: 실제 주문 매칭 로직 구현 (Sell Offer 찾아보기)
        println("Buy Order Matching 로직 실행 (Dummy)")

        // 매칭 안됐다고 가정하고 바로 주문 저장
        val newOrder = BuyOrder(playerUUID = playerUUID, itemId = itemId, quantityOrdered = quantity, pricePerUnit = pricePerUnit)
        val success = orderRepository.saveBuyOrder(newOrder)
        return if (success) {
            PlaceOrderResult(true, "구매 주문 생성됨 (매칭 로직 필요)", newOrder.id)
        } else {
            PlaceOrderResult(false, "주문 저장 실패")
        }
    }
    override suspend fun createSellOffer(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult {
        // 입력값 검증
        if (quantity <= 0) {
            return PlaceOrderResult(false, "수량은 0보다 커야 합니다")
        }
        if (pricePerUnit <= 0) {
            return PlaceOrderResult(false, "가격은 0보다 커야 합니다")
        }
        
        // 아이템에 대한 mutex 획득
        val itemMutex = getMutexForItem(itemId)
        
        // 아이템 lock 획득 후 처리 진행
        return itemMutex.withLock {
            println("Sell Offer Matching 로직 실행 (아이템 ID: $itemId)")
            
            // 아이템 소유 확인
            val hasItems = inventoryService.hasItems(playerUUID, itemId, quantity)
            if (!hasItems) {
                return@withLock PlaceOrderResult(false, "충분한 아이템이 없습니다")
            }
            
            var remainingQuantity = quantity
            var transactions = mutableListOf<Transaction>()
            var totalEarned = 0.0
            var itemsRemoved = false
            
            try {
                // 아이템 임시 제거
                inventoryService.removeItems(playerUUID, itemId, quantity)
                itemsRemoved = true
                
                // 해당 아이템의 활성 구매 주문들을 가격 내림차순으로 가져옴 (높은 가격 우선)
                val activeBuyOrders = orderRepository.findActiveBuyOrdersForItem(itemId)
                
                // 구매 주문과 매칭 시도
                for (buyOrder in activeBuyOrders) {
                    // 판매 가격이 구매 가격보다 높으면 매칭 불가
                    if (pricePerUnit > buyOrder.pricePerUnit) {
                        break // 더 낮은 가격의 주문은 확인할 필요 없음
                    }
                    
                    // 구매 주문에 대한 mutex 획득 (주문 잠금)
                    val orderMutex = getMutexForOrder(buyOrder.id)
                    orderMutex.withLock {
                        // 주문 최신 상태 확인 (동시성 문제 방지)
                        val updatedBuyOrder = orderRepository.findBuyOrderById(buyOrder.id)
                        if (updatedBuyOrder == null || 
                            updatedBuyOrder.status != OrderStatus.ACTIVE && 
                            updatedBuyOrder.status != OrderStatus.PARTIALLY_FILLED) {
                            return@withLock null // 이 주문은 건너뜀
                        }
                        
                        val buyerUUID = updatedBuyOrder.playerUUID
                        val availableToBuy = updatedBuyOrder.quantityOrdered - updatedBuyOrder.quantityFilled
                        
                        if (availableToBuy <= 0) return@withLock null // 이 주문은 건너뜀
                        
                        val matchedQuantity = minOf(remainingQuantity, availableToBuy)
                        val transactionPrice = updatedBuyOrder.pricePerUnit // 구매자 가격으로 거래
                        val totalPrice = transactionPrice * matchedQuantity
                        
                        // 세금 계산
                        val tax = totalPrice * taxRate
                        val sellerReceives = totalPrice - tax
                        
                        try {
                            // 1. 구매자에게 아이템 지급
                            inventoryService.addItems(buyerUUID, itemId, matchedQuantity)
                            
                            // 2. 판매자에게 돈 지급 (세금 제외)
                            economyService.deposit(playerUUID, sellerReceives)
                            
                            // 3. 구매 주문 업데이트
                            updatedBuyOrder.quantityFilled += matchedQuantity
                            if (updatedBuyOrder.quantityFilled >= updatedBuyOrder.quantityOrdered) {
                                updatedBuyOrder.status = OrderStatus.FILLED
                            } else {
                                updatedBuyOrder.status = OrderStatus.PARTIALLY_FILLED
                            }
                            val updateSuccess = orderRepository.updateBuyOrder(updatedBuyOrder)
                            
                            if (!updateSuccess) {
                                // 구매 주문 업데이트 실패시 롤백
                                economyService.withdraw(playerUUID, sellerReceives) // 안전을 위해 try-catch로 감싸도 좋음
                                inventoryService.removeItems(buyerUUID, itemId, matchedQuantity)
                                return@withLock null // 이 주문은 건너뜀
                            }
                            
                            // 4. 거래 내역 저장
                            val transaction = Transaction(
                                buyerUUID = buyerUUID,
                                sellerUUID = playerUUID,
                                itemId = itemId,
                                quantity = matchedQuantity,
                                pricePerUnit = transactionPrice
                            )
                            
                            val txSuccess = transactionRepository.saveTransaction(transaction)
                            if (!txSuccess) {
                                // 거래 저장 실패시에도 계속 진행 (로그만 남김)
                                println("거래 내역 저장 실패: $transaction")
                                // 여기서는 롤백하지 않음 - 실제 거래는 완료됨
                            } else {
                                transactions.add(transaction)
                            }
                            
                            // 판매자 총 수익 누적
                            totalEarned += sellerReceives
                            
                            // 남은 수량 갱신
                            remainingQuantity -= matchedQuantity
                            if (remainingQuantity <= 0) return@withLock true // 모든 수량 매칭 완료
                        } catch (e: Exception) {
                            println("주문 매칭 중 오류 발생: ${e.message}")
                            // 이 매칭은 건너뛰고 다음 주문 시도
                            return@withLock null
                        }
                        return@withLock true // 매칭 성공
                    }
                }
                
                // 남은 수량이 있으면 판매 제안으로 등록
                if (remainingQuantity > 0) {
                    val newOffer = SellOffer(
                        playerUUID = playerUUID,
                        itemId = itemId,
                        quantityOffered = remainingQuantity,
                        pricePerUnit = pricePerUnit
                    )
                    
                    val success = orderRepository.saveSellOffer(newOffer)
                    
                    if (success) {
                        val message = if (quantity > remainingQuantity) {
                            "${quantity - remainingQuantity}개는 즉시 판매되었고, ${remainingQuantity}개는 판매 제안으로 등록되었습니다. 총 수익: ${totalEarned}원"
                        } else {
                            "판매 제안이 등록되었습니다."
                        }
                        return@withLock PlaceOrderResult(true, message, newOffer.id)
                    } else {
                        // 저장 실패 시 아이템 반환 필요
                        inventoryService.addItems(playerUUID, itemId, remainingQuantity)
                        return@withLock PlaceOrderResult(false, "일부 수량만 매칭되었고, 나머지 제안 저장에 실패했습니다.")
                    }
                }
                
                // 모든 아이템이 매칭된 경우
                return@withLock PlaceOrderResult(true, "모든 수량(${quantity}개)이 즉시 판매되었습니다. 총 수익: ${totalEarned}원", null)
            } catch (e: Exception) {
                // 오류 발생 시 롤백
                println("Sell Offer 생성 중 오류 발생: ${e.message}")
                
                // 이미 제거된 아이템이 있으면 반환
                if (itemsRemoved) {
                    try {
                        // 이미 판매된 수량을 제외하고 반환
                        val quantityToReturn = quantity - (quantity - remainingQuantity)
                        if (quantityToReturn > 0) {
                            inventoryService.addItems(playerUUID, itemId, quantityToReturn)
                        }
                    } catch (e2: Exception) {
                        println("아이템 반환 중 오류 발생: ${e2.message}")
                    }
                }
                
                return@withLock PlaceOrderResult(false, "판매 제안 처리 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    override suspend fun executeInstantBuy(playerUUID: String, itemId: String, maxQuantity: Int): TransactionResult {
        // 입력값 검증
        if (maxQuantity <= 0) {
            return TransactionResult(false, "구매 수량은 0보다 커야 합니다.")
        }

        // 아이템에 대한 mutex 획득
        val itemMutex = getMutexForItem(itemId)

        return itemMutex.withLock {
            println("Instant Buy 로직 실행 (아이템 ID: $itemId)")

            // 1. 가장 낮은 가격의 Sell Offer 찾기
            val activeSellOffers = orderRepository.findActiveSellOffersForItem(itemId)
            if (activeSellOffers.isEmpty()) {
                return@withLock TransactionResult(false, "구매 가능한 판매 제안이 없습니다.")
            }

            // 2. 필요한 총 금액 계산 및 구매 가능한 최대 수량 결정
            var remainingQuantity = maxQuantity
            var totalCost = 0.0
            var potentialPurchases = mutableListOf<Pair<SellOffer, Int>>() // (제안, 구매수량) 쌍

            for (offer in activeSellOffers) {
                if (remainingQuantity <= 0) break

                val availableToBuy = offer.quantityOffered - offer.quantitySold
                val quantityToBuy = minOf(remainingQuantity, availableToBuy)
                val cost = quantityToBuy * offer.pricePerUnit

                potentialPurchases.add(Pair(offer, quantityToBuy))
                totalCost += cost
                remainingQuantity -= quantityToBuy
            }

            val actualQuantity = maxQuantity - remainingQuantity
            if (actualQuantity <= 0) {
                return@withLock TransactionResult(false, "구매 가능한 상품이 없습니다.")
            }

            // 3. 플레이어 재화 확인
            if (!economyService.hasEnoughFunds(playerUUID, totalCost)) {
                return@withLock TransactionResult(false, "충분한 재화가 없습니다. 필요: ${totalCost}원")
            }

            var fundsWithdrawn = false
            var transactions = mutableListOf<Transaction>()
            var successfulPurchases = mutableListOf<Triple<UUID, String, Int>>() // (판매자ID, 아이템ID, 수량) 튜플
            var totalItemsBought = 0
            var totalPaid = 0.0

            try {
                // 4. 구매자 재화 차감
                economyService.withdraw(playerUUID, totalCost)
                fundsWithdrawn = true

                // 5. 실제 거래 처리
                for ((offer, quantityToBuy) in potentialPurchases) {
                    // 판매 제안에 대한 mutex 획득
                    val offerMutex = getMutexForOrder(offer.id)

                    val purchaseSuccess = offerMutex.withLock {
                        // 판매 제안 최신 상태 확인 (동시성 문제 방지)
                        val updatedOffer = orderRepository.findSellOfferById(offer.id)
                        if (updatedOffer == null ||
                            updatedOffer.status != OrderStatus.ACTIVE &&
                            updatedOffer.status != OrderStatus.PARTIALLY_FILLED) {
                            return@withLock null // 이 제안은 건너뜀
                        }

                        val sellerUUID = updatedOffer.playerUUID
                        val availableToBuy = updatedOffer.quantityOffered - updatedOffer.quantitySold
                        val finalQuantityToBuy = minOf(quantityToBuy, availableToBuy)

                        if (finalQuantityToBuy <= 0) return@withLock null // 이 제안은 건너뜀

                        val transactionPrice = updatedOffer.pricePerUnit
                        val subtotal = finalQuantityToBuy * transactionPrice

                        // 세금 계산
                        val tax = subtotal * taxRate
                        val sellerReceives = subtotal - tax

                        try {
                            // 판매자에게 재화 지급
                            economyService.deposit(sellerUUID, sellerReceives)

                            // 구매자에게 아이템 지급
                            inventoryService.addItems(playerUUID, itemId, finalQuantityToBuy)

                            // 판매 제안 상태 업데이트
                            updatedOffer.quantitySold += finalQuantityToBuy
                            if (updatedOffer.quantitySold >= updatedOffer.quantityOffered) {
                                updatedOffer.status = OrderStatus.SOLD
                            } else {
                                updatedOffer.status = OrderStatus.PARTIALLY_FILLED
                            }

                            val updateSuccess = orderRepository.updateSellOffer(updatedOffer)
                            if (!updateSuccess) {
                                // 업데이트 실패 시 롤백
                                economyService.withdraw(sellerUUID, sellerReceives)
                                inventoryService.removeItems(playerUUID, itemId, finalQuantityToBuy)
                                return@withLock null // 이 제안은 건너뜀
                            }

                            // 거래 기록
                            val transaction = Transaction(
                                buyerUUID = playerUUID,
                                sellerUUID = sellerUUID,
                                itemId = itemId,
                                quantity = finalQuantityToBuy,
                                pricePerUnit = transactionPrice
                            )

                            val txSuccess = transactionRepository.saveTransaction(transaction)
                            if (!txSuccess) {
                                // 거래 저장 실패시에도 계속 진행 (로그만 남김)
                                println("거래 내역 저장 실패: $transaction")
                            } else {
                                transactions.add(transaction)
                            }

                            // 성공적인 구매 기록
                            successfulPurchases.add(Triple(UUID.fromString(sellerUUID), itemId, finalQuantityToBuy))
                            totalItemsBought += finalQuantityToBuy
                            totalPaid += subtotal

                            return@withLock finalQuantityToBuy
                        } catch (e: Exception) {
                            println("거래 처리 중 오류 발생: ${e.message}")
                            return@withLock null
                        }
                    }

                    if (purchaseSuccess == null || purchaseSuccess == 0) {
                        // 이 제안은 건너뜀
                        continue
                    }
                }

                // 구매 결과 반환
                if (totalItemsBought == 0) {
                    // 모든 구매 시도 실패 시 재화 환불
                    if (fundsWithdrawn) {
                        economyService.deposit(playerUUID, totalCost)
                    }
                    return@withLock TransactionResult(false, "구매 처리 중 오류가 발생했습니다.")
                } else if (totalItemsBought < actualQuantity) {
                    // 일부 구매 성공 시
                    val refundAmount = totalCost - totalPaid
                    if (refundAmount > 0) {
                        economyService.deposit(playerUUID, refundAmount)
                    }
                    return@withLock TransactionResult(
                        success = true,
                        message = "요청한 ${maxQuantity}개 중 ${totalItemsBought}개만 구매 성공했습니다. 총 비용: ${totalPaid}원",
                        transactionId = transactions.firstOrNull()?.id,
                        quantityTransacted = totalItemsBought,
                        averagePrice = if (totalItemsBought > 0) totalPaid / totalItemsBought else null
                    )
                } else {
                    // 모든 구매 성공
                    return@withLock TransactionResult(
                        success = true,
                        message = "${totalItemsBought}개 아이템을 총 ${totalPaid}원에 구매했습니다.",
                        transactionId = transactions.firstOrNull()?.id,
                        quantityTransacted = totalItemsBought,
                        averagePrice = if (totalItemsBought > 0) totalPaid / totalItemsBought else null
                    )
                }
            } catch (e: Exception) {
                // 오류 발생 시 롤백
                println("Instant Buy 처리 중 오류 발생: ${e.message}")

                // 이미 구매에 성공한 아이템 처리
                if (successfulPurchases.isNotEmpty()) {
                    for ((sellerUUID, itemId, quantity) in successfulPurchases) {
                        try {
                            // 이미 지급된 아이템 회수 및 판매자 재화 회수는 복잡할 수 있어 로그만 남김
                            println("거래 롤백 필요: 판매자 $sellerUUID, 아이템 $itemId, 수량 $quantity")
                        } catch (e2: Exception) {
                            println("롤백 실패: ${e2.message}")
                            // 롤백 실패해도 플레이어에게 메시지만 표시 (수동으로 처리 필요)
                            return@withLock TransactionResult(false, "구매 처리는 실패했지만 환불은 완료되었습니다. 관리자에게 문의하세요.")
                        }
                    }
                }

                // 모든 금액 환불
                if (fundsWithdrawn) {
                    try {
                        economyService.deposit(playerUUID, totalCost)
                    } catch (e2: Exception) {
                        println("재화 환불 중 오류 발생: ${e2.message}")
                    }
                }

                return@withLock TransactionResult(false, "구매 처리 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }


    override suspend fun executeInstantSell(playerUUID: String, itemId: String, quantity: Int): TransactionResult {
        // 입력값 검증
        if (quantity <= 0) {
            return TransactionResult(false, "판매 수량은 0보다 커야 합니다.")
        }

        // 아이템 보유 확인
        if (!inventoryService.hasItems(playerUUID, itemId, quantity)) {
            return TransactionResult(false, "충분한 아이템이 없습니다.")
        }

        // 아이템에 대한 mutex 획득
        val itemMutex = getMutexForItem(itemId)

        return itemMutex.withLock {
            println("Instant Sell 로직 실행 (아이템 ID: $itemId)")

            // 1. 가장 높은 가격의 Buy Order 찾기
            val activeBuyOrders = orderRepository.findActiveBuyOrdersForItem(itemId)
            if (activeBuyOrders.isEmpty()) {
                return@withLock TransactionResult(false, "구매 주문이 없어 즉시 판매할 수 없습니다.")
            }

            var itemsRemoved = false
            var remainingQuantity = quantity
            var transactions = mutableListOf<Transaction>()
            var successfulSales = mutableListOf<Triple<UUID, String, Int>>() // (구매자ID, 아이템ID, 수량) 튜플
            var totalEarned = 0.0

            try {
                // 2. 아이템 임시 제거
                val removeResult = inventoryService.removeItems(playerUUID, itemId, quantity)
                if (!removeResult) {
                    return@withLock TransactionResult(false, "아이템 제거 중 오류가 발생했습니다.")
                }
                itemsRemoved = true

                // 3. 실제 거래 처리
                for (buyOrder in activeBuyOrders) {
                    if (remainingQuantity <= 0) break

                    // 구매 주문에 대한 mutex 획득
                    val orderMutex = getMutexForOrder(buyOrder.id)

                    val saleSuccess = orderMutex.withLock {
                        // 구매 주문 최신 상태 확인 (동시성 문제 방지)
                        val updatedBuyOrder = orderRepository.findBuyOrderById(buyOrder.id)
                        if (updatedBuyOrder == null ||
                            updatedBuyOrder.status != OrderStatus.ACTIVE &&
                            updatedBuyOrder.status != OrderStatus.PARTIALLY_FILLED) {
                            return@withLock null // 이 주문은 건너뜀
                        }

                        val buyerUUID = updatedBuyOrder.playerUUID
                        val availableToBuy = updatedBuyOrder.quantityOrdered - updatedBuyOrder.quantityFilled
                        val matchedQuantity = minOf(remainingQuantity, availableToBuy)

                        if (matchedQuantity <= 0) return@withLock null // 이 주문은 건너뜀

                        val transactionPrice = updatedBuyOrder.pricePerUnit
                        val subtotal = matchedQuantity * transactionPrice

                        // 세금 계산
                        val tax = subtotal * taxRate
                        val sellerReceives = subtotal - tax

                        try {
                            // 구매자에게 아이템 지급
                            val addResult = inventoryService.addItems(buyerUUID, itemId, matchedQuantity)
                            if (!addResult) {
                                return@withLock null // 이 주문은 건너뜀
                            }

                            // 판매자에게 재화 지급
                            val depositResult = economyService.deposit(playerUUID, sellerReceives)
                            if (!depositResult) {
                                // 롤백: 지급된 아이템 회수
                                inventoryService.removeItems(buyerUUID, itemId, matchedQuantity)
                                return@withLock null // 이 주문은 건너뜀
                            }

                            // 구매 주문 상태 업데이트
                            updatedBuyOrder.quantityFilled += matchedQuantity
                            if (updatedBuyOrder.quantityFilled >= updatedBuyOrder.quantityOrdered) {
                                updatedBuyOrder.status = OrderStatus.FILLED
                            } else {
                                updatedBuyOrder.status = OrderStatus.PARTIALLY_FILLED
                            }

                            val updateSuccess = orderRepository.updateBuyOrder(updatedBuyOrder)
                            if (!updateSuccess) {
                                // 롤백: 지급된 아이템 및 재화 회수
                                inventoryService.removeItems(buyerUUID, itemId, matchedQuantity)
                                economyService.withdraw(playerUUID, sellerReceives)
                                return@withLock null // 이 주문은 건너뜀
                            }

                            // 거래 기록
                            val transaction = Transaction(
                                buyerUUID = buyerUUID,
                                sellerUUID = playerUUID,
                                itemId = itemId,
                                quantity = matchedQuantity,
                                pricePerUnit = transactionPrice
                            )

                            val txSuccess = transactionRepository.saveTransaction(transaction)
                            if (!txSuccess) {
                                // 거래 저장 실패시에도 계속 진행 (로그만 남김)
                                println("거래 내역 저장 실패: $transaction")
                            } else {
                                transactions.add(transaction)
                            }

                            // 성공적인 판매 기록
                            successfulSales.add(Triple(UUID.fromString(buyerUUID), itemId, matchedQuantity))
                            totalEarned += sellerReceives

                            return@withLock matchedQuantity
                        } catch (e: Exception) {
                            println("거래 처리 중 오류 발생: ${e.message}")
                            return@withLock null
                        }
                    }

                    if (saleSuccess == null || saleSuccess == 0) {
                        // 이 주문은 건너뜀
                        continue
                    }

                    remainingQuantity -= saleSuccess
                }

                // 판매 결과 반환
                if (remainingQuantity == quantity) {
                    // 모든 판매 시도 실패 시 아이템 반환
                    if (itemsRemoved) {
                        inventoryService.addItems(playerUUID, itemId, quantity)
                    }
                    return@withLock TransactionResult(false, "판매 처리 중 오류가 발생했습니다.")
                } else if (remainingQuantity > 0) {
                    // 일부 판매 성공 시 남은 아이템 반환
                    inventoryService.addItems(playerUUID, itemId, remainingQuantity)
                    val soldQuantity = quantity - remainingQuantity
                    return@withLock TransactionResult(
                        success = true,
                        message = "요청한 ${quantity}개 중 ${soldQuantity}개만 판매 성공했습니다. 총 수익: ${totalEarned}원",
                        transactionId = transactions.firstOrNull()?.id,
                        quantityTransacted = soldQuantity,
                        averagePrice = if (soldQuantity > 0) totalEarned / soldQuantity else null
                    )
                } else {
                    // 모든 판매 성공
                    return@withLock TransactionResult(
                        success = true,
                        message = "${quantity}개 아이템을 총 ${totalEarned}원에 판매했습니다.",
                        transactionId = transactions.firstOrNull()?.id,
                        quantityTransacted = quantity,
                        averagePrice = totalEarned / quantity
                    )
                }
            } catch (e: Exception) {
                // 오류 발생 시 롤백
                println("Instant Sell 처리 중 오류 발생: ${e.message}")

                // 이미 판매에 성공한 아이템 처리 (복잡하므로 로그만 남김)
                if (successfulSales.isNotEmpty()) {
                    for ((buyerUUID, itemId, soldQuantity) in successfulSales) {
                        println("거래 롤백 필요: 구매자 $buyerUUID, 아이템 $itemId, 수량 $soldQuantity")
                    }
                }

                // 남은 아이템 반환
                if (itemsRemoved && remainingQuantity > 0) {
                    try {
                        inventoryService.addItems(playerUUID, itemId, remainingQuantity)
                    } catch (e2: Exception) {
                        println("아이템 반환 중 오류 발생: ${e2.message}")
                    }
                }

                return@withLock TransactionResult(false, "판매 처리 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    override suspend fun cancelOrderOrOffer(playerUUID: String, orderId: String): PlaceOrderResult {
        // 주문/제안에 대한 mutex 획득
        val orderMutex = getMutexForOrder(orderId)

        return orderMutex.withLock {
            println("주문/제안 취소 로직 실행 (주문 ID: $orderId)")

            // 1. orderId로 BuyOrder 또는 SellOffer 찾기
            val buyOrder = orderRepository.findBuyOrderById(orderId)
            if (buyOrder != null) {
                // 구매 주문인 경우

                // 2. 주문이 해당 플레이어의 것인지 확인
                if (buyOrder.playerUUID != playerUUID) {
                    return@withLock PlaceOrderResult(false, "본인의 주문만 취소할 수 있습니다.")
                }

                // 3. 주문 상태 확인
                if (buyOrder.status != OrderStatus.ACTIVE && buyOrder.status != OrderStatus.PARTIALLY_FILLED) {
                    return@withLock PlaceOrderResult(false, "이미 완료되었거나 취소된 주문입니다.")
                }

                try {
                    // 4. 남은 금액 계산 및 환불
                    val remainingQuantity = buyOrder.quantityOrdered - buyOrder.quantityFilled
                    val refundAmount = remainingQuantity * buyOrder.pricePerUnit

                    if (refundAmount > 0) {
                        val depositResult = economyService.deposit(playerUUID, refundAmount)
                        if (!depositResult) {
                            return@withLock PlaceOrderResult(false, "환불 처리 중 오류가 발생했습니다.")
                        }
                    }

                    // 5. 주문 상태 취소로 변경
                    buyOrder.status = OrderStatus.CANCELLED
                    val updateResult = orderRepository.updateBuyOrder(buyOrder)
                    if (!updateResult) {
                        // 환불했는데 취소 처리 실패 시 롤백 시도
                        if (refundAmount > 0) {
                            try {
                                economyService.withdraw(playerUUID, refundAmount)
                            } catch (e: Exception) {
                                println("롤백 실패: ${e.message}")
                                // 롤백 실패해도 플레이어에게 메시지만 표시 (수동으로 처리 필요)
                                return@withLock PlaceOrderResult(false, "취소 처리는 실패했지만 환불은 완료되었습니다. 관리자에게 문의하세요.")
                            }
                        }
                        return@withLock PlaceOrderResult(false, "주문 취소 중 오류가 발생했습니다.")
                    }

                    return@withLock PlaceOrderResult(true, "구매 주문이 취소되었습니다. 환불 금액: ${refundAmount}원")
                } catch (e: Exception) {
                    println("구매 주문 취소 중 오류 발생: ${e.message}")
                    return@withLock PlaceOrderResult(false, "주문 취소 중 오류가 발생했습니다: ${e.message}")
                }
            } else {
                // 판매 제안인 경우
                val sellOffer = orderRepository.findSellOfferById(orderId)
                if (sellOffer == null) {
                    return@withLock PlaceOrderResult(false, "해당 주문 또는 제안을 찾을 수 없습니다.")
                }

                // 2. 제안이 해당 플레이어의 것인지 확인
                if (sellOffer.playerUUID != playerUUID) {
                    return@withLock PlaceOrderResult(false, "본인의 제안만 취소할 수 있습니다.")
                }

                // 3. 제안 상태 확인
                if (sellOffer.status != OrderStatus.ACTIVE && sellOffer.status != OrderStatus.PARTIALLY_FILLED) {
                    return@withLock PlaceOrderResult(false, "이미 완료되었거나 취소된 제안입니다.")
                }

                try {
                    // 4. 남은 아이템 계산 및 반환
                    val remainingQuantity = sellOffer.quantityOffered - sellOffer.quantitySold

                    if (remainingQuantity > 0) {
                        val addResult = inventoryService.addItems(playerUUID, sellOffer.itemId, remainingQuantity)
                        if (!addResult) {
                            return@withLock PlaceOrderResult(false, "아이템 반환 중 오류가 발생했습니다.")
                        }
                    }

                    // 5. 제안 상태 취소로 변경
                    sellOffer.status = OrderStatus.CANCELLED
                    val updateResult = orderRepository.updateSellOffer(sellOffer)
                    if (!updateResult) {
                        // 아이템 반환했는데 취소 처리 실패 시 롤백 시도
                        if (remainingQuantity > 0) {
                            try {
                                inventoryService.removeItems(playerUUID, sellOffer.itemId, remainingQuantity)
                            } catch (e: Exception) {
                                println("롤백 실패: ${e.message}")
                                // 롤백 실패해도 플레이어에게 메시지만 표시 (수동으로 처리 필요)
                                return@withLock PlaceOrderResult(false, "취소 처리는 실패했지만 아이템은 반환되었습니다. 관리자에게 문의하세요.")
                            }
                        }
                        return@withLock PlaceOrderResult(false, "제안 취소 중 오류가 발생했습니다.")
                    }

                    return@withLock PlaceOrderResult(true, "판매 제안이 취소되었습니다. 반환 아이템: ${sellOffer.itemId} ${remainingQuantity}개")
                } catch (e: Exception) {
                    println("판매 제안 취소 중 오류 발생: ${e.message}")
                    return@withLock PlaceOrderResult(false, "제안 취소 중 오류가 발생했습니다: ${e.message}")
                }
            }
        }
    }
}