package prd.peurandel.prdcore.Manager

import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.ClientSession
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import java.util.logging.Logger

/**
 * MongoDB를 사용한 카테고리 저장소 구현체
 */
class MongoCategoryRepositoryImpl(
    private val database: CoroutineDatabase,
    private val logger: Logger? = null
) : CategoryRepository {

    private val categoryCollection: CoroutineCollection<Category> = database.getCollection("categories")

    /**
     * 루트 카테고리(부모가 없는 카테고리)들을 모두 찾습니다.
     */
    override suspend fun findRootCategories(): List<Category> {
        return try {
            // parentCategoryId가 null인 모든 카테고리 조회
            categoryCollection.find(Category::parentCategoryId eq null).toList()
        } catch (e: Exception) {
            logger?.warning("루트 카테고리 조회 중 오류 발생: ${e.message}")
            emptyList()
        }
    }

    /**
     * ID로 특정 카테고리를 찾습니다.
     */
    override suspend fun findById(id: String): Category? {
        return try {
            categoryCollection.findOneById(id)
        } catch (e: Exception) {
            logger?.warning("카테고리 ID 조회 중 오류 발생: ${e.message}")
            null
        }
    }

    /**
     * 특정 부모 카테고리에 속한 모든 하위 카테고리를 찾습니다.
     */
    suspend fun findByParentId(parentId: String, session: ClientSession? = null): List<Category> {
        return try {
            if (session != null) {
                categoryCollection.find(session, Category::parentCategoryId eq parentId).toList()
            } else {
                categoryCollection.find(Category::parentCategoryId eq parentId).toList()
            }
        } catch (e: Exception) {
            logger?.warning("부모 카테고리 ID로 조회 중 오류 발생: ${e.message}")
            emptyList()
        }
    }

    /**
     * 새 카테고리를 저장합니다.
     */
    suspend fun saveCategory(category: Category, session: ClientSession? = null): Boolean {
        return try {
            if (session != null) {
                categoryCollection.save(session, category)!!.wasAcknowledged()
            } else {
                categoryCollection.save(category)!!.wasAcknowledged()
            }
        } catch (e: Exception) {
            logger?.warning("카테고리 저장 중 오류 발생: ${e.message}")
            false
        }
    }

    /**
     * 카테고리를 업데이트합니다.
     */
    suspend fun updateCategory(category: Category, session: ClientSession? = null): Boolean {
        return try {
            if (session != null) {
                categoryCollection.updateOne(session, Category::id eq category.id, category).modifiedCount > 0
            } else {
                categoryCollection.updateOne(Category::id eq category.id, category).modifiedCount > 0
            }
        } catch (e: Exception) {
            logger?.warning("카테고리 업데이트 중 오류 발생: ${e.message}")
            false
        }
    }

    /**
     * 카테고리를 삭제합니다.
     * 주의: 하위 카테고리와 관련 제품도 함께 처리해야 할 수 있습니다.
     */
    suspend fun deleteCategory(id: String, session: ClientSession? = null): Boolean {
        return try {
            if (session != null) {
                categoryCollection.deleteOneById(session, id).deletedCount > 0
            } else {
                categoryCollection.deleteOneById(id).deletedCount > 0
            }
        } catch (e: Exception) {
            logger?.warning("카테고리 삭제 중 오류 발생: ${e.message}")
            false
        }
    }
}