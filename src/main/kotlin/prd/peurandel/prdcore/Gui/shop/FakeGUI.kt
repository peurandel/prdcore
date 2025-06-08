package prd.peurandel.prdcore.Gui.shop

import com.mongodb.client.MongoDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.BaseGUI
import prd.peurandel.prdcore.Manager.BazaarAPI
import prd.peurandel.prdcore.Manager.OrderBookSnapshot
import prd.peurandel.prdcore.Manager.ProductInfo
import java.text.DecimalFormat
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

/**
 * 바자 상점 GUI 클래스
 * 바자 시스템의 아이템 거래를 위한 사용자 인터페이스를 제공합니다.
 */
class FakeGUI(plugin: JavaPlugin, private val bazaarAPI: BazaarAPI, private val database: MongoDatabase) : BaseGUI(plugin, "바자 상점 - 다이아몬드", 54) {

    companion object {
        private const val DIAMOND_ITEM_ID = "DIAMOND" // MongoDB에 저장된 ID로 변경

        // GUI 슬롯 위치
        private const val INFO_SLOT = 4
        private const val SELL_OFFER_START_SLOT = 19
        private const val BUY_ORDER_START_SLOT = 28
        private const val INSTANT_BUY_SLOT = 49
        private const val INSTANT_SELL_SLOT = 51
        private const val CREATE_BUY_ORDER_SLOT = 47
        private const val CREATE_SELL_OFFER_SLOT = 53
    }

    private val priceFormat = DecimalFormat("#,##0.00")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // 상품 정보와 주문서 스냅샷 캐싱
    private var cachedProductInfo: ProductInfo? = null
    private var cachedOrderBook: OrderBookSnapshot? = null

    private fun getStainedGlassPane() : ItemStack {
        val item = ItemStack(Material.PINK_STAINED_GLASS_PANE)
        item.editMeta { meta ->
            meta.setHideTooltip(true)
        }
        return item
    }
    private fun getCategoryFarming() : ItemStack {
        val item = ItemStack(Material.NETHERITE_HOE)
        item.editMeta { meta ->
            meta.displayName(Component.text("${ChatColor.YELLOW}Farming"))
            meta.lore = listOf("${ChatColor.GRAY}Category")
        }
        return item
    }
    private fun getCategoryMining() : ItemStack {
        val item = ItemStack(Material.DIAMOND_PICKAXE)
        item.editMeta { meta ->

            meta.displayName(Component.text("${ChatColor.YELLOW}Mining"))
            meta.lore = listOf("${ChatColor.GRAY}Category")
        }
        return item
    }

    private fun getCategoryCombat() : ItemStack {
        val item = ItemStack(Material.IRON_SWORD)
        item.editMeta { meta ->

            meta.displayName(Component.text("${ChatColor.YELLOW}Combat"))
            meta.lore = listOf("${ChatColor.GRAY}Category")
        }
        return item
    }
    private fun getCategoryWoodFish() : ItemStack {
        val item = ItemStack(Material.FISHING_ROD)
        item.editMeta { meta ->

            meta.displayName(Component.text("${ChatColor.YELLOW}Wood & Fish"))
            meta.lore = listOf("${ChatColor.GRAY}Category")
        }
        return item
    }
    private fun getCategoryOddities() : ItemStack {
        val item = ItemStack(Material.FURNACE)
        item.editMeta { meta ->

            meta.displayName(Component.text("${ChatColor.LIGHT_PURPLE}Oddities"))
            meta.lore = listOf("${ChatColor.GRAY}Category")
        }
        return item
    }




