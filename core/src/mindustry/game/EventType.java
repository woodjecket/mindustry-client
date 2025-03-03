package mindustry.game;

import arc.math.geom.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock.*;

public class EventType{

    //events that occur very often
    public enum Trigger{
        shock,
        phaseDeflectHit,
        impactPower,
        thoriumReactorOverheat,
        fireExtinguish,
        acceleratorUse,
        newGame,
        tutorialComplete,
        flameAmmo,
        turretCool,
        enablePixelation,
        exclusionDeath,
        suicideBomb,
        openWiki,
        teamCoreDamage,
        socketConfigChanged,
        update,
        unitCommandChange,
        draw,
        drawOver,
        preDraw,
        postDraw,
        uiDrawBegin,
        uiDrawEnd,
        //before/after bloom used, skybox or planets drawn
        universeDrawBegin,
        //skybox drawn and bloom is enabled - use Vars.renderer.planets
        universeDraw,
        //planets drawn and bloom disabled
        universeDrawEnd
    }

    /** Replaces vanilla {@code teamCoreDamage}, allowing a tile to be passed. */
    public static class TeamCoreDamage{
        public final Tile core;

        public TeamCoreDamage(Tile core) { this.core = core; }
    }

    public static class WinEvent{}
    public static class LoseEvent{}
    public static class ResizeEvent{}
    public static class MapMakeEvent{}
    public static class MapPublishEvent{}
    public static class SaveWriteEvent{}
    public static class ClientCreateEvent{}
    public static class ServerLoadEvent{}
    public static class DisposeEvent{}
    public static class PlayEvent{}
    public static class ResetEvent{}
    public static class HostEvent{}
    public static class WaveEvent{}
    public static class TurnEvent{}
    /** Called when the player places a line, mobile or desktop.*/
    public static class LineConfirmEvent{}
    /** Called when a turret receives ammo, but only when the tutorial is active! */
    public static class TurretAmmoDeliverEvent{}
    /** Called when a core receives ammo, but only when the tutorial is active! */
    public static class CoreItemDeliverEvent{}
    /** Called when the player opens info for a specific block.*/
    public static class BlockInfoEvent{}
    /** Called *after* all content has been initialized. */
    public static class ContentInitEvent{}
    /** Called when the client game is first loaded. */
    public static class ClientLoadEvent{}
    /** Called after SoundControl registers its music. */
    public static class MusicRegisterEvent{}
    /** Called *after* all the modded files have been added into Vars.tree */
    public static class FileTreeInitEvent{}
    /** Called when a game begins and the world tiles are loaded. Entities are not yet loaded at this stage.  */
    public static class WorldLoadEvent{}
    /** Called just after joining a server */
    public static class ServerJoinEvent{}

    public static class SaveLoadEvent{
        public final boolean isMap;

        public SaveLoadEvent(boolean isMap){
            this.isMap = isMap;
        }
    }

    /** Called when a sector is destroyed by waves when you're not there. */
    public static class SectorLoseEvent{
        public final Sector sector;

        public SectorLoseEvent(Sector sector){
            this.sector = sector;
        }
    }

    /** Called when a sector is destroyed by waves when you're not there. */
    public static class SectorInvasionEvent{
        public final Sector sector;

        public SectorInvasionEvent(Sector sector){
            this.sector = sector;
        }
    }

    public static class LaunchItemEvent{
        public final ItemStack stack;

        public LaunchItemEvent(ItemStack stack){
            this.stack = stack;
        }
    }

    public static class SectorLaunchEvent{
        public final Sector sector;

        public SectorLaunchEvent(Sector sector){
            this.sector = sector;
        }
    }

    public static class SchematicCreateEvent{
        public final Schematic schematic;

        public SchematicCreateEvent(Schematic schematic){
            this.schematic = schematic;
        }
    }

    public static class ClientPreConnectEvent{
        public final Host host;

        public ClientPreConnectEvent(Host host){
            this.host = host;
        }
    }

    /** Consider using Menus.registerMenu instead. */
    public static class MenuOptionChooseEvent{
        public final Player player;
        public final int menuId, option;

        public MenuOptionChooseEvent(Player player, int menuId, int option){
            this.player = player;
            this.option = option;
            this.menuId = menuId;
        }
    }

    public static class PlayerChatEvent{
        public final Player player;
        public final String message;

        public PlayerChatEvent(Player player, String message){
            this.player = player;
            this.message = message;
        }
    }

    /** Fired just after a chat message is added to chatfrag. */
    public static class PlayerChatEventClient{}

    /** Called when a sector is conquered, e.g. a boss or base is defeated. */
    public static class SectorCaptureEvent{
        public final Sector sector;

        public SectorCaptureEvent(Sector sector){
            this.sector = sector;
        }
    }

