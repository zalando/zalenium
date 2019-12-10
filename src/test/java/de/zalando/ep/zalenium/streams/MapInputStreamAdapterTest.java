package de.zalando.ep.zalenium.streams;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapInputStreamAdapterTest {

    @Test
    public void itShouldEnableWrappingNamedInputStreamNamePairs() throws IOException {
        Map<String, File> files = new HashMap<String, File>() {{
            put("1", new File(MapInputStreamAdapterTest.class.getClassLoader().getResource("samples/file1.txt").getFile()));
            put("2", new File(MapInputStreamAdapterTest.class.getClassLoader().getResource("samples/file2.txt").getFile()));
            put("3", new File(MapInputStreamAdapterTest.class.getClassLoader().getResource("samples/file3.txt").getFile()));
        }};
        InputStreamGroupIterator adapter = new MapInputStreamAdapter(files);
        InputStreamDescriptor next;
        while((next = adapter.next()) != null) {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(next.get()))) {
                Assert.assertEquals(br.readLine(), next.name());
            }
        }
    }

    @Test
    public void itShouldReturnAnEmptyIteratorIfNoFilesProvided() throws IOException {
        InputStreamGroupIterator adapter = new MapInputStreamAdapter(Collections.emptyMap());
        Assert.assertNull(adapter.next());
    }
}
