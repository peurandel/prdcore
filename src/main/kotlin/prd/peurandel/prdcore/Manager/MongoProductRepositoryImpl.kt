package prd.peurandel.prdcore.Manager

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import org.litote.kmongo.regex
import java.util.logging.Logger

/**
 * MongoDB를 사용한 상품 저장소 구현체
 */
class MongoProductRepositoryImpl(
    private val database: CoroutineDatabase,
    private val logger: Logger? = null,
    private val scope: CoroutineScope // CoroutineScope 주입 받기
) : ProductRepository {

    private val productCollection: CoroutineCollection<Product> = database.getCollection("products")

    init {
        // 코루틴을 시작하여 비동기적으로 인덱스 생성
        scope.launch {
            try {
                logger?.info("상품 컬렉션 인덱스 생성 시도...")
                // 코루틴 내에서 suspend 함수 호출 가능
                productCollection.createIndex(Indexes.ascending(Product::categoryId.name))
                productCollection.createIndex(Indexes.text(Product::name.name)) // 복합 텍스트 인덱스 등 고려
                logger?.info("상품 컬렉션 인덱스 생성 완료.")
            } catch (e: Exception) {
                // onFailure와 유사하게 오류 처리
                logger?.warning("상품 컬렉션 인덱스 생성 중 오류: ${e.message}")
            }
        }
    }

    /**
     * 특정 카테고리에 속한 모든 상품을 조회합니다.
     */
    override suspend fun findByCategory(categoryId: String): List<Product> {
        return try {
            productCollection.find(Product::categoryId eq categoryId).toList()
        } catch (e: Exception) {
            logger?.warning("카테고리별 상품 조회 중 오류: ${e.message}")
            emptyList()
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
        return try {
            // 정규식을 사용한 부분 문자열 검색 (대소문자 무시)
            val filter = Filters.or(
                Product::name.regex(query, "i"),
                Product::description.regex(query, "i")
            )
            productCollection.find(filter).toList()
        } catch (e: Exception) {
            logger?.warning("상품명 검색 중 오류: ${e.message}")
            emptyList()
        }
    }

    /**
     * ID로 특정 상품을 조회합니다.
     */
    override suspend fun findById(id: String): Product? {
        return try {
            productCollection.findOneById(id)
        } catch (e: Exception) {
            logger?.warning("상품 ID 조회 중 오류: ${e.message}")
            null
        }
    }

    /**
     * 새 상품을 저장합니다.
     */
    suspend fun saveProduct(product: Product, session: ClientSession? = null): Boolean {
        return try {
            if (session != null) {
                productCollection.save(session, product)!!.wasAcknowledged()
            } else {
                productCollection.save(product)!!.wasAcknowledged()
            }
        } catch (e: Exception) {
            logger?.warning("상품 저장 중 오류: ${e.message}")
            false
        }
    }

    /**
     * 상품 정보를 업데이트합니다.
     */
    suspend fun updateProduct(product: Product, session: ClientSession? = null): Boolean {
        return try {
            if (session != null) {
                productCollection.updateOne(session, Product::_id eq product._id, product).modifiedCount > 0
            } else {
                productCollection.updateOne(Product::_id eq product._id, product).modifiedCount > 0
            }
        } catch (e: Exception) {
            logger?.warning("상품 업데이트 중 오류: ${e.message}")
            false
        }
    }

    /**
     * 상품의 거래 가능 상태를 변경합니다.
     */
    suspend fun updateTradableStatus(productId: String, isTradable: Boolean, session: ClientSession? = null): Boolean {
        return try {
            val update = com.mongodb.client.model.Updates.set(Product::isTradable.name, isTradable)

            if (session != null) {
                productCollection.updateOneById(session, productId, update).modifiedCount > 0
            } else {
                productCollection.updateOneById(productId, update).modifiedCount > 0
            }
        } catch (e: Exception) {
            logger?.warning("상품 거래 상태 업데이트 중 오류: ${e.message}")
            false
        }
    }

    /**
     * 상품을 삭제합니다.
     * 주의: 관련된 주문 데이터도 처리해야 할 수 있습니다.
     */
    suspend fun deleteProduct(id: String, session: ClientSession? = null): Boolean {
        return try {
            if (session != null) {
                productCollection.deleteOneById(session, id).deletedCount > 0
            } else {
                productCollection.deleteOneById(id).deletedCount > 0
            }
        } catch (e: Exception) {
            logger?.warning("상품 삭제 중 오류: ${e.message}")
            false
        }
    }

    /**
     * 거래 가능한 모든 상품 목록을 조회합니다.
     */
    suspend fun findAllTradableProducts(session: ClientSession? = null): List<Product> {
        return try {
            if (session != null) {
                productCollection.find(session, Product::isTradable eq true).toList()
            } else {
                productCollection.find(Product::isTradable eq true).toList()
            }
        } catch (e: Exception) {
            logger?.warning("거래 가능 상품 조회 중 오류: ${e.message}")
            emptyList()
        }
    }
}