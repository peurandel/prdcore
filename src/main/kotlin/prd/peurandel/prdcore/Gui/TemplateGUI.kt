package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button

class TemplateGUI(plugin: JavaPlugin,database: MongoDatabase) : BaseGUI(plugin,"Template GUI",27) {
    private val database = database

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        for (i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(48, Button().GoBack(plugin, "To Module Menu"))
        inventory.setItem(49, item)
    }

    override fun onInventoryClick(event: InventoryClickEvent) {
        val clickedItem = event.currentItem
        event.whoClicked.sendMessage("YOU CLICKED!")
        event.isCancelled = true
    }


    fun GUIInfo(uuid: String): ItemStack {
        val item: ItemStack = ItemStack(Material.BARRIER)
        val meta: ItemMeta = item.itemMeta
        val keyButton = NamespacedKey(plugin, "button")
        val keyUserUUID = NamespacedKey(plugin, "userUUID")
        val keySuitUUID = NamespacedKey(plugin, "suitUUID")
        meta.persistentDataContainer.set(keyButton, PersistentDataType.STRING, "close")
        meta.persistentDataContainer.set(keyUserUUID, PersistentDataType.STRING, uuid)
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }
}