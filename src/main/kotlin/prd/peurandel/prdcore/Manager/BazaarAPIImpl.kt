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
import org.bukkit.Bukkit
import org.bukkit.entity.Player

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
    val material: String,
    val glass_color: String,
    val parentCategoryId: String? = null // 최상위 카테고리는 null
)
@Serializable
data class ProductGroupProducts(
    val productId: String,
    val slot: Int
)

@Serializable
data class ProductGroup(
    @SerialName("_id")
    val _id: String, // 예: "DIAMOND", "WHEAT"
    val name: String, // 예: "다이아몬드", "밀"
    val categoryId: String, // 소속 카테고리 ID
    val material: String, // GUI 표시용 Material (예: "DIAMOND", "WHEAT")
    val row: Int,
    val products: List<ProductGroupProducts>,
    val description: String? = null,
    val displayOrder: Int = 0 // GUI에서 표시 순서
)

@Serializable
data class Product(
    @SerialName("_id")
    val _id: String, // 예: "DIAMOND", "DIAMOND_BLOCK"
    val name: String, // 예: "다이아몬드", "다이아몬드 블록"
    val productGroupId: String, // ProductGroup ID (예: "DIAMOND")
    val categoryId: String, // 빠른 조회용 (중복이지만 성능 향상)
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
    val buyerUUID: String,
    val sellerUUID: String,
    val itemId: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val timestampCompleted: Instant = Instant.now(),
    val buyOrderId: String? = null, // 연관된 주문 ID (선택적)
    val sellOfferId: String? = null // 연관된 제안 ID (선택적)
)

// --- DTOs (Data Transfer Objects - API가 외부에 노출하는 데이터 형태) ---

data class CategoryInfo(
    val id: String,
    val name: String,
    val material: String,
    val glass_color: String,
)
data class ProductGroupInfo(
    val id: String,
    val name: String,
    val categoryId: String,
    val material: String,
    val row: Int,
    val products: List<ProductGroupProducts>,
    val description: String?
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
    val quantityTransacted: Int = 0,
    val averagePrice: Double = 0.0,
    val errorType: TransactionErrorType? = null,
    val availableQuantity: Int = 0,
    val requestedQuantity: Int = 0,
    val partiallyFulfilled: Boolean = false,
    val remainingFunds: Double = 0.0,
    val totalCost: Double = 0.0
)

enum class TransactionErrorType {
    INSUFFICIENT_FUNDS,
    INSUFFICIENT_INVENTORY,
    NO_ACTIVE_ORDERS,
    INVALID_QUANTITY,
    PRICE_CHANGED,
    DUPLICATE_TRANSACTION,
    PARTIAL_FULFILLMENT,
    SYSTEM_ERROR
}

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
    suspend fun findAll(): List<Category>
    // ... 기타 필요한 메소드 (save, update 등)
}

interface ProductGroupRepository {
    suspend fun findByCategory(categoryId: String): List<ProductGroup>
    suspend fun findById(id: String): ProductGroup?
    suspend fun findAll(): List<ProductGroup>
    // ... 기타 필요한 메소드 (save, update 등)
}

