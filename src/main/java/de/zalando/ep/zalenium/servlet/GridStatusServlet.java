package de.zalando.ep.zalenium.servlet;

import static com.google.common.net.MediaType.JSON_UTF_8;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.openqa.grid.web.servlet.RegistryBasedServlet;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

public class GridStatusServlet extends RegistryBasedServlet {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    public GridStatusServlet() {
        this(null);
    }

    public GridStatusServlet(ZaleniumRegistry registry) {
        super(registry);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (getRegistry() == null) {
            throw new RuntimeException("Registry is null");
        }

        int allProxiesCount = Optional.ofNullable(getRegistry().getAllProxies().size()).orElse(0);

        GridStatus status = ZaleniumConfiguration.getDesiredContainersOnStartup() <= allProxiesCount ? GridStatus.FULL_CAPACITY
                : allProxiesCount > 0 ? GridStatus.AVAILABLE
                : GridStatus.UNAVAILABLE;

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        byte[] statusReturn = gson.toJson(status).getBytes(UTF_8);
        response.setStatus(HTTP_OK);
        response.setContentType(JSON_UTF_8.toString());
        response.setContentLength(statusReturn.length);
        try (ServletOutputStream out = response.getOutputStream()) {
            out.write(statusReturn);
        }
    }

    enum GridStatus {
        AVAILABLE, /* One proxy registered */
        FULL_CAPACITY, /* All desired proxy registered */
        UNAVAILABLE /* Grid without proxy registered */
    }
}