    /** Called when the player withdraws items from a block. */
    public static class WithdrawEvent{
        public final Building tile;
        public final Player player;
        public final Item item;
        public final int amount;

        public WithdrawEvent(Building tile, Player player, Item item, int amount){
            this.tile = tile;
            this.player = player;
            this.item = item;
            this.amount = amount;
        }
    }

    /** Called when a player deposits items to a block.*/
    public static class DepositEvent{
        public final Building tile;
        public final Player player;
        public final Item item;
        public final int amount;

        public DepositEvent(Building tile, Player player, Item item, int amount){
            this.tile = tile;
            this.player = player;
            this.item = item;
            this.amount = amount;
        }
    }

    /** Called when a player configures any building. */
    public static class ConfigEvent{
        public final Building tile;
        public final Player player;
        public final Object value;

        public ConfigEvent(Building tile, Player player, Object value){
            this.tile = tile;
            this.player = player;
            this.value = value;
        }
    }

    /** Called just before a player configures any building. */
    public static class ConfigEventBefore{
        public final Building tile;
        public final Player player;
        public final Object value;

        public ConfigEventBefore(Building tile, Player player, Object value){
            this.tile = tile;
            this.player = player;
            this.value = value;
        }
    }

    /** Called when a player taps any tile. */
    public static class TapEvent{
        public final Player player;
        public final Tile tile;

        public TapEvent(Player player, Tile tile){
            this.tile = tile;
            this.player = player;
        }
    }

    public static class PickupEvent{
        public final Unit carrier;
        public final @Nullable Unit unit;
        public final @Nullable Building build;

        public PickupEvent(Unit carrier, Unit unit){
            this.carrier = carrier;
            this.unit = unit;
            this.build = null;
        }

        public PickupEvent(Unit carrier, Building build){
            this.carrier = carrier;
            this.build = build;
            this.unit = null;
        }
    }

    public static class PayloadDropEvent{
        public final Unit carrier;
        public final @Nullable Unit unit;
        public final @Nullable Building build;

        public PayloadDropEvent(Unit carrier, Unit unit){
            this.carrier = carrier;
            this.unit = unit;
            this.build = null;
        }

        public PayloadDropEvent(Unit carrier, Building build){
            this.carrier = carrier;
            this.build = build;
            this.unit = null;
        }
    }

    public static class UnitControlEvent{
        public final Player player;
        public final @Nullable Unit unit;

        public UnitControlEvent(Player player, @Nullable Unit unit){
            this.player = player;
            this.unit = unit;
        }
    }

    public static class BuildingCommandEvent{
        public final Player player;
        public final Building building;
        public final Vec2 position;

        public BuildingCommandEvent(Player player, Building building, Vec2 position){
            this.player = player;
            this.building = building;
            this.position = position;
        }
    }

    public static class GameOverEvent{
        public final Team winner;

        public GameOverEvent(Team winner){
            this.winner = winner;
        }
    }

    /** Literally just GameOverEvent but only fired when connected as a client. */
    public static class GameOverEventClient{
        public final Team winner;

        public GameOverEventClient(Team winner){
            this.winner = winner;
        }
    }

    /**
     * Called when a bullet damages a building. May not be called for all damage events!
     * This event is re-used! Never do anything to re-raise this event in the listener.
     * */
    public static class BuildDamageEvent{
        public Building build;
        public Bullet source;

        public BuildDamageEvent set(Building build, Bullet source){
            this.build = build;
            this.source = source;
            return this;
        }
    }

    /**
     * Called *before* a tile has changed.
     * WARNING! This event is special: its instance is reused! Do not cache or use with a timer.
     * Do not modify any tiles inside listeners that use this tile.
     * */
    public static class TilePreChangeEvent{
        public Tile tile;

        public TilePreChangeEvent set(Tile tile){
            this.tile = tile;
            return this;
        }
    }

    /**
     * Called *after* a tile has changed.
     * WARNING! This event is special: its instance is reused! Do not cache or use with a timer.
     * Do not modify any tiles inside listener code.
     * */
    public static class TileChangeEvent{
        public Tile tile;

        public TileChangeEvent set(Tile tile){
            this.tile = tile;
            return this;
        }
    }

    /**
     * Called after a building's team changes.
     * Event object is reused, do not nest!
     * */
    public static class BuildTeamChangeEvent{
        public Team previous;
        public Building build;

        public BuildTeamChangeEvent set(Team previous, Building build){
            this.build = build;
            this.previous = previous;
            return this;
        }
    }

    /** Called when a core block is placed/removed or its team is changed. */
    public static class CoreChangeEvent{
        public CoreBuild core;

        public CoreChangeEvent(CoreBuild core){
            this.core = core;
        }
    }