    override fun initializeItems(plugin: JavaPlugin, player: String) {
        // 모든 슬롯을 배경 유리로 초기화\
        inventory.setItem(0, getCategoryFarming())
        inventory.setItem(1, getStainedGlassPane())
        inventory.setItem(2, getStainedGlassPane())
        inventory.setItem(3, getStainedGlassPane())
        inventory.setItem(4, getStainedGlassPane())
        inventory.setItem(5, getStainedGlassPane())
        inventory.setItem(6, getStainedGlassPane())
        inventory.setItem(7, getStainedGlassPane())
        inventory.setItem(8, getStainedGlassPane())
        inventory.setItem(9, getCategoryMining())
        inventory.setItem(10, getStainedGlassPane())
        inventory.setItem(17, getStainedGlassPane())
        inventory.setItem(18, getCategoryCombat())
        inventory.setItem(19, getStainedGlassPane())
        inventory.setItem(26, getStainedGlassPane())
        inventory.setItem(27, getCategoryWoodFish())
        inventory.setItem(28, getStainedGlassPane())
        inventory.setItem(35, getStainedGlassPane())
        inventory.setItem(36, getCategoryOddities())
        inventory.setItem(37, getStainedGlassPane())
        inventory.setItem(44, getStainedGlassPane())

        inventory.setItem(46, getStainedGlassPane())
        inventory.setItem(53, getStainedGlassPane())

        // 비동기로 바자 데이터 로드 및 UI 구성
        GlobalScope.launch {
            try {
                Bukkit.getLogger().info("[BazaarShopGUI] 다이아몬드 상품 정보 로드 시작: itemId=$DIAMOND_ITEM_ID")

                // 다이아몬드 상품 정보와 주문서 스냅샷 가져오기
                val productBookPair = bazaarAPI.getProductDetailsAndOrderBook(DIAMOND_ITEM_ID)

                if (productBookPair == null) {
                    Bukkit.getLogger().warning("[BazaarShopGUI] 다이아몬드 상품 정보를 가져올 수 없습니다. productBookPair is null")
                    return@launch
                }

                Bukkit.getLogger().info("[BazaarShopGUI] 다이아몬드 상품 정보 로드 성공")

                val (productInfo, orderBook) = productBookPair
                Bukkit.getLogger().info("[BazaarShopGUI] 상품 정보: id=${productInfo.id}, name=${productInfo.name}")
                Bukkit.getLogger().info("[BazaarShopGUI] 주문서: itemId=${orderBook.itemId}, 구매 주문 수=${orderBook.buyOrders.size}, 판매 제안 수=${orderBook.sellOrders.size}")

                cachedProductInfo = productInfo
                cachedOrderBook = orderBook

                // UI 요소 동기적으로 업데이트
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // 상품 정보 아이템 설정
                        inventory.setItem(INFO_SLOT, createInfoItem(productInfo, orderBook))
                        Bukkit.getLogger().info("[BazaarShopGUI] 상품 정보 아이템 생성 완료")

                        // 판매 제안 표시 (가장 낮은 가격부터)
                        displaySellOffers(orderBook)
                        Bukkit.getLogger().info("[BazaarShopGUI] 판매 제안 표시 완료")

                        // 구매 주문 표시 (가장 높은 가격부터)
                        Bukkit.getLogger().info("[BazaarShopGUI] 구매 주문 표시 시작")
                        displayBuyOrders(orderBook)
                        Bukkit.getLogger().info("[BazaarShopGUI] 구매 주문 표시 완료")

                        // 즉시 구매 버튼
                        inventory.setItem(INSTANT_BUY_SLOT, createInstantBuyItem(orderBook))
                        Bukkit.getLogger().info("[BazaarShopGUI] 즉시 구매 버튼 생성 완료")

                        // 즉시 판매 버튼
                        inventory.setItem(INSTANT_SELL_SLOT, createInstantSellItem(orderBook))
                        Bukkit.getLogger().info("[BazaarShopGUI] 즉시 판매 버튼 생성 완료")

                        // 구매 주문 생성 버튼
                        inventory.setItem(CREATE_BUY_ORDER_SLOT, createButtonItem(Material.EMERALD_BLOCK, "§a구매 주문 생성",
                            listOf("§7특정 가격으로 다이아몬드를 구매하는 주문을 생성합니다.")))
                        Bukkit.getLogger().info("[BazaarShopGUI] 구매 주문 생성 버튼 생성 완료")
                    } catch (e: Exception) {
                        Bukkit.getLogger().severe("[BazaarShopGUI] UI 업데이트 중 오류 발생: ${e.message}")
                        e.printStackTrace()
                    }
                })
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[BazaarShopGUI] 다이아몬드 상품 정보 로드 중 오류 발생: ${e.message}")
                e.printStackTrace()
                Bukkit.getPlayer(player)?.sendMessage("§c오류가 발생했습니다: ${e.message}")
            }
        }

        // GUI 정보/닫기 버튼
        inventory.setItem(INSTANT_BUY_SLOT, GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString()))
    }

    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true

        // 클릭한 플레이어 확인
        val player = event.whoClicked as? Player ?: return

        // 빈 슬롯 클릭 무시
        if (event.currentItem == null) return

        // 슬롯에 따른 처리
        when (event.slot) {
            // 즉시 구매 버튼
            INSTANT_BUY_SLOT -> handleInstantBuy(player)

            // 즉시 판매 버튼
            INSTANT_SELL_SLOT -> handleInstantSell(player)

            // 구매 주문 생성 버튼
            CREATE_BUY_ORDER_SLOT -> openBuyOrderCreationGUI(player)

            // 판매 제안 생성 버튼
            CREATE_SELL_OFFER_SLOT -> openSellOfferCreationGUI(player)

            // 뒤로 가기 버튼
            45 -> handleGoBack(player)
        }

        // 판매 제안 슬롯 클릭 (19-23)
        if (event.slot in SELL_OFFER_START_SLOT..(SELL_OFFER_START_SLOT + 4)) {
            handleSellOfferClick(player, event.slot - SELL_OFFER_START_SLOT)
        }

        // 구매 주문 슬롯 클릭 (28-32)
        if (event.slot in BUY_ORDER_START_SLOT..(BUY_ORDER_START_SLOT + 4)) {
            handleBuyOrderClick(player, event.slot - BUY_ORDER_START_SLOT)
        }
    }

    /**
     * 즉시 구매 처리
     */
    private fun handleInstantBuy(player: Player) {
        openQuantityInputGUI(player, "구매할 수량") { quantity ->
            GlobalScope.launch {
                val result = bazaarAPI.instantBuy(player.uniqueId.toString(), DIAMOND_ITEM_ID, quantity.toInt())
                player.sendMessage(if (result.success) "§a${result.quantityTransacted}개의 다이아몬드를 구매했습니다. (평균가: ${result.averagePrice})"
                else "§c구매 실패: ${result.message}")

                // GUI 다시 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    open(plugin, player)
                })
            }
        }
    }

    /**
     * 즉시 판매 처리
     */
    private fun handleInstantSell(player: Player) {
        openQuantityInputGUI(player, "판매할 수량") { quantity ->
            GlobalScope.launch {
                val result = bazaarAPI.instantSell(player.uniqueId.toString(), DIAMOND_ITEM_ID, quantity.toInt())
                player.sendMessage(if (result.success) "§a${result.quantityTransacted}개의 다이아몬드를 판매했습니다. (평균가: ${result.averagePrice})"
                else "§c판매 실패: ${result.message}")

                // GUI 다시 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    open(plugin, player)
                })
            }
        }
    }

    /**
     * 구매 주문 생성 GUI 열기
     */
    private fun openBuyOrderCreationGUI(player: Player) {
        openPriceAndQuantityInputGUI(player, "구매 주문 생성") { price, quantity ->
            GlobalScope.launch {
                val result = bazaarAPI.placeBuyOrder(player.uniqueId.toString(), DIAMOND_ITEM_ID, quantity.toInt(), price.toDouble())
                player.sendMessage(if (result.success) "§a${quantity}개의 다이아몬드 구매 주문이 생성되었습니다. (단가: $price)"
                else "§c주문 생성 실패: ${result.message}")

                // GUI 다시 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    open(plugin, player)
                })
            }
        }
    }

    /**
     * 판매 제안 생성 GUI 열기
     */
    private fun openSellOfferCreationGUI(player: Player) {
        openPriceAndQuantityInputGUI(player, "판매 제안 생성") { price, quantity ->
            GlobalScope.launch {
                val result = bazaarAPI.placeSellOffer(player.uniqueId.toString(), DIAMOND_ITEM_ID, quantity.toInt(), price.toDouble())
                player.sendMessage(if (result.success) "§a${quantity}개의 다이아몬드 판매 제안이 생성되었습니다. (단가: $price)"
                else "§c제안 생성 실패: ${result.message}")

                // GUI 다시 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    open(plugin, player)
                })
            }
        }
    }

    /**
     * 판매 제안 클릭 처리 (즉시 구매)
     */
    private fun handleSellOfferClick(player: Player, index: Int) {
        val orderBook = cachedOrderBook ?: return

        // 클릭한 인덱스에 해당하는 판매 제안이 없으면 무시
        if (index >= orderBook.sellOrders.size) return

        val sellOffer = orderBook.sellOrders[index]
        openQuantityInputGUI(player, "구매할 수량") { quantity ->
            GlobalScope.launch {
                val intQuantity = quantity.toInt()
                if (intQuantity <= 0) {
                    player.sendMessage("§c유효하지 않은 수량입니다.")
                    return@launch
                }

                val actualQuantity = minOf(intQuantity, sellOffer.totalQuantity)
                val result = bazaarAPI.instantBuy(player.uniqueId.toString(), DIAMOND_ITEM_ID, actualQuantity)
                player.sendMessage(if (result.success) "§a${result.quantityTransacted}개의 다이아몬드를 구매했습니다. (가격: ${sellOffer.price})"
                else "§c구매 실패: ${result.message}")

                // GUI 다시 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    open(plugin, player)
                })
            }
        }
    }

    /**
     * 구매 주문 클릭 처리 (즉시 판매)
     */
    private fun handleBuyOrderClick(player: Player, index: Int) {
        val orderBook = cachedOrderBook ?: return

        // 클릭한 인덱스에 해당하는 구매 주문이 없으면 무시
        if (index >= orderBook.buyOrders.size) return

        val buyOrder = orderBook.buyOrders[index]
        openQuantityInputGUI(player, "판매할 수량") { quantity ->
            GlobalScope.launch {
                val intQuantity = quantity.toInt()
                if (intQuantity <= 0) {
                    player.sendMessage("§c유효하지 않은 수량입니다.")
                    return@launch
                }

                val actualQuantity = minOf(intQuantity, buyOrder.totalQuantity)
                val result = bazaarAPI.instantSell(player.uniqueId.toString(), DIAMOND_ITEM_ID, actualQuantity)
                player.sendMessage(if (result.success) "§a${result.quantityTransacted}개의 다이아몬드를 판매했습니다. (가격: ${buyOrder.price})"
                else "§c판매 실패: ${result.message}")

                // GUI 다시 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    open(plugin, player)
                })
            }
        }
    }

    /**
     * 뒤로 가기 처리
     */
    private fun handleGoBack(player: Player) {
        // 여기에 메인 메뉴나 이전 화면으로 돌아가는 코드 구현
        // 예: MainGUI(plugin, database).open(plugin, player)
        player.closeInventory()
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
            meta?.setDisplayName("§e$quantity§f개 선택")
            meta?.lore = listOf("§7클릭하여 $quantity 수량 선택")
            item.itemMeta = meta

            quantityInventory.setItem(10 + i, item)
        }

        // 커스텀 수량 버튼
        val customItem = ItemStack(Material.BOOK)
        val customMeta = customItem.itemMeta
        customMeta?.setDisplayName("§b사용자 지정 수량")
        customMeta?.lore = listOf("§7채팅으로 원하는 수량을 입력하세요")
        customItem.itemMeta = customMeta
        quantityInventory.setItem(16, customItem)

        // 취소 버튼
        val cancelItem = ItemStack(Material.BARRIER)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta?.setDisplayName("§c취소")
        cancelMeta?.lore = listOf("§7이전 메뉴로 돌아갑니다")
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

                        player.sendMessage("§a채팅으로 수량을 입력하세요. 취소하려면 '취소'를 입력하세요.")

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
                                        player.sendMessage("§c0보다 큰 수량을 입력해야 합니다. 다시 시도하세요.")
                                        return
                                    }

                                    HandlerList.unregisterAll(this)
                                    Bukkit.getScheduler().runTask(plugin, Runnable {
                                        callback(quantity.toString())
                                    })
                                } catch (e: NumberFormatException) {
                                    player.sendMessage("§c유효한 숫자를 입력해야 합니다. 다시 시도하세요.")
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
        }

        Bukkit.getPluginManager().registerEvents(quantityListener, plugin)
        player.openInventory(quantityInventory)
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
            meta?.setDisplayName("§e$price§f원 선택")
            meta?.lore = listOf("§7클릭하여 $price 가격 선택")
            item.itemMeta = meta

            priceInventory.setItem(10 + i, item)
        }

        // 커스텀 가격 버튼
        val customItem = ItemStack(Material.BOOK)
        val customMeta = customItem.itemMeta
        customMeta?.setDisplayName("§b사용자 지정 가격")
        customMeta?.lore = listOf("§7채팅으로 원하는 가격을 입력하세요")
        customItem.itemMeta = customMeta
        priceInventory.setItem(16, customItem)

        // 취소 버튼
        val cancelItem = ItemStack(Material.BARRIER)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta?.setDisplayName("§c취소")
        cancelMeta?.lore = listOf("§7이전 메뉴로 돌아갑니다")
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

                        player.sendMessage("§a채팅으로 가격을 입력하세요. 취소하려면 '취소'를 입력하세요.")

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
                                        player.sendMessage("§c0보다 큰 가격을 입력해야 합니다. 다시 시도하세요.")
                                        return
                                    }

                                    HandlerList.unregisterAll(this)
                                    openQuantityInputGUI(player, "$title - 수량") { quantity ->
                                        callback(price.toString(), quantity)
                                    }
                                } catch (e: NumberFormatException) {
                                    player.sendMessage("§c유효한 숫자를 입력해야 합니다. 다시 시도하세요.")
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
        }

        Bukkit.getPluginManager().registerEvents(priceListener, plugin)
        player.openInventory(priceInventory)
    }

    /**
     * 상품 정보 아이템 생성
     */
    private fun createInfoItem(productInfo: ProductInfo, orderBook: OrderBookSnapshot): ItemStack {
        val item = ItemStack(Material.DIAMOND)
        val meta = item.itemMeta!!
        meta.setDisplayName("§6${productInfo.name}")

        val lore = mutableListOf(
            "§7${productInfo.description ?: ""}",
            "§8----------------",
            "§a최고 구매가: ${orderBook.highestBuyPrice?.let { priceFormat.format(it) } ?: "없음"}",
            "§c최저 판매가: ${orderBook.lowestSellPrice?.let { priceFormat.format(it) } ?: "없음"}",
            "§8----------------",
            "§7바자에서 거래되는 다이아몬드입니다."
        )

        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    /**
     * 판매 제안 표시
     */
    private fun displaySellOffers(orderBook: OrderBookSnapshot) {
        orderBook.sellOrders.take(5).forEachIndexed { index, entry ->
            val item = ItemStack(Material.RED_STAINED_GLASS_PANE)
            val meta = item.itemMeta!!
            meta.setDisplayName("§c판매 제안: ${priceFormat.format(entry.price)}")
            meta.lore = listOf(
                "§7수량: ${entry.totalQuantity}개",
                "§7총 금액: ${priceFormat.format(entry.price * entry.totalQuantity)}",
                "§8----------------",
                "§e클릭하여 이 가격에 즉시 구매"
            )
            item.itemMeta = meta
            inventory.setItem(SELL_OFFER_START_SLOT + index, item)
        }
    }

    /**
     * 구매 주문 표시
     */
    private fun displayBuyOrders(orderBook: OrderBookSnapshot) {
        orderBook.buyOrders.take(5).forEachIndexed { index, entry ->
            val item = ItemStack(Material.GREEN_STAINED_GLASS_PANE)
            val meta = item.itemMeta!!
            meta.setDisplayName("§a구매 주문: ${priceFormat.format(entry.price)}")
            meta.lore = listOf(
                "§7수량: ${entry.totalQuantity}개",
                "§7총 금액: ${priceFormat.format(entry.price * entry.totalQuantity)}",
                "§8----------------",
                "§e클릭하여 이 가격에 즉시 판매"
            )
            item.itemMeta = meta
            inventory.setItem(BUY_ORDER_START_SLOT + index, item)
        }
    }

    /**
     * 즉시 구매 버튼 생성
     */
    private fun createInstantBuyItem(orderBook: OrderBookSnapshot): ItemStack {
        val lowestPrice = orderBook.lowestSellPrice
        val availableQuantity = orderBook.sellOrders.firstOrNull()?.totalQuantity ?: 0

        val item = ItemStack(Material.EMERALD)
        val meta = item.itemMeta!!
        meta.setDisplayName("§a즉시 구매")

        val lore = if (lowestPrice != null && availableQuantity > 0) {
            listOf(
                "§7현재 최저가: ${priceFormat.format(lowestPrice)}",
                "§7구매 가능 수량: $availableQuantity",
                "§8----------------",
                "§e클릭하여 즉시 구매"
            )
        } else {
            listOf(
                "§c판매 제안이 없습니다.",
                "§8----------------",
                "§7구매 주문을 생성하여 기다려보세요."
            )
        }

        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    /**
     * 즉시 판매 버튼 생성
     */
    private fun createInstantSellItem(orderBook: OrderBookSnapshot): ItemStack {
        val highestPrice = orderBook.highestBuyPrice
        val acceptableQuantity = orderBook.buyOrders.firstOrNull()?.totalQuantity ?: 0

        val item = ItemStack(Material.GOLD_INGOT)
        val meta = item.itemMeta!!
        meta.setDisplayName("§6즉시 판매")

        val lore = if (highestPrice != null && acceptableQuantity > 0) {
            listOf(
                "§7현재 최고가: ${priceFormat.format(highestPrice)}",
                "§7판매 가능 수량: $acceptableQuantity",
                "§8----------------",
                "§e클릭하여 즉시 판매"
            )
        } else {
            listOf(
                "§c구매 주문이 없습니다.",
                "§8----------------",
                "§7판매 제안을 생성하여 기다려보세요."
            )
        }

        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    /**
     * 버튼 아이템 생성 헬퍼 메서드
     */
    private fun createButtonItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(name)
        meta.lore = lore

        val keyButton = NamespacedKey(plugin, "button")
        meta.persistentDataContainer.set(keyButton, PersistentDataType.STRING, name.lowercase().replace(" ", "_"))

        item.itemMeta = meta
        return item
    }

    /**
     * GUI 정보 아이템 생성
     */
    fun GUIInfo(uuid: String): ItemStack {
        val item: ItemStack = ItemStack(Material.BARRIER)
        val meta: ItemMeta = item.itemMeta
        val keyButton = NamespacedKey(plugin, "button")
        val keyUserUUID = NamespacedKey(plugin, "userUUID")
        meta.persistentDataContainer.set(keyButton, PersistentDataType.STRING, "close")
        meta.persistentDataContainer.set(keyUserUUID, PersistentDataType.STRING, uuid)
        meta.setDisplayName("§c닫기")
        item.itemMeta = meta
        return item
    }
}
