package space.mori.chzzk_bot.chzzk

import org.slf4j.Logger
import space.mori.chzzk_bot.models.User
import space.mori.chzzk_bot.services.CommandService
import space.mori.chzzk_bot.services.CounterService
import space.mori.chzzk_bot.services.UserService
import xyz.r2turntrue.chzzk4j.chat.ChatMessage
import xyz.r2turntrue.chzzk4j.chat.ChzzkChat
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter



class MessageHandler(
    private val channel: ChzzkChannel,
    private val logger: Logger,
    private val listener: ChzzkChat
) {
    private val commands = mutableMapOf<String, (msg: ChatMessage, user: User) -> Unit>()

    private val counterPattern = Regex("<counter:([^>]+)>")
    private val personalCounterPattern = Regex("<counter_personal:([^>]+)>")
    private val dailyCounterPattern = Regex("<daily_counter:([^>]+)>")
    private val namePattern = Regex("<name>")
    private val followPattern = Regex("<following>")

    init {
        reloadCommand()
    }

    internal fun reloadCommand() {
        val user = UserService.getUser(channel.channelId)
            ?: throw RuntimeException("User not found. it's bug? ${channel.channelName} - ${channel.channelId}")
        val commands = CommandService.getCommands(user)

        commands.map {
            this.commands.put(it.command.lowercase()) { msg, user ->
                logger.debug("${channel.channelName} - ${it.command} - ${it.content}/${it.failContent}")

                val result = replaceCounters(Pair(it.content, it.failContent), user, msg, listener, msg.profile?.nickname ?: "")
                listener.sendChat(result)
            }
        }
    }

    internal fun handle(msg: ChatMessage, user: User) {
        val commandKey = msg.content.split(' ')[0]

        commands[commandKey.lowercase()]?.let { it(msg, user) }
    }

    private fun replaceCounters(chat: Pair<String, String>, user: User, msg: ChatMessage, listener: ChzzkChat, userName: String): String {
        var result = chat.first
        var isFail = false

        result = counterPattern.replace(result) {
            val name = it.groupValues[1]
            CounterService.updateCounterValue(name, 1, user).toString()
        }

        result = personalCounterPattern.replace(result) {
            val name = it.groupValues[1]
            CounterService.updatePersonalCounterValue(name, msg.userId, 1, user).toString()
        }

        result = dailyCounterPattern.replace(result) {
            val name = it.groupValues[1]
            val dailyCounter = CounterService.getDailyCounterValue(name, msg.userId, user)

            return@replace if(dailyCounter.second)
                CounterService.updateDailyCounterValue(name, msg.userId, 1, user).first.toString()
            else {
                isFail = true
                dailyCounter.first.toString()
            }
        }

        if(isFail && chat.second != "") {
            result = chat.second
            result = dailyCounterPattern.replace(result) {
                val name = it.groupValues[1]
                val dailyCounter = CounterService.getDailyCounterValue(name, msg.userId, user)

                dailyCounter.first.toString()
            }
        }

        result = followPattern.replace(result) {
            val following = getFollowDate(listener.chatId, msg.userId)
            val dateString: String? = following.content.streamingProperty["followDate"]
            val today = LocalDate.now()

            if(dateString == null) {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                // 문자열을 LocalDate 객체로 변환
                val pastDate = LocalDate.parse(dateString, formatter)

                val period = Period.between(pastDate, today)
                period.days.toString()
            } else ""
        }
        if(isFail) {
            return chat.second
        }

        result = namePattern.replace(result, userName)

        return result
    }
}