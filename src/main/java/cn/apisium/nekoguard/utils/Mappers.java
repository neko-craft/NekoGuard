package cn.apisium.nekoguard.utils;

public final class Mappers {
    public final static SeriesMapper CHATS = new SeriesMapper("time", "player", "message");
    public final static SeriesMapper COMMANDS = new SeriesMapper("time", "type", "performer", "command");
    public final static SeriesMapper BLOCKS = new SeriesMapper("data", "action", "performer", "time", "world", "x", "y", "z");
    public final static SeriesMapper CONTAINER_ACTIONS = new SeriesMapper("time", "performer", "source", "target", "item");
}
