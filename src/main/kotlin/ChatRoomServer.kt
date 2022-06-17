import kotlinx.coroutines.*
import mu.KLogger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.LinkedList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ChatRoomServer(private val logger: KLogger, address: InetAddress, port: Int) {


    private val inetSocketAddress = InetSocketAddress(address, port)

    private val mutex = ReentrantLock()

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

    private val clients = LinkedList<ConnectedClient>()

    @Volatile
    private var acceptJob: Job? = null

    /**
     * Parent coroutine scope.
     * Used to launch coroutines.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val rooms = RoomSet()

    private val nextClientID = AtomicInteger(0)

    private val startedShutdown = AtomicBoolean(false)


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
        // Future property
        acceptJob = scope.launch {
            runInternal()
        }

        logger.info { "Server Started" }
    }


    fun removeFromList(elem: ConnectedClient) {
        mutex.withLock {
            clients.remove(elem)
        }
    }

    private suspend fun runInternal() {

        logger.info { serverState.get() }
        while (serverState.get() == State.ONLINE) {
            try {

                if (!startedShutdown.get()) {
                    val clientChannel = serverChannel.acceptSuspend()

                    val clientName = "client-${nextClientID.incrementAndGet()}"
                    logger.info { "New client connected: $clientName" }
                    val client = ConnectedClient(clientName, clientChannel, rooms, ::removeFromList)
                    mutex.withLock { clients.add(client) }
                }

            } catch (e: Exception) {
                logger.warn { "Exception caught '{}', which may happen when the listener is closed, continuing..." }
                // continuing...
            }
            logger.info { "Waiting for clients to end, before ending accept loop" }
        }

        clients.forEach { client ->
            client.exit()
            client.join()
        }
        logger.info { "Accept thread ending" }
        serverState.set(State.ENDED)

    }

    fun join() {
        if (serverState.get() == State.OFFLINE || serverState.get() == State.ENDED) {
            logger.error { "Server is not running " }
            throw Exception("Server is not running")
        }

        while (serverState.get() == State.STARTING || acceptJob == null) Thread.yield()

        runBlocking {
            if (acceptJob?.isActive == true) acceptJob?.join()
        }
    }


    /**
     * Exits the application abruptly
     */
    fun stop() {
        if (!serverState.compareAndSet(State.ONLINE, State.ENDING)) {
            logger.info { "Could not stop server" }
            throw IllegalStateException("Server is not online")
        }

        serverChannel.close()
        channelGroup.shutdownNow()
        serverState.set(State.ENDED)
    }


    /**
     * Starts the shutdown process, does not let more clients join.
     *
     * @param timeout The timeout in seconds
     * If the timeout is exceeded the application ends abruptly.
     */
    fun shutdown(timeout: Long) {
        fun notifyShutdown() {
            clients.forEach {

            }
        }

        if (!serverState.compareAndSet(State.ONLINE, State.ENDING)) {
            logger.info { "Could not shutdown server" }
            throw IllegalStateException("Server is not online")
        }
        logger.info("Shutdown started")

        serverChannel.close()

        notifyShutdown()

        channelGroup.shutdown()
        if (!channelGroup.awaitTermination(timeout, TimeUnit.SECONDS)) {
            channelGroup.shutdownNow()
        }
        serverState.set(State.ENDED)
    }

}