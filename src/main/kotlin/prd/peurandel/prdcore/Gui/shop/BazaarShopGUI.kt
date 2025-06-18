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
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.BaseGUI
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataType
import prd.peurandel.prdcore.Manager.Category

/**
 * 바자회 물건 구매 GUI
 */
class BazaarShopGUI(plugin: JavaPlugin, private val database: MongoDatabase) : BaseGUI(plugin,"바자", 54) {

    companion object {
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
            FARMING, MINING, COMBAT, WOOD_FISH, ODDITIES
        }
        // 제품 GUI 정보를 담는 HashMap - O(1) 조회를 위해 맵으로 정의
        private val PRODUCT_GUI_INFOS = hashMapOf(
            "DIAMOND" to ProductGUIInfo(
                "DIAMOND", 
                36, 
                Material.DIAMOND, 
                listOf(
                    ProductInfo("DIAMOND", 11, ItemStack(Material.DIAMOND)),
                    ProductInfo("DIAMOND_BLOCK", 15, ItemStack(Material.DIAMOND_BLOCK))
                )
            ),
            "GOLD" to ProductGUIInfo(
                "GOLD", 
                36, 
                Material.GOLD_INGOT, 
                listOf(
                    ProductInfo("GOLD_INGOT", 11, ItemStack(Material.GOLD_INGOT)),
                    ProductInfo("GOLD_BLOCK", 15, ItemStack(Material.GOLD_BLOCK))
                )
            ),
            "COPPER" to ProductGUIInfo(
                "COPPER", 
                36, 
                Material.COPPER_INGOT, 
                listOf(
                    ProductInfo("COPPER_INGOT", 11, ItemStack(Material.COPPER_INGOT)),
                    ProductInfo("COPPER_BLOCK", 15, ItemStack(Material.COPPER_BLOCK))
                )
            ),
            "COAL" to ProductGUIInfo(
                "COAL", 
                36, 
                Material.COAL, 
                listOf(
                    ProductInfo("COAL", 11, ItemStack(Material.COAL)),
                    ProductInfo("COAL_BLOCK", 15, ItemStack(Material.COAL_BLOCK))
                )
            ),
            "IRON" to ProductGUIInfo(
                "IRON", 
                36, 
                Material.IRON_INGOT, 
                listOf(
                    ProductInfo("IRON_INGOT", 11, ItemStack(Material.IRON_INGOT)),
                    ProductInfo("IRON_BLOCK", 15, ItemStack(Material.IRON_BLOCK))
                )
            )
            // 필요한 다른 제품들은 여기에 추가하면 됨
        )

        // 카테고리 정보 목록 - 여기만 수정하면 됩니다
        val CATEGORY_INFOS = listOf(
            CategoryInfo(Category.FARMING, 0, Material.NETHERITE_HOE, "농사"),
            CategoryInfo(Category.MINING, 9, Material.DIAMOND_PICKAXE, "광업"),
            CategoryInfo(Category.COMBAT, 18, Material.IRON_SWORD, "전투"),
            CategoryInfo(Category.WOOD_FISH, 27, Material.FISHING_ROD, "목재 & 낚시"),
            CategoryInfo(Category.ODDITIES, 36, Material.ENDER_EYE, "특수 아이템")
        )

        val productSlots = listOf(11, 12, 13, 14, 15, 16,
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


        fun getCategory(category: String): Category {
            return Category.valueOf(category)
        }
    }

    // 카테고리 관련 텍스트
    private val CATEGORY_LORE = Component.text("클릭하여 이 카테고리의 물품을 확인하세요", NamedTextColor.GRAY)
    private val CATEGORY_SELECTED_LORE = listOf(
        Component.text("이 카테고리의 물품들이 표시됩니다", NamedTextColor.GRAY),
        Component.text("▶ 선택됨", NamedTextColor.GREEN, TextDecoration.BOLD)
    )



    // 현재 선택된 카테고리 (슬롯 ID가 아닌 Category 열거형으로 관리)
    private var selectedCategory: Category = Category.FARMING


    private fun getStainedGlassPane(color: String): ItemStack {
        val item = ItemStack(Material.valueOf(color))
        item.editMeta { meta ->
            meta.displayName(Component.text(" "))
        }
        return item
    }
    private fun setStainedGlassPane(inventory: Inventory,color: String) {
        for (i in grassPaneIndexs) {

            val item = ItemStack(Material.valueOf(color))
            item.editMeta { meta ->
                meta.displayName(Component.text(" "))
            }
            inventory.setItem(i, getStainedGlassPane(color))
        }

    }

