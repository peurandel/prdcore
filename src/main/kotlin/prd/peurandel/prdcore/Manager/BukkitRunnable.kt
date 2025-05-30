package prd.peurandel.prdcore.Manager

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import kotlinx.serialization.json.Json
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bson.Document
import org.bukkit.*
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.ScoreboardManager
import org.bukkit.util.NumberConversions
import org.bukkit.util.Vector
import prd.peurandel.prdcore.EventListner.SkillTick
import java.util.*
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class BukkitRunnable(plugin: JavaPlugin,database: MongoDatabase) : BukkitRunnable() {

    val plugin = plugin
    val database = database
    val suitManager = SuitManager(plugin, database)
    val skillManager = SkillManager(plugin)
    val skillTick = SkillTick(plugin)
    private val sidebarManager = SidebarManager(database)
    private val scoreboardManager: ScoreboardManager = Bukkit.getScoreboardManager()

    override fun run() {
        // 모든 플레이어를 대상으로 연산 수행
        for (world in Bukkit.getServer().worlds) {
            for (entity in world.entities) {

                // 레이저임을 식별하기 위한 데이터 태그
                val key = entity.persistentDataContainer.get(NamespacedKey(plugin, "skill"), PersistentDataType.STRING) ?: "null"
                val type = entity.persistentDataContainer.get(NamespacedKey(plugin, "type"), PersistentDataType.STRING) ?: "null"
                if(entity is Player) {
                    performOperation(entity)
                } else if(type == "skill") {
                    skillTick.tick(key,entity)
                }
            }
        }
    }

    private fun performOperation(player: Player) {
        val key = player.uniqueId
        if (!isInHash(key)) return
        val playerCollection = database.getCollection("users")
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player.name)).first().toJson())

        // Get the player's specific scoreboard from sidebarManager
        val playerScoreboard = sidebarManager.getPlayerScoreboard(player)

        // Update the sidebar with the player's specific scoreboard
        sidebarManager.updateSidebar(player, playerScoreboard, user.money, 0, user.research_point)

        val map: MutableMap<String, Any?> = PlayerDataCache.cache[key] ?: return
        if (isHavingSuit(map)) SuitHandler(map, player)
        else {
            if(isHavingController(player)) player.inventory.setItemInOffHand(ItemStack(Material.AIR))
        }

    }

    private fun isInHash(key: UUID): Boolean {
        return PlayerDataCache.cache[key] != null
    }

    private fun isHavingSuit(map: MutableMap<String, Any?>): Boolean {
        return map["suit"] != null
    }

    private fun SuitHandler(map: MutableMap<String, Any?>, player: Player) {


        val suit = map["suit"] as Document
        val Suit : WardrobeItem = Json.decodeFromString(suit.toJson())

        //controller handler
        controllerHandler(player)
        //Energy
        val energy = Suit.energy as Int
        val max_energy = Suit.max_energy as Int
        val energyPer = energy * 100 / max_energy
        //Durability

        val durability = Suit.durability as Int
        val max_durability = Suit.max_durability as Int
        val durabilityPer = durability * 100 / max_durability
        val currentSpeed = map["flightSpeed"] as? Vector ?: Vector(0.0, 0.0, 0.0)
        val currentSpeedInKmPerHour: Int = (currentSpeed.length() * 72).toInt()

        val actionBarMessage = StringBuilder("${ChatColor.GREEN}E: $energyPer%")

        if (map["isFlight"] == true) {
            actionBarMessage.append("${ChatColor.WHITE} $currentSpeedInKmPerHour km/h")
        }
        if(map["onFlightMode"] == true) { /*엔진 차징용*/
            map["EngineCharge"] = ChargeEngine(map)
            actionBarMessage.append("${ChatColor.WHITE} ${(map["EngineCharge"] as Double *100).toInt()}% ")
        }

        val skillDoc = suit["skill"] as Document
        val heldItemSlot = player.inventory.heldItemSlot
        val slotskill = skillDoc["slot$heldItemSlot"] as ArrayList<*>
        val skill = if(slotskill.isNotEmpty() && slotskill[0] != null) {
            slotskill[0] as Document
        } else null

        if (skill!=null) actionBarMessage.append("${ChatColor.GRAY} ${ChatColor.BOLD} ${skill["name"]} ${ChatColor.RESET}")

        actionBarMessage.append(" ${ChatColor.AQUA}D: $durabilityPer%")

        player.sendActionBar(actionBarMessage.toString())

        //if(energy < max_energy) suit.set("energy",energy+1)

        //내구도를 다 쓰면?
        if (durability <= 0) {
            player.sendMessage("슈트가 박살났습니다!")
            suitManager.offSuit(player)
            return
        }

        //저항
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 20, 4))

        //비행
        if (map["isFlight"] == (true ?: false)) flightHandler(map, player)

        // 스킬
        if (map["right_clicked"] == true) {
            if (player.currentInput.isSneak) {
                Collections.rotate(slotskill,-1)
            }
            else {
                if(slotskill.isNotEmpty() && slotskill[0] != null) {
                    val skill = slotskill[0] as Document
                    skillManager.triggerSkill("${skill["type"]}", player)

                }
            }
            map["right_clicked"] = false
        }

    }

    fun ChargeEngine(map: MutableMap<String, Any?>): Double {
        var engineCharge= map["EngineCharge"] as? Double ?: 0.0
        if (engineCharge<1.0) engineCharge += 0.01
        return engineCharge
    }
    fun controllerHandler(player: Player) {
        val controllSlot = player.inventory.itemInOffHand
        if(controllSlot.getData(DataComponentTypes.ITEM_NAME) != Component.text("컨트롤러")) {
            player.inventory.addItem(controllSlot)
            add_controller(player)
        }
    }

    fun isHavingController(player: Player) : Boolean {
        val itemInOffHand = player.inventory.itemInOffHand
        return itemInOffHand.getData(DataComponentTypes.ITEM_NAME) == Component.text("컨트롤러")
    }
    fun add_controller(player: Player) {
        val controller = ItemStack.of(Material.STICK)
        val itemModelKey: Key = Key.key("minecraft", "air")
        val itemNameComponent: Component = Component.text("컨트롤러")
        val controllerMeta = controller.itemMeta
        controller.setData(DataComponentTypes.ITEM_MODEL,itemModelKey)
        controller.setData(DataComponentTypes.ITEM_NAME,itemNameComponent)
        player.inventory.setItemInOffHand(controller)
    }
    fun flightHandler(map: MutableMap<String, Any?>, player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 20, 1, false, false))
        player.world.spawnParticle(Particle.FLAME, player.location, 3, 0.0, 0.0, 0.0, 0.1, null)

        if (map["EngineCharge"]==null) {
            player.sendMessage("${ChatColor.RED}ERROR: NOT FOUND ENGINE CHARGE")
        }

        if (player.isOnGround) stopFlight(player)

        var currentSpeed = map["flightSpeed"] as? Vector ?: Vector(0.0, 0.0, 0.0)
        val suit = map["suit"] as Document

        val acceleration = calcurateAccelerate(map,suit)


        val playerDirection = player.location.direction.normalize()

        if (player.currentInput.isForward) {
            //val targetSpeed = playerDirection.multiply(acceleration)

            //currentSpeed = lerpVector(currentSpeed, currentSpeed.add(targetSpeed), 0.1)

            //currentSpeed = currentSpeed.add(forwardVector)
            currentSpeed = forwardHandler(player,acceleration)
        }

        if (player.currentInput.isBackward) {
            val currentSpeedMagnitude = currentSpeed.length()

            // 현재 속도가 줄어들도록 설정. 만약 감소량이 현재 속도보다 크다면 0으로 설정
            val newSpeedMagnitude = if (currentSpeedMagnitude > acceleration) currentSpeedMagnitude - acceleration else 0.0

            // 방향을 유지하면서 크기만 조정
            currentSpeed = currentSpeed.normalize().multiply(newSpeedMagnitude)
        }

        if (player.currentInput.isRight) {
            // 플레이어 진행 방향(playerDirection)을 기준으로 우측 벡터 계산
            val rightVector = playerDirection.crossProduct(Vector(0,1,0)).normalize().multiply(acceleration/4)
            currentSpeed = currentSpeed.add(rightVector)
        }

        if (player.currentInput.isLeft) {
            // 플레이어 진행 방향(playerDirection)을 기준으로 좌측 벡터 계산
            val leftVector = playerDirection.crossProduct(Vector(0,1,0)).normalize().multiply(-acceleration/4)
            currentSpeed = currentSpeed.add(leftVector)
        }

        // 8. 수직 상승 입력 처리 (점프 키)
        if (player.currentInput.isJump) {
            currentSpeed = currentSpeed.add(Vector(0.0, acceleration/2, 0.0)) // 수직 상승 가속도 (acceleration/2 로 설정, 조절 가능)
        }


        val airDensity = 1.225 // 공기 밀도 (kg/m³) - 설정 파일에서 가져오거나 상수 값으로 사용
        val dragCoefficient = (suit["dragCoefficient"] ?: 0.47) as Double // 항력 계수 - Suit 설정에서 가져오기 (기본값 0.47)
        val playerArea = 0.5 // 플레이어 단면적 (m²) - 설정 파일에서 가져오거나 상수 값으로 사용

        val speedMagnitude = currentSpeed.length()
        val airResistanceMagnitude = 0.10 * airDensity * speedMagnitude * speedMagnitude * dragCoefficient * playerArea

        val resistanceVector = currentSpeed.clone().normalize().multiply(airResistanceMagnitude)
        currentSpeed = currentSpeed.subtract(resistanceVector)


        // currentSpeed의 크기가 0이 아닐 때만 업데이트
        if (currentSpeed.isFinite() && currentSpeed.length() > 0) {
            player.velocity = currentSpeed
            map["flightSpeed"] = currentSpeed
        } else {
            map["flightSpeed"] = Vector(0.0, 0.0, 0.0)
        }
    }



    fun calcurateAccelerate(map: MutableMap<String, Any?>,suit: Document): Double {
        val engineCharge= map["EngineCharge"] as Double
        var propulsion: Double = (suit["propulsion"] ?: 2000.0) as Double
        propulsion *= engineCharge
        val armorDoc = suit["armor"] as Document
        val armorWeight = armorDoc["weight"] as Int
        //val height = map["height"] as Int
        val weight = (180.0 / 100) * (180.0 / 100) * 25
        val suitWeight = ((suit["weight"] as Double) * 4.8)*(1.0+armorWeight/100)
        return propulsion/((suitWeight + weight)*20)
    }
    fun forwardHandler(player: Player, accelerate: Double): Vector {
        //가속도
        return addAccelerate(player,accelerate)
    }
    fun addAccelerate(player: Player, accelerate: Double): Vector {
        val velocity = player.velocity
        val direction = player.location.direction.clone()

        // 목표 방향 벡터 (플레이어 시선 방향) - 단위 벡터로 정규화
        val targetDirection = direction.normalize()

        // 고정 보간 비율 (속도에 독립적으로 유지)
        val factor = 0.6 // 보간 비율 고정 (조절 가능)

        // 현재 속도 벡터와 목표 방향 벡터를 구면 선형 보간 (Slerp) - **정규화되지 않은 velocity 사용**
        val slerpedDirection = slerpVector(velocity.clone(), targetDirection, factor) // **[수정됨] velocity.clone() 사용 (정규화 제거)**

        // 가속 벡터는 보간된 방향 벡터 (slerpedDirection) 를 사용하여 계산
        val accelerationVector = slerpedDirection.multiply(accelerate)

        // 최종 속도 벡터 계산 (보간된 속도 + 가속 벡터)
        val newVelocity = velocity.add(accelerationVector)

        return newVelocity
    }


    fun lerpVector(current: Vector, target: Vector, factor: Double): Vector {
        return Vector(
            current.x + (target.x - current.x) * factor,
            current.y + (target.y - current.y) * factor,
            current.z + (target.z - current.z) * factor
        )
    }

    fun slerpVector(current: Vector, target: Vector, factor: Double): Vector {
        // **[수정됨] slerpVector 내부에서 정규화**
        val currentNormal = current.normalize()
        val targetNormal = target.normalize()

        var dot = currentNormal.dot(targetNormal)

        // 벡터가 거의 반대 방향인 경우, acos 연산 오류 방지 (NaN 방지)
        dot = min(1.0, max(-1.0, dot))

        val theta = acos(dot) // 두 벡터 사이의 각도 (라디안)

        if (theta < 1e-5) { // 각도가 매우 작은 경우, 선형 보간으로 대체 (특이점 회피)
            return lerpVector(current, target, factor)
        }

        val sinTheta = sin(theta)

        val startFactor = sin((1 - factor) * theta) / sinTheta
        val endFactor = sin(factor * theta) / sinTheta

        // **[수정됨] 정규화된 방향으로 보간하되, 크기는 유지**
        return Vector(
            startFactor * current.x + endFactor * target.x,
            startFactor * current.y + endFactor * target.y,
            startFactor * current.z + endFactor * target.z
        ).normalize().multiply(current.length() + (target.length() - current.length()) * factor) // **[수정됨] 크기 보간 추가**
    }
    fun Vector.isFinite(): Boolean {
        return NumberConversions.isFinite(x) && NumberConversions.isFinite(y) && NumberConversions.isFinite(z)
    }


    fun startFlight(player: Player, map: MutableMap<String, Any?>) {
        //마커 소환
        map["isFlight"] = true
        map["flightSpeed"] = Vector(0,0,0)
        player.world.spawnParticle(Particle.CLOUD, player.location, 10, 0.3, 0.0, 0.3, 0.2, null)
    }


    fun stopFlight(player: Player) {
        val map: MutableMap<String, Any?> = PlayerDataCache.cache[player.uniqueId] ?: return

        //해시에 플라이 값 초기화
        map["isFlight"] = false
    }
}

// 플러그인 초기화 시 작업 등록
fun startPlayerTask(plugin: JavaPlugin,database: MongoDatabase) {
    val task = BukkitRunnable(plugin,database)
    task.runTaskTimer(plugin, 0L, 1L) // 1틱마다 실행 (20틱 = 1초)
}
