package cn.apisium.nekoguard.utils;

public final class Tuple<A, B, C> {
    public A a;
    public B b;
    public C c;

    public Tuple(final A a, final B b, final C c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }
}
