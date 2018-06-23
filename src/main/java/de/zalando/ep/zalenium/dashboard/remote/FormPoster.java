package de.zalando.ep.zalenium.dashboard.remote;

import java.net.URI;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;



public class FormPoster {

    private String remoteHost;

    public void setRemoteHost(String host) {
        remoteHost = host;
    }


    public String getRemoteHost() {
        return remoteHost;
    }

    public HttpResponse post(List<FormField> fields) throws Exception {
        if( null == remoteHost || 0 == remoteHost.length() ) {
            throw new IllegalArgumentException("remoteHost");
        }
        if( null == fields ) {
            throw new IllegalArgumentException("fields");
        }


        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost uploadFile = new HttpPost(new URI(remoteHost));
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        for (FormField field : fields) {

            if ( field instanceof FormKeyValuePair) {
                builder.addTextBody(field.keyName, (((FormKeyValuePair) field).value), ((FormKeyValuePair) field).mimeType);
            } else if ( field instanceof FormFile) {
                FormFile tmp = (FormFile)field;
                builder.addBinaryBody(tmp.keyName, tmp.stream, tmp.mimeType, tmp.fileName);
            }
        }

        HttpEntity multipart = builder.build();
        uploadFile.setEntity(multipart);
        CloseableHttpResponse response = httpClient.execute(uploadFile);
        return response;
    }
}
