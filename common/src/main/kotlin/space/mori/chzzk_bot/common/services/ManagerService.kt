package space.mori.chzzk_bot.common.services

import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.common.models.Manager
import space.mori.chzzk_bot.common.models.Managers
import space.mori.chzzk_bot.common.models.User

object ManagerService {
    fun saveManager(user: User, discordId: Long, name: String): Manager {
        if (user.liveAlertGuild == null)
            throw RuntimeException("${user.username} has no liveAlertGuild")

        return transaction {
            Manager.new {
                this.user = user
                this.discordGuildId = user.liveAlertGuild!!
                this.managerId = discordId
                this.lastUserName = name
            }
        }
    }

    fun updateManager(user: User, discordId: Long, name: String): Manager {
        return transaction {
            if (user.liveAlertGuild == null)
                throw RuntimeException("${user.username} has no liveAlertGuild")

            val manager = getUser(user.liveAlertGuild!!, discordId) ?: throw RuntimeException("$name isn't manager.")

            manager.lastUserName = name

            manager
        }
    }

    fun getUser(guildId: Long, discordId: Long): Manager? {
        return transaction {
            val manager = Manager.find(
                (Managers.discordGuildId eq guildId) and (Managers.managerId eq discordId),
            )
                .with(Manager::user)
                .firstOrNull()
            manager
        }
    }

    fun getAllUsers(guildId: Long): List<Manager> {
        return transaction {
            val result = Manager.find(Managers.discordGuildId eq guildId)
                .with(Manager::user)
                .toList()

            result.forEach { it.load(Manager::user) }

            result
        }
    }

    fun deleteManager(user: User, discordId: Long): Manager {
        if (user.liveAlertGuild == null)
            throw RuntimeException("${user.username} has no liveAlertGuild")
        return deleteManager(user.liveAlertGuild!!, discordId)
    }

    fun deleteManager(guildId: Long, discordId: Long): Manager {
        return transaction {
            val managerRow = Manager.find((Managers.discordGuildId eq guildId) and (Managers.managerId eq discordId)).firstOrNull()

            managerRow ?: throw RuntimeException("Manager not found! $discordId")
            managerRow.delete()

            managerRow
        }
    }
}