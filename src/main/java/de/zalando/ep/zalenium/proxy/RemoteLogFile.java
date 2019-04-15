package de.zalando.ep.zalenium.proxy;

public class RemoteLogFile {
    private String remoteUrl;
    private boolean authenticationRequired;
    private String localFileName;

    public RemoteLogFile(String remoteUrl, String localFileName, boolean authenticationRequired) {
        this.remoteUrl = remoteUrl;
        this.localFileName = localFileName;
        this.authenticationRequired = authenticationRequired;
    }

    public String getRemoteUrl(){
        return remoteUrl;
    }

    public String getLocalFileName(){
        return localFileName;
    }

    public boolean isAuthenticationRequired(){
        return authenticationRequired;
    }
}