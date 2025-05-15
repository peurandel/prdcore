package prd.peurandel.prdcore.Gui.shop

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.serialization.json.Json
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
import prd.peurandel.prdcore.ItemStack.Button
import prd.peurandel.prdcore.Manager.User
import prd.peurandel.prdcore.Manager.itemInfoMap

class bazaargui(plugin: JavaPlugin, database: MongoDatabase) : BaseGUI(plugin,"커뮤니티 상점",54) {
    private val database = database
    val playerCollection = database.getCollection("users")

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        for (i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(49, item)
    }

    override fun onInventoryClick(event: InventoryClickEvent) {

        event.isCancelled = true
        val clickedItem: ItemStack? = event.currentItem

        val player = event.whoClicked as Player
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player.name)).first().toJson())
        //player.sendMessage("YOU CLICKED! ${getShopType(event)}")
        val clickedSlot = event.rawSlot

        if(clickedSlot in 0..53) { // 상점 아이템
            player.sendMessage("느그 방금 상점 아이템 클릭함 ㅋㅋ")
            //인벤토리
        } else if(clickedSlot >= 0 && clickedSlot < player.inventory.size + (event.inventory?.size ?: 0) && clickedSlot >= event.inventory?.size ?: 0) {
            val itemInfo = if(clickedItem!=null) itemInfoMap.cache[clickedItem.type] else null

            if(itemInfo != null ) {
                val totalprice = itemInfo.price * clickedItem!!.amount
                val money = user.money + totalprice
                clickedItem.amount = 0
                playerCollection.updateOne(
                    Filters.eq<String>("name", player.name),
                    Updates.combine(
                        Updates.set("money",money),
                    )
                )
            } else {
                player.sendMessage("앙기모")
            }
        }


        open(plugin,player)

    }

    fun getShopType(event: InventoryClickEvent): String {
        val shopkey = NamespacedKey(plugin,"shop")
        val shop = getInfoItem(event)?.itemMeta?.persistentDataContainer?.get(shopkey, PersistentDataType.STRING) as String
        return shop
    }

    fun getInfoItem(event: InventoryClickEvent): ItemStack? {
        return event.inventory.getItem(49)
    }
    fun GUIInfo(uuid: String): ItemStack {
        val item: ItemStack = ItemStack(Material.BARRIER)
        val meta: ItemMeta = item.itemMeta
        val key = NamespacedKey(plugin, "type")
        val shopkey = NamespacedKey(plugin, "shop")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "admin_shop")
        meta.persistentDataContainer.set(shopkey, PersistentDataType.STRING, title)
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }
}