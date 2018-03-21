package de.zalando.ep.zalenium.util;

import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.exception.NewSessionException;
import org.openqa.grid.web.servlet.handler.RequestHandler;
import org.openqa.grid.web.servlet.handler.SeleniumBasedRequest;

import javax.servlet.http.HttpServletResponse;

public class MockedRequestHandler extends RequestHandler {

    MockedRequestHandler(
            SeleniumBasedRequest request,
            HttpServletResponse response,
            GridRegistry registry) {
        super(request, response, registry);
    }

    public void setSession(TestSession session) {
        super.setSession(session);
    }

    @Override
    protected void forwardRequest(TestSession session, RequestHandler handler) {
        // do nothing
    }

    @Override
    public void forwardNewSessionRequestAndUpdateRegistry(TestSession session)
            throws NewSessionException {
        // do nothing
    }

}
