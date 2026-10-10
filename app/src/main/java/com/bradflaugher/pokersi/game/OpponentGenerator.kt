package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.Match
import com.bradflaugher.pokersi.run.Boss
import com.bradflaugher.pokersi.run.OpponentSpec
import com.bradflaugher.pokersi.run.RunState
import kotlin.random.Random

/**
 * A small, local generator of silly opponents for quick games and run tables.
 * Deliberately simple and replaceable: the LLM side has a richer persona
 * generator that can take over by producing [OpponentProfile]s the same way.
 */
object OpponentGenerator {
    private val FIRST = listOf(
        "Byte-Sized", "Lucky", "Captain", "Lady", "Sir", "Big", "Lil", "Doctor", "Professor", "Aunt",
        "Turbo", "Mega", "Sneaky", "Grandpa", "Madame", "Disco", "Cosmic", "Señor", "Baron", "Pixel",
    )
    private val LAST = listOf(
        "Bob", "Lou", "Deb", "Chip", "Ace", "Dot", "Gus", "Mo", "Kit", "Pip", "Ray", "Vic", "Zed", "Fifi",
        "Bluff", "Rex", "Nell", "Otto", "Suzy", "Max",
    )
    private val BIOS = listOf(
        "Learned poker from a fortune cookie.",
        "Retired slot machine. Still goes ding.",
        "Counts cards. Mostly up to seven.",
        "Has never once folded a pair. Proudly.",
        "Thinks every hand is a royal flush.",
        "Runs on 3 AA batteries and spite.",
        "Bluffs with the confidence of a toaster.",
        "Studied game theory for a whole weekend.",
        "Swears the deck is haunted.",
        "Collects chips. Also potato chips.",
        "Plays every hand like it's the last.",
        "Was a calculator in a past life.",
        "Brought snacks. Will not share.",
        "Reads tells. Mostly wrong ones.",
        "Has a lucky sock. It's not lucky.",
        "Moonlights as a karaoke robot.",
    )
    private val CATCHPHRASES = listOf(
        "Beep boop, pay up.", "I've got a feeling.", "Read 'em and weep!", "All in on vibes.",
        "Chips go brrr.", "Is this Go Fish?", "Trust the process.", "Fold? Never heard of it.",
        "Let's dance, meatbag.", "My circuits say raise.", "Too easy.", "Shuffle up and deal!",
        "I'm just here for the snacks.", "Not my first rodeo.", "Bingo! Wait, wrong game.",
    )

    /** A random opponent from [seed]; the same seed always makes the same opponent. */
    fun profile(seed: Long): OpponentProfile {
        val r = Random(seed)
        val name = "${FIRST[r.nextInt(FIRST.size)]} ${LAST[r.nextInt(LAST.size)]}"
        return OpponentProfile(
            name = name,
            avatarSeed = r.nextLong(),
            style = BotStyle.ALL[r.nextInt(BotStyle.ALL.size)],
            bio = BIOS[r.nextInt(BIOS.size)],
            catchphrase = CATCHPHRASES[r.nextInt(CATCHPHRASES.size)],
        )
    }

    /** [count] opponents with distinct names, from [seed]. */
    fun table(seed: Long, count: Int): List<OpponentProfile> {
        val out = mutableListOf<OpponentProfile>()
        var salt = 0
        while (out.size < count) {
            val p = profile(Match.mix(seed, salt++))
            if (out.none { it.name == p.name }) out += p
        }
        return out
    }

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
