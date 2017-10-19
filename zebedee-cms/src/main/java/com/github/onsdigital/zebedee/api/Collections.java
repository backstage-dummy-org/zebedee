package com.github.onsdigital.zebedee.api;

import com.github.davidcarboni.restolino.framework.Api;
import com.github.davidcarboni.restolino.helpers.Path;
import com.github.onsdigital.zebedee.content.util.ContentUtil;
import com.github.onsdigital.zebedee.dataset.api.DatasetAPIClient;
import com.github.onsdigital.zebedee.dataset.api.exception.DatasetAPIException;
import com.github.onsdigital.zebedee.dataset.api.exception.DatasetNotFoundException;
import com.github.onsdigital.zebedee.dataset.api.exception.UnexpectedResponseException;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnexpectedErrorException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.CollectionDataset;
import com.github.onsdigital.zebedee.json.CollectionDatasetVersion;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.CollectionDescriptions;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.CollectionOwner;
import com.github.onsdigital.zebedee.service.DatasetService;
import com.github.onsdigital.zebedee.service.ZebedeeDatasetService;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.util.ZebedeeCmsService;
import com.google.gson.JsonSyntaxException;
import org.apache.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import static com.github.onsdigital.zebedee.logging.ZebedeeLogBuilder.logError;
import static com.github.onsdigital.zebedee.logging.ZebedeeLogBuilder.logInfo;

@Api
public class Collections {

    private ZebedeeCmsService zebedeeCmsService;
    private DatasetService datasetService;

    /**
     * Default constructor used instantiates dependencies itself.
     */
    public Collections() {
        zebedeeCmsService = ZebedeeCmsService.getInstance();
        datasetService = new ZebedeeDatasetService(
                DatasetAPIClient.getInstance(),
                zebedeeCmsService.getInstance());
    }

    /**
     * Constructor allowing dependencies to be injected.
     *
     * @param zebedeeCmsService
     * @param datasetService
     */
    public Collections(ZebedeeCmsService zebedeeCmsService, DatasetService datasetService) {
        this.zebedeeCmsService = zebedeeCmsService;
        this.datasetService = datasetService;
    }

    /**
     * Retrieves current {@link CollectionDescription} objects
     *
     * @param request
     * @param response
     * @return a List of {@link Collection#description}.
     * @throws IOException
     */
    @GET
    public CollectionDescriptions get(HttpServletRequest request, HttpServletResponse response)
            throws ZebedeeException {
        try {
            Session session = Root.zebedee.getSessionsService().get(request);
            CollectionDescriptions result = new CollectionDescriptions();
            List<Collection> collections = Root.zebedee.getCollections().list();
            CollectionOwner collectionOwner = zebedeeCmsService.getPublisherType(session.getEmail());

            for (Collection collection : collections) {
                if (Root.zebedee.getPermissionsService().canView(session, collection.description)
                        && (collection.description.collectionOwner.equals(collectionOwner))) {

                    CollectionDescription description = new CollectionDescription();
                    description.id = collection.description.id;
                    description.name = collection.description.name;
                    description.publishDate = collection.description.publishDate;
                    description.approvalStatus = collection.description.approvalStatus;
                    description.type = collection.description.type;
                    description.teams = collection.description.teams;
                    result.add(description);
                }
            }

            // sort the collections alphabetically by name.
            java.util.Collections.sort(result, Comparator.comparing(o -> o.name));

            return result;
        } catch (IOException e) {
            logError(e, "Unexpected error while attempting to get collections")
                    .logAndThrow(UnexpectedErrorException.class);
        }
        return null;
    }

