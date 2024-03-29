package mindustry.game.griefprevention;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Array;
import arc.util.Log;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.type.Player;
import mindustry.entities.type.TileEntity;
import mindustry.entities.type.Unit;
import mindustry.game.EventType;
import mindustry.game.EventType.DepositEvent;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.TileChangeEvent;
import mindustry.game.EventType.WithdrawEvent;
import mindustry.gen.Call;
import mindustry.gen.Sounds;
import mindustry.net.Administration.TraceInfo;
import mindustry.net.Packets.AdminAction;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.power.ItemLiquidGenerator;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.sandbox.ItemSource;
import mindustry.world.blocks.sandbox.LiquidSource;
import mindustry.world.blocks.storage.Unloader;
import mindustry.world.blocks.storage.Vault;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.WeakHashMap;

import static mindustry.Vars.*;

public class GriefWarnings {
    private Instant nextWarningTime = Instant.now();
    public WeakHashMap<Tile, TileInfo> tileInfo = new WeakHashMap<>();
    public WeakHashMap<Player, PlayerStats> playerStats = new WeakHashMap<>();
    /** whether or not to send warnings to all players */
    public boolean broadcast = false;
    /** whether or not to be very noisy about everything */
    public boolean verbose = false;
    /** whether or not to flat out state the obvious, pissing everyone off */
    public boolean debug = false;
    /** whether or not to show the persistent tileinfo display */
    public boolean tileInfoHud = true;
    /** whether or not to automatically ban when we are 100% sure that player is griefing (eg. intentionally crashing other clients) */
    /**modified to ban certain names instead*/
    public boolean autoban = true;
    /** automatically kick filtered text & blacklisted name*/
    public boolean autokick = true; //dont ask
    /** whether to automatically perform an admin trace on player joins */
    public boolean autotrace = true;
    /** whether to log every action captured by the action log */
    public boolean logActions = false;

    public Tile lastalerttile;
    public int lastalertplayer;
    public ArrayList<String> autoBanTarget = new ArrayList<String>();
    public ArrayList<String> chatFilteredText = new ArrayList<String>();
    public ArrayList<String> ipList = new ArrayList<String>();
    public CommandHandler commandHandler = new CommandHandler();
    public FixGrief fixer = new FixGrief();
    public boolean mute;
    public Auto auto;
    public RefList refs = new RefList();
    public ActionLog actionLog = new ActionLog();
    public File file = new File("traceLog.txt");
    public BufferedWriter bw;
    public int autoCloseFile;

