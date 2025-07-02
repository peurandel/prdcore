package prd.peurandel.prdcore.Gui.buildings

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
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.BaseGUI
import prd.peurandel.prdcore.ItemStack.Button
import prd.peurandel.prdcore.ItemStack.ItemSerialization
import prd.peurandel.prdcore.Manager.*
import java.util.*

class armoryGUI(plugin: JavaPlugin, database: MongoDatabase) : BaseGUI(plugin,"무기고",54) {

    private val database = database
    val playerCollection = database.getCollection("users")

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        // 기초적인 Wardrobe 구성
        val wardrobe = WardrobeItem(
            name = player,
            uuid = UUID.randomUUID().toString(),
            engine = "Furnace",
            tier = 1,
            energy = 1000,
            skill = Skill(
                slot0 = ArrayList(),
                slot1 = ArrayList(),
                slot2 = ArrayList(),
                slot3 = ArrayList(),
                slot4 = ArrayList(),
                slot5 = ArrayList(),
                slot6 = ArrayList(),
                slot7 = ArrayList(),
                slot8 = ArrayList()
            ),
            armorName = "Homogenous Rolled Armor",
            material = "iron",
            helmet = null,
            chestplate = null,
            leggings = null,
            boots = null,
            max_energy = 1000,
            armor = Armor(armor = 0, cost = 0, duration = 0, weight = 0),
            duration = 35,
            weight = 7.87,
            max_durability = 840,
            durability = 840
        )
        fillInventory(player, inventory, wardrobe)

    }

    fun testitem(wardrobeSet: WardrobeItem): ItemStack {
        val itemstack = ItemStack(Material.DIAMOND)
        itemstack.lore = listOf("E: ${wardrobeSet.energy}")
        return itemstack
    }
    fun fillInventory(player: String, inv: Inventory, wardrobeSet: WardrobeItem) {

        inv.clear()
        inv.setItem(0, testitem(wardrobeSet))
        inv.setItem(13, if (wardrobeSet.helmet != null) ItemSerialization.deserializeItemStack(wardrobeSet.helmet) else ItemStack(Material.RED_STAINED_GLASS_PANE))
        inv.setItem(22, if (wardrobeSet.chestplate != null) ItemSerialization.deserializeItemStack(wardrobeSet.chestplate) else ItemStack(Material.RED_STAINED_GLASS_PANE))
        inv.setItem(31, if (wardrobeSet.leggings != null) ItemSerialization.deserializeItemStack(wardrobeSet.leggings) else ItemStack(Material.RED_STAINED_GLASS_PANE))
        inv.setItem(40, if (wardrobeSet.boots != null) ItemSerialization.deserializeItemStack(wardrobeSet.boots) else ItemStack(Material.RED_STAINED_GLASS_PANE))

        val item: ItemStack = GUIInfo(wardrobeSet)

        inv.setItem(48, Button().GoBack(plugin, "To Module Menu"))
        inv.setItem(49, item)
    }

    override fun onInventoryClick(event: InventoryClickEvent) {

        event.isCancelled = true
        val clickedItem: ItemStack? = event.currentItem
        val inv = event.inventory
        val wardrobe: WardrobeItem = Json.decodeFromString(getWardrobe(event))
        val player = event.whoClicked as Player
        if(clickedItem!!.type == Material.DIAMOND) {
            wardrobe.energy = wardrobe.energy!! + 1
            player.sendMessage("It's now ${wardrobe.energy}")
            fillInventory(player.name,inv,wardrobe)
        }
    }

    fun getWardrobe(event: InventoryClickEvent): String {
        val wardrobe = NamespacedKey(plugin, "wardrobe")
        val wardrobeSet = getInfoItem(event)?.itemMeta?.persistentDataContainer?.get(wardrobe, PersistentDataType.STRING) as String
        return wardrobeSet
    }

    fun getInfoItem(event: InventoryClickEvent): ItemStack? {
        return event.inventory.getItem(49)
    }
    fun GUIInfo(wardrobeSet: WardrobeItem): ItemStack {
        plugin.logger.info("앙기모222 ${wardrobeSet.energy}")
        val item: ItemStack = ItemStack(Material.BARRIER)
        val meta: ItemMeta = item.itemMeta
        val key = NamespacedKey(plugin, "type")
        val wardrobe = NamespacedKey(plugin, "wardrobe")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "armory")
        meta.persistentDataContainer.set(wardrobe, PersistentDataType.STRING, Json.encodeToString(wardrobeSet))
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }
}