    public static class StateChangeEvent{
        public final State from, to;

        public StateChangeEvent(State from, State to){
            this.from = from;
            this.to = to;
        }
    }

    public static class UnlockEvent{
        public final UnlockableContent content;

        public UnlockEvent(UnlockableContent content){
            this.content = content;
        }
    }

    public static class ResearchEvent{
        public final UnlockableContent content;

        public ResearchEvent(UnlockableContent content){
            this.content = content;
        }
    }

    /**
     * Called when block building begins by placing down the ConstructBlock.
     * The tile's block will nearly always be a ConstructBlock.
     */
    public static class BlockBuildBeginEvent{
        public final Tile tile;
        public final Team team;
        public final @Nullable Unit unit;
        public final boolean breaking;

        public BlockBuildBeginEvent(Tile tile, Team team, Unit unit, boolean breaking){
            this.tile = tile;
            this.team = team;
            this.unit = unit;
            this.breaking = breaking;
        }
    }

    /**
     * Called when block building begins by placing down the ConstructBlock.
     */
    public static class BlockBuildBeginEventBefore {
        public final Tile tile;
        public final Team team;
        public final @Nullable Unit unit;
        public final boolean breaking;
        public final @Nullable Block newBlock;

        public BlockBuildBeginEventBefore(Tile tile, Team team, Unit unit, boolean breaking, Block newBlock){
            this.tile = tile;
            this.team = team;
            this.unit = unit;
            this.breaking = breaking;
            this.newBlock = newBlock;
        }
    }

    public static class BlockBuildEndEvent{
        public final Tile tile;
        public final Team team;
        public final @Nullable Unit unit;
        public final boolean breaking;
        public final @Nullable Object config;
        public final Block previous;

        public BlockBuildEndEvent(Tile tile, @Nullable Unit unit, Team team, boolean breaking, @Nullable Object config, Block previous){
            this.tile = tile;
            this.team = team;
            this.unit = unit;
            this.breaking = breaking;
            this.config = config;
            this.previous = previous;
        }
    }

    public static class BlockBuildEventTile {
        public final Tile tile;
        public final Team team;
        public final Unit unit;
        public final Block oldBlock;
        public final Block newBlock;
        public final @Nullable Object oldConfig;
        public final @Nullable Object config;

        public BlockBuildEventTile(Tile tile, Team team, Unit unit, Block oldBlock, Block newBlock, @Nullable Object oldConfig, @Nullable Object config) {
            this.tile = tile;
            this.team = team;
            this.unit = unit;
            this.oldBlock = oldBlock;
            this.newBlock = newBlock;
            this.oldConfig = oldConfig;
            this.config = config;
        }
    }

    public static class BuildPayloadPickup {
        public final Tile tile;
        public final Unit unit;
        public final Building building;

        public BuildPayloadPickup(Tile tile, Unit unit, Building building) {
            this.tile = tile;
            this.unit = unit;
            this.building = building;
        }
    }

    public static class BuildPayloadDrop {
        public final Tile tile;
        public final Unit unit;
        public final Building building;

        public BuildPayloadDrop(Tile tile, Unit unit, Building building) {
            this.tile = tile;
            this.unit = unit;
            this.building = building;
        }
    }

    public static class BlockBreakEvent {
        public final Tile tile;
        public final Team team;
        public final @Nullable Unit unit;
        public final Block oldBlock;
        public final @Nullable Object oldConfig;
        public final byte rotation;

        public BlockBreakEvent(Tile tile, Team team, @Nullable Unit unit, Block oldBlock, @Nullable Object oldConfig, byte rotation) {
            this.tile = tile;
            this.team = team;
            this.unit = unit;
            this.oldBlock = oldBlock;
            this.oldConfig = oldConfig;
            this.rotation = rotation;
        }
    }

    public static class BlockRotateEvent {
        public final @Nullable Player player;
        public final Building build;
        public final boolean direction;
        public final int oldRotation;
        public final int newRotation;

        public BlockRotateEvent(@Nullable Player player, Building build, boolean direction, int oldRotation, int newRotation) {
            this.build = build;
            this.player = player;
            this.direction = direction;
            this.oldRotation = oldRotation;
            this.newRotation = newRotation;
        }
    }

    /**
     * Called when a player or drone begins building something.
     * This does not necessarily happen when a new ConstructBlock is created.
     */
    public static class BuildSelectEvent{
        public final Tile tile;
        public final Team team;
        public final Unit builder;
        public final boolean breaking;

        public BuildSelectEvent(Tile tile, Team team, Unit builder, boolean breaking){
            this.tile = tile;
            this.team = team;
            this.builder = builder;
            this.breaking = breaking;
        }
    }

