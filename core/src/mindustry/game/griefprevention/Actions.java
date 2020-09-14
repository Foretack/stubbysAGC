package mindustry.game.griefprevention;

import arc.math.Mathf;
import mindustry.content.Blocks;
import mindustry.entities.traits.BuilderTrait.BuildRequest;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Pos;
import mindustry.world.Tile;
import mindustry.world.blocks.power.PowerNode;

import java.util.Date;

import static mindustry.Vars.*;

public class Actions {
    public enum UndoResult {
        success, mismatch, unavailable
    }

    public static abstract class Action {
        public static String name;
        public Player actor;
        public Date timestamp;

        public Action(Player actor) {
            this.actor = actor;
            this.timestamp = new Date();
        }

        public UndoResult undo() {
            return UndoResult.unavailable;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static abstract class TileAction extends Action {
        public Tile tile;

        public TileAction(Player actor, Tile tile) {
            super(actor);
            this.tile = tile;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class Construct extends TileAction {
        public static String name = "[sky]Built[]";

        public Block previousBlock;
        public int previousRotation;
        public int previousConfig;
        public Block constructBlock;
        public int constructRotation;

        public Construct(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (tile.block() != constructBlock) return UndoResult.mismatch;
            if (previousBlock == null || !previousBlock.canReplace(tile.block())) { // don't deconstruct if replacement possible
                BuildRequest removeRequest = new BuildRequest(tile.x, tile.y);
                player.buildQueue().addLast(removeRequest);
            }
            if (previousBlock != null && previousBlock.isVisible()) {
                BuildRequest rebuildRequest = new BuildRequest(tile.x, tile.y, previousRotation, previousBlock);
                rebuildRequest.configure(previousConfig);
                player.buildQueue().addLast(rebuildRequest);
            }
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + "[royal] " + constructBlock.name + " []at  " + griefWarnings.formatTile(tile);
        }
    }

    public static class Deconstruct extends TileAction {
        public static String name = "[goldenrod]Destroyed[]";

        public Block previousBlock;
        public int previousRotation;
        public int previousConfig;

        public Deconstruct(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            // if there's already a build request for this location, don't shove more on the build queue
            // NOTE: this is potentially expensive?
            if (player.buildQueue().indexOf(b -> b.x == tile.x && b.y == tile.y && !b.breaking) > -1) {
                return UndoResult.mismatch;
            }
            if (previousBlock.isVisible()) {
                BuildRequest rebuildRequest = new BuildRequest(tile.x, tile.y, previousRotation, previousBlock);
                rebuildRequest.configure(previousConfig);
                player.buildQueue().addLast(rebuildRequest);
            }
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + "[royal] " + (previousBlock == null ? "null" : previousBlock.name) + " []at " + griefWarnings.formatTile(tile);
        }
    }

    // used for tiles with positional configuration (mass drivers, item bridges, etc)
    public static class ConfigurePositional extends TileAction {
        public static String name = "[#d899ff]Configured[]";

        public Block targetBlock;
        public int beforeConfig;
        public int afterConfig;

        public ConfigurePositional(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (tile.block() != targetBlock) return UndoResult.mismatch;
            if (tile.entity == null || tile.entity.config() != afterConfig) return UndoResult.mismatch;
            tile.configure(beforeConfig);
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + " [royal]" + targetBlock.name + " []at " + griefWarnings.formatTile(tile);
        }
    }

    // used for tiles with item/liquid selection configuration (unloaders, sorters, item source, etc)
    public static class ConfigureItemSelect extends TileAction {
        public static String name = "[#d899ff]Changed[]";

        public Block targetBlock;
        public int beforeConfig;
        public int afterConfig;

        public ConfigureItemSelect(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (tile.block() != targetBlock) return UndoResult.mismatch;
            if (tile.entity == null || tile.entity.config() != afterConfig) return UndoResult.mismatch;
            tile.configure(beforeConfig);
            return UndoResult.success;
        }

        @Override
        public String toString() {
            String itemIcon;        //This is probably the worst way to do it, but hey, as long as it works
            if(beforeConfig == -1){
                itemIcon = "[lightgray]none[]"; //previous one was showing to unloaders as well, didn't fit well
            }else if(beforeConfig == 0){
                itemIcon = "\uF838";
            }else if(beforeConfig == 1){
                itemIcon = "\uF837";
            }else if(beforeConfig == 2){
                itemIcon = "\uF836";
            }else if(beforeConfig == 3){
                itemIcon = "\uF835";
            }else if(beforeConfig == 4){
                itemIcon = "\uF834";
            }else if(beforeConfig == 5){
                itemIcon = "\uF833";
            }else if(beforeConfig == 6){
                itemIcon = "\uF832";
            }else if(beforeConfig == 7){
                itemIcon = "\uF831";
            }else if(beforeConfig == 8){
                itemIcon = "\uF830";
            }else if(beforeConfig == 9){
                itemIcon = "\uF82F";
            }else if(beforeConfig == 10){
                itemIcon = "\uF82E";
            }else if(beforeConfig == 11){
                itemIcon = "\uF82D";
            }else if(beforeConfig == 12){
                itemIcon = "\uF82C";
            }else if(beforeConfig == 13){
                itemIcon = "\uF82B";
            }else if(beforeConfig == 14){
                itemIcon = "\uF82A";
            }else{
                itemIcon = "\uF829";
            }
            String itemIconAfter;
            if(afterConfig == -1){
                itemIconAfter = "[lightgray]none[]";
            }else if(afterConfig == 0){
                itemIconAfter = "\uF838";
            }else if(afterConfig == 1){
                itemIconAfter = "\uF837";
            }else if(afterConfig == 2){
                itemIconAfter = "\uF836";
            }else if(afterConfig == 3){
                itemIconAfter = "\uF835";
            }else if(afterConfig == 4){
                itemIconAfter = "\uF834";
            }else if(afterConfig == 5){
                itemIconAfter = "\uF833";
            }else if(afterConfig == 6){
                itemIconAfter = "\uF832";
            }else if(afterConfig == 7){
                itemIconAfter = "\uF831";
            }else if(afterConfig == 8){
                itemIconAfter = "\uF830";
            }else if(afterConfig == 9){
                itemIconAfter = "\uF82F";
            }else if(afterConfig == 10){
                itemIconAfter = "\uF82E";
            }else if(afterConfig == 11){
                itemIconAfter = "\uF82D";
            }else if(afterConfig == 12){
                itemIconAfter = "\uF82C";
            }else if(afterConfig == 13){
                itemIconAfter = "\uF82B";
            }else if(afterConfig == 14){
                itemIconAfter = "\uF82A";
            }else{
                itemIconAfter = "\uF829";
            }                               //my life is filled with regret
            return name +
                    " [royal]" + targetBlock.name + " []at " + griefWarnings.formatTile(tile) + " " + itemIcon + " -> " + itemIconAfter;
        }
    }

    public static class ConfigurePowerNode extends TileAction {
        public static String name = "[yellow]Configured Node[]";

        // whether the configure was a disconnect
        public boolean disconnect;

        public int other;

        public ConfigurePowerNode(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (!(tile.block() instanceof PowerNode)) return UndoResult.mismatch;
            boolean has = tile.entity.power.links.contains(other);;
            if (disconnect == has) return UndoResult.mismatch;
            tile.configure(other);
            return UndoResult.success;

        }

        @Override

        public String toString() {
            String whatReallyHappened;      //We may never know
            if(disconnect){
                whatReallyHappened = "[orange]Disconnected from[]";
            }else{
                whatReallyHappened = "[green]Connected to[]";
            }
            return name + " at " + griefWarnings.formatTile(tile) +". " + whatReallyHappened + " (" + Pos.x(other) + ", " + Pos.y(other) + ")";
        }
    }

    public static class DepositItems extends TileAction {
        public static String name = "[forest]Deposited[]";

        public Item item;
        public int amount;

        public DepositItems(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public String toString() {
            return name + " " + amount + " [tan]" + item.name + "[] to " + griefWarnings.formatTile(tile);
        }
    }

    public static class WithdrawItems extends TileAction {
        public static String name = "[salmon]Withdrew[]";

        public Item item;
        public int amount;

        public WithdrawItems(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public String toString() {
		return name + " " + amount + " [tan]" + item.name + "[] from " + griefWarnings.formatTile(tile);
        }
    }

    public static class RotateBlock extends TileAction {
        public static String name = "RotateBlock";

        public Block targetBlock;
        public int beforeRotation;
        public boolean direction;

        public RotateBlock(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (tile.block() != targetBlock) return UndoResult.mismatch;
            if (tile.rotation() != Mathf.mod(beforeRotation + Mathf.sign(direction), 4)) {
                return UndoResult.mismatch;
            }
            Call.rotateBlock(player, tile, !direction);
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "targetBlock: " + targetBlock.name + ", " +
                    "beforeRotation: " + beforeRotation + ", " +
                    "direction: " + (direction ? 1 : -1) + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class TapTile extends TileAction {
        public static String name = "TapTile";

        public TapTile(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }
}
