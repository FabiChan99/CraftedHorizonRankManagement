package me.fabichan.craftedHorizonRankManagement

import me.fabichan.craftedHorizonRankManagement.commands.discord.AccountLinkButtonClick
import me.fabichan.craftedHorizonRankManagement.commands.discord.AccountLinkSubmitModalEvent
import me.fabichan.craftedHorizonRankManagement.commands.discord.SendRegisterModal
import me.fabichan.craftedHorizonRankManagement.commands.minecraft.RankSyncCommandExecuter
import me.fabichan.craftedHorizonRankManagement.commands.minecraft.UserJoinSync
import me.fabichan.craftedHorizonRankManagement.util.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import org.bukkit.plugin.java.JavaPlugin
import java.sql.SQLException

class CraftedHorizonRankManagement : JavaPlugin() {
    private lateinit var jda: JDA
    private lateinit var dbclient: DbUtil


    companion object properties {
        var ChatPrefix = "CraftedHorizonCBSystem"
        lateinit var PluginInstance: CraftedHorizonRankManagement
            private set
    }

    init {
        PluginInstance = this
    }

    override fun onEnable() {
        logger.info("Starte CH-Rank Verwaltung")
        saveDefaultConfig()
        getConfig().options().copyDefaults(true)
        
        MessageConfigManager(this)

        val prefix = config.getString("general.chatprefix")
        if (prefix != null) {
            ChatPrefix = prefix
        }
        
        dbclient = DbUtil.getInstance(this)
        try {
            val bottoken: String? = config.getString("bot.bottoken")
            if (bottoken.isNullOrEmpty()) {
                logger.severe("Bot-Token ist nicht gesetzt!")
                server.pluginManager.disablePlugin(this)
                return
            }
            jda = JDABuilder.createDefault(bottoken)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES, GatewayIntent.MESSAGE_CONTENT)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .build()
            jda.awaitReady()
            Thread.sleep(2000)
            JDAProvider.initialize(jda)
            logger.info("Bot wurde erfolgreich gestartet")
            logger.info("Bot-Name: ${jda.selfUser.name}")
        }
        catch (e: Exception) {
            logger.severe(String.format("Bot konnte nicht gestartet werden: %s", e.message))
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
            return
        }
        DbUtil.initDatabase()
        val commandManager = CommandManager()
        commandManager.addCommand(SendRegisterModal())
        // init linkmanager
        LinkManager(this)
        jda.addEventListener(commandManager)
            try {
            jda.addEventListener(
                AccountLinkSubmitModalEvent(this),
                AccountLinkButtonClick(this),
                UserJoinSync(this)
            )
        } catch (ignored: SQLException) {
        }
        val guild = jda.getGuildById(config.getLong("bot.guildid"))
        logger.info("Verbunden mit Guild: ${guild?.name}")
        if (guild == null) {
            logger.severe("Guild-ID ist nicht gesetzt oder der Bot ist nicht auf dem Server!")
            server.pluginManager.disablePlugin(this)
            return
        }
        
        for (command in commandManager.commands) {
            logger.info(java.lang.String.format("Slash-Command %s wird registriert!", command.name))
            command.commandData?.let { guild.upsertCommand(it).queue() }
            logger.info(java.lang.String.format("Slash-Command %s wurde registriert!", command.name))
        }
        
        getCommand("ranksync")?.setExecutor(RankSyncCommandExecuter(this)) 
        
        val ranksyncconfig = CustomConfigManager(this, "ranks.yml")
        RankSyncTask.initialize(this, ranksyncconfig)
        UserUpdateListener(this)

        
        logger.info("CH-Rank Verwaltung wurde erfolgreich gestartet")
    }

    override fun onDisable() {
        logger.info("Stoppe CH-Rank Verwaltung")

        try {
            if (this::jda.isInitialized) {
                try {
                    jda.shutdownNow()
                    logger.info("Bot wurde erfolgreich heruntergefahren")
                } catch (e: Exception) {
                    logger.severe("Fehler beim Herunterfahren des Bots: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            logger.severe("Unerwarteter Fehler beim Herunterfahren des Bots: ${e.message}")
            e.printStackTrace()
        }
        Thread.sleep(2000)

        try {
            if (this::dbclient.isInitialized) {
                DbUtil.closeDataSource()
                logger.info("Datenbankverbindung wurde erfolgreich geschlossen")
            }
        } catch (e: Exception) {
            logger.severe("Fehler beim Schließen der Datenbankverbindung: ${e.message}")
            e.printStackTrace()
        }
    }

}
