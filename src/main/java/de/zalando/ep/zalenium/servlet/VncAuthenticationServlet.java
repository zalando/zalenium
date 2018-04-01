package de.zalando.ep.zalenium.servlet;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.web.servlet.RegistryBasedServlet;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;

/**
 * Authenticates the VNC requests to ensure that they are trying to access hosts and ports that the registry knows
 * about, otherwise authentication is denied. 
 * <p>
 * This is to stop the nginx proxy becoming an open proxy into the SDN.
 */
public class VncAuthenticationServlet extends RegistryBasedServlet {
    
    private static final int UNAUTHORISED = 403;
    private static final int AUTHORISED = 200;
    private static Pattern VNC_REGEX = Pattern.compile("^\\/vnc\\/host\\/(?<host>[^\\/]+)\\/port\\/(?<port>\\d+)\\/");
    private static Pattern WEB_SOCKET_REGEX = Pattern.compile("^\\/proxy\\/(?<host>[^:]+):(?<port>\\d+)\\/websockify");
    
    private static final Logger LOGGER = LoggerFactory.getLogger(VncAuthenticationServlet.class.getName());

    public VncAuthenticationServlet() {
        super(null);
    }
    
    public VncAuthenticationServlet(GridRegistry registry) {
        super(registry);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            authenticate(req, resp);
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            authenticate(req, resp);
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }
    
    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) {
        try {
            authenticate(req, resp);
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }

    private void authenticate(HttpServletRequest request, HttpServletResponse response) {
        // We could get a null header, so we should handle this situation
        Optional<String> urlToAuthenticate = Optional.ofNullable(request.getHeader("X-Original-URI"));
        
        Optional<String> path = urlToAuthenticate.flatMap(s -> {
            try {
                return Optional.of(new URI(s).getPath());
            } catch (URISyntaxException e) {
                LOGGER.error("Failed to parse url [" + s + "]", e);
                return Optional.empty();
            }
        });
        
        // Apply the regexes looking for the first one that matches
        Optional<Matcher> matcher = path.flatMap(p -> getRegexStream().map(regex -> regex.matcher(p)).filter(Matcher::find).findFirst());
        
        Optional<String> host = matcher.map(res -> res.group("host"));
        Optional<String> port = matcher.map(res -> res.group("port"));
        
        // Find the first docker selenium remote proxies that matches the host and port, if so return 200, otherwise 403 forbidden
        Integer httpCode = StreamSupport.stream(getRegistry().getAllProxies().spliterator(), false)
        .filter(proxy -> proxy instanceof DockerSeleniumRemoteProxy)
        .map(proxy -> ((DockerSeleniumRemoteProxy)proxy).getRegistration())
        .filter(reg -> host.equals(Optional.of(reg.getIpAddress())) && port.equals(Optional.of(reg.getNoVncPort().toString())))
        .findAny()
        .map(x -> AUTHORISED)
        .orElse(UNAUTHORISED);
        
        response.setStatus(httpCode);
    }
    
    private Stream<Pattern> getRegexStream() {
        return Stream.of(VNC_REGEX, WEB_SOCKET_REGEX);
    }
}
