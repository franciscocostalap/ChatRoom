

sealed class Line private constructor(){
    class Message(val value: String): Line()
    class EnterRoomCommand(val roomName: String): Line()
    object LeaveRoomCommand: Line()
    object ExitCommand: Line()
    class InvalidLine(val reason: String): Line()


    companion object{

        fun parse(line: String): Line{
            if(!line.startsWith("/"))
                return Message(line)

            val parts = line.split(" ")
            return when(parts[0]){
                "/enter" -> parseEnterRoom(parts)
                "/leave" -> parseLeaveRoom(parts)
                "/exit" -> parseExit(parts)
                else -> InvalidLine("Unknown command.")
            }
        }


        private fun parseExit(parts: List<String>): Line =
            if (parts.size != 1)
                InvalidLine("/exit command does not have arguments")
            else ExitCommand


        private fun parseLeaveRoom(parts: List<String>): Line =
            if (parts.size != 1)
                InvalidLine("/leave command does not have arguments")
            else
                LeaveRoomCommand


        private fun parseEnterRoom(parts: List<String>): Line =
            if (parts.size != 2)
                InvalidLine("/enter command requires exactly one argument");
            else
                EnterRoomCommand(parts[1])
    }

}




