package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button
import prd.peurandel.prdcore.ItemStack.ItemSerialization
import java.util.*

class ResearchTechFireArmGUI(plugin: JavaPlugin, database: MongoDatabase) : BaseGUI(plugin,"Research FireArm",54) {
    private val database = database
    val playerCollection = database.getCollection("users")

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        val playerDoc: Document = playerCollection.find(Filters.eq("name",player)).first()

        for (i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(0, getResearch(playerDoc,"full_matel_jacket"))
        inventory.setItem(48, Button().GoBack(plugin, "To Module Menu"))
        inventory.setItem(49, item)
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



    fun getResearch(playerDoc: Document, name: String): ItemStack {

        val itemDoc = getItemDocument(name)

        val itemStack: ItemStack = ItemSerialization.deserializeItemStack(itemDoc.getString("item"))
        val itemMeta = itemStack.itemMeta

        if (itemMeta != null) {
            addEnchantments(itemMeta, isResearch(playerDoc,name))
            setItemMetaDetails(itemMeta, isResearch(playerDoc,name), itemDoc,name)
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            itemStack.setItemMeta(itemMeta)
        }
        return itemStack
    }

    private fun addEnchantments(itemMeta: ItemMeta, isResearch: Boolean) {
        if (isResearch) {
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true)
        }
    }

    private fun setItemMetaDetails(itemMeta: ItemMeta, isResearch: Boolean, itemDoc: Document, name: String) {
        val itemName = itemDoc.getString("name")
        val itemLore = itemDoc.getString("lore")

        val tier = itemDoc.getInteger("tier")
        val lore: MutableList<String> = ArrayList()
        lore.add(ChatColor.GRAY.toString() + itemLore)
        lore.add("${ChatColor.AQUA}요구하는 슈트의 티어: $tier")
        if (!isResearch) {
            val requireEx = itemDoc.getInteger("require_ex")

            lore.add(ChatColor.RED.toString() + "Require Exp: " + requireEx)
        }
        itemMeta.setDisplayName(ChatColor.GREEN.toString() + itemName)
        itemMeta.lore = lore


        val key = NamespacedKey(plugin,"name")
        itemMeta.persistentDataContainer.set(key, PersistentDataType.STRING,name)
    }

    fun isResearch(playerDoc: Document, name: String): Boolean{
        val researchDoc = playerDoc["research"] as Document
        val researchList: List<String> = if (researchDoc.containsKey("firearm") && researchDoc["firearm"] is List<*>) {
            researchDoc["firearm"] as List<String>
        } else {
            // 예외 처리: firearm 키가 없거나 값이 List<String> 타입이 아닌 경우 빈 리스트 반환
            emptyList()
        }
        for(research in researchList) {
            if(Objects.equals(research,name)) {
                return true
            }
        }
        return false
    }
    fun getItemDocument(name: String): Document {
        val serverDoc = database.getCollection("server")
        val item = serverDoc.find(Filters.eq("name","item")).first() as Document
        val techsDoc = item["techs"] as Document
        val itemContainer = techsDoc["firearm"] as Document
        return itemContainer[name] as Document
    }

}
