package prd.peurandel.prdcore.EventListner

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Marker
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Manager.SkillManager
import java.util.*

class SkillTick(val plugin: JavaPlugin) {

    fun tick(skillId: String, entity: Entity) {
        when (skillId) {
            "repulsor" -> RepulsorTick(entity as? Marker ?: return) // RepulsorTick은 Marker를 받도록 수정
            else -> plugin.logger.info("Unknown skillId: $skillId")
        }
    }

    private fun isCollidingWithWall(entity: Entity, distanceToCheck: Double = 0.5): Boolean {
        val velocity = entity.velocity
        if (velocity.lengthSquared() == 0.0) return false // 정지 상태면 벽 감지 안함

        val checkIncrement = velocity.clone().normalize().multiply(distanceToCheck)
        val nextLocation = entity.location.clone().add(checkIncrement)
        val block = nextLocation.block
        return block.type.isSolid
    }

    private fun getNearbyEnemies(entity: Entity, radius: Double, excludeShooter: LivingEntity? = null): List<LivingEntity> {
        val world = entity.world
        val nearbyEntities = world.getNearbyEntities(entity.location, radius, radius, radius) // 각 축에 대한 반경 명시
        val enemies = mutableListOf<LivingEntity>()
        for (nearbyEntity in nearbyEntities) {
            if (nearbyEntity is LivingEntity && nearbyEntity != entity && nearbyEntity != excludeShooter) {
                enemies.add(nearbyEntity)
            }
        }
        return enemies
    }

    private fun handleDamage(target: LivingEntity, damage:Double,source: LivingEntity?, particle: Particle = Particle.EXPLOSION, count: Int = 10) {
        // 데미지 적용
        target.damage(damage, source)
        
        // 파티클 효과
        target.world.spawnParticle(particle, target.location, count, 0.2, 0.2, 0.2, 0.0)
    }


    private fun RepulsorTick(repulsor: Marker) {
        val world = Bukkit.getWorlds().firstOrNull() ?: return
        if (!repulsor.isValid) return

        // 청크 언로드/로드 후에도 수명 체크를 위해 생성 시간 확인
        val creationTime = repulsor.persistentDataContainer
            .get(NamespacedKey(plugin, "creation_time"), PersistentDataType.LONG) ?: 0L
        val maxLifespan = repulsor.persistentDataContainer
            .get(NamespacedKey(plugin, "max_lifespan"), PersistentDataType.LONG) ?: 15000L
        
        // 현재 시간과 비교하여 수명을 체크
        val currentTime = System.currentTimeMillis()
        if (currentTime - creationTime > maxLifespan) {
            // 최대 수명을 초과했으므로 제거
            repulsor.remove()
            return
        }

        val repulsorLocation = repulsor.location
        val shooter: LivingEntity? = repulsor.persistentDataContainer
            .get(NamespacedKey(plugin, "skill_owner"), PersistentDataType.STRING)
            ?.let { UUID.fromString(it) }
            ?.let { Bukkit.getPlayer(it) }

        // 파티클 효과
        world.spawnParticle(Particle.FLAME, repulsorLocation, 3, 0.1, 0.1, 0.1, 0.0, null, true)

        val velocity = repulsor.velocity
        val velocityMagnitude = velocity.lengthSquared()

        if (velocityMagnitude > 0.0) {
            val normalizedVelocity = velocity.normalize()
            for (i in 1..10) { // 텔레포트 횟수 감소
                // 벽 감지
                if (isCollidingWithWall(repulsor)) {
                    repulsor.remove()
                    return
                }

                repulsor.teleport(repulsor.location.clone().add(normalizedVelocity.multiply(1))) // 이동 거리 감소

                // 주변 적 감지 및 충돌 처리
                val nearbyEnemies = getNearbyEnemies(repulsor, 1.0, shooter)
                if (nearbyEnemies.isNotEmpty()) {
                    for (enemy in nearbyEnemies) {
                        // 리펄서는 직접 체력을 감소시키는 방식으로 데미지 처리 (무적시간 우회)
                        handleDamage(enemy, 7.0, shooter, Particle.EXPLOSION, 10)
                        
                        // 맞췄을 때 주인에게 화살 맞은 소리 재생
                        (shooter as? Player)?.playSound(shooter.location, "minecraft:entity.arrow.hit_player", 1.0f, 1.0f)
                    }
                    
                    // 적과 충돌했으므로 리펄서 제거
                    repulsor.remove()
                    return
                }
                
                // 파티클 효과 (텔레포트마다 생성)
                world.spawnParticle(Particle.FLAME, repulsor.location, 3, 0.1, 0.1, 0.1, 0.0, null, true)
            }
        } else {
            // velocity가 0인 경우에 대한 처리 (선택 사항)
            // plugin.logger.info("Repulsor 엔티티의 velocity가 0입니다.")
        }
        repulsor.velocity = velocity // 마지막 velocity 다시 설정 (큰 의미는 없을 수 있음)
    }
}