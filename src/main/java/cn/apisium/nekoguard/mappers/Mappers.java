package cn.apisium.nekoguard.mappers;

public final class Mappers {
    public final static SeriesMapper CHATS = new SeriesMapper("time", "player", "message");
    public final static SeriesMapper COMMANDS = new SeriesMapper("time", "type", "performer", "command");
    public final static SeriesMapper BLOCKS = new SeriesMapper("block", "action", "performer", "time", "world", "x", "y", "z");
    public final static SeriesMapper CONTAINER_ACTIONS = new SeriesMapper("time", "item", "se", "sw", "sx", "sy", "sz", "te", "tw", "tx", "ty", "tz");
    public final static SeriesMapper DEATHS = new SeriesMapper("time", "performer", "type", "entity", "cause", "world", "x", "y", "z");
    public final static SeriesMapper SPAWNS = new SeriesMapper("time", "reason", "type", "world", "x", "y", "z", "id");
    public final static SeriesMapper ITEM_ACTIONS = new SeriesMapper("time", "performer", "action", "item", "world", "x", "y", "z");
}
