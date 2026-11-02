# Other: Villages

(Called Village Rework until 0.18.5. The mod id stays `villagerework`, so items and saved villages carry over.)

A Fabric mod for Minecraft 26.3 that replaces villager trading with village-owned
profession stations that players help grow.

Current state: **0.18.4**. Everything planned for now is in: villages, stations and perks, take-home
stations with tier looks, the wandering trader, the bell screen, and prosperity with moods and party days.
See "Version history" and "Full test checklist" near the end.

## Setup

1. Install **JDK 25** (for example Eclipse Temurin 25).
2. Install **IntelliJ IDEA** (Community is fine) and the **Minecraft Development** plugin.
3. Open this folder in IntelliJ (File → Open → select the folder).
   IntelliJ detects the Gradle project and downloads Minecraft, Fabric and their
   libraries. The first import takes a few minutes.
4. In Project Structure, set the project SDK to JDK 25.
5. Optional: run the Gradle task `genSources` to get readable Minecraft source
   (useful for looking up how villagers work).

## Running and building

| Task | What it does |
|---|---|
| `runClient` | Starts a Minecraft client with the mod loaded |
| `runServer` | Starts a local dedicated server with the mod loaded |
| `build` | Builds the mod jar into `build/libs/` |

Run them from the Gradle panel in IntelliJ, or in a terminal with `./gradlew runClient`
(`gradlew.bat runClient` on Windows).

### Putting it on the server

Copy `build/libs/villagerework-<version>.jar` (not the `-sources` jar) into the server's
`mods` folder, next to Fabric API 0.163.0+26.3 or newer. Every player needs the same jar
(and Fabric API) in their own `mods` folder too, for the station screens.

## Config

`config/villagerework.json` is created on first start. Every number the design relies
on is there: village radius and founding requirements, worker caps, daily input caps,
output multipliers, progress per level, emerald diminishing returns, prosperity and moods.

Edit it, then run `/vr config reload` in game. Invalid values are clamped, and
per-level lists are padded or trimmed to match `maxLevel`.

## Debug commands (operators only)

| Command | Purpose |
|---|---|
| `/vr config reload` | Re-read the config file |
| `/vr config show` | Print the main config values |
| `/vr emeralds [count]` | Preview what `count` emeralds in one day give, split over 1–6 stations |
| `/vr scan [radius]` | List vanilla village points of interest (beds, bells, workstations) and villagers around you, and whether vanilla counts the spot as a village |
| `/vr village list` | Every village in this dimension, nearest first |
| `/vr village info` | Details of the nearest village: bell, origin, state, members, beds |
| `/vr village remove` | Delete the nearest village's data (the bell block stays). Natural villages are rediscovered on the next update; player-founded ones have to be founded again from the bell |
| `/vr village sync` | Run the village update now instead of waiting for the next one |
| `/vr station list` | Professions of the nearest village: level, progress, station position |
| `/vr station setlevel <profession> <level>` | Set a profession's level in the nearest village, e.g. `librarian 2` |
| `/vr station fill <profession>` | Fill a profession's progress bar in the nearest village, to test upgrading |
| `/vr day run` | Run the evening work now for the nearest village (uses donations, then closes the request) |
| `/vr day sunrise` | Give every working station in the nearest village a fresh request |
| `/vr day status` | Time of day, latest work day, and each working station's request |

Player commands (used by the panel buttons, only work next to the bell):
`/village`, `/village make <x y z>`, `/village border <x y z>`, `/village rename <x y z> [name]`,
`/village panel <x y z>`, `/village donate <x y z>`, `/village disband <x y z> [confirm]`.

`/villagerework` works as a long form of `/vr`.

## How villages work (Phase 1)

- **Natural villages** are discovered automatically when a player comes within
  `village.discoveryRadius` of their bell. That bell becomes the **center bell** and gets
  an enchant-particle glow.
- **Village size** (0.19.0): the border reaches `village.radius` = **68** blocks from the center bell (48 before),
  and centers stay `village.minDistanceBetweenVillages` = 140 apart (128 before). Existing villages grow too;
  the config moves old default values forward once (configVersion 7).
- **Population**: a generated village gets one villager per house spot (mostly unemployed, sometimes a baby or a
  nitwit), so bigger villages have more. The WorldGen datapack (0.19.0) leaves about half the spots empty and
  makes villages size 11. New chunks only.
