package prd.peurandel.prdcore.Gui.shop

import com.mongodb.client.MongoDatabase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.BaseGUI
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bson.Document
import org.bukkit.Bukkit
import prd.peurandel.prdcore.Manager.Category

/**
 * 바자회 물건 구매 GUI
 */
class BazaarShopGUI(plugin: JavaPlugin, private val database: MongoDatabase) : BaseGUI(plugin,"바자회 상점", 54) {

    companion object {
        // 카테고리 정보를 담는 클래스
        data class CategoryInfo(
            val category: Category,  // 카테고리 열거형
            val slotId: Int,         // 슬롯 위치
            val material: Material,   // 아이템 재질
            val displayName: String   // 표시 이름
        )
        enum class Category {
            FARMING, MINING, COMBAT, WOOD_FISH, ODDITIES
        }
        // 카테고리 정보 목록 - 여기만 수정하면 됩니다
        val CATEGORY_INFOS = listOf(
            CategoryInfo(Category.FARMING, 0, Material.NETHERITE_HOE, "농사"),
            CategoryInfo(Category.MINING, 9, Material.DIAMOND_PICKAXE, "광업"),
            CategoryInfo(Category.COMBAT, 18, Material.IRON_SWORD, "전투"),
            CategoryInfo(Category.WOOD_FISH, 27, Material.FISHING_ROD, "목재 & 낚시"),
            CategoryInfo(Category.ODDITIES, 36, Material.ENDER_EYE, "특수 아이템")
        )
        
        // 슬롯 ID로 카테고리 찾기
        fun getCategoryBySlot(slotId: Int): Category? {
            return CATEGORY_INFOS.find { it.slotId == slotId }?.category
        }
        
        // 카테고리로 슬롯 ID 찾기
        fun getSlotByCategory(category: Category): Int {
            return CATEGORY_INFOS.find { it.category == category }?.slotId ?: 0
        }
        
        // 카테고리로 정보 객체 찾기
        fun getInfoByCategory(category: Category): CategoryInfo {
            return CATEGORY_INFOS.find { it.category == category } 
                ?: CATEGORY_INFOS[0] // 기본값은 첫 번째 카테고리
        }
    }

    // 카테고리 관련 텍스트
    private val CATEGORY_LORE = Component.text("클릭하여 이 카테고리의 물품을 확인하세요", NamedTextColor.GRAY)
    private val CATEGORY_SELECTED_LORE = listOf(
        Component.text("이 카테고리의 물품들이 표시됩니다", NamedTextColor.GRAY),
        Component.text("▶ 선택됨", NamedTextColor.GREEN, TextDecoration.BOLD)
    )

    // 유리판 위치
    private val grassPaneIndexs = listOf(
        1, 2, 3, 4, 5, 6, 7, 8,
        10,  17,
        19,  26,
        28,  35,
        37,  44,
        46,  53,
    )

    // 현재 선택된 카테고리 (슬롯 ID가 아닌 Category 열거형으로 관리)
    private var selectedCategory: Category = Category.FARMING

    /**
     * 선택된 카테고리 설정 메서드
     * 다른 메서드 호출 전에 먼저 호출해야 합니다
     */
    fun setCategory(category: Category): BazaarShopGUI {
        this.selectedCategory = category
        return this
    }

    private fun getStainedGlassPane(): ItemStack {
        val item = ItemStack(Material.PINK_STAINED_GLASS_PANE)
        item.editMeta { meta ->
            meta.displayName(Component.text(" "))
        }
        return item
    }
    
    // 카테고리 아이템 생성
    private fun createCategoryItem(info: CategoryInfo): ItemStack {
        val item = ItemStack(info.material)
        item.editMeta { meta ->
            meta.displayName(Component.text(info.displayName, NamedTextColor.YELLOW))
            
            // 선택된 카테고리라면 인챈트 효과와 선택됨 표시 추가
            if (selectedCategory == info.category) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                meta.lore(CATEGORY_SELECTED_LORE)
            } else {
                meta.lore(listOf(CATEGORY_LORE))
            }
        }
        return item
    }

    // 정보 아이템 생성
    private fun createInfoItem(): ItemStack {
        val item = ItemStack(Material.BOOK)
        item.editMeta { meta ->
            meta.displayName(Component.text("바자 상점 정보", NamedTextColor.GOLD))
            meta.lore(listOf(
                Component.text("카테고리를 클릭하여 아이템을 확인하세요.", NamedTextColor.GRAY)
            ))
        }
        return item
    }

    // 뒤로 가기 버튼 생성
    private fun createBackButton(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(Component.text("뒤로 가기", NamedTextColor.RED))
        }
        return item
    }

    // GUI 초기화
    override fun initializeItems(plugin: JavaPlugin, player: String) {
        // 가장자리 유리판 배치
        for (i in grassPaneIndexs) {
            inventory.setItem(i, getStainedGlassPane())
        }

        // 카테고리 아이템 배치
        for (categoryInfo in CATEGORY_INFOS) {
            inventory.setItem(categoryInfo.slotId, createCategoryItem(categoryInfo))
        }

        // 기본 GUI 항목
        inventory.setItem(49, createInfoItem()) // 정보 아이템
        inventory.setItem(45, createBackButton()) // 뒤로 가기 버튼

        // 선택된 카테고리에 따른 상품 로드
        loadCategoryItems()
    }

    // 카테고리에 따른 상품 로드
    private fun loadCategoryItems() {
        try {
            val collection = database.getCollection("products")
            val products = collection.find(Document("category", selectedCategory.name))

            // 상품 슬롯 위치 계산 및 표시 (구현 필요)
            // 슬롯 11, 12, 13, 14, 15, 20, 21, 22, 23, 24 등에 상품 표시
            
        } catch (e: Exception) {
            plugin.logger.warning("상품 로드 중 오류 발생: ${e.message}")
        }
    }

    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return
        
        val clickedSlot = event.slot
        
        // 카테고리 아이템 클릭 처리
        val categoryFromSlot = getCategoryBySlot(clickedSlot)
        if (categoryFromSlot != null) {
            handleCategoryClick(clickedSlot, player)
            return
        }
        
        // 나머지 클릭 처리
        handleOtherClick(clickedItem, player, clickedSlot)
    }

    // 카테고리 클릭 처리
    private fun handleCategoryClick(clickedSlot: Int, player: Player) {
        // 카테고리 슬롯 ID에 따른 카테고리 결정
        val newCategory = getCategoryBySlot(clickedSlot) ?: return

        // 이미 선택된 카테고리를 다시 클릭했으면 무시
        if (selectedCategory == newCategory) {
            return
        }
        
        // 카테고리 변경
        selectedCategory = newCategory
        
        // 플레이어 인벤토리 닫기
        player.closeInventory()
        open(plugin, player)
    }

    // 다른 아이템 클릭 처리
    private fun handleOtherClick(clickedItem: ItemStack, player: Player, clickedSlot: Int) {
        when(clickedSlot) {
            45 -> {
                // 뒤로 가기 버튼
                player.closeInventory()
                // 필요하면 이전 GUI로 이동 로직 추가
            }
            // 기타 버튼 처리...
        }
    }
}
