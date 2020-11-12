package mindustry.game.griefprevention;

import arc.Core;
import arc.math.geom.Vec2;
import arc.struct.Array;

import java.time.Instant;
import java.util.ArrayList;

import arc.func.Cons;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Strings.*;
import mindustry.Vars;
import mindustry.entities.type.Player;
import mindustry.game.griefprevention.Actions.Action;
import mindustry.game.griefprevention.Actions.TileAction;
import mindustry.game.griefprevention.Actions.UndoResult;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.net.Packets.AdminAction;
import mindustry.type.Item;
import mindustry.world.Block;  // fuck this import
import mindustry.world.Build;
import mindustry.game.Team;
import mindustry.game.Teams.BrokenBlock; // fuck all of these imports too
import mindustry.game.Teams.TeamData;   //fuck you for reading this too
import mindustry.entities.traits.BuilderTrait.BuildRequest; //FUCK EVERYTHING
import mindustry.world.Tile;
import mindustry.world.blocks.BlockPart;
import org.mozilla.javascript.*;
import sun.rmi.transport.Target;

import static mindustry.Vars.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

// introducing the worst command system known to mankind
//im a serbian war criminal
public class CommandHandler {
    public static class CommandContext {
        public List<String> args;

        public CommandContext(List<String> args) {
            this.args = args;
        }
    }

    private Instant nextRunTime = Instant.now();
    public ContextFactory scriptContextFactory = new ContextFactory();
    public Context scriptContext;
    public Scriptable scriptScope;
    public HashMap<String, Cons<CommandContext>> commands = new HashMap<>();

    public CommandHandler() {
        addCommand("fixpower", this::fixPower);
        addCommand("fp", this::fixPower);
        addCommand("verbose", settingsToggle("verbose", "verbose logging", v -> griefWarnings.verbose = v));
        addCommand("debug", settingsToggle("debug", "debug logging", v -> griefWarnings.debug = v));
        addCommand("spam", settingsToggle("spam", "verbose and debug logging", v -> {
            griefWarnings.verbose = v;
            griefWarnings.debug = v;
        }));
        addCommand("broadcast", settingsToggle("broadcast", "broadcast of messages", v -> griefWarnings.broadcast = v));
        addCommand("tileinfo", this::tileInfo);
        addCommand("players", this::players);
        addCommand("p", this::players);
        addCommand("votekick", this::votekick);
        addCommand("tileinfohud", settingsToggle("tileinfohud", "tile information hud", v -> griefWarnings.tileInfoHud = v));
        addCommand("autoban", settingsToggle("autoban", "automatic bans", v -> griefWarnings.autoban = v));
        addCommand("autotrace", settingsToggle("autotrace", "automatic trace", v -> griefWarnings.autotrace = v));
        addCommand("auto", this::auto);
        addCommand("a", this::auto);
        //bad idea
        addCommand("nextwave", this::nextwave);
        addCommand("playerinfo", this::playerInfo);
        addCommand("pi", this::playerInfo); // playerinfo takes too long to type
        addCommand("eval", this::eval);
        addCommand("freecam", createToggle("freecam", "free movement of camera", v -> griefWarnings.auto.setFreecam(v)));
        addCommand("show", this::show);
        addCommand("s", this::show);
        addCommand("logactions", settingsToggle("logactions", "log all actions captured by the action log", v -> griefWarnings.logActions = v));
        addCommand("getactions", this::getactions);
        addCommand("ga", this::getactions);
        addCommand("undoactions", this::undoactions);
        addCommand("ua", this::undoactions);
        addCommand("copy", this::copy);
        addCommand("copyga", this::copyga);
        //addCommand ("report", this::report);
        addCommand("rebuild", this::rebuild);
        addCommand("lastalert", this::lastalert);
        addCommand("la", this::lastalert);
        addCommand("mute", this::mute);
        addCommand("unmute", this::unmute);
        /**     DONT USE UNLESS IF ABSOLUTELY NECESSARY     */
        /**     DONT USE UNLESS IF ABSOLUTELY NECESSARY     */
        /**     DONT USE UNLESS IF ABSOLUTELY NECESSARY     */
        addCommand("blacklist", this::blacklist);
        addCommand("autokick", settingsToggle("autokick", "automatic kicks", v -> griefWarnings.autokick = v));
        addCommand("chatfilter", this::chatfilter);
        /**     DONT USE UNLESS IF ABSOLUTELY NECESSARY     */
        /**     DONT USE UNLESS IF ABSOLUTELY NECESSARY     */
        /**     DONT USE UNLESS IF ABSOLUTELY NECESSARY     */
        addCommand("uuid", this::uuid);
        addCommand("u", this::uuid);
        addCommand("lastuuid", this::lastuuid);
        addCommand("lu", this::lastuuid);
        addCommand("clear", this::clear); //sometimes you just wanna clear chat
        addCommand("mapinfo", this::mapinfo);


        // mods context not yet initialized here
        scriptContext = scriptContextFactory.enterContext();
        scriptContext.setOptimizationLevel(9);
        scriptContext.getWrapFactory().setJavaPrimitiveWrap(false);
        scriptScope = new ImporterTopLevel(scriptContext);

        try {
            scriptContext.evaluateString(scriptScope, Core.files.internal("scripts/global.js").readString(), "global.js", 1, null);
        } catch (Throwable ex) {
            Log.err("global.js load failed", ex);
        } finally {
            Context.exit();
        }
    }

