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

class ResearchGUI(plugin: JavaPlugin, private var database: MongoDatabase) : BaseGUI(plugin,"Research",54) {

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        for(i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(4, Button().Research(plugin))
        inventory.setItem(10, Button().Engine(plugin))
        inventory.setItem(13, Button().Skill(plugin))
        inventory.setItem(16, Button().SoftWare(plugin))
        inventory.setItem(28, Button().Armor(plugin))
        inventory.setItem(31, Button().Magic(plugin))
        inventory.setItem(34, Button().Orbital(plugin))
        inventory.setItem(48, Button().GoBack(plugin,"To Main Menu"))
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
            "magic" -> {ResearchMagicGUI(plugin,database).open(plugin,player)}
            "engine" -> {ResearchEngineGUI(plugin,database).open(plugin,player)}
            "software" -> {ResearchSoftwareGUI(plugin,database).open(plugin,player)}
            "orbital" -> {ResearchOrbitalGUI(plugin,database).open(plugin,player)}
            "skill" -> {ResearchTechGUI(plugin,database).open(plugin,player)}
            "armor" -> {ResearchArmorGUI(plugin,database).open(plugin,player)}
            "goback" -> {MainGUI(plugin,database).open(plugin,player)}
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
        val item = ItemStack(Material.BARRIER)
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
