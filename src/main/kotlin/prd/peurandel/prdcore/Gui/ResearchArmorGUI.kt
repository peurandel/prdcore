package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
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
import java.util.*

class ResearchArmorGUI(plugin: JavaPlugin, private var database: MongoDatabase) : BaseGUI(plugin,"Research Armor",54) {
    val playerCollection = database.getCollection("users")

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        val playerCollection = database.getCollection("users")
        val playerDoc: Document = playerCollection.find(Filters.eq("name",player)).first()

        for(i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(4, Button().Armor(plugin))
        inventory.setItem(10, getResearch(playerDoc,"Homogenous Rolled Armor"))
        inventory.setItem(11, getResearch(playerDoc,"Cast Armor"))
        inventory.setItem(12, getResearch(playerDoc,"Composite Armor"))
        inventory.setItem(13, getResearch(playerDoc,"Reactive Armor"))
        inventory.setItem(14, getResearch(playerDoc,"Spaced Armor"))
        inventory.setItem(15, getResearch(playerDoc,"Nano Armor"))

        inventory.setItem(48, Button().GoBack(plugin,"To Research Menu"))
        inventory.setItem(49,item)
    }


    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as Player
        val rawSlot = event.rawSlot as Int
        if(isVaildSlot(event,rawSlot)) {
            val item = event.currentItem
            val buttonName = item?.let { ButtonName(it) }
            if (item != null && 9<=rawSlot && rawSlot<=45) {
                processArmorButton(player,item)
            }
            if (buttonName != null){
                processButton(player,buttonName)
            }
        }
    }
    fun getResearch(playerDoc: Document, name: String): ItemStack{
        val researchDoc = playerDoc["research"] as Document
        val isResearch = isResearch(researchDoc,name)
        val itemDoc = getItemDocument(name)

        val itemStack: ItemStack = ItemStack(Material.BARRIER)//ItemSerialization.deserializeItemStack(itemDoc.getString("item"))
        val itemMeta = itemStack.itemMeta

        if (itemMeta != null) {
            addEnchantments(itemMeta, isResearch)
            setItemMetaDetails(itemMeta, isResearch, itemDoc,name)
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

    private fun setItemMetaDetails(itemMeta: ItemMeta, isResearch: Boolean, itemDoc: Document,name: String) {
        val itemName = itemDoc.getString("name")
        val itemLore = itemDoc.getString("lore")

        val weight = itemDoc.getInteger("weight")
        val duration = itemDoc.getInteger("duration")
        val armor = itemDoc.getInteger("armor")
        val cost = itemDoc.getInteger("cost")

        val lore: MutableList<String> = ArrayList()
        lore.add(ChatColor.GRAY.toString() + itemLore)
        lore.add((if (weight == 0) ChatColor.YELLOW else if (weight < 0) ChatColor.BLUE else ChatColor.RED).toString() + "Weight: " + weight+"%")
        lore.add((if (duration == 0) ChatColor.YELLOW else if (duration > 0) ChatColor.BLUE else ChatColor.RED).toString() + "Duration: " + duration+"%")
        lore.add((if (armor == 0) ChatColor.YELLOW else if (armor > 0) ChatColor.BLUE else ChatColor.RED).toString() + "Armor: " + armor+"%")
        lore.add((if (cost == 0) ChatColor.YELLOW else if (cost < 0) ChatColor.BLUE else ChatColor.RED).toString() + "Cost: " + cost+"%")
        if (!isResearch) {
            val require_ex = itemDoc.getInteger("require_ex")
            val require_engine = itemDoc.getString("require_engine")

            lore.add("${ChatColor.RED}Require Exp: ${require_ex}")
            lore.add("${ChatColor.RED}Require Engine: ${require_engine}")
        }

        val key = NamespacedKey(plugin,"name")
        itemMeta.persistentDataContainer.set(key, PersistentDataType.STRING,name)
        itemMeta.setDisplayName(ChatColor.GREEN.toString() + itemName)
        itemMeta.lore = lore
    }
    fun getItemDocument(name: String): Document {
        val serverDoc = database.getCollection("server")
        val item = serverDoc.find(Filters.eq("name","item")).first() as Document
        val itemContainer = item["armor"] as Document
        return itemContainer[name] as Document
    }


    fun isResearch(researchDoc: Document, name: String): Boolean{
        val researchList: List<String> = researchDoc["armor"] as List<String>
        //val researchEngineList: List<String> = researchDoc["engine"] as List<String>

        for(research in researchList) {
            if(Objects.equals(research,name)) {
                return true
            }
        }
        return false
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


    fun processArmorButton(player : Player,item: ItemStack) {
        val playerDoc: Document = playerCollection.find(Filters.eq("name",player.name)).first()
        val key = NamespacedKey(plugin,"name")
        val itemName = item.itemMeta.persistentDataContainer.get(key, PersistentDataType.STRING) as String
        val researchDoc = playerDoc["research"] as Document
        val researchList: List<String> = researchDoc["armor"] as List<String>
        val researchEngineList: List<String> = researchDoc["engine"] as List<String>

        val isResearch = isResearch(researchDoc,itemName)

        if(isResearch) {
            return
        }
        val itemDoc = getItemDocument(itemName)

        val requireEx = itemDoc.getInteger("require_ex")
        val requireEngine = itemDoc.getString("require_engine")

        var exp = playerDoc.get("research_point") as Int

        if(requireEx <= exp && researchEngineList.contains(requireEngine)) {
            exp -= requireEx

            val AddedResearchList = researchList.toMutableList()
            AddedResearchList.add(itemName)
            playerCollection.updateOne(
                Filters.eq<String>("name", player.name),
                Updates.combine(
                    Updates.set("research.armor", AddedResearchList),
                    Updates.set("research_point", exp)
                )
            )
            open(plugin,player)

        } else {
            player.sendMessage("조건 만족 안됨 ㅡㅡ")
        }

    }
}