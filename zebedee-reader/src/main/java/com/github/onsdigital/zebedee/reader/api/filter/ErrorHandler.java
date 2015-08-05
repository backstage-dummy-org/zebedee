package com.github.onsdigital.zebedee.reader.api.filter;

import com.github.davidcarboni.restolino.api.RequestHandler;
import com.github.davidcarboni.restolino.framework.ServerError;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.reader.api.ServerResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The error handler catches various exceptions and sets the response status code accordingly.
 */
public class ErrorHandler implements ServerError {
    @Override
    public ServerResponse handle(HttpServletRequest req, HttpServletResponse res, RequestHandler requestHandler, Throwable t) {

        // If it's an ApiException subclass, set the status code and message
        if (t != null && ZebedeeException.class.isAssignableFrom(t.getClass())) {
            ZebedeeException exception = (ZebedeeException) t;
            res.setStatus(exception.statusCode);
            System.out.println(exception.statusCode + ": " + exception.getMessage());
            return new ServerResponse(exception.getMessage());
        }
        // Otherwise leave the default 500 response
        t.printStackTrace();
        return new ServerResponse("Internal Server Error");
    }
}