# ChatRoom

This base implementation uses the following design:

![App Diagram](AppDiagram.png)

## Implementation details

- Each server instance uses a coroutine to listen for new connections and creates a client instance for each.
- Each client instance uses two coroutines that use the same client scope.

A main coroutine that reads and processes control messages from a queue. These control messages can be:

- A text message posted to a room where the client is present.
- A text line sent by the remote connected client.
- An indication that the read stream from the remote connected client ended.
- An indication that the handling of the client should end (e.g. because the server is ending).

A read coroutine that reads lines from the remote client connection and transform these into control messages sent to
the main coroutine.
Most interactions with the client are done by sending messages to the client control queue.

The parsing of the remote client lines (commands or messages) and server side commands are done by the Line class.

#### Nio Coroutines

This project adds a simple api to use AsynchronousSocketChannels with coroutines.

Server Side Functionalities:

- **/shutdown [timeout]** - Clients are not forced to exit. Instead, the server will wait for the clients to
  exit for [timeout] milliseconds.
  After the timeout, the server will exit abruptly.

- **/exit** - Ends the application abruptly, forcing the clients to exit.

Client Side Functionalities:

- **/enter [room name]** - Client enters the specified room or creates it if it does not exist.

- **/leave** - Client leaves the current room.

- **/exit** - Client exits the application.

## Shutdown implementation

```kotlin

fun shutdown(timeout: Duration) {

    if (!serverState.compareAndSet(State.ONLINE, State.ENDING)) { // Only one thread can shutdown the server.
        logger.info { "Could not shutdown server" }
        throw IllegalStateException("Server is not online")
    }

    logger.info("Shutdown started")
    runBlocking {
        try {
            withTimeout(timeout.inWholeMilliseconds) { // Suspending join with timeout
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


```

When the shutdown process is called, it begins by checking if the server state is **Online** and if it is, the server
state
is set to **Ending**. If the server is not **Online** there's no point in starting the shutdown, an **IllegalStateException** is thrown.

The start of this process implies to stop accepting new clients and wait for the termination of the existing ones or for the timeout to pass. For that purpose the job associated to the cycle of acceptance is cancelled and suspends the coroutine until the cancellation is complete.

Therefore, when an **CancellationException** is thrown, is known that the shutdown has started so a warning is sent to
all the clients informing them about it. Before finishing the cancellation process with success, it waits for all the jobs associated
to the clients that are already connected to the server to terminate.

Finally, it shutdowns and sets the server state to **Ended**. On the other hand,
if it times out, ends the application abruptly and forces the clients to exit.
