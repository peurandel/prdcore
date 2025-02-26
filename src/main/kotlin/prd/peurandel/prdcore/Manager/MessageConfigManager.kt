package prd.peurandel.prdcore.Manager
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

class MessageConfigManager(private val plugin: Plugin, private val configFileName: String) { // 클래스 이름 변경: ConfigManager -> MessageConfigManager

    private lateinit var config: YamlConfiguration
    private val configFile: File = File(plugin.dataFolder, configFileName)

    // ... (init, loadConfig, createDefaultConfig 함수는 이전 ConfigManager 와 동일) ...

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
     * 메시지 가져오기 (플레이스홀더 치환 및 컬러 코드 적용)
     * @param key 메시지 키 (messages.yml 에서의 경로)
     * @param placeholders 플레이스홀더 (Pair<플레이스홀더 이름, 값>...)
     * @return 최종 메시지 (컬러 코드 적용 완료)
     */
    fun getMessage(key: String, vararg placeholders: Pair<String, String>): String { // 함수 이름 변경 (의미 명확하게)
        var message = config.getString(key, "&c[메시지 오류] $key") ?: "&c[메시지 오류] $key" // 메시지 키가 없을 경우 기본 메시지
        val prefix = config.getString("prefix", "") ?: "" // prefix 가져오기, 없으면 빈 문자열
        message = prefix + message // 접두사 추가

        for ((placeholder, value) in placeholders) {
            message = message.replace("{$placeholder}", value) // 플레이스홀더 치환
        }

        return colorize(message) // 컬러 코드 적용 함수 (아래에 정의)
    }

    // 컬러 코드 적용 함수 (Bukkit API 활용) - 이전과 동일
    private fun colorize(message: String): String {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message)
    }

    /**
     * 설정 파일 다시 로드
     */
    fun reloadConfig() { // 함수 이름 유지 (reloadConfig)
        loadConfig()
        plugin.logger.info("$configFileName 파일을 다시 로드했습니다.")
    }
}