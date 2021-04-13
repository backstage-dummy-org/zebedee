package com.github.onsdigital.zebedee;

import com.github.onsdigital.zebedee.data.processing.DataIndex;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.DeleteContentRequestDeniedException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.Credentials;
import com.github.onsdigital.zebedee.keyring.Keyring;
import com.github.onsdigital.zebedee.keyring.KeyringException;
import com.github.onsdigital.zebedee.keyring.cache.SchedulerKeyCache;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.Collections;
import com.github.onsdigital.zebedee.model.Content;
import com.github.onsdigital.zebedee.model.KeyringCache;
import com.github.onsdigital.zebedee.model.ZebedeeCollectionReader;
import com.github.onsdigital.zebedee.model.encryption.ApplicationKeys;
import com.github.onsdigital.zebedee.model.encryption.EncryptionKeyFactory;
import com.github.onsdigital.zebedee.model.publishing.PublishedCollections;
import com.github.onsdigital.zebedee.permissions.service.PermissionsService;
import com.github.onsdigital.zebedee.service.DatasetService;
import com.github.onsdigital.zebedee.service.ImageService;
import com.github.onsdigital.zebedee.service.ServiceStore;
import com.github.onsdigital.zebedee.service.ServiceStoreImpl;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.session.service.Sessions;
import com.github.onsdigital.zebedee.teams.service.TeamsService;
import com.github.onsdigital.zebedee.user.model.User;
import com.github.onsdigital.zebedee.user.service.UsersService;
import com.github.onsdigital.zebedee.verification.VerificationAgent;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.github.onsdigital.zebedee.configuration.Configuration.isVerificationEnabled;
import static com.github.onsdigital.zebedee.exceptions.DeleteContentRequestDeniedException.beingEditedByAnotherCollectionError;
import static com.github.onsdigital.zebedee.exceptions.DeleteContentRequestDeniedException.beingEditedByThisCollectionError;
import static com.github.onsdigital.zebedee.exceptions.DeleteContentRequestDeniedException.markedDeleteInAnotherCollectionError;
import static com.github.onsdigital.zebedee.logging.CMSLogEvent.error;
import static com.github.onsdigital.zebedee.logging.CMSLogEvent.info;

public class Zebedee {

    public static final String PUBLISHED = "master";
    public static final String COLLECTIONS = "collections";
    public static final String PUBLISHED_COLLECTIONS = "publish-log";
    public static final String ZEBEDEE = "zebedee";
    public static final String USERS = "users";
    public static final String SESSIONS = "sessions";
    public static final String PERMISSIONS = "permissions";
    public static final String TEAMS = "teams";
    public static final String LAUNCHPAD = "launchpad";
    public static final String APPLICATION_KEYS = "application-keys";
    public static final String SERVICES = "services";
    public static final String KEYRING = "keyring";

    private final Path publishedCollectionsPath;
    private final Path collectionsPath;
    private final Path usersPath;
    private final Path sessionsPath;
    private final Path permissionsPath;
    private final Path teamsPath;
    private final Path applicationKeysPath;
    private final Path redirectPath;
    private final Path servicePath;
    private final Path keyRingPath;

    private final VerificationAgent verificationAgent;
    private final ApplicationKeys applicationKeys;
    private final PublishedCollections publishedCollections;
    private final Collections collections;
    private final Content published;
    private final KeyringCache legacyKeyringCache;
    private final Path publishedContentPath;
    private final Path path;
    private final PermissionsService permissionsService;
    private final Keyring collectionKeyring;
    private final SchedulerKeyCache schedulerKeyCache;
    private final EncryptionKeyFactory encryptionKeyFactory;

    private final UsersService usersService;
    private final TeamsService teamsService;
    private final Sessions sessions;
    private final DataIndex dataIndex;
    private final DatasetService datasetService;
    private final ImageService imageService;
    private final ServiceStoreImpl serviceStoreImpl;

