package prd.peurandel.prdcore.Manager

import com.mongodb.client.ClientSession
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.util.logging.Logger

class MongoCategoryRepositoryImpl(
    private val database: CoroutineDatabase,
    private val logger: Logger? = null
) : CategoryRepository {

    private val categoryCollection = database.getCollection<Document>("categories")

    override suspend fun findRootCategories(): List<Category> {
        return try {
            logger?.info("루트 카테고리 조회 시작")
            
            // 전체 카테고리 수 확인
            val totalCount = categoryCollection.countDocuments()
            logger?.info("전체 카테고리 수: $totalCount")
            
            // parentCategoryId가 null인 모든 카테고리 조회 (Document로 조회)
            val documents = categoryCollection.find(Document("parentCategoryId", null)).toList()
            logger?.info("조회된 Document 수: ${documents.size}")
            
            // Document를 Category 객체로 변환
            val rootCategories = documents.mapNotNull { doc ->
                try {
                    Category(
                        id = doc.getString("_id") ?: "",
                        name = doc.getString("name") ?: "",
                        material = doc.getString("material") ?: "STONE",
                        glass_color = doc.getString("glass_color"),
                        parentCategoryId = doc.getString("parentCategoryId")
                    )
                } catch (e: Exception) {
                    logger?.warning("Category Document 변환 실패: ${e.message}")
                    null
                }
            }
            
            logger?.info("루트 카테고리 조회 결과: ${rootCategories.size}개")
            rootCategories
            
        } catch (e: Exception) {
            logger?.warning("루트 카테고리 조회 중 오류 발생: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun findById(id: String): Category? {
        return try {
            val document = categoryCollection.findOne(Document("_id", id))
            document?.let { doc ->
                Category(
                    id = doc.getString("_id") ?: "",
                    name = doc.getString("name") ?: "",
                    material = doc.getString("material") ?: "STONE",
                    glass_color = doc.getString("glass_color"),
                    parentCategoryId = doc.getString("parentCategoryId")
                )
            }
        } catch (e: Exception) {
            logger?.warning("카테고리 조회 실패 (id: $id): ${e.message}")
            null
        }
    }

    suspend fun findByParentId(parentId: String, session: ClientSession? = null): List<Category> {
        return try {
            val documents = categoryCollection.find(Document("parentCategoryId", parentId)).toList()
            documents.mapNotNull { doc ->
                try {
                    Category(
                        id = doc.getString("_id") ?: "",
                        name = doc.getString("name") ?: "",
                        material = doc.getString("material") ?: "STONE",
                        glass_color = doc.getString("glass_color"),
                        parentCategoryId = doc.getString("parentCategoryId")
                    )
                } catch (e: Exception) {
                    logger?.warning("Category Document 변환 실패: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger?.warning("부모 카테고리 ID로 조회 중 오류 발생: ${e.message}")
            emptyList()
        }
    }

    suspend fun saveCategory(category: Category, session: ClientSession? = null): Boolean {
        return try {
            val document = Document("_id", category.id)
                .append("name", category.name)
                .append("parentCategoryId", category.parentCategoryId)
            categoryCollection.save(document)
            true
        } catch (e: Exception) {
            logger?.warning("카테고리 저장 중 오류 발생: ${e.message}")
            false
        }
    }

    suspend fun updateCategory(category: Category, session: ClientSession? = null): Boolean {
        return try {
            val document = Document("_id", category.id)
                .append("name", category.name)
                .append("parentCategoryId", category.parentCategoryId)
            categoryCollection.updateOne(Document("_id", category.id), document)
            true
        } catch (e: Exception) {
            logger?.warning("카테고리 업데이트 중 오류 발생: ${e.message}")
            false
        }
    }

    suspend fun deleteCategory(id: String, session: ClientSession? = null): Boolean {
        return try {
            categoryCollection.deleteOne(Document("_id", id))
            true
        } catch (e: Exception) {
            logger?.warning("카테고리 삭제 중 오류 발생: ${e.message}")
            false
        }
    }

    override suspend fun findAll(): List<Category> {
        return try {
            val documents = categoryCollection.find().toList()
            documents.mapNotNull { doc ->
                try {
                    Category(
                        id = doc.getString("_id") ?: "",
                        name = doc.getString("name") ?: "",
                        material = doc.getString("material") ?: "STONE",
                        glass_color = doc.getString("glass_color"),
                        parentCategoryId = doc.getString("parentCategoryId")
                    )
                } catch (e: Exception) {
                    logger?.warning("Category Document 변환 실패: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger?.warning("모든 카테고리 조회 실패: ${e.message}")
            emptyList()
        }
    }

}