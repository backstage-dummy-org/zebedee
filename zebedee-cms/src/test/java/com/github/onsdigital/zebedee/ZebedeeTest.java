package com.github.onsdigital.zebedee;

import com.github.onsdigital.zebedee.api.Root;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.model.Collection;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ZebedeeTest {

	Path expectedPath;
	Builder builder;
	Map<String, String> env;

	@Before
	public void setUp() throws Exception {
		env = Root.env;
		Root.env = new HashMap<>(); // Run tests with known environment variables

		builder = new Builder(this.getClass());
		expectedPath = builder.parent.resolve(Zebedee.ZEBEDEE);
	}

	@After
	public void tearDown() throws Exception {
		builder.delete();
		Root.env = env;
	}

	@Test
	public void shouldCreate() throws IOException, UnauthorizedException {

		// Given
		// No existing Zebedee structure
		FileUtils.deleteDirectory(expectedPath.toFile());

		// When
		Zebedee.create(builder.parent);

		// Then
		assertTrue(Files.exists(expectedPath));
		assertTrue(Files.exists(expectedPath.resolve(Zebedee.PUBLISHED)));
        assertTrue(Files.exists(expectedPath.resolve(Zebedee.COLLECTIONS)));
        assertTrue(Files.exists(expectedPath.resolve(Zebedee.USERS)));
	}

	@Test
	public void shouldInstantiate() throws IOException {

		// Given
		// An existing Zebedee structure

		// When
		new Zebedee(expectedPath);

		// Then
		// No error should occur.
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldNotInstantiate() throws IOException {

		// Given
		// No Zebedee structure
		FileUtils.deleteDirectory(expectedPath.toFile());

		// When
		new Zebedee(expectedPath);

		// Then
		// An exception should be thrown
	}

	@Test
	public void shouldListReleases() throws IOException {

		// Given
		Zebedee zebedee = new Zebedee(expectedPath);

		// When
		List<Collection> releases = zebedee.collections.list();

		// Then
		assertEquals(builder.collections.size(), releases.size());
	}

	@Test
	public void shouldNotBeBeingEdited() throws IOException {

		// Given
		Zebedee zebedee = new Zebedee(expectedPath);

		// When
		int actual = zebedee.isBeingEdited(builder.contentUris.get(0));

		// Then
		assertEquals(0, actual);
	}

	@Test
	public void shouldBeBeingEdited() throws IOException {

		// Given
		Zebedee zebedee = new Zebedee(expectedPath);
		String path = builder.contentUris.get(0).substring(1);
        Path reviewed = builder.collections.get(0).resolve(Collection.REVIEWED);
        Path beingEdited = reviewed.resolve(path);
        Files.createDirectories(beingEdited.getParent());
		Files.createFile(beingEdited);

		// When
		int actual = zebedee.isBeingEdited(builder.contentUris.get(0));

		// Then
		assertEquals(1, actual);
	}

	@Test
	public void shouldPublish() throws IOException {

		// Given
		// There is content ready to be published:
		Zebedee zebedee = new Zebedee(expectedPath);
		Collection release = new Collection(builder.collections.get(1), zebedee);
		String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createReviewedFile(uri);

		// When
		boolean published = zebedee.publish(release);

		// Then
		assertTrue(published);
		Path launchpadPath = builder.zebedee.resolve(Zebedee.LAUNCHPAD);
		assertTrue(Files.exists(launchpadPath.resolve(uri.substring(1))));
	}

	@Test
	public void shouldNotPublishIfAnythingInProgress() throws IOException {

		// Given
		// There is content ready to be published:
		Zebedee zebedee = new Zebedee(expectedPath);
		Collection release = new Collection(builder.collections.get(1), zebedee);
		String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createInProgressFile(uri);

		// When
		boolean published = zebedee.publish(release);

		// Then
		assertFalse(published);
		Path publishedPath = builder.zebedee.resolve(Zebedee.PUBLISHED);
		assertFalse(Files.exists(publishedPath.resolve(uri.substring(1))));
	}

    @Test
    public void shouldDeleteCollectionAfterPublish() throws IOException {

        // Given
        // There is content ready to be published:
        Zebedee zebedee = new Zebedee(expectedPath);
        Collection release = new Collection(builder.collections.get(1), zebedee);
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createReviewedFile(uri);

        // When
        zebedee.publish(release);

        // Then
        // The release folder should have been deleted:
        Path releaseFolder = builder.collections.get(1);
        assertFalse(Files.exists(releaseFolder));
    }

}