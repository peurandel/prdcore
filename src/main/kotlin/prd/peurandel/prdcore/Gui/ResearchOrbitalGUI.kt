package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
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
import prd.peurandel.prdcore.ItemStack.Button

class ResearchOrbitalGUI(plugin: JavaPlugin, private var database: MongoDatabase) : BaseGUI(plugin,"Research Orbital",54) {

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        for(i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(4, Button().Orbital(plugin))
        inventory.setItem(48, Button().GoBack(plugin,"To Research Menu"))
        inventory.setItem(49,item)
    }


    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val rawSlot = event.rawSlot
        if(isVaildSlot(event,rawSlot)) {
            val item = event.currentItem
            val buttonName = item?.let { ButtonName(it) }
            if (buttonName != null){
                processButton(event.whoClicked as Player,buttonName)
            }
        }
    }

    fun processButton(player : Player, buttonName : String) {
        when(buttonName) {
            "goback" -> {ResearchGUI(plugin,database).open(plugin,player)}
            "close" -> {player.closeInventory()}
        }
    }
    fun ButtonName(item: ItemStack): String? {
        val meta = item.itemMeta
        val key = NamespacedKey(plugin,"button")
        val container = meta.persistentDataContainer
        return container.get(key, PersistentDataType.STRING)
    }
    fun isVaildSlot(event: InventoryClickEvent, rawSlot: Int): Boolean {
        return rawSlot < event.inventory.size
    }

    fun GUIInfo(uuid: String) : ItemStack {
        val item: ItemStack = ItemStack(Material.BARRIER)
        val meta: ItemMeta = item.itemMeta
        val keyButton = NamespacedKey(plugin,"button")
        val keyUserUUID = NamespacedKey(plugin,"userUUID")
        meta.persistentDataContainer.set(keyButton, PersistentDataType.STRING,"close")
        meta.persistentDataContainer.set(keyUserUUID, PersistentDataType.STRING,uuid)
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }
}