package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.serialization.json.Json
import org.bson.Document
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
import prd.peurandel.prdcore.ItemStack.ItemSerialization
import prd.peurandel.prdcore.Manager.Armor
import prd.peurandel.prdcore.Manager.Skill
import prd.peurandel.prdcore.Manager.User
import prd.peurandel.prdcore.Manager.WardrobeItem
import java.util.*
import java.util.logging.Logger
import kotlin.math.max
import kotlin.math.min
import kotlin.time.times

class WardrobeGUI(plugin: JavaPlugin,database: MongoDatabase) : BaseGUI(plugin,"Wardrobe",54) {
    val playerCollection: MongoCollection<Document> = database.getCollection("users")
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val database = database
    override fun initializeItems(plugin: JavaPlugin, player: String) {
        for(i in 0..53) {
            inventory.setItem(i,ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        var playerDoc: Document? = playerCollection.find(Filters.eq("name",player)).first()

        var playerdata: User = json.decodeFromString(playerDoc!!.toJson())

        val wardrobeCount = playerdata.wardrobeCount
        val currentPage = playerdata.wardrobepage


        val startIndex = currentPage * 5
        var infoList: List<String> = listOf()
        inventory.setItem(0,Button().Slot(plugin,"Slot${startIndex}",false,infoList))
        inventory.setItem(9,Button().Slot(plugin,"Slot${startIndex+1}",false,infoList))
        inventory.setItem(18,Button().Slot(plugin,"Slot${startIndex+2}",false,infoList))
        inventory.setItem(27,Button().Slot(plugin,"Slot${startIndex+3}",false,infoList))
        inventory.setItem(36,Button().Slot(plugin,"Slot${startIndex+4}",false,infoList))

        for(i in 0 until max(0,min(5,wardrobeCount-5*currentPage))) {
            val slotIndex = startIndex + i
            if(slotIndex >= wardrobeCount) { // **wardrobeCount 기준으로 슬롯이 필요한지 확인 (더 정확)**
                // 더 이상 슬롯이 필요하지 않으면 WardrobeAdd 호출 안 함
                continue // 다음 루프로 진행
            }
            if(slotIndex >= playerdata.wardrobe.size) { // (기존 조건 유지, 혹시 wardrobe 리스트 크기가 부족할 경우 대비)
                if (playerDoc != null) {
                    WardrobeAdd(player,"slot${currentPage*5+i}",playerDoc,playerCollection)
                }
                playerDoc = playerCollection.find(Filters.eq("name",player)).first() // MongoDB에서 최신 문서 다시 로드
                playerdata = json.decodeFromString(playerDoc!!.toJson()) // playerdata 객체 갱신

            }


            val wardrobeset = playerdata.wardrobe[currentPage*5+i]
            val uuid = wardrobeset.uuid
            val helmet = wardrobeset.helmet
            val chestplate = wardrobeset.chestplate
            val leggings = wardrobeset.leggings
            val boots = wardrobeset.boots
            infoList = listOf(
                "Engine : ${wardrobeset.engine}",
                "Armor : ${wardrobeset.armorName}",
                "Tier : ${wardrobeset.tier}",
                "Duration : ${((wardrobeset.duration?.times(24*(1.0+ (wardrobeset.armor?.duration ?: 0)/100))))}",
                "Weight : ${wardrobeset.weight?.times(1.0+(wardrobeset.armor?.weight ?: 0)/100)}"

            )
            inventory.setItem(i * 9 + 0, Button().Slot(plugin, wardrobeset.name, true,infoList))
            inventory.setItem(i * 9 + 1, if (helmet != null) ItemSerialization.deserializeItemStack(helmet) else ItemStack(Material.RED_STAINED_GLASS_PANE))
            inventory.setItem(i * 9 + 2, if (chestplate != null) ItemSerialization.deserializeItemStack(chestplate) else ItemStack(Material.RED_STAINED_GLASS_PANE))
            inventory.setItem(i * 9 + 3, if (leggings != null) ItemSerialization.deserializeItemStack(leggings) else ItemStack(Material.RED_STAINED_GLASS_PANE))
            inventory.setItem(i * 9 + 4, if (boots != null) ItemSerialization.deserializeItemStack(boots) else ItemStack(Material.RED_STAINED_GLASS_PANE))
            inventory.setItem(i * 9 + 5, Button().Skill(plugin, uuid))
            inventory.setItem(i * 9 + 6, Button().Module(plugin, uuid))

        }
        // GUI의 정보를 담는 아이템 넣기.
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())
        inventory.setItem(48, Button().GoBack(plugin,"To Main Menu"))
        inventory.setItem(49,item)

        if (wardrobeCount > (currentPage + 1) * 5) inventory.setItem(53, Button().Next_Page(plugin, (currentPage + 1).toString() + "/" + (wardrobeCount / 5 + 1)))
        if (currentPage != 0) inventory.setItem(45, Button().Previous_Page(plugin, (currentPage + 1).toString() + "/" + (wardrobeCount / 5 + 1)))

    }

    override fun onInventoryClick(event: InventoryClickEvent) {

        val playerCollection: MongoCollection<Document> = database.getCollection("users")
        val playerDoc: Document? = playerCollection.find(Filters.eq("name",event.whoClicked.name)).first()
        val playerdata: User = json.decodeFromString(playerDoc!!.toJson())

        val rawSlot = event.rawSlot

        val currentPage = playerdata.wardrobepage
        val suitIndex = currentPage*5 + (rawSlot/9)
        val inItem = event.cursor
        if(isVaildSlot(event,rawSlot)) {
            event.isCancelled = true

            val item = event.currentItem
            val buttonName = item?.let { ButtonName(it) }
            val rowIndex = getRowIndex(rawSlot);

            if (suitIndex < playerdata.wardrobe.size) processArmorItem(event,event.whoClicked as? Player ?: return,rawSlot,inItem,rowIndex,suitIndex)
            if (buttonName != null){
                processButton(event, event.whoClicked as Player,buttonName,playerDoc)
            }
        }

    }


    private fun getRowIndex(rawSlot: Int): Int {
        return rawSlot % 9 - 1
    }

    fun processButton(event: InventoryClickEvent, player : Player, buttonName : String,playerDoc: Document) {

        val playerset: User = json.decodeFromString(playerDoc.toJson())
        when(buttonName) {
            "skill" -> {
                val currentPage = playerset.wardrobepage
                val wardrobeSet: WardrobeItem = playerset.wardrobe[currentPage*5+event.rawSlot/9]

                skillGUI(plugin,database,wardrobeSet.uuid).open(plugin,player)
            }
            "module" -> {
                val currentPage = playerset.wardrobepage
                val wardrobeSet: WardrobeItem = playerset.wardrobe[currentPage*5+event.rawSlot/9]

                ModuleGUI(plugin,database,wardrobeSet.uuid).open(plugin,player)
            }
            "nextpage" -> {
                nextPage(player,plugin,playerDoc)
            }
            "previouspage" -> {
                previousPage(player,plugin,playerDoc)
            }
            "goback" -> {
                MainGUI(plugin,database).open(plugin,player)

            }
            "close" -> {
                player.closeInventory()
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

    fun WardrobeAdd(player: String, name: String, doc: Document, playerCollection: MongoCollection<Document>) {
        val wardrobeList = (doc["wardrobe"] as? List<*>)?.toMutableList() ?: mutableListOf()

        val wardrobe = WardrobeItem(
            name = name,
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

        //wardrobeList.add(Document.parse(Json.encodeToString(wardrobe)))
        //doc["wardrobe"] = wardrobeList
        //playerCollection.updateOne(Filters.eq("player", player), Document("\$set", doc), UpdateOptions().upsert(true))

        val wardrobeDocument = Document.parse(Json.encodeToString(wardrobe)) // WardrobeItem을 Document로 변환

        playerCollection.updateOne(
            Filters.eq("name", player),
            Updates.push("wardrobe", wardrobeDocument), // $push 연산자로 wardrobe 배열에 추가
            UpdateOptions().upsert(true) // upsert 옵션은 유지 (플레이어 문서가 없을 경우 생성)
        )
    }

    fun processArmorItem(event: InventoryClickEvent, player: Player, rawSlot: Int, inItem: ItemStack, rowIndex: Int,suitIndex: Int) {
        val armorTypes: List<String> = mutableListOf("helmet", "chestplate", "leggings", "boots")
        if(rowIndex>=0 && rowIndex < armorTypes.size) {
            val name = ChatColor.stripColor(event.inventory.getItem(rawSlot - rawSlot%9)?.itemMeta?.displayName)
            var armor: String? = null
            if ((isHelmet(inItem) && rowIndex == 0) ||
                (isChestplate(inItem) && rowIndex == 1) ||
                (isLeggings(inItem) && rowIndex == 2) ||
                (isBoots(inItem) && rowIndex == 3)) {
                armor = ItemSerialization.serializeItemStack(inItem)
            }
            val playerCollection: MongoCollection<Document> = database.getCollection("users")

            playerCollection.updateOne(
                Filters.eq<String>(
                    "uuid",
                    player.uniqueId.toString()
                ), Updates.set("wardrobe.${suitIndex}.${armorTypes.get(rowIndex)}",armor)
            )
            open(plugin,player)
        }
    }

    fun isHelmet(item: ItemStack?): Boolean {
        if (item == null) {
            return false
        }
        val material = item.type
        return material == Material.LEATHER_HELMET || material == Material.CHAINMAIL_HELMET || material == Material.IRON_HELMET || material == Material.GOLDEN_HELMET || material == Material.DIAMOND_HELMET || material == Material.NETHERITE_HELMET || material == Material.TURTLE_HELMET || material == Material.PLAYER_HEAD
    }

    fun isChestplate(item: ItemStack?): Boolean {
        if (item == null) {
            return false
        }
        val material = item.type
        return material == Material.LEATHER_CHESTPLATE || material == Material.CHAINMAIL_CHESTPLATE || material == Material.IRON_CHESTPLATE || material == Material.GOLDEN_CHESTPLATE || material == Material.DIAMOND_CHESTPLATE || material == Material.NETHERITE_CHESTPLATE
    }

    fun isLeggings(item: ItemStack?): Boolean {
        if (item == null) {
            return false
        }
        val material = item.type
        return material == Material.LEATHER_LEGGINGS || material == Material.CHAINMAIL_LEGGINGS || material == Material.IRON_LEGGINGS || material == Material.GOLDEN_LEGGINGS || material == Material.DIAMOND_LEGGINGS || material == Material.NETHERITE_LEGGINGS
    }

    fun isBoots(item: ItemStack?): Boolean {
        if (item == null) {
            return false
        }
        val material = item.type
        return material == Material.LEATHER_BOOTS || material == Material.CHAINMAIL_BOOTS || material == Material.IRON_BOOTS || material == Material.GOLDEN_BOOTS || material == Material.DIAMOND_BOOTS || material == Material.NETHERITE_BOOTS
    }

    fun nextPage(player: Player, plugin: JavaPlugin,playerDoc: Document) {

        val currentPage = playerDoc.getInteger("wardrobepage")
        if (currentPage < (playerDoc.getInteger("wardrobeCount") - 1) / 5) {
            playerCollection.updateOne(
                Filters.eq<String>(
                    "uuid",
                    player.uniqueId.toString()
                ), Updates.set("wardrobepage",currentPage+1)
            )
            open(plugin,player)
        }
    }

    fun previousPage(player: Player, plugin: JavaPlugin, playerDoc: Document) {

        val currentPage = playerDoc.getInteger("wardrobepage")
        if (currentPage > 0) {
            playerCollection.updateOne(
                Filters.eq<String>(
                    "uuid",
                    player.uniqueId.toString()
                ), Updates.set("wardrobepage",currentPage-1)
            )
            open(plugin,player)
        }
    }
}