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
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button
import prd.peurandel.prdcore.ItemStack.ItemSerialization
import prd.peurandel.prdcore.Manager.SuitManager
import java.util.ArrayList

class ModuleArmorGUI(plugin: JavaPlugin, database: MongoDatabase, suitUUID: String) : BaseGUI(plugin,"Module Armor",54) {
    private val database = database
    private val suitUUID = suitUUID
    val playerCollection = database.getCollection("users")
    val serverCollection = database.getCollection("server")
    val suitManager = SuitManager(plugin, database)
    val itemDoc: Document = serverCollection.find(Filters.eq("name","item")).first()

    override fun initializeItems(plugin: JavaPlugin, player: String) {

        fillInventory(player)

        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(48, Button().GoBack(plugin,"To Module Menu"))
        inventory.setItem(49,item)
    }

    fun fillInventory(player: String) {
        val playerDoc: Document = playerCollection.find(Filters.eq("name",player)).first()
        val itemDoc: Document = serverCollection.find(Filters.eq("name","item")).first()
        val armorDoc: Document = itemDoc["armor"] as Document
        val materialDoc: Document = itemDoc["material"] as Document

        val researchDoc = playerDoc["research"] as Document
        val researchList: List<String> = researchDoc["armor"] as List<String>
        for(i in 0..53) {
            val index = i % 4 + 4 * (i / 9).toInt()
            if(i % 9 == 4) {
                inventory.setItem(i, ItemStack(Material.GRAY_STAINED_GLASS_PANE))
            } else if((i % 9) < 4 && index < researchList.size) {
                inventory.setItem(i, getArmorItem(playerDoc, armorDoc, researchList, index))
            } else {
                inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
            }
        }
        inventory.setItem(5, getMaterialItem(playerDoc,materialDoc,"iron"))
        inventory.setItem(6, getMaterialItem(playerDoc,materialDoc,"copper"))
        inventory.setItem(7, getMaterialItem(playerDoc,materialDoc,"kevlar"))
        inventory.setItem(8, getMaterialItem(playerDoc,materialDoc,"ceramic"))
        inventory.setItem(14, getMaterialItem(playerDoc,materialDoc,"gold"))
        inventory.setItem(15, getMaterialItem(playerDoc,materialDoc,"aluminum"))
        inventory.setItem(16, getMaterialItem(playerDoc,materialDoc,"diamond"))
        inventory.setItem(17, getMaterialItem(playerDoc,materialDoc,"netherite"))
        inventory.setItem(23, getMaterialItem(playerDoc,materialDoc,"titanium"))
        inventory.setItem(24, getMaterialItem(playerDoc,materialDoc,"tungsten"))

    }
    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val rawSlot = event.rawSlot
        val player = event.whoClicked as Player
        if(isVaildSlot(event,rawSlot)) {
            val item = event.currentItem
            val buttonName = item?.let { ButtonName(it) }
            val playerDoc: Document = playerCollection.find(Filters.eq("name",player.name)).first()


            if(item?.let { isArmorProccess(it) } == true) SelectArmorItem(event,player,item,playerDoc)
            if(item?.let { isMaterialProccess(it) } == true) SelectMaterialItem(event,player,item,playerDoc)

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

    fun getMaterialItem(playerDoc: Document, materialDoc: Document,name: String): ItemStack? {
        val valueDoc = materialDoc[name] as Document

        val item = valueDoc.getString("item")
        val itemStack = ItemSerialization.deserializeItemStack(item)
        val meta = itemStack.itemMeta

        val key = NamespacedKey(plugin,"material")
        val processkey = NamespacedKey(plugin,"process")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,name)
        meta.persistentDataContainer.set(processkey, PersistentDataType.STRING,"material")

        if(isSelected(playerDoc,name,"material")) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        }

        itemStack.itemMeta = meta


