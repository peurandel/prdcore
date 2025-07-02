package prd.peurandel.prdcore.Gui.shop

import com.mongodb.client.MongoDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.BaseGUI
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataType
import prd.peurandel.prdcore.Manager.BazaarAPI
import prd.peurandel.prdcore.Manager.Category

/**
 * 바자회 물건 구매 GUI
 */
class FakeGUI(plugin: JavaPlugin,private val bazaarAPI: BazaarAPI, private val database: MongoDatabase) : BaseGUI(plugin,"바자", 54) {

    companion object {
        // 카테고리 슬롯 위치 (고정)
        val categorySlots = listOf(0, 9, 18, 27, 36)

        // 상품 그룹 표시 슬롯
        val productGroupSlots = listOf(11, 12, 13, 14, 15, 16,
            20, 21, 22, 23, 24, 25,
            29, 30, 31, 32, 33, 34,
            38, 39, 40, 41, 42, 43)

        // 유리판 위치
        private val grassPaneIndexs = listOf(
            1, 2, 3, 4, 5, 6, 7, 8,
            10,  17,
            19,  26,
            28,  35,
            37,  44,
            46,  53,
        )

        // 카테고리 ID별 기본 Material 매핑 (fallback용)
        private val categoryMaterials = mapOf(
            "farming" to Material.NETHERITE_HOE,
            "mining" to Material.DIAMOND_PICKAXE,
            "looting" to Material.IRON_SWORD,
            "wood" to Material.FISHING_ROD,
            "oddities" to Material.ENDER_EYE
        )

        // 카테고리 ID별 한글 이름 매핑
        private val categoryNames = mapOf(
            "farming" to "농업",
            "mining" to "채굴",
            "looting" to "전투",
            "wood" to "목재/낚시",
            "oddities" to "기타"
        )

        // 카테고리 ID별 유리판 색상 매핑
        private val categoryGlassColors = mapOf(
            "farming" to "GREEN_STAINED_GLASS_PANE",
            "mining" to "GRAY_STAINED_GLASS_PANE",
            "looting" to "RED_STAINED_GLASS_PANE",
            "wood" to "BROWN_STAINED_GLASS_PANE",
            "oddities" to "PURPLE_STAINED_GLASS_PANE"
        )

        // Lore 상수들
        private val CATEGORY_LORE = Component.text("클릭하여 카테고리를 선택하세요", NamedTextColor.GRAY)
        private val CATEGORY_SELECTED_LORE = listOf(
            Component.text("현재 선택된 카테고리", NamedTextColor.GREEN),
            Component.text("클릭하여 다른 카테고리를 선택하세요", NamedTextColor.GRAY)
        )
        // 카테고리 정보를 담는 클래스
        data class CategoryInfo(
            val category: Category,  // 카테고리 열거형
            val slotId: Int,         // 슬롯 위치
            val material: Material,   // 아이템 재질
            val displayName: String   // 표시 이름
        )

        data class ProductGUIInfo(
            val productId: String,
            val size: Int,
            val material: Material,
            val productList: List<ProductInfo>
        )
        data class ProductInfo(
            val productId: String,
            val guiSlot: Int,
            val itemstack: ItemStack
        )
        enum class Category {
            FARMING, MINING, LOOTING, WOOD, ODDITIES
        }
        // 제품 GUI 정보를 담는 HashMap - O(1) 조회를 위해 맵으로 정의
        private val PRODUCT_GUI_INFOS = hashMapOf<String, ProductGUIInfo>()

        fun getCategory(category: String): Category {
            return Category.valueOf(category)
        }
    }

    // 동적 데이터
    private var categories: List<prd.peurandel.prdcore.Manager.CategoryInfo> = emptyList()
    private var currentProductGroups: List<prd.peurandel.prdcore.Manager.ProductGroupInfo> = emptyList()
    private var selectedCategoryId: String? = null
    private var guiState: GUIState = GUIState.CATEGORY_VIEW

    enum class GUIState {
        CATEGORY_VIEW,      // 카테고리 선택 상태
        PRODUCT_GROUP_VIEW, // 상품 그룹 표시 상태
        PRODUCT_VIEW        // 개별 상품 표시 상태
    }

