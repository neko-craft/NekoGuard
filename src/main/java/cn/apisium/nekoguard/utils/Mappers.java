package cn.apisium.nekoguard.utils;

public final class Mappers {
    public final static SeriesMapper CHATS = new SeriesMapper("time", "player", "message");
    public final static SeriesMapper INSPECT_BLOCKS = new SeriesMapper("data", "action", "performer", "time");
    public final static SeriesMapper CONTAINER_ACTIONS = new SeriesMapper("time", "performer", "source", "target", "item");
}
