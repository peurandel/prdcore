package prd.peurandel.prdcore

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Commands.PRDCommand
import prd.peurandel.prdcore.Commands.SuitCommand
import prd.peurandel.prdcore.Commands.TabCompleter.SuitTabCompleter
import prd.peurandel.prdcore.EventListner.*
import prd.peurandel.prdcore.Gui.*
import prd.peurandel.prdcore.Handler.PlayerHandler
import prd.peurandel.prdcore.Manager.*
import java.util.*


class Main : JavaPlugin() {

    private lateinit var mongoDBManager : MongoDBManager    //private lateinit var configManager: ConfigManager
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun onEnable() {

        mongoDBManager = MongoDBManager("mongodb://localhost:27017")
        if (mongoDBManager == null) logger.info("연결 자체에 문제 있음")
        val database = mongoDBManager.connectToDataBase("server")
        if (database == null) logger.info("데타베이스에 문제 있음")
        // 이벤트 리스너 등록
        val inventoryClickHandler = InventoryClickHandler()
        server.pluginManager.registerEvents(inventoryClickHandler, this)
        server.pluginManager.registerEvents(PlayerJoinHandler(database,this), this)
        server.pluginManager.registerEvents(DamageHandler(), this)
        server.pluginManager.registerEvents(SneakHandler(this,database), this)

        server.pluginManager.registerEvents(PlayerHandler(), this)

        setPlayerSet(database)
        // GUI 등록
        registerGUIs(inventoryClickHandler,database)

        getCommand("suit")?.apply {
            setExecutor(SuitCommand(this@Main,database))
            setTabCompleter(SuitTabCompleter())
        }
        getCommand("prd")?.setExecutor(PRDCommand(this,database  ))

        //Bukkit.getPluginCommand("suit")!!.apply{
        //    setExecutor(SuitCommand(this@Main,database))
        //    setTabCompleter(SuitTabCompleter())
        //}
        //Bukkit.getPluginCommand("prd")?.setExecutor(PRDCommand(this,database))
        //Bukkit.getPluginCommand("ai")?.apply{
        //}

        startPlayerTask(this,database)

    }

    override fun onDisable() {
        // Plugin shutdown logic

        mongoDBManager = MongoDBManager("mongodb://localhost:27017")
        val database = mongoDBManager.connectToDataBase("server")

        val suitManager = SuitManager(this,database)

        for(player: Player in Bukkit.getOnlinePlayers()) {
            val dataContainer = player.persistentDataContainer
            val key = NamespacedKey(this, "suit")

            val suit = dataContainer.get(key, PersistentDataType.STRING) ?: return // null일 경우 종료

            // 첫 번째 "."의 위치를 찾음
            val SuitArr = suit.split(":")

            // 첫 번째 "."을 기준으로 나누기
            val suitOwner = SuitArr[0]
            val suitUUID = SuitArr[1]

            suitManager.saveSuit(player,suitOwner,suitUUID)
        }
        PlayerDataCache.cache.clear()

        mongoDBManager.close()

    }

    private fun registerGUIs(handler: InventoryClickHandler,database: MongoDatabase) {
        handler.registerGUI(WardrobeGUI(this,database))
        handler.registerGUI(MainGUI(this,database))
        handler.registerGUI(ModuleGUI(this,database, "Module"))
        handler.registerGUI(ResearchGUI(this,database))
        handler.registerGUI(ResearchArmorGUI(this,database))
        handler.registerGUI(ResearchMagicGUI(this,database))
        handler.registerGUI(ResearchTechGUI(this,database))
        handler.registerGUI(ResearchTechPropellantGUI(this,database))
        handler.registerGUI(ResearchSoftwareGUI(this,database))
        handler.registerGUI(ResearchOrbitalGUI(this,database))
        handler.registerGUI(ResearchEngineGUI(this,database))
        handler.registerGUI(ModuleEngineGUI(this,database,"Module Engine"))
        handler.registerGUI(ModuleArmorGUI(this,database,"Module Armor"))
        handler.registerGUI(ModuleSoftwareGUI(this,database,"Module Software"))
        handler.registerGUI(ModuleOrbitalGUI(this,database,"Module Orbital"))
        handler.registerGUI(ModuleMagicGUI(this,database,"Module Magic"))
        handler.registerGUI(ModuleTechGUI(this,database,"Module Skill"))
        handler.registerGUI(skillGUI(this,database,"Skill"))
        handler.registerGUI(skillSlotGUI(this,database,"suitUUID",0))
    }

    private fun setPlayerSet(database: MongoDatabase) {
        val userCollection = database.getCollection("users")
        val suitManager = SuitManager(this,database)

        for(player in Bukkit.getOnlinePlayers()) {
            val playerDoc = userCollection.find(Filters.eq("uuid",player.uniqueId.toString())).first()
            val user: User = json.decodeFromString(playerDoc!!.toJson())

            val userData = mutableMapOf<String, Any?>().apply{
                put("joinTime",user.joinTime)
                put("height",user.height)
            }

            //서버 들어왔을때 정의되는 값이기에 덮혀쓰여져도 무방
            PlayerDataCache.cache[player.uniqueId] = userData


            val dataContainer = player.persistentDataContainer
            val key = NamespacedKey(this, "suit")

            val suit = dataContainer.get(key, PersistentDataType.STRING) ?: return // null일 경우 종료

            // 첫 번째 "."의 위치를 찾음
            val SuitArr = suit.split(":")

            // 첫 번째 "."을 기준으로 나누기
            val suitOwner = SuitArr[0]
            val suitUUID = SuitArr[1]


            val suitDoc = suitManager.loadUUIDSuit(playerDoc,suitUUID)


            val suitData = mutableMapOf<String, Any?>().apply{
                put("suit",suitDoc)
            }

            // 1. 기존 캐시 데이터 가져오기 (없으면 새로 생성)
            val playerCache = PlayerDataCache.cache.getOrPut(player.uniqueId) { mutableMapOf() }

            // 2. suitData 를 기존 캐시에 병합 (덮어쓰지 않고 추가/업데이트)
            playerCache.putAll(suitData)

        }
    }
}