package prd.peurandel.prdcore.EventListner

import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import prd.peurandel.prdcore.Handler.SkillHandlerAnnotation
import prd.peurandel.prdcore.Manager.PlayerDataCache
import prd.peurandel.prdcore.Manager.SkillManager
import prd.peurandel.prdcore.Manager.SkillTickEvent
import prd.peurandel.prdcore.Manager.SkillTriggerEvent
import java.util.*

object SkillHandler {
    @SkillHandlerAnnotation(skillId = "fly")
    fun FlySkill(plugin: JavaPlugin, event: SkillTriggerEvent) {
        val player = event.context as? Player ?: return
        val map: MutableMap<String, Any?> = PlayerDataCache.cache[player.uniqueId] ?: return
        if(map["onFlightMode"]==true) {
            map["onFlightMode"] = false
            player.sendMessage("비행모드를 비활성화 했습니다.")
            map["isFlight"] = false
        } else {
            map["onFlightMode"] = true
            player.sendMessage("비행모드를 활성화 했습니다.")
            map["EngineCharge"] = 0.0.toFloat()
        }

    }
    @SkillHandlerAnnotation(skillId = "repulsor")
    fun RepulsorSkill(plugin: JavaPlugin, event: SkillTriggerEvent) {
        val skillManager = SkillManager(plugin)
        val player = event.context as? Player ?: return
        val location = player.eyeLocation
        val direction = location.direction.normalize() // 바라보는 방향 벡터를 가져와 정규화합니다.
        val speed = 3.0 // 엔티티가 날아갈 속도를 설정합니다.

        // ArmorStand 생성
        val laser = player.world.spawnEntity(location, EntityType.MARKER) as Marker

        laser.persistentDataContainer.set(NamespacedKey(plugin, "type"), PersistentDataType.STRING, "skill")

        // 레이저임을 식별하기 위한 데이터 태그
        val key = NamespacedKey(plugin, "skill")
        laser.persistentDataContainer.set(key, PersistentDataType.STRING, "repulsor")

        // 발사한 플레이어 정보 저장 (틱에서 데미지 제외 등에 사용 가능)
        val shooterKey = NamespacedKey(plugin, "skill_owner")
        laser.persistentDataContainer.set(shooterKey, PersistentDataType.STRING, player.uniqueId.toString())

        // 생성 시간 저장 (청크 언로드/로드에도 일관적인 수명 관리를 위함)
        val creationTimeKey = NamespacedKey(plugin, "creation_time")
        laser.persistentDataContainer.set(creationTimeKey, PersistentDataType.LONG, System.currentTimeMillis())
        
        // 최대 수명 저장 (15초 = 15000ms)
        val lifespanKey = NamespacedKey(plugin, "max_lifespan")
        laser.persistentDataContainer.set(lifespanKey, PersistentDataType.LONG, 15000L)

        // **엔티티의 이동 방향 (velocity) 설정**
        laser.velocity = direction.multiply(speed)

        // 일정 시간 후 ArmorStand 제거 (틱 방식으로 충돌 처리하므로 너무 오래 남아있으면 안됨)
        // 청크 언로드/로드에 대비해 SkillTick에서도 수명을 확인하도록 수정했으므로 여기서의 제거 작업은 백업용
        object : BukkitRunnable() {
            override fun run() {
                if (laser.isValid) {
                    laser.remove()
                }
            }
        }.runTaskLater(plugin, 300)
    }

}