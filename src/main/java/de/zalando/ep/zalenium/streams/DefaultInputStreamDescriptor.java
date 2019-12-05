package de.zalando.ep.zalenium.streams;

import java.io.InputStream;

public final class DefaultInputStreamDescriptor implements InputStreamDescriptor{
    private final InputStream is;
    private final String name;

    public DefaultInputStreamDescriptor(InputStream is, String name) {
        this.is = is;
        this.name = name;
    }

    @Override
    public InputStream get() {
        return is;
    }

    @Override
    public String name() {
        return name;
    }
}