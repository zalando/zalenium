package de.zalando.ep.zalenium.streams;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;

public class TarInputStreamGroupWrapper implements InputStreamGroupIterator{

    private final TarArchiveInputStream stream;
    private TarArchiveEntry currentTarEntry;

    public TarInputStreamGroupWrapper(TarArchiveInputStream stream) {
        if(stream == null) {
            throw new RuntimeException("Stream cannot be null");
        }
        this.stream = stream;
    }

    @Override
    public InputStreamDescriptor next() throws IOException {
        do {
            currentTarEntry = stream.getNextTarEntry();
        } while(currentTarEntry != null && currentTarEntry.isDirectory());

        return currentTarEntry == null ? null : new DefaultInputStreamDescriptor(stream, currentTarEntry.getName());
    }

}