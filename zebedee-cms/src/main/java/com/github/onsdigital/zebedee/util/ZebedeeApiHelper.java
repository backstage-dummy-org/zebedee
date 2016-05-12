package com.github.onsdigital.zebedee.util;

import com.github.onsdigital.zebedee.api.Collections;
import com.github.onsdigital.zebedee.api.Root;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.Session;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.CollectionWriter;
import com.github.onsdigital.zebedee.model.ZebedeeCollectionReader;
import com.github.onsdigital.zebedee.model.ZebedeeCollectionWriter;
import com.github.onsdigital.zebedee.reader.CollectionReader;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static com.github.onsdigital.zebedee.logging.ZebedeeLogBuilder.logError;

/**
 * Class adds a wrapper around common Zebedee operations with which are implemented as static methods.
 * Using this wrapper to access the static methods instead of accessing them directly means these calls can be easily
 * replaced with mock implementations making unit tests easier and cleaner to write.
 * Also adds additional error logging and rethrows all exceptions as a {@link ZebedeeException} removing unnecessary
 * try/catch/throws code from calling classes.
 */
public class ZebedeeApiHelper {

    private static final String COLLECTION_WRI_ERROR_MSG = "Could not obtain collection writer for requested collection";
    private static final String COLLECTION_READ_ERROR_MSG = "Could not obtain collection reader for requested collection";
    private static final String COLLECTION_NOT_FOUND_MSG = "Could not find requested collection.";
    private static final String SESSION_NOT_FOUND_MSG = "Could not get session from request";

    private static final ZebedeeApiHelper instance = new ZebedeeApiHelper();

    public static ZebedeeApiHelper getInstance() {
        return instance;
    }

    private ZebedeeApiHelper() {
        // use getInstance() method.
    }

    public Session getSession(HttpServletRequest request) throws ZebedeeException {
        try {
            return Root.zebedee.sessions.get(request);
        } catch (IOException e) {
            logError(e, SESSION_NOT_FOUND_MSG).log();
            throw new UnauthorizedException(SESSION_NOT_FOUND_MSG);
        }
    }

    public CollectionWriter getZebedeeCollectionWriter(Collection collection, Session session)
            throws ZebedeeException {
        try {
            return new ZebedeeCollectionWriter(Root.zebedee, collection, session);
        } catch (IOException e) {
            logError(e, COLLECTION_WRI_ERROR_MSG)
                    .collectionId(collection)
                    .user(session.email)
                    .log();
            throw new BadRequestException(COLLECTION_WRI_ERROR_MSG);
        }
    }

    public CollectionReader getZebedeeCollectionReader(Collection collection, Session session) throws ZebedeeException {
        try {
            return new ZebedeeCollectionReader(Root.zebedee, collection, session);
        } catch (IOException e) {
            logError(e, COLLECTION_READ_ERROR_MSG)
                    .collectionId(collection)
                    .user(session.email)
                    .log();
            throw new BadRequestException(COLLECTION_READ_ERROR_MSG);
        }

    }

    public Collection getCollection(HttpServletRequest request) throws ZebedeeException {
        try {
            return Collections.getCollection(request);
        } catch (IOException e) {
            logError(e, COLLECTION_NOT_FOUND_MSG).log();
            throw new NotFoundException(COLLECTION_NOT_FOUND_MSG);
        }
    }
}
