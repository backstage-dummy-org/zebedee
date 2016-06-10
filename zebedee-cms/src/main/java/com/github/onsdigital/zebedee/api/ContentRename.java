package com.github.onsdigital.zebedee.api;

import com.github.davidcarboni.restolino.framework.Api;
import com.github.onsdigital.zebedee.audit.Audit;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.Session;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import java.io.IOException;

@Api
public class ContentRename {
    /**
     * Just like content move but has additional checks to ensure only a rename takes place in the same directory.
     *
     * @param request
     * @param response
     * @return
     * @throws IOException
     * @throws BadRequestException
     * @throws UnauthorizedException
     */
    @POST
    public boolean RenameContent(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ZebedeeException {

        Session session = Root.zebedee.sessions.get(request);
        com.github.onsdigital.zebedee.model.Collection collection = Collections.getCollection(request);

        String uri = request.getParameter("uri");
        String toUri = request.getParameter("toUri");

        Root.zebedee.collections.renameContent(session, collection, uri, toUri);
        Audit.Event.CONTENT_RENAMED
                .parameters()
                .host(request)
                .collection(collection)
                .fromTo(uri, toUri)
                .actionedBy(session.email)
                .log();
        return true;
    }
}
