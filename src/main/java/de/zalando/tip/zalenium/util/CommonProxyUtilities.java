package de.zalando.tip.zalenium.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CommonProxyUtilities {

    private static final Logger LOG = Logger.getLogger(CommonProxyUtilities.class.getName());

    /*
        Reading a JSON with DockerSelenium capabilities from a given URL
        http://stackoverflow.com/questions/4308554/simplest-way-to-read-json-from-a-url-in-java
        http://stackoverflow.com/questions/496651/connecting-to-remote-url-which-requires-authentication-using-java
     */
    public JsonElement readJSONFromUrl(String jsonUrl) {
        try {
            URL url = new URL(jsonUrl);
            URLConnection urlConnection = url.openConnection();

            if (url.getUserInfo() != null) {
                String basicAuth = "Basic " + new String(new Base64().encode(url.getUserInfo().getBytes()));
                urlConnection.setRequestProperty("Authorization", basicAuth);
            }

            InputStream is = urlConnection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            is.close();
            return new JsonParser().parse(jsonText);
        } catch (Exception e) {
            LOG.log(Level.FINE, e.toString(), e);
        }
        return null;
    }

    public JsonElement readJSONFromFile(String fileName) {
        try(FileReader fr = new FileReader(new File(currentLocalPath(), fileName))) {
            BufferedReader rd = new BufferedReader(fr);
            String jsonText = readAll(rd);
            return new JsonParser().parse(jsonText);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e.toString(), e);
        }
        return null;
    }

    public String currentLocalPath() {
        try {
            File jarLocation = new File(CommonProxyUtilities.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI().getPath());
            return jarLocation.getParent();
        } catch (URISyntaxException e) {
            LOG.log(Level.SEVERE, e.toString(), e);
        }
        return null;
    }

    /*
        Downloading a file, method adapted from:
        http://code.runnable.com/Uu83dm5vSScIAACw/download-a-file-from-the-web-for-java-files-and-save
     */
    public void downloadFile(String fileNameWithFullPath, String url) throws InterruptedException {
        int maxAttempts = 10;
        int currentAttempts = 0;
        // Videos are usually not ready right away, we put a little sleep to avoid falling into the catch/retry.
        Thread.sleep(1000 * 5);
        while (currentAttempts < maxAttempts) {
            try {
                URL link = new URL(url);
                URLConnection urlConnection = link.openConnection();

                if (link.getUserInfo() != null) {
                    String basicAuth = "Basic " + new String(new Base64().encode(link.getUserInfo().getBytes()));
                    urlConnection.setRequestProperty("Authorization", basicAuth);
                }

                //Code to download
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while (-1!=(n=in.read(buf)))
                {
                    out.write(buf, 0, n);
                }
                out.close();
                in.close();
                byte[] response = out.toByteArray();

                FileOutputStream fos = new FileOutputStream(fileNameWithFullPath);
                fos.write(response);
                fos.close();
                //End download code
                LOG.log(Level.INFO, "Video downloaded from " + url + " to " + fileNameWithFullPath);
                currentAttempts = maxAttempts + 1;
            } catch (IOException e) {
                // Catching this exception generally means that the file was not ready, so we try again.
                currentAttempts++;
                if (currentAttempts >= maxAttempts) {
                    LOG.log(Level.SEVERE, e.toString(), e);
                } else {
                    LOG.log(Level.INFO, "Trying download once again from " + url);
                    Thread.sleep(currentAttempts * 5 * 1000);
                }
            } catch (Exception e) {
                currentAttempts = maxAttempts + 1;
                LOG.log(Level.SEVERE, e.toString(), e);
            }
        }
    }

    public String getCurrentDateAndTimeFormatted() {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        return dateFormat.format(new Date());
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = reader.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

}