- **One village per generated village** (0.18.5). Big generated villages (e.g. from a size datapack) can have
  several bells far apart; only one becomes a center. The center is the bell nearest the town center (the
  structure's first piece), whichever bell you walk up to first; discovery waits until the town center is
  loaded. A structure that already has any village inside its footprint isn't discovered again.
- **Shift-right-click any bell with an empty hand** to open the bell screen (0.17.11: the mod's own screen,
  laid out like an inventory):
  - Center bell: village name, "A natural village" / "Founded by …", Residents, Beds, Donated emeralds, and a
    grid of all 13 stations in their current look (tier marks at II/III; greyed out when the village has no
    such station, or lost its block). Hovering a station shows its profession, block and level. Buttons:
    **Show border**, **Rename**, **Disband**, **Close**. A **"?"** tab on the right opens a short "How villages
    work" window (the bell, stations, emeralds, buying your own stations; 0.17.13); Back returns.
  - Another bell inside a village: tells you which village it belongs to and where the center is.
    If the village is dormant, it says whether this bell can become the new center, or which
    village it's too close to, and offers **Disband**.
  - A bell outside any village: founding requirements, with **Make village** when they're met.
- **Items only villagers used to sell** are rewards now: chainmail armour (armorer, II–III), the globe banner
  pattern (cartographer, III) and explorer maps (cartographer: ocean monument at II, trial chambers and woodland
  mansion at III; 0.17.15). Explorer maps search from the village's bell, claim their structure like vanilla's,
  and a reward slot holds only one (`StationTypes.explorerMap`, `cap`).
- **Crafting a bell** (0.17.14; villagers don't trade, so they can't sell one):
  ```
  stone  stick  stone
  stone  gold   stone
  stone         stone
  ```
  6 stone (plain stone, not cobblestone), 1 stick, 1 gold ingot. It shows in the recipe book once you've had
  a gold ingot. Files: `data/villagerework/recipe/bell.json`, `advancement/recipes/decorations/bell.json`.
- Every villager inside the border is a **resident** and gathers at the center bell in the
  late afternoon, before dusk (vanilla's meeting time). Other bells still ring
  (and still reveal raiders) but villagers don't meet at them.
- **Break the center bell** and the village goes dormant. Place a bell anywhere inside the
  village and it becomes the new center; the village keeps its name and data. This is also how
  you move a village on purpose: the border always follows the center bell. A replacement bell
  must keep the minimum distance to other villages, so a village can't be moved into another one.
- **Disband** removes a village and all its records (name, stations, levels) after a confirmation.
  It's on the center bell, and on any bell inside a dormant village. A disbanded natural village
  isn't rediscovered automatically; found a new one from a bell if you want it back.
- If two villages ever overlap (for example after raising `village.radius`), each villager,
  workstation and position belongs to the nearest center bell. On an exact tie, the older village wins.
- Villages can't be closer than `village.minDistanceBetweenVillages` to each other.

Every player needs the mod on their client (it adds items and screens).

## The stations (Phase 8)

All thirteen professions work the same way: a daily request ("We need" on the panel), the donate slot,
rewards, the progress bar and the upgrade item. Request amounts are for level I and scale ×2 at II and ×3 at
III (capped at one stack); fuel (coal, charcoal) is 8 / 16 / 32 (0.20.0). Requests marked (II) or (III) only
come up from that level. Each level adds rewards to the pool; single items (tools, armor, potions, maps)
arrive one at a time.

| Station (block) | We need (amount at I) | Rewards at I | Added at II | Added at III | Upgrade items II / III |
|---|---|---|---|---|---|
| Farmer (composter) | wheat seeds 16, beetroot seeds 16, bone meal 8 | wheat, carrots, potatoes, beetroot, sugar cane | bread, pumpkin pie, honey bottles, honeycomb | golden carrots, glistering melon | iron block / nether wart |
| Fisherman (barrel) | string 8, sticks 16, kelp 16, fishing rod 1 | cod, salmon | ink sacs, tropical fish, pufferfish | prismarine shards & crystals, nautilus shell | turtle scute / heart of the sea |
| Butcher (smoker) | coal, wheat 16, carrots 16, potatoes 16, sweet berries 16 | cooked pork, beef, mutton, chicken, leather, feathers, bone meal | cooked rabbit, rabbit hide, dried kelp blocks | rabbit stew | lead / honey block |
| Mason (stonecutter) | cobblestone 32, clay balls 16, coal, andesite 16, sand 16, resin clumps 16 (II), quartz 16 (III) | bricks, stone bricks, polished granite/diorite/andesite, glass panes | terracotta, glazed terracotta, resin bricks | quartz blocks, tuff bricks, polished deepslate | amethyst block / quartz block |
| Shepherd (loom) | wheat 16, shears 1, bone meal 8, red / yellow / blue / black dye 8 | white wool, colored wool, string | carpets, banners, mixed dyes (orange, lime, pink, light blue, gray) | flower, field masoned and bordure indented patterns, rare dyes (magenta, purple, cyan, light gray, brown) | iron block / snout (piglin) banner pattern |
| Armorer (blast furnace) | coal, raw iron 8, charcoal, raw copper 8, lava bucket 1 (the empty bucket drops back out) | iron nuggets, iron chains, gold nuggets, iron bars | iron ingots, chainmail helmet & boots | chainmail chestplate & leggings | iron block / netherite scrap |
| Toolsmith (smithing table) | coal, sticks 16, flint 8, raw copper 8, cobblestone 32 | iron & copper nuggets, buckets, copper pickaxe/axe/shovel | shears, flint and steel, compass, lanterns | iron pickaxe, shovel, axe, hoe; iron pickaxe with Efficiency I | anvil / diamond block |
| Weaponsmith (grindstone) | gravel 16, coal, sticks 16, raw copper 8, string 8 | copper sword & axe, flint | iron sword & axe, shield, bell | iron sword & axe with Sharpness I | iron block / blaze rod |
| Fletcher (fletching table) | sticks 16, flint 8, feathers 8 | arrows | spectral arrows | tipped arrows (slowness, poison, swiftness, healing) | target block / ghast tear |
| Tanner (cauldron) | leather 8, rabbit hide 8, string 8, armadillo scutes 4 (III) | item frames, bundles, leather horse armor, dyed leather cap or boots | dyed leather chestplate or leggings | iron & golden horse armor, wolf armor | saddle / armadillo scute |
| Cleric (brewing stand) | glass bottles 8, nether wart 8, rotten flesh 16, sugar 8; spider eyes 4, honey bottles 4 (II); rabbit's foot 1, blaze powder 4, golden carrots 4, glistering melon 4 (III) | redstone, glowstone dust, potions of night vision, swiftness, strength | bottles o' enchanting, lapis lazuli, long night vision / swiftness / strength | Swiftness II, Strength II, healing, regeneration | ghast tear / rabbit's foot |
| Librarian (lectern) | sugar cane 16, ink sacs 8, feathers 8, lapis lazuli 8 (II) | books, paper, **enchanted books** (level I enchant) | bookshelves, book and quill | chiseled bookshelves | amethyst cluster / echo shard |
| Cartographer (cartography table) | paper 16, glass panes 8, compasses 4 | empty maps, glow item frames | explorer maps: villages (plains, desert, savanna, snowy, taiga), mineshaft, desert and jungle pyramids, swamp hut, warm ocean ruins, ocean monument | spyglass, globe banner pattern, explorer maps: trial chambers, woodland mansion, ancient city, buried treasure | spyglass / lodestone |

**Stations feed each other** (0.20.0). Many rewards are another station's request, so hoppers can chain them
(a hopper into a station fills today's request; one underneath empties the rewards):
farmer → butcher (wheat, carrots, potatoes), shepherd (wheat), librarian (sugar cane), cleric (honey bottles,
golden carrots, glistering melon); butcher → tanner (leather, rabbit hide), fletcher and librarian (feathers),
farmer and shepherd (bone meal); librarian → cartographer (paper); mason → cartographer (glass panes);
toolsmith → cartographer (compass), shepherd (shears); weaponsmith → fletcher and toolsmith (flint);
shepherd → fisherman, tanner, weaponsmith (string); fisherman → librarian (ink sacs); cleric → librarian (lapis).
A station never asks for its next upgrade item before it has it (0.20.1: the tanner's armadillo scutes and the
cleric's rabbit's foot are asked for only at level III). No station asks for what it makes (0.20.0 changed: librarian paper → sugar cane, weaponsmith flint → gravel,
shepherd string → shears). Coal, iron and the rare items always come from players.

**Opening the panel** (0.19.0: like a chest, a right-click opens it whatever you hold; sneaking with an item
still places it)
- Barrel: a normal right-click opens storage with the panel docked.
- Composter, cauldron, fletching table (no screen of their own): right-click. Items with their own vanilla use
  keep it: compostables (and rotten flesh / spider eyes) go into the composter, a full composter gives its bone
  meal, and buckets, bottles, dyed armor, banners and shulker boxes use the cauldron as usual.
- Smoker, blast furnace, brewing stand, cartography table, loom, stonecutter, smithing table, grindstone,
  fletching table (from II): right-click (also shift-right-click) opens the vanilla screen with the **panel
  docked on its right** (0.16). If the panel would go past the window's edge, the screen moves left to make
  room; next to an open recipe book it folds into a **bell tab** at the screen's top-right corner, and
  clicking the tab closes the recipe book.
- Barrel, composter, cauldron, fletching table at I: the station screen, with the panel on the right too.
- Lectern: right-click opens the panel while it has no book (a book in hand goes onto the lectern instead);
  with a book, the book opens as usual and shift-right-click (empty hand) opens the panel.
- Hoppers only reach the village slots on a working station (also for smokers, blast furnaces and
  brewing stands).

## Rewards, rerolls and librarian books (0.11)

**One reward slot per level**
- The panel's **Rewards** row always shows three slots. Level I fills the first, II the first two,
  III all three (`stations.rewardSlotsByLevel`). Locked slots are marked with a barrier.
- Every unlocked slot gets its own pick from the pool each evening, independently: two or three
  slots can hold the same good. A slot still holding a good gets more of it, up to a stack.
- Each slot's delivery is smaller than before (`outputMultiplierByLevel` 1 / 1.5 / 2, was 1 / 2 / 3.5),
  so a full day is roughly ×1, ×3 and ×6 of level I in total. A server config with the old default
  numbers is moved to the new ones once on start; changed values are kept.
- A hopper underneath empties all three.

**Rerolls**
- A **Reroll** button sits under the three reward slots while any reward is waiting. Hover shows the
  price. Pay it and the villagers swap **everything still in the unlocked reward slots** for new picks
  from the same level's pool, each slot on its own. Take out what you want to keep first; only what's
  left is rerolled.
- The price is per station and **doubles with each reroll**: 1, 2, 4, 8 … emeralds, at most 64.
  If an enchanted book is among the rewards, the book price applies (3, 6, 12 …). It starts over at the
  station's sunrise (and with `/vr day sunrise`). The emeralds are spent; they don't count as a donation.
  Creative players reroll for free.
- Each new reward is the same size relative to a full delivery: a slot holding two days' worth of
  bread becomes two days' worth of the new good (at most one stack); half a stack taken out first means
  half as much afterwards. Plain goods never reroll into themselves; books, wool colors and similar can
  come back as a different variant.
- The `rerolls` config section: `enabled`, `baseCost`, `bookBaseCost`, `maxCost`.

**Librarian enchanted books**
- The librarian's pool has enchanted books at every level, always with **one level I enchantment**.
  With Enchant Library, a level I book on a shelf unlocks that enchant at level I; combining books
  in an anvil is still how you reach higher levels.
- Enchantments come in three tiers. The station's level sets the odds of each tier
  (`librarian.tierWeightsByLevel`, default I: 1/0/0, II: 3/2/0, III: 2/3/1), then any enchant in the
  tier is equally likely.
  - **Tier I:** protection (all four), unbreaking, efficiency, power, sharpness, feather falling,
    respiration, aqua affinity, lure, quick charge, piercing, loyalty, knockback, punch
  - **Tier II:** fortune, looting, silk touch, depth strider, thorns, infinity, flame, fire aspect,
    multishot, sweeping edge, riptide, channeling, luck of the sea, density, breach, lunge
  - **Tier III:** mending, frost walker, soul speed, swift sneak, wind burst
  - Never: curses, smite, bane of arthropods, impaling (the bane families replace them).
- The tiers are lists of enchantment ids in the `librarian` config section, so modded enchantments
  can be added; ids that don't exist are skipped. `librarian.bookWeight` (2) sets how often a slot
  picks a book (plain books 4, bookshelves 2, book and quill 1, lapis 2); changing it needs a restart.

## Take-home stations (0.17)

Workers sell their workstation with the station's tier, so its perks work at home too.
- **Buying:** right-click a worker (adult, with a profession) in a village whose station of that profession is
  at level II: a trade screen offers "<Profession>'s <Block> II" for 12 emeralds + a plain block of the same
  kind. At level III it also offers tier III for 32 emeralds + the block (`takeHomes.priceByTier`). Other
  villagers still just say which village they're from. The fisherman sells barrels (0.17.3): a bought barrel
  has extra rows of its own (one at II, two at III), shown as a bigger chest screen; they spill out with the
  barrel's own slots when it's broken. The villager's own trades stay switched off; this is a separate little shop.
- **The item:** the vanilla block with a name, its tier marks (0.17.8; was a glint) and "Level II/III". It never counts as payment in a
  trade (so shift-click buying can't use up the ones you just bought).
- **Placed:** it works like a village station of that tier for every perk tied to the block: smoker/blast
  furnace, brewing speed and the Bottle XP strip, mill/honing, trims, banner layers and copies, stonecutter,
  cartography copies and the Cave Map strip, composter, the tanner's cauldron (with its own water budget),
  fletching table (screen at II, arrows at III), lectern messages. No village panel, no requests or rewards.
  A green sparkle now and then shows it's special.
- **Breaking it** (by hand or by an explosion) drops the take-home again with its tier (0.17.1), so it can be
  moved and placed again. Pistons can't push it. Village stations still drop a plain block.
- **In a village** it keeps working with its own tier but can't be donated ("A bought workstation can't be
  donated"), and a new natural village doesn't take it as a station.
- Debug: `/vr takehome <profession> <2|3>` gives one.

## Upgraded looks (0.17.5)

Station blocks at tier II or III carry painted marks, so the tier is visible without any numbers.
- **Which blocks:** village stations at level II/III (not in a dormant village) and placed take-homes at their
  tier. Village stations showing it means players can see what a village's workers would sell before buying.
- **How:** the blocks stay vanilla. The server tells each client which positions are tiered (`StationLooks`:
  on the tick after any village or take-home change, plus once a second as a safety net; only changes go out).
  The player who places a take-home sees its marks at once: their own client already knows the tier from the
  item (0.17.6). Then the client draws a second copy of every station-textured face on top
  with the matching overlay texture (`client/looks/StationOverlays`). It follows every state and rotation (lit
  smoker, open barrel, filled cauldron) and resource packs still show underneath.
- **Overlay textures:** `assets/villagerework/textures/block/tier/<vanilla texture>_<tier>.png`, e.g.
  `smoker_front_2.png`, transparent except for the marks. A missing file just leaves that face plain. Placed
  blocks pick new PNGs up by themselves.
- **Take-home items** show the marks too (`items/take_home/<block>_<tier>.json`). After adding or removing PNGs,
  run `python tools/station_models.py <minecraft client jar>` to regenerate those item models. Brewing stand
  and cauldron items are flat pictures; they get marks from `textures/item/tier/brewing_stand_2.png` etc. if
  painted, otherwise they keep the vanilla picture.
- Painted (0.17.8): all 13 stations, gold for II, gold with emerald for III. Faces left plain on purpose:
  cartography table side 3, grindstone side/round, lectern top/base. The brewing stand's base is fully repainted gold.
- The take-home items no longer have the enchantment glint: the marks show the tier. (Items bought before 0.17.8
  keep their glint until they're placed and picked up again.) The green sparkle on placed take-homes stays.
- The brewing stand and cauldron items are flat pictures in vanilla; as take-homes they show as small 3D blocks
  with the marks instead (`items/take_home/<item>_block.json`), unless a flat `textures/item/tier/` picture is painted.

## The bell rings (0.20.0)

The center bell swings and rings at **sunrise**, when stations make their new requests, and at the **evening
gathering**, when the day's work is done and stations close (three rings in a content or abundant village).
Only players get the swing and sound: a real ring makes vanilla villagers run indoors, which would break up the
gathering. Code: `village/BellRings.java`.

## Renown (0.20.0)

What a village works toward once its stations are at level III. Each evening, every level III station adds its
contribution (how much of what it needed it got, 0 to 1) to the village's **renown**. Renown fills ranks: the
first costs 10, each next one 5 more (`takeHomes.renownFirstRank`, `renownRankStep`). **Each rank makes the
stations workers sell 10% cheaper**, compounding (`renownDiscountPerRank`), never below 1 emerald (`minPrice`).

| Rank | Renown needed in total | Tier II / tier III price | A full village (13 stations at III, all supplied) |
|---|---|---|---|
| 0 | 0 | 12 / 32 | |
| 1 | 10 | 11 / 29 | about a day |
| 5 | 100 | 7 / 19 | about a week |
| 10 | 325 | 4 / 11 | about 25 days |
| 20 | 1150 | 1 / 4 | about 3 months |
| 33 | ~2970 | 1 / 1 | over 200 days |

The bell screen shows a **Renown** bar (gold, like a full station bar) with the rank as a Roman numeral; hovering
it says what fills it and what it does, without numbers. Debug: `/vr renown` (points, rank, prices),
`/vr renown set <points>`. Code: `village/Renown.java`.

## Natural village chests (0.20.0)

Chests in generated villages get a little extra on top of their loot table: 1–3 emeralds in about 40% of chests
(`village.chestEmeraldChance`, `chestEmeraldMax`) and a level I enchanted book, picked like the librarian's, in
about 15% (`chestBookChance`). Added on top, so it works with Enchant Library's own village chest books.
Code: `village/VillageLoot.java`.

## Golems: cap and forgiveness (0.18.5, 0.19.0)

**Golem cap (0.19.0).** Vanilla villagers summon an iron golem when three of them panic together (hurt by a
player or a mob, or a hostile mob nearby) or five gossip together, if they slept in the last day and haven't
seen a golem for 30 seconds. In big villages most golems are out of sight, so they kept summoning more. Inside a
village they now stop when the village has **one golem per 8 residents** (at least 1, at most 4;
`village.residentsPerGolem`, `golemMin`, `golemMax`). Golems you build don't count against anything; outside
villages vanilla rules apply. Code: `village/GolemCap.java`, `VillagerMixin.villagerework$golemCap`.

**Forgiveness (0.18.5).**

Iron golems attack a player whom nearby villagers dislike. That dislike is vanilla gossip: every villager that
sees you kill or hurt one remembers it, they pass it on to each other, and it fades only slowly (about 10 points a
day; a killing spree takes 8+ days). The mod doesn't use gossip for anything else (no trading), so it was only
keeping golems angry.

Now villages **forgive** a player who **dies** or **sleeps through the night**: every loaded villager forgets its
bad gossip about that player (major and minor negative) and golems that are after them calm down. Villagers that
were unloaded then are forgiven when they next load, for 3 days. The lasting consequence stays: the village mood
keeps its death and hurt memories (see Prosperity). Code: `village/Forgiveness.java`.

## Deep emeralds (0.18.5)

Mountain emeralds are vanilla. On top of that, **larger emerald veins** generate deep in every overworld biome:
`data/villagerework/worldgen/feature/ore_emerald_deep.json` (vein size 10, vanilla's mountain veins are 3; half the
blocks touching air are dropped) and `placed_feature/ore_emerald_deep.json` (one try in about 1 chunk in 6, between
Y −64 and −16). Change the numbers there; the mod adds the feature with Fabric's biome API in `VillageRework`.
Only chunks generated after installing get the new veins.

## Prosperity (0.18)

Every village has a **prosperity** score from 0 to 100 that rises and falls. It's never shown as a number: the bell
screen shows the village's **face** in the top right (miserable, unhappy, neutral, content, abundant), and hovering
it shows the mood and what the villagers are talking about lately ("Someone cured a zombie villager!").

**How it adds up** (`prosperity` in the config):
- **Standing**, from what the village is: a base of 35, +1.5 per station level (all active stations together, up
  to +36), +4 per iron golem inside the border (counting at most 3).
- **Memory**, from what happened lately. Each event starts at its full worth and fades to nothing over some days:

  | Event | Points | Fades over |
  |---|---|---|
  | A zombie villager cured inside the village (once per day per village) | +10 | 5 days |
  | A raid beaten (doesn't stack: a new one replaces the old memory) | +20 | 5 days |
  | A villager dies (any cause) | −8 | 4 days |
  | An iron golem dies | −6 | 3 days |
  | A player hurts a villager (the same villager counts again after 30 s) | −3 | 2 days |
  | A villager turns into a zombie villager (counted instead of the death) | −12 | 4 days |

  All memories together stay between −40 and +30.
- **Moods:** below 20 miserable, 20–39 unhappy, 40–59 neutral, 60–79 content, 80+ abundant.

**What the mood does:**
- **Production at every level:** progress and goods ×0.7 / ×0.85 / ×1 / ×1.1 / ×1.25.
- **Breeding** (0.18.3): the food a villager needs before it wants to breed follows the mood: miserable never,
  then 18 / 12 (vanilla) / 9 / 6 (`prosperity.breedFood`). Free beds are still needed, as in vanilla.
- **Unhappy or miserable:** angry clouds over residents now and then; at the afternoon gathering they grumble
  ("no" sounds, more clouds). Right-clicking a villager there makes it shake its head with a "no".
  Miserable: children don't play tag and golems never hand out poppies.
- **Content and abundant:** the bell rings three times at the gathering (instead of once); golems hand villagers poppies more often
  (about 4× vanilla when content, 10× when abundant); children get bursts of speed while they play.
- **Abundant:** happy particles, and on about 3 days in 10 (`prosperity.partyChancePerDay`) a **party** at the
  gathering (time 9000–11000): the villagers dance like after a won raid (jumping, cheering, setting off
  fireworks) and the bell plays the **Lava Chicken** disc. `prosperity.parties`, `fireworks` and `song` switch
  parts off for every village; the bell screen's party switch for one village.
- **Raids defended** is counted on the bell screen.
- Abuse guards: cure loops gain nothing (zombifying costs more than a cure gives, and cures have a cooldown);
  raid farms gain nothing extra (no stacking); golems count only up to 3; hits on the same villager count once
  per 30 s.
- The faces are `textures/gui/mood/<mood>.png` (16×16, painted in 0.18.1: crying, angry, plain, raised brows, sunglasses).
- Debug: `/vr prosperity` (how the score adds up), `/vr prosperity event <cure|raid|death|golem_death|hurt|zombified>`,
  `/vr prosperity clear`.

## Station perks (0.13+)

An upgraded station doesn't just make more goods: the block itself works better. Perks start at
level II and only work on an **active station of a village that isn't dormant** (later also on
take-home blocks bought from villagers). Breaking a station gives a plain block with no perks.
Nothing is announced; the block simply behaves differently.

Design rules: perks are conveniences and alternatives (speed, capacity, a second way to get
something), never power, and there's always a vanilla way that costs a bit more time or material.

| Station | Level II | Level III | In |
|---|---|---|---|
| Farmer (composter) | Rich compost: an item that adds no layer gets a second try | Rotten flesh and spider eyes compost; a full composter gives 2 bone meal | **0.13** |
| Butcher (smoker) | Fuel lasts 50% longer | Cooks 50% faster | **0.13** |
| Armorer (blast furnace) | Fuel lasts 50% longer | Iron/gold armor and tools give back ingots (half the material, at least 1) | **0.13** |
| Cleric (brewing stand) | Brews 50% faster | XP bottling | **0.13** / **0.15** |
| Leatherworker (cauldron) | Dirt → mud | Concrete powder → concrete | **0.13** |
| Fisherman (barrel) | 4 rows | 5 rows | **0.14** |
| Mason (stonecutter) | Woodcutting | Reverse cuts (stairs → block) | **0.14** |
| Toolsmith (smithing table) | Strip a trim (material back, template not) | Re-trim (new material, no template) | **0.14** |
| Weaponsmith (grindstone) | Milling (cobble → gravel → sand/flint, bone → 4 bone meal) | Honing with flint (temporary edge) | **0.14** / **0.15** |
| Shepherd (loom) | 8 banner layers | 12 banner layers | **0.14** |
| Cartographer (cartography table) | One empty map makes two copies | Sonar maps (cave maps) | **0.14** / **0.15** |
| Fletcher (fletching table) | Fletching screen, 6 arrows | 16 tipped arrows per lingering potion; explosive arrows | **0.15** |
| Librarian (lectern) | Title cards from a signed book | Story scroll from a signed book | **0.15** |

**Details for 0.13**
- Composter: works for hand use and items thrown in by players (hoppers on a village station reach the
  village slots, not the composter). The second try applies to every compostable item.
- Smoker and blast furnace: the speed and fuel bonus is taken when a new piece of fuel starts burning,
  like vanilla's own fuel speeds. Plain furnaces never change.
- Blast furnace recycling: chestplate 4 ingots, leggings 3, helmet 2, boots 2, swords and tools 1
  (`perks.recycleShare` 0.5). If the output slot holds nuggets, take them out first.
- Brewing stand: 20 seconds becomes about 13. The speed is set when blaze powder is added.
- Cauldron: throw items into the water. A full cauldron soaks **64 items** (`perks.itemsPerFullCauldron`),
  so each water level is about 21. Small throws add up: the station remembers how much of the top level
  is used. If there isn't enough water for the whole stack, only part converts and the rest drops out as
  its own stack. Refill with a bucket or rain. Dirt, coarse dirt and rooted dirt make mud at II; concrete
  powder of any color makes concrete at III.
**Details for 0.15.6: cave maps (sonar maps until 0.16.1)**
- Cartographer, level III: the cartography table's screen gets a strip underneath:
  **[empty maps] [amethyst shards] [Cave Map] [output]** (0.16.1; it belongs to the table, not the village
  panel). Each map + shard → an unused **Cave Map** (called sonar map before 0.16.1); one click makes as many
  as the inputs and the output allow.
- Use it like an empty map: it becomes a filled map centered on you, **locked to the depth you're standing
  at**. It maps from 32 blocks below that depth to 4 above it (`perks.sonarBelow`, `sonarAbove`): far down,
  only a little up, so at a cliff in a big cave you see the ground below, and ledges or ceilings above you
  stay out. Its tooltip says "Sonar · depth Y −42".
- While you hold it inside that band, it maps caves instead of the surface: twice a second it floods out from
  you through open space (air, plants, water, lava) up to 16 blocks (`perks.sonarRadius`) and stops at walls.
  - Each column shows the **topmost floor inside the band with reachable open space above it**, in the
    block's map color (a ledge over a lower cave shows the ledge).
  - **Depth shading** in four tiers of the 36-block band (a map color has four brightness steps): the top
    9 blocks light, then normal, dark, and darkest for the deepest 9. Water is shaded the same way; lava is
    always bright orange. Walls the flood ran into are a near-black outline.
  - Unexplored parts are a faint lavender-grey grid (map colour "clay", 0.17.10; light gray before) instead of
    vanilla's beige paper: 8×8 cells, so at the default scale each cell is 8 blocks across. Maps drawn before
    0.17.10 keep their grey grid where it's still unexplored.
  - Nothing behind solid rock is revealed, not even a sealed air pocket in the same column: no x-ray.
- It's a normal map otherwise: copy, zoom out, lock, put it in an item frame. Works in the Nether too.
- Art (0.17.9): the unused Cave Map is `textures/item/sonar_map.png`, a filled one `cave_map.png` (its own
  icon via `items/cave_map.json`; same picture for now), and held in first person it lies on purple-grey paper,
  `textures/map/cave_map_background.png` (vanilla's 64×64 map paper recoloured; `client/mixin/FirstPersonMapMixin`).

**Details for 0.15.3: lectern messages**
- Librarian's lectern with a **signed** book (not a book and quill), triggered by a **redstone signal only**:
  each time the signal turns on (a button, pressure plate, or a calibrated sculk sensor next to it).
- Level II, **title card**: page 1 shows as a title on the screens of players within 16 blocks
  (`lectern.range`, the same reach as a calibrated sculk sensor). Line 1 is the title, line 2 the subtitle.
- Level III, **story scroll**: a book with more than one page scrolls up the screen like the end credits:
  title, "by author", then the pages. Esc skips it, holding space or a mouse button speeds it up, and it
  closes by itself at the end. A one-page book still gives a title card at III. Players with another
  screen open (inventory, chest) aren't interrupted; they miss that scroll.
- Limits: a lectern triggers at most once every 10 seconds (`lectern.cooldownSeconds`); the same scroll
  plays to the same player at most once every 2 minutes (`lectern.scrollRepeatMinutes`). Breaking the
  lectern resets both, so a rebuilt lectern works right away.
- This is the mod's first network message (the scroll), so the mod must be on the client, as before.

**Details for 0.15.2: fletching and explosive arrows**
- Fletching table (fletcher), level II+: a normal right-click opens the **fletching screen** (shaft, head
  and fletching slots → result); shift-right-click with an empty hand opens the village panel. At level I
  it works as before (empty hand opens the panel).
  - II: stick + flint + feather → **6 arrows** (crafting makes 4).
  - III: arrows + a lingering potion → up to **16 tipped arrows** from one potion (crafting: 8).
  - III: 4 arrows + TNT + fire charge → **4 explosive arrows**.
  - Shift-click items from your inventory: they go to the right slot. Shift-click the result: crafts as
    many whole times as the inputs and your inventory allow. Inputs return to you when you close it.
- **Explosive arrow** (new item, `villagerework:explosive_arrow`): works in bows, crossbows and dispensers;
  Infinity doesn't apply. On impact it explodes and is gone. In flight it trails smoke and small flames like a
  lit fuse (0.17.10; arrows still in the air after a server restart fly without it).
  - Blocks: strength 3, like a creeper (`explosiveArrows.blockPower`; beds are 5). On by default;
    `/vr explosivearrows breakblocks off` (or `on`) switches it and saves the config. Ancient debris
    resists explosions, so blasting netherrack uncovers it, as with beds.
  - Creatures and players: half of what that explosion would normally do (`explosiveArrows.entityDamage`).
  - Not in the creative inventory; `/give @s villagerework:explosive_arrow 16` for testing. The texture is
    `assets/villagerework/textures/item/explosive_arrow.png` (0.17.9).

**Details for 0.15.0**
- Brewing stand (cleric), level III: the brewing stand's screen gets a strip underneath
  **[glass bottles] [Bottle XP] [output]** (0.16.1; it belongs to the stand, not the village panel). Each glass bottle in the input + 7 XP points
  (`perks.xpPerBottle`) become an ordinary bottle o' enchanting (it stacks with found ones), which gives back
  3–11 XP, 7 on average: no loss over many bottles; the cost is the bottles and the trip. The input's count is
  your control: one click bottles as many as the input, your XP and the output allow. The button is greyed
  out when it can't make any. Whatever is left in the row goes back to you when you close the screen.
- Grindstone (weaponsmith), level III: a sword or axe plus a piece of flint (in either slot) gives a
  **honed** copy: +2 attack damage for the next 40 hits (`perks.honedAttackBonus`, `honedHits`). A pickaxe,
  shovel or hoe gets +5 mining efficiency (about Efficiency II) for the next 64 blocks (`honedMiningBonus`,
  `honedBlocks`). Meant for the early game: stone and iron tools feel it, diamond with Sharpness or
  Efficiency V barely does. The item shows "Honed (n)" and counts down; at 0 the edge and the line
  disappear and the item is exactly as before. Honing again starts the count over, never stacks.
  Enchantments are kept. Taking the result uses the item and one flint (no XP). A server config from
  0.15.0 is moved to these numbers once (`configVersion` 3).

**Details for 0.14**
- Barrel (fisherman): the station screen shows 4 rows at II and 5 at III (`perks.barrelExtraRowsByLevel`).
  The extra rows are kept by the village like the station slots; breaking the barrel spills them with the
  rest. If the level goes down, hidden rows keep their items until it's back up.
- Stonecutter (mason): at II logs, stems and bamboo blocks cut into planks (same amount as crafting), and
  planks into stairs (1), slabs (2) or a fence (1). At III any stairs cut back into one of their block.
  Other stonecutters don't show these. Slabs can't be reversed: a stonecutter takes one item per cut.
  The recipes are generated from vanilla's by `../../villagerework-0.18.4/tools/stonecutter_data.py` (13 woods, 93 stairs).
- Smithing table (toolsmith): at II put trimmed armor alone in the middle slot: the result is the armor
  without its trim, and the trim's material comes back to you (the template doesn't). At III trimmed armor
  plus a trim material, template slot empty, keeps the pattern in the new material. Trimming with a
  template always uses it up, as in vanilla (copying templates costs diamonds, a good sink).
- Grindstone (weaponsmith): at II put a stack in the **top** slot: cobblestone or cobbled deepslate →
  gravel, gravel → sand, bone → 4 bone meal; or gravel in the **bottom** slot → flint (2 gravel each).
  The whole stack is milled when you take the result (no XP); shift-click and taking half work too, and
  only what you took uses up input. The screen is titled "Mill, Repair & Disenchant". Grinding
  enchantments works as before.
- Loom (shepherd): banners get up to 8 layers at II, 12 at III (`perks.loomLayersByLevel`). Copying a
  banner in the crafting grid and the banner tooltip work up to 12 layers for every banner.
- Cartography table (cartographer): at II a map plus one empty map gives three maps instead of two.
  (Paper is already vanilla's zoom out, so copies with paper weren't possible.)

- **Dispensers and cauldrons (all cauldrons, not a perk):** a dispenser facing a cauldron fills it with
  a water, lava or powder snow bucket (water and powder snow also top up a partly filled one) and gets
  the empty bucket back; an empty bucket takes a full cauldron; a water bottle adds a level and a glass
  bottle takes one. Facing anything else, buckets and bottles work as in vanilla. Droppers don't do this.
- Config section `perks`: `enabled`, `fuelMultiplier`, `smokerSpeedMultiplier`, `brewingSpeedMultiplier`,
  `recycleShare`, `itemsPerFullCauldron`, `loomLayersByLevel`, `barrelExtraRowsByLevel`, `honedAttackBonus`,
  `honedHits`, `honedMiningBonus`, `honedBlocks`, `xpPerBottle`.

## The wandering trader (0.12)

The trader is the emerald market: he mostly **buys village goods for emeralds**, and sells a couple of
less common items. He comes far more often than in vanilla.

**When he comes**
- Vanilla rolls once a day (25–75%) and then 1 in 10, so in practice he shows up every couple of weeks.
- **One trader at a time.** While he's around, no other comes. The count starts the day he leaves.
- From then on the chance grows each day (`trader.visitChanceByDaysSince`): 25% the second day,
  50% the third, **certain on the fourth**. With his two-day stay, a new trader arrives about every 5 days.
  When he's due he spawns as soon as he finds a spot near a player (checked every minute).
- His stay is counted in played time, like vanilla's despawn timer: `/time add` and sleeping skip days
  but don't make him leave sooner. If he can't be found (area not loaded, or killed), he counts as
  around until his day is up.
- **Left-behind traders leave** (0.20.0). Vanilla only counts a trader's stay down while his area is loaded,
  so a trader in a village you left stayed there, frozen, while the next one came elsewhere; back home you
  could find several. Now a trader that loads and isn't the current one (or whose day is over) leaves a
  couple of seconds later, with his llamas.
- He spawns like vanilla: near a random player (at the village bell if one is close), with two
  llamas, and leaves after **one day** (0.20.0; was two). The `spawn_wandering_traders` game rule still turns him off.

**What he trades** (each visit picks at random from these lists)
- **5 orders for level I goods** (3 before 0.19.0), 1 emerald a trade, 4 trades each (up to 20 emeralds):
  24 wheat → 1, 18 carrot → 1, 18 potato → 1, 18 beetroot → 1, 18 cod → 1, 12 salmon → 1, 12 cooked porkchop → 1, 12 cooked beef → 1, 12 cooked mutton → 1, 12 cooked chicken → 1, 24 bricks → 1, 24 stone bricks → 1, 18 white wool → 1, 6 iron chain → 1, 24 arrow → 1, 6 item frame → 1, 18 redstone → 1, 12 glowstone dust → 1, 9 book → 1, 6 map → 1, 1 bucket → 1, 1 iron sword → 1, 1 iron axe → 1, 1 bundle → 1 (only an empty one, 0.19.0, so nothing inside is lost).
  The prices are about 3 days of a level I station's delivery per emerald.
- **3 orders for level II goods**, 3 trades each (up to 18): 24 bread → 2, 12 pumpkin pie → 2, 18 ink sac → 2, 12 tropical fish → 2, 12 pufferfish → 2, 18 cooked rabbit → 2, 12 dried kelp block → 2, 48 terracotta → 2, 18 iron ingot → 2, 24 spectral arrow → 2, 18 experience bottle → 2, 6 bookshelf → 2, 1 shield → 1, 1 chainmail helmet → 1, 1 chainmail boots → 1, 1 shears → 1, 1 flint and steel → 1.
  Stackable goods: about 4 days of delivery for 2 emeralds. Single items: 1 emerald each, 6 trades.
- **1 order for level III goods**, 2 trades (up to 10): 18 golden carrot → 5, 12 glistering melon slice → 5, 24 prismarine shard → 5, 18 prismarine crystals → 5, 24 quartz block → 5, 48 tuff bricks → 5, 48 polished deepslate → 5, 36 lapis lazuli → 5, 3 nautilus shell → 5, 1 chainmail chestplate → 2, 1 chainmail leggings → 2, 1 iron horse armor → 2, 1 golden horse armor → 2, 1 rabbit stew → 2, 1 flower banner pattern → 2, 1 field masoned banner pattern → 2, 1 bordure indented banner pattern → 2.
  Stackable goods: about 3 days of delivery for 5 emeralds. Single items: 2 emeralds each, 5 trades.
- **4 items for sale** (2 before 0.19.0): 1 blue ice for 6, 4 packed ice for 3, 4 podzol for 3, 1 cherry sapling for 5, 1 pale oak sapling for 5, 1 mangrove propagule for 5, 1 jungle sapling for 5, 1 acacia sapling for 5, 1 dark oak sapling for 5, 1 brain coral block for 3, 1 bubble coral block for 3, 1 fire coral block for 3, 1 horn coral block for 3, 1 tube coral block for 3, 1 sea pickle for 2, 1 slime ball for 4, 2 pointed dripstone for 2, 2 small dripleaf for 2, 1 firefly bush for 3, 1 spore blossom for 8, 4 moss blocks for 3, 4 glow berries for 2, 8 red sand for 2,
  2 lily pads for 2 (emeralds; the last four added in 0.19.0).
- So a visit is worth **up to 48 emeralds** if you have everything he asks for.
- He never buys enchanted books, and doesn't sell milestone items or name tags.

**Changing the trades**
- The trades are plain data in the mod: `data/villagerework/villager_trade/wandering_trader/` (one file per
  trade), the lists in `data/villagerework/tags/villager_trade/wandering_trader/`, and how many he picks
  from each list in `data/villagerework/trade_set/wandering_trader/`. A datapack can override any of them.
- All prices come from one list in `../../villagerework-0.18.4/tools/trader_data.py`; run
  `python3 tools/trader_data.py src/main/resources prices.tsv` to regenerate the files after changing it.
- `trader.replaceTrades` off gives him vanilla's trades again, `trader.guaranteedVisits` off vanilla's timing.
- `/vr trader status` shows the days since his last visit and today's chance; `/vr trader due` makes him
  come within a minute.

## How emeralds work (Phase 7)

- **Throw emeralds on the ground inside a village.** Villagers walk over, pick them up (happy
  particles and a "yes") and they go to the bell. They can't be taken back.
  (Like all mob pickups, this needs the `mobGriefing` game rule on, which is the default.)
- The bell panel shows **Donated emeralds** as a stack. The bell keeps at most **64 per day**;
  anything more is taken but wasted. A stack never shows more than 64, which hints at that.
- **At the evening work** the day's emeralds become points: 64 emeralds = 100 points, fewer
  scale down (rounded). The points are shared evenly between the stations that got at least some of
  their request that day, at most 10 per station; the rest is wasted. **Each point is +10%** to that
  evening's progress and goods, so 10 points doubles them. Stations that got nothing don't share.
- Emeralds thrown after the evening work count for the next evening.
- Each player's total is remembered per village (not shown) for later rewards.
- All numbers are in the `emeralds` section of the config. `/vr emeralds <count>` previews the split.

## How levels work (Phase 6)

- Each station goes from **level I to III** (`stations.maxLevel`).
- The bar fills from the evening work (only when you donate). When it's full (it turns gold),
  put the **milestone item** for the next level into the slot next to **Upgrade** and press it:
  - Fisherman: II needs a **turtle scute**, III a **heart of the sea**.
  - Farmer: II needs an **iron block**, III **nether wart**.
- Upgrading uses the item, raises the level, empties the bar, drops `stations.levelUpXp` XP (91 points since
  0.19.0: about levels 0 → 7) at the
  station, and celebrates (particles and the level-up sound). Anything in the donate slot goes back
  to you, and the villagers make a new request sized for the new level.
- Higher levels ask for more each day and deliver more goods (the per-level lists in the config).
- The upgrade is checked on the server, so the button can't be used to cheat.
- At max level the bar, the Upgrade button and the milestone slot are hidden.

## How the daily work works (Phase 5)

Every profession has daily work since Phase 8 (see "The stations" above).

- **The station screen** has the village panel docked on the left (like the recipe book) and the
  block's own screen on the right:
  - **Barrel**: a normal right-click opens the barrel's storage with the village panel next to it.
  - **Composter**: right-click opens the panel (any item in hand since 0.19.0). Holding compostables
    composts as usual, and a full composter still gives its bone meal.
  - The panel, top to bottom:
    ```
    Fisherman Station
    We need   [16 String]      today's request; faded once fully donated, a barrier after the evening
    Donate    [ ]              today's item only, never more than requested
    Rewards   [ ] [ ] [ ]      the villagers' goods, take only (one slot per level, see 0.11)
    Level II
    [■■■■■■■□□□]               hover: progress
    [Upgrade] [ghost item]     the item for the next level, faded until you put one in
    ```
  - Each day the request changes to a different item from the station's pool
    (fisherman: string, sticks, kelp; farmer: wheat seeds, beetroot seeds, bone meal).
  - **Shift-click** from your inventory: donate slot first, then the milestone slot, then the
    barrel's storage. Without storage, items move between inventory and hotbar like vanilla stations.
  - This screen is drawn by the mod on the client, so **the mod must be installed on the client**.
- **A station's day has two moments:**
  - **Sunrise**: a new request (a different item from the pool, sized by level).
    A station also asks right away when it's donated, and right after a level-up. If that happens after the
    evening work (evening or night), the new request starts closed and opens at sunrise (0.19.0; before, it
    stayed open through the night and all the next day).
  - **Evening, when villagers start gathering at the bell** (time 9000, before anyone can sleep):
    the work below is done, and the request closes (barrier) until sunrise.
- The evening work, for each station with at least one worker:
  1. **Contribution** = how much of today's request is in the supply slots, 0–100%. That much is used.
  2. **Progress** toward the next level = contribution × crew × `progressPerWorkerPerDay`, where the first
     worker counts as `firstWorkerWeight` (2) workers (0.19.0). Bars: 80 / 200 / 500 (was 100 / 250 / 500).
     A lone worker with full supplies fills level I in 10 days, a full crew of 3 in 5.
     No contribution, no progress. The bar stops when full; the level-up itself comes in Phase 6.
  3. **Rewards**: one bulk delivery into each unlocked reward slot, scaled by contribution and level.
     **No contribution, no rewards** (0.20.0, `idleRewardSlots` 0): a station that got nothing makes nothing,
     so it's clear it didn't work. An empty slot gets a
     random good (fisherman: cod, salmon or ink sacs; farmer: wheat, carrots, potatoes or beetroot).
     If the slot already holds goods, more of the same are added up to a full stack.
     A full slot gets nothing that day.
- A station without workers does no work.
- If the village was unloaded, missed evenings are caught up (up to `maxCatchUpDays`), but supplies
  only count for the latest one.
- **Hoppers** work like with a furnace: a hopper pointing into the station fills the supply slots
  (only accepted items), a hopper underneath pulls the goods. While a block is an active village
  station, hoppers never reach its own storage or function (no hopper-fed barrel storage, no hopper
  composting). Players can still use it by hand.
- Breaking a station, or disbanding the village, **drops** its supplies and goods.

## How the workforce works (Phase 4)

- A villager's **profession is locked** once it has one. It counts toward that profession in the
  village it lives in, up to `stations.workerCapPerStation` (3 by default).
- A **worker** is a villager whose profession has an active station. Workers walk to the station
  during work hours. If the station is broken, they keep their profession for a while: after
  `stations.jobReleaseDays` (1 day) without an active station for their job in the village (lost, or never
  donated, e.g. a villager from the edge of a big generated village), they become unemployed and take the next
  free job (0.19.0). The timers are memory only, so a server restart starts them over.
- **Adults without a profession** (grown-up babies, cured zombies without a job, villagers you bring in)
  take a job at an active station with free slots, preferring stations with no workers yet; ties are
  random. You'll see happy-villager particles and the outfit change. Checked every village update
  (5 seconds), so donating a station gets it staffed quickly. Nitwits never work.
- Villagers beyond every cap stay without a profession until a slot opens.
- **One at a time** (0.20.0): after a station takes a worker it waits `stations.hireDelayHours` (2 in-game hours,
  about 2 minutes) before taking the next, so with several unemployed villagers you have time to donate more
  stations and the jobs spread out. Empty stations are always filled first.
- **Right-clicking a villager** says what it does and where (0.19.0): "Farmer of Oakvale"; residents without a
  job (and children) say "A resident of Oakvale".
- **Farmers don't pick up bone meal** (0.19.0; vanilla has them collect it), so bone meal dropped in a village
  stays for players. They still collect seeds and wheat.
- The village keeps a **roster** of its workers, so a worker who wanders outside the border or into
  unloaded chunks keeps its slot. A slot opens when the worker dies, is zombified, or settles in
  another village. A station can still show more than 3 when villagers arrive with a profession
  (natural villagers, cured zombies, or ones that got a job while vanilla job claiming was on):
  professions are locked, so the cap only limits new jobs.
- The workforce only runs while `features.disableVanillaJobClaiming` is `true`. With vanilla job
  claiming on, vanilla hands out professions and the workforce pauses, so the two never fight.
  The server log, `/vr config reload` and `/vr station list` warn you when a vanilla switch is on.
- **Disbanding** a village makes all its residents (except nitwits) unemployed again.
- **Natural villages** start with 10–30% progress toward level 2 on their seeded stations
  (`stations.naturalStartProgressMin/Max`). This only applies to villages discovered from now on.
- A village a player founds, even inside a generated village, starts with no stations.
  That's what makes "disband, found again, donate the stations you want" a clean restart.
- Worker numbers are deliberately not shown to players (only in debug commands). Players learn
  how a village is staffed by looking at the villagers' outfits.

## How stations work (Phase 3)

- **Shift-right-click a workstation with an empty hand** inside a village. If the village has no
  station for that profession yet, you get a **Donate** prompt. All 13 workstations work, including
  the ones without a screen (composter, cauldron, fletching table, empty lectern).
- **One station per profession per village.** Once a village has a barrel station, other barrels
  in the village act completely normally (no prompt, they just open).
- A donated station gets an enchant-particle glow (placeholder). Shift-clicking it shows its panel:
  profession, level and progress. Workers, inputs and outputs come in later phases.
- **The village owns the level, not the block.** Breaking a station gives you an ordinary,
  stackable workstation. The village keeps the profession's level and progress, and the next
  workstation of that kind you donate takes over from there.
- Stations destroyed in other ways (explosions, pistons, commands) are noticed on the next update.
- **Natural villages start with their generated workstations donated**, one per profession,
  closest to the bell first. Turn this off with `stations.autoDonateNaturalStations`.
  Villages you've already visited get this the next time you're near them.
- The bell panel lists the village's active stations, plus any profession it remembers
  but currently has no station for.

## What changed in Phase 2

Both switches in the `features` section of the config now work, and apply as soon as you
run `/vr config reload`:

- `disableVanillaTrading`: right-clicking a villager no longer opens the trade screen.
  The villager looks at you, and the action bar says which village it lives in.
  Name tags, leads and spawn eggs still work. The wandering trader still trades.
- `disableVanillaJobClaiming`: villagers no longer take jobs from workstations, and
  villagers who already have a profession **keep it as a cosmetic**, even if their
  workstation is broken. Phase 4 decides how professions are handed out instead.

## Full test checklist (0.20.2)

One pass over everything the mod does. Each line is something to see or try in game; the debug commands
that make it quick are in brackets. Use a fresh test world with cheats on, plus a copy of the real world
for the last section. The old per-version checklists are in `../../villagerework-0.18.4/docs/old-checklists.md`.

**Start-up**
- [ ] The game starts with no mod errors. `Requested post effect does not exist: minecraft:end_of_frame` is a
      vanilla 26.3 warning (Minecraft asks for an effect it doesn't ship) and can be ignored.
- [ ] `config/villagerework.json` exists and has a `prosperity` section; `/vr config reload` works.
- [ ] Leave the world and open a second one in the same session: no borders, moods or tier marks carry over.

**Villages and the bell screen**
- [ ] Walk up to a natural village: its center bell glows (`/vr village list`).
- [ ] Shift-right-click the center bell with an empty hand: name, "A natural village", Residents, Beds, Donated
      emeralds, the 13-station grid (missing ones greyed out, hover shows name and level), Show border /
      Rename / Disband / Close.
- [ ] Left-side tabs: "?" opens the help window (Back and Esc return), the mood face shows gossip on hover, the
      party switch toggles with no grey box left behind.
- [ ] Show border draws a red wall of particles about 8 blocks tall, easy to spot from a distance; Rename changes the name; Disband asks in a red title with an amber
      warning, Back cancels, Yes removes the village.
- [ ] Show border shows the bigger edge (68 blocks) on an existing village after updating.
- [ ] A freshly generated village (new WorldGen datapack) has roughly half the villagers it used to.
- [ ] A big generated village (size datapack) becomes one village, centered on the bell at its town center,
      even when you walk up to an outer bell first (`/vr village list`).
- [ ] A bell outside villages lists founding requirements and offers **Make village** when they're met.
- [ ] Break the center bell: the village goes dormant; place a new bell inside: it becomes the center.
- [ ] Craft a bell: stone, stick, stone / stone, gold ingot, stone / stone, empty, stone.

**Workforce and daily work**
- [ ] Villagers don't claim jobs or trade on their own; right-clicking a worker says "<Job> of <village>", an
      unemployed one or a child "A resident of <village>".
- [ ] Farmers walk past bone meal on the ground (they still pick up seeds and wheat).
- [ ] Break a station whose workers you can see; after a day without it (`/time add 24000`, then wait for a
      village update) they lose their outfit and soon take another free station's job.
- [ ] Level a station up, or donate one, after the evening gathering: its request shows the barrier until
      sunrise, then a new request appears.
- [ ] Shift-right-click a workstation inside a village with an empty hand: Donate prompt → it becomes a station.
- [ ] A worker takes the station; in the evening the request is used and progress and goods appear
      (`/vr day run`, `/vr day sunrise`, `/vr day status`).
- [ ] Fill a bar and pay the milestone: the station goes to II, then III (`/vr station fill <profession>`).
- [ ] Breaking a station gives a plain block; the village keeps the level for the next one donated.
- [ ] Hoppers under a station empty its rewards.
- [ ] One worker alone with full supplies fills level I in about 10 days (`/vr day run` repeatedly).
- [ ] With 4 unemployed villagers, donate one station: one takes it at once, the next only after ~2 minutes;
      donate a second station in between and it gets a worker first.
- [ ] A station that got nothing delivers no rewards that evening (`/vr day run`).
- [ ] The center bell swings and rings at sunrise and at the gathering (three times in a content village);
      villagers keep gathering instead of running indoors.
- [ ] Coal and charcoal requests are 8 at I, 16 at II, 32 at III. Requests marked (II)/(III) in the table only
      appear from that level (mason resin/quartz, cleric ingredients, librarian lapis, tanner armadillo scutes).
- [ ] Fisherman sometimes asks for a fishing rod; shepherd for shears; librarian for sugar cane; weaponsmith
      for gravel.
- [ ] New rewards show up: butcher leather/feathers/bone meal, farmer sugar cane and honey, mason glass panes and
      resin bricks, toolsmith copper tools/compass/lantern, weaponsmith copper sword/axe and flint (iron at II,
      bell at II), cleric potions at every level and lapis at II, librarian paper and chiseled bookshelves,
      tanner wolf armor, cartographer glow item frames and the new explorer maps (each points to a real structure).
- [ ] Shepherd (loom): take rewards with left and right clicks, also clicking next to the slots on the panel:
      nothing falls on the ground. Requests include red/yellow/blue/black dye; mixed dyes at II, rare dyes at III.
- [ ] Upgrade items: cartographer III needs a lodestone, cleric III a rabbit's foot; tanner and cleric don't ask
      for their upgrade item (scutes, rabbit's foot) before level III.
- [ ] A hopper chain from a farmer's rewards into a butcher fills the butcher when it asks for wheat/carrots/potatoes.
- [ ] A level-up drops about 7 levels' worth of XP for a player at level 0.
- [ ] The butcher sometimes asks for potatoes or sweet berries; armorer, toolsmith, mason, weaponsmith ask for
      coal less often (raw copper, cobblestone, andesite, sand, string, lava bucket). Supplying a lava bucket
      drops the empty bucket back out at the evening.
- [ ] A level I tanner can deliver leather, leather horse armor, or a dyed leather cap or boots.
- [ ] Right-click a composter, cauldron, fletching table and empty lectern while holding e.g. dirt: the panel
      opens. Compostables still compost, buckets still fill/empty the cauldron, a book still goes onto the lectern,
      sneaking still places the block.

**Emeralds, rewards, rerolls**
- [ ] Thrown emeralds are picked up and counted; the bell takes at most 64 per day.
- [ ] Rewards: 1 / 2 / 3 slots at I / II / III; Reroll doubles in price (1, 2, 4 …; books 3, 6 …), resets at sunrise.
- [ ] Librarian books are always level I enchantments; tiers follow the station level.
- [ ] Armorer gives chainmail, cartographer the globe pattern and explorer maps (ocean monument at II, trial
      chambers and woodland mansion at III), and each map points to a structure near the village.

**Station perks** (`/vr station setlevel <profession> 2|3`)
- [ ] Composter: second try at II; rotten flesh composts and 2 bone meal at III.
- [ ] Smoker and blast furnace: fuel lasts longer at II; smoker cooks faster and blast furnace recycles at III.
- [ ] Brewing stand: faster at II; Bottle XP strip at III.
- [ ] Cauldron: dirt → mud at II, concrete powder → concrete at III.
- [ ] Barrel: 4 rows at II, 5 at III. Stonecutter: woodcutting at II, reverse stairs at III.
- [ ] Smithing table: strip a trim at II, re-trim without template at III.
- [ ] Grindstone: milling at II, honing with flint at III ("Honed (n)" counts down).
- [ ] Loom: 8 and 12 banner layers. Cartography table: 3 maps from a copy at II, Cave Map strip at III.
- [ ] Fletching table: fletching screen with 6 arrows at II; 16 tipped arrows and explosive arrows at III.
- [ ] Lectern with a signed book and a redstone pulse: title card at II, story scroll at III.
- [ ] Dispensers fill and empty cauldrons with buckets and bottles.

**Cave Maps and explosive arrows**
- [ ] A Cave Map has its own icon; held in hand it lies on purple-grey paper; unexplored areas show the lavender grid.
- [ ] Underground it maps only open space within its depth band; nothing behind solid rock shows.
- [ ] Explosive arrows trail sparks and smoke in flight and explode on impact; `/vr explosivearrows breakblocks off` stops block damage.

**Take-home stations and tier looks**
- [ ] A worker at a II station sells its block (12 emeralds + block); at III also tier III (32 + block).
- [ ] The item shows the tier marks (brewing stand and cauldron as small 3D blocks), no glint.
- [ ] Placed, the marks appear at once and the perks work without a village; broken, it drops again with its tier.
- [ ] It can't be donated or pushed by pistons. A bought barrel has its extra rows.
- [ ] Village stations at II/III show their marks too; they disappear when the village goes dormant.

**Wandering trader**
- [ ] He arrives at least every 4 days (`/vr trader status`, `/vr trader due`) and buys village goods for emeralds.
- [ ] He leaves after one day. Leave him in a village, travel until a new one comes, go back: the old one
      (and his llamas) is gone within seconds of arriving.
- [ ] His screen has 5 level I orders and 4 items for sale. A bundle with items in it can't be sold to him; an
      empty one can.

**Prosperity and moods** (`/vr prosperity`, `/vr prosperity event <kind>`, `/vr prosperity clear`)
- [ ] The face changes with the score; hovering shows recent events as gossip.
- [ ] Each event moves the score as expected: cure +, raid +, death −, golem death −, hurt −, zombified −.
- [ ] Hitting the same villager twice within 30 s counts once; a cure counts once per day.
- [ ] Miserable or unhappy: angry clouds, grumbling at the gathering, head shake on right-click; miserable
      villagers never breed, children don't play tag and golems give no poppies.
- [ ] Content/abundant: the bell chimes at the gathering, more poppies, children sprint while playing,
      production goes up (×1.1 / ×1.25).
- [ ] Abundant: happy particles; on a party day the villagers dance, fireworks go off and Lava Chicken plays.
- [ ] With the party switch off: no fireworks, no music, no dance. The config switches do the same for every village.
- [ ] Raids beaten are counted on the bell screen.

**Renown, chests, help**
- [ ] The bell screen shows the Renown bar; with level III stations supplied it grows each evening; hover text
      has no numbers. `/vr renown set 100` → rank V, take-homes 7 / 19 emeralds in that village's shop.
- [ ] Fresh village chests sometimes hold emeralds or a level I enchanted book.
- [ ] The "?" window shows the new text and the 13 station perks, each with its block icon, name and II / III lines.
- [ ] The "?" window has paragraph gaps, the new sections, and scrolls (wheel and dragging the bar) when the
      game window is small.

**Golems and forgiveness**
- [ ] A village with ~10 residents keeps at most 1 golem from panics (hit a villager a few times; `/vr prosperity`
      shows the golem count); with 16+ residents up to 2, and so on to 4.
- [ ] Kill a few villagers in front of others: golems attack you.
- [ ] Die (or get killed by the golem) and come back: golems leave you alone.
- [ ] Repeat, then sleep through the night instead: golems leave you alone in the morning. Leaving the bed at
      night doesn't count.
- [ ] Repeat, die far away so the village unloads, then return: golems leave you alone.
- [ ] The village mood still shows the deaths (`/vr prosperity`).

**Deep emeralds** (new chunks only)
- [ ] In a freshly generated area outside mountains, emerald veins of several blocks show up below Y −16
      (spectator mode or an x-ray pack makes it quick); mountain emeralds are as before.

**On the real server**
- [ ] Copy the world, start the server with the new jar, and check that existing villages, stations, levels,
      take-homes and rewards are all still there.
- [ ] Both players can open the bell screen and station screens, and both see the tier marks.

## Known notes

- **Every player needs the mod.** The mod adds items (explosive arrow, Cave Map), so a client without it can't
  join the server. The vanilla-dialog fallback in `ui/BellView` is kept but won't normally run.
- **Renown, hiring delay and bell rings**: hire timers and rings are memory only; a restart just lets the next
  hire happen at once. Renown is saved with the world.
- **Old rewards in slots** (0.20.0): rewards a station no longer makes (e.g. the librarian's lapis, the
  cartographer's banners) stay in their slot until taken; the slot then gets new rewards.
- **Opening stations with an item in hand** (0.19.0): the client doesn't know which blocks are stations, so
  right-clicking a composter, cauldron or fletching table with a block in hand can show that block for a moment
  before the panel opens and the server removes it.
- **Explorer maps** search for their structure when a reward is picked, like a vanilla cartographer trade. Far
  from any mansion this can make the server pause for a moment in the evening.
- **Lava Chicken** starts as a plain sound: switching the party off while it plays doesn't stop the song, only the
  next party.
- **Forgiveness for unloaded villagers** lasts 3 days and is memory only: after a server restart, villagers that
  were unloaded when you were forgiven keep their bad gossip until it fades the vanilla way.
- **Villages already doubled up** before 0.18.5 stay as they are; disband the extra one from its bell.
- **Memory only, by design:** border rings, pending forgiveness, tier-mark syncing, lectern cooldowns, flying explosive arrows and the
  deferred death checks are reset when the server stops (0.18.4), so they never leak into the next world.

## Version history

| Version | What it added |
|---|---|
| 0.20.2 | New help text in the bell's "?" window, with the station perks listed by block icon |
| 0.20.1 | Loom panel clicks no longer drop items; shepherd dyes; lodestone and rabbit's foot upgrade items; scute and rabbit's foot requests at III |
| 0.20.0 | Renown, daily bell rings, stations feed each other, new requests/rewards, fuel 8/16/32, no rewards without contribution, hiring one at a time, left-behind traders leave, 1-day trader, village chest extras, scrollable help |
| 0.19.0 | Renamed Other: Villages; bigger borders, golem cap, job release, closed evening requests, faster level I, more level-up XP, less coal, trader 5 buys / 4 sells, empty bundles only, open stations with items, job names, farmers skip bone meal |
| 0.18.5 | One village per generated village, red stacked border, forgiveness on death and morning, deep emeralds |
| 0.18.4 | Clean state between worlds; README with one full checklist |
| 0.18.3 | Living moods: grumbling, head shakes, chimes, poppies, playing children, breeding, party days |
| 0.18.2 | Side tabs on the bell screen ("?", mood face, party switch) |
| 0.18.1 | Painted mood faces; per-village party switch |
| 0.18.0 | Prosperity: a 0–100 village mood that sets production and celebrations |
| 0.17.15 | Explorer maps as cartographer rewards |
| 0.17.14 | Craftable bell |
| 0.17.11–13 | The mod's own bell screen with station grid and help tab |
| 0.17.9–10 | Cave Map and explosive arrow art, fuse trail, lavender grid |
| 0.17.5–8 | Tier marks on upgraded stations and take-home items |
| 0.17.0–4 | Take-home stations |
| 0.16.x | Station screens with the village panel docked on the right |
| 0.13–0.15.9 | Station perks for all 13 stations |
| 0.12 | The wandering trader as the emerald market |
| 0.11 | Reward slots, rerolls, librarian books |
| Phases 1–8 | Villages, no vanilla trading, stations, workforce, daily work, levels, emeralds, all 13 stations |

## Layout

```
src/main/java/dev/villagerework/
  VillageRework.java        mod entrypoint
  config/VRConfig.java      all tunable values, loading and validation
  command/VRCommands.java   /vr debug commands
  command/VillageCommands.java  /village player commands behind the panel buttons
  village/Village.java      one village: id, name, center bell, origin, dormant
  village/VillageRegistry.java  all villages in a dimension, saved with the world
  village/VillageManager.java   discovery, bell checks, member sync, founding, glow
  village/BorderDisplay.java    the red particle wall for "Show border"
  village/GolemCap.java         golems per village (0.19.0)
  village/BellRings.java        the bell at sunrise and the gathering (0.20.0)
  village/Renown.java           renown and take-home discounts (0.20.0)
  village/VillageLoot.java      extras in natural village chests (0.20.0)
  village/Forgiveness.java      villages forgive a player on death or after sleeping (gossip, golems)
  resources/data/villagerework/worldgen/   the deep emerald vein (feature + placed_feature)
  village/VillageNames.java     generated village names
  village/ProfessionStation.java  one profession in a village: level, progress, active station
  village/StationManager.java   donating, removing, validating and seeding stations
  village/WorkforceManager.java  who works where, job assignment, releasing on disband
  village/WorkCycle.java     the evening work: contribution, progress, goods, next request
  village/StationTypes.java  what each station asks for, gives, and needs to level up (all thirteen)
  village/Upgrades.java      leveling a station up
  perk/Perks.java           the perk level of a block (village station level; later bought blocks)
  perk/PerkContext.java     hands that level from a method's start to a call inside it (for mixins)
  perk/FurnacePerks.java    blast furnace recycling table
  perk/CauldronPerks.java   soaking items in the leatherworker's cauldron
  mixin/ComposterBlockMixin.java, AbstractFurnaceBlockEntityMixin.java, BrewingStandBlockEntityMixin.java,
  mixin/LayeredCauldronBlockMixin.java   the 0.13 perks
  perk/CauldronDispensing.java, mixin/DispenserBlockMixin.java   dispensers fill and empty cauldrons
  perk/StonecutterPerks.java, LoomPerks.java, GrindstonePerks.java, MillingSlots.java, BarrelPerks.java   0.14 perks
  mixin/StonecutterMenuMixin.java, LoomMenuMixin.java, GrindstoneMenuMixin.java, SmithingMenuMixin.java,
  mixin/CartographyTableMenuMixin.java, BannerDuplicateRecipeMixin.java, BannerPatternLayersMixin.java
  client/mixin/LoomScreenMixin.java   the loom screen's layer limit
  village/ExtraStorage.java, ContainerSlice.java   the barrel's extra rows
  perk/Honing.java, mixin/ItemStackMixin.java   honed weapons and tools (and their countdown)
  perk/XpBottling.java   the cleric's Bottle XP action
  ui/FletchingMenu.java, perk/FletchingRecipes.java, client/FletchingScreen.java   the fletching screen
  item/ModItems.java, ExplosiveArrowItem.java, ExplosiveArrows.java, mixin/AbstractArrowMixin.java   explosive arrows
  resources/assets/villagerework/   the explosive arrow's name, model and texture
  perk/LecternMessages.java, net/StoryPayload.java, mixin/BlockBehaviourMixin.java, client/StoryScreen.java
                            lectern title cards and story scrolls
  perk/SonarMaps.java, item/SonarMapItem.java, mixin/MapItemMixin.java, ui/StationActions.java
                            sonar maps, and the perk actions on the station panel
tools/stonecutter_data.py   generates the woodcutting and reverse-cut recipes (not needed for building)
tools/station_models.py    generates the take-home item models from the tier overlay PNGs (not needed for building)
  trader/TraderVisits.java   when the wandering trader comes (days since last visit, saved)
  trader/TraderTrades.java   the trader's four trade sets (data in resources/data/villagerework/)
  mixin/WanderingTraderSpawnerMixin.java  the trader's new visit timing
  mixin/WanderingTraderMixin.java  the trader's trades come from the mod's trade sets
  village/Rerolls.java       paying emeralds to swap a waiting reward (price, new pick, amount)
  village/EmeraldDonations.java  villagers picking up emeralds for the bell
  village/StationInventory.java  a station's supply and goods slots (also what hoppers see)
  ui/StationMenu.java       the station screen's slots and shift-click rules
  ui/StationRules.java      panel slot rules and synced values (shared by the screen and the docked panel)
  ui/DockedStation.java     the panel docked to vanilla screens (slots, values, buttons)
  perk/TakeHomes.java, perk/TakeHomeShop.java, mixin/BlockItemMixin.java   take-home stations
  perk/StationLooks.java, net/StationLooksPayload.java   which tiered blocks each client should mark (0.17.5)
  ui/ActionRow.java, ui/StationActions.java   perk action strips under vanilla screens (Bottle XP, Cave Map)
  mixin/AbstractContainerMenuMixin.java, BaseContainerBlockEntityMixin.java, AbstractFurnaceMenuMixin.java,
  BrewingStandMenuMixin.java, ServerGamePacketListenerImplMixin.java (+ the loom, stonecutter, grindstone,
  smithing and cartography menu mixins)   docking hooks
  ui/StationMenus.java      the two station screen types (with storage, panel only)
client/StationScreen.java   draws the station screen (vanilla look + panel)
village/Prosperity.java   the prosperity score, its events, and the celebrations (0.18)
client/BellScreen.java, BellHelpScreen.java   the bell screen and its help window (0.17.11; content from ui/BellView via net/BellScreenPayload)
client/looks/StationLooksClient.java, StationOverlays.java   tier marks on placed station blocks (0.17.5)
client/StationPanelRenderer.java, DockPanel.java, ActionStrip.java, mixin/AbstractContainerScreenMixin.java,
  mixin/AbstractRecipeBookScreenMixin.java   draw the docked panel, its bell tab and the action strips
  mixin/HopperBlockEntityMixin.java  hoppers reach the station's village slots
  ui/Dialogs.java           shared panel building blocks
  ui/BellPanel.java, ui/BellView.java   what each bell screen version shows (sent as net/BellScreenPayload)
  ui/StationPanel.java      station panel and donate prompt
  ai/GatedBehavior.java     switches vanilla AI behaviors on and off from the config
  mixin/VillagerMixin.java  no trade screen; head shakes and breeding food by mood
  mixin/OfferFlowerGoalMixin.java, ai/MoodGatedBehavior.java   poppies and children playing by mood
  client/mixin/FirstPersonMapMixin.java   the Cave Map's purple-grey paper in hand
  mixin/VillagerGoalPackagesMixin.java  no vanilla job claiming
  mixin/                    changes to vanilla code (from Phase 1)
src/client/java/dev/villagerework/client/
  VillageReworkClient.java  client entrypoint (screens from Phase 1)
docs/old-checklists.md      the per-version test checklists up to 0.18.3
```
