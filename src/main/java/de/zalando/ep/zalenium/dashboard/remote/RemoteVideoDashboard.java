package de.zalando.ep.zalenium.dashboard.remote;

import de.zalando.ep.zalenium.dashboard.TestInformation;
import org.apache.http.entity.ContentType;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RemoteVideoDashboard extends RemoteDashboard {

    @Override
    public void updateDashboard(TestInformation testInformation) throws Exception {
        if (null == testInformation) throw new IllegalArgumentException("testInformation");

        if(!isEnabled() || !testInformation.isVideoRecorded() ) {
            return;
        }

        List<FormField> fields = new ArrayList<>();

        fields.add(new FormFile() {{
                       keyName = "video";
                       mimeType = ContentType.create("video/mp4");
                       stream = new FileInputStream(Paths.get(testInformation.getVideoFolderPath(), testInformation.getFileName()).toString());
                       fileName = testInformation.getFileName();
                   }}
        );

        this.setupMetadata(testInformation).addProperty("Type", "video");

        fields.add(new FormKeyValuePair() {{
               keyName = "metadata";
               mimeType = ContentType.create("application/json");
               value = jsonToString(testInformation.getMetadata());
           }}
        );

        this.getFormPoster().Post(fields);
    }
}
