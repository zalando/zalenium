package de.zalando.ep.zalenium.dashboard.remote;


import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.dashboard.DashboardInterface;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Represents some external system that stores and indexes artifacts related to test execution
 */
public abstract class RemoteDashboard implements DashboardInterface {

    private Logger LOGGER = Logger.getLogger(RemoteDashboard.class.getName());
    private FormPoster formPoster = new FormPoster();
    public static CommonProxyUtilities utils = new CommonProxyUtilities();


    public RemoteDashboard setFormPoster(FormPoster poster) { this.formPoster = poster; return this;}
    public FormPoster getFormPoster() { return this.formPoster;}

    public void setUrl(String Url) { this.getFormPoster().setRemoteHost(Url);}
    public String getUrl() { return this.getFormPoster().getRemoteHost();}

    protected String jsonToString(JsonObject obj) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(obj);
    }
    protected JsonObject setupMetadata(TestInformation ti) {
        if(null == ti.getMetadata()) {
            ti.setMetadata( new JsonObject());
        }
        return ti.getMetadata();
    }

    public boolean isEnabled() {
        if (null == formPoster || null == formPoster.getRemoteHost()) {
            LOGGER.log(Level.FINE, "Remote dashboard is disabled.");
            return false;
        } else {
            LOGGER.log(Level.FINER, "Remote dashboard is logging to " + formPoster.getRemoteHost());
            return true;
        }
    }

    public void resetDashboard() throws Exception {throw new UnsupportedOperationException();}
    public void cleanupDashboard() throws Exception {throw new UnsupportedOperationException();}
    public abstract void updateDashboard(TestInformation testInformation) throws Exception;
}
