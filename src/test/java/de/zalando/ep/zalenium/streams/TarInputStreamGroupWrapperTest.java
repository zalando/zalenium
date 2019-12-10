package de.zalando.ep.zalenium.streams;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TarInputStreamGroupWrapperTest {

    @Test
    public void itShouldEnableWrappingNamedInputStreamNamePairs() throws IOException {
        InputStreamGroupIterator adapter = new TarInputStreamGroupWrapper(new TarArchiveInputStream(MapInputStreamAdapterTest.class.getClassLoader().getResourceAsStream("samples/files.tar")));
        InputStreamDescriptor next;
        while((next = adapter.next()) != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(next.get()));
            Assert.assertEquals("./file" + br.readLine() + ".txt", next.name());
        }
    }
}
