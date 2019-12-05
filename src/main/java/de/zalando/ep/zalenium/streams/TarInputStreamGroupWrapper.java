package de.zalando.ep.zalenium.streams;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;

public class TarInputStreamGroupWrapper implements InputStreamGroupIterator{

    private final TarArchiveInputStream stream;
    private TarArchiveEntry currentTarEntry;
    private boolean started = false;

    public TarInputStreamGroupWrapper(TarArchiveInputStream stream) {
        if(stream == null) {
            throw new RuntimeException("Stream cannot be null");
        }

        this.stream = stream;
    }

    @Override
    public InputStreamDescriptor next() throws IOException {
        while(!started || (currentTarEntry != null && currentTarEntry.isDirectory())) {
            started = true;
            currentTarEntry = stream.getNextTarEntry();
        }
        return currentTarEntry == null ? null : new DefaultInputStreamDescriptor(stream, currentTarEntry.getName());
    }


}