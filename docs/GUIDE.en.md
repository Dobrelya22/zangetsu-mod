# Zangetsu guide

[Русская версия](GUIDE.md)

From your first night to the Final Form. This guide spoils the cutscenes, so if you want to discover them yourself, read only the first two sections.

## 1. Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.16 or newer for Minecraft **1.21.1**. You need Java 21.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=1.21.1) for 1.21.1.
3. Put `zangetsu-*.jar` and Fabric API into `.minecraft/mods`.
4. Launch the game with the Fabric profile. The mod is required on both client and server.

The game language is picked up automatically: English and Russian are included.

## 2. Your first night: getting the sword

There is nothing to find or craft. **Just go to sleep in a bed.** You will have a dream: a crimson sky and a giant Yhwach above you. Hear him out, and Zangetsu will plunge into the ground in front of you.

- **Take the sword: right-click it.** Your hearts are restored and you permanently gain **+10 health** (5 hearts). You wake up in the morning with the sword in your hand.
- **Refuse: right-click your own bed behind you.** This is permanent: the dream never returns and you will never have the sword.

Nothing can hurt you in the dream, so feel free to look around.

## 3. Your bond with the eye

An eye lives on the blade, and all of the sword's power depends on your bond with it.

- **Kill evil mobs.** The tougher the mob, the more bond you gain; bosses give +60 at once.
- **Spare the good ones.** Killing animals lowers your bond, and killing villagers and golems lowers it more.
- **Bond below zero?** The eye gets angry and will not help you at all. Kill evil mobs until it forgives you.

**The eye only opens at night or in the dark.** In daylight the sword is simply very strong and its abilities sleep. Want to try out a new level? Go down into a cave.

## 4. Levels

| Level | Bond needed | What it unlocks |
|---|---|---|
| I "Awakening" | 0 | The sword slices incoming arrows and deflects fireballs and tridents |
| II "Trust" | 50 | Parrying, severing limbs, the reaction check and executions |
| III "Unity" | 150 | The sword strikes the nearest evil mobs on its own |
| IV "Hollow" | 350 | Hollow Form (the `H` key) and Getsuga Tensho |
| V "Final Form" | 700 | Horned mask, fire, cloak and chains, a bigger Getsuga, long-range executions and the great reaction zone |

Your level and bond are shown in the top left corner. Damage grows with every level.

## 5. Combat

**Attacks.** Press LMB: there are eight moves that flow into a combo. A press during an attack is remembered and the next move starts on its own, so don't spam, get into the rhythm instead.

**Limbs (from level II, while the eye is open).** The sword parries mob attacks and strikes back: zombies, piglins and skeletons lose the left arm first, then the right. Without arms a mob cannot attack and drops its weapon. Endermen get their knees cut: with one gone they can no longer teleport, with both gone they cannot move.

**Reaction check.** When your enemy has one hit left or there is nothing left to cut off, it freezes and a ring with a needle appears in the middle of the screen:

- Click **LMB** while the needle is inside the zone to execute the enemy. Pieces fly apart in full 3D, and the move is chosen at random.
- Miss or click too late and the enemy recovers, so you have to finish it with a normal hit.
- **The zone varies:** small without the mask, medium in Hollow Form.
- **The Final Form adds a white zone.** Hit it for a freeze frame, "YOU'RE TOO SLOW", a dash behind the enemy and the most brutal execution. Hit the red zone for a quick execution with a dash of up to 18 blocks.

## 6. Hollow Form

From level IV, the **`H`** key puts on the mask. Strength, speed, defence and regeneration go up, and the abilities work even in daylight. **Right-click with the sword** to fire **Getsuga Tensho**: a huge wave that cuts down evil mobs in its path.

**The gauge above your hearts** shows how much form time you have left. The total is 2 minutes:

- It refills with every new in-game day.
- **It also refills with every monster you kill:** 5 seconds for a zombie, more for tougher mobs, from 3 to 30 seconds. Half as much while in the form.
- Press `H` at any time to take the mask off; the rest is kept.
- The mask cracks at 30, 15 and 5 seconds. When the gauge runs out the mask shatters and you are exhausted for a short while.

At level V, putting on the mask plays a long rite. You are invulnerable during it and the gauge is not spent.

## 7. If you get killed

**With a real totem in hand,** the totem goes first.

**Not in the Final Form: the sword saves you at the cost of itself.** Time stops, Yhwach appears and shatters the blade. You live, but you have no sword. Go to sleep and you will find yourself on the **field of swords**:

- The true one has its **eye on the inner side**: face the eye and the edge is on your right.
- The fakes have the eye on the outside and the edge on the left. A fake crumbles in your hand, but the dream goes on.
- Can't find it within a minute? The true sword starts winking and ringing on its own.

Find it and you wake up with the sword and all your progress.

**In the Final Form: the Hollow takes over your body.** You fall to your knees and drive the sword into the ground, a hole opens through your chest, and from then on you can only watch. The Hollow deals with a crowd of monsters on its own. After the scene you are alive with 5 hearts and your sword is intact, but **the mask is broken for one in-game day**: no form, and kills do not refill the gauge.

The sword does not save you from the void or from commands.

## 8. FAQ

**The sword does nothing.** It's daytime and the eye is closed. Wait for night or go somewhere dark. If the eye is squinting, it's angry: kill a few evil mobs.

**I can't drop the sword or put it in a chest.** That's by design: it is bound to you. It doesn't drop on death, doesn't break and doesn't burn in lava.

**My sword is gone.** Go to sleep: you will dream of hundreds of identical swords. Take any of them and you wake up with it and all your progress.

**I refused the sword and changed my mind.** In normal play that's permanent. With cheats, `/zangetsu awaken` will help.

**Can I rebind the keys?** Yes: Options → Controls → "Zangetsu".

## 9. Testing commands

These require cheats.

| Command | What it does |
|---|---|
| `/zangetsu awaken` | Start the first dream right away and lift a refusal |
| `/zangetsu give` | Get the sword without the scene |
| `/zangetsu level 1..5` | Set your level |
| `/zangetsu bond <number>` | Set your bond |
| `/zangetsu hollow` | Refill the form gauge |
| `/zangetsu dream` | Go to the dream of swords |
| `/zangetsu break` | Show Yhwach breaking the sword |
| `/zangetsu rampage` | Show the Hollow taking over (requires level V) |
| `/zangetsu reset` | Reset everything |
