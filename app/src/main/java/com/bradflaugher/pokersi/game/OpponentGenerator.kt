package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.Match
import com.bradflaugher.pokersi.llm.Persona
import com.bradflaugher.pokersi.llm.Personas
import com.bradflaugher.pokersi.run.Boss
import com.bradflaugher.pokersi.run.OpponentSpec
import com.bradflaugher.pokersi.run.RunState

/** Silly opponents for quick games and run tables, built from [Personas]. */
object OpponentGenerator {
    /**
     * A random opponent from [seed]; the same seed always makes the same
     * opponent. Characters come from the LLM side's [Personas] (voices,
     * bios, quirks); their House Bot style follows the persona's traits.
     */
    fun profile(seed: Long): OpponentProfile = fromPersona(Personas.generate(seed))

    /** The table profile for a persona; also used to rebuild the persona from a profile. */
    fun fromPersona(persona: Persona): OpponentProfile = OpponentProfile(
        name = persona.name,
        avatarSeed = persona.seed,
        style = BotStyle(
            name = persona.name,
            tightness = persona.tightness.toDouble(),
            aggression = persona.aggression.toDouble(),
            bluffing = persona.bluffiness * 0.35,
            chattiness = 0.6,
        ),
        bio = persona.bio,
        catchphrase = persona.catchphrase,
        voice = persona.voice,
    )

    /** [count] opponents with distinct names, from [seed]. */
    fun table(seed: Long, count: Int): List<OpponentProfile> = Personas.table(seed, count).map(::fromPersona)

    /** The boss's face (stable per boss). */
    fun bossAvatarSeed(boss: Boss): Long = boss.id.hashCode().toLong() * 31 + 7

    /**
     * Profiles for a run table, by seat (seat 0, the player, is null). At a boss
     * table seat 1 is the boss itself: its name, its face, its rule as a bio.
     */
    fun forRunTable(state: RunState): List<OpponentProfile?> {
        val boss = state.table.boss
        return listOf<OpponentProfile?>(null) + state.table.opponents.mapIndexed { i, spec ->
            val p = forRunOpponent(spec, state.seed)
            if (i == 0 && boss != null) p.copy(name = boss.name, avatarSeed = bossAvatarSeed(boss), bio = boss.description) else p
        }
    }

    /** A profile for a run table's opponent: its name and style come from the run, the rest from the name. */
    fun forRunOpponent(spec: OpponentSpec, runSeed: Long): OpponentProfile {
        val base = profile(Match.mix(runSeed xor spec.name.hashCode().toLong(), 7))
        return base.copy(name = spec.name, style = spec.botStyle, avatarSeed = spec.name.hashCode().toLong() * 7919L + 13)
    }
}
