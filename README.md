# Other: Villages

A Fabric mod for Minecraft 26.3 that replaces villager trading with villages you grow.
Villagers work at profession stations owned by their village, and the village gets better as you supply it.
Upgraded stations can be bought and taken home as better versions of the vanilla blocks.

**Required on both client and server.** Requires [Fabric API](https://modrinth.com/mod/fabric-api).

## Why

Vanilla's villager system rewards the same few chores over and over: rerolling workstations, curing villagers for
discounts, building trading halls. The trades often feel detached from the rest of the game.
Other: Villages is a different villager system that could fit in vanilla. It's not meant as a "better" version,
just something else.

- **No exploits.** No vanilla trading, no free rerolls, no zombie-curing discounts, no raid farms for value.
- **Grows with you.** Nothing skips ore or gear progression. Automation is possible, but never the best way.
- **Conveniences, not power.** Every perk has a vanilla alternative that costs more time or material.
- **Show, don't count.** Almost no HUD or chat. You read a village from tier marks on its blocks, its mood face
  and how the villagers behave.

## Features

### Villages and the bell
- The **bell** is a village's anchor. Natural villages are found automatically when you come near; you can also
  found your own from any bell. Bells can be crafted (stone, stick, gold ingot).
- **Shift + right-click a bell with an empty hand** to open the village screen: residents, beds, donated
  emeralds, all 13 stations, the village's mood and renown, and a "?" tab with a short guide.
- Breaking the center bell makes the village dormant; placing a new bell inside makes it the new center.

### Stations and daily work
- **Donate** a workstation (shift + right-click it with an empty hand inside a village). One station per
  profession per village; up to three villagers work at each.
- Every morning a station asks for something ("We need"). In the evening, the workers turn what you donated into
  progress and goods. A station that got nothing makes nothing.
- Fill the progress bar and pay a milestone item to upgrade a station, up to **level III**. Higher levels unlock
  more reward slots and better goods.
- Many stations' goods are another station's needs, so hoppers can chain them together.
- **Rewards can be rerolled** for emeralds; the price doubles with each reroll and resets at sunrise.
- The village owns the level, not the block: breaking a station gives a plain block, and the next one you donate
  carries on.

<details>
<summary>All 13 stations: what they need, give and upgrade with</summary>

| Station (block) | Needs (examples) | Gives at I | Added at II | Added at III | Upgrade items II / III |
|---|---|---|---|---|---|
| Farmer (composter) | seeds, bone meal | wheat, carrots, potatoes, beetroot, sugar cane | bread, pumpkin pie, honey | golden carrots, glistering melon | iron block / nether wart |
| Fisherman (barrel) | string, sticks, kelp, fishing rod | cod, salmon | ink sacs, tropical fish, pufferfish | prismarine, nautilus shell | turtle scute / heart of the sea |
| Butcher (smoker) | coal, crops, sweet berries | cooked meat, leather, feathers, bone meal | rabbit, dried kelp blocks | rabbit stew | lead / honey block |
| Mason (stonecutter) | cobblestone, clay, sand, andesite | bricks, stone bricks, polished stone, glass panes | terracotta, resin bricks | quartz, tuff bricks, polished deepslate | amethyst block / quartz block |
| Shepherd (loom) | wheat, shears, dyes | wool, string | carpets, banners, mixed dyes | banner patterns, rare dyes | iron block / snout banner pattern |
| Armorer (blast furnace) | coal, raw iron, raw copper, lava | iron and gold nuggets, chains, bars | iron ingots, chainmail helmet & boots | chainmail chestplate & leggings | iron block / netherite scrap |
| Toolsmith (smithing table) | coal, sticks, flint, raw copper | copper tools, buckets | shears, compass, lanterns | iron tools, Efficiency I pickaxe | anvil / diamond block |
| Weaponsmith (grindstone) | gravel, coal, sticks, raw copper | copper sword & axe, flint | iron sword & axe, shield, bell | Sharpness I iron weapons | iron block / blaze rod |
| Fletcher (fletching table) | sticks, flint, feathers | arrows | spectral arrows | tipped arrows | target block / ghast tear |
| Tanner (cauldron) | leather, rabbit hide, string | item frames, bundles, leather gear | dyed leather armor | iron & gold horse armor, wolf armor | saddle / armadillo scute |
| Cleric (brewing stand) | bottles, nether wart, rotten flesh, sugar | redstone, glowstone, potions | bottles o' enchanting, lapis | stronger potions | ghast tear / rabbit's foot |
| Librarian (lectern) | sugar cane, ink sacs, feathers | books, paper, level I enchanted books | bookshelves, book and quill | chiseled bookshelves | amethyst cluster / echo shard |
| Cartographer (cartography table) | paper, glass panes, compasses | maps, glow item frames | explorer maps (villages, temples, monument…) | spyglass, globe pattern, mansion & trial chamber maps | spyglass / lodestone |

</details>

### Emeralds
- **Throw emeralds on the ground in a village.** Villagers carry them to the bell, and that evening they speed
  up every station that was supplied. The bell takes up to a stack a day.
- The **wandering trader** is the emerald market. He comes every few days, mostly buys village goods for
  emeralds and sells a few uncommon items.
- Larger emerald veins generate deep underground in every biome (new chunks only), and natural village chests
  sometimes hold emeralds or a level I enchanted book.

### Station perks
An upgraded station's block works better too. Nothing is announced; the block just behaves differently.

| Station | Level II | Level III |
|---|---|---|
| Composter | Items get a second try at adding a layer | Rotten flesh and spider eyes compost; 2 bone meal |
| Smoker | Fuel lasts 50% longer | Cooks 50% faster |
| Blast furnace | Fuel lasts 50% longer | Iron and gold gear smelts back into ingots |
| Brewing stand | Brews 50% faster | Bottle your XP into bottles o' enchanting |
| Cauldron | Dirt → mud | Concrete powder → concrete |
| Barrel | 4 rows | 5 rows |
| Stonecutter | Cuts wood | Turns stairs back into blocks |
| Smithing table | Strip a trim (material back) | Re-trim in a new material without a template |
| Grindstone | Mill cobble → gravel → sand, gravel → flint, bone → bone meal | Hone weapons and tools with flint for a temporary edge |
| Loom | 8 banner layers | 12 banner layers |
| Cartography table | One empty map makes two copies | **Cave Maps** |
| Fletching table | Fletching screen, 6 arrows per craft | 16 tipped arrows per lingering potion; **explosive arrows** |
| Lectern | A redstone pulse shows a signed book as a title card on nearby screens | Multi-page books scroll up like end credits |

Also: dispensers can fill and empty any cauldron with buckets and bottles.

**Cave Maps** map the caves around you at the depth you made them, flooding out through open space only, so
nothing behind solid rock is revealed. **Explosive arrows** explode on impact (block damage can be switched off).

### Take-home stations
Workers at a level II or III station sell their block: tier II for 12 emeralds + a plain block, tier III for 32.
A take-home keeps that tier's perks wherever you place it, drops again with its tier when broken, and shows
painted tier marks (gold for II, gold and emerald for III). Village stations show the same marks.

### Renown
Once stations reach level III, supplying them earns the village **renown**. Each rank makes that village's
take-home stations cheaper.

### Village mood
Every village has a mood: miserable, unhappy, neutral, content or abundant, shown as a face on the bell screen.
It rises with upgraded stations, iron golems, cured zombie villagers and beaten raids, and falls when villagers or
golems die or get hurt.
- Happy villages work faster and breed more easily; children play, golems hand out poppies, and abundant
  villages sometimes throw a **party** with dancing and fireworks.
- Unhappy villages grumble, work slower and shake their heads at you.

### Golems
- Villages stop summoning iron golems at one per 8 residents (at most 4), so big villages don't fill up with them.
- Villages **forgive** a player who dies or sleeps through the night, so golems don't stay angry for days.
  The village mood still remembers what happened.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** (0.163.0+26.3 or newer) and the **Other: Villages** jar in your `mods` folder.
3. On a server, every player needs the same mod (and Fabric API) on their client.

Existing worlds work: natural villages are picked up as you visit them, and their workstations become stations
with a little starting progress. Deep emerald veins and chest extras only appear in newly generated chunks.

## Configuration

`config/villagerework.json` is created on first start. It covers village size and spacing, worker caps,
requests and rewards per level, emeralds, rerolls, librarian book odds, the wandering trader, perks, explosive
arrows, lectern messages, take-home prices and renown, and village mood (including switches for parties,
fireworks and music). Edit it and run `/vr config reload`; invalid values are clamped.

The wandering trader's trades and the deep emerald veins are data files under
`data/villagerework/` and can be overridden with a datapack.

## Commands

Players don't need commands: the bell and station screens use these behind their buttons
(`/village make`, `border`, `rename`, `donate`, `disband`, `party`).

Operators have `/vr` (or `/villagerework`) for testing and admin:

| Command | Purpose |
|---|---|
| `/vr config reload\|show` | Reload or print the config |
| `/vr village list\|info\|remove\|sync` | Inspect or remove villages |
| `/vr station list\|setlevel\|fill` | Inspect stations, set levels, fill progress |
| `/vr day run\|sunrise\|status` | Run the evening work or a new morning now |
| `/vr emeralds [count]` | Preview what a day's emeralds would give |
| `/vr trader status\|due` | Trader timing; make him come now |
| `/vr prosperity [clear\|event <kind>]` | Village mood details and test events |
| `/vr renown [set <points>]` | Renown, rank and prices |
| `/vr takehome <profession> <2\|3>` | Give a take-home station |
| `/vr explosivearrows breakblocks on\|off` | Toggle explosive arrow block damage |
| `/vr scan [radius]` | List vanilla village blocks and villagers nearby |

## Compatibility

- Works alongside **Enchant Library**: the librarian's level I books unlock enchants on its shelves. Neither mod
  requires the other.
- Vanilla trading and job claiming are switched off (both can be turned back on in the config). The wandering
  trader still trades, with the mod's own trades.
- Other mods that change villager trading or professions will likely conflict.

## Known issues

- Explorer map rewards search for their structure like a vanilla cartographer, which can pause the server for a
  moment far from any mansion.
- Switching a village's party off doesn't stop a song that's already playing.

## Building from source

Requires JDK 25. Run `./gradlew build` (`gradlew.bat build` on Windows); the jar is in `build/libs/`.
`./gradlew runClient` starts a test client. Developer notes and the full test checklist are in
[`docs/development.md`](docs/development.md).

## License

See [LICENSE](LICENSE).