    {
        try {
            bw = new BufferedWriter(new FileWriter(file, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public GriefWarnings() {
        Events.on(DepositEvent.class, this::handleDeposit);
        Events.on(WithdrawEvent.class, this::handleWithdraw);
        Events.on(TileChangeEvent.class, this::handleTileChange);
        Events.on(ResetEvent.class, this::reset);
        loadSettings();
    }

    public void loadSettings() {
        broadcast = Core.settings.getBool("griefwarnings.broadcast", false);
        verbose = Core.settings.getBool("griefwarnings.verbose", false);
        debug = Core.settings.getBool("griefwarnings.debug", false);
        tileInfoHud = Core.settings.getBool("griefwarnings.tileinfohud", true);
        autoban = Core.settings.getBool("griefwarnings.autoban", false);
        autotrace = Core.settings.getBool("griefwarnings.autotrace", true);
        logActions = Core.settings.getBool("griefwarnings.logactions", false);
    }

    public void saveSettings() {
        Core.settings.put("griefwarnings.broadcast", broadcast);
        Core.settings.put("griefwarnings.verbose", verbose);
        Core.settings.put("griefwarnings.debug", debug);
        Core.settings.put("griefwarnings.tileinfohud", tileInfoHud);
        Core.settings.put("griefwarnings.autoban", autoban);
        Core.settings.put("griefwarnings.autotrace", autotrace);
        Core.settings.put("griefwarnings.logactions", logActions);
        Core.settings.save();
    }

    public boolean sendMessage(String message, boolean throttled) {
        // if (!net.active()) return false;
        //imagine having a message spam chat when it's about chat spam
        if (message.length() > maxTextLength && !mute) {
            ui.chatfrag.addMessage(
                    "[scarlet][yellow] Warnings exceeded allowed chat length!",
                    null);
            ui.chatfrag.addMessage(message, null);
            ui.chatfrag.addMessage("Message length was [accent]" + message.length(), null);
            Log.warn("[antigrief] Oversize message not sent (size " + message.length() + "): " + message);
            return false;
        }
        if (!Instant.now().isAfter(nextWarningTime) && throttled) return false;
        nextWarningTime = Instant.now().plusSeconds(1);
        if (broadcast) Call.sendChatMessage(message);
        else if (net.client()) ui.chatfrag.addMessage(message, null);
        else if (net.server()) Log.info("[antigrief] " + message);
        return true;
    }

    public boolean sendMessage(String message) {
        return sendMessage(message, true);
    }
    public void replyUnlock(String message){ ui.hudfrag.showToast(message);}
    public void replyAlert(String message){ ui.showInfo(message);}
    public void sendLocal(String message) { ui.chatfrag.addMessage(message, null); }

    public float getDistanceToCore(Unit unit, float x, float y) {
        TileEntity nearestCoreEntity = unit.getClosestCore();
        if (nearestCoreEntity == null) return Float.POSITIVE_INFINITY;
        Tile nearestCore = nearestCoreEntity.getTile();
        return Mathf.dst(x, y, nearestCore.x, nearestCore.y);
}

    public float getDistanceToCore(Unit unit, Tile tile) {
        return getDistanceToCore(unit, tile.x, tile.y);
    }

    public float getDistanceToCore(Unit unit) {
        return getDistanceToCore(unit, unit.x, unit.y);
    }

    public void reset() {
        tileInfo.clear();
        playerStats.clear();
        refs.reset();
        actionLog.reset();
        ipList.clear();
        if (auto != null) auto.reset();
        try{
            bw.flush();
        }catch(IOException e){Log.info("There was a problem saving some data pls ficx");}
        System.out.println("Reset event");
    }

    public void handleTileChange(TileChangeEvent event) {
        // if (event.tile.block() == Blocks.air) tileInfo.remove(event.tile);
    }

    public TileInfo getOrCreateTileInfo(Tile tile, boolean doLinking) {
        TileInfo info = tile.info;
        if (info == null) {
            TileInfo newInfo = new TileInfo();
            info = newInfo;
            tileInfo.put(tile, newInfo);
            tile.info = newInfo;
            if (doLinking) tile.getLinkedTiles(linked -> getOrCreateTileInfo(linked, false).doLink(newInfo));
        }
        return info;
    }

    public TileInfo getOrCreateTileInfo(Tile tile) {
        return getOrCreateTileInfo(tile, true);
    }

    public PlayerStats getOrCreatePlayerStats(Player target) {
        PlayerStats stats = target.stats;
        if (stats == null) {
            stats = new PlayerStats(target);
            playerStats.put(target, stats);
            target.stats = stats;
            refs.get(target); // create ref
            if (target == player) return stats;
            if (target.isAdmin){
                sendLocal("[purple][] " + formatPlayer(target));
            }
            if (player.isAdmin && autotrace) {
                stats.doTrace(trace -> {
                    try{
                        bw.write(target.name.replaceAll("\\[[^]]*]", "") + " " + formatTrace(trace));
                    }catch(IOException e){e.printStackTrace();Log.info("You should probably ignore that error but ask about it anyway,");}
                    sendLocal("[lime][] " + formatPlayer(target) );//Still runs trace but doesn't show. Don't like it? Too bad!
                    Log.infoTag("antigrief", "Player join: " + target.name + " (" + player.id+ ") " + formatTrace(trace));
                    //Potentially gonna spam #in-game-relay, but who cares
                    if(target.name.toLowerCase().contains("�")){
                        Call.sendChatMessage("/d <AUTO> banned: " + target.name + " " + griefWarnings.formatTrace(trace));
                        doAutoban(target, null);
                    }

                    else if(target.name.toLowerCase().contains("nigger")){
                        Call.sendChatMessage("/d <AUTO> kicked: " + target.name + " " + griefWarnings.formatTrace(trace));
                        doAutokick(target, null);
                    }
                    else if(target.name.toLowerCase().contains("nexity")){
                            Call.sendChatMessage("/d <AUTO> banned: " + target.name + " " + griefWarnings.formatTrace(trace));
                            doAutoban(target, null);
                        }
                    else{
                        for(String s: autoBanTarget){
                            if(target.name.toLowerCase().replaceAll("\\[[^]]*]", "").trim().contains(s)){
                                Call.sendChatMessage("/d <AUTO> Banned: " + target.name + " " + griefWarnings.formatTrace(trace));
                                doAutoban(target, null);
                            }
                        }

                        if (!ipList.isEmpty() && ipList.toString().contains(target.stats.trace.ip)){
                            sendLocal("[purple]SECURITY ALERT![] Latest join contains preexisting IP");
                        }
                        else if (!target.isAdmin && target.stats.trace.ip != null){
                            ipList.add(target.stats.trace.ip);
                        }


                    }


                });
            }
            else{
                sendLocal("[lime][] " + formatPlayer(target));
            }
        }
        return stats;
    }

    // these are called before the BuildBlock is placed so tile contains the previous block
    public void handleBeginBreak(Tile tile) {
        TileInfo info = getOrCreateTileInfo(tile);
        info.previousBlock = tile.block();
        info.previousRotation = tile.rotation();
        info.previousConfig = (tile.entity != null) ? tile.entity.config() : -1;
    }

    public void handleBeginPlace(Tile tile, Block result, int rotation) {
        TileInfo info = getOrCreateTileInfo(tile);
        info.previousBlock = tile.block();
        info.previousRotation = tile.rotation();
        info.previousConfig = (tile.entity != null) ? tile.entity.config() : -1;
    }

    public void handleBlockConstructProgress(Player builder, Tile tile, Block cblock, float progress, Block previous) {
        TileInfo info = getOrCreateTileInfo(tile);
        if (builder != null) info.constructedBy = builder;

        boolean didWarn = false;
        float coreDistance = getDistanceToCore(builder, tile);
        // persistent warnings that keep showing
        //this is fucking annoying
        if (coreDistance < 12 && cblock instanceof NuclearReactor && !mute) {
            String message = "[scarlet][] " + formatPlayer(builder) + " is building a reactor [stat]" +
                    Math.round(coreDistance) + "[] blocks from core. [stat]" + Math.round(progress * 100) + "%";
            sendMessage(message);
            didWarn = true;
            lastalerttile = tile;
            lastalertplayer = builder.id;
        }else if (coreDistance < 0 && cblock instanceof ItemLiquidGenerator) {
            String message = "[scarlet]WARNING[] " + formatPlayer(builder) + " is building a generator [stat]" +
                    Math.round(coreDistance) + "[] blocks from core. [stat]" + Math.round(progress * 100) + "%";
            sendMessage(message);
            didWarn = true;
        }


        // one-time block construction warnings
        if (!info.constructSeen) {
            tile.getLinkedTiles(linked -> getOrCreateTileInfo(linked, false).doLink(info));
            info.constructSeen = true;
            info.currentBlock = cblock;

            Actions.Construct action = new Actions.Construct(builder, tile);
            action.previousBlock = info.previousBlock;
            action.previousRotation = info.previousRotation;
            action.previousConfig = info.previousConfig;
            action.constructBlock = cblock;
            action.constructRotation = tile.rotation();
            actionLog.add(action);

            if (!didWarn) {
                if (cblock instanceof NuclearReactor) {
                    Array<Tile> bordering = tile.entity.proximity;
                    boolean hasCryo = false;
                    for (Tile neighbor : bordering) {
                        if (
                            neighbor.entity != null && neighbor.entity.liquids != null &&
                            neighbor.entity.liquids.current() == Liquids.cryofluid
                        ) {
                            hasCryo = true;
                            break;
                        }
                    }
                    if (!hasCryo && !mute) {
                        String message = "[orange][] " + formatPlayer(builder) +
                            " is building a reactor at " + formatTile(tile);
                        sendMessage(message, false);
						Sounds.hint.play();
						lastalerttile = tile;
                        lastalertplayer = builder.id;
                    }
                }
            }
        }
    }

    public void handleBlockConstructFinish(Tile tile, Block block, int builderId) {
        TileInfo info = getOrCreateTileInfo(tile);
        Player targetPlayer = playerGroup.getByID(builderId);
        tile.getLinkedTiles(linked -> getOrCreateTileInfo(linked, false).doLink(info));
        info.constructedBy = targetPlayer;
        info.currentBlock = block;

        if (targetPlayer != null) {
            if (!info.constructSeen) {
                Actions.Construct action = new Actions.Construct(targetPlayer, tile);
                action.previousBlock = info.previousBlock;
                action.previousRotation = info.previousRotation;
                action.previousConfig = info.previousConfig;
                action.constructBlock = tile.block();
                action.constructRotation = tile.rotation();
                actionLog.add(action);
            }

            PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
            stats.blocksConstructed++;

            if (debug) {
                sendMessage("[cyan]Debug[] " + formatPlayer(targetPlayer) + " builds [accent]" +
                        tile.block().name + "[] at " + formatTile(tile), false);
            }
        }
    }

    public void handleBlockDeconstructProgress(Player builder, Tile tile, Block cblock, float progress, Block previous) {
        TileInfo info = getOrCreateTileInfo(tile);
        if (builder != null) info.deconstructedBy = builder;

        float coreDistance = getDistanceToCore(builder, tile);

        if (!info.deconstructSeen) {
            info.deconstructSeen = true;

            Actions.Deconstruct action = new Actions.Deconstruct(builder, tile);
            action.previousBlock = info.previousBlock;
            action.previousRotation = info.previousRotation;
            action.previousConfig = info.previousConfig;
            actionLog.add(action);
        }
            if (coreDistance < 6 && cblock instanceof Vault && !mute) {
                String message = "[scarlet][] " + formatPlayer(builder) + " is deconstructing a core vault!";
                sendMessage(message, true); //not sure if the spam is needed, this is useful both ways
                lastalerttile = tile;
                lastalertplayer = builder.id;
                }
            }


    public void handleBlockDeconstructFinish(Tile tile, Block block, int builderId) {
        // this runs before the block is actually removed
        TileInfo info = getOrCreateTileInfo(tile);
        Player targetPlayer = playerGroup.getByID(builderId);
        if (targetPlayer != null) info.deconstructedBy = targetPlayer;
        info.reset();
        tile.getLinkedTiles(linked -> getOrCreateTileInfo(linked, false).unlink());

        if (targetPlayer != null) {
            if (!info.deconstructSeen) {
                Actions.Deconstruct action = new Actions.Deconstruct(targetPlayer, tile);
                action.previousBlock = info.previousBlock;
                action.previousRotation = info.previousRotation;
                action.previousConfig = info.previousConfig;
                actionLog.add(action);
            }

            PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
            stats.blocksBroken++;

            if (debug) {
                sendMessage("[cyan]Debug[] " + targetPlayer.name + "[white] ([stat]#" + builderId +
                        "[]) deconstructs [accent]" + tile.block().name + "[] at " + formatTile(tile), false);
            }
        }
    }

    //no idea what to make of this
    public Player getNearestPlayerByLocation(float x, float y) {
        // grab every player in a 10x10 area and then find closest
        Array<Player> candidates = playerGroup.intersect(x - 5, y - 5, 10, 10);
        if (candidates.size == 0) return null;
        if (candidates.size == 1) return candidates.first();
        if (candidates.size > 1) {
            float nearestDistance = Float.MAX_VALUE;
            Player nearestPlayer = null;
            for (Player player : candidates) {
                float distance = Mathf.dst(x, y, player.x, player.y);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }
            return nearestPlayer;
        }
        return null; // this should be impossible
    }

    public void handleDeposit(DepositEvent event) {
        Player targetPlayer = event.player;
        Tile tile = event.tile;
        Item item = event.item;
        int amount = event.amount;
        if (targetPlayer == null) return;
        if (verbose) {
            sendMessage("[green]Verbose[] " + targetPlayer.name + "[white] ([stat]#" + targetPlayer.id +
                "[]) transfers " + amount + " " + item.name + " to " + tile.block().name + " " + formatTile(tile), false);
        }

        Actions.DepositItems action = new Actions.DepositItems(targetPlayer, tile);
        action.item = item;
        action.amount = amount;
        actionLog.add(action);

        if (item.equals(Items.thorium) && tile.block() instanceof NuclearReactor && !mute) {
            String message = "[orange][] " + targetPlayer.name + "[white] ([stat]#" +
                targetPlayer.id + "[]) transfers  to a reactor. " + formatTile(tile);
            sendMessage(message);
			Sounds.hint.play();
			lastalerttile = tile;
            lastalertplayer = targetPlayer.id;
        }
        //Imagine having warnings for blast transfers. Like, who cares?
    }

    public void handleWithdraw(WithdrawEvent event) {
        Actions.WithdrawItems action = new Actions.WithdrawItems(event.player, event.tile);
        action.item = event.item;
        action.amount = event.amount;
        actionLog.add(action);
    }

    public void handlePlayerEntitySnapshot(Player targetPlayer) {
        // System.out.println("received entity snapshot for " + targetPlayer.name + "#" + targetPlayer.id);
        // System.out.println("entity previous: " + playerStats.get(targetPlayer));
        PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
        if (debug) {
            sendMessage("[cyan]Debug[] Player snapshot: " + targetPlayer.name + "[white] ([stat]#" + targetPlayer.id + "[])", false);
        }
    }

    public void handlePlayerDisconnect(int playerId) {
        Player targetPlayer = playerGroup.getByID(playerId);
        // System.out.println("player disconnect: " + targetPlayer.name + "#" + targetPlayer.id);
        PlayerStats stats = playerStats.get(targetPlayer);
        if (stats != null) {
            stats.gone = true;
            String traceString = "";
            if (stats.trace != null) traceString = " \n" + formatTrace(stats.trace);
            sendLocal("[red][] " + formatPlayer(targetPlayer) );
            //if (!targetPlayer.isAdmin && targetPlayer.stats.trace.ip != null){
            if (!targetPlayer.isAdmin) {
                try {
                    ipList.remove(stats.trace.ip);
                } catch (Exception e) {
                    sendLocal("[red][] error removing IP");
                }
            }
        }
    }

    public void handleWorldDataBegin() {
        playerStats.clear();
    }

    public String formatPlayer(Player target) {
        String playerString;
        if (target != null) {
            int ref = refs.get(target);
            playerString = target.name + "[white] ([stat]#" + target.id + " &" + ref + "[])";
        } else {
            playerString = "[lightgray]unknown[]";
        }
        return playerString;
    }

    public String formatColor(Color color) {
        return "[#" + Integer.toHexString(((int)(255 * color.r) << 24) | ((int)(255 * color.g) << 16) | ((int)(255 * color.b) << 8)) + "]";
    }

    public String formatColor(Color color, String toFormat) {
        return formatColor(color) + toFormat + "[]";
    }

    public String formatItem(Item item) {
        if (item == null) return "(none)";
        return formatColor(item.color, item.name);
    }

    public String formatTile(Tile tile) {
        if (tile == null) return "(none)";
        return "(" + tile.x + ", " + tile.y + ")";
    }

    public String formatRatelimit(Ratelimit rl) {
        return (rl.check() ? "exceeded" : "not exceeded") + " (" + rl.events() + " events in " + rl.findTime + " ms)";
    }

    public String formatRatelimit(Ratelimit rl, Player source) {
        return (rl.check() ? "[#d899ff]Alert! []" : "not exceeded") + formatPlayer(source) + " (" + rl.events() + ")";
    }

    public String formatTrace(TraceInfo trace) {
        if (trace == null) return "(trace not available)";
        //dingdong, a better alternative to "true"/"false"
        String MobileOrDesktop;
        if(trace.mobile){
            MobileOrDesktop = "Mobile";
        }else{
            MobileOrDesktop = "Desktop";
        }
        return trace.ip + " /// " + trace.uuid + " /// " + MobileOrDesktop;
    }

    public String formatUUID(TraceInfo trace){
        if (trace.uuid == null) return "(UUID untraceable)";
        return trace.uuid;
    }
    public String formatIP(TraceInfo trace){
        if (trace.ip == null) return "(IP untraceable)";
        return trace.ip;
    }

    public void handlePowerGraphSplit(Player targetPlayer, Tile tile, PowerGraph oldGraph, PowerGraph newGraph1, PowerGraph newGraph2) {
        int oldGraphCount = oldGraph.all.size;
        int newGraph1Count = newGraph1.all.size;
        int newGraph2Count = newGraph2.all.size;

        if (Math.min(oldGraphCount - newGraph1Count, oldGraphCount - newGraph2Count) > 100 && !mute) {
            //who cares about what the graph says? if its more than 100 then its significant
            sendMessage("[yellow][] Power split by " + formatPlayer(targetPlayer) + " " + formatTile(tile));
			//i need better sounds, this is slightly annoying
            Sounds.eSwing.play();
            lastalerttile = tile;
            lastalertplayer = targetPlayer.id;
        }
    }

    public void handleBlockBeforeConfigure(Tile tile, Player targetPlayer, int value) {
        TileInfo info = getOrCreateTileInfo(tile);
        if (targetPlayer != null) {
            info.logInteraction(targetPlayer);

            PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
            stats.configureCount++;
            // don't trip on auto item source config
            if (tile.block() != Blocks.itemSource && stats.configureRatelimit.get() && !mute) {
                stats.configureRatelimit.nextTick(rl -> sendMessage("[#d899ff]Ratelimit[] " + formatRatelimit(rl, targetPlayer)));
				Sounds.hint.play();
                lastalerttile = null;
                lastalertplayer = targetPlayer.id;
            }
        }

        Block block = tile.block();
        if (block.posConfig) {
            Actions.ConfigurePositional action = new Actions.ConfigurePositional(targetPlayer, tile);
            action.targetBlock = tile.block();
            action.beforeConfig = tile.entity.config();
            action.afterConfig = value;
            actionLog.add(action);
        } else if (block instanceof ItemSource || // excuse hard coded values
                block instanceof Sorter ||
                block instanceof Unloader ||
                block instanceof LiquidSource) {
            Actions.ConfigureItemSelect action = new Actions.ConfigureItemSelect(targetPlayer, tile);
            action.targetBlock = tile.block();
            action.beforeConfig = tile.entity.config();
            action.afterConfig = value;
            actionLog.add(action);
        } else if (block instanceof PowerNode) {
            Actions.ConfigurePowerNode action = new Actions.ConfigurePowerNode(targetPlayer, tile);
            action.disconnect = tile.entity.power.links.contains(value); // should never be null, if not then oh no
            action.other = value;
            actionLog.add(action);
        }
    }

    public void handleRotateBlock(Player targetPlayer, Tile tile, boolean direction) {
        TileInfo info = getOrCreateTileInfo(tile);
        if (targetPlayer != null) {
            info.lastRotatedBy = targetPlayer;
            info.logInteraction(targetPlayer);

            PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
            stats.rotateCount++;
            //lol imagine seeing this
            if (stats.rotateRatelimit.get() && !mute) {
                stats.rotateRatelimit.nextTick(rl -> sendMessage("[#d899ff]Rotate[] " + formatRatelimit(rl, targetPlayer)));
				Sounds.hint.play();
                lastalerttile = null;
                lastalertplayer = targetPlayer.id;
            }
        }

        if (verbose) {
            sendMessage("[green]Verbose[] " + formatPlayer(targetPlayer) + " rotates " +
                tile.block().name + " at " + formatTile(tile));
        }

        Actions.RotateBlock action = new Actions.RotateBlock(targetPlayer, tile);
        action.targetBlock = tile.block();
        action.beforeRotation = tile.rotation();
        action.direction = direction;
        actionLog.add(action);
    }

    public void handleTileTapped(Player target, Tile tile) {
        /* this is not a good idea
        Actions.TapTile action = new Actions.TapTile(target, tile);
        actionLog.add(action);
        */
    }

    public void handleThoriumReactorHeat(Tile tile, float heat) {
        if (heat > 0.5f && tile.interactable(player.getTeam()) && !mute) {
            //STOP SPAMMING THE FUCKING CHAT
            //mute ftw
            sendMessage("[orange][]  " + formatTile(tile) + " is overheating!");
            lastalerttile = tile;
        }
    }

    public boolean doAutoban(Player targetPlayer, String reason) {
        if (player.isAdmin && targetPlayer != null && autoban) {
            Call.onAdminRequest(targetPlayer, AdminAction.ban);
            String message = "[purple][AUTOBAN][] Banning player: " + formatPlayer(targetPlayer); //made this sexier
            if (reason != null) message += " (" + reason + ")";
            sendLocal(message);
            return true;
        } else return false;
    }
    public boolean doAutokick(Player targetPlayer, String reason) {
        if (player.isAdmin && targetPlayer != null && autokick) {
            Call.onAdminRequest(targetPlayer, AdminAction.kick);
            String message = "[sky][AUTOKICK][] Kicking player: " + formatPlayer(targetPlayer);
            if (reason != null) message += " (" + reason + ")";
            sendLocal(message);
            return true;
        } else return false;
    }

    public TileInfo getTileInfo(Tile tile) {
        TileInfo info = tileInfo.get(tile);
        if (info != null && info.link != null) info = info.link;
        return info;
    }

    public void handleMessageBlockText(Player targetPlayer, Tile tile, String text) {
        // TODO: maybe log what the text said?
        if (targetPlayer == null) return;
        TileInfo info = getOrCreateTileInfo(tile);
        info.logInteraction(targetPlayer);
    }

    /**
     * AdminAction trace result hook
     * @param target
     * @param info
     * @return True if trace result ui should be inhibited, false otherwise
     */
    public boolean handleTraceResult(Player target, TraceInfo info) {
        PlayerStats stats = playerStats.get(target);
        if (stats == null) return false;
        boolean requested = stats.autoTraceRequested;
        stats.handleTrace(info);
        return requested;
    }

    public void loadComplete() {
        auto = new Auto();
    }

    //what are you looking for? >:(
}
