package prd.peurandel.prdcore.Manager
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

class ConfigManager(private val plugin: Plugin, private val configFileName: String) {

    private lateinit var config: YamlConfiguration
    private val configFile: File = File(plugin.dataFolder, configFileName)

    init {
        loadConfig()
    }

    private fun loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig()
        }

        config = YamlConfiguration.loadConfiguration(configFile)
        // UTF-8 인코딩 설정 (한글 깨짐 방지)
        try {
            config.loadFromString(configFile.readText(StandardCharsets.UTF_8))
        } catch (e: IOException) {
            plugin.logger.warning("config 파일을 UTF-8로 읽는 데 실패했습니다. 기본 로더를 사용합니다.")
            config = YamlConfiguration.loadConfiguration(configFile) // 기본 로더 사용 (인코딩 문제 발생 가능성)
        }
    }

    private fun createDefaultConfig() {
        configFile.parentFile?.mkdirs() // 폴더가 없다면 생성
        try {
            plugin.saveResource(configFileName, false) // 플러그인 리소스에서 복사
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("기본 config 파일을 찾을 수 없습니다: $configFileName")
            // 기본 내용 직접 작성 (fallback) - 필요한 경우
            // configFile.writeText("default: value")
        } catch (e: IOException) {
            plugin.logger.severe("config 파일을 생성하는 데 실패했습니다: $configFileName - ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 설정 값 가져오기 (String)
     * @param path 설정 경로 (config.yml 에서의 경로)
     * @param default 기본값 (설정 파일에 없을 경우 반환)
     * @return 설정 값 (String), 설정이 없으면 기본값
     */
    fun getString(path: String, default: String): String {
        return config.getString(path, default) ?: default // null safe, 엘비스 연산자 활용
    }

    /**
     * 설정 값 가져오기 (Int)
     * @param path 설정 경로
     * @param default 기본값
     * @return 설정 값 (Int), 설정이 없으면 기본값
     */
    fun getInt(path: String, default: Int): Int {
        return config.getInt(path, default)
    }

    /**
     * 설정 값 가져오기 (Boolean)
     * @param path 설정 경로
     * @param default 기본값
     * @return 설정 값 (Boolean), 설정이 없으면 기본값
     */
    fun getBoolean(path: String, default: Boolean): Boolean {
        return config.getBoolean(path, default)
    }

    /**
     * 설정 값 가져오기 (List<String>)
     * @param path 설정 경로
     * @param default 기본값 (빈 리스트)
     * @return 설정 값 (List<String>), 설정이 없으면 기본값 (빈 리스트)
     */
    fun getStringList(path: String, default: List<String> = emptyList()): List<String> {
        return config.getStringList(path) ?: default // null safe, 엘비스 연산자 활용
    }

    // 필요에 따라 다른 타입 (Double, Long, ConfigurationSection 등) 가져오는 메서드 추가 가능

    /**
     * 설정 파일 다시 로드
     */
    fun reloadConfig() {
        loadConfig()
        plugin.logger.info("$configFileName 파일을 다시 로드했습니다.")
    }
}