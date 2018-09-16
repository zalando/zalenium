package de.zalando.ep.zalenium.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommonProxyUtilities {

    private static final Logger LOG = LoggerFactory.getLogger(CommonProxyUtilities.class.getName());
    public static final String metadataCookieName = "zaleniumMetadata";

    /*
        Reading a JSON with DockerSelenium capabilities from a given URL
        http://stackoverflow.com/questions/4308554/simplest-way-to-read-json-from-a-url-in-java
        http://stackoverflow.com/questions/496651/connecting-to-remote-url-which-requires-authentication-using-java
     */
    public JsonElement readJSONFromUrl(String jsonUrl, String user, String password) {
        int maxAttempts = 10;
        int currentAttempts = 0;
        while (currentAttempts < maxAttempts) {
            try {
                URL url = new URL(jsonUrl);
                URLConnection urlConnection = url.openConnection();
                String userPass = user + ":" + password;
                String basicAuth = "Basic " + new String(new Base64().encode(userPass.getBytes()));
                urlConnection.setRequestProperty("Authorization", basicAuth);

                InputStream is = urlConnection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                String jsonText = readAll(rd);
                is.close();
                return new JsonParser().parse(jsonText);
            } catch (Exception e) {
                currentAttempts++;
                LOG.error(e.toString(), e);
                if (currentAttempts >= maxAttempts) {
                    LOG.error(e.toString(), e);
                } else {
                    LOG.info("Trying download once again from " + jsonUrl);
                    try {
                        Thread.sleep(1000 * 5);
                    } catch (InterruptedException iE) {
                        LOG.debug(iE.toString(), iE);
                    }
                }
            }
        }
        return null;
    }

    public String currentLocalPath() {
        try {
            File jarLocation = new File(CommonProxyUtilities.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI().getPath());
            return jarLocation.getParent();
        } catch (URISyntaxException e) {
            LOG.error(e.toString(), e);
        }
        return null;
    }

    /*
        Downloading a file, method adapted from:
        http://code.runnable.com/Uu83dm5vSScIAACw/download-a-file-from-the-web-for-java-files-and-save
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void downloadFile(String fileUrl, String fileNameWithFullPath, String user, String password,
                             boolean authenticate)
            throws InterruptedException {
        int maxAttempts = 10;
        int currentAttempts = 0;
        // Videos are usually not ready right away, we put a little sleep to avoid falling into the catch/retry.
        Thread.sleep(1000 * 5);
        while (currentAttempts < maxAttempts) {
            try {
                URL link = new URL(fileUrl);
                String userPass = user + ":" + password;
                URLConnection urlConnection = link.openConnection();
                if (authenticate) {
                    String basicAuth = "Basic " + new String(new Base64().encode(userPass.getBytes()));
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
                File fileToDownload = new File(fileNameWithFullPath);
                File fileToDownloadFolder = fileToDownload.getParentFile();
                if (!fileToDownloadFolder.exists()) {
                    fileToDownloadFolder.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(fileNameWithFullPath);
                fos.write(response);
                fos.close();
                //End download code
                currentAttempts = maxAttempts + 1;
                LOG.info("File downloaded to " + fileNameWithFullPath);
            } catch (IOException e) {
                // Catching this exception generally means that the file was not ready, so we try again.
                currentAttempts++;
                if (currentAttempts >= maxAttempts) {
                    LOG.info(e.toString(), e);
                } else {
                    LOG.info("Trying download once again from " + fileUrl);
                    Thread.sleep(currentAttempts * 5 * 1000);
                }
            } catch (Exception e) {
                currentAttempts = maxAttempts + 1;
                LOG.error(e.toString(), e);
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    public String getDateAndTimeFormatted(Date d) {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        return dateFormat.format(d);
    }

    @SuppressWarnings("WeakerAccess")
    public String getShortDateAndTime(Date d) {
        DateFormat dateFormat = new SimpleDateFormat("dd-MMM HH:mm:ss");
        return dateFormat.format(d);
    }
    
    @SuppressWarnings("WeakerAccess")
    public Date getDateAndTime(Date d, int addtionalDays) {
        return new Date(d.getTime() + addtionalDays * 86400000);
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
