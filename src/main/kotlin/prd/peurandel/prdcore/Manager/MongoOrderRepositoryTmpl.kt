package prd.peurandel.prdcore.Manager
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant
import org.bukkit.Bukkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- OrderRepository 인터페이스 구현 ---
class MongoOrderRepositoryImpl(
    // bazaar 데이터베이스 객체를 주입받음
    private val database: MongoDatabase
) : OrderRepository {

    // 각 메소드에서 사용할 컬렉션을 명시적으로 지정
    private val buyOrderCollection = database.getCollection("buy_orders")
    private val sellOfferCollection = database.getCollection("sell_offers")

    override suspend fun findActiveBuyOrdersForItem(itemId: String, limit: Int): List<BuyOrder> {
        return withContext(Dispatchers.IO) {
            try {
                Bukkit.getLogger().info("[MongoOrderRepository] 구매 주문 조회 시작: itemId=$itemId, limit=$limit")
                
                // Document 형식으로 조회
                val docCollection = database.getCollection("buy_orders")
                Bukkit.getLogger().info("[MongoOrderRepository] buy_orders 컬렉션 접근 성공")
                
                val filter = Filters.and(
                    Filters.eq("itemId", itemId),
                    Filters.`in`("status", OrderStatus.ACTIVE.name, OrderStatus.PARTIALLY_FILLED.name)
                )
                val sort = Sorts.orderBy(Sorts.descending("pricePerUnit"), Sorts.ascending("timestampPlaced"))
                
                Bukkit.getLogger().info("[MongoOrderRepository] 필터 및 정렬 설정 완료")
                
                val documents = docCollection.find(filter).sort(sort).limit(limit).toList()
                Bukkit.getLogger().info("[MongoOrderRepository] 조회된 구매 주문 수: ${documents.size}")
                
                // Document에서 BuyOrder 객체로 수동 변환
                val result = documents.mapIndexed { index, doc ->
                    Bukkit.getLogger().info("[MongoOrderRepository] 구매 주문 #$index 변환 시작")
                    try {
                        val id = if (doc.get("_id") is ObjectId) {
                            doc.getObjectId("_id").toString()
                        } else {
                            doc.getString("_id") ?: UUID.randomUUID().toString()
                        }
                        val playerUUID = doc.getString("playerUUID") ?: ""
                        val docItemId = doc.getString("itemId") ?: ""
                        val quantityOrdered = doc.getInteger("quantityOrdered", 0)
                        val quantityFilled = doc.getInteger("quantityFilled", 0)
                        val pricePerUnit = doc.getDouble("pricePerUnit") ?: 0.0
                        val timestampPlaced = doc.getLong("timestampPlaced") ?: Instant.now().toEpochMilli()
                        val statusStr = doc.getString("status") ?: OrderStatus.ACTIVE.name
                        
                        Bukkit.getLogger().info("[MongoOrderRepository] 구매 주문 #$index 필드 추출 완료: id=$id, itemId=$docItemId")
                        
                        val status = try {
                            OrderStatus.valueOf(statusStr)
                        } catch (e: Exception) {
                            Bukkit.getLogger().warning("[MongoOrderRepository] 상태 변환 실패: $statusStr -> 기본값 ACTIVE 사용")
                            OrderStatus.ACTIVE
                        }
                        
                        val order = BuyOrder(
                            id = id,
                            playerUUID = playerUUID,
                            itemId = docItemId,
                            quantityOrdered = quantityOrdered,
                            quantityFilled = quantityFilled,
                            pricePerUnit = pricePerUnit,
                            timestampPlaced = timestampPlaced,
                            status = status
                        )
                        
                        Bukkit.getLogger().info("[MongoOrderRepository] 구매 주문 #$index 변환 완료")
                        order
                    } catch (e: Exception) {
                        Bukkit.getLogger().severe("[MongoOrderRepository] 구매 주문 #$index 변환 실패: ${e.message}")
                        e.printStackTrace()
                        null
                    }
                }.filterNotNull()
                
                Bukkit.getLogger().info("[MongoOrderRepository] 구매 주문 조회 완료: ${result.size}개 반환")
                result
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 구매 주문 조회 실패: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    override suspend fun findActiveSellOffersForItem(itemId: String, limit: Int): List<SellOffer> {
        return withContext(Dispatchers.IO) {
            try {
                Bukkit.getLogger().info("[MongoOrderRepository] 판매 제안 조회 시작: itemId=$itemId, limit=$limit")
                
                // Document 형식으로 조회
                val docCollection = database.getCollection("sell_offers")
                Bukkit.getLogger().info("[MongoOrderRepository] sell_offers 컬렉션 접근 성공")
                
                val filter = Filters.and(
                    Filters.eq("itemId", itemId),
                    Filters.`in`("status", OrderStatus.ACTIVE.name, OrderStatus.PARTIALLY_FILLED.name)
                )
                val sort = Sorts.orderBy(Sorts.ascending("pricePerUnit"), Sorts.ascending("timestampPlaced"))
                
                Bukkit.getLogger().info("[MongoOrderRepository] 필터 및 정렬 설정 완료")
                
                val documents = docCollection.find(filter).sort(sort).limit(limit).toList()
                Bukkit.getLogger().info("[MongoOrderRepository] 조회된 판매 제안 수: ${documents.size}")
                
                // Document에서 SellOffer 객체로 수동 변환
                val result = documents.mapIndexed { index, doc ->
                    Bukkit.getLogger().info("[MongoOrderRepository] 판매 제안 #$index 변환 시작")
                    try {
                        val id = if (doc.get("_id") is ObjectId) {
                            doc.getObjectId("_id").toString()
                        } else {
                            doc.getString("_id") ?: UUID.randomUUID().toString()
                        }
                        val playerUUID = doc.getString("playerUUID") ?: ""
                        val docItemId = doc.getString("itemId") ?: ""
                        val quantityOffered = doc.getInteger("quantityOffered", 0)
                        val quantitySold = doc.getInteger("quantitySold", 0)
                        val pricePerUnit = doc.getDouble("pricePerUnit") ?: 0.0
                        val timestampPlaced = doc.getLong("timestampPlaced") ?: Instant.now().toEpochMilli()
                        val statusStr = doc.getString("status") ?: OrderStatus.ACTIVE.name
                        
                        Bukkit.getLogger().info("[MongoOrderRepository] 판매 제안 #$index 필드 추출 완료: id=$id, itemId=$docItemId")
                        
                        val status = try {
                            OrderStatus.valueOf(statusStr)
                        } catch (e: Exception) {
                            Bukkit.getLogger().warning("[MongoOrderRepository] 상태 변환 실패: $statusStr -> 기본값 ACTIVE 사용")
                            OrderStatus.ACTIVE
                        }
                        
                        val offer = SellOffer(
                            id = id,
                            playerUUID = playerUUID,
                            itemId = docItemId,
                            quantityOffered = quantityOffered,
                            quantitySold = quantitySold,
                            pricePerUnit = pricePerUnit,
                            timestampPlaced = timestampPlaced,
                            status = status
                        )
                        
                        Bukkit.getLogger().info("[MongoOrderRepository] 판매 제안 #$index 변환 완료")
                        offer
                    } catch (e: Exception) {
                        Bukkit.getLogger().severe("[MongoOrderRepository] 판매 제안 #$index 변환 실패: ${e.message}")
                        e.printStackTrace()
                        null
                    }
                }.filterNotNull()
                
                Bukkit.getLogger().info("[MongoOrderRepository] 판매 제안 조회 완료: ${result.size}개 반환")
                result
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 판매 제안 조회 실패: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    override suspend fun findBuyOrderById(orderId: String): BuyOrder? {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("_id", orderId)
                val doc = buyOrderCollection.find(filter).firstOrNull()
                if (doc != null) {
                    val id = if (doc.get("_id") is ObjectId) {
                        doc.getObjectId("_id").toString()
                    } else {
                        doc.getString("_id") ?: UUID.randomUUID().toString()
                    }
                    val playerUUID = doc.getString("playerUUID") ?: ""
                    val docItemId = doc.getString("itemId") ?: ""
                    val quantityOrdered = doc.getInteger("quantityOrdered", 0)
                    val quantityFilled = doc.getInteger("quantityFilled", 0)
                    val pricePerUnit = doc.getDouble("pricePerUnit") ?: 0.0
                    val timestampPlaced = doc.getLong("timestampPlaced") ?: Instant.now().toEpochMilli()
                    val statusStr = doc.getString("status") ?: OrderStatus.ACTIVE.name
                    
                    val status = try {
                        OrderStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        Bukkit.getLogger().warning("[MongoOrderRepository] 상태 변환 실패: $statusStr -> 기본값 ACTIVE 사용")
                        OrderStatus.ACTIVE
                    }
                    
                    return@withContext BuyOrder(
                        id = id,
                        playerUUID = playerUUID,
                        itemId = docItemId,
                        quantityOrdered = quantityOrdered,
                        quantityFilled = quantityFilled,
                        pricePerUnit = pricePerUnit,
                        timestampPlaced = timestampPlaced,
                        status = status
                    )
                }
                return@withContext null
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 구매 주문 조회 실패: ${e.message}")
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    override suspend fun findSellOfferById(orderId: String): SellOffer? {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("_id", orderId)
                val doc = sellOfferCollection.find(filter).firstOrNull()
                if (doc != null) {
                    val id = if (doc.get("_id") is ObjectId) {
                        doc.getObjectId("_id").toString()
                    } else {
                        doc.getString("_id") ?: UUID.randomUUID().toString()
                    }
                    val playerUUID = doc.getString("playerUUID") ?: ""
                    val docItemId = doc.getString("itemId") ?: ""
                    val quantityOffered = doc.getInteger("quantityOffered", 0)
                    val quantitySold = doc.getInteger("quantitySold", 0)
                    val pricePerUnit = doc.getDouble("pricePerUnit") ?: 0.0
                    val timestampPlaced = doc.getLong("timestampPlaced") ?: Instant.now().toEpochMilli()
                    val statusStr = doc.getString("status") ?: OrderStatus.ACTIVE.name
                    
                    val status = try {
                        OrderStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        Bukkit.getLogger().warning("[MongoOrderRepository] 상태 변환 실패: $statusStr -> 기본값 ACTIVE 사용")
                        OrderStatus.ACTIVE
                    }
                    
                    SellOffer(
                        id = id,
                        playerUUID = playerUUID,
                        itemId = docItemId,
                        quantityOffered = quantityOffered,
                        quantitySold = quantitySold,
                        pricePerUnit = pricePerUnit,
                        timestampPlaced = timestampPlaced,
                        status = status
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 판매 제안 조회 실패: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    override suspend fun findActiveOrdersByPlayer(playerUUID: String): List<Pair<BuyOrder?, SellOffer?>> {
        return withContext(Dispatchers.IO) {
            try {
                val buyOrders = buyOrderCollection.find(Filters.eq("playerUUID", playerUUID)).toList()
                val sellOffers = sellOfferCollection.find(Filters.eq("playerUUID", playerUUID)).toList()
                
                val result = mutableListOf<Pair<BuyOrder?, SellOffer?>>()
                buyOrders.forEach { doc ->
                    val id = if (doc.get("_id") is ObjectId) {
                        doc.getObjectId("_id").toString()
                    } else {
                        doc.getString("_id") ?: UUID.randomUUID().toString()
                    }
                    val playerUUID = doc.getString("playerUUID") ?: ""
                    val docItemId = doc.getString("itemId") ?: ""
                    val quantityOrdered = doc.getInteger("quantityOrdered", 0)
                    val quantityFilled = doc.getInteger("quantityFilled", 0)
                    val pricePerUnit = doc.getDouble("pricePerUnit") ?: 0.0
                    val timestampPlaced = doc.getLong("timestampPlaced") ?: Instant.now().toEpochMilli()
                    val statusStr = doc.getString("status") ?: OrderStatus.ACTIVE.name
                    
                    val status = try {
                        OrderStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        Bukkit.getLogger().warning("[MongoOrderRepository] 상태 변환 실패: $statusStr -> 기본값 ACTIVE 사용")
                        OrderStatus.ACTIVE
                    }
                    
                    result.add(Pair(
                        BuyOrder(
                            id = id,
                            playerUUID = playerUUID,
                            itemId = docItemId,
                            quantityOrdered = quantityOrdered,
                            quantityFilled = quantityFilled,
                            pricePerUnit = pricePerUnit,
                            timestampPlaced = timestampPlaced,
                            status = status
                        ),
                        null
                    ))
                }
                sellOffers.forEach { doc ->
                    val id = if (doc.get("_id") is ObjectId) {
                        doc.getObjectId("_id").toString()
                    } else {
                        doc.getString("_id") ?: UUID.randomUUID().toString()
                    }
                    val playerUUID = doc.getString("playerUUID") ?: ""
                    val docItemId = doc.getString("itemId") ?: ""
                    val quantityOffered = doc.getInteger("quantityOffered", 0)
                    val quantitySold = doc.getInteger("quantitySold", 0)
                    val pricePerUnit = doc.getDouble("pricePerUnit") ?: 0.0
                    val timestampPlaced = doc.getLong("timestampPlaced") ?: Instant.now().toEpochMilli()
                    val statusStr = doc.getString("status") ?: OrderStatus.ACTIVE.name
                    
                    val status = try {
                        OrderStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        Bukkit.getLogger().warning("[MongoOrderRepository] 상태 변환 실패: $statusStr -> 기본값 ACTIVE 사용")
                        OrderStatus.ACTIVE
                    }
                    
                    result.add(Pair(
                        null,
                        SellOffer(
                            id = id,
                            playerUUID = playerUUID,
                            itemId = docItemId,
                            quantityOffered = quantityOffered,
                            quantitySold = quantitySold,
                            pricePerUnit = pricePerUnit,
                            timestampPlaced = timestampPlaced,
                            status = status
                        )
                    ))
                }
                result
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 활성 주문 조회 실패: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    override suspend fun saveBuyOrder(order: BuyOrder): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = Document("_id", order.id)
                    .append("playerUUID", order.playerUUID)
                    .append("itemId", order.itemId)
                    .append("quantityOrdered", order.quantityOrdered)
                    .append("quantityFilled", order.quantityFilled)
                    .append("pricePerUnit", order.pricePerUnit)
                    .append("timestampPlaced", order.timestampPlaced)
                    .append("status", order.status.name)
                buyOrderCollection.insertOne(doc)
                true
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 구매 주문 저장 실패: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    override suspend fun saveSellOffer(offer: SellOffer): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = Document("_id", offer.id)
                    .append("playerUUID", offer.playerUUID)
                    .append("itemId", offer.itemId)
                    .append("quantityOffered", offer.quantityOffered)
                    .append("quantitySold", offer.quantitySold)
                    .append("pricePerUnit", offer.pricePerUnit)
                    .append("timestampPlaced", offer.timestampPlaced)
                    .append("status", offer.status.name)
                sellOfferCollection.insertOne(doc)
                true
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 판매 제안 저장 실패: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    override suspend fun updateBuyOrder(order: BuyOrder): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("_id", order.id)
                val doc = Document("_id", order.id)
                    .append("playerUUID", order.playerUUID)
                    .append("itemId", order.itemId)
                    .append("quantityOrdered", order.quantityOrdered)
                    .append("quantityFilled", order.quantityFilled)
                    .append("pricePerUnit", order.pricePerUnit)
                    .append("timestampPlaced", order.timestampPlaced)
                    .append("status", order.status.name)
                buyOrderCollection.replaceOne(filter, doc)
                true
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 구매 주문 업데이트 실패: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    override suspend fun updateSellOffer(offer: SellOffer): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("_id", offer.id)
                val doc = Document("_id", offer.id)
                    .append("playerUUID", offer.playerUUID)
                    .append("itemId", offer.itemId)
                    .append("quantityOffered", offer.quantityOffered)
                    .append("quantitySold", offer.quantitySold)
                    .append("pricePerUnit", offer.pricePerUnit)
                    .append("timestampPlaced", offer.timestampPlaced)
                    .append("status", offer.status.name)
                sellOfferCollection.replaceOne(filter, doc)
                true
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[MongoOrderRepository] 판매 제안 업데이트 실패: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
}