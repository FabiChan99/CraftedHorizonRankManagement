package me.fabichan.craftedHorizonRankManagement.util

import com.google.gson.Gson
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement
import me.fabichan.craftedHorizonRankManagement.util.RankSyncTask.Companion.invalidateCachesForMember
import me.fabichan.craftedHorizonRankManagement.util.RankSyncTask.Companion.pluginInstance
import me.fabichan.craftedHorizonRankManagement.util.RankSyncTask.Companion.syncRoles
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.exceptions.HierarchyException
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.event.EventBus
import net.luckperms.api.event.user.UserDataRecalculateEvent
import net.luckperms.api.model.group.Group
import net.luckperms.api.model.user.User
import net.luckperms.api.node.matcher.NodeMatcher
import net.luckperms.api.node.types.InheritanceNode
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap


class RankSyncTask {

    companion object {
        lateinit var pluginInstance: CraftedHorizonRankManagement
        private lateinit var rolePermissions: Map<String, String>
        private lateinit var rankConfig: CustomConfigManager
        private var isInited = false
        private var timerDuration: Long = 20 * 60 // Default 60 seconds
        
        private val memberCache = ConcurrentHashMap<Long, Member?>()
        private val roleCache = ConcurrentHashMap<String, Role?>()
        private val permissionCache = ConcurrentHashMap<String, Boolean>()

        fun initialize(plugin: CraftedHorizonRankManagement, config: CustomConfigManager) {
            if (isInited) {
                pluginInstance.logger.warning("RankSyncTask already initialized.")
                return
            }
            pluginInstance = plugin
            rankConfig = config
            rolePermissions = try {
                loadRolePermissions()
            } catch (e: Exception) {
                pluginInstance.logger.severe("Failed to load role permissions: ${e.message}")
                emptyMap()
            }
            timerDuration = 20L * pluginInstance.config.getInt("bot.ranksynctimer", 60) // 20 ticks = 1 second

            if (rolePermissions.isNotEmpty()) {
                reoccurringTask()
            } else {
                pluginInstance.logger.severe("Role permissions are empty. Task will not start.")
            }
            isInited = true
        }
        
        fun invalidateCachesForMember(member: Member) {
            if (memberCache.containsKey(member.idLong)){
                memberCache.remove(member.idLong)
            }
            permissionCache.keys.removeIf { it.startsWith("${member.id}-") }
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
                            val member = memberCache.computeIfAbsent(discordId) {
                                guild.getMemberById(discordId)
                            }

                            if (member != null) {
                                pluginInstance.logger.info("[AutoRankSync] Syncing roles for ${member.id}")

                                Bukkit.getScheduler().runTaskAsynchronously(pluginInstance, Runnable {
                                    try {
                                        syncRoles(member)
                                    } catch (e: Exception) {
                                        pluginInstance.logger.severe("Failed to sync roles for ${member.id}: ${e.message}")
                                        e.printStackTrace()
                                    }
                                })
                            } else {
                                pluginInstance.logger.warning("Member with Discord ID $discordId not found in guild $guildId.")
                            }
                        }
                    } catch (e: Exception) {
                        pluginInstance.logger.severe("An error occurred during the reoccurring task: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }.runTaskTimer(pluginInstance, 200, timerDuration)
        }

        @JvmStatic
        fun syncRoles(member: Member) {
            try {
                val isLinked = LinkManager.isLinked(member.idLong)
                if (!isLinked) {
                    pluginInstance.logger.warning("Member ${member.id} is not linked.")
                    return
                }

                rolePermissions.forEach { (permission, roleId) ->
                    val role = roleCache.computeIfAbsent(roleId) {
                        member.guild.getRoleById(roleId)
                    }

                    if (role == null) {
                        pluginInstance.logger.warning("Role with ID $roleId not found in guild ${member.guild.id}")
                        return@forEach
                    }

                    val cacheKey = "${member.id}-$permission"
                    val hasPermission = permissionCache[cacheKey]

                    if (hasPermission == null) {
                        hasPermission(member, permission) { permissionResult ->
                            permissionCache[cacheKey] = permissionResult
                            handleRoleAssignment(member, role, permissionResult)
                        }
                    } else {
                        handleRoleAssignment(member, role, hasPermission)
                    }
                }
            } catch (e: HierarchyException) {
                pluginInstance.logger.warning("Failed to modify roles for member ${member.id} due to hierarchy issue: ${e.message}")
            } catch (e: Exception) {
                pluginInstance.logger.severe("Unexpected error during role sync for member ${member.id}: ${e.message}")
                e.printStackTrace()
            }
        }
        
        @Suppress("USELESS_IS_CHECK")
        private fun handleRoleAssignment(member: Member, role: Role, hasPermission: Boolean) {
            synchronized(member) {
                try {
                    val shouldAddRole = hasPermission && !member.roles.contains(role)
                    val shouldRemoveRole = !hasPermission && member.roles.contains(role)

                    if (shouldAddRole || shouldRemoveRole) {
                        Bukkit.getScheduler().runTaskAsynchronously(pluginInstance, Runnable {
                            try {
                                if (shouldAddRole) {
                                    member.guild.addRoleToMember(member, role).queue(
                                        { pluginInstance.logger.info("Added role ${role.id} to member ${member.id}") },
                                        { exception -> pluginInstance.logger.warning("Failed to add role ${role.id} to member ${member.id}: ${exception.message}") }
                                    )
                                } else if (shouldRemoveRole) {
                                    member.guild.removeRoleFromMember(member, role).queue(
                                        { pluginInstance.logger.info("Removed role ${role.id} from member ${member.id}") },
                                        { exception -> pluginInstance.logger.warning("Failed to remove role ${role.id} from member ${member.id}: ${exception.message}") }
                                    )
                                }
                            } catch (e: Exception) {
                                pluginInstance.logger.severe("Error while assigning roles to member ${member.id}: ${e.message}")
                                e.printStackTrace()
                            }
                        })
                    }
                } catch (e: Exception) {
                    pluginInstance.logger.severe("Error while checking roles for member ${member.id}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }




        private fun hasPermission(member: Member, permission: String, callback: (Boolean) -> Unit) {
            object : BukkitRunnable() {
                override fun run() {
                    try {
                        val mcId = LinkManager.getMinecraftUuid(member.idLong)
                        if (mcId == null) {
                            pluginInstance.logger.warning("No Minecraft UUID found for member ${member.id}")
                            callback(false)
                            return
                        }

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
                        e.printStackTrace()
                        callback(false)
                    }
                }
            }.runTaskAsynchronously(pluginInstance)
        }

        private fun loadRolePermissions(): Map<String, String> {
            val config: FileConfiguration = rankConfig.config
            val rolePermissions = ConcurrentHashMap<String, String>()
            try {
                config.getConfigurationSection("ranks")?.getKeys(false)?.forEach { rank ->
                    val permission = config.getString("ranks.$rank.permission")
                    val roleId = config.getString("ranks.$rank.roleId")
                    if (permission != null && roleId != null) {
                        rolePermissions[permission] = roleId
                    } else {
                        pluginInstance.logger.warning("Rank $rank has missing permission or roleId in the configuration.")
                    }
                } ?: pluginInstance.logger.warning("No ranks section found in the configuration.")
            } catch (e: Exception) {
                pluginInstance.logger.severe("Failed to load role permissions: ${e.message}")
                e.printStackTrace()
            }
            return rolePermissions
        }
    }
}

class RankApiUtils {

    companion object {
        private val webTeamMember = HashSet<WebApiField>()

        fun initialize() {
            scheduleWebTeamMemberUpdates()
            runKtorServer()
            pluginInstance.logger.info("RankApiUtils initialized.")
        }
        

        private fun runKtorServer() {
            object : BukkitRunnable() {
                override fun run() {
                    runKtorInSubThread()
                }
            }.runTaskAsynchronously(pluginInstance)
        }

        private fun scheduleWebTeamMemberUpdates() {
            object : BukkitRunnable() {
                override fun run() {
                    // Run asynchronously without blocking the main server thread
                    CompletableFuture.runAsync {
                        updateWebTeamMembers()
                    }
                }
            }.runTaskTimer(pluginInstance, 0, 20 * 60 * 5) // Runs every 5 minutes
        }
        

        private fun updateWebTeamMembers() {
            if (!checkLuckPermsAvailability()) return

            val allowedRoles = listOf("inhaber", "teamleitung", "admin", "moderator", "supporter", "builder", "helfer")
            val lpProvider = pluginInstance.server.servicesManager.getRegistration(net.luckperms.api.LuckPerms::class.java)?.provider
                ?: run {
                    pluginInstance.logger.warning("LuckPerms provider is not available.")
                    return
                }
            
            webTeamMember.clear()
            lpProvider.groupManager.loadAllGroups().join()
            for (role in allowedRoles) {
                val group = lpProvider.groupManager.getGroup(role)
                if (group != null) {
                    val users = getUsersInGroup(role)
                    for (user in users) {
                        addUserToWebTeamMembers(user, group)
                    }
                }
            }
        }

        private fun getUsersInGroup(groupName: String): List<User> {
            val api = LuckPermsProvider.get()
            val group: Group? = api.groupManager.getGroup(groupName)
            requireNotNull(group) { "Group $groupName not found" }
            val userManager = api.userManager
            val users: MutableList<User> = ArrayList()
            for (uuid in userManager.searchAll<InheritanceNode>(
                NodeMatcher.key<InheritanceNode>(
                    InheritanceNode.builder(
                        group
                    ).build()
                )
            ).join().keys) {
                val user =
                    if (userManager.isLoaded(uuid)) userManager.getUser(uuid) else userManager.loadUser(uuid).join()
                checkNotNull(user) { "Could not load data of $uuid" }
                users.add(user)
            }
            
            
            return users
        }

        private fun checkLuckPermsAvailability(): Boolean {
            return pluginInstance.server.servicesManager.getRegistration(net.luckperms.api.LuckPerms::class.java) != null
        }

        private fun addUserToWebTeamMembers(user: net.luckperms.api.model.user.User, group: net.luckperms.api.model.group.Group) {
            val playerName = user.username
            val mcuuid = user.uniqueId
            val rankWeight = group.weight.orElse(0)
            val rankName = group.name ?: "Spieler"

            playerName?.let {
                // if already UUID in the list, don't add again
                if (webTeamMember.any { it.mcuuid == mcuuid.toString() }) return@let
                webTeamMember.add(WebApiField(it, mcuuid.toString(), rankWeight, rankName))
            }
        }

        private fun runKtorInSubThread() {
            val server = embeddedServer(Netty, port = 4001) {
                routing {
                    get("/team") {
                        try {
                            val sendString = withContext(Dispatchers.IO) {
                                val prettyJson = Json { prettyPrint = true }
                                prettyJson.encodeToString(webTeamMember)
                            }
                            call.respondText(sendString)
                        } catch (e: Exception) {
                            pluginInstance.logger.severe("Error in Ktor server: ${e.message}")
                            call.respondText("Error generating team data: ${e.message}")
                        }
                    }
                }
            }
            server.start(wait = true)
        }
    }
}


@Serializable
data class WebApiField(val playerName: String, val mcuuid: String, val rankWeight: Int, val rankName: String)

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
                val jda = JDAProvider.jda
                if (jda == null) {
                    plugin.logger.severe("JDA ist nicht initialisiert!")
                    return
                }

                val uuid = event.user.uniqueId
                val discordId = LinkManager.getDiscordId(uuid) ?: return
                val guild = jda.getGuildById(plugin.config.getString("bot.guildid") ?: return)
                val member = guild?.getMemberById(discordId) ?: return
                
                invalidateCachesForMember(member)
                
                syncRoles(member)
            }
        }.runTaskAsynchronously(plugin)
    }
}