    // 카테고리 아이템 생성
    private fun createCategoryItem(info: CategoryInfo): ItemStack {
        val item = ItemStack(info.material)
        item.editMeta { meta ->
            meta.displayName(Component.text(info.displayName, NamedTextColor.YELLOW))
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "button"),
                PersistentDataType.STRING,
                "category"
            )
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "category"),
                PersistentDataType.STRING,
                info.category.name
            )
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
        val item = itemStack
        item.editMeta { meta ->
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

    // GUI 초기화
    override fun initializeItems(plugin: JavaPlugin, player: String) {
        // 가장자리 유리판 배치
        setStainedGlassPane(inventory,"YELLOW_STAINED_GLASS_PANE")

        // 카테고리 아이템 배치
        for (categoryInfo in CATEGORY_INFOS) {
            inventory.setItem(categoryInfo.slotId, createCategoryItem(categoryInfo))
        }

        // 기본 GUI 항목
        inventory.setItem(50, createInfoItem()) // 정보 아이템
        inventory.setItem(49, createCloseButton()) // 뒤로 가기 버튼

        // 선택된 카테고리에 따른 상품 로드
        loadCategoryItems()
    }

    // 카테고리에 따른 상품 로드
    private fun loadCategoryItems() {

        for (slot in productSlots) {
            inventory.setItem(slot, null)
        }

        // 선택된 카테고리에 따라 하드코딩된 상품 표시
        when (selectedCategory) {
            Category.FARMING -> loadFarmingItems()
            Category.MINING -> loadMiningItems()
            Category.COMBAT -> loadCombatItems()
            Category.WOOD_FISH -> loadWoodFishItems()
            Category.ODDITIES -> loadOdditiesItems()
        }
    }

    // 농사 카테고리 상품 로드
    private fun loadFarmingItems() {
        setStainedGlassPane(inventory,"YELLOW_STAINED_GLASS_PANE")
        val items = listOf(
            createProductItem("WHEAT", "밀", Material.WHEAT),
            createProductItem("CARROT", "당근", Material.CARROT),
            createProductItem("POTATO", "감자", Material.POTATO),
            createProductItem("BEETROOT", "비트", Material.BEETROOT),
            createProductItem("PUMPKIN", "호박", Material.PUMPKIN),
            createProductItem("MELON", "수박", Material.MELON),
            createProductItem("SUGAR_CANE", "사탕수수", Material.SUGAR_CANE),
            createProductItem("COCOA_BEANS", "코코아콩", Material.COCOA_BEANS)
        )
        placeProductItems(items)
    }

    // 광업 카테고리 상품 로드
    private fun loadMiningItems() {
        setStainedGlassPane(inventory,"BLUE_STAINED_GLASS_PANE")

        val items = listOf(
            createProductItem("COAL", "석탄", Material.COAL),
            createProductItem("GOLD", "금", Material.GOLD_INGOT),
            createProductItem("IRON", "철", Material.IRON_INGOT),
            createProductItem("COPPER", "구리", Material.COPPER_INGOT),
            createProductItem("DIAMOND", "다이아몬드", Material.DIAMOND),
            createProductItem("NETHERITE", "네더라이트", Material.NETHERITE_INGOT),
            createProductItem("EMERALD", "에메랄드", Material.EMERALD),
            createProductItem("LAPIS_LAZULI", "청금석", Material.LAPIS_LAZULI),
            createProductItem("REDSTONE", "레드스톤", Material.REDSTONE),
        )
        placeProductItems(items)
    }

    // 전투 카테고리 상품 로드
    private fun loadCombatItems() {
        setStainedGlassPane(inventory,"RED_STAINED_GLASS_PANE")

        val items = listOf(
            createProductItem("ARROW", "화살", Material.ARROW),
            createProductItem("GUNPOWDER", "화약", Material.GUNPOWDER),
            createProductItem("BONE", "뼈", Material.BONE),
            createProductItem("SPIDER_EYE", "거미 눈", Material.SPIDER_EYE),
            createProductItem("ROTTEN_FLESH", "썩은 살점", Material.ROTTEN_FLESH),
            createProductItem("ENDER_PEARL", "엔더 진주", Material.ENDER_PEARL),
            createProductItem("BLAZE_ROD", "블레이즈 막대", Material.BLAZE_ROD)
        )
        placeProductItems(items)
    }

    // 목재 & 낚시 카테고리 상품 로드
    private fun loadWoodFishItems() {
        setStainedGlassPane(inventory,"ORANGE_STAINED_GLASS_PANE")
        val items = listOf(
            createProductItem("OAK_LOG", "참나무 원목", Material.OAK_LOG),
            createProductItem("SPRUCE_LOG", "가문비나무 원목", Material.SPRUCE_LOG),
            createProductItem("BIRCH_LOG", "자작나무 원목", Material.BIRCH_LOG),
            createProductItem("JUNGLE_LOG", "정글 나무 원목", Material.JUNGLE_LOG),
            createProductItem("ACACIA_LOG", "아카시아 나무 원목", Material.ACACIA_LOG),
            createProductItem("DARK_OAK_LOG", "짙은 참나무 원목", Material.DARK_OAK_LOG),
            createProductItem("MANGROVE_LOG", "맹그로브 나무 원목", Material.MANGROVE_LOG),
            createProductItem("CHERRY_LOG", "벚꽃 나무 원목", Material.CHERRY_LOG),
            createProductItem("PALE_OAK_LOG", "아카시아 나무 원목", Material.PALE_OAK_LOG),
            createProductItem("CRIMSON_STEM", "진홍빛 자루", Material.CRIMSON_STEM),
            createProductItem("WARPED_STEM", "뒤틀린 자루", Material.WARPED_STEM),
            createProductItem("COD", "대구", Material.COD),
            createProductItem("SALMON", "연어", Material.SALMON),
            createProductItem("TROPICAL_FISH", "열대어", Material.TROPICAL_FISH),
            createProductItem("PUFFERFISH", "복어", Material.PUFFERFISH)
        )
        placeProductItems(items)
    }

    // 특수 아이템 카테고리 상품 로드
    private fun loadOdditiesItems() {
        setStainedGlassPane(inventory,"PINK_STAINED_GLASS_PANE")
        val items = listOf(
            createProductItem("NETHER_STAR", "네더의 별", Material.NETHER_STAR),
            createProductItem("DRAGON_EGG", "드래곤 알", Material.DRAGON_EGG),
            createProductItem("ELYTRA", "겉날개", Material.ELYTRA),
            createProductItem("HEART_OF_THE_SEA", "바다의 심장", Material.HEART_OF_THE_SEA),
            createProductItem("SHULKER_SHELL", "셜커 껍데기", Material.SHULKER_SHELL),
            createProductItem("TOTEM_OF_UNDYING", "불사의 토템", Material.TOTEM_OF_UNDYING),
        )
        placeProductItems(items)
    }

    // 상품 아이템 생성
    private fun createProductItem(productId: String, displayName: String, material: Material): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(Component.text(displayName, NamedTextColor.YELLOW))

            // 상품 정보 표시
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
            // 상품 ID를 아이템 메타데이터에 저장 (필요시 나중에 추출할 수 있음)
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "productId"),
                PersistentDataType.STRING,
                productId
            )
        }
        return item
    }

    // 상품 아이템을 슬롯에 배치
    private fun placeProductItems(items: List<ItemStack>) {

        for (i in items.indices.take(productSlots.size)) {
            inventory.setItem(productSlots[i], items[i])
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
        }
    }

    // 상품 아이템 클릭 처리
    private fun handleProductClick(clickedItem: ItemStack, player: Player) {
        // 아이템에서 상품 ID 추출
        val productId = clickedItem.itemMeta?.persistentDataContainer?.get(
            NamespacedKey(plugin, "productId"),
            PersistentDataType.STRING
        ) ?: return

        // 상품 이름 추출
        //val productName = clickedItem.itemMeta?.displayName() ?: Component.text(productId)

        // 거래 GUI 열기
        openProductsGUI(player, productId) { quantity ->
            GlobalScope.launch {


                Bukkit.getScheduler().runTask(plugin, Runnable {
                    open(plugin, player)
                })
            }
        }
    }


    private fun openProductsGUI(player: Player, productId: String, callback: (Int) -> Unit) {
        // 인벤토리 생성
        val productInfo = getGUIByProductId(productId)
        if (productInfo != null) {
            val productInventory = Bukkit.createInventory(null, productInfo.size, "바자 -> $productId")

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
        val productInventory = Bukkit.createInventory(null, 36, "$productsId -> $productId")
        for (i in 0 until 36) {
            productInventory.setItem(i,getStainedGlassPane("GRAY_STAINED_GLASS_PANE"))
        }
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
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(productListener, plugin)

        player.openInventory(productInventory)

    }
    // 각 항목 별 GUI 구성
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
    // 카테고리 클릭 처리
    private fun handleCategoryClick(category: String, player: Player) {

        // 이미 선택된 카테고리를 다시 클릭했으면 무시
        if (selectedCategory == getCategory(category)) {
            return

        }

        // 카테고리 변경
        selectedCategory = getCategory(category)

        // 플레이어 인벤토리 닫기
        open(plugin, player)
    }



}
