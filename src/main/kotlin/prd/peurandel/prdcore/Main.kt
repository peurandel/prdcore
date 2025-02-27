package prd.peurandel.prdcore

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bson.types.ObjectId
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
import java.util.logging.Logger


class Main : JavaPlugin() {

    private lateinit var mongoDBManager : MongoDBManager    //private lateinit var configManager: ConfigManager
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private lateinit var configManager: ConfigManager
    private lateinit var messageConfigManager: MessageConfigManager // MessageConfigManager 인스턴스

    private lateinit var logger: Logger

    override fun onEnable() {
        configManager = ConfigManager(this, "config.yml") // config.yml 파일 로드
        loadPluginSettings()




        mongoDBManager = MongoDBManager("mongodb://localhost:27017")
        val database = mongoDBManager.connectToDataBase("server")

        logger.info("플러그인이 활성화되었습니다.")

        //Keep the comments in front the code when the Docs are already created
        createDocsForTest(database)



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

    private fun loadPluginSettings() {
    }

    fun getPluginConfig(): ConfigManager { // ConfigManager 인스턴스 반환 메서드 (필요한 경우)
        return configManager
    }
    fun getMessageConfig(): MessageConfigManager { // MessageConfigManager 인스턴스 반환 메서드 (필요한 경우)
        return messageConfigManager
    }

    fun createDocsForTest(database: MongoDatabase) {

        val serverCollection = database.getCollection("server")
        val Engine = ResearchEngine(
            name = "Engine",
            engine = mutableListOf(
                Engine(
                    name = "Basic Furnace",
                    id = "basic_furnace",
                    lore = listOf(
                        "원시의 기운이 느껴집니다."
                    ),
                    tier = 1,
                    energy = 1000,
                    requireEx = 0,
                    requireResearch = null
                )
            )
        )
        val Armor = ResearchArmor(
            name = "ArmorType",
            armor = mutableListOf(
                ArmorType(
                    name = "주조장갑",
                    id = "Cast Armor",
                    lore = listOf(
                        "기본적인 장갑입니다.",
                        "값싸고 든든합니다.",
                    ),
                    armor = 0,
                    weight = 0,
                    duration = -10,
                    cost = -20,
                    requireEx = 0,

                    )
            )
        )
        val Material = ResearchMaterial(
            name = "Material",
            material = mutableListOf(
                Material(
                    name = "강철",
                    id = "steel",
                    lore = listOf(
                        "기본적인 장갑 재질입니다."
                    ),
                    weight = 4.87,
                    armor = 15,
                    cost = 3,
                    duration = 840,
                    requireEx = 0
                )
            )
        )

        val Magic = ResearchMagic(
            name = "Magic",
            magics = mutableListOf(
                Magics(
                    name = "보호막",
                    id = "shield",
                    lore = listOf(
                        "장갑을 보호하는 보호막입니다."
                    ),
                    requireEx = 0
                )
            )
        )

        val Skills = ResearchSkills(
            name = "Skill",
            skills = mutableListOf(
                Skills(
                    name = "테스트",
                    id = "steel",
                    lore = listOf(
                        "일단 존나 테스트"
                    ),
                    requireEx = 0
                )
            )
        )

        val EngineDoc = Document.parse(Json.encodeToString(Engine))
        val ArmorDoc = Document.parse(Json.encodeToString(Armor))
        val MaterialDoc = Document.parse(Json.encodeToString(Material))
        val MagicDoc = Document.parse(Json.encodeToString(Magic))
        val SkillDoc = Document.parse(Json.encodeToString(Skills))

        val simpleDoc = Document.parse("""{"test": "document"}""")
        val result = serverCollection.insertOne(simpleDoc)

        logger.info("Inserted document ID: ${result.insertedId}")
        serverCollection.insertOne(EngineDoc)
        serverCollection.insertOne(ArmorDoc)
        serverCollection.insertOne(MaterialDoc)
        serverCollection.insertOne(MagicDoc)
        serverCollection.insertOne(SkillDoc)
    }

}