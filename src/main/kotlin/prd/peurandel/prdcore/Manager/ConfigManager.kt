package prd.peurandel.prdcore.Manager
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

class ConfigManager(private val plugin: JavaPlugin) {

    private val configMap = mutableMapOf<String, FileConfiguration>()

    fun loadConfig(configName: String): FileConfiguration {
        val configFile = File(plugin.dataFolder, "$configName.yml")
        if (!configFile.exists()) {
            plugin.saveResource("$configName.yml", false) // 플러그인 내부의 기본 설정 복사
        }
        val config = YamlConfiguration.loadConfiguration(configFile)
        configMap[configName] = config
        return config
    }

    fun getConfig(configName: String): FileConfiguration? {
        return configMap[configName]
    }

    fun saveConfig(configName: String) {
        val config = configMap[configName] ?: return
        val configFile = File(plugin.dataFolder, "$configName.yml")
        try {
            config.save(configFile)
        } catch (e: IOException) {
            plugin.logger.severe("$configName.yml 파일을 저장하는 동안 오류가 발생했습니다: ${e.message}")
        }
    }

    fun logWarning(message: String) {
        plugin.logger.warning(message)
    }

    fun logInfo(message: String) {
        plugin.logger.info(message)
    }

    fun logSevere(message: String) {
        plugin.logger.severe(message)
    }
    // 필요에 따라 다른 컨피그 관련 유틸리티 메서드를 추가할 수 있습니다.
}