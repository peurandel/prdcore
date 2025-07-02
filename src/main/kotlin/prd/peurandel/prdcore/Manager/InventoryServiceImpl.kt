package prd.peurandel.prdcore.Manager

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Logger

/**
 * InventoryService 인터페이스의 구현체
 * 플레이어 인벤토리에 아이템 추가, 제거, 확인하는 기능 제공
 */
class InventoryServiceImpl(private val plugin: JavaPlugin) : InventoryService {
    private val logger = plugin.logger

    /**
     * 플레이어가 특정 아이템을 충분히 가지고 있는지 확인
     */
    override suspend fun hasItems(playerUUID: String, itemId: String, quantity: Int): Boolean {
        try {
            val player = getPlayerByUUID(playerUUID) ?: return false

            // 현재는 단순히 다이아몬드만 처리 (예시)
            // 실제 구현에서는 itemId에 따라 적절한 아이템을 확인해야 함
            if (itemId == "DIAMOND") {
                val diamond = Material.DIAMOND
                val count = countItems(player, diamond)
                logger.info("[InventoryService] 플레이어 ${player.name}의 다이아몬드 수량: $count / 필요: $quantity")
                return count >= quantity
            } else {
                logger.warning("[InventoryService] 지원하지 않는 아이템 ID: $itemId")
                return false
            }
        } catch (e: Exception) {
            logger.severe("[InventoryService] hasItems 오류: ${e.message}")
            return false
        }
    }

    /**
     * 플레이어 인벤토리에서 아이템 제거
     */
    override suspend fun removeItems(playerUUID: String, itemId: String, quantity: Int): Boolean {
        try {
            val player = getPlayerByUUID(playerUUID) ?: return false

            if (itemId == "DIAMOND") {
                return removeItemsFromInventory(player, Material.DIAMOND, quantity)
            } else {
                logger.warning("[InventoryService] 지원하지 않는 아이템 ID: $itemId")
                return false
            }
        } catch (e: Exception) {
            logger.severe("[InventoryService] removeItems 오류: ${e.message}")
            return false
        }
    }

    /**
     * 플레이어 인벤토리에 아이템 추가
     */
    override suspend fun addItems(playerUUID: String, itemId: String, quantity: Int): Boolean {
        try {
            val player = getPlayerByUUID(playerUUID) ?: return false

            if (itemId == "DIAMOND") {
                val diamond = ItemStack(Material.DIAMOND, quantity)
                val leftover = player.inventory.addItem(diamond)

                if (leftover.isEmpty()) {
                    logger.info("[InventoryService] 플레이어 ${player.name}에게 다이아몬드 $quantity 개 추가 완료")
                    return true
                } else {
                    // 인벤토리가 가득 차서 일부만 추가된 경우
                    val added = quantity - leftover.values.sumOf { it.amount }
                    logger.warning("[InventoryService] 플레이어 ${player.name}에게 다이아몬드 일부만 추가됨: $added/$quantity")
                    return false
                }
            } else {
                logger.warning("[InventoryService] 지원하지 않는 아이템 ID: $itemId")
                return false
            }
        } catch (e: Exception) {
            logger.severe("[InventoryService] addItems 오류: ${e.message}")
            return false
        }
    }

    /**
     * UUID로 플레이어 찾기
     */
    private fun getPlayerByUUID(uuidStr: String): Player? {
        try {
            val uuid = UUID.fromString(uuidStr)
            return Bukkit.getPlayer(uuid)
        } catch (e: Exception) {
            logger.warning("[InventoryService] 잘못된 UUID 형식: $uuidStr")
            return null
        }
    }

    /**
     * 플레이어 인벤토리에서 특정 아이템 개수 세기
     */
    private fun countItems(player: Player, material: Material): Int {
        var count = 0
        for (item in player.inventory.contents) {
            if (item != null && item.type == material) {
                count += item.amount
            }
        }
        return count
    }

    /**
     * 플레이어 인벤토리에서 특정 아이템 제거
     */
    private suspend fun removeItemsFromInventory(player: Player, material: Material, amount: Int): Boolean {
        if (!hasItems(player.uniqueId.toString(), material.name, amount)) {
            return false
        }

        var remainingToRemove = amount
        val contents = player.inventory.contents

        for (i in contents.indices) {
            val item = contents[i] ?: continue

            if (item.type == material) {
                if (item.amount <= remainingToRemove) {
                    remainingToRemove -= item.amount
                    player.inventory.setItem(i, null)
                } else {
                    item.amount -= remainingToRemove
                    remainingToRemove = 0
                }

                if (remainingToRemove <= 0) {
                    break
                }
            }
        }

        // 업데이트된 인벤토리 저장
        player.updateInventory()
        logger.info("[InventoryService] 플레이어 ${player.name}의 인벤토리에서 ${material.name} ${amount}개 제거 완료")
        return true
    }
}
