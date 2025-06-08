package prd.peurandel.prdcore

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Commands.PRDCommand
import prd.peurandel.prdcore.Commands.SuitCommand
import prd.peurandel.prdcore.Commands.TabCompleter.SuitTabCompleter
import prd.peurandel.prdcore.EventListner.*
import prd.peurandel.prdcore.Gui.*
import prd.peurandel.prdcore.Gui.buildings.armoryGUI
import prd.peurandel.prdcore.Gui.shop.BazaarShopGUI
import prd.peurandel.prdcore.Gui.shop.shopgui
import prd.peurandel.prdcore.Handler.PlayerHandler
import prd.peurandel.prdcore.Manager.BazaarAPI
import prd.peurandel.prdcore.Manager.DatabaseManager
import prd.peurandel.prdcore.Manager.EconomyService
import prd.peurandel.prdcore.Manager.InventoryService
import prd.peurandel.prdcore.Manager.*
import prd.peurandel.prdcore.Manager.BazaarAPIImpl
import prd.peurandel.prdcore.Manager.DummyBazaarCoreServiceImpl
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class Main : JavaPlugin() {

    lateinit var mongoDBManager : MongoDBManager    //private lateinit var configManager: ConfigManager
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private lateinit var messageConfigManager: MessageConfigManager // MessageConfigManager 인스턴스
    lateinit var sidebarManager: SidebarManager
    private lateinit var inventoryClickHandler: InventoryClickHandler

    // 트랜잭션 지원을 위한 MongoClient와 관련 객체들
    private lateinit var mongoClient: com.mongodb.client.MongoClient
    private lateinit var databaseManager: DatabaseManager

    // BazaarAPI 접근을 위한 속성 추가
    private lateinit var bazaarAPI: BazaarAPI

    companion object {
        lateinit var configManager: ConfigManager
            private set // 외부에서 직접 설정하는 것을 방지

        // 다른 클래스에서 BazaarAPI 접근 가능하도록 설정
        private var instance: Main? = null
        fun getInstance(): Main = instance!!
    }

    private var logger: Logger = getLogger()

    override fun onEnable() {
        try {
            // 싱글톤 인스턴스 설정
            instance = this
            logger.info("[초기화] 싱글톤 인스턴스 설정 완료")

            // 설정 관리자 초기화 및 설정 로드
            configManager = ConfigManager(this)
            loadItemConfig()
            loadPluginSettings()
            logger.info("[초기화] 설정 로드 완료")
            
            // MongoDB 매니저 초기화
            mongoDBManager = MongoDBManager("mongodb://localhost:27017")
            logger.info("[초기화] MongoDB 매니저 초기화 완료")

            // SidebarManager 초기화 추가
            logger.info("[초기화] SidebarManager 초기화 완료")

            val job = SupervisorJob()
            val pluginScope = CoroutineScope(job + Dispatchers.IO)
            logger.info("[초기화] 코루틴 설정 완료")

            // --- MongoDB 초기화 ---
            try {
                // 1. MongoClient와 CoroutineClient 설정
                val connectionString = ConnectionString("mongodb://localhost:27017")

                // MongoDB 클라이언트 설정
                val mongoClientSettings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .build()

                // MongoDB 클라이언트 생성
                mongoClient = MongoClients.create(mongoClientSettings)
                logger.info("[디버깅] MongoDB 클라이언트 생성됨")

                // Bazaar 데이터베이스 생성
                val bazaarDatabase = mongoClient.getDatabase("bazaar")
                logger.info("[디버깅] bazaar 데이터베이스 접근됨")

                // 2. 기존 DB 연결 (이전 코드와 호환성 유지)
                val log_database = mongoDBManager.connectToDataBase("logs") 
                    ?: throw IllegalStateException("logs 데이터베이스 연결 실패")

                val serverDatabase = mongoDBManager.connectToDataBase("server")
                    ?: throw IllegalStateException("server 데이터베이스 연결 실패")
                sidebarManager = SidebarManager(serverDatabase)

                logger.info("[초기화] 모든 데이터베이스 연결 완료")

                // 3. 트랜잭션 관리자 구현체 생성
                databaseManager = MongoDatabaseManagerImpl(mongoClient, bazaarDatabase, logger)
                logger.info("[초기화] 데이터베이스 매니저 초기화 완료")

                // --- Repository 구현체 생성 ---
                val categoryRepo = MongoCategoryRepositoryImpl(bazaarDatabase)
                val productRepo = MongoProductRepositoryImpl(bazaarDatabase, logger, pluginScope)
                val orderRepo = MongoOrderRepositoryImpl(bazaarDatabase)
                val transactionRepo = MongoTransactionRepositoryImpl(bazaarDatabase, logger)
                logger.info("[초기화] Repository 초기화 완료")

                // --- 서비스 구현체 생성 ---
                val inventoryService = InventoryServiceImpl(this)
                val economyService = EconomyServiceImpl(this)
                logger.info("[초기화] 서비스 초기화 완료")

                // --- BazaarAPI 생성 ---
                val bazaarCoreService = DummyBazaarCoreServiceImpl(
                    orderRepo,
                    transactionRepo,
                    economyService,
                    inventoryService
                )
                
                bazaarAPI = BazaarAPIImpl(
                    categoryRepo,
                    productRepo,
                    orderRepo,
                    transactionRepo,
                    economyService,
                    inventoryService,
                    bazaarCoreService,
                    databaseManager
                )
                logger.info("[초기화] BazaarAPI 초기화 완료")

                // 테스트 데이터 생성
                logger.info("[초기화] Attempting to create test data...")
                try {
                    createDocsForTest(bazaarDatabase)
                    logger.info("[초기화] 테스트 데이터 생성 완료")
                } catch (e: Exception) {
                    logger.warning("[초기화] 테스트 데이터 생성 중 문제 발생: ${e.message}")
                    // 테스트 데이터 생성 실패는 치명적이지 않으므로 계속 진행
                }

                try {
                    // 3. GUI 초기화 및 등록
                    logger.info("[GUI] InventoryClickHandler 초기화 중...")
                    val inventoryClickHandler = InventoryClickHandler()

                    // GUI 등록 (이전 코드와 동일하게)
                    logger.info("[GUI] GUI 등록 시작...")
                    registerGUIs(inventoryClickHandler, serverDatabase)
                    logger.info("[GUI] GUI 등록 완료")
                    
                    // 이벤트 리스너로 등록
                    logger.info("[GUI] InventoryClickHandler 이벤트 리스너로 등록...")
                    server.pluginManager.registerEvents(inventoryClickHandler, this)
                    logger.info("[GUI] InventoryClickHandler 등록 완료")
                } catch (e: Exception) {
                    logger.severe("[GUI] GUI 초기화 중 오류 발생: ${e.message}")
                    e.printStackTrace()
                    throw e // 상위로 예외 전파
                }
                
                try {
                    // 4. 일반 이벤트 리스너 등록
                    registerEventListeners(serverDatabase)
                    logger.info("[초기화] 이벤트 리스너 등록 완료")
                } catch (e: Exception) {
                    logger.severe("[이벤트] 이벤트 리스너 등록 중 오류: ${e.message}")
                    e.printStackTrace()
                    throw e  // 상위로 예외 전파
                }
                
                try {
                    // 5. 플레이어 데이터 설정
                    setPlayerSet(serverDatabase)
                    logger.info("[초기화] 플레이어 데이터 설정 완료")
                } catch (e: Exception) {
                    logger.severe("[초기화] 플레이어 데이터 설정 중 오류: ${e.message}")
                    e.printStackTrace()
                }

                try {
                    // 6. 명령어 등록
                    registerCommands(serverDatabase)
                    logger.info("[명령어] 모든 명령어가 등록되었습니다.")
                } catch (e: Exception) {
                    logger.severe("[명령어] 명령어 등록 중 오류 발생: ${e.message}")
                    e.printStackTrace()
                    throw e  // 상위 예외 처리로 전파
                }
                
                try {
                    // 플레이어 태스크 시작
                    startPlayerTask(this, serverDatabase)
                    logger.info("[초기화] 플레이어 태스크 시작 완료")
                } catch (e: Exception) {
                    logger.severe("[초기화] 플레이어 관련 초기화 중 오류: ${e.message}")
                    e.printStackTrace()
                }
                
                logger.info("PRD Core 플러그인이 성공적으로 활성화되었습니다.")
                
            } catch (e: Exception) {
                logger.severe("[Bazaar] 초기화 실패: ${e.message}")
                e.printStackTrace()
            }
        } catch (e: Exception) {
            logger.severe("[심각] 플러그인 초기화 중 치명적 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 모든 명령어를 등록합니다.
     */
    private fun registerCommands(database: MongoDatabase) {
        try {
            logger.info("[명령어] 명령어 등록 시작...")
            
            // PRD 명령어
            getCommand("prd")?.apply {
                logger.info("[명령어] prd 명령어 초기화 중...")
                
                // BazaarAPI 초기화 안전 확인
                if (!::bazaarAPI.isInitialized) {
                    logger.severe("[명령어] bazaarAPI가 초기화되지 않았습니다. 임시 인스턴스 생성...")

                }
                
                val executor = PRDCommand(this@Main, database, bazaarAPI)
                setExecutor(executor)
                tabCompleter = executor
                logger.info("[명령어] prd 명령어 등록 완료")
            } ?: logger.warning("[명령어] prd 명령어를 찾을 수 없습니다! plugin.yml 설정을 확인하세요.")
            
            // Suit 명령어
            getCommand("suit")?.apply {
                logger.info("[명령어] suit 명령어 초기화 중...")
                setExecutor(SuitCommand(this@Main, database))
                tabCompleter = SuitTabCompleter()
                logger.info("[명령어] suit 명령어 등록 완료")
            } ?: logger.warning("[명령어] suit 명령어를 찾을 수 없습니다! plugin.yml 설정을 확인하세요.")
            
            logger.info("[명령어] 모든 명령어가 등록되었습니다.")
        } catch (e: Exception) {
            logger.severe("[명령어] 명령어 등록 중 오류 발생: ${e.message}")
            e.printStackTrace()
            throw e  // 상위 예외 처리로 전파
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
        try {
            // MongoDB 클라이언트 종료
            mongoClient.close()

            logger.info("[Bazaar] MongoDB 연결 종료됨")
        } catch (e: Exception) {
            logger.warning("[Bazaar] MongoDB 연결 종료 중 오류: ${e.message}")
        }

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

    private fun loadItemConfig() {
        val itemConfig = configManager.loadConfig("item-info") // item-info.yml 로드
        val infoSection: ConfigurationSection? = itemConfig.getConfigurationSection("item-info")
        itemInfoMap.cache.clear()

        if (infoSection != null) {
            for (key in infoSection.getKeys(false)) {
                try {
                    val material = org.bukkit.Material.valueOf(key.uppercase())
                    val price = infoSection.getInt("$key.price")
                    val loreList = infoSection.getStringList("$key.lore")
                    val itemInfo = ItemInfo(price, loreList)
                    itemInfoMap.cache[material] = itemInfo
                } catch (e: IllegalArgumentException) {
                    logger.warning("item-info.yml에 잘못된 아이템 타입이 있습니다: $key")
                } catch (e: Exception) {
                    logger.warning("item-info.yml에 '$key' 아이템 정보 로딩 중 오류 발생: ${e.message}")
                }
            }
        } else {
            logger.warning("item-info.yml에 'item-info' 섹션이 없습니다.")
        }
        logger.info("아이템 정보 로드 완료: ${itemInfoMap.cache.entries.joinToString { (k, v) -> "$k - 가격: ${v.price}, 설명: ${v.lore}" }}")
    }

    private fun registerGUIs(handler: InventoryClickHandler, database: MongoDatabase) {
        try {
            logger.info("[GUI] 각 GUI 인스턴스를 InventoryClickHandler에 등록합니다...")
            
            // 기본 GUI 등록
            handler.registerGUI(MainGUI(this,database))
            handler.registerGUI(ResearchGUI(this,database))
            handler.registerGUI(ResearchEngineGUI(this,database))
            handler.registerGUI(ResearchArmorGUI(this,database))
            handler.registerGUI(ResearchTechGUI(this,database))
            handler.registerGUI(ResearchTechFireArmGUI(this,database))
            handler.registerGUI(ResearchTechPropellantGUI(this,database))
            handler.registerGUI(ResearchMagicGUI(this,database))
            handler.registerGUI(ResearchOrbitalGUI(this,database))
            handler.registerGUI(ResearchSoftwareGUI(this,database))
            handler.registerGUI(WardrobeGUI(this,database))
            handler.registerGUI(ModuleSoftwareGUI(this,database,""))
            handler.registerGUI(shopgui(this,database,"build"))
            handler.registerGUI(shopgui(this,database,"bazaar"))
            handler.registerGUI(armoryGUI(this,database))
            
            // 중요: BazaarShopGUI 생성 시 BazaarAPI 전달 (이전 버전과 동일하게)
            if (::bazaarAPI.isInitialized) {
                val bazaarDB = mongoClient.getDatabase("bazaar")
                handler.registerGUI(BazaarShopGUI(this, bazaarDB))
            } else {
                logger.warning("[GUI] BazaarAPI가 초기화되지 않아 BazaarShopGUI를 등록할 수 없습니다.")
            }
            
            logger.info("[GUI] 모든 GUI가 성공적으로 등록되었습니다.")
        } catch (e: Exception) {
            logger.severe("[GUI] GUI 등록 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setPlayerSet(database: MongoDatabase) {
        val userCollection = database.getCollection("users")
        val suitManager = SuitManager(this,database)

        for(player in Bukkit.getOnlinePlayers()) {
            val playerDoc = userCollection.find(com.mongodb.client.model.Filters.eq("uuid",player.uniqueId.toString())).first()
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
        logger.info("[초기화] createDocsForTest started.")

        val productsCollection = database.getCollection("products") // Changed to 'products' collection

        // Sample MINING product
        val diamondProduct = Document()
            .append("productId", "DIAMOND_001")
            .append("productName", "Diamond")
            .append("material", "DIAMOND")
            .append("category", "MINING")
            .append("basePrice", 250.0)
            // Add other necessary fields if your createProductItem expects them

        // Sample FARMING product
        val wheatProduct = Document()
            .append("productId", "WHEAT_001")
            .append("productName", "Wheat")
            .append("material", "WHEAT")
            .append("category", "FARMING")
            .append("basePrice", 10.0)

        // Sample COMBAT product
        val ironSwordProduct = Document()
            .append("productId", "IRON_SWORD_001")
            .append("productName", "Iron Sword")
            .append("material", "IRON_SWORD")
            .append("category", "COMBAT")
            .append("basePrice", 100.0)

        // Sample WOOD_FISH products
        val oakLogProduct = Document()
            .append("productId", "OAK_LOG_001")
            .append("productName", "Oak Log")
            .append("material", "OAK_LOG")
            .append("category", "WOOD_FISH")
            .append("basePrice", 5.0)

        val codProduct = Document()
            .append("productId", "COD_001")
            .append("productName", "Raw Cod")
            .append("material", "COD")
            .append("category", "WOOD_FISH")
            .append("basePrice", 15.0)

        // Sample ODDITIE product
        val emeraldProduct = Document()
            .append("productId", "EMERALD_001")
            .append("productName", "Emerald")
            .append("material", "EMERALD")
            .append("category", "ODDITIE")
            .append("basePrice", 500.0)

        // Check if products already exist to avoid duplicates (optional, but good practice)
        if (productsCollection.countDocuments(Filters.eq("productId", "DIAMOND_001")) == 0L) {
            productsCollection.insertOne(diamondProduct)
            logger.info("Inserted test product: Diamond (MINING)")
        }
        if (productsCollection.countDocuments(Filters.eq("productId", "WHEAT_001")) == 0L) {
            productsCollection.insertOne(wheatProduct)
            logger.info("Inserted test product: Wheat (FARMING)")
        }
        if (productsCollection.countDocuments(Filters.eq("productId", "IRON_SWORD_001")) == 0L) {
            productsCollection.insertOne(ironSwordProduct)
            logger.info("Inserted test product: Iron Sword (COMBAT)")
        }
        if (productsCollection.countDocuments(Filters.eq("productId", "OAK_LOG_001")) == 0L) {
            productsCollection.insertOne(oakLogProduct)
            logger.info("Inserted test product: Oak Log (WOOD_FISH)")
        }
        if (productsCollection.countDocuments(Filters.eq("productId", "COD_001")) == 0L) {
            productsCollection.insertOne(codProduct)
            logger.info("Inserted test product: Raw Cod (WOOD_FISH)")
        }
        if (productsCollection.countDocuments(Filters.eq("productId", "EMERALD_001")) == 0L) {
            productsCollection.insertOne(emeraldProduct)
            logger.info("Inserted test product: Emerald (ODDITIE)")
        }

        logger.info("[초기화] createDocsForTest finished inserting product data.")

        // Original code inserting into 'server' collection (can be kept if needed for other tests)
        val serverCollection = database.getCollection("server")
        val Engine = ResearchEngine(
            name = "Engine",
            engine = mutableListOf(
                Engine(
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
                )
            )
        )
        val Armor = ResearchArmor(
            name = "ArmorType",
            armor = mutableListOf(
                ArmorType(
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
                )
            )
        )
        val Material = ResearchMaterial(
            name = "Material",
            material = mutableListOf(
                Material(
                    name = "강철",
                    type = "steel",
                    lore = listOf(
                        "기본적인 장갑 재질입니다."
                    ),
                    weight = 4.87,
                    armor = 15,
                    cost = 3,
                    duration = 840,
                    item = "asdf",
                    requireEx = 0
                )
            )
        )

        val Magic = ResearchMagic(
            name = "Magic",
            magics = mutableListOf(
                Magics(
                    name = "보호막",
                    type = "shield",
                    lore = listOf(
                        "장갑을 보호하는 보호막입니다."
                    ),
                    item = "asdf",
                    requireEx = 0
                )
            )
        )

        val Skills = ResearchSkills(
            name = "Skill",
            skills = mutableListOf(
                Skills(
                    name = "테스트",
                    type = "steel",
                    lore = listOf(
                        "일단 존나 테스트"
                    ),
                    item = "asdf",
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

        logger.info("Inserted document ID into server collection: ${result.insertedId}") // Clarified log
        serverCollection.insertOne(EngineDoc)
        serverCollection.insertOne(ArmorDoc)
        serverCollection.insertOne(MaterialDoc)
        serverCollection.insertOne(MagicDoc)
        serverCollection.insertOne(SkillDoc)
    }

    /**
     * 모든 이벤트 리스너를 등록합니다. (InventoryClickHandler 제외)
     */
    private fun registerEventListeners(database: MongoDatabase) {
        try {
            logger.info("[이벤트] 이벤트 리스너 등록 시작...")
            
            // 기타 이벤트 리스너 등록
            server.pluginManager.registerEvents(PlayerJoinHandler(database, this), this)
            logger.info("[이벤트] PlayerJoinHandler 등록 완료")
            
            server.pluginManager.registerEvents(DamageHandler(), this)
            logger.info("[이벤트] DamageHandler 등록 완료")
            
            server.pluginManager.registerEvents(SneakHandler(this, database), this)
            logger.info("[이벤트] SneakHandler 등록 완료")
            
            server.pluginManager.registerEvents(playerInteractHandler(this, database), this)
            logger.info("[이벤트] PlayerInteractHandler 등록 완료")
            
            server.pluginManager.registerEvents(PlayerHandler(), this)
            logger.info("[이벤트] PlayerHandler 등록 완료")
            
            logger.info("[이벤트] 모든 이벤트 리스너가 성공적으로 등록되었습니다.")
        } catch (e: Exception) {
            logger.severe("[이벤트] 이벤트 리스너 등록 중 오류: ${e.message}")
            e.printStackTrace()
            throw e  // 상위로 예외 전파
        }
    }

    fun getBazaarAPI(): BazaarAPI {
        if (!::bazaarAPI.isInitialized) {
            logger.severe("BazaarAPI가 초기화되지 않았습니다!")
            throw IllegalStateException("BazaarAPI가 초기화되지 않았습니다. 서버를 재시작하세요.")
        }
        return bazaarAPI
    }
}