package prd.peurandel.prdcore.Gui.shop

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.components.GuiType
import dev.triumphteam.gui.guis.Gui
import kotlinx.coroutines.CoroutineScope
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.GuiScope
import prd.peurandel.prdcore.Manager.BazaarAPI
import prd.peurandel.prdcore.Manager.BuyOrder
import prd.peurandel.prdcore.Manager.ProductGroupInfo
import prd.peurandel.prdcore.Manager.ProductInfo
import prd.peurandel.prdcore.Manager.SellOffer
import prd.peurandel.prdcore.Manager.TransactionErrorType
import prd.peurandel.prdcore.Manager.TransactionResult

class BazaarProductGUI(
    private val player: Player,
    private val plugin: JavaPlugin,
    private val bazaarAPI: BazaarAPI,
    private val productGroup: ProductGroupInfo,
    private val product: ProductInfo
) {

    private val guiScope = GuiScope(plugin) // 1. 만능 비동기 실행기 인스턴스 생성

    private var buyOrders = emptyList<BuyOrder>()
    private var sellOffers = emptyList<SellOffer>()
    private var gui = Gui.gui()
        .title(Component.text("${productGroup.name} -> ${product.name}"))
        .rows(4)
        .disableAllInteractions()
        .create()
    fun open() {

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).
        name(Component.text(" ")).asGuiItem{it.isCancelled = true}
        guiScope.launch(
            onLoading = {
                (0 until 36).forEach { i ->
                    gui.setItem(i, borderItem)
                }
                setupStaticItems()
                val backButton = ItemBuilder.from(Material.ARROW)
                    .name(Component.text("§b이전 페이지"))
                    .asGuiItem {
                        it.isCancelled = true
                        BazaarProductGroupGUI(player, plugin, bazaarAPI, productGroup).open()
                    }
                gui.setItem(30, backButton)
                gui.open(player)
            },
            fetch1 = { // 첫 번째 데이터: 활성 구매 주문
                bazaarAPI.getActiveBuyOrders(product.id)
            },
            fetch2 = { // 두 번째 데이터: 활성 판매 제안
                bazaarAPI.getActiveSellOffers(product.id)
            },
            onSuccess = { fetchedBuyOrders, fetchedSellOffers ->
                this.buyOrders = fetchedBuyOrders
                this.sellOffers = fetchedSellOffers
                setupOrders() // 데이터 로딩이 완료된 후 주문 관련 아이템을 설정합니다.
                gui.update() // GUI를 업데이트하여 변경사항을 반영합니다.
            },
            onError = {
                val errorItem = ItemBuilder.from(Material.BARRIER).name(Component.text("§c오류 발생")).asGuiItem()
                gui.setItem(0, errorItem)
                gui.update()
            }
        )
    }

    private fun setupStaticItems() {
        val closeButton = ItemBuilder.from(Material.BARRIER)
            .name(Component.text("§c닫기"))
            .asGuiItem { it.isCancelled = true; it.whoClicked.closeInventory() }
        gui.setItem(31, closeButton)
    }

    private fun setupOrders() {

        val lore = mutableListOf<Component?>()
        lore.add(Component.text("${ChatColor.GRAY}${product.name}"))
        val IconItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text(product.name))
            .asGuiItem{it.isCancelled = true}

        // TODO: 한개, 한 스택, 인벤토리 전체 채우기, 커스텀 갯수
        val InstanceBuyButton = ItemBuilder.from(Material.GOLDEN_HORSE_ARMOR)
            .name(Component.text("즉시 구매"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openInstantBuyAskStock()
            }

        // TODO: 그냥 냅다 모든 아이템 판매임.
        val InstanceSellButton = ItemBuilder.from(Material.HOPPER)
            .name(Component.text("즉시 판매"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openInstantSellAskStock()
            }
        // TODO : 각 항목별 1개, Big Stack, 2^10 스택 등 요 부분은 커스텀.
        // TODO : 각 버튼으로 갯수를 만들면, 최고로 싸게 구매 제안 한 것과 같은 가격으로 가거나, 거기서 0.1원 올리거나, 즉시 구매가와 즉시 판매가 사이의 가격 차이(Spread)의 5%를 이용해 주문 가격을 자동으로 설정
        // 하거나 커스텀
        // TODO : 이후 confirm 버튼

        val BuyOrderButton = ItemBuilder.from(Material.FILLED_MAP)
            .name(Component.text("구매 제안"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openBuyOrderAskStock()
            }

        // TODO : 기본적으로 인벤토리 안의 아이템들 전체임.
        // TODO : 최고로 싸게 판매 제안과 같은 가격, 거기서 0.1원 내리거나, 5% of Spread 하거나, 커스텀
        // TODO : 이후 confirm
        val SellOfferButton = ItemBuilder.from(Material.MAP)
            .name(Component.text("판매 제안 생성"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openSellOfferAskStock()
            }

        gui.setItem(10, InstanceBuyButton)
        gui.setItem(11, InstanceSellButton)
        gui.setItem(13, IconItem)
        gui.setItem(15, BuyOrderButton)
        gui.setItem(16, SellOfferButton)
    }

    private fun openBuyOrderAskStock() {
        // 일단 당장은 1세트, 3세트, 5세트로 가고 TODO : 이걸 데이터 추가해서 각 항목별로 구분시키자.
        val lore = mutableListOf<Component?>()
        lore.add(Component.text("${ChatColor.GRAY}구매 요청 설정"))
        gui = Gui.gui()
            .title(Component.text("몇 개나 원하십니까?"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val oneStackItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("1세트 (64개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openBuyOrderAskAmount(64)
            }

        val ThreeStackItem = ItemBuilder.from(Material.CHEST)
            .name(Component.text("3세트 (192개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openBuyOrderAskAmount(192)
            }

        val FiveStackItem = ItemBuilder.from(Material.CHEST)
            .name(Component.text("5세트 (320개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openBuyOrderAskAmount(320)
            }

        val CustomItem = ItemBuilder.from(Material.OAK_SIGN)
            .name(Component.text("임의값"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                askQuantityViaChat(
                    onSuccess = { quantity -> openBuyOrderAskAmount(quantity) },
                    onCancel = { openBuyOrderAskStock() }
                )
            }
        gui.setItem(10, oneStackItem)
        gui.setItem(12, ThreeStackItem)
        gui.setItem(14, FiveStackItem)
        gui.setItem(16, CustomItem)
        setupStaticItems()
        val backButton = ItemBuilder.from(Material.ARROW)
            .name(Component.text("§b이전 페이지"))
            .asGuiItem {
                it.isCancelled = true
                open()
            }
        gui.setItem(30, backButton)
        gui.open(player)
    }

    private fun openBuyOrderAskAmount(quantity: Int = 0) {
        // 일단 당장은 1세트, 3세트, 5세트로 가고 TODO : 이걸 데이터 추가해서 각 항목별로 구분시키자.
        val lore = mutableListOf<Component?>()
        lore.add(Component.text("구매할 아이템: ${product.name}"))

        gui = Gui.gui()
            .title(Component.text("개당 얼마에 구매하고 싶으십니까?"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }
        val highestBuyOrder = buyOrders.firstOrNull()
        val lowestSellOffer = sellOffers.firstOrNull()

        val SameAsBestItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("최고 구매 요청과 같은 가격"))
            .lore(
                Component.text("§7현재 최고 구매가: §e${highestBuyOrder?.pricePerUnit ?: "없음"}"),
                Component.text("§e클릭하여 이 가격으로 구매 제안")
            )
            .asGuiItem {
                it.isCancelled = true
                val price = buyOrders.firstOrNull()?.pricePerUnit
                if (price != null) {
                    openConfirmBuyOrder(quantity, price)
                } else {
                    player.sendMessage(Component.text("§c최고 구매가가 없어 가격을 설정할 수 없습니다."))
                    open()
                }
            }

        val TopOrderPlus1Item = ItemBuilder.from(Material.GOLD_NUGGET)
            .name(Component.text("최고가 + 1 코인"))
            .lore(
                Component.text("§7현재 최고 구매가: §e${highestBuyOrder?.pricePerUnit ?: "없음"}"),
                Component.text("§7제안할 가격: §e${highestBuyOrder?.let { String.format("%.1f", it.pricePerUnit + 1.0) } ?: "없음"}"),
                Component.text("§e클릭하여 이 가격으로 구매 제안")
            )
            .asGuiItem {
                it.isCancelled = true
                val price = buyOrders.firstOrNull()?.let { it.pricePerUnit + 1.0 }
                if (price != null) {
                    openConfirmBuyOrder(quantity, price)
                } else {
                    player.sendMessage(Component.text("§c최고 구매가가 없어 가격을 설정할 수 없습니다."))
                    open()
                }
            }

        val PerOfSpreadItem = ItemBuilder.from(Material.GOLDEN_HORSE_ARMOR)
            .name(Component.text("중간 가격에 제안"))
            .lore(
                Component.text("§7최고 구매가: §e${highestBuyOrder?.pricePerUnit ?: "없음"}"),
                Component.text("§7최저 판매가: §e${lowestSellOffer?.pricePerUnit ?: "없음"}"),
                Component.text("§7제안할 가격: §e${
                    if (highestBuyOrder != null && lowestSellOffer != null && lowestSellOffer.pricePerUnit > highestBuyOrder.pricePerUnit) {
                        String.format("%.1f", (highestBuyOrder.pricePerUnit + lowestSellOffer.pricePerUnit) / 2)
                    } else {
                        "계산 불가"
                    }
                }"),
                Component.text("§e클릭하여 이 가격으로 구매 제안")
            )
            .asGuiItem {
                it.isCancelled = true
                val highestBuy = buyOrders.firstOrNull()
                val lowestSell = sellOffers.firstOrNull()
                if (highestBuy != null && lowestSell != null && lowestSell.pricePerUnit > highestBuy.pricePerUnit) {
                    val price = (highestBuy.pricePerUnit + lowestSell.pricePerUnit) / 2
                    openConfirmBuyOrder(quantity, price)
                } else {
                    player.sendMessage(Component.text("§c가격 범위를 계산할 수 없어 제안할 수 없습니다."))
                    open()
                }
            }

        val CustomItem = ItemBuilder.from(Material.OAK_SIGN)
            .name(Component.text("임의값"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                askPriceViaChat(
                    onSuccess = { price -> openConfirmBuyOrder(quantity, price) },
                    onCancel = { openBuyOrderAskAmount(quantity) }
                )
            }

        gui.setItem(10, SameAsBestItem)
        gui.setItem(12, TopOrderPlus1Item)
        gui.setItem(14, PerOfSpreadItem)
        gui.setItem(16, CustomItem)

        setupStaticItems()
        val backButton = ItemBuilder.from(Material.ARROW)
            .name(Component.text("§b이전 페이지"))
            .asGuiItem {
                it.isCancelled = true
                openBuyOrderAskStock()
            }
        gui.setItem(30, backButton)
        gui.open(player)

    }

    private fun openConfirmBuyOrder(quantity: Int, price: Double) {
        gui = Gui.gui()
            .title(Component.text("구매 제안 확인"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val confirmInfo = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("§e${product.name} §f구매 제안"))
            .lore(
                Component.text("§7수량: §a$quantity"),
                Component.text("§7개당 가격: §a${String.format("%.1f", price)}"),
                Component.text("§7총 가격: §a${String.format("%.1f", quantity * price)}"),
                Component.text(""),
                Component.text("§a클릭하여 구매 제안을 생성합니다.")
            )
            .asGuiItem()

        val confirmButton = ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
            .name(Component.text("§a확인"))
            .asGuiItem {
                it.isCancelled = true
                guiScope.launch(
                    fetch = {
                        bazaarAPI.placeBuyOrder(player.uniqueId.toString(), product.id, quantity, price)
                    },
                    onSuccess = { result ->
                        player.closeInventory()
                        if (result.success) {
                            player.sendMessage(Component.text("§a구매 제안이 성공적으로 생성되었습니다. (ID: ${result.orderId})"))
                        } else {
                            player.sendMessage(Component.text("§c주문 생성 실패: ${result.message}"))
                        }
                    },
                    onError = {
                        player.closeInventory()
                        player.sendMessage(Component.text("§c주문 생성 중 오류가 발생했습니다."))
                    }
                )
            }

        val cancelButton = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
            .name(Component.text("§c취소"))
            .asGuiItem {
                it.isCancelled = true
                open() // 이전 화면으로 돌아가기
            }

        gui.setItem(13, confirmInfo)
        gui.setItem(21, cancelButton)
        gui.setItem(23, confirmButton)

        gui.open(player)
    }

    private fun openInstantSellAskStock() {
        val lore = mutableListOf<Component?>()
        lore.add(Component.text("즉시 판매할 아이템: ${product.name}"))

        gui = Gui.gui()
            .title(Component.text("즉시 판매할 수량을 선택하세요"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val oneStackItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("1세트 (64개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openConfirmInstantSell(64)
            }

        val threeStackItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("3세트 (192개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openConfirmInstantSell(192)
            }

        val fiveStackItem = ItemBuilder.from(Material.CHEST)
            .name(Component.text("Fill Inventory"))
            .lore(lore + Component.text("${ChatColor.GRAY}인벤토리의 해당 아이템 모두 판매"))
            .asGuiItem {
                it.isCancelled = true
                openConfirmInstantSellFillInventory()
            }

        val customItem = ItemBuilder.from(Material.OAK_SIGN)
            .name(Component.text("임의값"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                askQuantityViaChat(
                    onSuccess = { quantity -> openConfirmInstantSell(quantity) },
                    onCancel = { openInstantSellAskStock() }
                )
            }

        val backButton = ItemBuilder.from(Material.ARROW)
            .name(Component.text("§b이전 페이지"))
            .asGuiItem {
                it.isCancelled = true
                open()
            }

        gui.setItem(11, oneStackItem)
        gui.setItem(13, threeStackItem)
        gui.setItem(15, fiveStackItem)
        gui.setItem(17, customItem)
        gui.setItem(30, backButton)
        gui.open(player)
    }

    private fun openConfirmInstantSell(quantity: Int) {
        gui = Gui.gui()
            .title(Component.text("즉시 판매 확인"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val highestBuyOrderPrice = buyOrders.firstOrNull()?.pricePerUnit ?: 0.0

        val confirmInfo = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("§e${product.name} §f즉시 판매"))
            .lore(
                Component.text("§7수량: §a$quantity"),
                Component.text("§7예상 개당 가격: §a~${String.format("%.1f", highestBuyOrderPrice)}"),
                Component.text("§7예상 총 수익: §a~${String.format("%.1f", quantity * highestBuyOrderPrice)}"),
                Component.text(""),
                Component.text("§c(실제 가격은 가장 높은 구매 주문에 따라 결정됩니다)"),
                Component.text("§a클릭하여 즉시 판매합니다.")
            )
            .asGuiItem()

        val confirmButton = ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
            .name(Component.text("§a확인"))
            .asGuiItem {
                it.isCancelled = true
                guiScope.launch(
                    fetch = {
                        bazaarAPI.instantSell(player.uniqueId.toString(), product.id, quantity)
                    },
                    onSuccess = { result ->
                        player.closeInventory()
                        handleInstantSellResult(result, quantity)
                    },
                    onError = { error ->
                        player.closeInventory()
                        player.sendMessage(Component.text("§c즉시 판매 중 시스템 오류가 발생했습니다."))
                        player.sendMessage(Component.text("§7오류: ${error.message}"))
                    }
                )
            }

        val cancelButton = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
            .name(Component.text("§c취소"))
            .asGuiItem {
                it.isCancelled = true
                open() // 이전 화면으로 돌아가기
            }

        gui.setItem(13, confirmInfo)
        gui.setItem(21, cancelButton)
        gui.setItem(23, confirmButton)

        gui.open(player)
    }

    private fun handleInstantSellResult(result: TransactionResult, requestedQuantity: Int) {
        if (result.success) {
            // 성공 메시지
            player.sendMessage(Component.text("§a✓ 판매가 완료되었습니다!"))
            
            // 판매 세부 정보
            val soldQuantity = result.quantityTransacted
            val averagePrice = result.averagePrice
            val totalEarned = result.totalCost // 판매의 경우 totalCost가 수익을 의미
            
            player.sendMessage(Component.text("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
            player.sendMessage(Component.text("§f상품: §e${product.name}"))
            player.sendMessage(Component.text("§f판매 수량: §a${soldQuantity}개"))
            player.sendMessage(Component.text("§f평균 단가: §a${String.format("%.2f", averagePrice)}원"))
            player.sendMessage(Component.text("§f총 수익: §a${String.format("%.2f", totalEarned)}원"))
            
            // 부분 체결 정보
            if (result.partiallyFulfilled) {
                player.sendMessage(Component.text(""))
                player.sendMessage(Component.text("§e⚠ 부분 체결 알림"))
                player.sendMessage(Component.text("§7요청 수량: §c${requestedQuantity}개"))
                player.sendMessage(Component.text("§7실제 판매: §a${soldQuantity}개"))
                player.sendMessage(Component.text("§7미체결 수량: §c${requestedQuantity - soldQuantity}개"))
                player.sendMessage(Component.text("§7사유: 구매 주문 부족"))
            }
            
            player.sendMessage(Component.text("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
            
        } else {
            // 실패 메시지 - 오류 타입별로 다른 메시지 표시
            when (result.errorType) {
                TransactionErrorType.INSUFFICIENT_INVENTORY -> {
                    player.sendMessage(Component.text("§c✗ 아이템이 부족합니다"))
                    player.sendMessage(Component.text("§7요청 수량: §c${result.requestedQuantity}개"))
                    player.sendMessage(Component.text("§7팁: §f인벤토리를 확인하고 충분한 아이템이 있는지 확인하세요"))
                }
                TransactionErrorType.NO_ACTIVE_ORDERS -> {
                    player.sendMessage(Component.text("§c✗ 구매 중인 주문이 없습니다"))
                    if (result.availableQuantity > 0) {
                        player.sendMessage(Component.text("§7구매 가능한 수량: §e${result.availableQuantity}개"))
                        player.sendMessage(Component.text("§7팁: §f더 적은 수량으로 다시 시도해보세요"))
                    } else {
                        player.sendMessage(Component.text("§7현재 이 상품의 구매 주문이 없습니다"))
                        player.sendMessage(Component.text("§7팁: §f나중에 다시 시도하거나 판매 제안을 등록하세요"))
                    }
                }
                TransactionErrorType.INVALID_QUANTITY -> {
                    player.sendMessage(Component.text("§c✗ 잘못된 수량입니다"))
                    player.sendMessage(Component.text("§7요청 수량: §c${result.requestedQuantity}개"))
                    player.sendMessage(Component.text("§7팁: §f1개 이상의 수량을 입력하세요"))
                }
                TransactionErrorType.PARTIAL_FULFILLMENT -> {
                    player.sendMessage(Component.text("§e⚠ 부분 체결"))
                    player.sendMessage(Component.text("§7요청: §c${result.requestedQuantity}개"))
                    player.sendMessage(Component.text("§7판매: §a${result.quantityTransacted}개"))
                    player.sendMessage(Component.text("§7평균 가격: §a${String.format("%.2f", result.averagePrice)}원"))
                }
                TransactionErrorType.SYSTEM_ERROR -> {
                    player.sendMessage(Component.text("§c✗ 시스템 오류"))
                    player.sendMessage(Component.text("§7${result.message}"))
                    player.sendMessage(Component.text("§7팁: §f잠시 후 다시 시도하거나 관리자에게 문의하세요"))
                }
                else -> {
                    player.sendMessage(Component.text("§c✗ 판매 실패"))
                    player.sendMessage(Component.text("§7사유: ${result.message}"))
                }
            }
        }
    }

    private fun openConfirmInstantSellFillInventory() {
        val emptySlots = getEmptyInventorySlots()
        val quantity = emptySlots * 64

        gui = Gui.gui()
            .title(Component.text("Fill Inventory"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val confirmInfo = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("§e${product.name} §fFill Inventory"))
            .lore(
                Component.text("§7인벤토리 빈 공간만큼 판매"),
                Component.text("§7예상 수량: §a$quantity"),
                Component.text(""),
                Component.text("§c주의: 거래 시점에 다시 인벤토리를 확인합니다."),
                Component.text("§a클릭하여 Fill Inventory합니다.")
            )
            .asGuiItem()

        val confirmButton = ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
            .name(Component.text("§a확인"))
            .asGuiItem {
                it.isCancelled = true
                guiScope.launch(
                    fetch = {
                        // 거래 시점에 다시 빈 슬롯 확인
                        val finalEmptySlots = getEmptyInventorySlots()
                        if (finalEmptySlots > 0) {
                            val finalQuantity = finalEmptySlots * 64
                            bazaarAPI.instantSell(player.uniqueId.toString(), product.id, finalQuantity)
                        } else {
                            throw Exception("인벤토리에 빈 공간이 없습니다.")
                        }
                    },
                    onSuccess = { result ->
                        player.closeInventory()
                        if (result.success) {
                            player.sendMessage(Component.text("§a성공적으로 Fill Inventory했습니다!"))
                            player.sendMessage(Component.text("§7Fill Inventory한 수량: §e${result.quantityTransacted}"))
                            player.sendMessage(Component.text("§7평균 단가: §e${String.format("%.2f", result.averagePrice)}"))
                            player.sendMessage(Component.text("§7총 수익: §e${String.format("%.2f", (result.quantityTransacted ?: 0) * (result.averagePrice ?: 0.0))}"))
                        } else {
                            player.sendMessage(Component.text("§cFill Inventory 실패: ${result.message}"))
                        }
                    },
                    onError = {
                        player.closeInventory()
                        player.sendMessage(Component.text("§cFill Inventory 중 오류가 발생했습니다."))
                    }
                )
            }

        val cancelButton = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
            .name(Component.text("§c취소"))
            .asGuiItem {
                it.isCancelled = true
                open() // 이전 화면으로 돌아가기
            }

        gui.setItem(13, confirmInfo)
        gui.setItem(21, cancelButton)
        gui.setItem(23, confirmButton)

        gui.open(player)
    }

    private fun openSellOfferAskStock() {
        val lore = mutableListOf<Component?>()
        lore.add(Component.text("판매할 아이템: ${product.name}"))

        gui = Gui.gui()
            .title(Component.text("판매할 수량을 선택하세요"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val oneStackItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("1세트 (64개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openSellOfferAskAmount(64)
            }

        val threeStackItem = ItemBuilder.from(Material.CHEST)
            .name(Component.text("3세트 (192개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openSellOfferAskAmount(192)
            }

        val fiveStackItem = ItemBuilder.from(Material.CHEST)
            .name(Component.text("5세트 (320개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openSellOfferAskAmount(320)
            }

        val customItem = ItemBuilder.from(Material.OAK_SIGN)
            .name(Component.text("임의값"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                askQuantityViaChat(
                    onSuccess = { quantity -> openSellOfferAskAmount(quantity) },
                    onCancel = { openSellOfferAskStock() }
                )
            }

        val backButton = ItemBuilder.from(Material.ARROW)
            .name(Component.text("§b이전 페이지"))
            .asGuiItem {
                it.isCancelled = true
                open()
            }

        gui.setItem(10, oneStackItem)
        gui.setItem(12, threeStackItem)
        gui.setItem(14, fiveStackItem)
        gui.setItem(16, customItem)
        gui.setItem(30, backButton)
        gui.open(player)
    }

    private fun openSellOfferAskAmount(quantity: Int) {
        val lore = mutableListOf<Component?>()
        lore.add(Component.text("판매할 아이템: ${product.name}"))

        gui = Gui.gui()
            .title(Component.text("개당 얼마에 판매하고 싶으십니까?"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val highestBuyOrder = buyOrders.firstOrNull()
        val lowestSellOffer = sellOffers.firstOrNull()

        val sameAsLowestItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("최저 판매 제안과 같은 가격"))
            .lore(
                Component.text("§7현재 최저 판매가: §e${lowestSellOffer?.pricePerUnit ?: "없음"}"),
                Component.text("§e클릭하여 이 가격으로 판매 제안")
            )
            .asGuiItem {
                it.isCancelled = true
                val price = lowestSellOffer?.pricePerUnit
                if (price != null) {
                    openConfirmSellOffer(quantity, price)
                } else {
                    player.sendMessage(Component.text("§c최저 판매가가 없어 가격을 설정할 수 없습니다."))
                    open()
                }
            }

        val lowestOfferMinus1Item = ItemBuilder.from(Material.GOLD_NUGGET)
            .name(Component.text("최저가 - 1 코인"))
            .lore(
                Component.text("§7현재 최저 판매가: §e${lowestSellOffer?.pricePerUnit ?: "없음"}"),
                Component.text("§7제안할 가격: §e${lowestSellOffer?.let { String.format("%.1f", it.pricePerUnit - 1.0) } ?: "없음"}"),
                Component.text("§e클릭하여 이 가격으로 판매 제안")
            )
            .asGuiItem {
                it.isCancelled = true
                val price = lowestSellOffer?.let { it.pricePerUnit - 1.0 }
                if (price != null && price > 0) {
                    openConfirmSellOffer(quantity, price)
                } else {
                    player.sendMessage(Component.text("§c0보다 큰 가격으로만 제안할 수 있습니다."))
                    open()
                }
            }

        val perOfSpreadItem = ItemBuilder.from(Material.GOLDEN_HORSE_ARMOR)
            .name(Component.text("중간 가격에 제안"))
            .lore(
                Component.text("§7최고 구매가: §e${highestBuyOrder?.pricePerUnit ?: "없음"}"),
                Component.text("§7최저 판매가: §e${lowestSellOffer?.pricePerUnit ?: "없음"}"),
                Component.text("§7제안할 가격: §e${
                    if (highestBuyOrder != null && lowestSellOffer != null && lowestSellOffer.pricePerUnit > highestBuyOrder.pricePerUnit) {
                        String.format("%.1f", (highestBuyOrder.pricePerUnit + lowestSellOffer.pricePerUnit) / 2)
                    } else {
                        "계산 불가"
                    }
                }"),
                Component.text("§e클릭하여 이 가격으로 판매 제안")
            )
            .asGuiItem {
                it.isCancelled = true
                val highestBuy = buyOrders.firstOrNull()
                val lowestSell = sellOffers.firstOrNull()
                if (highestBuy != null && lowestSell != null && lowestSell.pricePerUnit > highestBuy.pricePerUnit) {
                    val price = (highestBuy.pricePerUnit + lowestSell.pricePerUnit) / 2
                    openConfirmSellOffer(quantity, price)
                } else {
                    player.sendMessage(Component.text("§c가격 범위를 계산할 수 없어 제안할 수 없습니다."))
                    open()
                }
            }

        val customItem = ItemBuilder.from(Material.OAK_SIGN)
            .name(Component.text("임의값"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                askPriceViaChat(
                    onSuccess = { price -> openConfirmSellOffer(quantity, price) },
                    onCancel = { openSellOfferAskAmount(quantity) }
                )
            }

        gui.setItem(10, sameAsLowestItem)
        gui.setItem(12, lowestOfferMinus1Item)
        gui.setItem(14, perOfSpreadItem)
        gui.setItem(16, customItem)

        val backButton = ItemBuilder.from(Material.ARROW)
            .name(Component.text("§b이전 페이지"))
            .asGuiItem {
                it.isCancelled = true
                openSellOfferAskStock()
            }

        gui.setItem(30, backButton)
        gui.open(player)
    }

    private fun openConfirmSellOffer(quantity: Int, price: Double) {
        gui = Gui.gui()
            .title(Component.text("판매 제안 확인"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val confirmInfo = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("§e${product.name} §f판매 제안"))
            .lore(
                Component.text("§7수량: §a$quantity"),
                Component.text("§7개당 가격: §a${String.format("%.1f", price)}"),
                Component.text("§7총 예상 수익: §a${String.format("%.1f", quantity * price)}"),
                Component.text(""),
                Component.text("§a클릭하여 판매 제안을 생성합니다.")
            )
            .asGuiItem()

        val confirmButton = ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
            .name(Component.text("§a확인"))
            .asGuiItem {
                it.isCancelled = true
                guiScope.launch(
                    fetch = {
                        bazaarAPI.placeSellOffer(player.uniqueId.toString(), product.id, quantity, price)
                    },
                    onSuccess = { result ->
                        player.closeInventory()
                        if (result.success) {
                            player.sendMessage(Component.text("§a판매 제안이 성공적으로 생성되었습니다. (ID: ${result.orderId})"))
                        } else {
                            player.sendMessage(Component.text("§c판매 제안 생성 실패: ${result.message}"))
                        }
                    },
                    onError = {
                        player.closeInventory()
                        player.sendMessage(Component.text("§c판매 제안 생성 중 오류가 발생했습니다."))
                    }
                )
            }

        val cancelButton = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
            .name(Component.text("§c취소"))
            .asGuiItem {
                it.isCancelled = true
                open() // 이전 화면으로 돌아가기
            }

        gui.setItem(13, confirmInfo)
        gui.setItem(21, cancelButton)
        gui.setItem(23, confirmButton)

        gui.open(player)
    }

    private fun openInstantBuyAskStock() {
        val lore = mutableListOf<Component?>()
        lore.add(Component.text("즉시 구매할 아이템: ${product.name}"))

        gui = Gui.gui()
            .title(Component.text("즉시 구매할 수량을 선택하세요"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val oneStackItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("1세트 (64개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openConfirmInstantBuy(64)
            }

        val threeStackItem = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("3세트 (192개)"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                openConfirmInstantBuy(192)
            }

        val fiveStackItem = ItemBuilder.from(Material.CHEST)
            .name(Component.text("Fill Inventory"))
            .lore(lore + Component.text("${ChatColor.GRAY}인벤토리의 빈 공간만큼 구매"))
            .asGuiItem {
                it.isCancelled = true
                openConfirmInstantBuyFillInventory()
            }

        val customItem = ItemBuilder.from(Material.OAK_SIGN)
            .name(Component.text("임의값"))
            .lore(lore)
            .asGuiItem {
                it.isCancelled = true
                askQuantityViaChat(
                    onSuccess = { quantity -> openConfirmInstantBuy(quantity) },
                    onCancel = { openInstantBuyAskStock() }
                )
            }

        val backButton = ItemBuilder.from(Material.ARROW)
            .name(Component.text("§b이전 페이지"))
            .asGuiItem {
                it.isCancelled = true
                open()
            }

        gui.setItem(10, oneStackItem)
        gui.setItem(12, threeStackItem)
        gui.setItem(14, fiveStackItem)
        gui.setItem(16, customItem)
        gui.setItem(30, backButton)
        gui.open(player)
    }

    private fun openConfirmInstantBuy(quantity: Int) {
        gui = Gui.gui()
            .title(Component.text("즉시 구매 확인"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val lowestSellOfferPrice = sellOffers.firstOrNull()?.pricePerUnit ?: 0.0

        val confirmInfo = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("§e${product.name} §f즉시 구매"))
            .lore(
                Component.text("§7수량: §a$quantity"),
                Component.text("§7예상 개당 가격: §a~${String.format("%.1f", lowestSellOfferPrice)}"),
                Component.text("§7예상 총 비용: §a~${String.format("%.1f", quantity * lowestSellOfferPrice)}"),
                Component.text(""),
                Component.text("§c(실제 가격은 가장 낮은 판매 제안에 따라 결정됩니다)"),
                Component.text("§a클릭하여 즉시 구매합니다.")
            )
            .asGuiItem()

        val confirmButton = ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
            .name(Component.text("§a확인"))
            .asGuiItem {
                it.isCancelled = true
                guiScope.launch(
                    fetch = {
                        bazaarAPI.instantBuy(player.uniqueId.toString(), product.id, quantity)
                    },
                    onSuccess = { result ->
                        player.closeInventory()
                        handleInstantBuyResult(result, quantity)
                    },
                    onError = { error ->
                        player.closeInventory()
                        player.sendMessage(Component.text("§c즉시 구매 중 시스템 오류가 발생했습니다."))
                        player.sendMessage(Component.text("§7오류: ${error.message}"))
                    }
                )
            }

        val cancelButton = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
            .name(Component.text("§c취소"))
            .asGuiItem {
                it.isCancelled = true
                open() // 이전 화면으로 돌아가기
            }

        gui.setItem(13, confirmInfo)
        gui.setItem(21, cancelButton)
        gui.setItem(23, confirmButton)

        gui.open(player)
    }

    private fun handleInstantBuyResult(result: TransactionResult, requestedQuantity: Int) {
        if (result.success) {
            // 성공 메시지
            player.sendMessage(Component.text("§a✓ 구매가 완료되었습니다!"))
            
            // 구매 세부 정보
            val purchasedQuantity = result.quantityTransacted
            val averagePrice = result.averagePrice
            val totalCost = result.totalCost
            
            player.sendMessage(Component.text("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
            player.sendMessage(Component.text("§f상품: §e${product.name}"))
            player.sendMessage(Component.text("§f구매 수량: §a${purchasedQuantity}개"))
            player.sendMessage(Component.text("§f평균 단가: §a${String.format("%.2f", averagePrice)}원"))
            player.sendMessage(Component.text("§f총 비용: §a${String.format("%.2f", totalCost)}원"))
            
            // 부분 체결 정보
            if (result.partiallyFulfilled) {
                player.sendMessage(Component.text(""))
                player.sendMessage(Component.text("§e⚠ 부분 체결 알림"))
                player.sendMessage(Component.text("§7요청 수량: §c${requestedQuantity}개"))
                player.sendMessage(Component.text("§7실제 구매: §a${purchasedQuantity}개"))
                player.sendMessage(Component.text("§7미체결 수량: §c${requestedQuantity - purchasedQuantity}개"))
                player.sendMessage(Component.text("§7사유: 판매 제안 부족"))
            }
            
            player.sendMessage(Component.text("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
            
        } else {
            // 실패 메시지 - 오류 타입별로 다른 메시지 표시
            when (result.errorType) {
                TransactionErrorType.INSUFFICIENT_FUNDS -> {
                    player.sendMessage(Component.text("§c✗ 재화가 부족합니다"))
                    player.sendMessage(Component.text("§7필요 금액: §e${String.format("%.2f", result.totalCost)}원"))
                    player.sendMessage(Component.text("§7팁: §f/money 명령어로 잔액을 확인하세요"))
                }
                TransactionErrorType.NO_ACTIVE_ORDERS -> {
                    player.sendMessage(Component.text("§c✗ 판매 중인 상품이 없습니다"))
                    if (result.availableQuantity > 0) {
                        player.sendMessage(Component.text("§7이용 가능한 수량: §e${result.availableQuantity}개"))
                        player.sendMessage(Component.text("§7팁: §f더 적은 수량으로 다시 시도해보세요"))
                    } else {
                        player.sendMessage(Component.text("§7현재 이 상품의 판매 제안이 없습니다"))
                        player.sendMessage(Component.text("§7팁: §f나중에 다시 시도하거나 구매 주문을 등록하세요"))
                    }
                }
                TransactionErrorType.INVALID_QUANTITY -> {
                    player.sendMessage(Component.text("§c✗ 잘못된 수량입니다"))
                    player.sendMessage(Component.text("§7요청 수량: §c${result.requestedQuantity}개"))
                    player.sendMessage(Component.text("§7팁: §f1개 이상의 수량을 입력하세요"))
                }
                TransactionErrorType.PARTIAL_FULFILLMENT -> {
                    player.sendMessage(Component.text("§e⚠ 부분 체결"))
                    player.sendMessage(Component.text("§7요청: §c${result.requestedQuantity}개"))
                    player.sendMessage(Component.text("§7구매: §a${result.quantityTransacted}개"))
                    player.sendMessage(Component.text("§7평균 가격: §a${String.format("%.2f", result.averagePrice)}원"))
                }
                TransactionErrorType.SYSTEM_ERROR -> {
                    player.sendMessage(Component.text("§c✗ 시스템 오류"))
                    player.sendMessage(Component.text("§7${result.message}"))
                    player.sendMessage(Component.text("§7팁: §f잠시 후 다시 시도하거나 관리자에게 문의하세요"))
                }
                else -> {
                    player.sendMessage(Component.text("§c✗ 구매 실패"))
                    player.sendMessage(Component.text("§7사유: ${result.message}"))
                }
            }
        }
    }

    private fun openConfirmInstantBuyFillInventory() {
        val emptySlots = getEmptyInventorySlots()
        val quantity = emptySlots * 64

        gui = Gui.gui()
            .title(Component.text("Fill Inventory"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem { it.isCancelled = true }
        (0 until 36).forEach { i -> gui.setItem(i, borderItem) }

        val confirmInfo = ItemBuilder.from(Material.valueOf(product.id))
            .name(Component.text("§e${product.name} §fFill Inventory"))
            .lore(
                Component.text("§7인벤토리 빈 공간만큼 구매"),
                Component.text("§7예상 수량: §a$quantity"),
                Component.text(""),
                Component.text("§c주의: 거래 시점에 다시 인벤토리를 확인합니다."),
                Component.text("§a클릭하여 Fill Inventory합니다.")
            )
            .asGuiItem()

        val confirmButton = ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
            .name(Component.text("§a확인"))
            .asGuiItem {
                it.isCancelled = true
                guiScope.launch(
                    fetch = {
                        // 거래 시점에 다시 빈 슬롯 확인
                        val finalEmptySlots = getEmptyInventorySlots()
                        if (finalEmptySlots > 0) {
                            val finalQuantity = finalEmptySlots * 64
                            bazaarAPI.instantBuy(player.uniqueId.toString(), product.id, finalQuantity)
                        } else {
                            throw Exception("인벤토리에 빈 공간이 없습니다.")
                        }
                    },
                    onSuccess = { result ->
                        player.closeInventory()
                        if (result.success) {
                            player.sendMessage(Component.text("§a성공적으로 Fill Inventory했습니다!"))
                            player.sendMessage(Component.text("§7Fill Inventory한 수량: §e${result.quantityTransacted}"))
                            player.sendMessage(Component.text("§7평균 단가: §e${String.format("%.2f", result.averagePrice)}"))
                            player.sendMessage(Component.text("§7총 비용: §e${String.format("%.2f", (result.quantityTransacted ?: 0) * (result.averagePrice ?: 0.0))}"))
                        } else {
                            player.sendMessage(Component.text("§cFill Inventory 실패: ${result.message}"))
                        }
                    },
                    onError = {
                        player.closeInventory()
                        player.sendMessage(Component.text("§cFill Inventory 중 오류가 발생했습니다."))
                    }
                )
            }

        val cancelButton = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
            .name(Component.text("§c취소"))
            .asGuiItem {
                it.isCancelled = true
                open() // 이전 화면으로 돌아가기
            }

        gui.setItem(13, confirmInfo)
        gui.setItem(21, cancelButton)
        gui.setItem(23, confirmButton)

        gui.open(player)
    }

    /**
     * 수량 입력을 위한 채팅 리스너
     */
    private fun askQuantityViaChat(onSuccess: (Int) -> Unit, onCancel: () -> Unit = { open() }) {
        player.closeInventory()
        player.sendMessage(Component.text("${ChatColor.GREEN}채팅으로 수량을 입력하세요. 취소하려면 '취소'를 입력하세요."))
        
        val chatListener = object : Listener {
            @EventHandler
            fun onPlayerChat(event: AsyncPlayerChatEvent) {
                if (event.player.uniqueId != player.uniqueId) return
                
                event.isCancelled = true
                
                if (event.message.lowercase() == "취소" || event.message.lowercase() == "cancel") {
                    HandlerList.unregisterAll(this)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        onCancel()
                    })
                    return
                }
                
                try {
                    val quantity = event.message.toInt()
                    if (quantity <= 0) {
                        player.sendMessage(Component.text("${ChatColor.RED}0보다 큰 수량을 입력해야 합니다. 다시 시도하세요."))
                        return
                    }
                    
                    HandlerList.unregisterAll(this)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        onSuccess(quantity)
                    })
                } catch (e: NumberFormatException) {
                    player.sendMessage(Component.text("${ChatColor.RED}유효한 숫자를 입력해야 합니다. 다시 시도하세요."))
                }
            }
            
            @EventHandler
            fun onPlayerQuit(event: PlayerQuitEvent) {
                if (event.player.uniqueId == player.uniqueId) {
                    HandlerList.unregisterAll(this)
                }
            }
        }
        
        Bukkit.getPluginManager().registerEvents(chatListener, plugin)
    }
    
    /**
     * 가격 입력을 위한 채팅 리스너
     */
    private fun askPriceViaChat(onSuccess: (Double) -> Unit, onCancel: () -> Unit = { open() }) {
        player.closeInventory()
        player.sendMessage(Component.text("${ChatColor.GREEN}채팅으로 가격을 입력하세요. 취소하려면 '취소'를 입력하세요."))
        
        val chatListener = object : Listener {
            @EventHandler
            fun onPlayerChat(event: AsyncPlayerChatEvent) {
                if (event.player.uniqueId != player.uniqueId) return
                
                event.isCancelled = true
                
                if (event.message.lowercase() == "취소" || event.message.lowercase() == "cancel") {
                    HandlerList.unregisterAll(this)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        onCancel()
                    })
                    return
                }
                
                try {
                    val price = event.message.toDouble()
                    if (price <= 0) {
                        player.sendMessage(Component.text("${ChatColor.RED}0보다 큰 가격을 입력해야 합니다. 다시 시도하세요."))
                        return
                    }
                    
                    HandlerList.unregisterAll(this)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        onSuccess(price)
                    })
                } catch (e: NumberFormatException) {
                    player.sendMessage(Component.text("${ChatColor.RED}유효한 숫자를 입력해야 합니다. 다시 시도하세요."))
                }
            }
            
            @EventHandler
            fun onPlayerQuit(event: PlayerQuitEvent) {
                if (event.player.uniqueId == player.uniqueId) {
                    HandlerList.unregisterAll(this)
                }
            }
        }
        
        Bukkit.getPluginManager().registerEvents(chatListener, plugin)
    }

    private fun getItemCountInInventory(): Int {
        var itemCount = 0
        for (i in 0 until 36) {
            val item = player.inventory.getItem(i)
            if (item != null && item.type == Material.valueOf(product.id)) {
                itemCount += item.amount
            }
        }
        return itemCount
    }

    private fun getEmptyInventorySlots(): Int {
        var emptySlots = 0
        for (i in 0 until 36) {
            if (player.inventory.getItem(i) == null) {
                emptySlots++
            }
        }
        return emptySlots
    }
}