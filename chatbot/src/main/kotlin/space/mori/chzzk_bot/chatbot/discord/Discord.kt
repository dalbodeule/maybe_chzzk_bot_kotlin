package space.mori.chzzk_bot.chatbot.discord

import io.github.cdimascio.dotenv.dotenv
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.utils.IData
import space.mori.chzzk_bot.common.utils.IStreamInfo
import space.mori.chzzk_bot.chatbot.discord.commands.*
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.ManagerService
import java.time.Instant

val dotenv = dotenv {
    ignoreIfMissing = true
}

class Discord: ListenerAdapter() {
    private var guild: Guild? = null
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        lateinit var bot: JDA

        internal fun getChannel(guildId: Long, channelId: Long): TextChannel? = bot.getGuildById(guildId)?.getTextChannelById(channelId)

        fun sendDiscord(user: User, status: IData<IStreamInfo?>) {
            if(status.content == null) return
            if(user.liveAlertMessage != "" && user.liveAlertGuild != null && user.liveAlertChannel != null) {
                val channel = getChannel(user.liveAlertGuild!!, user.liveAlertChannel!!) ?: throw RuntimeException("${user.liveAlertChannel} is not valid.")

                val embed = EmbedBuilder()
                embed.setTitle(status.content!!.liveTitle, "https://chzzk.naver.com/live/${user.token}")
                embed.setDescription("${user.username} 님이 방송을 시작했습니다.")
                embed.setTimestamp(Instant.now())
                embed.setAuthor(user.username, "https://chzzk.naver.com/live/${user.token}", status.content!!.channel.channelImageUrl)
                embed.addField("카테고리", status.content!!.liveCategoryValue, true)
                embed.addField("태그", status.content!!.tags.joinToString(", "), true)
                embed.setImage(status.content!!.liveImageUrl.replace("{type}", "1080"))

                channel.sendMessage(
                    MessageCreateBuilder()
                        .setContent(user.liveAlertMessage)
                        .setEmbeds(embed.build())
                        .build()
                ).queue()
            }
        }
    }

    private val commands = listOf(
        AddCommand,
        AlertCommand,
        PingCommand,
        RegisterCommand,
        RemoveCommand,
        UpdateCommand,
        AddManagerCommand,
        ListManagerCommand,
        RemoveManagerCommand,
    )

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        val handler = commands.find { it.name == event.name }
        logger.debug("Handler: ${handler?.name ?: "undefined"} command")
        handler?.run(event, bot)
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        event.member?.let { ManagerService.deleteManager(event.guild.idLong, it.idLong) }
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        commandUpdate(event.guild)
    }

    private fun commandUpdate(guild: Guild) {
        guild.updateCommands().addCommands(* commands.map { it.command}.toTypedArray())
            .onSuccess {
                logger.info("Command update on guild success!")
            }
            .queue()
    }

    private fun commandUpdate(bot: JDA) {
        bot.updateCommands().addCommands(* commands.map { it.command}.toTypedArray())
            .onSuccess {
                logger.info("Command update bot boot success!")
            }
            .queue()
    }

    fun enable() {
        val thread = Thread {
            try {
                bot = JDABuilder.createDefault(dotenv["DISCORD_TOKEN"])
                    .setActivity(Activity.playing("치지직 보는중"))
                    .addEventListeners(this)
                    .build().awaitReady()

                guild = bot.getGuildById(dotenv["GUILD_ID"])

                commandUpdate(bot)
                bot.guilds.forEach {
                    commandUpdate(it)
                }
            } catch (e: Exception) {
                logger.info("Could not enable Discord!")
                logger.debug(e.stackTraceToString())
            }
        }
        thread.start()
    }

    fun disable() {
        try {
            bot.shutdown()
        } catch(e: Exception) {
            logger.info("Error while shutting down Discord!")
            logger.debug(e.stackTraceToString())
        }
    }
}