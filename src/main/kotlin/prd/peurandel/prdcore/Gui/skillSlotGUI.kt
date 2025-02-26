package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button
import prd.peurandel.prdcore.Manager.SuitManager
import prd.peurandel.prdcore.Manager.WardrobeItem

class skillSlotGUI(plugin: JavaPlugin, database: MongoDatabase, suitUUID: String, slot: Byte) : BaseGUI(plugin, "Suit Skill Slot", 27) {
    private val database = database
    val slot = slot
    val suitUUID = suitUUID

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        for (i in 0..26) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val playerUUID = Bukkit.getOfflinePlayer(player).player?.uniqueId.toString()
        val item: ItemStack = GUIInfo(playerUUID)

        val wardrobe = SuitManager(plugin,database).getWardrobeItem(playerUUID,getSuitUUID(inventory))
        val selectedSkills = when (slot.toInt()) {
            0 -> wardrobe?.skill?.slot0
            1 -> wardrobe?.skill?.slot1
            2 -> wardrobe?.skill?.slot2
            3 -> wardrobe?.skill?.slot3
            4 -> wardrobe?.skill?.slot4
            5 -> wardrobe?.skill?.slot5
            6 -> wardrobe?.skill?.slot6
            7 -> wardrobe?.skill?.slot7
            8 -> wardrobe?.skill?.slot8
            else -> null
        }
        inventory.setItem(21, Button().GoBack(plugin, "To Module Menu"))
        inventory.setItem(22, item)
    }

    override fun onInventoryClick(event: InventoryClickEvent) {
        val clickedItem = event.currentItem
        event.whoClicked.sendMessage("It's Skill Slot")
        event.isCancelled = true
    }


    fun GUIInfo(uuid: String): ItemStack {
        val item: ItemStack = ItemStack(Material.BARRIER)
        val meta: ItemMeta = item.itemMeta
        val keyButton = NamespacedKey(plugin, "button")
        val keyUserUUID = NamespacedKey(plugin, "userUUID")
        val keySuitUUID = NamespacedKey(plugin, "suitUUID")
        val keySlot = NamespacedKey(plugin, "suitSlot")
        meta.persistentDataContainer.set(keyButton, PersistentDataType.STRING, "close")
        meta.persistentDataContainer.set(keyUserUUID, PersistentDataType.STRING, uuid)
        meta.persistentDataContainer.set(keySuitUUID, PersistentDataType.STRING, suitUUID)
        meta.persistentDataContainer.set(keySlot, PersistentDataType.BYTE, slot)
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }
    fun getSuitUUID(inventory: Inventory): String {
        val keySuitUUID = NamespacedKey(plugin,"suitUUID")
        val suitUUID = getInfoItem(inventory)?.itemMeta?.persistentDataContainer?.get(keySuitUUID,PersistentDataType.STRING) as String
        return suitUUID
    }
    fun getInfoItem(inventory: Inventory): ItemStack? {
        return inventory.getItem(49)
    }
}