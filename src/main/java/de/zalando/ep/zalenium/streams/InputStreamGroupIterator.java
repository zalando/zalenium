package de.zalando.ep.zalenium.streams;

import java.io.IOException;

public interface InputStreamGroupIterator {

    InputStreamDescriptor next() throws IOException;

}