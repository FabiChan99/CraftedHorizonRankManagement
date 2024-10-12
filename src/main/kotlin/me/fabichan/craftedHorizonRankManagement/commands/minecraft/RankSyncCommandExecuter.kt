package me.fabichan.craftedHorizonRankManagement.commands.minecraft

import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement
import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement.properties.ChatPrefix
import me.fabichan.craftedHorizonRankManagement.util.LinkManager
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RankSyncCommandExecuter(private val plugin: CraftedHorizonRankManagement): CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            handleRankSync(sender, args)
        } else {
            sender.sendMessage("${ChatPrefix}${ChatColor.RED}Commandusage: ${ChatColor.GOLD}/ranksync")
        }
        return true
    }

    private fun handleRankSync(sender: CommandSender, args: Array<out String>?) {
        val player = sender as Player
        val pending = LinkManager.isPending(player.uniqueId)
        if (pending) {
            val lcodeforPlayer = LinkManager.getPendingLinkCode(player.uniqueId)
            val expiresAt = LinkManager.getPendingExpirationDate(lcodeforPlayer)
            player.sendMessage("${ChatPrefix}${ChatColor.RED}Es steht bereits eine Verlinkung aus! Dein Link-Code: ${ChatColor.AQUA}$lcodeforPlayer ${ChatColor.RED}! Teile diesen Code mit niemandem! Gebe diesen Code in den vorgesehenen Link-Kanal ein! Dieser Code ist noch gültig bis: ${ChatColor.GOLD}$expiresAt")
            return
        }
        val isLinked = LinkManager.isLinked(player.uniqueId)
        if (isLinked) {
            player.sendMessage("${ChatPrefix}${ChatColor.RED}Dein Account ist bereits verlinkt!")
            return
        }
        val linkCode = LinkManager.generateLinkCode(player.uniqueId)
        val expirationDate = LinkManager.getPendingExpirationDate(linkCode)
        player.sendMessage("${ChatPrefix}${ChatColor.GREEN}Dein Link-Code: ${ChatColor.AQUA}$linkCode ${ChatColor.GREEN}! Teile diesen Code mit niemandem! Gebe diesen Code in den vorgesehenen Link-Kanal ein! Dieser Code ist noch gültig bis: ${ChatColor.GOLD}$expirationDate")
        return
    }
}
