package de.zalando.ep.zalenium.dashboard.remote;

import de.zalando.ep.zalenium.dashboard.TestInformation;
import org.apache.http.entity.ContentType;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RemoteLogDashboard extends RemoteDashboard {

    private String logType;

    public RemoteLogDashboard(String logType) {
        this.logType = logType;
    }

    @Override
    public void updateDashboard(TestInformation testInformation) throws Exception {
        if (null == testInformation) {
            throw new IllegalArgumentException("testInformation");
        }

        if(!isEnabled()) {
            return;
        }

        List<FormField> fields = new ArrayList<>();
        FormFile uploadFile = new FormFile();
        uploadFile.keyName = this.logType;
        uploadFile.mimeType = ContentType.create("text/plain");
        if ("driverlog".equalsIgnoreCase(uploadFile.keyName)) {
            uploadFile.stream = new FileInputStream(Paths.get(testInformation.getVideoFolderPath(),
                    testInformation.getBrowserDriverLogFileName()).toString());
        } else {
            // We assume that it is "seleniumlog"
            uploadFile.stream = new FileInputStream(Paths.get(testInformation.getVideoFolderPath(),
                    testInformation.getSeleniumLogFileName()).toString());
        }
        uploadFile.fileName = testInformation.getTestNameNoExtension()+".log";
        fields.add(uploadFile);

        this.setupMetadata(testInformation).addProperty("Type", "logfile");
        FormKeyValuePair kvp = new FormKeyValuePair();
        kvp.keyName = "metadata";
        kvp.mimeType = ContentType.create("application/json");
        kvp.value = jsonToString(testInformation.getMetadata());
        fields.add(kvp);

        this.getFormPoster().post(fields);
    }
}