    public String runConsole(String text) {
        Context prevContext = Context.getCurrentContext();
        if (prevContext != null) Context.exit();
        Context ctx = scriptContextFactory.enterContext(scriptContext);
        try {
            Object o = ctx.evaluateString(scriptScope, text, "console.js", 1, null);
            if (o instanceof NativeJavaObject) {
                o = ((NativeJavaObject) o).unwrap();
            }
            if (o instanceof Undefined) {
                o = "undefined";
            }
            return String.valueOf(o);
        } catch (Throwable t) {
            Log.err("Script error", t);
            return t.toString();
        } finally {
            Context.exit();
            if (prevContext != null) platform.enterScriptContext(prevContext);
        }
    }

    public void addCommand(String name, Cons<CommandContext> handler) {
        commands.put(name, handler);
    }

    public void reply(String message) {
        ui.chatfrag.addMessage(message, null);
    }
    public void replyAlert(String message){ ui.showInfo(message);}
    public void replyUnlock(String message){ ui.hudfrag.showToast(message);}
    public void areAlertsMuted(String message){ ui.hudfrag.setHudText(message);}
    public void clear(boolean clear){ui.chatfrag.clearMessages();}

    public boolean runCommand(String message) {
        if (!message.startsWith("/")) return false;
        String[] args = message.split(" ");
        args[0] = args[0].substring(1);
        Cons<CommandContext> command = commands.get(args[0].toLowerCase());
        if (command == null) return false;
        command.get(new CommandContext(Arrays.asList(args)));
        return true;
    }

    /**
     * Reconnect every power node to everything it can connect to, intended to
     * be used after power griefing incidents.
     * If "redundant" is present as an argument, connect the block even if it is
     * already part of the same power graph.
     */
    public void clear(CommandContext ctx){
        clear(true);
    }

    public void fixPower(CommandContext ctx) {
        boolean redundant = ctx.args.contains("redundant"); //bad!bad!bad!
        griefWarnings.fixer.fixPower(redundant);
        reply("[green]Done");
    }

    public Cons<CommandContext> createToggle(String name, String description, Cons<Boolean> consumer) {
        return ctx -> {
            if (ctx.args.size() < 2) {
                reply("[orange]Not enough arguments");
                reply("Usage: " + name + " <on|off>");
                return;
            }
            switch (ctx.args.get(1).toLowerCase()) {
            case "on":
            case "true":
                consumer.get(true);
                reply("Enabled " + description);
                break;
            case "off":
            case "false":
                consumer.get(false);
                reply("Disabled " + description);
                break;
            default:
                reply("[orange]Not enough arguments");
                reply("Usage: " + name + " <on|off>");
            }
        };
    }

    public Cons<CommandContext> settingsToggle(String name, String description, Cons<Boolean> consumer) {
        return createToggle(name, description, v -> {
            consumer.get(v);
            griefWarnings.saveSettings();
        });
    }

    public Array<String> tileInfo(Tile tile) {
        TileInfo info = griefWarnings.tileInfo.get(tile);
        Array<String> out = new Array<>();
        out.add(griefWarnings.formatTile(tile));
        Block currentBlock = tile.block();
        if (currentBlock == null) {
            out.add("[yellow]Nonexistent block");
            return out;
        }
        if (currentBlock instanceof BlockPart) currentBlock = tile.link().block();
        out.add("[sky]" + currentBlock.name + "[]");
        out.add("[#" + tile.getTeam().color + "]" + tile.getTeam() + "[]");
        if (info == null) {
            return out;
        }
        Block previousBlock = info.previousBlock;
        Player deconstructedBy = info.deconstructedBy;
        if (info.link != null) info = info.link;
        out.add("Placed by: " + griefWarnings.formatPlayer(info.constructedBy));
        out.add("Removed by: " + griefWarnings.formatPlayer(deconstructedBy));
        if (previousBlock != null) out.add("Previous: " + previousBlock.name);
        out.add("[accent] []Configs:[accent]" + info.configureCount + "[]");
        if (info.interactedPlayers.size > 0) {
            for (Player player : info.interactedPlayers.iterator()) {
                out.add("  - " + griefWarnings.formatPlayer(player));
            }
        }
        if (info.lastInteractedBy != null)
            out.add("Last [accent][] by: " + griefWarnings.formatPlayer(info.lastInteractedBy));
        return out;
    }

