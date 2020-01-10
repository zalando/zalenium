package de.zalando.ep.zalenium.streams;

import java.io.InputStream;

public interface InputStreamDescriptor {

    InputStream get();
    String name();

}