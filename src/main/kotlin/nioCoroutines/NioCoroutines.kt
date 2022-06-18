package nioCoroutines

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val decoder = Charsets.UTF_8.newDecoder()

/**
 * Line Separator.
 */
private val ls = System.lineSeparator()

private object AcceptHandler: CompletionHandler<AsynchronousSocketChannel, Continuation<AsynchronousSocketChannel>>{

    override fun completed(result: AsynchronousSocketChannel, attachment: Continuation<AsynchronousSocketChannel>){
        attachment.resume(result)
    }

    override fun failed(exc: Throwable, attachment: Continuation<AsynchronousSocketChannel>){
        attachment.resumeWithException(exc)
    }

}

private object ReadWriteHandler: CompletionHandler<Int, Continuation<Int>>{

    override fun completed(result: Int, attachment: Continuation<Int>){
        attachment.resume(result)
    }

    override fun failed(exc: Throwable, attachment: Continuation<Int>){
        attachment.resumeWithException(exc)
    }

}

/**
 * Accepts a new connection as a new [AsynchronousSocketChannel] suspending the current coroutine.
 */
suspend fun AsynchronousServerSocketChannel.acceptSuspend(): AsynchronousSocketChannel{
    return suspendCancellableCoroutine { cont ->
        accept(cont, AcceptHandler)
    }
}

/**
 * Reads data from the channel as a [ByteBuffer] suspending the current coroutine.
 */
suspend fun AsynchronousSocketChannel.readTo(buffer: ByteBuffer): Int {
    return suspendCancellableCoroutine { cont->

        cont.invokeOnCancellation {
            close()
        }

        read(buffer, cont, ReadWriteHandler)
    }
}

/**
 * Reads data from the channel as a [ByteBuffer] suspending the current coroutine.
 */
suspend fun AsynchronousSocketChannel.writeFrom(buffer: ByteBuffer): Int {
    return suspendCancellableCoroutine { cont->

        cont.invokeOnCancellation {
            close()
        }

        write(buffer, cont, ReadWriteHandler)
    }
}

/**
 * TODO
 */
suspend fun AsynchronousSocketChannel.readLine(): String?{
    val buffer = ByteBuffer.allocate(1024)
    val bytesRead = readTo(buffer)
    if(bytesRead == 0) return null
    buffer.flip()
    return decoder.decode(buffer).toString()
}

/**
 * TODO
 */
suspend fun AsynchronousSocketChannel.writeLine(line: String){
    val buffer = ByteBuffer.wrap("$line$ls".toByteArray())
    writeFrom(buffer)
    buffer.clear()
}















