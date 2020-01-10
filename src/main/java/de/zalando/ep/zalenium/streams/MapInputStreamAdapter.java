package de.zalando.ep.zalenium.streams;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.io.File;
import java.util.Map;

public class MapInputStreamAdapter implements  InputStreamGroupIterator{

    Iterator<Map.Entry<String, File>> streams;

    public MapInputStreamAdapter(Map<String, File> files) {
        streams = files.entrySet().stream()
                .iterator();
    }

    @Override
    public InputStreamDescriptor next() throws IOException {
        return streams.hasNext() ? getNextDescriptor() : null;
    }

    private InputStreamDescriptor getNextDescriptor() throws IOException {
        Map.Entry<String, File> entry = streams.next();
        return new DefaultInputStreamDescriptor(new FileInputStream(entry.getValue()), entry.getKey());
    }
}