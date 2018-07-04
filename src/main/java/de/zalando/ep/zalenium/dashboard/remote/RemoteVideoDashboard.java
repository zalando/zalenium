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

        FormFile uploadFile = new FormFile();
        uploadFile.keyName = "video";
        uploadFile.mimeType = ContentType.create("video/mp4");
        uploadFile.stream = new FileInputStream(Paths.get(testInformation.getVideoFolderPath(), testInformation.getFileName()).toString());
        uploadFile.fileName = testInformation.getFileName();
        fields.add(uploadFile);

        this.setupMetadata(testInformation).addProperty("Type", "video");

        FormKeyValuePair kvp = new FormKeyValuePair();
        kvp.keyName = "metadata";
        kvp.mimeType = ContentType.create("application/json");
        kvp.value = jsonToString(testInformation.getMetadata());
        fields.add(kvp);

        this.getFormPoster().post(fields);
    }
}
