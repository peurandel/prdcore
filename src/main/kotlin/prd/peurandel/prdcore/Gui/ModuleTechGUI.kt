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

class ModuleTechGUI(plugin: JavaPlugin, database: MongoDatabase, suitUUID: String) : BaseGUI(plugin,"Module Skills",54) {
    private val database = database
    private val suitUUID = suitUUID

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        for(i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(48, Button().GoBack(plugin,"To Module Menu"))
        inventory.setItem(49,item)
    }

    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val rawSlot = event.rawSlot
        if(isVaildSlot(event,rawSlot)) {
            val item = event.currentItem
            val buttonName = item?.let { ButtonName(it) }
            if (buttonName != null){
                processButton(event,event.whoClicked as Player,buttonName)
            }
        }
    }

    fun processButton(event: InventoryClickEvent, player : Player, buttonName : String) {
        when(buttonName) {

            "goback" -> {ModuleGUI(plugin,database,getSuitUUID(event)).open(plugin,player)}
            "close" -> {player.closeInventory()}
        }
    }

    fun getSuitUUID(event: InventoryClickEvent): String {
        val keySuitUUID = NamespacedKey(plugin,"suitUUID")
        val suitUUID = getInfoItem(event)?.itemMeta?.persistentDataContainer?.get(keySuitUUID, PersistentDataType.STRING) as String
        return suitUUID
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
        val keySuitUUID = NamespacedKey(plugin,"suitUUID")
        meta.persistentDataContainer.set(keyButton, PersistentDataType.STRING,"close")
        meta.persistentDataContainer.set(keyUserUUID, PersistentDataType.STRING,uuid)
        meta.persistentDataContainer.set(keySuitUUID, PersistentDataType.STRING,suitUUID)
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }

    fun getInfoItem(event: InventoryClickEvent): ItemStack? {
        return event.inventory.getItem(49)
    }
}