    /**
     * Get stored information for the tile under the cursor
     */
    public void tileInfo(CommandContext ctx) {
        Tile tile = getCursorTile();
        if (tile == null) {
            reply("cursor is not on a tile");
            return;
        }
        Array<String> out = tileInfo(tile);
        if (ctx.args.contains("send")) {
            for (String line : out) griefWarnings.sendMessage(line, false);
        } else {
            reply("====================");
            reply(String.join("\n", out));
        }
    }

    public Tile getCursorTile() {
        Vec2 vec = Core.input.mouseWorld(Core.input.mouseX(), Core.input.mouseY());
        return world.tile(world.toTile(vec.x), world.toTile(vec.y));
    }

    public void mapinfo(CommandContext ctx){
        float calculatedMapUpTime = ((state.wave * (state.rules.waveSpacing / 60) + (state.rules.waveSpacing / 60)) / 60);
        if (!state.rules.pvp) {
            reply("[pink]Time between waves:[] [lime]" + state.rules.waveSpacing / 60 + " seconds[]");
        }
        reply("[pink]Core respawn time:[] [lime]" + state.rules.respawnTime / 60 + " seconds[]");
        reply("[pink]Player health:[] [lime]" + state.rules.playerHealthMultiplier + "x[]");
        reply("[pink]Player damage:[] [lime]" + state.rules.playerDamageMultiplier + "x[]");
        reply("[pink]Banned blocks:[] " + state.rules.bannedBlocks.toString().replaceAll("[{}-]", " "));
        reply("[pink]Build cost:[] [lime]" + state.rules.buildCostMultiplier + "x[]");
        reply("[pink]Deconstruct refund:[] [lime]" + state.rules.deconstructRefundMultiplier + "x[]");
        if (calculatedMapUpTime < 60) {
            reply("[pink]Map uptime:[] [lime]" + calculatedMapUpTime + " minutes[]");
        }else {
            reply("[pink]Map uptime:[] [lime]" + calculatedMapUpTime / 60 + " hours[] or [lime]" + calculatedMapUpTime + " minutes[]");
        }
    }

    /**
     * Get list of all players and their ids
     */
    //had to make some changes cuz yes
    public void players(CommandContext ctx) {
        reply("Players:");
        StringBuilder response = new StringBuilder();
        for (Player target : playerGroup.all()) {
            response.append("\n[cyan][] " + griefWarnings.formatPlayer(target));
            PlayerStats stats = griefWarnings.playerStats.get(target);
            if (stats != null && stats.trace != null) {
                response.append("\n[yellow]")
                        .append(griefWarnings.formatTrace(stats.trace) + "[]\n[pink]=========================================================[]");
            }
        }
        reply(response.toString());
    }
    public void uuid(CommandContext ctx){
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        String commandName = ""; 

        if (name.contains("--")){
            String[] parts = name.split(" --");
            name = parts[0]; 
            commandName = parts[1];
        }
        PlayerStats stats = getStats(name);
        if (stats == null) {
            reply("[orange]Not found");
            return;
        }
        Player target = stats.wrappedPlayer.get();
        if (target == null) {
            reply("[orange]PlayerStats weakref gone?");
            return;
        }
        if (stats.trace == null) {
            reply("[scarlet]UUID untraceable");
        }
        if (commandName.contains("b")){
            Core.app.setClipboardText("ban " + griefWarnings.formatUUID(stats.trace) + " Griefing");
            replyUnlock("Copied ban UUID of: " + target.name);
        }
        else if (commandName.contains("l")){
            Core.app.setClipboardText("lookup " + griefWarnings.formatUUID(stats.trace));
            replyUnlock("Copied lookup UUID of: " + target.name);
        }
        else if (commandName.contains("k")){
            Core.app.setClipboardText("kick " + griefWarnings.formatUUID(stats.trace));
            replyUnlock("Copied kick UUID of: " + target.name);
        }
        else {
            Core.app.setClipboardText(griefWarnings.formatUUID(stats.trace));
            replyUnlock("Copied UUID of: " + target.name);
        }
    }