    /**
     * Put endpoints for /collections.
     * This supports only /collections/{collection_id}/datasets/{dataset_id}
     */
    @PUT
    public void put(HttpServletRequest request, HttpServletResponse response) throws ZebedeeException, IOException, DatasetAPIException {

        Session session = zebedeeCmsService.getSession(request);
        if (!zebedeeCmsService.getPermissions().canEdit(session)) {
            logInfo("Forbidden request made to the collection endpoint").log();
            response.setStatus(HttpStatus.SC_FORBIDDEN);
            return;
        }

        Path path = Path.newInstance(request);
        List<String> pathSegments = path.segments();

        if (!isValidPath(response, path, pathSegments)) return;

        String collectionID = pathSegments.get(1);
        String datasetID = pathSegments.get(3);

        try {
            switch (pathSegments.size()) {
                case 4: // /collections/{collection_id}/datasets/{dataset_id}

                    updateDatasetInCollection(collectionID, datasetID, request);
                    break;

                case 8: // /collections/{collection_id}/datasets/{dataset_id}/editions/{}/versions/{}

                    String edition = pathSegments.get(5);
                    String version = pathSegments.get(7);

                    updateDatasetVersionInCollection(collectionID, datasetID, edition, version, request);
                    break;

                default:
                    response.setStatus(HttpStatus.SC_NOT_FOUND);
                    return;
            }
        } catch (UnexpectedResponseException e) {
            throw new UnexpectedErrorException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } catch (DatasetNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (com.github.onsdigital.zebedee.dataset.api.exception.BadRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Delete endpoints for /collections.
     * This supports only /collections/{collection_id}/datasets/{dataset_ID}
     */
    @DELETE
    public void delete(HttpServletRequest request, HttpServletResponse response) throws ZebedeeException, IOException {

        Session session = zebedeeCmsService.getSession(request);
        if (!zebedeeCmsService.getPermissions().canEdit(session)) {
            logInfo("Forbidden request made to the collection endpoint").log();
            response.setStatus(HttpStatus.SC_FORBIDDEN);
            return;
        }

        Path path = Path.newInstance(request);
        List<String> pathSegments = path.segments();

        if (!isValidPath(response, path, pathSegments)) return;

        String collectionID = pathSegments.get(1);
        String datasetID = pathSegments.get(3);

        switch (pathSegments.size()) {
            case 4: // /collections/{collection_id}/datasets/{dataset_id}
                removeDatasetFromCollection(collectionID, datasetID);
                break;
            case 8: // /collections/{collection_id}/datasets/{dataset_id}/editions/{}/versions/{}
                String edition = pathSegments.get(5);
                String version = pathSegments.get(7);
                removeDatasetVersionFromCollection(collectionID, datasetID, edition, version);
                break;
            default:
                response.setStatus(HttpStatus.SC_NOT_FOUND);
                return;
        }
    }

    private void updateDatasetVersionInCollection(String collectionID, String datasetID, String edition, String version, HttpServletRequest request) throws ZebedeeException, IOException, DatasetAPIException {
        logInfo("PUT called on /collections/{}/datasets/{}/editions/{}/versions/{} endpoint")
                .addParameter("collectionID", collectionID)
                .addParameter("datasetID", datasetID)
                .addParameter("edition", edition)
                .addParameter("version", version)
                .log();

        try (InputStream body = request.getInputStream()){

            CollectionDatasetVersion datasetVersion = ContentUtil.deserialise(body, CollectionDatasetVersion.class);
            datasetService.updateDatasetVersionInCollection(collectionID, datasetID, edition, version, datasetVersion);

        } catch (JsonSyntaxException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    private void updateDatasetInCollection(String collectionID, String datasetID, HttpServletRequest request) throws ZebedeeException, IOException, DatasetAPIException {
        logInfo("PUT called on /collections/{}/datasets/{} endpoint")
                .addParameter("collectionID", collectionID)
                .addParameter("datasetID", datasetID)
                .log();
        try (InputStream body = request.getInputStream()){

            CollectionDataset dataset = ContentUtil.deserialise(body, CollectionDataset.class);
            datasetService.updateDatasetInCollection(collectionID, datasetID, dataset);

        } catch (JsonSyntaxException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    private void removeDatasetVersionFromCollection(String collectionID, String datasetID, String edition, String version) throws ZebedeeException, IOException {
        logInfo("DELETE called on /collections/{collection_id}/datasets/{}/editions/{}/versions/{} endpoint")
                .addParameter("collectionID", collectionID)
                .addParameter("datasetID", datasetID)
                .addParameter("edition", edition)
                .addParameter("version", version)
                .log();

        datasetService.removeDatasetVersionFromCollection(collectionID, datasetID, edition, version);
    }

    private void removeDatasetFromCollection(String collectionID, String datasetID) throws ZebedeeException, IOException {

        logInfo("DELETE called on /collections/{collection_id}/datasets/{} endpoint")
                .addParameter("collectionID", collectionID)
                .addParameter("datasetID", datasetID)
                .log();

        datasetService.removeDatasetFromCollection(collectionID, datasetID);
    }

    private boolean isValidPath(HttpServletResponse response, Path path, List<String> segments) {

        // /collections/{collection_id}/datasets/{dataset_id}
        // /collections/{collection_id}/datasets/{dataset_id}/editions/{}/versions/{}
        if (segments.size() < 4 ||
                !segments.get(2).equalsIgnoreCase("datasets")) {

            logInfo("Endpoint for colletions not found").addParameter("path", path).log();
            response.setStatus(HttpStatus.SC_NOT_FOUND);
            return false;
        }

        return true;
    }

    /**
     * Get the collection defined by the given HttpServletRequest
     *
     * @param request the request containing the id of the collection to get.
     * @return
     * @throws IOException
     */
    public static Collection getCollection(HttpServletRequest request)
            throws IOException {
        String collectionId = getCollectionId(request);
        return Root.zebedee.getCollections().getCollection(collectionId);
    }

    public static String getCollectionId(HttpServletRequest request) {
        Path path = Path.newInstance(request);
        List<String> segments = path.segments();

        String collectionId = "";
        if (segments.size() > 1) {
            collectionId = segments.get(1);
        }
        return collectionId;
    }

}