    /**
     * Create a new instance of Zebedee setting.
     *
     * @param configuration {@link ZebedeeConfiguration} contains the set up to use for this instance.
     */
    public Zebedee(ZebedeeConfiguration configuration) {
        this.path = configuration.getZebedeePath();
        this.publishedContentPath = configuration.getPublishedContentPath();
        this.sessions = configuration.getSessions();
        this.legacyKeyringCache = configuration.getKeyringCache();
        this.permissionsService = configuration.getPermissionsService();
        this.published = configuration.getPublished();
        this.dataIndex = configuration.getDataIndex();
        this.collections = configuration.getCollections();
        this.publishedCollections = configuration.getPublishCollections();
        this.applicationKeys = configuration.getApplicationKeys();
        this.teamsService = configuration.getTeamsService();
        this.usersService = configuration.getUsersService();
        this.verificationAgent = configuration.getVerificationAgent(isVerificationEnabled(), this);
        this.datasetService = configuration.getDatasetService();
        this.imageService = configuration.getImageService();
        this.serviceStoreImpl = configuration.getServiceStore();
        this.collectionKeyring = configuration.getCollectionKeyring();
        this.schedulerKeyCache = configuration.getSchedulerKeyCache();
        this.encryptionKeyFactory = configuration.getEncryptionKeyFactory();

        this.collectionsPath = configuration.getCollectionsPath();
        this.publishedCollectionsPath = configuration.getPublishedCollectionsPath();
        this.usersPath = configuration.getUsersPath();
        this.sessionsPath = configuration.getSessionsPath();
        this.permissionsPath = configuration.getPermissionsPath();
        this.teamsPath = configuration.getTeamsPath();
        this.applicationKeysPath = configuration.getApplicationKeysPath();
        this.redirectPath = configuration.getRedirectPath();
        this.servicePath = configuration.getServicePath();
        this.keyRingPath = configuration.getKeyRingPath();
    }

    /**
     * This method works out how many {@link com.github.onsdigital.zebedee.model.Collection}s contain the given URI.
     * The intention is to allow double-checking in case of concurrent editing.
     * This should be 0 in order for someone to be allowed to edit a URI and
     * should be 1 after editing is initiated. If this returns more than 1 after
     * initiating editing then the current attempt to edit should be reverted -
     * presumably a race condition.
     *
     * @param uri The URI to check.
     * @return The number of {@link com.github.onsdigital.zebedee.model.Collection}s containing the given URI.
     * @throws IOException
     */
    public int isBeingEdited(String uri) throws IOException {
        int result = 0;

        // Is this URI present anywhere else?
        for (Collection collection : collections.list()) {
            if (collection.isInCollection(uri)) {
                result++;
            }
        }

        return result;
    }

    public Optional<Collection> checkForCollectionBlockingChange(Collection workingCollection, String uri) throws IOException {
        return collections.list()
                .stream()
                .filter(c -> c.isInCollection(uri) && !workingCollection.getDescription().getId()
                        .equals(c.getDescription().getId()))
                .findFirst();
    }

    public Optional<Collection> checkForCollectionBlockingChange(String uri) throws IOException {
        return collections.list()
                .stream()
                .filter(c -> c.isInCollection(uri))
                .findFirst();
    }

    public void checkAllCollectionsForDeleteMarker(String uri) throws IOException, DeleteContentRequestDeniedException {
        Path searchValue = Paths.get(uri);

        for (Collection collection : collections.list()) {
            if (collection.description.getPendingDeletes()
                    .stream()
                    .filter(existingDeleteRoot -> searchValue.startsWith(Paths.get(existingDeleteRoot.getRoot()
                            .contentPath)))
                    .findFirst().isPresent()) {
                throw markedDeleteInAnotherCollectionError(collection, uri);
            }
        }
    }

    public void isBeingEditedInAnotherCollection(Collection workingCollection, String uri, Session session) throws
            IOException,
            ZebedeeException {
        Optional<Collection> blockingCollection = collections.list()
                .stream()
                .filter(collection -> collection.isInCollection(uri))
                .findFirst();
        if (blockingCollection.isPresent()) {
            String title = new ZebedeeCollectionReader(this, blockingCollection.get(), session)
                    .getContent(uri).getDescription().getTitle();

            if (workingCollection.getDescription().getId().equals(blockingCollection.get().getDescription().getId())) {
                throw beingEditedByThisCollectionError(title);
            }
            throw beingEditedByAnotherCollectionError(blockingCollection.get(), title);
        }
    }

    public Path find(String uri) throws IOException {
        // There's currently only one place to look for content.
        // We may add one or more staging layers later.

        return published.get(uri);
    }


    public String toUri(Path path) {
        if (path == null) {
            return null;
        }

        // Remove zebedee section of path
        Path uriPath = this.path.relativize(path);

        // Strip off either launchpad, master or collections/mycollection/inprogress etc
        if (uriPath.startsWith(COLLECTIONS)) {
            uriPath = uriPath.subpath(3, uriPath.getNameCount());
        } else {
            uriPath = uriPath.subpath(1, uriPath.getNameCount());
        }

        // Return URI
        String s = uriPath.toString();
        if (s.startsWith("..")) {
            return null;
        } else if (s.endsWith("data.json")) {
            return "/" + s.substring(0, s.length() - "/data.json".length());
        } else {
            return "/" + s;
        }
    }

