package com.github.onsdigital.zebedee.model;

import com.github.davidcarboni.cryptolite.Keys;
import com.github.davidcarboni.cryptolite.Random;
import com.github.davidcarboni.restolino.json.Serialiser;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.ZebedeeTestBaseFixture;
import com.github.onsdigital.zebedee.configuration.CMSFeatureFlags;
import com.github.onsdigital.zebedee.content.page.base.PageDescription;
import com.github.onsdigital.zebedee.content.page.base.PageType;
import com.github.onsdigital.zebedee.content.page.release.Release;
import com.github.onsdigital.zebedee.content.util.ContentUtil;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.CollectionNotFoundException;
import com.github.onsdigital.zebedee.exceptions.ConflictException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.ApprovalStatus;
import com.github.onsdigital.zebedee.json.CollectionDataset;
import com.github.onsdigital.zebedee.json.CollectionDatasetVersion;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.CollectionInteractive;
import com.github.onsdigital.zebedee.json.CollectionType;
import com.github.onsdigital.zebedee.json.ContentDetail;
import com.github.onsdigital.zebedee.json.ContentStatus;
import com.github.onsdigital.zebedee.json.EventType;
import com.github.onsdigital.zebedee.model.content.item.ContentItemVersion;
import com.github.onsdigital.zebedee.model.content.item.VersionedContentItem;
import com.github.onsdigital.zebedee.model.publishing.scheduled.DummyScheduler;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.teams.model.Team;
import com.github.onsdigital.zebedee.teams.service.TeamsService;
import com.github.onsdigital.zebedee.util.ContentDetailUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CollectionTest extends ZebedeeTestBaseFixture {

    @Rule
    public TemporaryFolder rootDir = new TemporaryFolder();

    @Mock
    TeamsService teamsService;

    @Mock
    Team team;

    private static final String teamName = "some team";
    private static final String teamId = "12";
    private static final boolean recursive = false;
    Collection collection;
    Session publisher1Session;
    Session publisher2Session;
    String publisher1Email;
    FakeCollectionWriter collectionWriter;

    public void setUp() throws Exception {
        rootDir.create();

        collection = new Collection(builder.collections.get(1), zebedee);

        publisher1Session = new Session("5678", builder.publisher1.getEmail());
        publisher2Session = new Session("5678", builder.publisher2.getEmail());

        setUpPermissionsServiceMockForLegacyTests(zebedee, publisher1Session);
        ReflectionTestUtils.setField(zebedee, "teamsService", teamsService);

        when(permissionsService.canEdit(publisher2Session))
                .thenReturn(true);
        when(permissionsService.canEdit(publisher1Session))
                .thenReturn(true);

        publisher1Email = builder.publisher1.getEmail();
        collectionWriter = new FakeCollectionWriter(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
    }

    @Test
    public void shouldCreateCollection() throws Exception {

        // Given
        // The content doesn't exist at any level:
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setType(CollectionType.scheduled);
        collectionDescription.setPublishDate(new Date());
        String filename = PathUtils.toFilename(name);

        // When

        Collection.create(collectionDescription, zebedee, publisher1Session);


        // Then
        Path rootPath = builder.zebedeeRootPath.resolve(Zebedee.COLLECTIONS);
        Path releasePath = rootPath.resolve(filename);
        Path jsonPath = rootPath.resolve(filename + ".json");

        assertTrue(StringUtils.isNotEmpty(collectionDescription.getId()));

        assertTrue(Files.exists(releasePath));
        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(releasePath.resolve(Collection.REVIEWED)));
        assertTrue(Files.exists(releasePath.resolve(Collection.COMPLETE)));
        assertTrue(Files.exists(releasePath.resolve(Collection.IN_PROGRESS)));

        CollectionDescription createdCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(jsonPath)) {
            createdCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(createdCollectionDescription);
        assertEquals(collectionDescription.getName(), createdCollectionDescription.getName());
        assertEquals(collectionDescription.getPublishDate(), createdCollectionDescription.getPublishDate());
        assertEquals(ApprovalStatus.NOT_STARTED, createdCollectionDescription.getApprovalStatus());

        verifyKeyAddedToCollectionKeyring();
    }

    @Test
    public void shouldRenameCollection() throws Exception {

        // Given an existing collection
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setType(CollectionType.manual);
        collectionDescription.setPublishDate(new Date());
        String newName = "Economy Release";
        String filename = PathUtils.toFilename(newName);

        // When the rename function is called.

        Collection.create(collectionDescription, zebedee, publisher1Session);
        verifyKeyAddedToCollectionKeyring();

        Collection.rename(collectionDescription, newName, zebedee);

        // Then the collection is renamed.
        Path rootPath = builder.zebedeeRootPath.resolve(Zebedee.COLLECTIONS);
        Path releasePath = rootPath.resolve(filename);
        Path jsonPath = rootPath.resolve(filename + ".json");

        Path oldJsonPath = rootPath.resolve(PathUtils.toFilename(name) + ".json");

        assertTrue(Files.exists(releasePath));
        assertTrue(Files.exists(jsonPath));
        assertTrue(!Files.exists(oldJsonPath));
        assertTrue(Files.exists(releasePath.resolve(Collection.REVIEWED)));
        assertTrue(Files.exists(releasePath.resolve(Collection.COMPLETE)));
        assertTrue(Files.exists(releasePath.resolve(Collection.IN_PROGRESS)));

        CollectionDescription renamedCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(jsonPath)) {
            renamedCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(renamedCollectionDescription);
        assertEquals(collectionDescription.getId(), renamedCollectionDescription.getId());
        assertEquals(newName, renamedCollectionDescription.getName());
        assertEquals(collectionDescription.getPublishDate(), renamedCollectionDescription.getPublishDate());
        assertEquals(collectionDescription.getType(), renamedCollectionDescription.getType());
    }

    @Test
    public void shouldRenameCollectionSpecialChars() throws Exception {

        // Given an existing collection
        String name = "Collection A $$";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setType(CollectionType.manual);
        collectionDescription.setPublishDate(new Date());
        String newName = "Collection A";
        String filename = PathUtils.toFilename(newName);

        // When the rename function is called.

        Collection.create(collectionDescription, zebedee, publisher1Session);

        Collection.rename(collectionDescription, newName, zebedee);

        // Then the collection is renamed.
        Path rootPath = builder.zebedeeRootPath.resolve(Zebedee.COLLECTIONS);
        Path releasePath = rootPath.resolve(filename);
        Path jsonPath = rootPath.resolve(filename + ".json");

        Path oldJsonPath = rootPath.resolve(PathUtils.toFilename(name) + ".json");

        assertTrue(Files.exists(releasePath));
        assertTrue(Files.exists(jsonPath));
        assertTrue(!Files.exists(oldJsonPath));
        assertTrue(Files.exists(releasePath.resolve(Collection.REVIEWED)));
        assertTrue(Files.exists(releasePath.resolve(Collection.COMPLETE)));
        assertTrue(Files.exists(releasePath.resolve(Collection.IN_PROGRESS)));

        CollectionDescription renamedCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(jsonPath)) {
            renamedCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(renamedCollectionDescription);
        assertEquals(collectionDescription.getId(), renamedCollectionDescription.getId());
        assertEquals(newName, renamedCollectionDescription.getName());
        assertEquals(collectionDescription.getPublishDate(), renamedCollectionDescription.getPublishDate());
        assertEquals(collectionDescription.getType(), renamedCollectionDescription.getType());
    }

    @Test
    public void shouldRenameCollectionSameaName() throws Exception {

        // Given an existing collection
        String name = "Collection A";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setType(CollectionType.manual);
        collectionDescription.setPublishDate(new Date());
        String newName = "Collection A";
        String filename = PathUtils.toFilename(newName);

        // When the rename function is called.

        Collection.create(collectionDescription, zebedee, publisher1Session);

        Collection.rename(collectionDescription, newName, zebedee);

        // Then the collection is renamed.
        Path rootPath = builder.zebedeeRootPath.resolve(Zebedee.COLLECTIONS);
        Path releasePath = rootPath.resolve(filename);
        Path jsonPath = rootPath.resolve(filename + ".json");

        Path oldJsonPath = rootPath.resolve(PathUtils.toFilename(name) + ".json");

        assertTrue(Files.exists(releasePath));
        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(oldJsonPath));
        assertTrue(Files.exists(releasePath.resolve(Collection.REVIEWED)));
        assertTrue(Files.exists(releasePath.resolve(Collection.COMPLETE)));
        assertTrue(Files.exists(releasePath.resolve(Collection.IN_PROGRESS)));

        CollectionDescription renamedCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(jsonPath)) {
            renamedCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(renamedCollectionDescription);
        assertEquals(collectionDescription.getId(), renamedCollectionDescription.getId());
        assertEquals(newName, renamedCollectionDescription.getName());
        assertEquals(collectionDescription.getPublishDate(), renamedCollectionDescription.getPublishDate());
        assertEquals(collectionDescription.getType(), renamedCollectionDescription.getType());
    }

    @Test
    public void shouldUpdateCollection() throws Exception {
        Set<String> teamIds = new HashSet<>(Arrays.asList("12"));

        when(teamsService.findTeam(teamName))
                .thenReturn(team);
        when(team.getId())
                .thenReturn(teamId);
        doNothing().when(permissionsService).setViewerTeams(
                publisher1Session, collection.getDescription().getId(), teamIds);

        // Given an existing collection
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setType(CollectionType.manual);
        collectionDescription.setPublishDate(new Date());
        collectionDescription.setTeams(new ArrayList<>());
        Collection collection = Collection.create(collectionDescription, zebedee, publisher1Session);

        // When the collection is updated
        String newName = "Economy Release";
        String filename = PathUtils.toFilename(newName);
        CollectionDescription updatedDescription = new CollectionDescription(newName);
        updatedDescription.setType(CollectionType.scheduled);
        updatedDescription.setPublishDate(new DateTime(collectionDescription.getPublishDate()).plusHours(1).toDate());
        updatedDescription.setId(collectionDescription.getId());
        updatedDescription.setTeams(Arrays.asList(teamName));

        setUpKeyringMocks();

        Collection.update(collection, updatedDescription, zebedee, new DummyScheduler(), publisher1Session);

        // Then the properties of the description passed to update have been updated.
        Path rootPath = builder.zebedeeRootPath.resolve(Zebedee.COLLECTIONS);
        Path collectionFolderPath = rootPath.resolve(filename);
        Path collectionJsonPath = rootPath.resolve(filename + ".json");

        Path oldJsonPath = rootPath.resolve(PathUtils.toFilename(name) + ".json");

        assertTrue(Files.exists(collectionFolderPath));
        assertTrue(Files.exists(collectionJsonPath));
        assertTrue(!Files.exists(oldJsonPath));

        CollectionDescription updatedCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(collectionJsonPath)) {
            updatedCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(updatedCollectionDescription);
        assertEquals(collectionDescription.getId(), updatedCollectionDescription.getId());
        assertEquals(newName, updatedCollectionDescription.getName());
        assertEquals(updatedDescription.getType(), updatedCollectionDescription.getType());
        assertEquals(updatedDescription.getPublishDate(), updatedCollectionDescription.getPublishDate());
        assertTrue(updatedCollectionDescription.getEvents().hasEventForType(EventType.CREATED));
        assertEquals(updatedDescription.getTeams(), updatedCollectionDescription.getTeams());
        verify(permissionsService, times(1)).setViewerTeams(
                publisher1Session, collection.getDescription().getId(), teamIds);
    }

    @Test
    public void shouldRemoveViewerTeams() throws Exception {
        Set<Integer> teamIds = new HashSet<>(Arrays.asList(12));

        when(teamsService.findTeam(teamName))
                .thenReturn(team);
        when(team.getId())
                .thenReturn(teamId);

        // Given an existing collection
        String name = "Population Release 2";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setType(CollectionType.manual);
        collectionDescription.setPublishDate(new Date());
        collectionDescription.setTeams(Arrays.asList(teamName));
        Collection collection = Collection.create(collectionDescription, zebedee, publisher1Session);

        // When the collection is updated
        String filename = PathUtils.toFilename(name);
        CollectionDescription updatedDescription = new CollectionDescription(name);
        updatedDescription.setId(collectionDescription.getId());
        updatedDescription.setTeams(new ArrayList<>());

        Collection.update(collection, updatedDescription, zebedee, new DummyScheduler(), publisher1Session);

        // Then the properties of the description passed to update have been updated.
        Path rootPath = builder.zebedeeRootPath.resolve(Zebedee.COLLECTIONS);
        Path collectionFolderPath = rootPath.resolve(filename);
        Path collectionJsonPath = rootPath.resolve(filename + ".json");

        assertTrue(Files.exists(collectionFolderPath));
        assertTrue(Files.exists(collectionJsonPath));

        CollectionDescription updatedCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(collectionJsonPath)) {
            updatedCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(updatedCollectionDescription);
        assertEquals(collectionDescription.getId(), updatedCollectionDescription.getId());
        assertTrue(updatedCollectionDescription.getEvents().hasEventForType(EventType.CREATED));
        assertEquals(updatedDescription.getTeams(), updatedCollectionDescription.getTeams());
        verify(permissionsService, times(1)).setViewerTeams(
                publisher1Session, collection.getDescription().getId(), new HashSet<String>());
    }

    @Test
    public void shouldUpdateCollectionNameIfCaseIsChanged() throws Exception {

        // Given an existing collection
        String name = "population release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setType(CollectionType.manual);
        collectionDescription.setPublishDate(new Date());
        Collection collection = Collection.create(collectionDescription, zebedee, publisher1Session);

        // When the collection is updated
        String newName = "Population Release";
        String filename = PathUtils.toFilename(newName);
        CollectionDescription updatedDescription = new CollectionDescription(newName);
        updatedDescription.setType(CollectionType.manual);
        updatedDescription.setPublishDate(new Date());
        Collection.update(collection, updatedDescription, zebedee, new DummyScheduler(), publisher1Session);

        // Then the properties of the description passed to update have been updated.
        Path rootPath = builder.zebedeeRootPath.resolve(Zebedee.COLLECTIONS);
        Path collectionFolderPath = rootPath.resolve(filename);
        Path collectionJsonPath = rootPath.resolve(filename + ".json");

        assertTrue(Files.exists(collectionFolderPath));
        assertTrue(Files.exists(collectionJsonPath));

        CollectionDescription updatedCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(collectionJsonPath)) {
            updatedCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(updatedCollectionDescription);
        assertEquals(collectionDescription.getId(), updatedCollectionDescription.getId());
        assertEquals(newName, updatedCollectionDescription.getName());
        assertEquals(updatedDescription.getType(), updatedCollectionDescription.getType());
        assertTrue(updatedCollectionDescription.getEvents().hasEventForType(EventType.CREATED));
    }

    @Test
    public void shouldUpdateScheduleTimeForAScheduledCollection() throws Exception {

        // Given an existing collection that has been scheduled
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setPublishDate(DateTime.now().plusSeconds(2).toDate());
        collectionDescription.setType(CollectionType.scheduled);
        collectionDescription.setTeams(new ArrayList<>());
        Collection collection = Collection.create(collectionDescription, zebedee, publisher1Session);

        DummyScheduler scheduler = new DummyScheduler();
        scheduler.schedulePublish(collection, zebedee);

        // When the collection is updated with a new release time
        String newName = "Economy Release";
        CollectionDescription updatedDescription = new CollectionDescription(newName);
        updatedDescription.setType(CollectionType.scheduled);
        updatedDescription.setPublishDate(DateTime.now().plusSeconds(10).toDate());
        Collection updated = Collection.update(collection, updatedDescription, zebedee, scheduler, publisher1Session);

        assertTrue(scheduler.taskExistsForCollection(updated));
        long timeUntilTaskRun = scheduler.getTaskForCollection(updated).getDelay(TimeUnit.SECONDS);
        assertTrue(timeUntilTaskRun > 8);
    }

    @Test(expected = BadRequestException.class)
    public void updateCollectionShouldThrowBadRequestExceptionForNullCollection() throws Exception {

        // Given a null collection
        Collection collection = null;

        // When we call the static update method
        Collection.update(collection, new CollectionDescription("name"), zebedee, new DummyScheduler(), publisher1Session);

        // Then the expected exception is thrown.
    }

    @Test(expected = CollectionNotFoundException.class)
    public void shouldNotInstantiateInInvalidFolder() throws Exception {

        // Given
        // A folder that isn't a valid release:
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.setType(CollectionType.scheduled);
        collectionDescription.setPublishDate(new Date());

        Collection.create(collectionDescription, zebedee, publisher1Session);

        Path releasePath = builder.zebedeeRootPath.resolve(Zebedee.COLLECTIONS).resolve(
                PathUtils.toFilename(name));
        FileUtils.cleanDirectory(releasePath.toFile());

        // When
        new Collection(releasePath, zebedee);

        // Then
        // We should get an exception.
    }

    @Test
    public void shouldCreate() throws IOException {

        // Given
        // The content doesn't exist at any level:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";

        // When
        boolean created = collection.create(publisher1Session, uri);

        // Then
        assertTrue(created);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertTrue(Files.exists(inProgress.resolve(uri.substring(1))));

        // check an event has been created for the content being created.
        assertTrue(collection.getDescription().getEventsByUri().get(uri).hasEventForType(EventType.CREATED));
    }

    @Test
    public void shouldNotCreateIfPublished() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createPublishedFile(uri);

        // When
        boolean created = collection.create(publisher1Session, uri);

        // Then
        assertFalse(created);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
    }

    @Test
    public void shouldNotCreateIfReviewed() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createReviewedFile(uri);

        // When
        boolean created = collection.create(publisher1Session, uri);

        // Then
        assertFalse(created);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
    }

    @Test
    public void shouldNotCreateIfComplete() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createReviewedFile(uri);

        // When
        boolean created = collection.create(publisher1Session, uri);

        // Then
        assertFalse(created);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
    }

    @Test
    public void shouldNotCreateIfInProgress() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createInProgressFile(uri);

        // When
        boolean created = collection.create(publisher1Session, uri);

        // Then
        assertFalse(created);
    }

    @Test
    public void shouldDeleteAllFilesFromInProgressDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createInProgressFile("/" + jsonFile);
        builder.createInProgressFile("/" + csvFile);

        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);

        // When the delete method is called on the json file
        boolean result = collection.deleteContentDirectory(publisher1Session.getEmail(), jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(inProgress.resolve(jsonFile)));
        assertFalse(Files.exists(inProgress.resolve(csvFile)));
        // check an event has been created for the content being deleted.
        collection.getDescription().getEventsByUri().get("/" + jsonFile).hasEventForType(EventType.DELETED);
    }

    @Test
    public void shouldDeleteAllFilesFromCompleteDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createCompleteFile("/" + jsonFile);
        builder.createCompleteFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.COMPLETE);

        // When the delete method is called on the json file
        boolean result = collection.deleteContentDirectory(publisher1Session.getEmail(), jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertFalse(Files.exists(root.resolve(csvFile)));
        collection.getDescription().getEventsByUri().get("/" + jsonFile).hasEventForType(EventType.DELETED);
    }

    @Test
    public void shouldDeleteAllFilesFromReviewedDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createReviewedFile("/" + jsonFile);
        builder.createReviewedFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.REVIEWED);

        // When the delete method is called on the json file
        boolean result = collection.deleteContentDirectory(publisher1Email, jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertFalse(Files.exists(root.resolve(csvFile)));
        collection.getDescription().getEventsByUri().get("/" + jsonFile).hasEventForType(EventType.DELETED);
    }

    @Test
    public void shouldDeleteOnlyGivenFileFromReviewedDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createReviewedFile("/" + jsonFile);
        builder.createReviewedFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.REVIEWED);

        // When the delete method is called on the json file
        boolean result = collection.deleteFile(jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertTrue(Files.exists(root.resolve(csvFile)));
    }

    @Test
    public void shouldDeleteOnlyGivenFileFromCompleteDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createCompleteFile("/" + jsonFile);
        builder.createCompleteFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.COMPLETE);

        // When the delete method is called on the json file
        boolean result = collection.deleteFile(jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertTrue(Files.exists(root.resolve(csvFile)));
    }

    @Test
    public void shouldDeleteOnlyGivenFileFromInProgressDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createInProgressFile("/" + jsonFile);
        builder.createInProgressFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.IN_PROGRESS);

        // When the delete method is called on the json file
        boolean result = collection.deleteFile(jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertTrue(Files.exists(root.resolve(csvFile)));
    }

    @Test
    public void shouldEditPublished() throws IOException, BadRequestException {

        // Given
        // The content exists publicly:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);

        // When
        boolean edited = collection.edit(publisher1Session, uri, collectionWriter, recursive);

        // Then
        assertTrue(edited);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertTrue(Files.exists(inProgress.resolve(uri.substring(1))));

        Path published = builder.zebedeeRootPath.resolve(Zebedee.PUBLISHED);
        Path content = published.resolve(uri.substring(1));
        assertTrue(Files.exists(content));

        // check an event has been created for the content being created.
        collection.getDescription().getEventsByUri().get(uri).hasEventForType(EventType.EDITED);
    }

    @Test
    public void shouldEditComplete() throws IOException, BadRequestException {

        // Given
        // The content exists, has been edited and completed:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        builder.createCompleteFile(uri);

        // When
        boolean edited = collection.edit(publisher1Session, uri, collectionWriter, recursive);

        // Then
        // It should be edited
        assertTrue(edited);

        // It should be in in progress
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertTrue(Files.exists(inProgress.resolve(uri.substring(1))));

        // check the file no longer exists in complete, the previous version is no longer wanted.
        Path complete = builder.collections.get(1).resolve(Collection.COMPLETE);
        assertFalse(Files.exists(complete.resolve(uri.substring(1))));
    }

    @Test
    public void shouldEditReviewed() throws IOException, BadRequestException {

        // Given
        // The content exists, has been edited and reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        builder.createReviewedFile(uri);

        // When
        boolean edited = collection.edit(publisher1Session, uri, collectionWriter, recursive);

        // Then
        // It should be edited
        assertTrue(edited);

        // It should be in in progress
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertTrue(Files.exists(inProgress.resolve(uri.substring(1))));

        // check the file no longer exists in reviewed, the previous version is no longer wanted.
        Path reviewed = builder.collections.get(1).resolve(Collection.REVIEWED);
        assertFalse(Files.exists(reviewed.resolve(uri.substring(1))));
    }

    @Test
    public void shouldEditIfEditingAlready() throws IOException, BadRequestException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createInProgressFile(uri);

        // When
        boolean edited = collection.edit(publisher1Session, uri, collectionWriter, recursive);

        // Then
        assertTrue(edited);
    }

    @Test
    public void shouldNotEditIfEditingElsewhere() throws IOException, BadRequestException {

        // Given
        // The content already exists in another release:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.isBeingEditedElsewhere(uri, 0);

        // When
        boolean edited = collection.edit(publisher1Session, uri, collectionWriter, recursive);

        // Then
        assertFalse(edited);
    }

    @Test
    public void shouldNotEditIfDoesNotExist() throws IOException, BadRequestException {

        // Given
        // The content does not exist:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";

        // When
        boolean edited = collection.edit(publisher1Session, uri, collectionWriter, recursive);

        // Then
        assertFalse(edited);
    }

    @Test
    public void shouldReviewWithReviewer() throws IOException, ZebedeeException {

        // Given
        // The content exists, has been edited and complete:
        String uri = CreateCompleteContent();

        // When
        // One of the digital publishing team reviews it
        boolean reviewed = collection.review(publisher2Session, uri, recursive);

        // Then
        // The content should be reviewed and no longer located in "in progress"
        assertTrue(reviewed);
        Path edited = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(edited.resolve(uri.substring(1))));

        // check an event has been created for the content being created.
        collection.getDescription().getEventsByUri().get(uri).hasEventForType(EventType.REVIEWED);
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldNotReviewAsPublisher() throws IOException, ZebedeeException {

        // Given
        // The content exists, has been edited and complete by publisher1:
        String uri = CreateCompleteContent();

        // When
        // the original content creator attempts to review the content
        collection.review(publisher1Session, uri, recursive);

        // Then
        // expect an Unauthorized error
    }

    private String CreatePublishedContent() throws IOException {
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        return uri;
    }

    private String CreateEditedContent() throws IOException, BadRequestException {
        String uri = CreatePublishedContent();
        collection.edit(publisher1Session, uri, collectionWriter, recursive);
        return uri;
    }

    private String CreateCompleteContent() throws IOException, BadRequestException {
        String uri = CreateEditedContent();
        collection.complete(publisher1Session, uri, recursive);
        return uri;
    }

    @Test(expected = BadRequestException.class)
    public void shouldNotReviewIfContentHasNotBeenCompleted() throws IOException, ZebedeeException {

        // Given some content that has been edited by a publisher:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        collection.edit(publisher1Session, uri, collectionWriter, recursive);

        // When - A reviewer edits reviews content
        boolean reviewed = collection.review(publisher2Session, uri, recursive);

        // Then
        // Expect an error
    }

    @Test
    public void shouldComplete() throws IOException {

        // Given
        // The content exists, has been edited and complete:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        builder.createInProgressFile(uri);

        // When
        boolean complete = collection.complete(publisher1Session, uri, recursive);

        // Then
        assertTrue(complete);
        Path edited = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(edited.resolve(uri.substring(1))));

        // check an event has been created for the content being created.
        collection.getDescription().getEventsByUri().get(uri).hasEventForType(EventType.COMPLETED);
    }

    @Test
    public void completeShouldMoveFilesWithNoExtension() throws IOException {

        // Given
        // The content exists, has been edited and complete:
        String uri = "/economy/inflationandpriceindices/timeseries/fileWithNoExtension";
        builder.createInProgressFile(uri);

        // When
        boolean complete = collection.complete(publisher1Session, uri, recursive);

        // Then
        assertTrue(complete);
        Path inProgressPath = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        Path completedPath = builder.collections.get(1).resolve(Collection.COMPLETE);
        assertFalse(Files.exists(inProgressPath.resolve(uri.substring(1))));
        assertTrue(Files.exists(completedPath.resolve(uri.substring(1))));

        // check an event has been created for the content being created.
        collection.getDescription().getEventsByUri().get(uri).hasEventForType(EventType.COMPLETED);
    }

    @Test
    public void shouldNotCompleteIfReviewed() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createReviewedFile(uri);

        // When
        boolean isComplete = collection.complete(publisher1Session, uri, recursive);

        // Then
        assertFalse(isComplete);
    }

    @Test
    public void shouldNotCompleteIfAlreadyComplete() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createCompleteFile(uri);

        // When
        boolean isComplete = collection.complete(publisher1Session, uri, recursive);

        // Then
        assertFalse(isComplete);
    }

    @Test
    public void shouldNotCompleteIfNotEditing() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createCompleteFile(uri);

        // When
        boolean isComplete = collection.complete(publisher1Session, uri, recursive);

        // Then
        assertFalse(isComplete);
    }

    @Test(expected = BadRequestException.class)
    public void shouldNotReviewIfAlreadyReviewed() throws IOException, ZebedeeException {

        // Given
        // The content already exists:
        String uri = CreateCompleteContent();
        builder.createReviewedFile(uri);

        // When
        // An alternative publisher reviews the content
        collection.review(publisher2Session, uri, recursive);

        // Then
        // Expect error
    }

    @Test(expected = NotFoundException.class)
    public void shouldNotReviewIfNotPreviouslyCompleted() throws IOException, ZebedeeException {

        // Given
        // Some content:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        collection.edit(publisher1Session, uri, collectionWriter, recursive);

        // When content is trying to be reviewed before being completed
        boolean reviewed = collection.review(builder.createSession(publisher1Email), uri, recursive);

        // Then the expected exception is thrown.
    }

    @Test
    public void shouldBeInProgress() throws IOException {

        // Given
        // The content is currently being edited:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createInProgressFile(uri);

        // When
        boolean inProgress = collection.isInProgress(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertTrue(inProgress);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldBeComplete() throws IOException {

        // Given
        // The content has been completed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createCompleteFile(uri);

        // When
        boolean complete = collection.isComplete(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertTrue(complete);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldBeReviewed() throws IOException {

        // Given
        // The content has been reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createReviewedFile(uri);

        // When
        boolean reviewed = collection.isReviewed(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertTrue(reviewed);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldNotBeCompleteIfInProgress() throws IOException {

        // Given
        // The content has been reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createCompleteFile(uri);
        builder.createInProgressFile(uri);

        // When
        boolean isComplete = collection.isComplete(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertFalse(isComplete);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldNotBeReviewedIfComplete() throws IOException {

        // Given
        // The content has been complete:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createReviewedFile(uri);
        builder.createCompleteFile(uri);

        // When
        boolean reviewed = collection.isReviewed(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertFalse(reviewed);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldNotBeReviewedIfInProgress() throws IOException {

        // Given
        // The content has been reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createReviewedFile(uri);
        builder.createInProgressFile(uri);

        // When
        boolean reviewed = collection.isReviewed(uri);
        boolean inRelease = collection.isInCollection(uri);

        // Then
        assertFalse(reviewed);
        assertTrue(inRelease);
    }

    @Test
    public void shouldGetPath() throws IOException {

        // Given
        // We're editing some content:
        String uri = "/economy/inflationandpriceindices/timeseries/beer.html";
        builder.createPublishedFile(uri);
        builder.createReviewedFile(uri);
        builder.createInProgressFile(uri);

        // When
        // We write some output to the content:
        Path path = collection.getInProgressPath(uri);
        try (Writer writer = Files.newBufferedWriter(path,
                StandardCharsets.UTF_8)) {
            writer.append("test");
        }

        // Then
        // The output should have gone to the expected copy of the file:
        Path inProgressPath = builder.collections.get(1).resolve(
                Collection.IN_PROGRESS);
        Path expectedPath = inProgressPath.resolve(uri.substring(1));
        assertTrue(Files.size(expectedPath) > 0);
    }

    @Test
    public void shouldReturnInProgressUris() throws IOException {
        // Given
        // There are these files in progress:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        String uri2 = "/economy/someotherthing/timeseries/e4c4.html";
        builder.createInProgressFile(uri);
        builder.createInProgressFile(uri2);

        // When
        // We attempt to get the in progress files.
        List<String> uris = collection.inProgressUris();

        // Then
        // We get out the expected in progress files.
        assertTrue(uris.contains(uri));
        assertTrue(uris.contains(uri2));

        // and the uri lists for other states are empty.
        assertTrue(collection.completeUris().isEmpty());
        assertTrue(collection.reviewedUris().isEmpty());
    }

    @Test
    public void shouldReturnCompleteUris() throws IOException {
        // Given
        // There are these files complete:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        String uri2 = "/economy/someotherthing/timeseries/e4c4.html";
        builder.createCompleteFile(uri);
        builder.createCompleteFile(uri2);

        // When
        // We attempt to get the complete files.
        List<String> uris = collection.completeUris();

        // Then
        // We get out the expected complete files.
        assertTrue(uris.contains(uri));
        assertTrue(uris.contains(uri2));

        // and the uri lists for other states are empty.
        assertTrue(collection.inProgressUris().isEmpty());
        assertTrue(collection.reviewedUris().isEmpty());
    }

    @Test
    public void shouldReturnReviewedUris() throws IOException {
        // Given
        // There are these files reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        String uri2 = "/economy/someotherthing/timeseries/e4c4.html";
        builder.createReviewedFile(uri);
        builder.createReviewedFile(uri2);

        // When
        // We attempt to get the reviewed files.
        List<String> uris = collection.reviewedUris();

        // Then
        // We get out the expected reviewed files.
        assertTrue(uris.contains(uri));
        assertTrue(uris.contains(uri2));

        // and the uri lists for other states are empty.
        assertTrue(collection.inProgressUris().isEmpty());
        assertTrue(collection.completeUris().isEmpty());
    }

    @Test
    public void shouldFindInProgressUri() throws IOException {
        // Given
        // There is a file in progress
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createInProgressFile(uri);

        // When
        // We attempt to find the file.
        Path path = collection.find(uri);

        // Then
        // We get the path to the in progress file.
        assertTrue(path.toString().contains("/" + Collection.IN_PROGRESS + "/"));
    }

    @Test
    public void shouldFindCompleteUri() throws IOException {
        // Given
        // There is a file in progress
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createCompleteFile(uri);

        // When
        // We attempt to find the file.
        Path path = collection.find(uri);

        // Then
        // We get the path to the in progress file.
        assertTrue(path.toString().contains("/" + Collection.COMPLETE + "/"));
    }

    @Test
    public void shouldFindReviewedUri() throws IOException {
        // Given
        // There is a file in progress
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createReviewedFile(uri);

        // When
        // We attempt to find the file.
        Path path = collection.find(uri);

        // Then
        // We get the path to the in progress file.
        assertTrue(path.toString().contains("/" + Collection.REVIEWED + "/"));
    }

    @Test
    public void associateWithReleaseShouldUseExistingReleaseIfItsAlreadyInCollection() throws NotFoundException, IOException, BadRequestException {

        // Given
        // There is a release already in progress
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());
        collection.edit(publisher1Session, uri + "/data.json", collectionWriter, recursive);

        // When we attempt to associate the collection with a release
        Release result = collection.associateWithRelease(publisher1Session, release, collectionWriter);

        assertTrue(result.getDescription().getPublished());
        assertEquals(URI.create(uri), result.getUri());
    }

    @Test
    public void associateWithReleaseShouldSetReleaseToPublished() throws NotFoundException, IOException, BadRequestException {

        // Given a release that is announced
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());

        // When we attempt to associate the collection with a release
        Release result = collection.associateWithRelease(publisher1Session, release, collectionWriter);

        // Then the release is now in progress for the collection and the published flag is set to true
        assertTrue(collection.isInProgress(uri));
        assertTrue(result.getDescription().getPublished());
        assertEquals(URI.create(uri), result.getUri());
    }

    @Test
    public void populateReleaseQuietlyShouldReturnNullWhenCollectionNotAssociatedToRelease() throws ZebedeeException, IOException {
        // Given a collection that is NOT associated with a release
        String releaseUri = "";
        collection.getDescription().setReleaseUri(releaseUri);

        // When we attempt to populate the release from the collection.

        FakeCollectionReader collectionReader = new FakeCollectionReader(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
        FakeCollectionWriter collectionWriter = new FakeCollectionWriter(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
        Iterable<ContentDetail> collectionContent = Collections.emptyList();

        Release result = collection.populateReleaseQuietly(
                collectionReader,
                collectionWriter,
                collectionContent);

        // Then the returned release object is null
        assertNull(result);
    }

    @Test
    public void populateReleaseQuietlyShouldReturnNullWhenReleaseJsonInvalid() throws ZebedeeException, IOException {
        // Given a collection that is associated with a release and has an article
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());

        CollectionDescription description = new CollectionDescription();
        description.setId(Random.id());
        description.setName(description.getId());

        collection.getDescription().setReleaseUri(uri);
        collection.associateWithRelease(publisher1Session, release, collectionWriter);

        String releaseJsonUri = uri + "/data.json";

        collection.complete(publisher1Session, releaseJsonUri, recursive);
        collection.review(publisher2Session, releaseJsonUri, recursive);

        FileUtils.write(collection.getReviewed().getPath().resolve(releaseJsonUri.substring(1)).toFile(),
                Serialiser.serialise(new Object()), Charset.defaultCharset());


        // When we attempt to populate the release from the collection.
        FakeCollectionReader collectionReader = new FakeCollectionReader(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
        FakeCollectionWriter collectionWriter = new FakeCollectionWriter(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
        Iterable<ContentDetail> collectionContent = ContentDetailUtil.resolveDetails(collection.getReviewed(),
                collectionReader.getReviewed());

        Release result = collection.populateReleaseQuietly(
                collectionReader,
                collectionWriter,
                collectionContent);

        // Then the returned release object is null
        assertNull(result);
    }

    @Test
    public void populateReleaseQuietlyShouldAddLinksToReleasePageForCollectionContent() throws ZebedeeException, IOException {
        // Given a collection that is associated with a release and has an article
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());

        CollectionDescription description = new CollectionDescription();
        description.setId(Random.id());
        description.setName(description.getId());

        collection.getDescription().setReleaseUri(uri);
        collection.associateWithRelease(publisher1Session, release, collectionWriter);

        String releaseJsonUri = uri + "/data.json";

        collection.complete(publisher1Session, releaseJsonUri, recursive);
        collection.review(publisher2Session, releaseJsonUri, recursive);

        ContentDetail articleDetail = new ContentDetail("My article", "/some/uri", PageType.ARTICLE);
        FileUtils.write(collection.getReviewed().getPath().resolve("some/uri/data.json").toFile(),
                Serialiser.serialise(articleDetail), Charset.defaultCharset());


        // When we attempt to populate the release from the collection.

        FakeCollectionReader collectionReader = new FakeCollectionReader(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
        FakeCollectionWriter collectionWriter = new FakeCollectionWriter(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
        Iterable<ContentDetail> collectionContent = ContentDetailUtil.resolveDetails(collection.getReviewed(),
                collectionReader.getReviewed());

        Release result = collection.populateReleaseQuietly(
                collectionReader,
                collectionWriter,
                collectionContent);

        // Then the release is now in progress for the collection and the published flag is set to true
        assertNotNull(result);
        assertEquals(1, result.getRelatedDocuments().size());
        assertEquals("My article", result.getRelatedDocuments().get(0).getTitle());
        assertEquals("/some/uri", result.getRelatedDocuments().get(0).getUri().toString());
    }

    @Test
    public void populateReleaseQuietlyShouldAddLinksToReleasePageForCollectionContentCMD() throws ZebedeeException, IOException {
        // Given a collection that is associated with a release and has a CMD dataset
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());

        CollectionDescription description = new CollectionDescription();
        description.setId(Random.id());
        description.setName(description.getId());

        collection.getDescription().setReleaseUri(uri);
        collection.associateWithRelease(publisher1Session, release, collectionWriter);

        String releaseJsonUri = uri + "/data.json";

        collection.complete(publisher1Session, releaseJsonUri, recursive);
        collection.review(publisher2Session, releaseJsonUri, recursive);

        ContentDetail cmdDetail = new ContentDetail("My CMD dataset", "/some/uri", PageType.API_DATASET_LANDING_PAGE);
        FileUtils.write(collection.getReviewed().getPath().resolve("some/uri/data.json").toFile(),
                Serialiser.serialise(cmdDetail), Charset.defaultCharset());

        // When we attempt to populate the release from the collection.
        FakeCollectionReader collectionReader = new FakeCollectionReader(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
        FakeCollectionWriter collectionWriter = new FakeCollectionWriter(zebedee.getCollections().getPath().toString(),
                collection.getDescription().getId());
        Iterable<ContentDetail> collectionContent = ContentDetailUtil.resolveDetails(collection.getReviewed(),
                collectionReader.getReviewed());

        Release result = collection.populateReleaseQuietly(
                collectionReader,
                collectionWriter,
                collectionContent);

        // Then the release is populated with a link to the associated CMD dataset
        assertNotNull(result);
        assertEquals(1, result.getRelatedAPIDatasets().size());
        assertEquals(cmdDetail.description.title, result.getRelatedAPIDatasets().get(0).getTitle());
        assertEquals(cmdDetail.uri, result.getRelatedAPIDatasets().get(0).getUri().toString());
    }

    @Test
    public void createCollectionShouldAssociateWithReleaseIfReleaseUriIsPresent() throws Exception {
        // Given an existing release page
        ReflectionTestUtils.setField(zebedee, "permissionsService", permissionsService);

        when(permissionsService.canEdit(any(Session.class)))
                .thenReturn(true);

        setUpKeyringMocks();

        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());

        // When a new collection is created with the release uri given
        CollectionDescription collectionDescription = new CollectionDescription(Random.id());
        collectionDescription.setReleaseUri(release.getUri().toString());
        collectionDescription.setType(CollectionType.scheduled);

        Collection collection = Collection.create(collectionDescription, zebedee, publisher1Session);

        // The release page is in progress within the collection and the collection publish date has been
        // taken from the release page date.
        assertTrue(collection.isInProgress(uri));
        assertEquals(collection.getDescription().getPublishDate(), release.getDescription().getReleaseDate());
    }

    @Test(expected = BadRequestException.class)
    public void createCollectionShouldThrowExceptionIfReleaseDateIsNull() throws Exception {

        // Given an existing release page with a null release date
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, null);

        // When a new collection is created with the release uri given
        CollectionDescription collectionDescription = new CollectionDescription(Random.id());
        collectionDescription.setReleaseUri(release.getUri().toString());

        Collection.create(collectionDescription, zebedee, publisher1Session);


        // Then the expected exception is thrown
    }

    @Test(expected = ConflictException.class)
    public void createCollectionShouldThrowExceptionIfReleaseIsInAnotherCollection() throws Exception {
        // Given an existing release page which is associated with an existing collection
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());
        CollectionDescription collectionDescription = new CollectionDescription(Random.id());
        collectionDescription.setReleaseUri(release.getUri().toString());
        collectionDescription.setType(CollectionType.scheduled);

        ReflectionTestUtils.setField(zebedee, "permissionsService", permissionsService);

        when(permissionsService.canEdit(publisher1Session))
                .thenReturn(true);

        setUpKeyringMocks();

        Collection.create(collectionDescription, zebedee, publisher1Session);

        // When a new collection is created with the release uri given
        collectionDescription = new CollectionDescription(Random.id());
        collectionDescription.setReleaseUri(release.getUri().toString());

        Collection.create(collectionDescription, zebedee, publisher1Session);


        // Then the expected exception is thrown
    }


    @Test(expected = NotFoundException.class)
    public void versionShouldThrowNotFoundIfContentIsNotPublished() throws Exception {

        // Given a URI that has not been published / does not exist.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());

        // When we attempt to create a version for the page
        collection.version(publisher1Email, uri, collectionWriter);

        // Then a not found exception is thrown.
    }

    @Test(expected = ConflictException.class)
    public void versionShouldNotCreateASecondVersionForAURI() throws Exception {

        // Given a URI that has been published and already versioned in a collection.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());
        builder.createPublishedFile(uri + "/data.json");
        collection.version(publisher1Email, uri, collectionWriter);

        // When we attempt to create a version for the page for a second time
        collection.version(publisher1Email, uri, collectionWriter);

        // Then a ConflictException exception is thrown.
    }

    @Test
    public void versionShouldCreateVersionForUri() throws Exception {

        // Given an existing uri that has been publised.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());
        builder.createPublishedFile(uri + "/data.json");

        // When the version function is called for the URI
        ContentItemVersion version = collection.version(publisher1Email, uri, collectionWriter);

        // Then the version directory is created, with the page and associated files copied into it
        // check versions file exists
        Path versionsDirectoryPath = collection.getReviewed().get(Paths.get(uri).resolve(VersionedContentItem.getVersionDirectoryName()).toUri());
        assertTrue(Files.exists(versionsDirectoryPath));

        // check the json file is in there
        assertTrue(Files.exists(collection.getReviewed().get(version.getUri())));

        // check for an associated file
        assertTrue(Files.exists(collection.getReviewed().get(Paths.get(version.getUri()).resolve("data.json").toString())));
    }

    @Test(expected = NotFoundException.class)
    public void deleteVersionShouldThrowNotFoundIfVersionDoesNotExistInCollection() throws Exception {

        // Given a collection and a URI of a version that does not exist in the collection.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s/previous/v1", Random.id());

        // When we attempt to delete a version
        collection.deleteVersion("", uri);

        // Then a not found exception is thrown.
    }

    @Test(expected = BadRequestException.class)
    public void deleteVersionShouldThrowBadRequestIfNotAValidVersionUri() throws Exception {

        // Given a collection and a URI that is not a version.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());

        // When we attempt to delete a version
        collection.deleteVersion("", uri);

        // Then a BadRequestException is thrown.
    }

    @Test
    public void deleteVersionShouldDeleteVersionDirectory() throws Exception {

        // Given an existing version URI
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());
        builder.createPublishedFile(uri + "/data.json");
        ContentItemVersion version = collection.version(publisher1Email, uri, collectionWriter);

        assertTrue(Files.exists(collection.getReviewed().get(version.getUri())));
        assertTrue(Files.exists(collection.getReviewed().get(version.getUri()).resolve("data.json")));

        // When the delete version function is called for the version URI
        collection.deleteVersion("bob", version.getUri());

        // Then the versions directory is deleted.
        assertNull(collection.getReviewed().get(version.getUri()));
    }

    private Release createRelease(String uri, Date releaseDate) throws IOException {
        String trimmedUri = StringUtils.removeStart(uri, "/");
        Release release = new Release();
        release.setDescription(new PageDescription());
        release.getDescription().setPublished(false);
        release.getDescription().setReleaseDate(releaseDate);
        release.setUri(URI.create(uri));
        String content = ContentUtil.serialise(release);

        Path releasePath = zebedee.getPublished().getPath().resolve(trimmedUri + "/data.json");
        FileUtils.write(releasePath.toFile(), content);
        return release;
    }

    @Test
    public void moveContentShouldRenameInprogressFile() throws Exception {

        // Given the content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        String toUri = "/economy/inflationandpriceindices/timeseries/a9errenamed.html";
        builder.createInProgressFile(uri);

        setUpKeyringMocks();

        // When we move content
        boolean edited = collection.moveContent(publisher1Session, uri, toUri);

        // Then the file should exist only in the new location.
        assertTrue(edited);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
        assertTrue(Files.exists(inProgress.resolve(toUri.substring(1))));

        // check an event has been created for the content being created.
        assertTrue(collection.getDescription().getEventsByUri().get(uri).hasEventForType(EventType.MOVED));
    }

    @Test
    public void moveContentShouldRenameCompletedFiles() throws Exception {

        // Given the content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        String toUri = "/economy/inflationandpriceindices/timeseries/a9errenamed.html";
        builder.createCompleteFile(uri);

        setUpKeyringMocks();

        // When we move content
        boolean edited = collection.moveContent(publisher1Session, uri, toUri);

        // Then the file should exist only in the new location.
        assertTrue(edited);
        Path complete = builder.collections.get(1).resolve(Collection.COMPLETE);
        assertFalse(Files.exists(complete.resolve(uri.substring(1))));
        assertTrue(Files.exists(complete.resolve(toUri.substring(1))));

        // check an event has been created for the content being created.
        assertTrue(collection.getDescription().getEventsByUri().get(uri).hasEventForType(EventType.MOVED));
    }

    @Test
    public void moveContentShouldOverwriteExistingFiles() throws Exception {

        // Given some existing content in progress.
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        String toUri = "/economy/inflationandpriceindices/timeseries/a9errenamed.html";
        builder.createInProgressFile(uri);
        builder.createInProgressFile(toUri);

        setUpKeyringMocks();

        // When we move content to a URI where some content already exists.
        boolean edited = collection.moveContent(publisher1Session, uri, toUri);

        // Then the existing content should be overwritten.
        assertTrue(edited);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
        assertTrue(Files.exists(inProgress.resolve(toUri.substring(1))));

        // check an event has been created for the content being created.
        assertTrue(collection.getDescription().getEventsByUri().get(uri).hasEventForType(EventType.MOVED));
    }

    @Test
    public void isAllContentReviewed_shouldReturnTrueWhenEmpty() throws IOException, ZebedeeException {

        // Given an empty collection
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

        // When isAllContentReviewed() is called
        boolean allContentReviewed = collection.isAllContentReviewed(false);

        // Then the result is true
        assertTrue(allContentReviewed);
    }

    @Test
    public void isAllContentReviewed_shouldReturnFalseWhenFileInProgress() throws IOException, ZebedeeException {

        // Given a collection with a file in progress
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = spy(
                CollectionTest.createCollection(collectionPath, "isAllContentReviewed"));

        ArrayList<String> uriList = new ArrayList<>(Arrays.asList("/some/uri"));
        doReturn(uriList).when(collection).inProgressUris();

        // When isAllContentReviewed() is called
        boolean allContentReviewed = collection.isAllContentReviewed(false);

        // Then the result is false
        assertFalse(allContentReviewed);
    }

    @Test
    public void isAllContentReviewed_shouldReturnFalseWhenFileComplete() throws IOException, ZebedeeException {

        // Given a collection with a file in complete
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = spy(
                CollectionTest.createCollection(collectionPath, "isAllContentReviewed"));

        ArrayList<String> uriList = new ArrayList<>(Arrays.asList("/some/uri"));
        doReturn(uriList).when(collection).completeUris();

        // When isAllContentReviewed() is called
        boolean allContentReviewed = collection.isAllContentReviewed(false);

        // Then the result is false
        assertFalse(allContentReviewed);
    }

    @Test
    public void isAllContentReviewed_shouldReturnFalseWhenDatasetNotReviewed() throws IOException, ZebedeeException {
            // Given a collection with a dataset that has not been set to reviewed.
            Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
            Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

            CollectionDataset dataset = new CollectionDataset();
            dataset.setState(ContentStatus.Complete);
            collection.getDescription().addDataset(dataset);

            // When isAllContentReviewed() is called
            boolean allContentReviewed = collection.isAllContentReviewed(true);

            // Then the result is false
            assertFalse(allContentReviewed);
    }

    @Test
    public void isAllContentReviewed_shouldReturnFalseWhenInteractiveNotReviewed() throws IOException, ZebedeeException {
        System.setProperty(CMSFeatureFlags.ENABLE_INTERACTIVES_PUBLISHING, "true");
        CMSFeatureFlags.reset();

        // Given a collection with an interactive that has not been set to reviewed.
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

        CollectionInteractive interactive = new CollectionInteractive();
        interactive.setState(ContentStatus.Complete);
        collection.getDescription().addInteractive(interactive);

        // When isAllContentReviewed() is called
        boolean allContentReviewed = collection.isAllContentReviewed(true);

        // Then the result is false
        assertFalse(allContentReviewed);
    }

    @Test
    public void isAllContentReviewed_shouldReturnFalseWhenDatasetVersionNotReviewed() throws IOException, ZebedeeException {
            // Given a collection with a dataset version that has not been set to reviewed.
            Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
            Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

            CollectionDatasetVersion datasetVersion = new CollectionDatasetVersion();
            datasetVersion.setState(ContentStatus.Complete);
            collection.getDescription().addDatasetVersion(datasetVersion);

            // When isAllContentReviewed() is called
            boolean allContentReviewed = collection.isAllContentReviewed(true);

            // Then the result is false
            assertFalse(allContentReviewed);
    }

    @Test
    public void isAllContentReviewed_shouldReturnTrueWhenDatasetIsReviewed() throws IOException, ZebedeeException {

        // Given a collection with a dataset that has been set to reviewed.
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

        CollectionDataset dataset = new CollectionDataset();
        dataset.setState(ContentStatus.Reviewed);
        collection.getDescription().addDataset(dataset);

        // When isAllContentReviewed() is called
        boolean allContentReviewed = collection.isAllContentReviewed(false);

        // Then the result is true
        assertTrue(allContentReviewed);
    }

    @Test
    public void isAllContentReviewed_shouldReturnTrueWhenInteractiveIsReviewed() throws IOException, ZebedeeException {

        // Given a collection with an interactive that has been set to reviewed.
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

        CollectionInteractive interactive = new CollectionInteractive();
        interactive.setState(ContentStatus.Reviewed);
        collection.getDescription().addInteractive(interactive);

        // When isAllContentReviewed() is called
        boolean allContentReviewed = collection.isAllContentReviewed(false);

        // Then the result is true
        assertTrue(allContentReviewed);
    }

    @Test
    public void isAllContentReviewed_shouldReturnTrueWhenDatasetVersionIsReviewed() throws IOException, ZebedeeException {

        // Given a collection with a dataset version that has been set to reviewed.
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

        CollectionDatasetVersion datasetVersion = new CollectionDatasetVersion();
        datasetVersion.setState(ContentStatus.Reviewed);
        collection.getDescription().addDatasetVersion(datasetVersion);

        // When isAllContentReviewed() is called
        boolean allContentReviewed = collection.isAllContentReviewed(false);

        // Then the result is true
        assertTrue(allContentReviewed);
    }

    @Test
    public void getDatasetDetails() throws IOException, ZebedeeException {

        // Given a collection with a dataset.
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

        CollectionDataset dataset = new CollectionDataset();
        dataset.setUri("http://localhost:1234/datasets/123");
        dataset.setTitle("dataset wut");
        collection.getDescription().addDataset(dataset);

        // When getDatasetDetails() is called
        List<ContentDetail> datasetContent = collection.getDatasetDetails();

        // Then the expected values have been set
        ContentDetail datasetDetail = datasetContent.get(0);

        assertEquals("/datasets/123", datasetDetail.uri);
        assertEquals(PageType.API_DATASET_LANDING_PAGE, datasetDetail.getType());
        assertEquals(dataset.getTitle(), datasetDetail.description.title);
    }

    @Test
    public void getInteractiveDetails() throws IOException, ZebedeeException {

        // Given a collection with a dataset.
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

        CollectionInteractive interactive = new CollectionInteractive();
        interactive.setUri("/interactives/123");
        interactive.setTitle("title");
        collection.getDescription().addInteractive(interactive);

        // When getDatasetDetails() is called
        List<ContentDetail> interactiveContent = collection.getInteractiveDetails();

        // Then the expected values have been set
        ContentDetail interactiveDetail = interactiveContent.get(0);

        assertEquals("/interactives/123", interactiveDetail.uri);
        assertEquals(PageType.INTERACTIVE, interactiveDetail.getType());
        assertEquals(interactive.getTitle(), interactiveDetail.description.title);
    }

    @Test
    public void getDatasetVersionDetails() throws IOException, ZebedeeException {

        // Given a collection with a dataset version.
        Path collectionPath = Files.createTempDirectory(Random.id()); // create a temp directory to generate content into
        Collection collection = CollectionTest.createCollection(collectionPath, "isAllContentReviewed");

        CollectionDatasetVersion datasetVersion = new CollectionDatasetVersion();
        datasetVersion.setId("123");
        datasetVersion.setEdition("2015");
        datasetVersion.setVersion("1");
        datasetVersion.setTitle("dataset version wut");
        collection.getDescription().addDatasetVersion(datasetVersion);

        // When getDatasetVersionDetails() is called
        List<ContentDetail> datasetContent = collection.getDatasetVersionDetails();

        // Then the expected values have been set
        ContentDetail versionDetail = datasetContent.get(0);
        assertEquals("/datasets/123/editions/2015/versions/1", versionDetail.uri);
        assertEquals(PageType.API_DATASET_LANDING_PAGE, versionDetail.getType());
        assertEquals(datasetVersion.getTitle(), versionDetail.description.title);
    }

    public static Collection createCollection(Path destination, String collectionName) throws CollectionNotFoundException, IOException {

        CollectionDescription collection = new CollectionDescription(collectionName);
        collection.setType(CollectionType.manual);
        collection.setEncrypted(false);
        collection.setName(collectionName);

        String filename = PathUtils.toFilename(collectionName);
        collection.setId(filename + "-" + Random.id());
        Collection.CreateCollectionFolders(filename, destination);

        // Create the description:
        Path collectionDescriptionPath = destination.resolve(filename + ".json");
        try (OutputStream output = Files.newOutputStream(collectionDescriptionPath)) {
            Serialiser.serialise(output, collection);
        }

        return new Collection(destination.resolve(filename), null);
    }

    private void setUpKeyringMocks() throws Exception {
        SecretKey key = Keys.newSecretKey();
        when(encryptionKeyFactory.newCollectionKey())
                .thenReturn(key);

        when(collectionKeyring.get(any(), any()))
                .thenReturn(key);
    }
}
