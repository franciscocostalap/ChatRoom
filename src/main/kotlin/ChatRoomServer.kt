
import kotlinx.coroutines.*
import mu.KLogger
import nioCoroutines.acceptSuspend
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

class ChatRoomServer(private val logger: KLogger, address: InetAddress, port: Int) {


    companion object {
        const val OK_MESSAGE_FORMAT = "[OK]"
    }


    private val inetSocketAddress = InetSocketAddress(address, port)

    /**
     * Server possible states
     */
    private enum class State {
        OFFLINE, STARTING, ONLINE, ENDING, ENDED
    }

    /**
     * Server current state.
     */
    private val serverState = AtomicReference(State.OFFLINE)

    /**
     * Server channel group.
     */
    private val channelGroup = AsynchronousChannelGroup.withThreadPool(Executors.newSingleThreadExecutor())

    /**
     * Server Asynchronous Socket Channel
     */
    private val serverChannel = AsynchronousServerSocketChannel.open(channelGroup)

    private val clients = ConcurrentLinkedQueue<ConnectedClient>()

    @Volatile
    private var acceptJob: Job? = null

    /**
     * Parent coroutine scope.
     * Used to launch coroutines.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val rooms = RoomSet()

    private val nextClientID = AtomicInteger(0)


    /**
     * Starts the server and begins listening for connections.
     */
    fun run() {
        logger.trace { "Run called." }
        if (!serverState.compareAndSet(State.OFFLINE, State.STARTING)) {
            logger.info { "Could not start server" }
            throw IllegalStateException("Server is already running")
        }

        serverChannel.bind(inetSocketAddress)
        serverState.set(State.ONLINE)
        acceptJob = scope.launch {
            runInternal()
        }

        logger.info { "Server Started" }
    }

    private fun handleClientSession(clientChannel: AsynchronousSocketChannel) {
        val clientName = "client-${nextClientID.incrementAndGet()}"
        logger.info { "New client connected: $clientName" }
        val client = ConnectedClient(clientName, clientChannel, rooms) {
            clients.remove(it)
        }
        clients.add(client)
    }

    private suspend fun runInternal() {

        while (serverState.get() == State.ONLINE) {
            try {
                val clientChannel = serverChannel.acceptSuspend()

                /**
                 * Avoids race between cancelling the accept suspending point at shutdown
                 * and Accepting a connection
                 */
                if (serverState.get() != State.ENDING)
                    handleClientSession(clientChannel)

            } catch (e: CancellationException) {
                withContext(NonCancellable) {

                    if (serverState.get() == State.ENDING) {
                        logger.info { "Server ended" }
                        clients.forEach { client ->
                            client.writeToRemote("Server is shutting down... Please exit!")
                        }
                        clients.forEach { client ->
                            client.join()
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn { "Exception caught '{}', which may happen when the listener is closed, continuing..." }
                // continuing...
            }
            logger.info { "Waiting for clients to end, before ending accept loop" }
        }


        //logger.info { "Accept thread ending" }
        //serverState.set(State.ENDED)
    }

    override fun join() {
        if (serverState.get() == State.OFFLINE || serverState.get() == State.ENDED) {
            logger.error { "Server is not running " }
            throw IllegalStateException("Server is not running")
        }

        while (serverState.get() == State.STARTING || acceptJob == null) Thread.yield()

        runBlocking {
            if (acceptJob?.isActive == true) acceptJob?.join()
        }
    }

    /**
     * Exits the application abruptly
     */
    override fun stop() {
        if (serverState.get() == State.ENDING) {
            if (!serverState.compareAndSet(State.ENDING, State.ENDED)) {
                logger.info { "Could not stop server" }
                throw IllegalStateException("Server is already ended.")
            }
        } else
            if (!serverState.compareAndSet(State.ONLINE, State.ENDED)) {
                logger.info { "Could not stop server" }
                throw IllegalStateException("Server is not online")
            }

        clients.forEach {
            it.exit()
        }
        channelGroup.shutdownNow()
    }


    /**
     * Starts the shutdown process.
     * This will stop accepting new connections.
     * Waits for all the clients to exit for [timeout].
     * If the timeout is exceeded the application ends abruptly closing all the connections.
     *
     * @param timeout The timeout in seconds
     */
    override fun shutdown(timeout: Duration) {

        if (!serverState.compareAndSet(State.ONLINE, State.ENDING)) {
            logger.info { "Could not shutdown server" }
            throw IllegalStateException("Server is not online")
        }
        logger.info("Shutdown started")
        runBlocking {
            try {
                withTimeout(timeout.inWholeMilliseconds) {
                    acceptJob?.cancelAndJoin()
                    channelGroup.shutdown()
                    serverState.set(State.ENDED)
                }
            } catch (e: TimeoutCancellationException) {
                if (serverChannel.isOpen) {
                    stop()
                    logger.info("Shutdown after timeout")
                    return@runBlocking
                }
            }
        }

    }

}