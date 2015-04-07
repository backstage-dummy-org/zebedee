package com.github.onsdigital.zebedee.api;

import com.github.davidcarboni.restolino.framework.Api;
import com.github.onsdigital.zebedee.json.ResultMessage;
import com.github.onsdigital.zebedee.json.Session;
import org.eclipse.jetty.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import java.io.IOException;

@Api
public class Complete {
    /**
     * Set a page to the complete state.
     *
     * @param request
     * @param response
     * @return
     * @throws java.io.IOException
     */
    @POST
    public ResultMessage complete(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Locate the collection:
        com.github.onsdigital.zebedee.model.Collection collection = Collections.getCollection(request);
        if (collection == null) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            return new ResultMessage("Collection not found.");
        }

        // Locate the path:
        String uri = request.getParameter("uri");

        java.nio.file.Path path = collection.getInProgressPath(uri);
        if (path == null) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            return new ResultMessage("URI not in progress.");
        }

        // Check we're requesting a file:
        if (java.nio.file.Files.isDirectory(path)) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            return new ResultMessage("URI does not represent a file.");
        }

        // Attempt to review:
        Session session = Root.zebedee.sessions.get(request);
        if (!collection.complete(session.email, uri)) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            return new ResultMessage("URI was not reviewed.");
        }

        collection.save();

        return new ResultMessage("URI reviewed.");
    }
}

