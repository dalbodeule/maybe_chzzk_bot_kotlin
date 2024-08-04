package space.mori.chzzk_bot.chatbot.chzzk

import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.GetUserEvents
import space.mori.chzzk_bot.common.events.GetUserType
import space.mori.chzzk_bot.common.services.LiveStatusService
import space.mori.chzzk_bot.common.services.UserService
import xyz.r2turntrue.chzzk4j.Chzzk
import xyz.r2turntrue.chzzk4j.ChzzkBuilder
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel

val dotenv = dotenv {
    ignoreIfMissing = true
}

object Connector {
    val chzzk: Chzzk = ChzzkBuilder()
        .withAuthorization(dotenv["NID_AUT"], dotenv["NID_SES"])
        .build()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    fun getChannel(channelId: String): ChzzkChannel? = chzzk.getChannel(channelId)


    init {
        logger.info("chzzk logged: ${chzzk.isLoggedIn} / ${chzzk.loggedUser?.nickname ?: "----"}")
        dispatcher.subscribe(GetUserEvents::class) {
            if (it.type == GetUserType.REQUEST) {
                CoroutineScope(Dispatchers.Default).launch {
                    val channel = getChannel(it.uid ?: "")
                    if(channel == null) dispatcher.post(GetUserEvents(
                        GetUserType.NOTFOUND, null, null, null, null
                    ))
                    else {
                        val user = UserService.getUser(channel.channelId)
                        dispatcher.post(
                            GetUserEvents(
                                GetUserType.RESPONSE,
                                channel.channelId,
                                channel.channelName,
                                LiveStatusService.getLiveStatus(user!!)?.status ?: false,
                                channel.channelImageUrl
                            )
                        )
                    }
                }
            }
        }
    }
}