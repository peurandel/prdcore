package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button


class MainGUI(plugin: JavaPlugin, private var database: MongoDatabase) : BaseGUI(plugin,"Main Menu",54) {
    override fun initializeItems(plugin: JavaPlugin, player: String) {
        for(i in 0..53) {
            inventory.setItem(i,ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        inventory.setItem(19,Button().Research(plugin))
        inventory.setItem(13,Button().ProfileHead(player))
        inventory.setItem(25,Button().Wardrobe(plugin))


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
            "profile" -> {
                player.sendMessage("It's Profile")
            }
            "research" -> {
                ResearchGUI(plugin,database).open(plugin,player)
            }
            "wardrobe" -> {
                WardrobeGUI(plugin,database).open(plugin,player)
            }
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
}