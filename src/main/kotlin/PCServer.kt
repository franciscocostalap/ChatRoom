import kotlin.time.Duration


interface PCServer {

    fun run()
    fun join()
    fun shutdown(timeout: Duration)
    fun stop()

}