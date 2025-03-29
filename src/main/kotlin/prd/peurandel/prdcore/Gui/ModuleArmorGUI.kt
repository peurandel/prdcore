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
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button
import prd.peurandel.prdcore.ItemStack.ItemSerialization
import prd.peurandel.prdcore.Manager.*
import java.util.ArrayList

class ModuleArmorGUI(plugin: JavaPlugin, database: MongoDatabase, suitUUID: String) : BaseGUI(plugin,"Module Armor",54) {
    private val database = database
    private val suitUUID = suitUUID
    val playerCollection = database.getCollection("users")
    val serverCollection = database.getCollection("server")
    val suitManager = SuitManager(plugin, database)
    val material: ResearchMaterial = ResearchMaterial.create(serverCollection)

    override fun initializeItems(plugin: JavaPlugin, player: String) {

        fillInventory(player)

        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(48, Button().GoBack(plugin,"To Module Menu"))
        inventory.setItem(49,item)
    }

    fun fillInventory(player: String) {
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player)).first().toJson())

        val researchList: List<ArmorType> = user.research.armor
        for(i in 0..53) {
            val index = i % 4 + 4 * (i / 9).toInt()
            if(i % 9 == 4) {
                inventory.setItem(i, ItemStack(Material.GRAY_STAINED_GLASS_PANE))
            } else if((i % 9) < 4 && index < researchList.size) {
                inventory.setItem(i, getArmorItem(user, researchList, index))
            } else {
                inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
            }
        }
        inventory.setItem(5, getMaterialItem(user,"steel"))
        /*
        inventory.setItem(6, getMaterialItem(playerDoc,"copper"))
        inventory.setItem(7, getMaterialItem(playerDoc,"kevlar"))
        inventory.setItem(8, getMaterialItem(playerDoc,"ceramic"))
        inventory.setItem(14, getMaterialItem(playerDoc,"gold"))
        inventory.setItem(15, getMaterialItem(playerDoc,"aluminum"))
        inventory.setItem(16, getMaterialItem(playerDoc,"diamond"))
        inventory.setItem(17, getMaterialItem(playerDoc,"netherite"))
        inventory.setItem(23, getMaterialItem(playerDoc,"titanium"))
        inventory.setItem(24, getMaterialItem(playerDoc,"tungsten"))

         */
    }
    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val rawSlot = event.rawSlot
        val player = event.whoClicked as Player
        if(isVaildSlot(event,rawSlot)) {
            val item = event.currentItem
            val buttonName = item?.let { ButtonName(it) }
            val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player.name)).first().toJson())


            if(item?.let { isArmorProccess(it) } == true) SelectArmorItem(event,player,item,user)
            if(item?.let { isMaterialProccess(it) } == true) SelectMaterialItem(event,player,item,user)

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

    fun getMaterialItem(user: User,id: String): ItemStack {

        val materialSet = material.material.find {it.type == id} as prd.peurandel.prdcore.Manager.Material
        val item = materialSet.item
        val itemStack = if (item!=null)ItemSerialization.deserializeItemStack(item) else ItemStack(Material.BARRIER)
        val meta = itemStack.itemMeta

        val key = NamespacedKey(plugin,"material")
        val processkey = NamespacedKey(plugin,"process")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,materialSet.name)
        meta.persistentDataContainer.set(processkey, PersistentDataType.STRING,"material")
        val wardrobe = user.wardrobe.find { it.uuid == suitUUID } as WardrobeItem

        if(wardrobe.material == materialSet.name) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        }

        itemStack.itemMeta = meta


        return itemStack
    }
    fun getArmorItem(user:User, researchList: List<ArmorType>,i: Int): ItemStack {

        val index = i%9 + (i/9)
        val armor = researchList.get(index)

        val armorName = armor.name

        val item = ItemStack(Material.IRON_CHESTPLATE)

        //아이템 정보 정의
        val meta = item.itemMeta
        meta.setDisplayName(armorName)
        val lore: MutableList<String> = ArrayList()
        meta.lore = lore

        val key = NamespacedKey(plugin,"armor")
        val processkey = NamespacedKey(plugin,"process")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,armor.type)
        meta.persistentDataContainer.set(processkey, PersistentDataType.STRING,"armor")
        val wardrobe = user.wardrobe.find { it.uuid == suitUUID } as WardrobeItem

        if(wardrobe.armorName == armor.type) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        }

        item.itemMeta = meta
        return item
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

    fun SelectMaterialItem(event: InventoryClickEvent, player: Player,item: ItemStack, user: User) {

        val ItemKey = getKey(item,"material")
        //get WardrobeDoc
        val wardrobeIndex = suitManager.getWardrobeIndex(user,getSuitUUID(event))
        val wardrobe = user.wardrobe.find{it.uuid == getSuitUUID(event)} as WardrobeItem
        val armor_duration = wardrobe.duration as Int


        // Armor: weight, duration, armor, cost
        // weight = 무게
        // duration = 내구도
        // armor = 방어력
        // cost = 가격
        // 이 모든게 비율값임.
        val materialSet = material.material.find {it.name == ItemKey} as prd.peurandel.prdcore.Manager.Material

        val weight = materialSet.weight
        val duration = materialSet.duration
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
                Updates.set("wardrobe.${wardrobeIndex}.weight", weight*(1.0+wardrobe.armor!!.weight/100)),
                //Updates.set("wardrobe.${wardrobeIndex}.armor", Armor)

            )
        )
        // 새로 GUI를 오픈해버리면 suitUUID를 소실
        ModuleArmorGUI(plugin,database,getSuitUUID(event)).open(plugin,player)


    }
    fun SelectArmorItem(event: InventoryClickEvent, player: Player,item: ItemStack, user: User) {

        val ItemKey = getKey(item,"armor")
        // Armor: weight, duration, armor, cost
        // weight = 무게
        // duration = 내구도
        // armor = 방어력
        // cost = 가격
        // 이 모든게 비율값임.
        val armorSet = user.research.armor.find {it.type == ItemKey} as ArmorType
        val weight = armorSet.weight
        val duration = armorSet.duration
        val Armor = armorSet.armor
        val cost = armorSet.cost

        val wardrobeIndex = suitManager.getWardrobeIndex(user,getSuitUUID(event))

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