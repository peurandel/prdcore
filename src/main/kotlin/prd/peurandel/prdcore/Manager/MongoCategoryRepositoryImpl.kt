package prd.peurandel.prdcore.Manager

import com.mongodb.client.ClientSession
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId
import java.util.logging.Logger

/**
 * MongoDB를 사용한 카테고리 저장소 구현체
 */
class MongoCategoryRepositoryImpl(
    private val database: MongoDatabase,
    private val logger: Logger? = null
) : CategoryRepository {

    private val categoryCollection: MongoCollection<Document> = database.getCollection("categories")

    init {
        // 인덱스 생성
        try {
            categoryCollection.createIndex(Document("parentCategoryId", 1))
            logger?.info("Category 컬렉션 인덱스가 성공적으로 생성되었습니다.")
        } catch (e: Exception) {
            logger?.warning("Category 컬렉션 인덱스 생성 중 오류 발생: ${e.message}")
        }
    }

    /**
     * 루트 카테고리(부모가 없는 카테고리)들을 모두 찾습니다.
     */
    override suspend fun findRootCategories(): List<Category> {
        return withContext(Dispatchers.IO) {
            try {
                // parentCategoryId가 null인 모든 카테고리 조회
                val categories = mutableListOf<Category>()
                categoryCollection.find(Filters.eq("parentCategoryId", null))
                    .forEach { doc ->
                        val category = documentToCategory(doc)
                        if (category != null) {
                            categories.add(category)
                        }
                    }
                
                logger?.info("루트 카테고리 ${categories.size}개를 조회했습니다.")
                categories
            } catch (e: Exception) {
                logger?.warning("루트 카테고리 조회 중 오류 발생: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * ID로 특정 카테고리를 찾습니다.
     */
    override suspend fun findById(id: String): Category? {
        return withContext(Dispatchers.IO) {
            try {
                val result = categoryCollection.find(Filters.eq("_id", id)).first()
                val category = result?.let { documentToCategory(it) }
                if (category != null) {
                    logger?.info("카테고리를 ID로 찾았습니다: $id")
                } else {
                    logger?.info("카테고리를 찾을 수 없습니다: $id")
                }
                category
            } catch (e: Exception) {
                logger?.warning("카테고리 ID 조회 중 오류 발생: ${e.message}")
                null
            }
        }
    }

    /**
     * 특정 부모 카테고리에 속한 모든 하위 카테고리를 찾습니다.
     */
    suspend fun findByParentId(parentId: String, session: ClientSession? = null): List<Category> {
        return withContext(Dispatchers.IO) {
            try {
                val categories = mutableListOf<Category>()
                val filter = Filters.eq("parentCategoryId", parentId)
                
                if (session != null) {
                    categoryCollection.find(session, filter)
                        .forEach { doc ->
                            val category = documentToCategory(doc)
                            if (category != null) {
                                categories.add(category)
                            }
                        }
                } else {
                    categoryCollection.find(filter)
                        .forEach { doc ->
                            val category = documentToCategory(doc)
                            if (category != null) {
                                categories.add(category)
                            }
                        }
                }
                
                logger?.info("부모 카테고리($parentId) 하위의 카테고리 ${categories.size}개를 조회했습니다.")
                categories
            } catch (e: Exception) {
                logger?.warning("부모 카테고리 ID로 조회 중 오류 발생: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * 새 카테고리를 저장합니다.
     */
    suspend fun saveCategory(category: Category, session: ClientSession? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = categoryToDocument(category)
                val result = if (session != null) {
                    categoryCollection.insertOne(session, doc)
                } else {
                    categoryCollection.insertOne(doc)
                }
                
                val success = result.wasAcknowledged()
                if (success) {
                    logger?.info("카테고리가 성공적으로 저장되었습니다: ${category.id}")
                }
                success
            } catch (e: Exception) {
                logger?.warning("카테고리 저장 중 오류 발생: ${e.message}")
                false
            }
        }
    }

    /**
     * 카테고리를 업데이트합니다.
     */
    suspend fun updateCategory(category: Category, session: ClientSession? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = categoryToDocument(category)
                val filter = Filters.eq("_id", category.id)
                
                val result = if (session != null) {
                    categoryCollection.replaceOne(session, filter, doc)
                } else {
                    categoryCollection.replaceOne(filter, doc)
                }
                
                val success = result.modifiedCount > 0
                if (success) {
                    logger?.info("카테고리가 성공적으로 업데이트되었습니다: ${category.id}")
                }
                success
            } catch (e: Exception) {
                logger?.warning("카테고리 업데이트 중 오류 발생: ${e.message}")
                false
            }
        }
    }

    /**
     * 카테고리를 삭제합니다.
     * 주의: 하위 카테고리와 관련 제품도 함께 처리해야 할 수 있습니다.
     */
    suspend fun deleteCategory(id: String, session: ClientSession? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filter = Filters.eq("_id", id)
                
                val result = if (session != null) {
                    categoryCollection.deleteOne(session, filter)
                } else {
                    categoryCollection.deleteOne(filter)
                }
                
                val success = result.deletedCount > 0
                if (success) {
                    logger?.info("카테고리가 성공적으로 삭제되었습니다: $id")
                } else {
                    logger?.warning("카테고리 삭제 실패: $id - 해당 ID를 가진 카테고리가 없습니다.")
                }
                success
            } catch (e: Exception) {
                logger?.warning("카테고리 삭제 중 오류 발생: ${e.message}")
                false
            }
        }
    }

    /**
     * Category 객체를 MongoDB Document로 변환합니다.
     */
    private fun categoryToDocument(category: Category): Document {
        return Document()
            .append("_id", category.id)
            .append("name", category.name)
            .append("parentCategoryId", category.parentCategoryId)
    }

    /**
     * MongoDB Document를 Category 객체로 변환합니다.
     */
    private fun documentToCategory(doc: Document): Category? {
        return try {
            val id = if (doc.get("_id") is String) {
                doc.getString("_id")
            } else {
                doc.getObjectId("_id").toString()
            }
            
            Category(
                id = id,
                name = doc.getString("name"),
                parentCategoryId = doc.getString("parentCategoryId")
            )
        } catch (e: Exception) {
            logger?.warning("Category 문서 변환 중 오류 발생: ${e.message}, 문서: $doc")
            null
        }
    }
}