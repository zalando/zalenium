package de.zalando.ep.zalenium.dashboard.remote;

import de.zalando.ep.zalenium.dashboard.TestInformation;
import org.apache.http.entity.ContentType;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RemoteSeleniumLogDashboard extends RemoteDashboard {

    @Override
    public void updateDashboard(TestInformation testInformation) throws Exception {
        if (null == testInformation) throw new IllegalArgumentException("testInformation");

        if(!isEnabled()) {
            return;
        }

        List<FormField> fields = new ArrayList<>();

        FormFile uploadFile = new FormFile();
        uploadFile.keyName = "seleniumlog";
        uploadFile.mimeType = ContentType.create("text/plain");
        uploadFile.stream = new FileInputStream(Paths.get( testInformation.getVideoFolderPath(), testInformation.getSeleniumLogFileName()).toString());
        uploadFile.fileName  = testInformation.getSeleniumLogFileName();
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
