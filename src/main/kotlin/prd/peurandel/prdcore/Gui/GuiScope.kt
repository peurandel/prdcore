package prd.peurandel.prdcore.Gui
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import kotlin.coroutines.CoroutineContext

/**
 * Bukkit GUI 환경을 위한 안전한 코루틴 실행기.
 * 로딩 UI 표시, 비동기 데이터 가져오기, 결과/에러 처리를 자동화합니다.
 */
class GuiScope(
    private val plugin: JavaPlugin,
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private var job: Job? = null

    /**
     * 제네릭 비동기 작업을 실행합니다.
     * @param T 가져올 데이터의 타입
     * @param onLoading 작업 시작 시 메인 스레드에서 실행될 UI 로직
     * @param fetch 백그라운드 스레드에서 실행될 데이터 로딩 함수
     * @param onSuccess 작업 성공 시 메인 스레드에서 실행될 UI 로직
     * @param onError 작업 실패 시 메인 스레드에서 실행될 UI 로직
     */
    fun <T> launch(
        onLoading: () -> Unit,
        fetch: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        job?.cancel() // 이전 작업이 실행 중이면 취소
        job = scope.launch {
            try {
                // 1. 로딩 UI 표시
                Bukkit.getScheduler().runTask(plugin, Runnable { onLoading() })

                // 2. 데이터 가져오기
                val data = fetch()

                // 3. 성공 UI 업데이트
                Bukkit.getScheduler().runTask(plugin, Runnable { onSuccess(data) })
            } catch (e: CancellationException) {
                // 취소는 정상 동작
            } catch (e: Exception) {
                // 4. 에러 UI 업데이트
                Bukkit.getScheduler().runTask(plugin, Runnable { onError(e) })
            }
        }
    }

    /**
     * 로딩 UI 없이 비동기 작업을 실행합니다.
     * @param T 가져올 데이터의 타입
     * @param fetch 백그라운드 스레드에서 실행될 데이터 로딩 함수
     * @param onSuccess 작업 성공 시 메인 스레드에서 실행될 UI 로직
     * @param onError 작업 실패 시 메인 스레드에서 실행될 UI 로직
     */
    fun <T> launch(
        fetch: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (Exception) -> Unit
    ) {
        job?.cancel()
        job = scope.launch {
            try {
                val data = fetch()
                Bukkit.getScheduler().runTask(plugin, Runnable { onSuccess(data) })
            } catch (e: CancellationException) {
                // 취소는 정상 동작
            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(plugin, Runnable { onError(e) })
            }
        }
    }

    fun <T1, T2> launch(
        onLoading: () -> Unit,
        fetch1: suspend () -> T1,
        fetch2: suspend () -> T2,
        onSuccess: (T1, T2) -> Unit,
        onError: (Exception) -> Unit
    ) {
        job?.cancel()
        job = scope.launch {
            try {
                // 1. 로딩 UI 표시 (메인 스레드)
                Bukkit.getScheduler().runTask(plugin, Runnable { onLoading() })

                // 2. 데이터 병렬로 가져오기 (백그라운드 스레드)
                val deferred1 = async { fetch1() }
                val deferred2 = async { fetch2() }
                val result1 = deferred1.await()
                val result2 = deferred2.await()

                // 3. 성공 UI 업데이트 (메인 스레드)
                Bukkit.getScheduler().runTask(plugin, Runnable { onSuccess(result1, result2) })
            } catch (e: CancellationException) {
                // 취소는 정상 동작
            } catch (e: Exception) {
                // 4. 에러 UI 업데이트 (메인 스레드)
                Bukkit.getScheduler().runTask(plugin, Runnable { onError(e) })
            }
        }
    }

    fun <T1, T2, T3> launch(
        onLoading: () -> Unit,
        fetch1: suspend () -> T1,
        fetch2: suspend () -> T2,
        fetch3: suspend () -> T3,
        onSuccess: (T1, T2, T3) -> Unit,
        onError: (Exception) -> Unit
    ) {
        job?.cancel()
        job = scope.launch {
            try {
                // 1. 로딩 UI 표시 (메인 스레드)
                Bukkit.getScheduler().runTask(plugin, Runnable { onLoading() })

                // 2. 데이터 병렬로 가져오기 (백그라운드 스레드)
                val deferred1 = async { fetch1() }
                val deferred2 = async { fetch2() }
                val deferred3 = async { fetch3() }
                val result1 = deferred1.await()
                val result2 = deferred2.await()
                val result3 = deferred3.await()

                // 3. 성공 UI 업데이트 (메인 스레드)
                Bukkit.getScheduler().runTask(plugin, Runnable { onSuccess(result1, result2, result3) })
            } catch (e: CancellationException) {
                // 취소는 정상 동작
            } catch (e: Exception) {
                // 4. 에러 UI 업데이트 (메인 스레드)
                Bukkit.getScheduler().runTask(plugin, Runnable { onError(e) })
            }
        }
    }

    // GUI가 닫힐 때 등 필요시 외부에서 코루틴을 취소하기 위한 함수
    fun cancel() {
        scope.cancel()
    }
}