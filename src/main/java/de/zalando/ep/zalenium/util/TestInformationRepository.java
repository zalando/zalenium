package de.zalando.ep.zalenium.util;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * A collection of <code>TestInformation</code> forming the repository to be serialized as a JSON file and consumed by
 * the dashboard
 * 
 * @author robertzimmermann
 */
public class TestInformationRepository {

    private List<TestInformation> repository = new ArrayList<>();

    public static TestInformationRepository fromJsonString(String repositoryJsonString) {
        TestInformationRepository testInformationRepository = new TestInformationRepository();
        Type listType = new TypeToken<ArrayList<TestInformation>>() {
        }.getType();
        testInformationRepository.repository = new Gson().fromJson(repositoryJsonString, listType);
        if (testInformationRepository.repository == null) {
            testInformationRepository.repository = new ArrayList<>();
        }
        return testInformationRepository;
    }

    public void add(TestInformation testInformation) {
        repository.add(testInformation);
    }

    public int size() {
        return repository.size();
    }

    public TestInformation get(int index) {
        return repository.get(index);
    }

    public String toJson() {
        return new Gson().toJson(repository);
    }

    public String getVideoFolderPath() {
        String path = "";
        if (size() > 0) {
            path = get(0).getVideoFolderPath();
        }
        return path;
    }
}
