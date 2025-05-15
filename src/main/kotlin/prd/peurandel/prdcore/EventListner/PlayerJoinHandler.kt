package prd.peurandel.prdcore.EventListner

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Main
import prd.peurandel.prdcore.Manager.*
import java.util.*
import kotlin.collections.ArrayList

class PlayerJoinHandler(database: MongoDatabase,private val plugin: Main): Listener {
    val database = database
    val userCollection = database.getCollection("users")
    val suitManager = SuitManager(plugin,database)
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        if(isExisting(player)) updateUserName(player)
        else createNewUser(player)

        // 사이드바
        plugin.sidebarManager.createSidebar(event.player)

        setMap(player)
        loadSuit(player)
        // suitOwner와 suitName을 활용하는 로직 추가
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val dataContainer = player.persistentDataContainer

        val key = NamespacedKey(plugin, "suit")

        plugin.sidebarManager.removeSidebar(event.player)

        val suit = dataContainer.get(key, PersistentDataType.STRING) ?: return // null일 경우 종료

        // 첫 번째 "."의 위치를 찾음
        val SuitArr = suit.split(":")

        // 첫 번째 "."을 기준으로 나누기
        val suitOwner = SuitArr[0]
        val suitUUID = SuitArr[1]

        suitManager.saveSuit(player,suitOwner,suitUUID)
        PlayerDataCache.cache[player.uniqueId] ?.clear()
    }

    fun updateUserName(player: Player) {
        val playerDoc : Document = userCollection.find(Filters.eq("uuid",player.uniqueId.toString())).first()

        playerDoc["name"] = player.name
        userCollection.updateOne(Filters.eq("uuid",player.uniqueId.toString()),Document("\$set",playerDoc))
    }

    fun createNewUser(player: Player) {
        val newUser = User(
            uuid = player.uniqueId.toString(),
            name = player.name,
            joinTime = System.currentTimeMillis(),
            height = 180,
            changable = Changable(height = true),
            wardrobe = mutableListOf(
                WardrobeItem(
                    name = "slot0",
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
            ),
            wardrobeCount = 1,
            money = 10000,
            wardrobepage = 0,
            research = Research(
                engine = listOf(Engine(
                    name = "Basic Furnace",
                    type = "basic_furnace",
                    lore = listOf(
                        "원시의 기운이 느껴집니다."
                    ),
                    tier = 1,
                    energy = 1000,
                    requireEx = 0,
                    item = "asdf",
                    requireResearch = null
                )),
                armor = listOf(ArmorType(
                    name = "주조장갑",
                    type = "cast_armor",
                    lore = listOf(
                        "기본적인 장갑입니다.",
                        "값싸고 든든합니다.",
                    ),
                    armor = 0,
                    weight = 0,
                    duration = -10,
                    cost = -20,
                    item = "asdf",
                    requireEx = 0
                )),
                magic = emptyList(),
                skill = emptyList()
            ),
            research_point = 0
        )
        val playerDoc = Document.parse(Json.encodeToString(newUser))
        userCollection.insertOne(playerDoc)

    }


    fun setMap(player: Player) {
        val playerDoc = userCollection.find(Filters.eq("uuid",player.uniqueId.toString())).first()
        val user: User = json.decodeFromString(playerDoc!!.toJson())

        val userData = mutableMapOf<String, Any?>().apply{
            put("joinTime",user.joinTime)
            put("height",user.height)
        }

        //서버 들어왔을때 정의되는 값이기에 덮혀쓰여져도 무방
        PlayerDataCache.cache[player.uniqueId] = userData

    }
    fun loadSuit(player: Player) {
        val key = NamespacedKey(plugin, "suit")
        val testkey2 = NamespacedKey(plugin, "suit2")
        val dataContainer = player.persistentDataContainer

        val suit = dataContainer.get(key, PersistentDataType.STRING) ?: return // null일 경우 종료

        // 첫 번째 "."의 위치를 찾음
        val SuitArr = suit.split(":")

        // 첫 번째 "."을 기준으로 나누기
        val suitOwner = SuitArr[0]
        val suitUUID = SuitArr[1]


        val playerDoc = userCollection.find(Filters.eq("uuid",suitOwner)).first()

        val suitDoc = suitManager.loadUUIDSuit(playerDoc,suitUUID)

        val suitData = mutableMapOf<String, Any?>().apply{
            put("suit",suitDoc)
        }

        // 1. 기존 캐시 데이터 가져오기 (없으면 새로 생성)
        val playerCache = PlayerDataCache.cache.getOrPut(player.uniqueId) { mutableMapOf() }

        // 2. suitData 를 기존 캐시에 병합 (덮어쓰지 않고 추가/업데이트)
        playerCache.putAll(suitData)

        // 3. 업데이트된 캐시를 다시 저장 (getOrPut에서 이미 처리하므로 불필요)
        // PlayerDataCache.cache[player.uniqueId] = playerCache
    }

    fun isExisting(player: Player): Boolean {
        val user = userCollection.find(Filters.eq("uuid", player.uniqueId.toString())).firstOrNull()
        return user != null
    }
}