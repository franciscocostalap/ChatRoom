import kotlinx.coroutines.*
import mu.KotlinLogging
import nioCoroutines.readLine
import nioCoroutines.writeLine
import sync.MessageQueue
import java.nio.channels.AsynchronousSocketChannel


data class ConnectedClient(
    val name: String,
    private val channel: AsynchronousSocketChannel,
    private val rooms: RoomSet,
    private val onExit: (ConnectedClient) -> Unit
) {


    val logger = KotlinLogging.logger(this.toString())

    private val controlMessageQueue = MessageQueue<ControlMessage>()

    companion object {
        private const val TAKE_MESSAGE_TIMEOUT = 20_000L // 20 seconds
    }

    var currentRoom: Room? = null

    @Volatile
    private var exiting: Boolean = false

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var mainJob: Job? = null

    init {
        mainJob = scope.launch {
            mainLoop()
        }
    }

    fun postRoomMessage(message: String, sender: Room) {
        controlMessageQueue.put(RoomMessage(message, sender))
    }

    fun exit(){
        controlMessageQueue.put(Stop)
    }

    suspend fun join() {
        mainJob?.join()
    }

    private suspend fun mainLoop() {
        writeToRemote("Welcome to the chat server, $name")
        scope.launch {
            remoteReadLoop()
        }
        while (!exiting) {
            try {
                when (val controlMessage = controlMessageQueue.take(TAKE_MESSAGE_TIMEOUT)) {
                    is RoomMessage -> writeToRemote(controlMessage.message)
                    is RemoteLine -> executeCommand(controlMessage.message)
                    is RemoteInputEnded -> clientExit()
                    is Stop -> serverExit()
                }
            } catch (e: TimeoutCancellationException){
                logger.info { "Timeout for $name was reached on trying to get a message, ignored." }
            } catch (e: Exception){
                logger.error { "Unexpected exception while handling message: ${e.message}, ending connection" }
                exiting = true
            }
        }
        logger.info { "Exiting main loop" }
    }

    private suspend fun serverExit() {
        currentRoom?.leave(this)
        onExit(this)
        exiting = true
        writeErrorToRemote("Server is exiting")
    }

    private suspend fun executeCommand(message: String) {
        when (val line = Line.parseClient(message)) {
            is Line.InvalidLine -> writeErrorToRemote(line.reason)
            is Line.Message -> postMessageToRoom(line.value)
            is Line.EnterRoomCommand -> enterRoom(line.roomName)
            is Line.LeaveRoomCommand -> leaveRoom()
            is Line.ExitCommand -> clientExit()
            else -> error("Not expected here")
        }
    }

    private suspend fun clientExit() {
        currentRoom?.leave(this)
        // onExit(this)

        exiting = true
        writeOkToRemote()

        channel.close()
        scope.cancel()


    }

    private suspend fun leaveRoom() {
        if(currentRoom == null){
            writeErrorToRemote("You are not in a room.")

        }else{
            currentRoom!!.leave(this)
            currentRoom = null
            writeOkToRemote()
        }

    }

    private suspend fun enterRoom(roomName: String) {
        currentRoom?.leave(this)
        currentRoom = rooms.getOrCreateRoom(roomName)
        currentRoom?.enter(this)
        writeOkToRemote()
    }

    private suspend fun postMessageToRoom(message: String) {
        if(currentRoom == null){
            writeErrorToRemote("Need to be inside a room to post messages")
        }else{
            currentRoom?.post(this, message)
        }
    }

    private suspend fun remoteReadLoop(){
        try{
            while(!exiting){

                val line = channel.readLine() ?: break
                if(line.isNotBlank()){
                    logger.info { "[${currentRoom?.name ?: "Lobby"}] Received line from $name: $line" }
                    controlMessageQueue.put(RemoteLine(line))
                }
            }
        }catch (e: Exception){
            logger.error { "Unexpected exception while reading from remote: ${e.message}" }
        }finally {
            if(!exiting){
                controlMessageQueue.put(RemoteInputEnded)
            }
        }

        logger.info { "Exiting readLoop" }
    }

    suspend fun writeToRemote(line: String){
        channel.writeLine(line)
    }

    private suspend fun writeErrorToRemote(line: String) = writeToRemote("[Error: $line]")
    private suspend fun writeOkToRemote() = writeToRemote("[OK]")



    private sealed class ControlMessage()

    // A message sent by to a room
    private data class RoomMessage(val message: String, val sender: Room) : ControlMessage()

    // A line sent by the remote client.
    private data class RemoteLine(val message: String) : ControlMessage()

    // The information that the remote client stream has ended, probably because the
    // socket was closed.
    private object RemoteInputEnded : ControlMessage()

    // An instruction to stop handling this remote client
    private object Stop : ControlMessage()

    override fun toString() = name

}