    public void lastuuid(CommandContext ctx){
        String name = "#" + griefWarnings.lastalertplayer;
        PlayerStats stats = getStats(name);
        if (stats == null) {
            reply("[orange]Not found" + name);
            return;
        }
        Player target = stats.wrappedPlayer.get();
        if (target == null) {
            reply("[orange]PlayerStats weakref gone?");
            return;
        }
        if (stats.trace == null) {
            reply("[scarlet]UUID untraceable");
        }
        if (ctx.args.contains("b")){
            Core.app.setClipboardText("ban " + griefWarnings.formatUUID(stats.trace) + " grief");
            replyUnlock("Copied ban UUID of: " + target.name);
        }
        else if (ctx.args.contains("l")){
            Core.app.setClipboardText("lookup " + griefWarnings.formatUUID(stats.trace));
            replyUnlock("Copied lookup UUID of: " + target.name);
        }
        else if (ctx.args.contains("k")){
            Core.app.setClipboardText("kick " + griefWarnings.formatUUID(stats.trace));
            replyUnlock("Copied kick UUID of: " + target.name);
        }
        else {
            Core.app.setClipboardText(griefWarnings.formatUUID(stats.trace));
            replyUnlock("Copied UUID of: " + target.name);
        }

    }

    public void copy(CommandContext ctx) {
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        PlayerStats stats = getStats(name);
        if (stats == null) {
            reply("[orange]Not found");
            return;
        }
        Player target = stats.wrappedPlayer.get();
        if (target == null) {
            reply("[orange]PlayerStats weakref gone?");
            return;
        }
        Core.app.setClipboardText(target.name);
        String cr = "[green]Copied![]";
        reply(cr);

    }
    //I'm a headmod. This is pointless
    /*public void report(CommandContext ctx) {
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        PlayerStats stats = getStats(name);
        if (stats == null) {
            reply("[orange]Not found");
            return;
        }
        Player target = stats.wrappedPlayer.get();
        if (target == null) {
            reply("[orange]PlayerStats weakref gone?");
            return;
        }
        String cr = "Sent!";
        Call.sendChatMessage("/d [TEST] " + griefWarnings.actionLog.getActions(target));

    } */

    /**
     * Get information about a player
     */
    public void playerInfo(CommandContext ctx) {
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        PlayerStats stats = getStats(name);
        if (stats == null) {
            reply("[orange]Not found");
            return;
        }
        Player target = getPlayer(name);
        if (target == null) {
            reply("[orange]Player not found");
            return;
        }
        Core.app.setClipboardText(griefWarnings.formatTrace(stats.trace));
        //added colors cuz yeah
        //also pretty sure deleted some stuff as well
        String r = "[green]====================[]\n" +
                "[pink]Player[] " + griefWarnings.formatPlayer(target) + "\n" +
                "[pink]gone:[] " + stats.gone + "\n" +
                "[pink]position:[] (" + target.getX() + ", " + target.getY() + ")\n" +
                "[pink]trace:[] " + griefWarnings.formatTrace(stats.trace) + "\n" +
                "[pink]blocks constructed:[] " + stats.blocksConstructed + "\n" +
                "[pink]blocks broken:[] " + stats.blocksBroken + "\n" +
                "[pink]configure count:[] " + stats.configureCount + "\n" +
                "[sky]Player trace copied to clipboard[][goldenrod] (Requires /autotrace on)";
        reply(r);
    }
    /**
     * Get player by either id or full name
     */
    public Player getPlayer(String name) {
        Player target;
        if (name.startsWith("&")) {
            int ref;
            try {
                ref = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ex) {
                ref = -1;
            }
            target = griefWarnings.refs.get(ref);
        } else
            if (name.startsWith("#")) {
                int id;
                try {
                    id = Integer.parseInt(name.substring(1));
                } catch (NumberFormatException ex) {
                    id = -1;
                }
                target = playerGroup.getByID(id);
            } else {
                target = playerGroup.find(p -> p.name.replaceAll("\\[[^]]*]", "").replaceAll("[\uE800\uE813\uE809\uE80F\uE814\uE828]", "").trim().toLowerCase().startsWith(((name))));
            }
        return target;
    }

    public Tile findTile(String a, String b) {
        int x;
        int y;
        try {
            x = Integer.parseInt(a);
            y = Integer.parseInt(b);
        } catch (NumberFormatException ex) {
            return null;
        }
        return world.tile(x, y);
    }

    /**
     * Get information on player, including historical data
     */
    public PlayerStats getStats(String name) {
        if (name.startsWith("&")) {
            int ref;
            try {
                ref = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ex) {
                return null;
            }
            Player target = griefWarnings.refs.get(ref);
            if (target.stats != null) return target.stats;
            else return griefWarnings.getOrCreatePlayerStats(target);
        } else
            if (name.startsWith("#")) {
                int id;
                try {
                    id = Integer.parseInt(name.substring(1));
                } catch (NumberFormatException ex) {
                    return null;
                }
                for (Entry<Player, PlayerStats> e : griefWarnings.playerStats.entrySet()) {
                    if (e.getKey().id == id) return e.getValue();
                }
            } else {
                for (Entry<Player, PlayerStats> e : griefWarnings.playerStats.entrySet()) {
                    if (e.getKey().name.replaceAll("\\[[^]]*]", "").replaceAll("[\uE800\uE813\uE809\uE80F\uE814\uE828]", "").trim().toLowerCase().startsWith(name)) return e.getValue();
                }
            }
        return null;
    }

