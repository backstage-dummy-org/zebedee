package com.github.onsdigital.zebedee.model.content;

import com.github.onsdigital.zebedee.Builder;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.content.page.base.Page;
import com.github.onsdigital.zebedee.content.page.statistics.data.timeseries.TimeSeries;
import com.github.onsdigital.zebedee.data.framework.DataBuilder;
import com.github.onsdigital.zebedee.data.framework.DataPagesGenerator;
import com.github.onsdigital.zebedee.data.framework.DataPagesSet;
import com.github.onsdigital.zebedee.data.processing.DataPublicationDetailsTest;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.Session;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.CollectionWriter;
import com.github.onsdigital.zebedee.model.ZebedeeCollectionReader;
import com.github.onsdigital.zebedee.model.ZebedeeCollectionWriter;
import com.github.onsdigital.zebedee.reader.CollectionReader;
import com.github.onsdigital.zebedee.reader.ContentReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;

/**
 * Created by thomasridd on 1/26/16.
 */
public class CompoundContentReaderTest {
    Zebedee zebedee;
    Builder bob;
    Session publisher;
    Session reviewer;

    Collection collection;
    ContentReader publishedReader;
    CollectionReader collectionReader;
    CollectionWriter collectionWriter;
    DataBuilder dataBuilder;
    DataPagesGenerator generator;

    TimeSeries inProgressPage;
    TimeSeries completePage;
    TimeSeries reviewedPage;
    TimeSeries publishedPage;

    /**
     * Setup generates an instance of zebedee plus a collection
     *
     * It
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {

        bob = new Builder(DataPublicationDetailsTest.class);
        zebedee = new Zebedee(bob.zebedee, false);

        publisher = zebedee.openSession(bob.publisher1Credentials);
        reviewer = zebedee.openSession(bob.reviewer1Credentials);

        dataBuilder = new DataBuilder(zebedee, publisher, reviewer);
        generator = new DataPagesGenerator();

        CollectionDescription collectionDescription = new CollectionDescription();
        collectionDescription.name = "DataPublicationDetails";
        collectionDescription.isEncrypted = true;
        collection = Collection.create(collectionDescription, zebedee, publisher);

        publishedReader = new ContentReader(zebedee.published.path);
        collectionReader = new ZebedeeCollectionReader(zebedee, collection, publisher);
        collectionWriter = new ZebedeeCollectionWriter(zebedee, collection, publisher);

        // add a set of data in a collection
        inProgressPage = generator.exampleTimeseries("inprogress", "abcd");
        completePage = generator.exampleTimeseries("complete", "abcd");
        reviewedPage = generator.exampleTimeseries("reviewed", "abcd");
        publishedPage = generator.exampleTimeseries("published", "abcd");

        dataBuilder.addInProgressPage("/timeseries/inprogress", inProgressPage, collection, collectionWriter);
        dataBuilder.addInProgressPage("/timeseries/complete", completePage, collection, collectionWriter);
        dataBuilder.addInProgressPage("/timeseries/reviewed", reviewedPage, collection, collectionWriter);
        dataBuilder.publishPage(publishedPage, "/timeseries/published");
    }

    @After
    public void tearDown() throws IOException {
        bob.delete();
    }

    @Test
    public void compoundReader_givenSingleReader_canReadContent() throws IOException, NotFoundException {
        // Given
        // published content
        ContentReader contentReader = publishedReader;
        String uri = "/timeseries/published";
        CompoundContentReader compoundContentReader = new CompoundContentReader().add(contentReader);

        // When
        //
        TimeSeries timeSeries = (TimeSeries) compoundContentReader.getContent(uri);

        // Then
        // the page should have been read
        assertNotNull(timeSeries);
        assertEquals("published", timeSeries.getCdid());
    }

    @Test
    public void compoundReader_givenCollectionReader_readsContentFromAllLevels() throws IOException, NotFoundException {
        // Given
        //
        CollectionReader collectionReader = this.collectionReader;
        CompoundContentReader compoundContentReader = new CompoundContentReader().add(collectionReader);

        // When
        //
        TimeSeries inProgress = (TimeSeries) compoundContentReader.getContent("/timeseries/inprogress");
        TimeSeries complete = (TimeSeries) compoundContentReader.getContent("/timeseries/complete");
        TimeSeries reviewed = (TimeSeries) compoundContentReader.getContent("/timeseries/reviewed");

        // Then
        //
        assertEquals("inprogress", inProgress.getCdid());
        assertEquals("complete", complete.getCdid());
        assertEquals("reviewed", reviewed.getCdid());
    }

    @Test
    public void compoundReader_givenMultilevelCollectionReader_readsContentFromTop() throws IOException, BadRequestException, NotFoundException {
        // Given
        // we put another version of the published page which we put into inProgress
        TimeSeries duplicatePage = generator.exampleTimeseries("duplicate", "abcd");
        dataBuilder.addInProgressPage("/timeseries/published", duplicatePage, collection, collectionWriter);


        // When
        // we read from a compound where collectionReader has been added after published reader
        CompoundContentReader compoundContentReader = new CompoundContentReader().add(publishedReader).add(collectionReader);
        TimeSeries retrieved = (TimeSeries) compoundContentReader.getContent("/timeseries/published");

        // Then
        // we expect retrieved to be the collection version
        assertEquals("duplicate", retrieved.getCdid());
    }
}