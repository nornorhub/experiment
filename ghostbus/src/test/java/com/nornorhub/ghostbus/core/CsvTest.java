package com.nornorhub.ghostbus.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

public class CsvTest {

    private static Csv csv(String text) throws IOException {
        return new Csv(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void readsColumnsByName() throws IOException {
        Csv c = csv("﻿stop_id,stop_name,stop_lat\r\n1,MAIN ST,40.1\r\n\r\n2,BROAD ST,40.2\n");
        int id = c.column("stop_id");
        int name = c.column("stop_name");
        assertEquals(0, id);
        assertEquals(-1, c.column("stop_code"));

        List<String> row = c.next();
        assertEquals("1", Csv.get(row, id));
        assertEquals("MAIN ST", Csv.get(row, name));
        assertEquals("", Csv.get(row, c.column("stop_code")));
        assertEquals("BROAD ST", Csv.get(c.next(), name));
        assertNull(c.next());
    }

    @Test
    public void handlesQuotes() throws IOException {
        Csv c = csv("a,b,c\n\"x, y\",\"say \"\"hi\"\"\",\"two\nlines\"\n");
        List<String> row = c.next();
        assertEquals("x, y", row.get(0));
        assertEquals("say \"hi\"", row.get(1));
        assertEquals("two\nlines", row.get(2));
    }
}
