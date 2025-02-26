package prd.peurandel.prdcore.Manager
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException

class ConfigManager(private val plugin: JavaPlugin) {

    private val configFile: File = File(plugin.dataFolder, "config.yml")
    private var config: FileConfiguration = YamlConfiguration.loadConfiguration(configFile)

    init {
        // 초기화 시 config 파일이 존재하지 않으면 생성
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false)
        }
        reloadConfig()
    }

    fun reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    fun saveConfig() {
        try {
            config.save(configFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // 설정 값을 읽는 예제 메소드
    fun getString(path: String, default: String): String {
        return config.getString(path, default) ?: default
    }

    // 설정 값을 쓰는 예제 메소드
    fun setString(path: String, value: String) {
        config.set(path, value)
        saveConfig()
    }
}