    init {
        // GUI 생성 시 카테고리 데이터 로드
        GlobalScope.launch {
            try {
                categories = bazaarAPI.getRootCategories()
                plugin.logger.info("카테고리 데이터 로드 완료: ${categories.size}개")

                // 카테고리 로드 완료 후 GUI 업데이트
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    setupCategoryItems()
                })
            } catch (e: Exception) {
                plugin.logger.severe("카테고리 데이터 로드 실패: ${e.message}")
            }
        }
    }

    // 카테고리 아이템들을 GUI에 배치
    private fun setupCategoryItems() {
        // 카테고리 아이템 배치
        for (i in categories.indices.take(categorySlots.size)) {
            val category = categories[i]
            val categoryItem = createCategoryItem(category)
            inventory.setItem(categorySlots[i], categoryItem)
        }

        // 선택된 카테고리에 따라 유리판 색상 업데이트
        val glassColor = categoryGlassColors[selectedCategoryId] ?: "YELLOW_STAINED_GLASS_PANE"
        setStainedGlassPane(inventory, glassColor)
    }



    // 카테고리 아이템 생성
    private fun createCategoryItem(info: prd.peurandel.prdcore.Manager.CategoryInfo): ItemStack {
        val slotIndex = categories.indexOf(info)
        val slotId = if (slotIndex < categorySlots.size) categorySlots[slotIndex] else 0


        val material = categoryMaterials[info.id] ?: Material.STONE
        val displayName = categoryNames[info.id] ?: info.name


        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(Component.text(displayName, NamedTextColor.YELLOW))
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "button"),
                PersistentDataType.STRING,
                "category"
            )
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "category"),
                PersistentDataType.STRING,
                info.id
            )
            // 선택된 카테고리라면 인챈트 효과와 선택됨 표시 추가
            if (selectedCategoryId == info.id) {
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
    private fun createCloseButton(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(Component.text("닫기", NamedTextColor.RED))
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "button"),
                PersistentDataType.STRING,
                "close"
            )
        }
        return item
    }

    private fun createProductItem(itemStack: ItemStack,productId: String): ItemStack {
        val item = itemStack.clone()
        item.editMeta { meta ->
            meta.displayName(Component.text(productId, NamedTextColor.YELLOW))
            meta.lore(listOf(
                Component.text("상품 ID: $productId", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("클릭하여 상세 정보 확인", NamedTextColor.GREEN)
            ))
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "button"),
                PersistentDataType.STRING,
                "product"
            )
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "product"),
                PersistentDataType.STRING,
                productId
            )
        }
        // TODO: 바자 상점에 대한 지난날의 거래 데이터들을 표시

        return item
    }

    private fun getStainedGlassPane(color: String): ItemStack {
        val item = ItemStack(Material.valueOf(color))
        item.editMeta { meta ->
            meta.displayName(Component.text(" "))
        }
        return item
    }

    private fun setStainedGlassPane(inventory: Inventory, color: String) {
        for (i in grassPaneIndexs) {
            val item = ItemStack(Material.valueOf(color))
            item.editMeta { meta ->
                meta.displayName(Component.text(" "))
            }
            inventory.setItem(i, getStainedGlassPane(color))
        }
    }

    // GUI 초기화
    override fun initializeItems(plugin: JavaPlugin, player: String) {
        // 가장자리 유리판 배치
        setStainedGlassPane(inventory,categoryGlassColors[selectedCategoryId] ?: "YELLOW_STAINED_GLASS_PANE")

        // 기본 GUI 항목
        inventory.setItem(50, createInfoItem()) // 정보 아이템
        inventory.setItem(49, createCloseButton()) // 뒤로 가기 버튼

        // 선택된 카테고리에 따른 상품 로드
        loadCategoryItems()
    }

    // 카테고리에 따른 상품 로드
    private fun loadCategoryItems() {
        // 상품 그룹 슬롯 초기화
        val productGroupSlots = listOf(11, 12, 13, 20, 21, 22, 29, 30, 31)
        for (slot in productGroupSlots) {
            inventory.setItem(slot, null)
        }

        // GUI 상태에 따라 다른 아이템 표시
        when (guiState) {
            GUIState.CATEGORY_VIEW -> {
                // 카테고리 뷰에서는 아무것도 추가로 표시하지 않음
                // setupCategoryItems()에서 이미 카테고리들이 배치됨
            }
            GUIState.PRODUCT_GROUP_VIEW -> {
                // 상품 그룹 뷰에서는 setupProductGroupItems()에서 처리됨
                setupProductGroupItems()
            }
            GUIState.PRODUCT_VIEW -> {
                // 개별 상품 뷰는 별도 GUI에서 처리됨
            }
        }
    }

    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return

        // 나머지 클릭 처리
        val itemType = clickedItem.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, "button"), PersistentDataType.STRING) ?: return
        when(itemType) {
            "category" -> {
                val category = clickedItem.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, "category"), PersistentDataType.STRING) ?: return
                handleCategoryClick(category, player)
            }
            "product" -> {
                handleProductClick(clickedItem, player)

            }
            "close"-> {
                player.closeInventory()
            }
            "back_to_categories" -> {
                selectedCategoryId = null
                guiState = GUIState.CATEGORY_VIEW
                currentProductGroups = emptyList()
                setupCategoryItems()
            }
            "back_to_product_groups" -> {
                guiState = GUIState.PRODUCT_GROUP_VIEW
                setupProductGroupItems()
            }
        }
    }

    // 상품 아이템 클릭 처리
    private fun handleProductClick(clickedItem: ItemStack, player: Player) {
        try {
            val productGroupId = clickedItem.itemMeta.persistentDataContainer.get(
                NamespacedKey(plugin, "product_group_id"),
                PersistentDataType.STRING
            ) ?: return

            val productGroupName = clickedItem.itemMeta.persistentDataContainer.get(
                NamespacedKey(plugin, "product_group_name"),
                PersistentDataType.STRING
            ) ?: productGroupId

            plugin.logger.info("상품 그룹 클릭: $productGroupId ($productGroupName)")

            // 상품 그룹에 속한 상품들을 비동기로 로드
            GlobalScope.launch {
                try {
                    val products = bazaarAPI.getProductsByProductGroup(productGroupId)
                    plugin.logger.info("상품 그룹 '$productGroupId'의 상품 로드 완료: ${products.size}개")

                    // UI 업데이트는 메인 스레드에서
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (products.isEmpty()) {
                            player.sendMessage(Component.text("${ChatColor.YELLOW}해당 상품 그룹에 상품이 없습니다."))
                        } else {
                            openProductsGUI(player, productGroupName, products)
                        }
                    })
                } catch (e: Exception) {
                    plugin.logger.severe("상품 그룹 '$productGroupId' 로드 중 오류: ${e.message}")
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        player.sendMessage(Component.text("${ChatColor.RED}상품 로드 중 오류가 발생했습니다."))
                    })
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("상품 클릭 처리 중 오류: ${e.message}")
            player.sendMessage(Component.text("${ChatColor.RED}상품 클릭 처리 중 오류가 발생했습니다."))
        }
    }

    // 상품 목록 GUI 열기
    private fun openProductsGUI(player: Player, productGroupName: String, products: List<prd.peurandel.prdcore.Manager.ProductInfo>) {
        val inventorySize = 54
        val productInventory = Bukkit.createInventory(null, inventorySize, "바자 → $productGroupName")

        // 배경 유리판 설정
        for (i in 0 until inventorySize) {
            productInventory.setItem(i, getStainedGlassPane("GRAY_STAINED_GLASS_PANE"))
        }

        // 상품 아이템들을 슬롯에 배치 (최대 45개, 마지막 줄은 버튼용)
        val maxProducts = 45
        for (i in products.indices) {
            if (i >= maxProducts) break

            val product = products[i]
            val productItem = createProductItemForList(product)
            productInventory.setItem(i, productItem)
        }

        // 뒤로 가기 버튼
        val backButton = ItemStack(Material.ARROW)
        val backMeta = backButton.itemMeta
        backMeta.displayName(Component.text("${ChatColor.YELLOW}뒤로 가기"))
        backMeta.lore(listOf(Component.text("${ChatColor.GRAY}상품 그룹 목록으로 돌아갑니다")))
        backMeta.persistentDataContainer.set(NamespacedKey(plugin, "button"), PersistentDataType.STRING, "back_to_product_groups")
        backButton.itemMeta = backMeta
        productInventory.setItem(48, backButton)

        // 닫기 버튼
        val closeButton = ItemStack(Material.BARRIER)
        val closeMeta = closeButton.itemMeta
        closeMeta.displayName(Component.text("${ChatColor.RED}닫기"))
        closeMeta.persistentDataContainer.set(NamespacedKey(plugin, "button"), PersistentDataType.STRING, "close")
        closeButton.itemMeta = closeMeta
        productInventory.setItem(49, closeButton)

        // 이벤트 리스너 등록
        val productListener = object : Listener {
            @EventHandler
            fun onInventoryClick(event: InventoryClickEvent) {
                if (event.inventory != productInventory) return
                if (event.whoClicked.uniqueId != player.uniqueId) return

                event.isCancelled = true
                val item = event.currentItem ?: return
                val itemType = item.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, "button"), PersistentDataType.STRING)

                when(itemType) {
                    "individual_product" -> {
                        val productId = item.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, "product_id"), PersistentDataType.STRING) ?: return
                        val productName = item.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, "product_name"), PersistentDataType.STRING) ?: productId

                        openProductGUI(player, productGroupName, productId) { quantity ->
                            GlobalScope.launch {
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    open(plugin, player)
                                })
                            }
                        }
                        HandlerList.unregisterAll(this)
                    }
                    "back_to_product_groups" -> {
                        event.whoClicked.closeInventory()
                        HandlerList.unregisterAll(this)
                        // 상품 그룹 뷰로 되돌아가기
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            setupProductGroupItems()
                            player.openInventory(inventory)
                        })
                    }
                    "close" -> {
                        event.whoClicked.closeInventory()
                        HandlerList.unregisterAll(this)
                    }
                }
            }

            @EventHandler
            fun onInventoryClose(event: InventoryCloseEvent) {
                if (event.inventory == productInventory && event.player == player) {
                    HandlerList.unregisterAll(this)
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(productListener, plugin)
        player.openInventory(productInventory)
    }

    // 상품 목록용 아이템 생성
    private fun createProductItemForList(product: prd.peurandel.prdcore.Manager.ProductInfo): ItemStack {
        val material = try {
            Material.valueOf(product.id)
        } catch (e: IllegalArgumentException) {
            Material.STONE
        }

        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(Component.text("${ChatColor.GREEN}${product.name}"))
        meta.lore(listOf(
            Component.text("${ChatColor.GRAY}ID: ${product.id}"),
            Component.text("${ChatColor.YELLOW}클릭하여 거래하기"),
            Component.empty()
        ))

        meta.persistentDataContainer.set(NamespacedKey(plugin, "button"), PersistentDataType.STRING, "individual_product")
        meta.persistentDataContainer.set(NamespacedKey(plugin, "product_id"), PersistentDataType.STRING, product.id)
        meta.persistentDataContainer.set(NamespacedKey(plugin, "product_name"), PersistentDataType.STRING, product.name)

        item.itemMeta = meta
        return item
    }

    // 카테고리 클릭 처리
    private fun handleCategoryClick(category: String, player: Player) {
        // 이미 선택된 카테고리를 다시 클릭했으면 카테고리 선택 해제
        if (selectedCategoryId == category) {
            selectedCategoryId = null
            guiState = GUIState.CATEGORY_VIEW
            currentProductGroups = emptyList()
            setupCategoryItems()
            return
        }

        // 카테고리 변경
        selectedCategoryId = category
        guiState = GUIState.PRODUCT_GROUP_VIEW

        plugin.logger.info("카테고리 '$category' 선택됨")

        // 선택된 카테고리의 상품 그룹들을 비동기로 로드
        GlobalScope.launch {
            try {
                currentProductGroups = bazaarAPI.getProductGroupsByCategory(category)
                plugin.logger.info("카테고리 '$category'의 상품 그룹 로드 완료: ${currentProductGroups.size}개")

                // UI 업데이트는 메인 스레드에서
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    setupProductGroupItems()
                })
            } catch (e: Exception) {
                plugin.logger.severe("카테고리 '$category' 상품 그룹 로드 중 오류: ${e.message}")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage(Component.text("${ChatColor.RED}상품 그룹 로드 중 오류가 발생했습니다."))
                    // 오류 시 카테고리 뷰로 되돌리기
                    selectedCategoryId = null
                    guiState = GUIState.CATEGORY_VIEW
                    setupCategoryItems()
                })
            }
        }
    }

    // 상품 그룹 아이템들을 GUI에 배치
    private fun setupProductGroupItems() {
        // 인벤토리 초기화
        for (i in 0..53) {
            inventory.setItem(i, null)
        }

        // 배경 유리판 설정
        val glassColor = categoryGlassColors[selectedCategoryId] ?: "YELLOW_STAINED_GLASS_PANE"
        setStainedGlassPane(inventory, glassColor)

        // 상품 그룹 아이템 배치 (3x3 그리드 중앙 부분 사용)
        val productGroupSlots = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42)

        for (i in currentProductGroups.indices.take(productGroupSlots.size)) {
            val productGroup = currentProductGroups[i]
            val productGroupItem = createProductGroupItem(productGroup)
            inventory.setItem(productGroupSlots[i], productGroupItem)
        }

        // 뒤로 가기 버튼 (카테고리 뷰로)
        val backButton = ItemStack(Material.ARROW)
        val backMeta = backButton.itemMeta
        backMeta.displayName(Component.text("${ChatColor.YELLOW}뒤로 가기"))
        backMeta.lore(listOf(Component.text("${ChatColor.GRAY}카테고리 목록으로 돌아갑니다")))
        backMeta.persistentDataContainer.set(NamespacedKey(plugin, "button"), PersistentDataType.STRING, "back_to_categories")
        backButton.itemMeta = backMeta
        inventory.setItem(48, backButton)

        // 닫기 버튼
        inventory.setItem(49, createCloseButton())

        plugin.logger.info("상품 그룹 GUI 설정 완료: ${currentProductGroups.size}개")
    }

    // 상품 그룹 아이템 생성
    private fun createProductGroupItem(productGroup: prd.peurandel.prdcore.Manager.ProductGroupInfo): ItemStack {
        val material = try {
            Material.valueOf(productGroup.material.uppercase())
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("잘못된 Material: ${productGroup.material}, 기본값 CHEST 사용")
            Material.CHEST
        }

        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(Component.text(productGroup.name, NamedTextColor.GREEN))
        meta.lore(listOf(
            Component.text("카테고리: ${selectedCategoryId}", NamedTextColor.GRAY),
            Component.text(productGroup.description ?: "상품 그룹", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("클릭하여 상품 보기", NamedTextColor.YELLOW)
        ))

        meta.persistentDataContainer.set(NamespacedKey(plugin, "button"), PersistentDataType.STRING, "product")
        meta.persistentDataContainer.set(NamespacedKey(plugin, "product_group_id"), PersistentDataType.STRING, productGroup.id)
        meta.persistentDataContainer.set(NamespacedKey(plugin, "product_group_name"), PersistentDataType.STRING, productGroup.name)

        item.itemMeta = meta
        return item
    }

    /**
     * 가격과 수량 입력 GUI 열기 (두 단계 프로세스)
     */
    private fun openPriceAndQuantityInputGUI(player: Player, title: String, callback: (String, String) -> Unit) {
        // 가격 입력용 인벤토리 생성
        val priceInventory = Bukkit.createInventory(null, 9 * 3, "$title - 가격")

        // 가격 선택 버튼 설정
        val prices = listOf(1.0, 5.0, 10.0, 50.0, 100.0, 500.0, 1000.0)

        for (i in prices.indices) {
            val price = prices[i]
            val item = ItemStack(Material.PAPER)
            val meta = item.itemMeta
            meta?.displayName(Component.text("${ChatColor.YELLOW}$price${ChatColor.WHITE}원 선택"))
            meta?.lore(listOf(Component.text("${ChatColor.GRAY}클릭하여 $price 가격 선택")))
            item.itemMeta = meta

            priceInventory.setItem(10 + i, item)
        }

        // 커스텀 가격 버튼
        val customItem = ItemStack(Material.BOOK)
        val customMeta = customItem.itemMeta
        customMeta?.displayName(Component.text("${ChatColor.AQUA}사용자 지정 가격"))
        customMeta?.lore(listOf(Component.text("${ChatColor.GRAY}채팅으로 원하는 가격을 입력하세요")))
        customItem.itemMeta = customMeta
        priceInventory.setItem(16, customItem)

        // 취소 버튼
        val cancelItem = ItemStack(Material.BARRIER)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta?.displayName(Component.text("${ChatColor.RED}취소"))
        cancelMeta?.lore(listOf(Component.text("${ChatColor.GRAY}이전 메뉴로 돌아갑니다")))
        cancelItem.itemMeta = cancelMeta
        priceInventory.setItem(22, cancelItem)

        // 가격 선택 이벤트 핸들러
        val priceListener = object : Listener {
            @EventHandler
            fun onInventoryClick(event: InventoryClickEvent) {
                if (event.inventory != priceInventory) return
                if (event.whoClicked.uniqueId != player.uniqueId) return

                event.isCancelled = true

                if (event.currentItem == null) return

                when (event.slot) {
                    22 -> { // 취소 버튼
                        event.whoClicked.closeInventory()
                        HandlerList.unregisterAll(this)
                        open(plugin, player as Player) // 원래 바자 상점 GUI로 돌아감
                    }
                    16 -> { // 사용자 지정 가격
                        event.whoClicked.closeInventory()
                        HandlerList.unregisterAll(this)

                        player.sendMessage(Component.text("${ChatColor.GREEN}채팅으로 가격을 입력하세요. 취소하려면 '취소'를 입력하세요."))

                        // 채팅 입력 리스너
                        val chatListener = object : Listener {
                            @EventHandler
                            fun onPlayerChat(chatEvent: AsyncPlayerChatEvent) {
                                if (chatEvent.player.uniqueId != player.uniqueId) return

                                chatEvent.isCancelled = true

                                if (chatEvent.message.lowercase() == "취소") {
                                    HandlerList.unregisterAll(this)
                                    open(plugin, player)
                                    return
                                }

                                try {
                                    val price = chatEvent.message.toDouble()
                                    if (price <= 0) {
                                        player.sendMessage(Component.text("${ChatColor.RED}0보다 큰 가격을 입력해야 합니다. 다시 시도하세요."))
                                        return
                                    }

                                    HandlerList.unregisterAll(this)
                                    openQuantityInputGUI(player, "$title - 수량") { quantity ->
                                        callback(price.toString(), quantity)
                                    }
                                } catch (e: NumberFormatException) {
                                    player.sendMessage(Component.text("${ChatColor.RED}유효한 숫자를 입력해야 합니다. 다시 시도하세요."))
                                }
                            }
                        }

                        Bukkit.getPluginManager().registerEvents(chatListener, plugin)
                    }
                    else -> {
                        // 미리 정의된 가격 버튼 확인
                        for (i in prices.indices) {
                            if (event.slot == 10 + i) {
                                event.whoClicked.closeInventory()
                                HandlerList.unregisterAll(this)
                                openQuantityInputGUI(player, "$title - 수량") { quantity ->
                                    callback(prices[i].toString(), quantity)
                                }
                                return
                            }
                        }
                    }
                }
            }

            // 인벤토리 닫기 이벤트 핸들러 (ESC로 닫을 때도 리스너 해제)
            @EventHandler
            fun onInventoryClose(event: InventoryCloseEvent) {
                if (event.inventory == priceInventory && event.player.uniqueId == player.uniqueId) {
                    HandlerList.unregisterAll(this)
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(priceListener, plugin)
        player.openInventory(priceInventory)
    }

    /**
     * 수량 입력 GUI 열기
     */
    private fun openQuantityInputGUI(player: Player, title: String, callback: (String) -> Unit) {
        // 수량 선택용 인벤토리 생성
        val quantityInventory = Bukkit.createInventory(null, 9 * 3, title)

        // 수량 선택 버튼 설정
        val quantities = listOf(1, 5, 10, 32, 64, 128, 256)

        for (i in quantities.indices) {
            val quantity = quantities[i]
            val item = ItemStack(Material.PAPER)
            val meta = item.itemMeta
            meta?.displayName(Component.text("${ChatColor.YELLOW}$quantity${ChatColor.WHITE}개 선택"))
            meta?.lore(listOf(Component.text("${ChatColor.GRAY}클릭하여 $quantity 수량 선택")))
            item.itemMeta = meta

            quantityInventory.setItem(10 + i, item)
        }

        // 커스텀 수량 버튼
        val customItem = ItemStack(Material.BOOK)
        val customMeta = customItem.itemMeta
        customMeta?.displayName(Component.text("${ChatColor.AQUA}사용자 지정 수량"))
        customMeta?.lore(listOf(Component.text("${ChatColor.GRAY}채팅으로 원하는 수량을 입력하세요")))
        customItem.itemMeta = customMeta
        quantityInventory.setItem(16, customItem)

        // 취소 버튼
        val cancelItem = ItemStack(Material.BARRIER)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta?.displayName(Component.text("${ChatColor.RED}취소"))
        cancelMeta?.lore(listOf(Component.text("${ChatColor.GRAY}이전 메뉴로 돌아갑니다")))
        cancelItem.itemMeta = cancelMeta
        quantityInventory.setItem(22, cancelItem)

        // 수량 선택 이벤트 핸들러
        val quantityListener = object : Listener {
            @EventHandler
            fun onInventoryClick(event: InventoryClickEvent) {
                if (event.inventory != quantityInventory) return
                if (event.whoClicked.uniqueId != player.uniqueId) return

                event.isCancelled = true

                if (event.currentItem == null) return

                when (event.slot) {
                    22 -> { // 취소 버튼
                        event.whoClicked.closeInventory()
                        HandlerList.unregisterAll(this)
                        open(plugin, player as Player) // 원래 바자 상점 GUI로 돌아감
                    }
                    16 -> { // 사용자 지정 수량
                        event.whoClicked.closeInventory()
                        HandlerList.unregisterAll(this)

                        player.sendMessage(Component.text("${ChatColor.GREEN}채팅으로 수량을 입력하세요. 취소하려면 '취소'를 입력하세요."))

                        // 채팅 입력 리스너
                        val chatListener = object : Listener {
                            @EventHandler
                            fun onPlayerChat(chatEvent: AsyncPlayerChatEvent) {
                                if (chatEvent.player.uniqueId != player.uniqueId) return

                                chatEvent.isCancelled = true

                                if (chatEvent.message.lowercase() == "취소") {
                                    HandlerList.unregisterAll(this)
                                    open(plugin, player)
                                    return
                                }

                                try {
                                    val quantity = chatEvent.message.toInt()
                                    if (quantity <= 0) {
                                        player.sendMessage(Component.text("${ChatColor.RED}0보다 큰 수량을 입력해야 합니다. 다시 시도하세요."))
                                        return
                                    }

                                    HandlerList.unregisterAll(this)
                                    Bukkit.getScheduler().runTask(plugin, Runnable {
                                        callback(quantity.toString())
                                    })
                                } catch (e: NumberFormatException) {
                                    player.sendMessage(Component.text("${ChatColor.RED}유효한 숫자를 입력해야 합니다. 다시 시도하세요."))
                                }
                            }
                        }

                        Bukkit.getPluginManager().registerEvents(chatListener, plugin)
                    }
                    else -> {
                        // 미리 정의된 수량 버튼 확인
                        for (i in quantities.indices) {
                            if (event.slot == 10 + i) {
                                event.whoClicked.closeInventory()
                                HandlerList.unregisterAll(this)
                                callback(quantities[i].toString())
                                return
                            }
                        }
                    }
                }
            }

            // 인벤토리 닫기 이벤트 핸들러 (ESC로 닫을 때도 리스너 해제)
            @EventHandler
            fun onInventoryClose(event: InventoryCloseEvent) {
                if (event.inventory == quantityInventory && event.player.uniqueId == player.uniqueId) {
                    HandlerList.unregisterAll(this)
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(quantityListener, plugin)
        player.openInventory(quantityInventory)
    }

    /**
     * 판매 제안 생성 GUI 열기
     * 가격과 수량을 입력받기 위한 인터페이스 표시
     */
    private fun openSellOfferCreationGUI(player: Player, productId: String) {
        openPriceAndQuantityInputGUI(player, "판매 제안 생성") { price, quantity ->
            GlobalScope.launch {
                val result = bazaarAPI.placeSellOffer(player.uniqueId.toString(), productId, quantity.toInt(), price.toDouble())
                player.sendMessage(
                    if (result.success) Component.text("${ChatColor.GREEN}${quantity}개의 ${productId} 판매 제안이 생성되었습니다. (단가: $price)")
                    else Component.text("${ChatColor.RED}제안 생성 실패: ${result.message}")
                )

                // GUI 다시 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    open(plugin, player)
                })
            }
        }
    }

    private fun getGUIByProductId(productId: String): ProductGUIInfo? {
        return PRODUCT_GUI_INFOS[productId]
    }

    private fun createBackButton(productsId: String): ItemStack {
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(Component.text("${ChatColor.GREEN}GO BACK"))
            meta.lore(listOf(Component.text("${ChatColor.GRAY}To $productsId")))
            meta.persistentDataContainer.set(NamespacedKey(plugin, "button"),PersistentDataType.STRING, "back") }
        return item
    }
    private fun createBackToBazaarButton(): ItemStack {
        val item = ItemStack(Material.WHEAT)
        item.editMeta { meta ->
            meta.displayName(Component.text("${ChatColor.YELLOW}GO BACK"))
            meta.lore(listOf(Component.text("${ChatColor.GRAY}To Bazaar")))
            meta.persistentDataContainer.set(NamespacedKey(plugin, "button"),PersistentDataType.STRING, "back_to_bazaar") }
        return item
    }

    private fun createInstantBuyButton(productId: String): ItemStack {
        val item = ItemStack(Material.GOLDEN_HORSE_ARMOR)
        item.editMeta { meta ->
            meta.displayName(Component.text("${ChatColor.YELLOW}즉시 구매"))
            meta.lore(listOf(
                Component.text("${ChatColor.DARK_GRAY}$productId"),
                Component.empty(),
                Component.text("${ChatColor.GRAY}개당 가격 : ", NamedTextColor.GOLD),
                Component.text("${ChatColor.GRAY}세트 당 가격 : ", NamedTextColor.GOLD)
            ))
            meta.persistentDataContainer.set(NamespacedKey(plugin, "button"),PersistentDataType.STRING, "instant_buy")
        }
        return item
    }
    private fun createInstantSellButton(productId: String): ItemStack {
        val item = ItemStack(Material.HOPPER)
        item.editMeta { meta ->
            meta.displayName(Component.text("${ChatColor.YELLOW}즉시 판매"))
            meta.lore(listOf(
                Component.text("${ChatColor.DARK_GRAY}$productId"),
                Component.empty(),
                Component.text("${ChatColor.GRAY}개당 가격 : ", NamedTextColor.GOLD),
                Component.text("${ChatColor.GRAY}세트 당 가격 : ", NamedTextColor.GOLD)
            ))
            meta.persistentDataContainer.set(NamespacedKey(plugin, "button"),PersistentDataType.STRING, "instant_sell")
        }
        return item
    }
    private fun createBuyOrderButton(productId: String): ItemStack {
        val item = ItemStack(Material.FILLED_MAP)
        item.editMeta { meta ->
            meta.displayName(Component.text("${ChatColor.YELLOW}구매 제안"))
            meta.lore(listOf(
                Component.text("${ChatColor.DARK_GRAY}$productId"),
                Component.empty()
            ))
            meta.persistentDataContainer.set(NamespacedKey(plugin, "button"),PersistentDataType.STRING, "buy_order")
        }
        return item
    }
    private fun createSellOrderButton(productId: String): ItemStack {
        val item = ItemStack(Material.MAP)
        item.editMeta { meta ->
            meta.displayName(Component.text("${ChatColor.YELLOW}판매 제안"))
            meta.lore(listOf(
                Component.text("${ChatColor.DARK_GRAY}$productId"),
                Component.empty()
            ))
            meta.persistentDataContainer.set(NamespacedKey(plugin, "button"),PersistentDataType.STRING, "sell_order")
        }
        return item
    }
    // 각 항목 별 GUI 구성
    private fun openProductsGUI(player: Player, productId: String, callback: (Int) -> Unit) {
        // 인벤토리 생성
        val productInfo = getGUIByProductId(productId)
        if (productInfo != null) {
            val productInventory = Bukkit.createInventory(null, productInfo.size, "바자 → $productId")

            for (i in 0 until productInfo.size) {
                productInventory.setItem(i,getStainedGlassPane("GRAY_STAINED_GLASS_PANE"))
            }
            for (i in productInfo.productList.indices) {
                productInventory.setItem(productInfo.productList[i].guiSlot, createProductItem(productInfo.productList[i].itemstack,productInfo.productList[i].productId))
            }
            productInventory.setItem(productInfo.size-5, createCloseButton())
            productInventory.setItem(productInfo.size-6,createBackButton("Bazaar"))

            //이벤트 처리
            val productListener = object: Listener {
                @EventHandler
                fun onInventoryClick(event: InventoryClickEvent) {
                    event.isCancelled = true

                    val item = event.currentItem ?: return
                    val itemType = item.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, "button"), PersistentDataType.STRING) ?: return
                    when(itemType) {
                        "product" -> {
                            openProductGUI(player, productId, item.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, "product"), PersistentDataType.STRING) ?: return) { quantity ->
                                GlobalScope.launch {
                                    Bukkit.getScheduler().runTask(plugin, Runnable {
                                        open(plugin, player)
                                    })
                                }
                            }
                        }
                        "back"-> {
                            event.whoClicked.closeInventory()
                            HandlerList.unregisterAll(this)
                            open(plugin, player)
                        }
                        "close"-> {
                            event.whoClicked.closeInventory()
                            HandlerList.unregisterAll(this)
                        }
                    }
                }

                @EventHandler
                fun onInventoryClose(event: InventoryCloseEvent) {
                    if (event.inventory == productInventory && event.player == player) {
                        HandlerList.unregisterAll(this)
                    }
                }
            }

            Bukkit.getPluginManager().registerEvents(productListener, plugin)
            player.openInventory(productInventory)
        }
        else {
            player.sendMessage("해당 상품을 확인할 수 없습니다. 관리자에게 문의해주세요.")
            open(plugin, player)
        }

    }

    private fun openProductGUI(player: Player, productsId: String,productId: String, callback: (Int) -> Unit) {
        //인벤토리 설정
        val productInventory = Bukkit.createInventory(null, 36, "$productsId → $productId")
        for (i in 0 until 36) {
            productInventory.setItem(i,getStainedGlassPane("GRAY_STAINED_GLASS_PANE"))
        }


        // TODO: 즉시 구매: 금말갑옷, 즉시 판매: 호퍼, 중간: 팔 물건, 구매 제안 : 편 지도, 판매 제안 : 안편 지도
        // 제목, 상품 ID, Price per unit, Stack price, Click to pick amount

        productInventory.setItem(10,createInstantBuyButton(productId))
        productInventory.setItem(11,createInstantSellButton(productId))
        // TODO : 13, ITEM
        productInventory.setItem(15,createBuyOrderButton(productId))
        productInventory.setItem(16,createSellOrderButton(productId))


        productInventory.setItem(30,createBackButton(productsId))
        productInventory.setItem(31,createBackToBazaarButton())


        //이벤트 처리
        val productListener = object: Listener {
            @EventHandler
            fun onInventoryClick(event: InventoryClickEvent) {
                event.isCancelled = true

                val item = event.currentItem ?: return
                val itemType = item.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, "button"), PersistentDataType.STRING) ?: return
                when(itemType) {
                    "back" -> {
                        openProductsGUI(player, productsId) { quantity ->
                            GlobalScope.launch {
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    open(plugin, player)
                                })
                            }
                        }
                    }
                    "back_to_bazaar"-> {
                        event.whoClicked.closeInventory()
                        HandlerList.unregisterAll(this)
                        open(plugin, player)
                    }
                    "sell_order" -> {
                        openSellOfferCreationGUI(player, productId)
                        HandlerList.unregisterAll(this)
                    }
                }
            }
            // 이거 없으면 ESC로 닫아버리면 이벤트 처리가 유지된다. 반드시 넣어라
            @EventHandler
            fun onInventoryClose(event: InventoryCloseEvent) {
                if (event.inventory == productInventory && event.player == player) {
                    HandlerList.unregisterAll(this)
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(productListener, plugin)

        player.openInventory(productInventory)

    }
}
