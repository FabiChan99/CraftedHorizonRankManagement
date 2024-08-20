package me.fabichan.craftedHorizonRankManagement.util

import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement
import me.fabichan.craftedHorizonRankManagement.util.JDAProvider.jda
import me.fabichan.craftedHorizonRankManagement.util.RankSyncTask.Companion.syncRoles
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.exceptions.HierarchyException
import net.luckperms.api.LuckPerms
import net.luckperms.api.event.EventBus
import net.luckperms.api.event.user.UserDataRecalculateEvent
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RankSyncTask {

    companion object {
        private lateinit var pluginInstance: CraftedHorizonRankManagement
        private lateinit var rolePermissions: Map<String, String>
        private lateinit var rankConfig: CustomConfigManager
        private var timerDuration: Long = 20 * 60 // Default 60 seconds

        fun initialize(plugin: CraftedHorizonRankManagement, config: CustomConfigManager) {
            pluginInstance = plugin
            rankConfig = config
            rolePermissions = loadRolePermissions()
            timerDuration = 20L * pluginInstance.config.getInt("bot.ranksynctimer", 60) // 20 ticks = 1 second
            reoccurringTask()
        }

        private fun reoccurringTask() {
            object : BukkitRunnable() {
                override fun run() {
                    try {
                        val linkedDiscordIds = LinkManager.getAllDiscordsThatAreLinkedAndOnServer()
                        if (linkedDiscordIds.isNullOrEmpty()) {
                            pluginInstance.logger.warning("No linked Discord IDs found or none are online.")
                            return
                        }

                        val jda = JDAProvider.jda ?: run {
                            pluginInstance.logger.severe("JDA not initialized")
                            return
                        }

                        val guildId = pluginInstance.config.getString("bot.guildid") ?: run {
                            pluginInstance.logger.severe("Guild ID not set in config")
                            return
                        }

                        val guild = jda.getGuildById(guildId) ?: run {
                            pluginInstance.logger.severe("Guild not found for ID: $guildId")
                            return
                        }

                        linkedDiscordIds.forEach { discordId ->
                            guild.getMemberById(discordId)?.let { member ->
                                pluginInstance.logger.info("[AutoRankSync] Syncing roles for ${member.id}")
                                
                                Bukkit.getScheduler().runTaskAsynchronously(pluginInstance, Runnable {
                                    try {
                                        syncRoles(member)
                                    } catch (e: Exception) {
                                        pluginInstance.logger.severe("Failed to sync roles for ${member.id}: ${e.message}")
                                        e.printStackTrace()
                                    }
                                })
                            } ?: pluginInstance.logger.warning("Member with Discord ID $discordId not found in guild $guildId.")
                        }
                    } catch (e: Exception) {
                        pluginInstance.logger.severe("An error occurred during the reoccurring task: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }.runTaskTimer(pluginInstance, 0, timerDuration)
        }


        @JvmStatic
        fun syncRoles(member: Member) {
            try {
                val isLinked = LinkManager.isLinked(member.idLong)
                if (!isLinked) {
                    return
                }
                rolePermissions.forEach { (permission, roleId) ->
                    val role = member.guild.getRoleById(roleId)
                    if (role == null) {
                        pluginInstance.logger.warning("Role with ID $roleId not found in guild ${member.guild.id}")
                        return@forEach
                    }

                    hasPermission(member, permission) { hasPermission ->
                        if (hasPermission) {
                            if (!member.roles.contains(role)) {
                                member.guild.addRoleToMember(member, role).queue(
                                    { pluginInstance.logger.info("Added role ${role.id} to member ${member.id}") },
                                    { exception -> pluginInstance.logger.warning("Failed to add role ${role.id} to member ${member.id}: ${exception.message}") }
                                )
                            }
                        } else {
                            if (member.roles.contains(role)) {
                                member.guild.removeRoleFromMember(member, role).queue(
                                    { pluginInstance.logger.info("Removed role ${role.id} from member ${member.id}") },
                                    { exception -> pluginInstance.logger.warning("Failed to remove role ${role.id} from member ${member.id}: ${exception.message}") }
                                )
                            }
                        }
                    }
                }
            } catch (e: HierarchyException) {
                pluginInstance.logger.warning("Failed to modify roles for member ${member.id}: ${e.message}")
            } catch (e: Exception) {
                pluginInstance.logger.severe("Unexpected error during role sync for member ${member.id}: ${e.message}")
            }
        }

        private fun hasPermission(member: Member, permission: String, callback: (Boolean) -> Unit) {
            object : BukkitRunnable() {
                override fun run() {
                    try {
                        val mcId = LinkManager.getMinecraftUuid(member.idLong)
                        val lpProvider = pluginInstance.server.servicesManager.getRegistration(net.luckperms.api.LuckPerms::class.java)?.provider
                        val userManager = lpProvider?.userManager ?: run {
                            pluginInstance.logger.warning("LuckPerms user manager not available.")
                            callback(false)
                            return
                        }

                        var user = userManager.getUser(mcId)

                        if (user == null) {
                            val futureUser = userManager.loadUser(mcId)
                            user = futureUser.join()
                        }

                        if (user == null) {
                            pluginInstance.logger.warning("User with UUID $mcId not found in LuckPerms.")
                            callback(false)
                            return
                        }

                        val hasPermission = user.cachedData.permissionData.checkPermission(permission).asBoolean()

                        object : BukkitRunnable() {
                            override fun run() {
                                callback(hasPermission)
                            }
                        }.runTask(pluginInstance)
                    } catch (e: Exception) {
                        pluginInstance.logger.severe("Error checking permission for member ${member.id}: ${e.message}")
                        callback(false)
                    }
                }
            }.runTaskAsynchronously(pluginInstance)
        }

        private fun loadRolePermissions(): Map<String, String> {
            val config: FileConfiguration = rankConfig.config
            val rolePermissions = ConcurrentHashMap<String, String>()
            config.getConfigurationSection("ranks")?.getKeys(false)?.forEach { rank ->
                val permission = config.getString("ranks.$rank.permission")
                val roleId = config.getString("ranks.$rank.roleId")
                if (permission != null && roleId != null) {
                    rolePermissions[permission] = roleId
                } else {
                    pluginInstance.logger.warning("Rank $rank has missing permission or roleId in the configuration.")
                }
            } ?: pluginInstance.logger.warning("No ranks section found in the configuration.")
            return rolePermissions
        }
    }
}

class UserUpdateListener(private val plugin: CraftedHorizonRankManagement) : Listener {

    init {
        registerEvents()
    }

    private fun registerEvents() {
        val luckPerms = plugin.server.servicesManager.load(LuckPerms::class.java)
        val eventBus: EventBus = luckPerms?.eventBus ?: run {
            plugin.logger.severe("LuckPerms event bus not available.")
            return
        }
        
        eventBus.subscribe(plugin, UserDataRecalculateEvent::class.java, this::onUserDataRecalculate)
    }
    
    private fun onUserDataRecalculate(event: UserDataRecalculateEvent) {
        performActionOnUserUpdate(event)
    }

    private fun performActionOnUserUpdate(event: UserDataRecalculateEvent) {
        object : BukkitRunnable() {
            override fun run() {
                if (jda == null) {
                    plugin.logger.severe("JDA ist nicht initialisiert!")
                    return
                }
                val uuid = event.user.uniqueId
                val discordId = LinkManager.getDiscordId(uuid) ?: return
                val member = jda!!.getGuildById(
                    Objects.requireNonNull<String>(
                        plugin.config.getString("bot.guildid")
                    )
                )?.getMemberById(discordId)
                    ?: return
                syncRoles(member)
            }
        }.runTaskAsynchronously(plugin)
    }
}