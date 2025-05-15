package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.serialization.json.Json
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
import prd.peurandel.prdcore.Manager.*
import java.util.*

class ResearchArmorGUI(plugin: JavaPlugin, private var database: MongoDatabase) : BaseGUI(plugin,"Research Armor",54) {
    val playerCollection = database.getCollection("users")
    val serverCollection = database.getCollection("server")
    val armor = Json.decodeFromString<ResearchArmor>(serverCollection.find(Filters.eq("name", "ArmorType")).first().toJson())

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        val playerCollection = database.getCollection("users")
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player)).first().toJson())

        for(i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(4, Button().Armor(plugin))
        for(i in armor.armor.indices) {
            inventory.setItem(i+9, getResearch(user,armor.armor[i]))
        }
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
    fun getResearch(user: User, Armor: ArmorType): ItemStack{

        // research가 있긴한데 item이 없으면 air return
        if(Armor.item == null) { return ItemStack(Material.AIR)}
        val isResearch = isResearch(user.research.armor,Armor.type)

        val itemStack: ItemStack = ItemStack(Material.BARRIER) //ItemSerialization.deserializeItemStack(itemDoc.getString("item"))
        val itemMeta = itemStack.itemMeta

        if (itemMeta != null) {
            addEnchantments(itemMeta, isResearch)
            setItemMetaDetails(itemMeta, isResearch,Armor.type)
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

    private fun setItemMetaDetails(itemMeta: ItemMeta, isResearch: Boolean,type: String) {
        val armorSet = armor.armor.find {it.type==type} as ArmorType
        val itemType = armorSet.type

        val weight = armorSet.weight
        val duration = armorSet.duration
        val armor = armorSet.armor
        val cost = armorSet.cost

        val lore: MutableList<String> = armorSet.lore as MutableList<String>
        lore.add((if (weight == 0) ChatColor.YELLOW else if (weight < 0) ChatColor.BLUE else ChatColor.RED).toString() + "Weight: " + weight+"%")
        lore.add((if (duration == 0) ChatColor.YELLOW else if (duration > 0) ChatColor.BLUE else ChatColor.RED).toString() + "Duration: " + duration+"%")
        lore.add((if (armor == 0) ChatColor.YELLOW else if (armor > 0) ChatColor.BLUE else ChatColor.RED).toString() + "Armor: " + armor+"%")
        lore.add((if (cost == 0) ChatColor.YELLOW else if (cost < 0) ChatColor.BLUE else ChatColor.RED).toString() + "Cost: " + cost+"%")
        if (!isResearch) {
            val require_ex = armorSet.requireEx
            //val require_engine = armorSet.require TODO: 언젠가 ArmorType Require Engine 부분 추가해야함

            lore.add("${ChatColor.RED}Require Exp: ${require_ex}")
            //lore.add("${ChatColor.RED}Require Engine: ${require_engine}") TODO: 언젠가 ArmorType Require Engine 부분 추가해야함
        }

        val key = NamespacedKey(plugin,"name")
        itemMeta.persistentDataContainer.set(key, PersistentDataType.STRING,type)
        itemMeta.setDisplayName(ChatColor.GREEN.toString() + itemType)
        itemMeta.lore = lore
    }


    fun isResearch(research: List<ArmorType>, type: String): Boolean{
        for(researchSet in research) {
            if(researchSet.type==type) {
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
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player.name)).first().toJson())
        val key = NamespacedKey(plugin,"name")
        val itemType = item.itemMeta.persistentDataContainer.get(key, PersistentDataType.STRING) as String
        // val researchEngineList: List<String> = researchDoc["engine"] as List<String> TODO: 언젠가 ArmorType Require Engine 부분 추가해야함
        val researchList: MutableList<ArmorType> = user.research.armor.toMutableList()

        val isResearch = isResearch(user.research.armor,itemType)

        if(isResearch) {
            player.sendMessage("${ChatColor.RED}이미 연구된 항목입니다.")
            return
        }
        val armorSet = armor.armor.find {it.type == itemType} as ArmorType

        val requireEx = armorSet.requireEx
        // val requireEngine = armorSet.require_engine TODO: 언젠가 ArmorType Require Engine 부분 추가해야함

        var exp = user.research_point

        if(requireEx <= exp /*&& researchEngineList.contains(requireEngine)*/) { // TODO: 언젠가 ArmorType Require Engine 부분 추가해야함
            exp -= requireEx


            researchList.add(armorSet)
            val serializedList = researchList.map {
                Document.parse(Json.encodeToString(it))
            }

            playerCollection.updateOne(
                Filters.eq<String>("name", player.name),
                Updates.combine(
                    Updates.set("research.armor", serializedList),
                    Updates.set("research_point", exp)
                )
            )
            open(plugin,player)

        } else {
            player.sendMessage("조건 만족 안됨 ㅡㅡ")
        }

    }
}