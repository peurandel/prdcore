package prd.peurandel.prdcore.Manager

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.Document
import java.util.logging.Logger

/**
 * MongoDB를 사용한 상품 저장소 구현체
 */
class MongoProductRepositoryImpl(
    private val database: MongoDatabase,
    private val logger: Logger? = null,
    private val scope: CoroutineScope // CoroutineScope 주입 받기
) : ProductRepository {

    private val productCollection = database.getCollection("products")

    init {
        // 코루틴을 시작하여 비동기적으로 인덱스 생성
        scope.launch {
            try {
                logger?.info("상품 컬렉션 인덱스 생성 시도...")
                // 표준 MongoDB 드라이버로 인덱스 생성
                productCollection.createIndex(Indexes.ascending("categoryId"))
                productCollection.createIndex(Indexes.text("name")) // 복합 텍스트 인덱스 등 고려
                logger?.info("상품 컬렉션 인덱스 생성 완료.")
            } catch (e: Exception) {
                // 오류 처리
                logger?.warning("상품 컬렉션 인덱스 생성 중 오류: ${e.message}")
            }
        }
    }

    /**
     * 특정 카테고리에 속한 모든 상품을 조회합니다.
     */
    override suspend fun findByCategory(categoryId: String): List<Product> {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("categoryId", categoryId)
                val documents = productCollection.find(filter).toList()
                
                documents.mapNotNull { doc ->
                    try {
                        Product(
                            id = doc.getString("_id") ?: "",
                            name = doc.getString("name") ?: "",
                            categoryId = doc.getString("categoryId") ?: "",
                            description = doc.getString("description"),
                            isTradable = doc.getBoolean("isTradable", true),
                            itemMeta = doc.getString("itemMeta")
                        )
                    } catch (e: Exception) {
                        logger?.warning("상품 변환 중 오류: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                logger?.warning("카테고리별 상품 조회 중 오류: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * 특정 상품 그룹에 속한 모든 상품을 조회합니다.
     */
    override suspend fun findByProductGroup(productGroupId: String): List<Product> {
        return try {
            productCollection.find(Product::productGroupId eq productGroupId).toList()
        } catch (e: Exception) {
            logger?.warning("상품 그룹별 상품 조회 중 오류: ${e.message}")
            emptyList()
        }
    }

    /**
     * 상품명 또는 설명에 특정 문자열이 포함된 상품을 검색합니다.
     */
    override suspend fun findByNameQuery(query: String): List<Product> {
        return withContext(Dispatchers.IO) {
            try {
                // 정규식을 사용한 부분 문자열 검색 (대소문자 무시)
                val namePattern = java.util.regex.Pattern.compile(query, java.util.regex.Pattern.CASE_INSENSITIVE)
                val descPattern = java.util.regex.Pattern.compile(query, java.util.regex.Pattern.CASE_INSENSITIVE)
                
                val filter = Filters.or(
                    Filters.regex("name", namePattern),
                    Filters.regex("description", descPattern)
                )
                
                val documents = productCollection.find(filter).toList()
                
                documents.mapNotNull { doc ->
                    try {
                        Product(
                            id = doc.getString("_id") ?: "",
                            name = doc.getString("name") ?: "",
                            categoryId = doc.getString("categoryId") ?: "",
                            description = doc.getString("description"),
                            isTradable = doc.getBoolean("isTradable", true),
                            itemMeta = doc.getString("itemMeta")
                        )
                    } catch (e: Exception) {
                        logger?.warning("상품 변환 중 오류: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                logger?.warning("상품명 검색 중 오류: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * ID로 특정 상품을 조회합니다.
     */
    override suspend fun findById(id: String): Product? {
        return withContext(Dispatchers.IO) {
            try {
                logger?.info("[MongoProductRepository] 상품 ID 조회 시작: id=$id")
                
                val filter = Filters.eq("_id", id)
                val doc = productCollection.find(filter).first()
                
                if (doc != null) {
                    logger?.info("[MongoProductRepository] 상품 문서 조회 성공: _id=${doc.getString("_id")}")
                    
                    // 각 필드 추출 시 로깅 추가
                    val productId = doc.getString("_id") ?: ""
                    val name = doc.getString("name") ?: ""
                    val categoryId = doc.getString("categoryId") ?: ""
                    val description = doc.getString("description")
                    val isTradable = doc.getBoolean("isTradable", true)
                    val itemMeta = doc.getString("itemMeta")
                    
                    logger?.info("[MongoProductRepository] 상품 필드 추출 완료: id=$productId, name=$name, isTradable=$isTradable")
                    
                    val product = Product(
                        id = productId,
                        name = name,
                        categoryId = categoryId,
                        description = description,
                        isTradable = isTradable,
                        itemMeta = itemMeta
                    )
                    
                    logger?.info("[MongoProductRepository] Product 객체 생성 완료")
                    product
                } else {
                    logger?.warning("[MongoProductRepository] 상품을 찾을 수 없음: id=$id")
                    null
                }
            } catch (e: Exception) {
                logger?.severe("[MongoProductRepository] 상품 ID 조회 중 오류: ${e.message}")
                e.printStackTrace() // 상세 오류 로그 추가
                null
            }
        }
    }

    /**
     * 새 상품을 저장합니다.
     */
    suspend fun saveProduct(product: Product): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = Document("_id", product.id)
                    .append("name", product.name)
                    .append("categoryId", product.categoryId)
                    .append("description", product.description)
                    .append("isTradable", product.isTradable)
                    .append("itemMeta", product.itemMeta)
                
                productCollection.insertOne(doc)
                true
            } catch (e: Exception) {
                logger?.warning("상품 저장 중 오류: ${e.message}")
                false
            }
        }
    }

    /**
     * 상품 정보를 업데이트합니다.
     */
    suspend fun updateProduct(product: Product): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("_id", product.id)
                val doc = Document("_id", product.id)
                    .append("name", product.name)
                    .append("categoryId", product.categoryId)
                    .append("description", product.description)
                    .append("isTradable", product.isTradable)
                    .append("itemMeta", product.itemMeta)
                
                val result = productCollection.replaceOne(filter, doc)
                result.modifiedCount > 0
            } catch (e: Exception) {
                logger?.warning("상품 업데이트 중 오류: ${e.message}")
                false
            }
        }
    }

    /**
     * 상품의 거래 가능 상태를 변경합니다.
     */
    suspend fun updateTradableStatus(productId: String, isTradable: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("_id", productId)
                val update = Updates.set("isTradable", isTradable)
                
                val result = productCollection.updateOne(filter, update)
                result.modifiedCount > 0
            } catch (e: Exception) {
                logger?.warning("상품 거래 상태 업데이트 중 오류: ${e.message}")
                false
            }
        }
    }

    /**
     * 상품을 삭제합니다.
     * 주의: 관련된 주문 데이터도 처리해야 할 수 있습니다.
     */
    suspend fun deleteProduct(id: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("_id", id)
                val result = productCollection.deleteOne(filter)
                result.deletedCount > 0
            } catch (e: Exception) {
                logger?.warning("상품 삭제 중 오류: ${e.message}")
                false
            }
        }
    }

    /**
     * 거래 가능한 모든 상품 목록을 조회합니다.
     */
    suspend fun findAllTradableProducts(): List<Product> {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("isTradable", true)
                val documents = productCollection.find(filter).toList()
                
                documents.mapNotNull { doc ->
                    try {
                        Product(
                            id = doc.getString("_id") ?: "",
                            name = doc.getString("name") ?: "",
                            categoryId = doc.getString("categoryId") ?: "",
                            description = doc.getString("description"),
                            isTradable = doc.getBoolean("isTradable", true),
                            itemMeta = doc.getString("itemMeta")
                        )
                    } catch (e: Exception) {
                        logger?.warning("상품 변환 중 오류: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                logger?.warning("거래 가능 상품 조회 중 오류: ${e.message}")
                emptyList()
            }
        }
    }
}