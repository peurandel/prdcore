package prd.peurandel.prdcore.Manager

import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.util.logging.Logger

class MongoProductGroupRepositoryImpl(
    private val database: CoroutineDatabase,
    private val logger: Logger? = null
) : ProductGroupRepository {
    
    private val productGroupCollection = database.getCollection<ProductGroup>("productGroups")

    override suspend fun findByCategory(categoryId: String): List<ProductGroup> {
        return try {
            logger?.info("카테고리 $categoryId 의 상품 그룹 조회")
            
            val documents = productGroupCollection.find(Document("categoryId", categoryId)).toList()
            
            val productGroups = documents.mapNotNull { doc ->
                try {
                    ProductGroup(
                        _id = doc._id,
                        name = doc.name,
                        categoryId = doc.categoryId,
                        material = doc.material,
                        description = doc.description,
                        row = doc.row,
                        products = doc.products,
                        displayOrder = doc.displayOrder
                    )
                } catch (e: Exception) {
                    logger?.warning("상품 그룹 Document 변환 실패: ${e.message}")
                    null
                }
            }.sortedBy { it.displayOrder }
            
            logger?.info("카테고리 $categoryId 의 상품 그룹 ${productGroups.size}개 조회 완료")
            productGroups
            
        } catch (e: Exception) {
            logger?.severe("카테고리별 상품 그룹 조회 실패: ${e.message}")
            emptyList()
        }
    }

    override suspend fun findById(id: String): ProductGroup? {
        return try {
            logger?.info("상품 그룹 ID $id 조회")
            
            val document = productGroupCollection.findOne(Document("_id", id))
            
            document?.let { doc ->
                ProductGroup(
                    _id = doc._id,
                    name = doc.name,
                    categoryId = doc.categoryId,
                    material = doc.material,
                    description = doc.description,
                    row = doc.row,
                    products = doc.products,
                    displayOrder = doc.displayOrder
                )
            }
            
        } catch (e: Exception) {
            logger?.severe("상품 그룹 조회 실패: ${e.message}")
            null
        }
    }

    override suspend fun findAll(): List<ProductGroup> {
        return try {
            logger?.info("모든 상품 그룹 조회")
            
            val documents = productGroupCollection.find().toList()
            
            val productGroups = documents.mapNotNull { doc ->
                try {
                    ProductGroup(
                        _id = doc._id,
                        name = doc.name,
                        categoryId = doc.categoryId,
                        material = doc.material,
                        description = doc.description,
                        row = doc.row,
                        products = doc.products,
                        displayOrder = doc.displayOrder
                    )
                } catch (e: Exception) {
                    logger?.warning("상품 그룹 Document 변환 실패: ${e.message}")
                    null
                }
            }.sortedBy { it.displayOrder }
            
            logger?.info("모든 상품 그룹 ${productGroups.size}개 조회 완료")
            productGroups
            
        } catch (e: Exception) {
            logger?.severe("모든 상품 그룹 조회 실패: ${e.message}")
            emptyList()
        }
    }
}