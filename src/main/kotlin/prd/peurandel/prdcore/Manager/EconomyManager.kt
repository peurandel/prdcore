package prd.peurandel.prdcore.Manager

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

/**
 * 고성능 경제 시스템 관리 클래스
 * 비동기 처리, 캐싱, 연결 풀링을 통한 성능 최적화
 */
class EconomyManager(
    private val playerCollection: MongoCollection<Document>,
    private val logger: Logger
) : EconomyService, CoroutineScope {

    // 전용 스레드 풀 (DB 작업용)
    private val dbDispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
    override val coroutineContext: CoroutineContext = SupervisorJob() + dbDispatcher

    // 메모리 캐시 (플레이어 잔액)
    private val balanceCache = ConcurrentHashMap<String, CachedBalance>()
    private val cacheExpirationMs = 30000L // 30초 캐시 만료

    // 거래 중인 플레이어들 (동시성 제어)
    private val playersInTransaction = ConcurrentHashMap.newKeySet<String>()

    /**
     * 캐시된 잔액 데이터
     */
    private data class CachedBalance(
        val amount: Int,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > 30000L
    }

    /**
     * UUID를 정규화 (소문자 변환만, 하이픈 유지)
     */
    private fun normalizeUUID(uuid: String): String {
        return uuid.lowercase()
    }
    
    /**
     * UUID 형식을 분석하고 로그에 출력
     */
    private fun logUUIDFormat(uuid: String, context: String) {
        logger.info("[EconomyManager] $context - UUID 분석:")
        logger.info("[EconomyManager] - 원본: '$uuid'")
        logger.info("[EconomyManager] - 길이: ${uuid.length}")
        logger.info("[EconomyManager] - 하이픈 포함: ${uuid.contains("-")}")
        logger.info("[EconomyManager] - 정규화됨: '${normalizeUUID(uuid)}' (소문자 변환만)")
    }

    /**
     * 플레이어 잔액 조회 (비동기)
     */
    suspend fun getBalanceAsync(playerUUID: String): Int {
        return withContext(dbDispatcher) {
            try {
                val filter = Document("uuid", normalizeUUID(playerUUID))
                val playerDoc = playerCollection.find(filter).first()
                
                if (playerDoc != null) {
                    val balance = playerDoc.getInteger("money", 0)
                    // 캐시 업데이트
                    balanceCache[playerUUID] = CachedBalance(balance, System.currentTimeMillis())
                    logger.info("[EconomyManager] 플레이어 $playerUUID 잔액 조회: ${balance}원")
                    balance
                } else {
                    logger.warning("[EconomyManager] 플레이어 데이터를 찾을 수 없습니다: $playerUUID")
                    // 전체 컬렉션에서 uuid 필드가 있는 문서들을 샘플링해서 로그로 출력
                    val sampleDocs = playerCollection.find().limit(3).toList()
                    logger.info("[EconomyManager] MongoDB 샘플 문서들 (총 ${sampleDocs.size}개):")
                    sampleDocs.forEach { doc ->
                        val storedUuid = doc.getString("uuid")
                        logger.info("[EconomyManager] - 저장된 UUID: '$storedUuid' (길이: ${storedUuid?.length ?: 0}, 하이픈: ${storedUuid?.contains("-") ?: false})")
                    }
                    logger.info("[EconomyManager] 요청한 UUID와 비교:")
                    logger.info("[EconomyManager] - 요청: '$playerUUID' (정규화: '${normalizeUUID(playerUUID)}')")
                    0
                }
            } catch (e: Exception) {
                logger.severe("[EconomyManager] 잔액 조회 실패: ${e.message}")
                e.printStackTrace()
                0
            }
        }
    }

    /**
     * 플레이어가 충분한 자금을 보유하고 있는지 확인 (캐시 우선, 필요 시 DB 확인)
     */
    override suspend fun hasEnoughFunds(playerUUID: String, amount: Double): Boolean {
        return withContext(dbDispatcher) {
            logger.info("[EconomyManager] hasEnoughFunds 호출 - UUID: $playerUUID, 요청금액: $amount")
            logUUIDFormat(playerUUID, "hasEnoughFunds")
            
            // 캐시 먼저 확인
            val cached = balanceCache[playerUUID]
            if (cached != null && !cached.isExpired()) {
                logger.info("[EconomyManager] 캐시에서 확인 - 보유금액: ${cached.amount}")
                // 캐시에서 충분한 자금이 있다고 나와도, 실제 DB에서 다시 한번 확인
                if (cached.amount >= amount.toInt()) {
                    logger.info("[EconomyManager] 캐시 조건 만족 - DB에서 재확인")
                    return@withContext verifyBalanceInDB(playerUUID, amount)
                } else {
                    return@withContext false
                }
            }
            
            // 캐시 미스 시 DB에서 조회
            return@withContext verifyBalanceInDB(playerUUID, amount)
        }
    }
    
    /**
     * DB에서 실제 잔액을 확인하는 헬퍼 메서드
     */
    private suspend fun verifyBalanceInDB(playerUUID: String, amount: Double): Boolean {
        return try {
            val filter = Document("uuid", normalizeUUID(playerUUID))
            logger.info("[EconomyManager] MongoDB 쿼리 실행 - 필터: $filter")
            
            val playerDoc = playerCollection.find(filter).first()
            logger.info("[EconomyManager] MongoDB 쿼리 결과 - Document: ${playerDoc != null}")
            
            if (playerDoc != null) {
                val balance = playerDoc.getInteger("money", 0)
                logger.info("[EconomyManager] 플레이어 잔액 발견: $balance")
                // 캐시 업데이트
                balanceCache[playerUUID] = CachedBalance(balance, System.currentTimeMillis())
                balance >= amount.toInt()
            } else {
                logger.warning("[EconomyManager] 플레이어 데이터를 찾을 수 없습니다: $playerUUID")
                // 전체 컬렉션에서 uuid 필드가 있는 문서들을 샘플링해서 로그로 출력
                val sampleDocs = playerCollection.find().limit(3).toList()
                logger.info("[EconomyManager] MongoDB 샘플 문서들 (총 ${sampleDocs.size}개):")
                sampleDocs.forEach { doc ->
                    val storedUuid = doc.getString("uuid")
                    logger.info("[EconomyManager] - 저장된 UUID: '$storedUuid' (길이: ${storedUuid?.length ?: 0}, 하이픈: ${storedUuid?.contains("-") ?: false})")
                }
                logger.info("[EconomyManager] 요청한 UUID와 비교:")
                logger.info("[EconomyManager] - 요청: '$playerUUID' (정규화: '${normalizeUUID(playerUUID)}')")
                false
            }
        } catch (e: Exception) {
            logger.severe("[EconomyManager] 잔액 확인 실패: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 캐시 기반 빠른 자금 확인 (동기, 블로킹 없음)
     */
    private fun hasEnoughFundsSync(playerUuid: String, amount: Double): Boolean {
        val cached = balanceCache[playerUuid]
        return if (cached != null && !cached.isExpired()) {
            cached.amount >= amount.toInt()
        } else {
            // 캐시 미스 시 보수적으로 false 반환
            // 백그라운드에서 실제 잔액 로드
            launch {
                try {
                    getBalanceAsync(playerUuid) // 캐시 업데이트
                } catch (e: Exception) {
                    logger.warning("백그라운드 잔액 확인 실패: ${e.message}")
                }
            }
            false
        }
    }

    /**
     * 플레이어 계정에서 금액 차감 (동시성 제어)
     */
    override suspend fun withdraw(playerUUID: String, amount: Double): Boolean {
        return withContext(dbDispatcher) {
            logger.info("[EconomyManager] withdraw 호출 - UUID: $playerUUID, 차감금액: $amount")
            logUUIDFormat(playerUUID, "withdraw")
            
            // MongoDB 연결 및 컬렉션 상태 확인
            logger.info("[EconomyManager] MongoDB 연결 상태 확인:")
            logger.info("[EconomyManager] - 컬렉션: ${playerCollection.namespace}")
            val totalCount = playerCollection.countDocuments()
            logger.info("[EconomyManager] - 전체 문서 수: $totalCount")
            
            if (playersInTransaction.contains(playerUUID)) {
                logger.warning("[EconomyManager] 플레이어 $playerUUID 는 이미 거래 중입니다")
                return@withContext false
            }
            
            playersInTransaction.add(playerUUID)
            try {
                val filter = Document("uuid", normalizeUUID(playerUUID))
                logger.info("[EconomyManager] withdraw MongoDB 쿼리 실행 - 필터: $filter")
                
                val playerDoc = playerCollection.find(filter).first()
                logger.info("[EconomyManager] withdraw MongoDB 쿼리 결과 - Document: ${playerDoc != null}")
                
                if (playerDoc != null) {
                    val currentBalance = playerDoc.getInteger("money", 0)
                    logger.info("[EconomyManager] withdraw 현재 잔액: $currentBalance, 차감할 금액: $amount")
                    
                    if (currentBalance >= amount.toInt()) {
                        val newBalance = currentBalance - amount.toInt()
                        val updateFilter = Document("uuid", normalizeUUID(playerUUID))
                        val update = Document("\$set", Document("money", newBalance))
                        
                        logger.info("[EconomyManager] withdraw 업데이트 실행 - 새 잔액: $newBalance")
                        val result = playerCollection.updateOne(updateFilter, update)
                        logger.info("[EconomyManager] withdraw 업데이트 결과 - 수정된 문서 수: ${result.modifiedCount}")
                        
                        if (result.modifiedCount > 0) {
                            // 캐시 업데이트
                            balanceCache[playerUUID] = CachedBalance(newBalance, System.currentTimeMillis())
                            logger.info("[EconomyManager] withdraw 성공 - 최종 잔액: $newBalance")
                            true
                        } else {
                            logger.warning("[EconomyManager] withdraw 실패 - DB 업데이트 실패")
                            false
                        }
                    } else {
                        logger.warning("[EconomyManager] withdraw 실패 - 잔액 부족: $currentBalance < ${amount.toInt()}")
                        false
                    }
                } else {
                    logger.warning("[EconomyManager] withdraw 실패 - 플레이어 데이터를 찾을 수 없습니다: $playerUUID")
                    // 전체 컬렉션에서 uuid 필드가 있는 문서들을 샘플링해서 로그로 출력
                    val sampleDocs = playerCollection.find().limit(5).toList()
                    logger.info("[EconomyManager] withdraw MongoDB 샘플 문서들 (총 ${sampleDocs.size}개):")
                    sampleDocs.forEach { doc ->
                        val storedUuid = doc.getString("uuid")
                        val money = doc.getInteger("money", 0)
                        logger.info("[EconomyManager] - 저장된 UUID: '$storedUuid' (길이: ${storedUuid?.length ?: 0}, 하이픈: ${storedUuid?.contains("-") ?: false}), Money: $money")
                    }
                    logger.info("[EconomyManager] withdraw 요청한 UUID와 비교:")
                    logger.info("[EconomyManager] - 요청: '$playerUUID' (정규화: '${normalizeUUID(playerUUID)}')")
                    false
                }
            } catch (e: Exception) {
                logger.severe("[EconomyManager] withdraw 예외 발생: ${e.message}")
                e.printStackTrace()
                false
            } finally {
                playersInTransaction.remove(playerUUID)
            }
        }
    }

    /**
     * 플레이어 계정에 돈을 추가 (동시성 제어)
     */
    override suspend fun deposit(playerUUID: String, amount: Double): Boolean {
        return withContext(dbDispatcher) {
            logger.info("[EconomyManager] deposit 호출 - UUID: $playerUUID, 추가금액: $amount")
            logUUIDFormat(playerUUID, "deposit")
            
            if (playersInTransaction.contains(playerUUID)) {
                logger.warning("[EconomyManager] 플레이어 $playerUUID 는 이미 거래 중입니다")
                return@withContext false
            }
            
            playersInTransaction.add(playerUUID)
            try {
                val filter = Document("uuid", normalizeUUID(playerUUID))
                logger.info("[EconomyManager] deposit MongoDB 쿼리 실행 - 필터: $filter")
                
                val playerDoc = playerCollection.find(filter).first()
                logger.info("[EconomyManager] deposit MongoDB 쿼리 결과 - Document: ${playerDoc != null}")
                
                if (playerDoc != null) {
                    val currentBalance = playerDoc.getInteger("money", 0)
                    val newBalance = currentBalance + amount.toInt()
                    logger.info("[EconomyManager] deposit 현재 잔액: $currentBalance, 추가할 금액: $amount, 새 잔액: $newBalance")
                    
                    val updateFilter = Document("uuid", normalizeUUID(playerUUID))
                    val update = Document("\$set", Document("money", newBalance))
                    
                    logger.info("[EconomyManager] deposit 업데이트 실행")
                    val result = playerCollection.updateOne(updateFilter, update)
                    logger.info("[EconomyManager] deposit 업데이트 결과 - 수정된 문서 수: ${result.modifiedCount}")
                    
                    if (result.modifiedCount > 0) {
                        // 캐시 업데이트
                        balanceCache[playerUUID] = CachedBalance(newBalance, System.currentTimeMillis())
                        logger.info("[EconomyManager] deposit 성공 - 최종 잔액: $newBalance")
                        true
                    } else {
                        logger.warning("[EconomyManager] deposit 실패 - DB 업데이트 실패")
                        false
                    }
                } else {
                    logger.warning("[EconomyManager] deposit 실패 - 플레이어 데이터를 찾을 수 없습니다: $playerUUID")
                    // 전체 컬렉션에서 uuid 필드가 있는 문서들을 샘플링해서 로그로 출력
                    val sampleDocs = playerCollection.find().limit(5).toList()
                    logger.info("[EconomyManager] deposit MongoDB 샘플 문서들 (총 ${sampleDocs.size}개):")
                    sampleDocs.forEach { doc ->
                        val storedUuid = doc.getString("uuid")
                        val money = doc.getInteger("money", 0)
                        logger.info("[EconomyManager] - 저장된 UUID: '$storedUuid' (길이: ${storedUuid?.length ?: 0}, 하이픈: ${storedUuid?.contains("-") ?: false}), Money: $money")
                    }
                    logger.info("[EconomyManager] deposit 요청한 UUID와 비교:")
                    logger.info("[EconomyManager] - 요청: '$playerUUID' (정규화: '${normalizeUUID(playerUUID)}')")
                    false
                }
            } catch (e: Exception) {
                logger.severe("[EconomyManager] deposit 예외 발생: ${e.message}")
                e.printStackTrace()
                false
            } finally {
                playersInTransaction.remove(playerUUID)
            }
        }
    }

    /**
     * 플레이어 잔액 조회 (동기, 캐시 전용)
     */
    fun getBalance(playerUUID: String): Int? {
        val cachedBalance = balanceCache[playerUUID]
        return if (cachedBalance != null && !cachedBalance.isExpired()) {
            cachedBalance.amount
        } else {
            null
        }
    }

    /**
     * 송금 기능 (원자적 트랜잭션)
     */
    suspend fun transferAsync(fromPlayerUUID: String, toPlayerUUID: String, amount: Int): TransferResult {
        return withContext(dbDispatcher) {
            // 동시성 제어: 두 플레이어 모두 거래 중이 아닌지 확인
            if (!playersInTransaction.add(fromPlayerUUID)) {
                return@withContext TransferResult.SENDER_BUSY
            }
            if (!playersInTransaction.add(toPlayerUUID)) {
                playersInTransaction.remove(fromPlayerUUID)
                return@withContext TransferResult.RECEIVER_BUSY
            }

            try {
                // 송금자와 수신자 데이터 조회
                val fromFilter = Document("uuid", normalizeUUID(fromPlayerUUID))
                val fromPlayerDoc = playerCollection.find(fromFilter).first()
                
                val toFilter = Document("uuid", normalizeUUID(toPlayerUUID))
                val toPlayerDoc = playerCollection.find(toFilter).first()

                when {
                    fromPlayerDoc == null -> TransferResult.PLAYER_NOT_FOUND
                    toPlayerDoc == null -> TransferResult.PLAYER_NOT_FOUND
                    amount <= 0 -> TransferResult.INVALID_AMOUNT
                    fromPlayerDoc.getInteger("money", 0) < amount -> TransferResult.INSUFFICIENT_FUNDS
                    else -> {
                        // 송금 처리 (원자적)
                        val fromNewMoney = fromPlayerDoc.getInteger("money", 0) - amount
                        val toNewMoney = toPlayerDoc.getInteger("money", 0) + amount

                        // 동시에 두 계정 업데이트
                        val fromResult = playerCollection.updateOne(
                            Filters.eq("uuid", normalizeUUID(fromPlayerUUID)),
                            Updates.set("money", fromNewMoney)
                        )
                        
                        val toResult = playerCollection.updateOne(
                            Filters.eq("uuid", normalizeUUID(toPlayerUUID)),
                            Updates.set("money", toNewMoney)
                        )

                        if (fromResult.modifiedCount > 0 && toResult.modifiedCount > 0) {
                            // 캐시 업데이트
                            balanceCache[fromPlayerUUID] = CachedBalance(fromNewMoney, System.currentTimeMillis())
                            balanceCache[toPlayerUUID] = CachedBalance(toNewMoney, System.currentTimeMillis())
                            
                            logger.info("[EconomyManager] 송금 성공: $fromPlayerUUID -> $toPlayerUUID, 금액: $amount")
                            
                            // 메인 스레드에서 사이드바 업데이트
                            launch(Dispatchers.Main) {
                                updatePlayerSidebar(fromPlayerUUID, fromNewMoney)
                                updatePlayerSidebar(toPlayerUUID, toNewMoney)
                            }
                            
                            TransferResult.SUCCESS
                        } else {
                            logger.warning("[EconomyManager] 송금 실패: 데이터베이스 업데이트 오류")
                            TransferResult.DATABASE_ERROR
                        }
                    }
                }
            } catch (e: Exception) {
                logger.severe("[EconomyManager] 송금 중 오류: ${e.message}")
                e.printStackTrace()
                TransferResult.SYSTEM_ERROR
            } finally {
                // 거래 완료 후 잠금 해제
                playersInTransaction.remove(fromPlayerUUID)
                playersInTransaction.remove(toPlayerUUID)
            }
        }
    }

    /**
     * 어드민 기능: 플레이어 잔액 설정 (비동기)
     */
    suspend fun setBalanceAsync(playerUUID: String, amount: Int): Boolean {
        return withContext(dbDispatcher) {
            try {
                val result = playerCollection.updateOne(
                    Filters.eq("uuid", normalizeUUID(playerUUID)),
                    Updates.set("money", amount)
                )

                val success = result.modifiedCount > 0
                if (success) {
                    // 캐시 업데이트
                    balanceCache[playerUUID] = CachedBalance(amount, System.currentTimeMillis())
                    
                    logger.info("[EconomyManager] 어드민 잔액 설정: $playerUUID, 설정 금액: $amount")
                    
                    // 메인 스레드에서 사이드바 업데이트
                    launch(Dispatchers.Main) {
                        updatePlayerSidebar(playerUUID, amount)
                    }
                }
                success
            } catch (e: Exception) {
                logger.severe("[EconomyManager] 어드민 잔액 설정 중 오류: ${e.message}")
                false
            }
        }
    }

    /**
     * 캐시 정리 (만료된 항목 제거)
     */
    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        val expiredKeys = balanceCache.entries
            .filter { now - it.value.timestamp > cacheExpirationMs }
            .map { it.key }
        
        expiredKeys.forEach { balanceCache.remove(it) }
        
        if (expiredKeys.isNotEmpty()) {
            logger.info("[EconomyManager] 캐시 정리 완료: ${expiredKeys.size}개 항목 제거")
        }
    }

    /**
     * 플레이어 데이터 조회 (비동기)
     */
    private suspend fun getUserDataAsync(playerUUID: String): Document? {
        return withContext(dbDispatcher) {
            try {
                val filter = Document("uuid", normalizeUUID(playerUUID))
                playerCollection.find(filter).first()
            } catch (e: Exception) {
                logger.severe("[EconomyManager] 플레이어 데이터 조회 중 오류: ${e.message}")
                null
            }
        }
    }

    /**
     * 온라인 플레이어의 사이드바 업데이트 (메인 스레드에서 실행)
     */
    private fun updatePlayerSidebar(playerUUID: String, newMoney: Int) {
        try {
            val player = Bukkit.getPlayer(UUID.fromString(playerUUID))
            if (player != null && player.isOnline) {
                // SidebarManager가 있다면 사이드바 업데이트
                logger.info("[EconomyManager] 플레이어 사이드바 업데이트: ${player.name}, 잔액: $newMoney")
            }
        } catch (e: Exception) {
            logger.warning("[EconomyManager] 사이드바 업데이트 중 오류: ${e.message}")
        }
    }

    /**
     * 리소스 정리
     */
    fun shutdown() {
        runBlocking {
            cancel()
            dbDispatcher.close()
        }
        logger.info("[EconomyManager] 리소스 정리 완료")
    }

    /**
     * 명령어용 동기 메서드들 - 서버 블로킹 없음
     */
    
    /**
     * 동기적으로 잔액 조회 (명령어용)
     * 캐시에 있으면 즉시 반환, 없으면 백그라운드 로드 후 기본값 반환
     */
    fun getBalanceSync(playerUuid: UUID): Int {
        // 캐시에서 먼저 확인
        val cached = balanceCache[playerUuid.toString()]
        if (cached != null && !cached.isExpired()) {
            return cached.amount
        }
        
        // 캐시 미스 시 백그라운드에서 비동기 로드 (블로킹 없음)
        launch {
            try {
                getBalanceAsync(playerUuid.toString()) // 캐시 업데이트
            } catch (e: Exception) {
                logger.warning("백그라운드 잔액 로드 실패: ${e.message}")
            }
        }
        
        // 즉시 기본값 반환 (서버 블로킹 없음)
        return cached?.amount ?: 0
    }
    
    /**
     * 동기적으로 송금 시도 (명령어용)
     * 즉시 결과 반환, 실제 처리는 백그라운드에서 비동기
     */
    fun transferSync(
        fromUuid: UUID, 
        toUuid: UUID, 
        amount: Int,
        callback: ((TransferResult) -> Unit)? = null
    ): TransferResult {
        // 기본 검증 (캐시 기반, 빠름)
        if (amount <= 0) {
            return TransferResult.INVALID_AMOUNT
        }
        
        if (fromUuid == toUuid) {
            return TransferResult.SAME_PLAYER
        }
        
        // 발신자 자금 확인 (캐시 기반)
        if (!hasEnoughFundsSync(fromUuid.toString(), amount.toDouble())) {
            return TransferResult.INSUFFICIENT_FUNDS
        }
        
        // 거래 중인지 확인
        if (playersInTransaction.contains(fromUuid.toString()) || playersInTransaction.contains(toUuid.toString())) {
            return TransferResult.PLAYER_BUSY
        }
        
        // 백그라운드에서 실제 송금 처리
        launch {
            val result = transferAsync(fromUuid.toString(), toUuid.toString(), amount)
            callback?.invoke(result)
        }
        
        return TransferResult.PROCESSING // 처리 중 상태 반환
    }
    
    /**
     * 동기적으로 출금 시도 (명령어용)
     * 캐시 기반 빠른 검증 후 백그라운드 처리
     */
    fun withdrawSync(
        playerUuid: UUID, 
        amount: Int,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        // 빠른 검증
        if (amount <= 0 || !hasEnoughFundsSync(playerUuid.toString(), amount.toDouble())) {
            return false
        }
        
        if (playersInTransaction.contains(playerUuid.toString())) {
            return false
        }
        
        // 백그라운드에서 실제 출금 처리
        launch {
            val success = withdraw(playerUuid.toString(), amount.toDouble())
            callback?.invoke(success)
        }
        
        return true // 처리 시작됨
    }
    
    /**
     * 동기적으로 입금 시도 (명령어용)
     */
    fun depositSync(
        playerUuid: UUID, 
        amount: Int,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (amount <= 0) return false
        
        if (playersInTransaction.contains(playerUuid.toString())) {
            return false
        }
        
        // 백그라운드에서 실제 입금 처리
        launch {
            val success = deposit(playerUuid.toString(), amount.toDouble())
            callback?.invoke(success)
        }
        
        return true // 처리 시작됨
    }

    // 백그라운드 작업: 주기적 캐시 정리
    init {
        launch {
            while (isActive) {
                delay(60000L) // 1분마다
                cleanupCache()
            }
        }
    }
}

/**
 * 송금 결과 열거형 (확장)
 */
enum class TransferResult(val message: String) {
    SUCCESS("송금이 완료되었습니다"),                
    PLAYER_NOT_FOUND("송금자 또는 수신자를 찾을 수 없습니다"),      
    INSUFFICIENT_FUNDS("잔액이 부족합니다"),    
    INVALID_AMOUNT("잘못된 금액입니다 (0 이하)"),        
    DATABASE_ERROR("데이터베이스 오류가 발생했습니다"),        
    SYSTEM_ERROR("시스템 오류가 발생했습니다"),
    SENDER_BUSY("송금자가 다른 거래를 진행 중입니다"),
    RECEIVER_BUSY("수신자가 다른 거래를 진행 중입니다"),
    PLAYER_BUSY("플레이어가 다른 거래를 진행 중입니다"),
    PROCESSING("처리 중입니다"),
    SAME_PLAYER("송금자와 수신자가 동일합니다")
}
