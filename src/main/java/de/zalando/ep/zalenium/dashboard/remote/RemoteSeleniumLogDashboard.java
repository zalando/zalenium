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

        fields.add(new FormFile() {{
                       keyName = "seleniumlog";
                       mimeType = ContentType.create("text/plain");
                       stream = new FileInputStream(Paths.get( testInformation.getVideoFolderPath(), testInformation.getSeleniumLogFileName()).toString());
                       fileName  = testInformation.getSeleniumLogFileName();
                   }}
        );

        this.setupMetadata(testInformation).addProperty("Type", "logfile");

        fields.add(new FormKeyValuePair() {{
                       keyName = "metadata";
                       mimeType = ContentType.create("application/json");
                       value = jsonToString(testInformation.getMetadata());
                   }}
        );

        this.getFormPoster().Post(fields);

    }
}