    /** Called right before a block is destroyed.
     * The tile entity of the tile in this event cannot be null when this happens.*/
    public static class BlockDestroyEvent{
        public final Tile tile;

        public BlockDestroyEvent(Tile tile){
            this.tile = tile;
        }
    }

    public static class UnitDestroyEvent{
        public final Unit unit;

        public UnitDestroyEvent(Unit unit){
            this.unit = unit;
        }
    }

    public static class UnitDrownEvent{
        public final Unit unit;

        public UnitDrownEvent(Unit unit){
            this.unit = unit;
        }
    }

    /** Called when a unit is created in a reconstructor, factory or other unit. */
    public static class UnitCreateEvent{
        public final Unit unit;
        public final @Nullable Building spawner;
        public final @Nullable Unit spawnerUnit;

        public UnitCreateEvent(Unit unit, Building spawner, Unit spawnerUnit){
            this.unit = unit;
            this.spawner = spawner;
            this.spawnerUnit = spawnerUnit;
        }

        public UnitCreateEvent(Unit unit, Building spawner){
            this(unit, spawner, null);
        }
    }

    /** Called when a unit is spawned by wave. */
    public static class UnitSpawnEvent{
        public final Unit unit;

        public UnitSpawnEvent(Unit unit) {
            this.unit = unit;
        }
    }

    /** Called when a unit is dumped from any payload block. */
    public static class UnitUnloadEvent{
        public final Unit unit;

        public UnitUnloadEvent(Unit unit){
            this.unit = unit;
        }
    }

    public static class UnitChangeEvent{
        public final Player player;
        public final Unit unit;

        /** @param unit is the player's new unit. */
        public UnitChangeEvent(Player player, Unit unit){
            this.player = player;
            this.unit = unit;
        }
    }

    /** UnitChangeEvent can't be changed without breaking compatibility with some mods, this class adds the unit swapped from. */
    public static class UnitChangeEventClient{
        public final Player player;
        public final Unit newUnit;
        public final Unit oldUnit;

        public UnitChangeEventClient(Player player, Unit newUnit, Unit oldUnit){
            this.player = player;
            this.newUnit = newUnit;
            this.oldUnit = oldUnit;
        }
    }

    /** Called when a connection is established to a client. */
    public static class ConnectionEvent{
        public final NetConnection connection;

        public ConnectionEvent(NetConnection connection){
            this.connection = connection;
        }
    }

    /** Called when a player sends a connection packet. */
    public static class ConnectPacketEvent{
        public final NetConnection connection;
        public final ConnectPacket packet;

        public ConnectPacketEvent(NetConnection connection, ConnectPacket packet){
            this.connection = connection;
            this.packet = packet;
        }
    }

    /**
     * Called after player confirmed it has received world data and is ready to play.
     * Note that if this is the first world receival, then player.con.hasConnected is false.
     */
    public static class PlayerConnectionConfirmed{
        public final Player player;

        public PlayerConnectionConfirmed(Player player){
            this.player = player;
        }
    }

    /** Called after connecting; when a player receives world data and is ready to play. Fired only once, after initial connection. */
    public static class PlayerJoin{
        public final Player player;

        public PlayerJoin(Player player){
            this.player = player;
        }
    }

    /** Called when a player connects, but has not joined the game yet.*/
    public static class PlayerConnect{
        public final Player player;

        public PlayerConnect(Player player){
            this.player = player;
        }
    }

    /** Called before a player leaves the game. */
    public static class PlayerLeave{
        public final Player player;

        public PlayerLeave(Player player){
            this.player = player;
        }
    }

    public static class PlayerBanEvent{
        @Nullable
        public final Player player;
        public final String uuid;

        public PlayerBanEvent(Player player, String uuid){
            this.player = player;
            this.uuid = uuid;
        }
    }

    public static class PlayerUnbanEvent{
        @Nullable
        public final Player player;
        public final String uuid;

        public PlayerUnbanEvent(Player player, String uuid){
            this.player = player;
            this.uuid = uuid;
        }
    }

    public static class PlayerIpBanEvent{
        public final String ip;

        public PlayerIpBanEvent(String ip){
            this.ip = ip;
        }
    }

    public static class PlayerIpUnbanEvent{
        public final String ip;

        public PlayerIpUnbanEvent(String ip){
            this.ip = ip;
        }
    }

    public static class AdminRequestEvent{
        public final Player player;
        public final @Nullable Player other;
        public final AdminAction action;

        public AdminRequestEvent(Player player, Player other, AdminAction action){
            this.player = player;
            this.other = other;
            this.action = action;
        }
    }
    public static class SendChatMessageEvent {
        public final String message;

        public SendChatMessageEvent(String message) {
            this.message = message;
        }
    }
}