interface ProductRepository {
    suspend fun findByCategory(categoryId: String): List<Product>
    suspend fun findByProductGroup(productGroupId: String): List<Product>
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
    suspend fun getProductGroupsByCategory(categoryId: String): List<ProductGroupInfo>
    suspend fun getProductsByCategory(categoryId: String): List<ProductInfo>
    suspend fun getProductsByProductGroup(productGroupId: String): List<ProductInfo>
    suspend fun searchProductsByName(query: String): List<ProductInfo>
    suspend fun getProductDetailsAndOrderBook(itemId: String): OrderBookSnapshot?
    suspend fun getActiveBuyOrders(itemId: String): List<BuyOrder>
    suspend fun getActiveSellOffers(itemId: String): List<SellOffer>
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
    private val productGroupRepository: ProductGroupRepository,
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
        return categoryRepository.findRootCategories().map { CategoryInfo(it.id, it.name,it.material, it.glass_color) }
    }

    override suspend fun getProductGroupsByCategory(categoryId: String): List<ProductGroupInfo> {
        return productGroupRepository.findByCategory(categoryId).map { ProductGroupInfo(it._id, it.name, it.categoryId,it.material, it.row,it.products,it.description) }
    }


    override suspend fun getProductsByCategory(categoryId: String): List<ProductInfo> {
        // 카테고리 존재 여부 확인 등 추가 로직 가능
        return productRepository.findByCategory(categoryId)
            .filter { it.isTradable } // 거래 가능한 상품만 필터링
            .map { ProductInfo(it._id, it.name, it.description) }
    }

    override suspend fun getProductsByProductGroup(productGroupId: String): List<ProductInfo> {
        return productRepository.findByProductGroup(productGroupId)
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

    override suspend fun getProductDetailsAndOrderBook(itemId: String): OrderBookSnapshot? {
        val product = productRepository.findById(itemId) ?: return null // 상품 없으면 null 반환
        if (!product.isTradable) return null // 거래 불가능 상품이면 null 반환

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
            return@coroutineScope snapshot
        }
        // 이곳에 도달하면 안 됨 (coroutineScope가 결과를 반환하므로)
        return null
    }

    override suspend fun getActiveBuyOrders(itemId: String): List<BuyOrder> {
        return orderRepository.findActiveBuyOrdersForItem(itemId)
    }

    override suspend fun getActiveSellOffers(itemId: String): List<SellOffer> {
        return orderRepository.findActiveSellOffersForItem(itemId)
    }

    // --- 주문 생성/취소 관련 API ---

    override suspend fun placeBuyOrder(playerUUID: String, itemId: String, quantity: Int, pricePerUnit: Double): PlaceOrderResult {
        Bukkit.getLogger().info("[BUY_ORDER] 구매 제안 생성 시작 - 플레이어: $playerUUID, 아이템: $itemId, 수량: $quantity, 단가: $pricePerUnit")
        
        // 1. 총 필요 금액 계산
        val totalCost = quantity * pricePerUnit
        Bukkit.getLogger().info("[BUY_ORDER] 총 필요 금액: $totalCost")
        
        // 2. 플레이어 자금 확인
        if (!economyService.hasEnoughFunds(playerUUID, totalCost)) {
            Bukkit.getLogger().warning("[BUY_ORDER] 자금 부족 - 필요: $totalCost")
            return PlaceOrderResult(false, "소지금이 부족합니다. 필요 금액: ${totalCost}원")
        }
        
        // 3. 구매 제안 생성 시 즉시 돈 차감 (escrow 개념)
        val withdrawResult = economyService.withdraw(playerUUID, totalCost)
        if (!withdrawResult) {
            Bukkit.getLogger().warning("[BUY_ORDER] 돈 차감 실패 - 금액: $totalCost")
            return PlaceOrderResult(false, "돈 차감에 실패했습니다.")
        }
        Bukkit.getLogger().info("[BUY_ORDER] 돈 차감 완료 - 금액: $totalCost")
        
        // 4. 주문 생성 및 저장
        val newOrder = BuyOrder(
            playerUUID = playerUUID, 
            itemId = itemId, 
            quantityOrdered = quantity, 
            pricePerUnit = pricePerUnit
        )
        val success = orderRepository.saveBuyOrder(newOrder)
        
        if (success) {
            Bukkit.getLogger().info("[BUY_ORDER] 구매 제안 생성 성공 - ID: ${newOrder.id}")
            return PlaceOrderResult(true, "구매 제안이 생성되었습니다. (총 ${totalCost}원 차감)", newOrder.id)
        } else {
            // 5. 주문 저장 실패 시 돈 환불
            economyService.deposit(playerUUID, totalCost)
            Bukkit.getLogger().warning("[BUY_ORDER] 주문 저장 실패 - 돈 환불 완료: $totalCost")
            return PlaceOrderResult(false, "주문 저장에 실패했습니다. (돈이 환불되었습니다)")
        }
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
        Bukkit.getLogger().info("=== BazaarAPIImpl.instantBuy 시작 ===")
        Bukkit.getLogger().info("플레이어: $playerUUID")
        Bukkit.getLogger().info("아이템: $itemId") 
        Bukkit.getLogger().info("최대수량: $maxQuantity")

        // 1. 입력값 유효성 검사
        if (maxQuantity <= 0) {
            return TransactionResult(false, "구매 수량은 0보다 커야 합니다.")
        }
        val product = productRepository.findById(itemId)
        if (product == null || !product.isTradable) {
            return TransactionResult(false, "거래할 수 없는 상품입니다.")
        }

        Bukkit.getLogger().info("BazaarCoreService.executeInstantBuy 호출")
        // 2. 핵심 서비스 호출 (매칭, 재화 확인, 거래 처리, 아이템 지급)
        return bazaarCoreService.executeInstantBuy(playerUUID, itemId, maxQuantity)
    }

    override suspend fun instantSell(playerUUID: String, itemId: String, quantity: Int): TransactionResult {
        Bukkit.getLogger().info("=== BazaarAPIImpl.instantSell 시작 ===")
        Bukkit.getLogger().info("플레이어: $playerUUID")
        Bukkit.getLogger().info("아이템: $itemId") 
        Bukkit.getLogger().info("수량: $quantity")

        // 입력값 검증
        if (quantity <= 0) {
            return TransactionResult(
                success = false,
                message = "판매 수량은 0보다 커야 합니다.",
                errorType = TransactionErrorType.INVALID_QUANTITY,
                requestedQuantity = quantity
            )
        }
        val product = productRepository.findById(itemId)
        if (product == null || !product.isTradable) {
            return TransactionResult(false, "거래할 수 없는 상품입니다.")
        }

        // 2. 플레이어 인벤토리 확인
        if (!inventoryService.hasItems(playerUUID, itemId, quantity)) {
            return TransactionResult(
                success = false,
                message = "충분한 아이템이 없습니다. 인벤토리를 확인해주세요.",
                errorType = TransactionErrorType.INSUFFICIENT_INVENTORY,
                requestedQuantity = quantity
            )
        }

        Bukkit.getLogger().info("BazaarCoreService.executeInstantSell 호출")
        // 3. 핵심 서비스 호출 (매칭, 아이템 제거, 거래 처리, 재화 지급)
        // 판매 전 아이템을 먼저 제거하거나 '에스크로' 계정으로 옮기는 것이 안전함
        return bazaarCoreService.executeInstantSell(playerUUID, itemId, quantity)
    }

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
        Bukkit.getLogger().info("Buy Order Matching 로직 실행 (Dummy)")

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
            Bukkit.getLogger().info("Sell Offer Matching 로직 실행 (아이템 ID: $itemId")

            // 아이템 소유 확인
            val hasItems = inventoryService.hasItems(playerUUID, itemId, quantity)
            if (!hasItems) {
                return@withLock PlaceOrderResult(false, "충분한 아이템이 없습니다")
            }

            var remainingQuantity = quantity
            var transactions = mutableListOf<Transaction>()
            var successfulSales = mutableListOf<Triple<String, String, Int>>() // (구매자ID, 아이템ID, 수량) 튜플
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
                            (updatedBuyOrder.status != OrderStatus.ACTIVE &&
                            updatedBuyOrder.status != OrderStatus.PARTIALLY_FILLED)) {
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
                                return@withLock null
                            }

                            // 4. 거래 내역 저장
                            val transaction = Transaction(
                                id = UUID.randomUUID().toString(),
                                buyerUUID = buyerUUID,
                                sellerUUID = playerUUID,
                                itemId = itemId,
                                quantity = matchedQuantity,
                                pricePerUnit = transactionPrice,
                                timestampCompleted = Instant.now(),
                                buyOrderId = updatedBuyOrder.id,
                                sellOfferId = null // 즉시 판매는 기존 제안이 없음
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
        Bukkit.getLogger().info("=== DummyBazaarCoreServiceImpl.executeInstantBuy 시작 ===")
        Bukkit.getLogger().info("플레이어: $playerUUID")
        Bukkit.getLogger().info("아이템: $itemId")
        Bukkit.getLogger().info("최대수량: $maxQuantity")
        
        // 입력값 검증
        if (maxQuantity <= 0) {
            return TransactionResult(
                success = false,
                message = "구매 수량은 0보다 커야 합니다.",
                errorType = TransactionErrorType.INVALID_QUANTITY,
                requestedQuantity = maxQuantity
            )
        }

        // 아이템에 대한 mutex 획득
        val itemMutex = getMutexForItem(itemId)
        Bukkit.getLogger().info("§7아이템 mutex 획득: $itemId")

        return itemMutex.withLock {

            try {
                // 1. 가장 낮은 가격의 Sell Offer 찾기
                Bukkit.getLogger().info("§71단계: 활성 판매 제안 조회 중...")
                val activeSellOffers = orderRepository.findActiveSellOffersForItem(itemId)
                Bukkit.getLogger().info("§7조회된 판매 제안 수: ${activeSellOffers.size}")
                
                if (activeSellOffers.isEmpty()) {
                    Bukkit.getLogger().info("§c실패: 판매 제안이 없음")
                    return@withLock TransactionResult(
                        success = false,
                        message = "현재 판매 중인 상품이 없습니다. 나중에 다시 시도해주세요.",
                        errorType = TransactionErrorType.NO_ACTIVE_ORDERS,
                        requestedQuantity = maxQuantity,
                        availableQuantity = 0
                    )
                }

                // 2. 필요한 총 금액 계산 및 구매 가능한 최대 수량 결정
                Bukkit.getLogger().info("§72단계: 구매 계획 수립 중...")
                var totalCost = 0.0
                var potentialPurchases = mutableListOf<Pair<SellOffer, Int>>() // (제안, 구매수량) 쌍
                var totalAvailableQuantity = 0
                var remainingQuantity = maxQuantity

                for (offer in activeSellOffers) {
                    if (remainingQuantity <= 0) break
                    
                    val availableToBuy = offer.quantityOffered - offer.quantitySold
                    totalAvailableQuantity += availableToBuy
                    Bukkit.getLogger().info("§7제안 ${offer.id}: 가격 ${offer.pricePerUnit}, 구매가능 ${availableToBuy}개")
                    
                    val quantityToBuy = minOf(remainingQuantity, availableToBuy)
                    val cost = quantityToBuy * offer.pricePerUnit

                    potentialPurchases.add(Pair(offer, quantityToBuy))
                    totalCost += cost
                    remainingQuantity -= quantityToBuy
                }

                val actualQuantity = maxQuantity - remainingQuantity
                Bukkit.getLogger().info("§7구매 계산 완료: 실제수량 ${actualQuantity}개, 총비용 ${totalCost}원")
                
                if (actualQuantity <= 0) {
                    Bukkit.getLogger().info("§c실패: 구매 가능한 수량이 0")
                    return@withLock TransactionResult(
                        success = false,
                        message = "구매 가능한 상품이 없습니다.",
                        errorType = TransactionErrorType.NO_ACTIVE_ORDERS,
                        requestedQuantity = maxQuantity,
                        availableQuantity = totalAvailableQuantity
                    )
                }

                var fundsWithdrawn = false
                var transactions = mutableListOf<Transaction>()
                var totalItemsBought = 0
                var totalPaid = 0.0

                try {
                    // 3. 구매자 재화 차감
                    Bukkit.getLogger().info("§74단계: 구매자 재화 차감 중... (${totalCost}원)")
                    val withdrawSuccess = economyService.withdraw(playerUUID, totalCost)
                    if (!withdrawSuccess) {
                        Bukkit.getLogger().info("§c실패: 구매자 재화 차감 실패")
                        return@withLock TransactionResult(
                            success = false,
                            message = "결제 처리에 실패했습니다. 잠시 후 다시 시도해주세요.",
                            errorType = TransactionErrorType.SYSTEM_ERROR,
                            requestedQuantity = maxQuantity,
                            availableQuantity = actualQuantity,
                            totalCost = totalCost,
                            remainingFunds = 0.0
                        )
                    }
                    fundsWithdrawn = true
                    Bukkit.getLogger().info("§a성공: 재화 차감 완료")

                    // 4. 실제 거래 처리
                    Bukkit.getLogger().info("§75단계: 실제 거래 처리 시작 (${potentialPurchases.size}개 제안 처리)")
                    for ((offer, quantityToBuy) in potentialPurchases) {
                        // 판매 제안에 대한 mutex 획득
                        val offerMutex = getMutexForOrder(offer.id)

                        val purchaseResult = offerMutex.withLock {
                            Bukkit.getLogger().info("§7제안 ${offer.id} 처리 시작 (예상 구매량: ${quantityToBuy}개)")
                            
                            // 판매 제안 최신 상태 확인 (동시성 문제 방지)
                            val updatedOffer = orderRepository.findSellOfferById(offer.id)
                            if (updatedOffer == null ||
                                (updatedOffer.status != OrderStatus.ACTIVE &&
                                updatedOffer.status != OrderStatus.PARTIALLY_FILLED)) {
                                Bukkit.getLogger().info("§c제안 ${offer.id} 건너뜀: 상태 변경됨 - 다른 활성 제안 재조회")
                                
                                // 다른 활성 제안을 찾아서 거래 시도
                                val freshActiveSellOffers = orderRepository.findActiveSellOffersForItem(itemId)
                                    .filter { it.id != offer.id } // 현재 실패한 제안 제외
                                    .sortedBy { it.pricePerUnit } // 가격 순 정렬
                                
                                if (freshActiveSellOffers.isNotEmpty()) {
                                    val alternativeOffer = freshActiveSellOffers.first()
                                    val alternativeAvailable = alternativeOffer.quantityOffered - alternativeOffer.quantitySold
                                    if (alternativeAvailable > 0) {
                                        Bukkit.getLogger().info("§a대체 제안 발견: ${alternativeOffer.id} (가격: ${alternativeOffer.pricePerUnit}, 수량: ${alternativeAvailable})")
                                        
                                        // 대체 제안으로 거래 시도
                                        val altQuantityToBuy = minOf(quantityToBuy, alternativeAvailable)
                                        val altTransactionPrice = alternativeOffer.pricePerUnit
                                        val altSubtotal = altQuantityToBuy * altTransactionPrice
                                        val altTax = altSubtotal * taxRate
                                        val altSellerReceives = altSubtotal - altTax
                                        
                                        try {
                                            Bukkit.getLogger().info("§7대체 제안 ${alternativeOffer.id}에서 거래 시도 중... (${altQuantityToBuy}개, ${altSubtotal}원)")
                                            
                                            // 구매자에게 아이템 지급
                                            val addResult = inventoryService.addItems(playerUUID, itemId, altQuantityToBuy)
                                            if (!addResult) {
                                                Bukkit.getLogger().info("§c실패: 아이템 지급 실패")
                                                return@withLock null
                                            }
                                            
                                            // 판매자에게 돈 지급
                                            val depositResult = economyService.deposit(alternativeOffer.playerUUID, altSellerReceives)
                                            if (!depositResult) {
                                                Bukkit.getLogger().info("§c실패: 판매자에게 돈 지급 실패 - 아이템 롤백")
                                                inventoryService.removeItems(playerUUID, itemId, altQuantityToBuy)
                                                return@withLock null
                                            }
                                            
                                            // 판매 제안 상태 업데이트
                                            val updatedAltOffer = alternativeOffer.copy(
                                                quantitySold = alternativeOffer.quantitySold + altQuantityToBuy,
                                                status = if (alternativeOffer.quantitySold + altQuantityToBuy >= alternativeOffer.quantityOffered)
                                                    OrderStatus.SOLD else OrderStatus.PARTIALLY_FILLED
                                            )
                                            
                                            val updateSuccess = orderRepository.updateSellOffer(updatedAltOffer)
                                            if (!updateSuccess) {
                                                Bukkit.getLogger().info("§c실패: 데이터베이스 업데이트 실패 - 전체 롤백")
                                                economyService.withdraw(alternativeOffer.playerUUID, altSellerReceives)
                                                inventoryService.removeItems(playerUUID, itemId, altQuantityToBuy)
                                                return@withLock null
                                            }
                                            
                                            // 거래 기록
                                            val transaction = Transaction(
                                                id = UUID.randomUUID().toString(),
                                                buyerUUID = playerUUID,
                                                sellerUUID = alternativeOffer.playerUUID,
                                                itemId = itemId,
                                                quantity = altQuantityToBuy,
                                                pricePerUnit = altTransactionPrice,
                                                timestampCompleted = Instant.now(),
                                                buyOrderId = null,
                                                sellOfferId = alternativeOffer.id
                                            )
                                            
                                            val saveResult = transactionRepository.saveTransaction(transaction)
                                            if (!saveResult) {
                                                Bukkit.getLogger().info("§e거래 기록 저장 실패 (거래는 완료됨)")
                                            } else {
                                                transactions.add(transaction)
                                            }
                                            
                                            Bukkit.getLogger().info("§a성공: 대체 제안으로 거래 완료 (${altQuantityToBuy}개, ${altSubtotal}원)")
                                            return@withLock Pair(altQuantityToBuy, altSubtotal)
                                            
                                        } catch (e: Exception) {
                                            Bukkit.getLogger().info("§c대체 제안 거래 중 예외 발생: ${e.message}")
                                            return@withLock null
                                        }
                                    }
                                }
                                
                                Bukkit.getLogger().info("§c사용 가능한 대체 제안 없음")
                                return@withLock null // 이 제안은 건너뜀
                            }
                            val buyerUUID = updatedOffer.playerUUID
                            val availableToBuy = updatedOffer.quantityOffered - updatedOffer.quantitySold

                            if (availableToBuy <= 0) return@withLock null // 이 제안은 건너뜀

                            val matchedQuantity = minOf(quantityToBuy, availableToBuy)
                            val transactionPrice = updatedOffer.pricePerUnit // 구매자 가격으로 거래
                            val totalPrice = transactionPrice * matchedQuantity

                            // 세금 계산
                            val tax = totalPrice * taxRate
                            val sellerReceives = totalPrice - tax

                            try {
                                // 1. 구매자에게 아이템 지급
                                val addResult = inventoryService.addItems(playerUUID, itemId, matchedQuantity)
                                if (!addResult) {
                                    Bukkit.getLogger().info("§c실패: 아이템 지급 실패")
                                    return@withLock null
                                }
                                
                                // 2. 판매자에게 돈 지급
                                val depositResult = economyService.deposit(updatedOffer.playerUUID, sellerReceives)
                                if (!depositResult) {
                                    Bukkit.getLogger().info("§c실패: 판매자에게 돈 지급 실패 - 아이템 롤백")
                                    inventoryService.removeItems(playerUUID, itemId, matchedQuantity)
                                    return@withLock null
                                }
                                
                                // 3. 판매 제안 상태 업데이트
                                updatedOffer.quantitySold += matchedQuantity
                                if (updatedOffer.quantitySold >= updatedOffer.quantityOffered) {
                                    updatedOffer.status = OrderStatus.SOLD
                                } else {
                                    updatedOffer.status = OrderStatus.PARTIALLY_FILLED
                                }

                                val updateSuccess = orderRepository.updateSellOffer(updatedOffer)

                                if (!updateSuccess) {
                                    // 판매 제안 업데이트 실패시 롤백
                                    economyService.withdraw(updatedOffer.playerUUID, sellerReceives)
                                    inventoryService.removeItems(playerUUID, itemId, matchedQuantity)
                                    return@withLock null
                                }

                                // 4. 거래 기록
                                val transaction = Transaction(
                                    id = UUID.randomUUID().toString(),
                                    buyerUUID = playerUUID,
                                    sellerUUID = updatedOffer.playerUUID,
                                    itemId = itemId,
                                    quantity = matchedQuantity,
                                    pricePerUnit = transactionPrice,
                                    timestampCompleted = Instant.now(),
                                    sellOfferId = updatedOffer.id
                                )

                                val transactionSaved = transactionRepository.saveTransaction(transaction)
                                if (!transactionSaved) {
                                    // 거래 저장 실패시에도 계속 진행 (로그만 남김)
                                    println("거래 내역 저장 실패: $transaction")
                                } else {
                                    transactions.add(transaction)
                                }

                                Bukkit.getLogger().info("§a성공: 제안 ${offer.id} 거래 완료 (${matchedQuantity}개, ${totalPrice}원)")
                                return@withLock Pair(matchedQuantity, totalPrice)

                            } catch (e: Exception) {
                                Bukkit.getLogger().info("§c제안 ${offer.id} 거래 중 예외 발생: ${e.message}")
                                return@withLock null
                            }
                        }

                        // 결과 처리
                        if (purchaseResult != null) {
                            val (itemsBought, amountPaid) = purchaseResult
                            totalItemsBought += itemsBought
                            totalPaid += amountPaid
                            Bukkit.getLogger().info("§a성공 누적: ${totalItemsBought}개, ${totalPaid}원")
                        } else {
                            Bukkit.getLogger().info("§c제안 ${offer.id}에서 거래 실패")
                        }
                    }

                    Bukkit.getLogger().info("§7=== 거래 처리 완료 ===")
                    Bukkit.getLogger().info("§7총 구매된 아이템: ${totalItemsBought}개")
                    Bukkit.getLogger().info("§7총 지불 금액: ${totalPaid}원")
                    Bukkit.getLogger().info("§7성공한 거래 수: ${transactions.size}개")

                    // 사용하지 않은 금액 반환
                    val unusedFunds = totalCost - totalPaid
                    if (unusedFunds > 0) {
                        Bukkit.getLogger().info("§7사용하지 않은 금액 반환: ${unusedFunds}원")
                        economyService.deposit(playerUUID, unusedFunds)
                    }

                    if (totalItemsBought > 0) {
                        Bukkit.getLogger().info("§a최종 성공: ${totalItemsBought}개 구매, ${totalPaid}원 지불")
                        
                        // 평균 가격 계산
                        val averagePrice = if (totalItemsBought > 0) totalPaid / totalItemsBought else 0.0
                        
                        return@withLock TransactionResult(
                            success = true,
                            message = "${totalItemsBought}개의 ${itemId}을(를) 총 ${String.format("%.2f", totalPaid)}원에 구매했습니다.",
                            quantityTransacted = totalItemsBought,
                            averagePrice = averagePrice,
                            requestedQuantity = maxQuantity,
                            partiallyFulfilled = totalItemsBought < maxQuantity,
                            totalCost = totalPaid
                        )
                    } else {
                        // 모든 거래가 실패한 경우 전액 환불
                        if (fundsWithdrawn) {
                            Bukkit.getLogger().info("§c모든 거래 실패 - 전액 환불: ${totalCost}원")
                            economyService.deposit(playerUUID, totalCost)
                        }
                        
                        Bukkit.getLogger().info("§c최종 실패: 모든 거래 실패")
                        return@withLock TransactionResult(
                            success = false,
                            message = "거래를 완료할 수 없습니다. 잠시 후 다시 시도해주세요.",
                            errorType = TransactionErrorType.SYSTEM_ERROR,
                            requestedQuantity = maxQuantity,
                            totalCost = 0.0
                        )
                    }

                } catch (e: Exception) {
                    Bukkit.getLogger().info("§c예외 발생: ${e.message}")
                    e.printStackTrace()
                    
                    // 예외 발생 시 환불
                    if (fundsWithdrawn) {
                        try {
                            val refundAmount = totalCost - totalPaid
                            if (refundAmount > 0) {
                                Bukkit.getLogger().info("§7예외 발생으로 인한 환불: ${refundAmount}원")
                                economyService.deposit(playerUUID, refundAmount)
                            }
                        } catch (refundException: Exception) {
                            Bukkit.getLogger().info("§c환불 중 예외 발생: ${refundException.message}")
                        }
                    }

                    return@withLock TransactionResult(
                        success = false,
                        message = "시스템 오류가 발생했습니다. 관리자에게 문의해주세요.",
                        errorType = TransactionErrorType.SYSTEM_ERROR,
                        requestedQuantity = maxQuantity,
                        totalCost = totalPaid
                    )
                }

            } catch (e: Exception) {
                Bukkit.getLogger().info("§c치명적 예외 발생: ${e.message}")
                e.printStackTrace()
                return@withLock TransactionResult(
                    success = false,
                    message = "시스템 오류가 발생했습니다.",
                    errorType = TransactionErrorType.SYSTEM_ERROR,
                    requestedQuantity = maxQuantity
                )
            }
        }
    }

    override suspend fun executeInstantSell(playerUUID: String, itemId: String, quantity: Int): TransactionResult {
        Bukkit.getLogger().info("=== DummyBazaarCoreServiceImpl.executeInstantSell 시작 ===")
        Bukkit.getLogger().info("플레이어: $playerUUID")
        Bukkit.getLogger().info("아이템: $itemId")
        Bukkit.getLogger().info("수량: $quantity")
        
        // 입력값 검증
        if (quantity <= 0) {
            return TransactionResult(
                success = false,
                message = "판매 수량은 0보다 커야 합니다.",
                errorType = TransactionErrorType.INVALID_QUANTITY,
                requestedQuantity = quantity
            )
        }

        // 아이템 보유 확인
        if (!inventoryService.hasItems(playerUUID, itemId, quantity)) {
            return TransactionResult(
                success = false,
                message = "충분한 아이템이 없습니다. 인벤토리를 확인해주세요.",
                errorType = TransactionErrorType.INSUFFICIENT_INVENTORY,
                requestedQuantity = quantity
            )
        }

        // 아이템에 대한 mutex 획득
        val itemMutex = getMutexForItem(itemId)

        return itemMutex.withLock {
            println("Instant Sell 로직 실행 (아이템 ID: $itemId")

            try {
                // 1. 가장 높은 가격의 Buy Order 찾기
                Bukkit.getLogger().info("§71단계: 활성 구매 주문 조회 중...")
                val activeBuyOrders = orderRepository.findActiveBuyOrdersForItem(itemId)
                Bukkit.getLogger().info("§7조회된 구매 주문 수: ${activeBuyOrders.size}")
                
                if (activeBuyOrders.isEmpty()) {
                    Bukkit.getLogger().info("§c실패: 구매 주문이 없음")
                    return@withLock TransactionResult(
                        success = false,
                        message = "현재 구매 주문이 없습니다. 나중에 다시 시도해주세요.",
                        errorType = TransactionErrorType.NO_ACTIVE_ORDERS,
                        requestedQuantity = quantity,
                        availableQuantity = 0
                    )
                }

                // 2. 판매 가능한 수량과 예상 수익 계산
                Bukkit.getLogger().info("§72단계: 판매 계획 수립 중...")
                var totalEarned = 0.0
                var potentialSales = mutableListOf<Pair<BuyOrder, Int>>()
                var totalDemand = 0
                var remainingQuantity = quantity

                for (order in activeBuyOrders) {
                    if (remainingQuantity <= 0) break
                    
                    val availableToBuy = order.quantityOrdered - order.quantityFilled
                    totalDemand += availableToBuy
                    Bukkit.getLogger().info("§7주문 ${order.id}: 가격 ${order.pricePerUnit}, 구매가능 ${availableToBuy}개")
                    
                    val quantityToSell = minOf(remainingQuantity, availableToBuy)
                    val revenue = quantityToSell * order.pricePerUnit
                    val tax = revenue * taxRate
                    val netRevenue = revenue - tax

                    potentialSales.add(Pair(order, quantityToSell))
                    totalEarned += netRevenue
                    remainingQuantity -= quantityToSell
                }

                val actualQuantity = quantity - remainingQuantity
                Bukkit.getLogger().info("§7판매 계산 완료: 실제수량 ${actualQuantity}개, 총수익 ${totalEarned}원")
                
                if (actualQuantity <= 0) {
                    Bukkit.getLogger().info("§c실패: 판매 가능한 수량이 0")
                    return@withLock TransactionResult(
                        success = false,
                        message = "판매 가능한 구매 주문이 없습니다.",
                        errorType = TransactionErrorType.NO_ACTIVE_ORDERS,
                        requestedQuantity = quantity,
                        availableQuantity = totalDemand
                    )
                }

                var itemsRemoved = false
                var transactions = mutableListOf<Transaction>()
                var successfulSales = mutableListOf<Triple<String, String, Int>>()
                var totalSold = 0

                try {
                    // 3. 아이템 임시 제거
                    Bukkit.getLogger().info("§73단계: 아이템 임시 제거 중... (${quantity}개)")
                    val removeResult = inventoryService.removeItems(playerUUID, itemId, quantity)
                    if (!removeResult) {
                        Bukkit.getLogger().info("§c실패: 아이템 제거 실패")
                        return@withLock TransactionResult(
                            success = false,
                            message = "아이템 제거 중 오류가 발생했습니다. 인벤토리를 확인해주세요.",
                            errorType = TransactionErrorType.INSUFFICIENT_INVENTORY,
                            requestedQuantity = quantity
                        )
                    }
                    itemsRemoved = true

                    // 4. 실제 거래 처리
                    Bukkit.getLogger().info("§74단계: 실제 거래 처리 시작 (${potentialSales.size}개 주문 처리)")
                    for ((buyOrder, quantityToSell) in potentialSales) {
                        // 구매 주문에 대한 mutex 획득
                        val orderMutex = getMutexForOrder(buyOrder.id)

                        val saleResult = orderMutex.withLock {
                            Bukkit.getLogger().info("§7주문 ${buyOrder.id} 처리 시작 (예상 판매량: ${quantityToSell}개)")
                            
                            // 구매 주문 최신 상태 확인 (동시성 문제 방지)
                            val updatedBuyOrder = orderRepository.findBuyOrderById(buyOrder.id)
                            if (updatedBuyOrder == null ||
                                (updatedBuyOrder.status != OrderStatus.ACTIVE &&
                                updatedBuyOrder.status != OrderStatus.PARTIALLY_FILLED)) {
                                Bukkit.getLogger().info("§c주문 ${buyOrder.id} 건너뜀: 상태 변경됨 - 다른 활성 주문 재조회")
                                
                                // 다른 활성 주문을 찾아서 거래 시도
                                val freshActiveBuyOrders = orderRepository.findActiveBuyOrdersForItem(itemId)
                                    .filter { it.id != buyOrder.id } // 현재 실패한 주문 제외
                                    .sortedByDescending { it.pricePerUnit } // 가격 내림차순 정렬
                                
                                if (freshActiveBuyOrders.isNotEmpty()) {
                                    val alternativeOrder = freshActiveBuyOrders.first()
                                    val alternativeAvailable = alternativeOrder.quantityOrdered - alternativeOrder.quantityFilled
                                    if (alternativeAvailable > 0) {
                                        Bukkit.getLogger().info("§a대체 주문 발견: ${alternativeOrder.id} (가격: ${alternativeOrder.pricePerUnit}, 수량: ${alternativeAvailable})")
                                        
                                        // 대체 주문으로 거래 시도
                                        val altQuantityToSell = minOf(quantityToSell, alternativeAvailable)
                                        val altTransactionPrice = alternativeOrder.pricePerUnit
                                        val altSubtotal = altQuantityToSell * altTransactionPrice
                                        val altTax = altSubtotal * taxRate
                                        val altSellerReceives = altSubtotal - altTax
                                        
                                        try {
                                            Bukkit.getLogger().info("§7대체 주문 ${alternativeOrder.id}에서 거래 시도 중... (${altQuantityToSell}개, ${altSubtotal}원)")
                                            
                                            // 구매자에게 아이템 지급
                                            val addResult = inventoryService.addItems(alternativeOrder.playerUUID, itemId, altQuantityToSell)
                                            if (!addResult) {
                                                Bukkit.getLogger().info("§c실패: 아이템 지급 실패")
                                                return@withLock null
                                            }
                                            
                                            // 판매자에게 돈 지급
                                            val depositResult = economyService.deposit(playerUUID, altSellerReceives)
                                            if (!depositResult) {
                                                Bukkit.getLogger().info("§c실패: 판매자에게 돈 지급 실패 - 아이템 롤백")
                                                inventoryService.removeItems(alternativeOrder.playerUUID, itemId, altQuantityToSell)
                                                return@withLock null
                                            }
                                            
                                            // 구매 주문 상태 업데이트
                                            val updatedAltOrder = alternativeOrder.copy(
                                                quantityFilled = alternativeOrder.quantityFilled + altQuantityToSell,
                                                status = if (alternativeOrder.quantityFilled + altQuantityToSell >= alternativeOrder.quantityOrdered)
                                                    OrderStatus.FILLED else OrderStatus.PARTIALLY_FILLED
                                            )
                                            
                                            val updateSuccess = orderRepository.updateBuyOrder(updatedAltOrder)
                                            if (!updateSuccess) {
                                                Bukkit.getLogger().info("§c실패: 데이터베이스 업데이트 실패 - 전체 롤백")
                                                economyService.withdraw(playerUUID, altSellerReceives)
                                                inventoryService.removeItems(alternativeOrder.playerUUID, itemId, altQuantityToSell)
                                                return@withLock null
                                            }
                                            
                                            // 거래 기록
                                            val transaction = Transaction(
                                                id = UUID.randomUUID().toString(),
                                                buyerUUID = alternativeOrder.playerUUID,
                                                sellerUUID = playerUUID,
                                                itemId = itemId,
                                                quantity = altQuantityToSell,
                                                pricePerUnit = altTransactionPrice,
                                                timestampCompleted = Instant.now(),
                                                buyOrderId = alternativeOrder.id,
                                                sellOfferId = null // 즉시 판매는 기존 제안이 없음
                                            )
                                            
                                            val saveResult = transactionRepository.saveTransaction(transaction)
                                            if (!saveResult) {
                                                Bukkit.getLogger().info("§e거래 기록 저장 실패 (거래는 완료됨)")
                                            } else {
                                                transactions.add(transaction)
                                            }
                                            
                                            Bukkit.getLogger().info("§a성공: 대체 주문으로 거래 완료 (${altQuantityToSell}개, ${altSubtotal}원)")
                                            return@withLock Triple(alternativeOrder.playerUUID, itemId, altQuantityToSell)

                                        } catch (e: Exception) {
                                            Bukkit.getLogger().info("§c대체 주문 거래 중 예외 발생: ${e.message}")
                                            return@withLock null
                                        }
                                    }
                                }
                                
                                Bukkit.getLogger().info("§c사용 가능한 대체 주문 없음")
                                return@withLock null // 이 주문은 건너뜀
                            }
                            val buyerUUID = updatedBuyOrder.playerUUID
                            val availableToBuy = updatedBuyOrder.quantityOrdered - updatedBuyOrder.quantityFilled
                            val matchedQuantity = minOf(quantityToSell, availableToBuy)

                            if (matchedQuantity <= 0) return@withLock null

                            val transactionPrice = updatedBuyOrder.pricePerUnit
                            val subtotal = matchedQuantity * transactionPrice
                            val tax = subtotal * taxRate
                            val sellerReceives = subtotal - tax

                            try {
                                // 구매자에게 아이템 지급
                                Bukkit.getLogger().info("§7아이템 지급 시도... (${buyerUUID}에게 ${itemId} ${matchedQuantity}개)")
                                val addResult = inventoryService.addItems(buyerUUID, itemId, matchedQuantity)
                                if (!addResult) {
                                    Bukkit.getLogger().info("§c실패: 아이템 지급 실패")
                                    return@withLock null
                                }
                                Bukkit.getLogger().info("§a성공: 아이템 지급 완료")

                                // 판매자에게 돈 지급
                                Bukkit.getLogger().info("§7판매자에게 돈 지급 시도... (${sellerReceives}원 -> ${playerUUID})")
                                val depositResult = economyService.deposit(playerUUID, sellerReceives)
                                if (!depositResult) {
                                    Bukkit.getLogger().info("§c실패: 판매자에게 돈 지급 실패 - 아이템 롤백")
                                    inventoryService.removeItems(buyerUUID, itemId, matchedQuantity)
                                    return@withLock null
                                }
                                Bukkit.getLogger().info("§a성공: 판매자에게 돈 지급 완료")

                                // 구매 주문 상태 업데이트
                                Bukkit.getLogger().info("§7구매 주문 상태 업데이트 시도...")
                                updatedBuyOrder.quantityFilled += matchedQuantity
                                if (updatedBuyOrder.quantityFilled >= updatedBuyOrder.quantityOrdered) {
                                    updatedBuyOrder.status = OrderStatus.FILLED
                                } else {
                                    updatedBuyOrder.status = OrderStatus.PARTIALLY_FILLED
                                }

                                val updateSuccess = orderRepository.updateBuyOrder(updatedBuyOrder)

                                if (!updateSuccess) {
                                    Bukkit.getLogger().info("§c실패: 데이터베이스 업데이트 실패 - 전체 롤백")
                                    economyService.withdraw(playerUUID, sellerReceives)
                                    inventoryService.removeItems(buyerUUID, itemId, matchedQuantity)
                                    return@withLock null
                                }
                                Bukkit.getLogger().info("§a성공: 데이터베이스 업데이트 완료")

                                // 거래 기록
                                Bukkit.getLogger().info("§7거래 기록 저장 시도...")
                                val transaction = Transaction(
                                    id = UUID.randomUUID().toString(),
                                    buyerUUID = buyerUUID,
                                    sellerUUID = playerUUID,
                                    itemId = itemId,
                                    quantity = matchedQuantity,
                                    pricePerUnit = transactionPrice,
                                    timestampCompleted = Instant.now(),
                                    buyOrderId = updatedBuyOrder.id,
                                    sellOfferId = null // 즉시 판매는 기존 제안이 없음
                                )

                                val transactionSaved = transactionRepository.saveTransaction(transaction)
                                if (!transactionSaved) {
                                    Bukkit.getLogger().info("§e거래 기록 저장 실패 (거래는 완료됨)")
                                }
                                transactions.add(transaction) // 거래 기록 저장 성공 여부와 관계없이 추가

                                Bukkit.getLogger().info("§a주문 ${buyOrder.id} 거래 성공: ${matchedQuantity}개, ${subtotal}원")
                                return@withLock Triple(buyerUUID, itemId, matchedQuantity)

                            } catch (e: Exception) {
                                Bukkit.getLogger().info("§c주문 ${buyOrder.id} 거래 중 예외 발생: ${e.message}")
                                return@withLock null
                            }
                        }

                        // 결과 처리
                        if (saleResult != null) {
                            val (buyerUUID, _, quantity) = saleResult
                            totalSold += quantity
                            val revenue = quantity * buyOrder.pricePerUnit
                            val tax = revenue * taxRate
                            val netRevenue = revenue - tax
                            totalEarned += netRevenue
                            successfulSales.add(Triple(buyerUUID, itemId, quantity))
                            Bukkit.getLogger().info("§a성공 누적: ${totalSold}개, ${totalEarned}원")
                        } else {
                            Bukkit.getLogger().info("§c주문 ${buyOrder.id}에서 거래 실패")
                        }
                    }

                    Bukkit.getLogger().info("§7=== 거래 처리 완료 ===")
                    Bukkit.getLogger().info("§7총 판매된 아이템: ${totalSold}개")
                    Bukkit.getLogger().info("§7총 수익: ${totalEarned}원")
                    Bukkit.getLogger().info("§7성공한 거래 수: ${successfulSales.size}개")

                    // 남은 아이템 반환
                    if (remainingQuantity > 0) {
                        Bukkit.getLogger().info("§7남은 아이템 반환: ${remainingQuantity}개")
                        inventoryService.addItems(playerUUID, itemId, remainingQuantity)
                    }

                    if (totalSold > 0) {
                        Bukkit.getLogger().info("§a최종 성공: ${totalSold}개 판매, ${totalEarned}원 수익")
                        return@withLock TransactionResult(
                            success = true,
                            message = "${totalSold}개의 ${itemId}을(를) 총 ${String.format("%.2f", totalEarned)}원에 판매했습니다.",
                            quantityTransacted = totalSold,
                            averagePrice = totalEarned / totalSold,
                            requestedQuantity = quantity,
                            partiallyFulfilled = totalSold < quantity,
                            totalCost = totalEarned,
                        )
                    } else {
                        // 모든 거래가 실패한 경우
                        Bukkit.getLogger().info("§c최종 실패: 모든 거래 실패")
                        return@withLock TransactionResult(
                            success = false,
                            message = "거래를 완료할 수 없습니다. 잠시 후 다시 시도해주세요.",
                            errorType = TransactionErrorType.SYSTEM_ERROR,
                            requestedQuantity = quantity,
                            totalCost = 0.0
                        )
                    }

                } catch (e: Exception) {
                    Bukkit.getLogger().info("§c예외 발생: ${e.message}")
                    e.printStackTrace()
                    
                    // 예외 발생 시 롤백
                    if (itemsRemoved && remainingQuantity > 0) {
                        try {
                            Bukkit.getLogger().info("§7남은 아이템 반환: ${remainingQuantity}개")
                            inventoryService.addItems(playerUUID, itemId, remainingQuantity)
                        } catch (refundException: Exception) {
                            Bukkit.getLogger().info("§c환불 중 예외 발생: ${refundException.message}")
                        }
                    }

                    return@withLock TransactionResult(
                        success = false,
                        message = "시스템 오류가 발생했습니다. 관리자에게 문의해주세요.",
                        errorType = TransactionErrorType.SYSTEM_ERROR,
                        requestedQuantity = quantity,
                        totalCost = totalEarned
                    )
                }
            } catch (e: Exception) {
                Bukkit.getLogger().info("§c치명적 예외 발생: ${e.message}")
                e.printStackTrace()
                return@withLock TransactionResult(
                    success = false,
                    message = "시스템 오류가 발생했습니다.",
                    errorType = TransactionErrorType.SYSTEM_ERROR,
                    requestedQuantity = quantity
                )
            }
        }
    }

    override suspend fun cancelOrderOrOffer(playerUUID: String, orderId: String): PlaceOrderResult {
        // 주문/제안에 대한 mutex 획득
        val orderMutex = getMutexForOrder(orderId)

        return orderMutex.withLock {
            println("주문/제안 취소 로직 실행 (주문 ID: $orderId")

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