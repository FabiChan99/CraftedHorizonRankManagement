package me.fabichan.craftedHorizonRankManagement.util

import net.dv8tion.jda.api.JDA

object JDAProvider {
    var jda: JDA? = null
        private set

    fun initialize(jdaInstance: JDA?) {
        jda = jdaInstance
    }
}