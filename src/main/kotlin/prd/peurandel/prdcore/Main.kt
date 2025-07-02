package prd.peurandel.prdcore

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

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
import prd.peurandel.prdcore.Manager.*
import java.util.*
import java.util.logging.Logger


class Main : JavaPlugin() {

    private lateinit var mongoDBManager : MongoDBManager    //private lateinit var configManager: ConfigManager
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private lateinit var messageConfigManager: MessageConfigManager // MessageConfigManager 인스턴스
    lateinit var sidebarManager: SidebarManager
    
    // 트랜잭션 지원을 위한 MongoClient와 관련 객체들
    private lateinit var mongoClient: MongoClient
    private lateinit var coroutineClient: CoroutineClient
    private lateinit var bazaarCoroutineDB: CoroutineDatabase
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
        // 싱글톤 인스턴스 설정
        instance = this
        
        configManager = ConfigManager(this)
        loadItemConfig()
        loadPluginSettings()
        mongoDBManager = MongoDBManager("mongodb://localhost:27017")

        val job = SupervisorJob()
        val pluginScope = CoroutineScope(job + Dispatchers.IO)

        // --- MongoDB 초기화 ---
        try {
            // 1. MongoClient와 CoroutineClient 설정
            val connectionString = ConnectionString("mongodb://localhost:27017")
            val settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build()
            
            logger.info("[디버깅] MongoDB 연결 설정 생성됨: $connectionString")
            
            mongoClient = MongoClients.create(settings)
            logger.info("[디버깅] MongoDB 클라이언트 생성됨")
            
            try {
                coroutineClient = mongoClient.coroutine
                logger.info("[디버깅] MongoDB 코루틴 클라이언트 생성됨")
                
                // Bazaar 데이터베이스 생성
                bazaarCoroutineDB = coroutineClient.getDatabase("bazaar")
                logger.info("[디버깅] bazaar 데이터베이스 접근됨")
                
                // 컬렉션 존재 여부 확인
                pluginScope.launch {
                    try {
                        val collections = bazaarCoroutineDB.listCollectionNames().toList()
                        logger.info("[디버깅] 컬렉션 목록: $collections")
                        
                        // products 컬렉션에서 다이아몬드 정보 조회 시도
                        val productCollection = bazaarCoroutineDB.getCollection<Product>("products")
                        val diamond = productCollection.findOneById("diamond")
                        logger.info("[디버깅] 다이아몬드 정보 조회 결과: ${diamond != null}, 데이터: $diamond")
                        
                        // 리포지토리를 통한 조회 테스트
                        val productRepo = MongoProductRepositoryImpl(bazaarCoroutineDB,logger,pluginScope)
                        val diamondFromRepo = productRepo.findById("diamond")
                        logger.info("[디버깅] 리포지토리 다이아몬드 조회 결과: ${diamondFromRepo != null}, 데이터: $diamondFromRepo")
                        
                    } catch (e: Exception) {
                        logger.severe("[디버깅] 데이터베이스 조회 오류: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                logger.severe("[디버깅] MongoDB 코루틴 클라이언트 생성 중 오류: ${e.message}")
                e.printStackTrace()
            }
            
            // 2. 기존 DB 연결 (이전 코드와 호환성 유지)
            val log_database = mongoDBManager.connectToDataBase("logs")
            val server_database = mongoDBManager.connectToDataBase("server")
            val playerCollection = server_database.getCollection("users")
            
            // 3. KMongo 기반 Coroutine Database 객체 생성 (트랜잭션 지원)
            bazaarCoroutineDB = coroutineClient.getDatabase("bazaar")
            
            // 4. 트랜잭션 관리자 구현체 생성
            databaseManager = MongoDatabaseManagerImpl(coroutineClient, bazaarCoroutineDB, logger)


            // --- Repository 구현체 생성 ---
            val categoryRepo = MongoCategoryRepositoryImpl(bazaarCoroutineDB)
            val productRepo = MongoProductRepositoryImpl(bazaarCoroutineDB,logger,pluginScope)
            val productGroupRepo = MongoProductGroupRepositoryImpl(bazaarCoroutineDB, logger)
            val orderRepo = MongoOrderRepositoryImpl(bazaarCoroutineDB)
            val transactionRepo = MongoTransactionRepositoryImpl(bazaarCoroutineDB, logger)
            
            // 실제 경제 서비스 구현체 생성
            val economyService = EconomyManager(playerCollection, logger)

            val inventoryService = object : InventoryService {
                override suspend fun hasItems(playerUUID: String, itemId: String, quantity: Int): Boolean {
                    val player = Bukkit.getPlayer(java.util.UUID.fromString(playerUUID))
                    if (player == null) {
                        Bukkit.getLogger().warning("플레이어를 찾을 수 없습니다: $playerUUID")
                        return false
                    }
                    
                    try {
                        val material = org.bukkit.Material.valueOf(itemId.uppercase())
                        var totalAmount = 0
                        
                        // 플레이어 인벤토리에서 해당 아이템 개수 계산
                        for (item in player.inventory.contents) {
                            if (item != null && item.type == material) {
                                totalAmount += item.amount
                            }
                        }
                        
                        val hasEnough = totalAmount >= quantity
                        Bukkit.getLogger().info("아이템 소유 확인: $playerUUID, $itemId, 요청: $quantity, 보유: $totalAmount, 결과: $hasEnough")
                        return hasEnough
                    } catch (e: IllegalArgumentException) {
                        Bukkit.getLogger().warning("유효하지 않은 아이템 ID: $itemId")
                        return false
                    }
                }
                
                override suspend fun removeItems(playerUUID: String, itemId: String, quantity: Int): Boolean {
                    val player = Bukkit.getPlayer(java.util.UUID.fromString(playerUUID))
                    if (player == null) {
                        Bukkit.getLogger().warning("플레이어를 찾을 수 없습니다: $playerUUID")
                        return false
                    }
                    
                    try {
                        val material = org.bukkit.Material.valueOf(itemId.uppercase())
                        var remainingToRemove = quantity
                        
                        // 인벤토리에서 해당 아이템을 찾아서 제거
                        for (i in 0 until player.inventory.size) {
                            val item = player.inventory.getItem(i)
                            if (item != null && item.type == material) {
                                if (item.amount >= remainingToRemove) {
                                    // 이 스택에서 모든 필요한 아이템을 제거할 수 있음
                                    item.amount -= remainingToRemove
                                    if (item.amount == 0) {
                                        player.inventory.setItem(i, null)
                                    }
                                    remainingToRemove = 0
                                    break
                                } else {
                                    // 이 스택의 모든 아이템을 제거하고 계속
                                    remainingToRemove -= item.amount
                                    player.inventory.setItem(i, null)
                                }
                            }
                        }
                        
                        val success = remainingToRemove == 0
                        Bukkit.getLogger().info("아이템 제거: $playerUUID, $itemId, 요청: $quantity, 제거 완료: ${quantity - remainingToRemove}, 결과: $success")
                        return success
                    } catch (e: IllegalArgumentException) {
                        Bukkit.getLogger().warning("유효하지 않은 아이템 ID: $itemId")
                        return false
                    }
                }
                
                override suspend fun addItems(playerUUID: String, itemId: String, quantity: Int): Boolean {
                    val player = Bukkit.getPlayer(java.util.UUID.fromString(playerUUID))
                    if (player == null) {
                        Bukkit.getLogger().warning("플레이어를 찾을 수 없습니다: $playerUUID")
                        return false
                    }
                    
                    try {
                        val material = org.bukkit.Material.valueOf(itemId.uppercase())
                        val itemStack = ItemStack(material, quantity)
                        
                        // 인벤토리에 아이템 추가
                        val remainingItems = player.inventory.addItem(itemStack)
                        val success = remainingItems.isEmpty()
                        
                        if (!success) {
                            Bukkit.getLogger().warning("인벤토리 공간 부족으로 일부 아이템 추가 실패: $playerUUID, $itemId, 요청: $quantity")
                            // 추가되지 못한 아이템은 땅에 드롭
                            for (leftoverItem in remainingItems.values) {
                                player.world.dropItemNaturally(player.location, leftoverItem)
                            }
                        }
                        
                        Bukkit.getLogger().info("아이템 추가: $playerUUID, $itemId, 수량: $quantity, 결과: $success")
                        return true // 드롭으로라도 모든 아이템이 처리되었으므로 true 반환
                    } catch (e: IllegalArgumentException) {
                        Bukkit.getLogger().warning("유효하지 않은 아이템 ID: $itemId")
                        return false
                    }
                }
            }
            
            // BazaarCoreService 임시 구현체 생성
            val bazaarCoreService = DummyBazaarCoreServiceImpl(
                orderRepository = orderRepo,
                transactionRepository = transactionRepo,
                economyService = economyService,
                inventoryService = inventoryService
            )
            
            // --- BazaarAPI 초기화 --- 
            bazaarAPI = BazaarAPIImpl(
                categoryRepository = categoryRepo,
                productRepository = productRepo,
                productGroupRepository = productGroupRepo,
                orderRepository = orderRepo,
                transactionRepository = transactionRepo,
                economyService = economyService,
                inventoryService = inventoryService,
                bazaarCoreService = bazaarCoreService,
                databaseManager = databaseManager
            )
            
            logger.info("[Bazaar] 초기화 완료: MongoDB 트랜잭션 지원 활성화됨")
        } catch (e: Exception) {
            logger.severe("[Bazaar] 초기화 실패: ${e.message}")
            e.printStackTrace()
        }
        val database: MongoDatabase = mongoDBManager.connectToDataBase("server")
        sidebarManager = SidebarManager(database)

        logger.info("플러그인이 활성화되었습니다.")

        //createDocsForTest(database)

        // 이벤트 리스너 등록
        val inventoryClickHandler = InventoryClickHandler()
        server.pluginManager.registerEvents(inventoryClickHandler, this)
        server.pluginManager.registerEvents(PlayerJoinHandler(database,this), this)
        server.pluginManager.registerEvents(DamageHandler(), this)
        server.pluginManager.registerEvents(SneakHandler(this,database), this)
        server.pluginManager.registerEvents(playerInteractHandler(this,database), this)
        server.pluginManager.registerEvents(PlayerHandler(), this)


        setPlayerSet(database)
        // GUI 등록
        registerGUIs(inventoryClickHandler,database)

        getCommand("suit")?.apply {
            setExecutor(SuitCommand(this@Main,database))
            setTabCompleter(SuitTabCompleter())
        }
        getCommand("prd")?.apply {
            setExecutor(PRDCommand(this@Main,database,getBazaarAPI() ))
            setTabCompleter(PRDCommand(this@Main,database,getBazaarAPI()))
        }

        startPlayerTask(this,database)
    }

    // BazaarAPI 접근 메소드
    fun getBazaarAPI(): BazaarAPI {
        return bazaarAPI
    }

    override fun onDisable() {
        // Plugin shutdown logic
        try {
            // MongoDB 클라이언트 종료
            coroutineClient.close()
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
        handler.registerGUI(shopgui(this,database,"build"))
        handler.registerGUI(shopgui(this,database,"bazaar"))
        handler.registerGUI(armoryGUI(this,database))
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
        // 바자 테스트 데이터 생성
        try {
            val bazaarDatabase = mongoDBManager.connectToDataBase("bazaar")
            
            // 1. 카테고리 테스트 데이터 생성 (categories 컬렉션)
            val categoryCollection = bazaarDatabase.getCollection("categories")
            
            // 기존 카테고리 데이터가 있는지 확인
            val existingCategoryCount = categoryCollection.countDocuments()
            if (existingCategoryCount == 0L) {
                val categories = listOf(
                    Document().apply {
                        put("_id", "FARMING")
                        put("name", "농사")
                        put("description", "농업 관련 아이템")
                        put("displayOrder", 1)
                        put("parentCategoryId", null)
                    },
                    Document().apply {
                        put("_id", "MINING")
                        put("name", "광업")
                        put("description", "채굴 관련 아이템")
                        put("displayOrder", 2)
                        put("parentCategoryId", null)
                    },
                    Document().apply {
                        put("_id", "COMBAT")
                        put("name", "전투")
                        put("description", "전투 관련 아이템")
                        put("displayOrder", 3)
                        put("parentCategoryId", null)
                    },
                    Document().apply {
                        put("_id", "WOOD_FISH")
                        put("name", "목재 & 낚시")
                        put("description", "목재 및 낚시 관련 아이템")
                        put("displayOrder", 4)
                        put("parentCategoryId", null)
                    },
                    Document().apply {
                        put("_id", "ODDITIES")
                        put("name", "특수 아이템")
                        put("description", "특별한 아이템들")
                        put("displayOrder", 5)
                        put("parentCategoryId", null)
                    }
                )
                
                categories.forEach { categoryCollection.insertOne(it) }
                logger.info("Category 테스트 데이터 ${categories.size}개 생성 완료")
            } else {
                logger.info("Category 데이터가 이미 존재합니다 (${existingCategoryCount}개)")
            }
            
            // 2. ProductGroup 테스트 데이터 생성
            val productGroupCollection = bazaarDatabase.getCollection("productGroups")
            
            // 기존 ProductGroup 데이터가 있는지 확인
            val existingProductGroupCount = productGroupCollection.countDocuments()
            logger.info("기존 ProductGroup 데이터 개수: $existingProductGroupCount")
            
            // 잘못된 categoryId를 가진 기존 데이터를 삭제하고 새로 생성
            if (existingProductGroupCount > 0L) {
                logger.info("기존 ProductGroup 데이터를 삭제합니다...")
                productGroupCollection.deleteMany(Document())
                logger.info("기존 ProductGroup 데이터 삭제 완료")
            }
            
            // 카테고리별 ProductGroup 생성
            val productGroups = listOf(
                Document().apply {
                    put("_id", "WHEAT_GROUP")
                    put("name", "밀")
                    put("categoryId", "FARMING")
                    put("material", "WHEAT")
                    put("description", "밀 관련 아이템")
                    put("displayOrder", 1)
                },
                Document().apply {
                    put("_id", "IRON_GROUP")
                    put("name", "철 무기")
                    put("categoryId", "COMBAT")
                    put("material", "IRON_SWORD")
                    put("description", "철로 만든 무기류")
                    put("displayOrder", 1)
                },
                Document().apply {
                    put("_id", "WOOD_GROUP")
                    put("name", "나무")
                    put("categoryId", "WOOD_FISH")
                    put("material", "OAK_LOG")
                    put("description", "각종 나무 아이템")
                    put("displayOrder", 1)
                },
                Document().apply {
                    put("_id", "FISH_GROUP")
                    put("name", "생선")
                    put("categoryId", "WOOD_FISH")
                    put("material", "COD")
                    put("description", "각종 생선류")
                    put("displayOrder", 2)
                },
                Document().apply {
                    put("_id", "EMERALD_GROUP")
                    put("name", "에메랄드")
                    put("categoryId", "ODDITIES")
                    put("material", "EMERALD")
                    put("description", "특이한 녹색 보석")
                    put("displayOrder", 1)
                },
                Document().apply {
                    put("_id", "DIAMOND_GROUP")
                    put("name", "다이아몬드")
                    put("categoryId", "MINING")
                    put("material", "DIAMOND")
                    put("description", "다이아몬드 관련 아이템")
                    put("displayOrder", 1)
                }
            )
            
            productGroups.forEach { productGroupCollection.insertOne(it) }
            logger.info("ProductGroup 테스트 데이터 ${productGroups.size}개 생성 완료")
            
            // 3. Product 테스트 데이터 생성
            val productCollection = bazaarDatabase.getCollection("products")
            
            // 기존 상품 데이터가 있는지 확인
            val existingProductCount = productCollection.countDocuments()
            if (existingProductCount == 0L) {
                val products = listOf(
                    Document().apply {
                        put("_id", "DIAMOND")
                        put("name", "다이아몬드")
                        put("description", "희귀한 보석")
                        put("category", "MINING")
                        put("productGroupId", "DIAMOND_GROUP")
                        put("material", "DIAMOND")
                        put("basePrice", 100.0)
                        put("isTradable", true)
                    },
                    Document().apply {
                        put("_id", "DIAMOND_BLOCK")
                        put("name", "다이아몬드 블록")
                        put("description", "다이아몬드로 만든 블록")
                        put("category", "MINING")
                        put("productGroupId", "DIAMOND_GROUP")
                        put("material", "DIAMOND_BLOCK")
                        put("basePrice", 900.0)
                        put("isTradable", true)
                    },
                    Document().apply {
                        put("_id", "WHEAT")
                        put("name", "밀")
                        put("description", "기본적인 농작물")
                        put("category", "FARMING")
                        put("productGroupId", "WHEAT_GROUP")
                        put("material", "WHEAT")
                        put("basePrice", 2.0)
                        put("isTradable", true)
                    },
                    Document().apply {
                        put("_id", "IRON_SWORD")
                        put("name", "철 검")
                        put("description", "기본적인 철 검")
                        put("category", "COMBAT")
                        put("productGroupId", "IRON_GROUP")
                        put("material", "IRON_SWORD")
                        put("basePrice", 25.0)
                        put("isTradable", true)
                    },
                    Document().apply {
                        put("_id", "OAK_LOG")
                        put("name", "참나무 원목")
                        put("description", "기본적인 나무")
                        put("category", "WOOD_FISH")
                        put("productGroupId", "WOOD_GROUP")
                        put("material", "OAK_LOG")
                        put("basePrice", 1.0)
                        put("isTradable", true)
                    },
                    Document().apply {
                        put("_id", "COD")
                        put("name", "대구")
                        put("description", "신선한 대구")
                        put("category", "WOOD_FISH")
                        put("productGroupId", "FISH_GROUP")
                        put("material", "COD")
                        put("basePrice", 5.0)
                        put("isTradable", true)
                    },
                    Document().apply {
                        put("_id", "EMERALD")
                        put("name", "에메랄드")
                        put("description", "특이한 녹색 보석")
                        put("category", "ODDITIES")
                        put("productGroupId", "EMERALD_GROUP")
                        put("material", "EMERALD")
                        put("basePrice", 50.0)
                        put("isTradable", true)
                    }
                )
                
                products.forEach { productCollection.insertOne(it) }
                logger.info("Product 테스트 데이터 ${products.size}개 생성 완료")
            } else {
                logger.info("Product 데이터가 이미 존재합니다 (${existingProductCount}개)")
            }
            
            // 4. 서버 연구 데이터 생성
            val serverCollection = database.getCollection("server")
            
            // 기존 서버 데이터가 있는지 확인
            val existingServerCount = serverCollection.countDocuments()
            if (existingServerCount == 0L) {
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

                logger.info("Inserted document ID: ${result.insertedId}")
                serverCollection.insertOne(EngineDoc)
                serverCollection.insertOne(ArmorDoc)
                serverCollection.insertOne(MaterialDoc)
                serverCollection.insertOne(MagicDoc)
                serverCollection.insertOne(SkillDoc)
                
                logger.info("서버 연구 데이터 생성 완료")
            } else {
                logger.info("서버 데이터가 이미 존재합니다 (${existingServerCount}개)")
            }
            
        } catch (e: Exception) {
            logger.warning("바자 테스트 데이터 생성 중 오류: ${e.message}")
        }

    }

}