        return itemStack
    }
    fun getArmorItem(playerDoc:Document, armorDoc: Document, researchList: List<String>,i: Int): ItemStack? {

        val index = i%9 + (i/9)
        val armor: Document = armorDoc[researchList.get(index)] as Document
        //val armoritem = armor.getString("item")
        val armorName = armor.getString("name")

        val item = ItemStack(Material.IRON_CHESTPLATE)

        //아이템 정보 정의
        val meta = item.itemMeta
        meta.setDisplayName(armorName)
        val lore: MutableList<String> = ArrayList()
        meta.lore = lore

        val key = NamespacedKey(plugin,"armor")
        val processkey = NamespacedKey(plugin,"process")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,researchList.get(index))
        meta.persistentDataContainer.set(processkey, PersistentDataType.STRING,"armor")

        if(isSelected(playerDoc,researchList.get(index),"armorName")) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        }

        item.itemMeta = meta
        return item
    }
    fun isSelected(playerDoc: Document, Name: String,type: String): Boolean {
        val wardrobes = playerDoc["wardrobe"] as? List<Document> ?: return false

        for (wardrobeSet in wardrobes) {
            if (wardrobeSet.getString("uuid") == suitUUID) {
                return wardrobeSet.getString(type) == Name
            }
        }
        return false
    }

    fun isArmorProccess(item: ItemStack): Boolean {
        val meta = item.itemMeta
        val key = NamespacedKey(plugin,"process")
        val container = meta.persistentDataContainer
        return container.get(key, PersistentDataType.STRING) == "armor"
    }

    fun isMaterialProccess(item: ItemStack): Boolean {
        val meta = item.itemMeta
        val key = NamespacedKey(plugin,"process")
        val container = meta.persistentDataContainer
        return container.get(key, PersistentDataType.STRING) == "material"
    }


    fun getKey(item: ItemStack, type: String): String? {
        val meta = item.itemMeta
        val key = NamespacedKey(plugin,type)
        val container = meta.persistentDataContainer
        return container.get(key, PersistentDataType.STRING)
    }

    fun SelectMaterialItem(event: InventoryClickEvent, player: Player,item: ItemStack, playerDoc: Document) {

        val ItemKey = getKey(item,"material")
        val MaterialDoc: Document = itemDoc["material"] as Document
        //get WardrobeDoc
        val wardrobeIndex = suitManager.getWardrobeIndex(playerDoc,getSuitUUID(event))
        val wardrobeDoc = suitManager.getWardrobe(player.uniqueId.toString(),getSuitUUID(event)) as Document
        val armor_duration = wardrobeDoc.getInteger("duration")
        val wardrobeArmorDoc: Document = wardrobeDoc["armor"] as Document
        val wardrboeArmorWeight = wardrobeArmorDoc.getInteger("weight").toDouble()


        // Armor: weight, duration, armor, cost
        // weight = 무게
        // duration = 내구도
        // armor = 방어력
        // cost = 가격
        // 이 모든게 비율값임.
        val material: Document = MaterialDoc[ItemKey] as Document
        val weight = material.getDouble("weight")
        val duration = material.getInteger("duration")
        //지금은 안쓰임
        //val Armor = material.getInteger("armor")
        //val cost = material.getInteger("cost")

        playerCollection.updateOne(
            Filters.eq<String>("name", player.name),
            Updates.combine(
                Updates.set("wardrobe.${wardrobeIndex}.material",ItemKey),
                Updates.set("wardrobe.${wardrobeIndex}.max_durability",(duration * 24 * (1.0+ armor_duration/100)).toInt()),
                Updates.set("wardrobe.${wardrobeIndex}.duration", duration),
                //Updates.set("wardrobe.${wardrobeIndex}.cost", cost),
                Updates.set("wardrobe.${wardrobeIndex}.weight", weight*(1.0+wardrboeArmorWeight/100)),
                //Updates.set("wardrobe.${wardrobeIndex}.armor", Armor)

            )
        )
        // 새로 GUI를 오픈해버리면 suitUUID를 소실
        ModuleArmorGUI(plugin,database,getSuitUUID(event)).open(plugin,player)


    }
    fun SelectArmorItem(event: InventoryClickEvent, player: Player,item: ItemStack, playerDoc: Document) {

        val ItemKey = getKey(item,"armor")
        val ArmorDoc: Document = itemDoc["armor"] as Document
        // Armor: weight, duration, armor, cost
        // weight = 무게
        // duration = 내구도
        // armor = 방어력
        // cost = 가격
        // 이 모든게 비율값임.
        val armor: Document = ArmorDoc[ItemKey] as Document
        val weight = armor.getInteger("weight")
        val duration = armor.getInteger("duration")
        val Armor = armor.getInteger("armor")
        val cost = armor.getInteger("cost")

        val wardrobeIndex = suitManager.getWardrobeIndex(playerDoc,getSuitUUID(event))

        playerCollection.updateOne(
            Filters.eq<String>("name", player.name),
            Updates.combine(
                Updates.set("wardrobe.${wardrobeIndex}.armorName",ItemKey),
                Updates.set("wardrobe.${wardrobeIndex}.armor.duration", duration),
                Updates.set("wardrobe.${wardrobeIndex}.armor.cost", cost),
                Updates.set("wardrobe.${wardrobeIndex}.armor.weight", weight),
                Updates.set("wardrobe.${wardrobeIndex}.armor.armor", Armor),
                //material 초기화
                Updates.set("wardrobe.${wardrobeIndex}.material","iron"),
                Updates.set("wardrobe.${wardrobeIndex}.max_durability",(840 * (1.0+ duration /100)).toInt()),
                Updates.set("wardrobe.${wardrobeIndex}.duration",35),
                //Updates.set("wardrobe.${wardrobeIndex}.cost", cost),
                Updates.set("wardrobe.${wardrobeIndex}.weight",(7.87)*(1.0+weight/100)),

            )
        )
        // 새로 GUI를 오픈해버리면 suitUUID를 소실

        ModuleArmorGUI(plugin,database,getSuitUUID(event)).open(plugin,player)
    }
}