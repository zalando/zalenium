package de.zalando.ep.zalenium.dashboard.remote;

import org.apache.http.entity.ContentType;
import java.io.InputStream;

public class FormFile extends FormField {
    public InputStream stream;
    public ContentType mimeType;
    public String fileName;
}