    /**
     * Votekick overlay to allow /votekick using ids when prefixed by #
     */
    //maybe I'll put contain() here someday
    public void votekick(CommandContext ctx) {
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size())).toLowerCase();
        Player target = getPlayer(name);
        if (target == null) {
            reply("[orange]Player not found!");
            return;
        }
        reply("[cyan]Votekicking player:[] " + griefWarnings.formatPlayer(target));
        Call.sendChatMessage("/votekick " + target.name);
    }

    //doesn't work at all in multiplayer. someone fix pls
    public void rebuild(CommandContext ctx) {
        Team team = player.getTeam();
        TeamData data = state.teams.get(team);
        if (data.brokenBlocks.isEmpty()) {
            reply("Broken blocks queue is empty");
            return;
        }
        for (BrokenBlock broken : data.brokenBlocks) {
            if (Build.validPlace(team, broken.x, broken.y, content.block(broken.block), broken.rotation)) {
                Block block = content.block(broken.block);
                reply("Adding block " + block.name + " at (" + broken.x + ", " + broken.y + ")");
                player.buildQueue().addLast(new BuildRequest(broken.x, broken.y, broken.rotation, block).configure(broken.config));
            }
        }
        reply("Added rebuild to build queue");
    }


    /**
     * Control the auto mode
     */
    public void mute(CommandContext ctx){
        griefWarnings.mute = true;
        replyUnlock("[sky]Muted");
        ui.hudfrag.toggleHudText(true);
        areAlertsMuted("[yellow]MUTE: ON");
    }
    public void unmute(CommandContext ctx){
        griefWarnings.mute = false;
        replyUnlock("[sky]Unmuted");
        ui.hudfrag.toggleHudText(false);
    }

    public void auto(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("[orange]Not enough arguments");
            reply("Usage: auto <on|off|cancel|gotocore|gotoplayer|goto|distance|itemsource|dumptarget|pickuptarget>");
            return;
        }
        Auto auto = griefWarnings.auto;
        switch (ctx.args.get(1).toLowerCase()) {
        case "on":
            auto.enabled = true;
            reply("enabled auto mode");
            break;
        case "off":
            auto.enabled = false;
            reply("disabled auto mode");
            break;
        case "gotocore":
            Tile core = player.getClosestCore().getTile();
            auto.gotoTile(core, 50f);
            reply("[cyan]going to tile[] " + griefWarnings.formatTile(core));
            break;
        case "goto": {
            if (ctx.args.size() < 4) {
                reply("[orange]Not enough arguments");
                reply("Usage: auto goto [persist] <x> <y>");
                return;
            }
            int argStart = 2;
            boolean persist = false;
            String additional = ctx.args.get(argStart).toLowerCase();
            if (additional.equals("p")) {
                argStart++;
                persist = true;
            }
            Tile tile = findTile(ctx.args.get(argStart), ctx.args.get(argStart + 1));
            if (tile == null) {
                reply("[orange]Invalid tile");
                return;
            }
            auto.gotoTile(tile, persist ? 0f : 50f);
            auto.persist = persist;
            reply("[cyan]going to tile[] " + griefWarnings.formatTile(tile));
            break;
        }
        //ignore the intelliJ warnings pls
        case "1": {
            if (ctx.args.size() < 4) {
                reply("[orange]Not enough arguments");
                reply("Usage: auto goto [persist] <x> <y>");
                return;
            }
            int argStart = 2;
            boolean persist = false;
            String additional = ctx.args.get(argStart).toLowerCase();
            if (additional.equals("p")) {
                argStart++;
                persist = true;
            }
            Tile tile = findTile(ctx.args.get(argStart), ctx.args.get(argStart + 1));
            if (tile == null) {
                reply("[orange]Invalid tile");
                return;
            }
            auto.gotoTile(tile, persist ? 0f : 50f);
            auto.persist = persist;
            reply("[cyan]going to tile[] " + griefWarnings.formatTile(tile));
            break;
        }
        case "gotoplayer": {
            if (ctx.args.size() < 3) {
                reply("[orange]Not enough arguments");
                reply("Usage: auto gotoplayer [follow|assist|undo] <player>");
                return;
            }
            int nameStart = 2;
            boolean follow = false;
            boolean assist = false;
            boolean undo = false;
            float distance = 100f;
            String additional = ctx.args.get(nameStart).toLowerCase();
            switch (additional) {
            case "follow":
                nameStart++;
                follow = true;
                break;
            case "assist":
                nameStart++;
                assist = true;
                distance = 50f;
                break;
            case "undo":
                nameStart++;
                undo = true;
                distance = 50f;
                break;
            }
            String name = String.join(" ", ctx.args.subList(nameStart, ctx.args.size()));
            Player target = getPlayer(name);
            if (target == null) {
                reply("[orange]No such player");
                return;
            }
            if (assist) auto.assistEntity(target, distance);
            else
                if (undo) auto.undoEntity(target, distance);
                else auto.gotoEntity(target, distance, follow);
            reply("[cyan]going to player:[] " + griefWarnings.formatPlayer(target));
            break;
        }
        case "2": {
            if (ctx.args.size() < 3) {
                reply("[orange]Not enough arguments");
                reply("Usage: auto gotoplayer [follow|assist|undo] <player>");
                return;
            }
            int nameStart = 2;
            boolean follow = false;
            boolean assist = false;
            boolean undo = false;
            float distance = 100f;
            String additional = ctx.args.get(nameStart).toLowerCase();
            switch (additional) {
            case "f":
                nameStart++;
                follow = true;
                break;
            case "a":
                nameStart++;
                assist = true;
                distance = 50f;
                break;
            case "u":
                nameStart++;
                undo = true;
                distance = 50f;
                break;
            }
            String name = String.join(" ", ctx.args.subList(nameStart, ctx.args.size()));
            Player target = getPlayer(name);
            if (target == null) {
                reply("[orange]No such player");
                return;
            }
            if (assist) auto.assistEntity(target, distance);
            else
                if (undo) auto.undoEntity(target, distance);
                else auto.gotoEntity(target, distance, follow);
            reply("[cyan]going to player:[] " + griefWarnings.formatPlayer(target));
            break;
        }
        case "cancel": {
            auto.cancelMovement();
            reply("cancelled");
            break;
        }
        case "distance": {
            if (ctx.args.size() < 3) {
                reply("[orange]Not enough arguments");
                reply("Usage: auto distance <distance>");
                return;
            }
            float distance;
            try {
                distance = Float.parseFloat(ctx.args.get(2));
            } catch (NumberFormatException ex) {
                reply("[orange]Invalid number");
                return;
            }
            auto.targetDistance = distance;
            reply("set target distance to " + distance);
            break;
        }
        case "itemsource":
        case "is":{
            if (ctx.args.size() == 3) {
                if (ctx.args.get(2).toLowerCase().equals("cancel")) {
                    auto.manageItemSource(null);
                    reply("cancelled automatic item source configuration");
                    return;
                }
            }
            Tile tile = getCursorTile();
            if (tile == null) {
                reply("cursor is not on a tile");
                return;
            }
            if (!auto.manageItemSource(tile)) {
                reply("target tile is not an item source");
                return;
            }
            reply("automatically configuring item source " + griefWarnings.formatTile(tile));
            break;
        }
            case "dumptarget": {
            // usage: /auto dumptarget [<x> <y>]
            Tile tile = null;
            if (ctx.args.size() == 3) {
                if (ctx.args.get(2).toLowerCase().equals("reset")) {
                    auto.setAutoDumpTransferTarget(null);
                    reply("reset autodump target");
                    return;
                }
            } else
                if (ctx.args.size() == 4) {
                    tile = findTile(ctx.args.get(2), ctx.args.get(3));
                } else tile = getCursorTile();
            if (tile == null) {
                reply("cursor is not on a tile or invalid tile specified");
                return;
            }
            if (tile.isLinked()) tile = tile.link();
            if (!auto.setAutoDumpTransferTarget(tile)) {
                reply("target does not seem valid");
                return;
            }
            reply("automatically dumping player inventory to tile " + griefWarnings.formatTile(tile));
            break;
        }
        case "4": {
            // usage: /auto dumptarget [<x> <y>]
            Tile tile = null;
            if (ctx.args.size() == 3) {
                if (ctx.args.get(2).toLowerCase().equals("reset")) {
                    auto.setAutoDumpTransferTarget(null);
                    reply("reset autodump target");
                    return;
                }
            } else
                if (ctx.args.size() == 4) {
                    tile = findTile(ctx.args.get(2), ctx.args.get(3));
                } else tile = getCursorTile();
            if (tile == null) {
                reply("cursor is not on a tile or invalid tile specified");
                return;
            }
            if (tile.isLinked()) tile = tile.link();
            if (!auto.setAutoDumpTransferTarget(tile)) {
                reply("target does not seem valid");
                return;
            }
            reply("automatically dumping player inventory to tile " + griefWarnings.formatTile(tile));
            break;
        }
        case "pickuptarget": {
            // usage: /auto pickuptarget [<x> <y>] <item>
            Item item;
            Tile tile;
            if (ctx.args.size() == 3) {
                if (ctx.args.get(2).toLowerCase().equals("reset")) {
                    auto.setAutoPickupTarget(null, null);
                    reply("reset autopickup target");
                    return;
                }

                item = content.items().find(a -> a.name.equals(ctx.args.get(2)));
                tile = getCursorTile();
            } else
                if (ctx.args.size() == 5) {
                    item = content.items().find(a -> a.name.equals(ctx.args.get(4)));
                    tile = findTile(ctx.args.get(2), ctx.args.get(3));
                } else {
                    reply("invalid arguments");
                    return;
                }
            if (item == null) {
                reply("invalid item provided");
                return;
            }
            if (tile == null) {
                reply("cursor is not on a tile");
                return;
            }
            if (tile.isLinked()) tile = tile.link();
            if (!auto.setAutoPickupTarget(tile, item)) {
                reply("target does not seem valid");
                return;
            }
            reply("automatically picking up item " + item.name + " from tile " + griefWarnings.formatTile(tile));
            break;
        }
        case "3": {
            // usage: /auto pickuptarget [<x> <y>] <item>
            Item item;
            Tile tile;
            if (ctx.args.size() == 3) {
                if (ctx.args.get(2).toLowerCase().equals("reset")) {
                    auto.setAutoPickupTarget(null, null);
                    reply("reset autopickup target");
                    return;
                }

                item = content.items().find(a -> a.name.equals(ctx.args.get(2)));
                tile = getCursorTile();
            } else
                if (ctx.args.size() == 5) {
                    item = content.items().find(a -> a.name.equals(ctx.args.get(4)));
                    tile = findTile(ctx.args.get(2), ctx.args.get(3));
                } else {
                    reply("invalid arguments");
                    return;
                }
            if (item == null) {
                reply("invalid item provided");
                return;
            }
            if (tile == null) {
                reply("cursor is not on a tile");
                return;
            }
            if (tile.isLinked()) tile = tile.link();
            if (!auto.setAutoPickupTarget(tile, item)) {
                reply("target does not seem valid");
                return;
            }
            reply("automatically picking up item " + item.name + " from tile " + griefWarnings.formatTile(tile));
            break;
        }
        case "6": {
            boolean persist = false;
            if (ctx.args.contains("p")){
                persist = true;
                reply("[slate]Using persist mode");
            }
            if (ctx.args.contains("la")){
                Tile tile = griefWarnings.lastalerttile;
                auto.gotoTile(tile, persist ? 0f : 50f);
                auto.persist = persist;
                reply("[cyan]going to tile[] " + griefWarnings.formatTile(tile));
                break;
            }
            else {
                Tile tile = getCursorTile();
                if (tile == null) {
                    reply("[orange]Invalid tile");
                    return;
                }
                auto.gotoTile(tile, persist ? 0f : 50f);
                auto.persist = persist;
                reply("[cyan]going to tile[] " + griefWarnings.formatTile(tile));
                break;
            }
        }
        default:
            reply("unknown subcommand");
        }
    }

    public void nextwave(CommandContext ctx) {
        if (!player.isAdmin) {
            reply("not admin!");
            return;
        }
        int count = 1;
        if (ctx.args.size() > 1) {
            try {
                count = Integer.parseInt(ctx.args.get(1));
            } catch (NumberFormatException ex) {
                reply("invalid number");
                return;
            }
        }
        //TODO: fix this
        for (int i = 0; i < count; i++){
            Call.onAdminRequest(player, AdminAction.wave);
        }
        reply("done");
    }

    public void eval(CommandContext ctx) {
        String code = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        reply(runConsole(code));
    }

    /**
     * Switch to freecam and focus on an object
     */
    public void show(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("No target given");
            return;
        }

        if (ctx.args.size() == 3) {
            Tile tile = findTile(ctx.args.get(1), ctx.args.get(2));
            if (tile != null) {
                reply("[cyan]Showing tile[] " + griefWarnings.formatTile(tile));
                griefWarnings.auto.setFreecam(true, tile.getX(), tile.getY());
                return;
            }
        }

        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        Player target = getPlayer(name);
        if (target == null) {
            reply("Target does not exist");
            return;
        }

        reply("[cyan]Showing player[] " + griefWarnings.formatPlayer(target));
        griefWarnings.auto.setFreecam(true, target.x, target.y);
    }
    public void lastalert(CommandContext ctx) {
            Tile tile = griefWarnings.lastalerttile;
            String playerid = "#" + griefWarnings.lastalertplayer;

            if  (tile == null || ctx.args.contains("p")){
                Player target = getPlayer(playerid);
                if (target == null) {
                    reply("Target does not exist");
                    return;
                }
                reply("[cyan]Showing player[] " + griefWarnings.formatPlayer(target));
                griefWarnings.auto.setFreecam(true, target.x, target.y);
            }
            else if (tile != null) {
                reply("[cyan]Showing tile[] " + griefWarnings.formatTile(tile));
                griefWarnings.auto.setFreecam(true, tile.getX(), tile.getY());
                return;
            }
    }
    public void blacklist(CommandContext ctx){
        if (ctx.args.contains("clear")){
            griefWarnings.autoBanTarget.clear();
            replyUnlock("cleared blacklist names");
        }
        else if (ctx.args.contains("list")){
            if(griefWarnings.autoBanTarget.size() == 0){
            reply("[lightgray]none[]");
            }
            else {
                    replyAlert("current blacklist: \n" + griefWarnings.autoBanTarget);
                }
            }
        else{
            String blacklistName = String.join(" ", ctx.args.subList(1, ctx.args.size()));
            griefWarnings.autoBanTarget.add(blacklistName);
            replyUnlock("added <" + blacklistName + "> to blacklist");
        }
    }
    public void chatfilter(CommandContext ctx){
        if (ctx.args.contains("clear")){
            griefWarnings.chatFilteredText.clear();
            replyUnlock("cleared chat filter");
        }
        else if (ctx.args.contains("list")){
            if(griefWarnings.chatFilteredText.size() == 0){
                reply("[lightgray]none[]");
            }
            else {
                    replyAlert("current blacklisted text: \n" + griefWarnings.chatFilteredText);
            }
        }
        else{
            String blacklistText = String.join(" ", ctx.args.subList(1, ctx.args.size()));
            griefWarnings.chatFilteredText.add(blacklistText);
            replyUnlock("added <" + blacklistText + "> to blacklist");
        }
    }


    /**
     * Show action logs relevant to tile or player
     */
    public void getactions(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("No target given");
            return;
        }

        if (ctx.args.size() == 3) {
            Tile tile = findTile(ctx.args.get(1), ctx.args.get(2));
            if (tile != null) {
                reply("[cyan]Showing actions for tile[cyan] " + griefWarnings.formatTile(tile));
                Array<TileAction> actions = griefWarnings.actionLog.getActions(tile);
                // print backwards
                for (int i = actions.size - 1; i >= 0; i--) {
                    reply(actions.get(i).toString());
                }
                return;
            }
        }

        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        Player target = getPlayer(name);
        if (target == null) {
            reply("Target does not exist");
            return;
        }

        Array<Action> actions = griefWarnings.actionLog.getActions(target);
        for (int i = actions.size - 1; i >= 0; i--) {
            reply(actions.get(i).toString());
        }
    }

    public void copyga(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("No target given");
            return;
        }

        if (ctx.args.size() == 3) {
            Tile tile = findTile(ctx.args.get(1), ctx.args.get(2));
            if (tile != null) {
                reply("[cyan]Showing actions for tile[cyan] " + griefWarnings.formatTile(tile));
                Array<TileAction> actions = griefWarnings.actionLog.getActions(tile);
                // print backwards
                for (int i = actions.size - 1; i >= 0; i--) {
                    reply(actions.get(i).toString());
                }
                return;
            }
        }

        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        Player target = getPlayer(name);
        if (target == null) {
            reply("Target does not exist");
            return;
        }

        StringBuilder actionsText = new StringBuilder("\n");
        actionsText.append("Action log of: " + "`" + target.name + "` ");
        actionsText.append("```diff\n");
        Array<Action> actions = griefWarnings.actionLog.getActions(target);
        for (int i = actions.size - 1; i >= 0 ; i--) {
            actionsText.append(actions.get(i) + "\n");
        }
        actionsText.append("```");
        Core.app.setClipboardText(actionsText.toString().replaceAll("\\[[^]]*]", "").replaceAll("Built", "+Built").replaceAll("Destroyed", "-Destroyed"));
        replyUnlock("Copied action log");
    }

    /**
     * Undo actions of player
     */
    //prepare to crash your game!
    //fixed! (maybe)
    public void undoactions(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("No target given");
            return;
        }

        int count = -1;
        if (ctx.args.size() > 2) {
            try {
                count = Integer.parseInt(ctx.args.get(1));
            } catch (NumberFormatException ex) {
                // ignore
            }
        }

        int argStart = count > -1 ? 2 : 1;
        String name = String.join(" ", ctx.args.subList(argStart, ctx.args.size()));
        Player target = getPlayer(name);
        if (target == null) {
            reply("Invalid target");
            return;
        }

        Array<Action> actions = griefWarnings.actionLog.getActions(target);
        int j = 0;
        for (Action action : actions) {
            reply("[green]Undo:[] " + action.toString());
            if (action.undo() == UndoResult.mismatch) reply("[orange]mismatch");
            if (count > 0 && ++j >= count) break;
        }
    }
}