    public String toUri(String string) {
        return toUri(Paths.get(string));
    }

    /**
     * Deletes a collection folder structure. This method only deletes folders
     * and will throw an exception if any of the folders aren't empty. This
     * ensures that only a release that has been published can be deleted.
     *
     * @param path The {@link Path} to the collection folder.
     * @throws IOException If any of the subfolders is not empty or if a filesystem
     *                     error occurs.
     */
    public void delete(Path path) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path directory : stream) {
                // Recursively delete directories only
                if (Files.isDirectory(directory)) {
                    delete(directory);
                }
                if (directory.endsWith(".DS_Store")) { // delete any .ds_store hidden files
                    Files.delete(directory);
                }
                if (directory.endsWith(Content.REDIRECT)) { // also delete redirect table
                    Files.delete(directory);
                }
            }
        }
        Files.delete(path);
    }

    /**
     * Open a user session
     * <p>
     * This is a zebedee level operation since we need to unlock the keyring
     *
     * @param credentials
     * @return
     * @throws IOException
     * @throws NotFoundException
     * @throws BadRequestException
     */
    public Session openSession(Credentials credentials) throws IOException, NotFoundException, BadRequestException {
        if (credentials == null) {
            error().log("provided credentials are null no session will be opened");
            return null;
        }

        // Get the user
        User user = usersService.getUserByEmail(credentials.getEmail());

        if (user == null) {
            info().user(credentials.getEmail()).log("user not found no session will be created");
            return null;
        }

        Session session = createSession(user);
        unlockKeyring(user, credentials);
        cacheKeyring(user);

        return session;
    }

    private Session createSession(User user) throws IOException {
        try {
            return sessions.create(user);
        } catch (Exception ex) {
            error().user(user.getEmail())
                    .exception(ex)
                    .log("error attempting to create session for user");
            throw new IOException(ex);
        }
    }

    private void unlockKeyring(User user, Credentials credentials) throws IOException {
        // TODO This is step is required to maintain backwards compatability while migrating.
        // Once migration is complete the step can be removed completely.
        try {
            collectionKeyring.unlock(user, credentials.getPassword());
        } catch (KeyringException ex) {
            error().user(user.getEmail())
                    .exception(ex)
                    .log("failed to open user session error while caching collection keyring");
            throw new IOException(ex);
        }
    }

    private void cacheKeyring(User user) throws IOException {
        try {
            collectionKeyring.cacheKeyring(user);
        } catch (KeyringException ex) {
            error().user(user.getEmail())
                    .exception(ex)
                    .log("failed to open user session error while caching collection keyring");
            throw new IOException(ex);
        }
    }

    public TeamsService getTeamsService() {
        return this.teamsService;
    }

    public Path getPath() {
        return this.path;
    }

    public PermissionsService getPermissionsService() {
        return this.permissionsService;
    }

    public Path getPublishedContentPath() {
        return this.publishedContentPath;
    }

    public Content getPublished() {
        return this.published;
    }

    public Collections getCollections() {
        return this.collections;
    }

    public PublishedCollections getPublishedCollections() {
        return this.publishedCollections;
    }

    @Deprecated
    public KeyringCache getLegacyKeyringCache() {
        return this.legacyKeyringCache;
    }

    public ApplicationKeys getApplicationKeys() {
        return this.applicationKeys;
    }

    public Sessions getSessions() {
        return this.sessions;
    }

    public VerificationAgent getVerificationAgent() {
        return this.verificationAgent;
    }

    public DataIndex getDataIndex() {
        return this.dataIndex;
    }

    public UsersService getUsersService() {
        return usersService;
    }

    public DatasetService getDatasetService() {
        return datasetService;
    }

    public ImageService getImageService() {
        return imageService;
    }

    public ServiceStore getServiceStore() {
        return serviceStoreImpl;
    }

    public Path getServicePath() {
        return servicePath;
    }

    public Path getKeyRingPath() {
        return keyRingPath;
    }

    public Keyring getCollectionKeyring() {
        return this.collectionKeyring;
    }

    public SchedulerKeyCache getSchedulerKeyCache() {
        return this.schedulerKeyCache;
    }

    public EncryptionKeyFactory getEncryptionKeyFactory() {
        return this.encryptionKeyFactory;
    }
}
