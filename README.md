# Apex SMP

A Paper 1.21.11 plugin where every player is an apex predator. You spawn with a random apex and its passive; killing players drops **Kill Tokens**, and consuming 3 unlocks your **ability** (your apex evolution).

## Building

```bash
gradle build
```

The jar lands in `build/libs/ApexSMP-1.0.0.jar`. Drop it in your server's `plugins/` folder. Requires Paper 1.21.11 and Java 21+.

## The Apexes

| Apex | Passive | Ability (unlocked at 3 tokens) |
|---|---|---|
| Lion | Strength I, +10% speed | Blood Frenzy: +20% damage for 10s |
| Wolf | Speed I | Pack Hunt: 5 wolves + all enemies in 30 blocks glow for 20s |
| Rhino | +4 hearts | Charge: 7-block dash, 4.5 hearts + heavy knockback |
| T-Rex | Fire Resistance | Rend: 4-heart slash + 0.5 heart/sec bleed for 10s |
| Polar Bear | +2 hearts, Speed II on snow/ice | Deep Freeze: target trapped in an ice cube for 3s |
| Snake | +20% crouch speed, every 20th hit poisons | Venomous Bite: Poison II for 15s |
| Panther | Speed I | Shadow Dance: 3 teleport slashes of 2.5 hearts, target stunned |
| Hippo | Resistance I, Water Breathing | Riverquake: leap + slam, 4 hearts + huge knockback in 5 blocks |
| Dragon | Strength I, Speed I, Fire Res | Skyfall: 5s of flight, then a 4.5-heart ground slam |

The Dragon is never rolled: a player trades a dragon egg to an admin (`/apex dragon <player>` while they hold the egg).

Stuns freeze movement and block attacks but never block healing. No ability hit exceeds 5.5 hearts.

## Progression

- Kill a player: they drop a Kill Token (also craftable: 8 gold blocks around a diamond block).
- Right-click a token to consume it. 3 consumed = ability unlocked, used with `/ability`.
- `/apex withdraw [n]` converts consumed tokens back into items (dropping below 3 re-locks the ability).
- **Apex Reroll** (craft: 8 netherite ingots around a nether star) rerolls your apex. Expensive on purpose.
- **Apex Trader** (craft: 8 emerald blocks around a diamond block) trades your apex for a random different one.
- Rerolls and trades reset token progress.

## Commands

Players:
- `/ability` - use your unlocked ability
- `/apex info` - your apex, passive, ability, and token progress
- `/apex withdraw [amount]` - withdraw consumed kill tokens

Admins (`apexsmp.admin`, default op):
- `/apex start` - give every online player an Apex Roll Totem (right-click to roll with the animation)
- `/apex panel` - GUI panel: per-player reroll, tokens, lock/unlock, dragon grant, item gifts
- `/apex set <player> <apex>` / `/apex roll <player>`
- `/apex tokens <player> <n>` / `/apex unlock|lock <player>`
- `/apex dragon <player>` - accept a dragon egg trade-in
- `/apex give <player> <token|roller|reroll|trader> [n]`
- `/apex logs [filter] [count]` - query the event log (rolls, abilities, tokens, kills, admin actions); files in `plugins/ApexSMP/logs/`
