package com.example.aml.transaction;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

final class CsvInput {
    private CsvInput() {}

    static CSVParser open(MultipartFile file) throws IOException {
        InputStream input = file.getInputStream();
        PushbackInputStream pushback = new PushbackInputStream(input, 3);
        try {
            byte[] first = pushback.readNBytes(3);
            if (first.length != 3 || (first[0] & 0xff) != 0xef || (first[1] & 0xff) != 0xbb
                    || (first[2] & 0xff) != 0xbf) {
                pushback.unread(first);
            }
            return CSVParser.parse(new InputStreamReader(pushback, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).get());
        } catch (IOException | RuntimeException exception) {
            pushback.close();
            throw exception;
        }
    }
}
