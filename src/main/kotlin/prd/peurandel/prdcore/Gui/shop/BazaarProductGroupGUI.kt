package prd.peurandel.prdcore.Gui.shop

import com.mongodb.client.MongoDatabase
import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import kotlinx.coroutines.Job
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.GuiScope
import prd.peurandel.prdcore.Manager.BazaarAPI
import prd.peurandel.prdcore.Manager.ProductGroupInfo

class BazaarProductGroupGUI(
    private val player: Player,
    private val plugin: JavaPlugin,
    private val bazaarAPI: BazaarAPI,
    private val productGroup: ProductGroupInfo) {

    private val guiScope = GuiScope(plugin) // 1. 만능 비동기 실행기 인스턴스 생성

    private val productsSlotList = productGroup.products.map { it.slot }

    private val gui = Gui.gui()
        .title(Component.text("${productGroup.categoryId} -> ${productGroup.name}"))
        .rows(productGroup.row)
        .disableAllInteractions()
        .create()

    fun open() {

        val borderItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).
        name(Component.text(" ")).asGuiItem{it.isCancelled = true}
        guiScope.launch(
            onLoading = {
                for ( i in 0 until productGroup.row*9) {
                    gui.setItem(i, borderItem)
                }
                setupStaticItems()
                gui.open(player)
            },
            fetch = {
                bazaarAPI.getProductsByProductGroup(productGroup.id)
            },
            onSuccess = { products ->
                productsSlotList.forEach { i ->
                    val product = products.find { it.id == productGroup.products.find { it.slot == i }!!.productId } ?: return@forEach

                    val productItem = ItemBuilder.from(Material.valueOf(product.id)).
                    name(Component.text(product.name)).
                    lore(product.description?.let { listOf(Component.text("§7$it")) } ?: emptyList()).
                    asGuiItem() {
                        it.isCancelled = true
                        BazaarProductGUI(player, plugin, bazaarAPI, productGroup, product).open()
                    }
                    gui.setItem(i, productItem)
                }
                gui.update()
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
        val backButton = ItemBuilder.from(Material.ARROW)
            .name(Component.text("§b이전 페이지"))
            .asGuiItem {
                it.isCancelled = true
                BazaarShopGUI(plugin,player, bazaarAPI).open()
            }
        gui.setItem(productGroup.row*9-6, backButton)
        gui.setItem(productGroup.row*9-5, closeButton)

    }
}