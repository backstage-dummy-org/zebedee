package com.github.onsdigital.zebedee.data.processing;

import com.github.davidcarboni.cryptolite.Random;
import com.github.onsdigital.zebedee.Builder;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.content.page.base.Page;
import com.github.onsdigital.zebedee.content.page.base.PageType;
import com.github.onsdigital.zebedee.content.page.statistics.data.timeseries.TimeSeries;
import com.github.onsdigital.zebedee.data.framework.DataBuilder;
import com.github.onsdigital.zebedee.data.framework.DataPagesGenerator;
import com.github.onsdigital.zebedee.data.framework.DataPagesSet;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

import static org.junit.Assert.*;

/**
 * DataProcessor is the workhorse of the Data Publisher
 *
 * It combines
 *  - a timeseries generated by Brian
 *  - any existing version of this timeseries
 *  - a data landing page
 *  - a timeseries dataset page
 *
 *
 */
public class DataProcessorTest {
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

    DataPagesSet published;
    DataPagesSet inReview;

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
        inReview = generator.generateDataPagesSet("dataprocessor", "inreview", 2015, 2, "");
        dataBuilder.addReviewedDataPagesSet(inReview, collection, collectionWriter);

        // add a set of data to published
        published = generator.generateDataPagesSet("dataprocessor", "published", 2015, 2, "");
        dataBuilder.publishDataPagesSet(published);
    }

    @After
    public void tearDown() throws IOException {
        bob.delete();
    }





    @Test
    public void publishUriForTimeseries_givenDetails_returnsParentTimeseriesFolder() throws ParseException, URISyntaxException, IOException, ZebedeeException {
        // Given
        // A timeseries from our reviewed dataset
        DataPublicationDetails details = inReview.getDetails(publishedReader, collectionReader.getReviewed());
        TimeSeries series = inReview.timeSeriesList.get(0);

        // When
        // we get the publish uri for a timeseries
        String publishUri = new DataProcessor().publishUriForTimeseries(series, details);

        // Then
        // we expect it to be the cdid at the same root
        String cdid = series.getCdid();
        assertEquals(details.parentFolderUri + "/timeseries/" + cdid, publishUri);
    }






    @Test
    public void initialTimeseries_givenNewTimeseries_returnsEmptyTimeseries() throws IOException, ParseException, URISyntaxException, ZebedeeException {
        // Given
        // We upload a data collection to a zebedee instance where we don't have current published content
        DataPublicationDetails details = inReview.getDetails(publishedReader, collectionReader.getReviewed());
        TimeSeries timeSeries = inReview.timeSeriesList.get(0);

        // When
        // we get the initialTimeseries
        TimeSeries initial = new DataProcessor().initialTimeseries(timeSeries, publishedReader, details);

        // Then
        // we expect it to be a skeleton timeseries
        assertEquals(PageType.timeseries, initial.getType());
        assertEquals(0, initial.years.size());
        assertEquals(0, initial.months.size());
        assertEquals(0, initial.quarters.size());
    }

    @Test
    public void initialTimeseries_givenExistingTimeseries_returnsCurrentTimeseries() throws IOException, ParseException, URISyntaxException, ZebedeeException {
        // Given
        // We upload a data collection to a zebedee instance with current published content
        DataPagesSet republish = generator.generateDataPagesSet("dataprocessor", "published", 2016, 2, "");
        dataBuilder.addReviewedDataPagesSet(republish, collection, collectionWriter);

        DataPublicationDetails details = republish.getDetails(publishedReader, collectionReader.getReviewed());
        TimeSeries timeSeries = republish.timeSeriesList.get(0);

        // When
        // we get the initialTimeseries
        TimeSeries initial = new DataProcessor().initialTimeseries(timeSeries, publishedReader, details);

        // Then
        // we expect it to be the published timeseries complete with existing data
        assertEquals(PageType.timeseries, initial.getType());
        assertNotEquals(0, initial.years.size());
        assertNotEquals(0, initial.months.size());
        assertNotEquals(0, initial.quarters.size());
    }








    @Test
    public void syncMetadata_givenVariedDetailSet_takesContactsFromLandingPage() throws IOException, ZebedeeException, ParseException, URISyntaxException {

        // Given
        // A timeseries and the initial timeseries
        DataPublicationDetails details = inReview.getDetails(publishedReader, collectionReader.getReviewed());
        TimeSeries timeSeries = inReview.timeSeriesList.get(0);

        DataProcessor processor = new DataProcessor();
        TimeSeries initial = processor.initialTimeseries(timeSeries, publishedReader, details);

        // If
        // we randomise contacts
        setRandomContact(timeSeries);
        setRandomContact(initial);
        setRandomContact(details.landingPage);
        setRandomContact(details.datasetPage);


        // When
        // we sync details
        TimeSeries synced = processor.syncLandingPageMetadata(initial, details);
        synced = processor.syncTimeSeriesMetadata(synced, timeSeries);

        // Then
        // the timeseries should have contact details from the landing page
        assertEquals(details.landingPage.getDescription().getContact().getName(), synced.getDescription().getContact().getName());
        assertEquals(details.landingPage.getDescription().getContact().getEmail(), synced.getDescription().getContact().getEmail());
        assertEquals(details.landingPage.getDescription().getContact().getTelephone(), synced.getDescription().getContact().getTelephone());
        assertEquals(details.landingPage.getDescription().getContact().getOrganisation(), synced.getDescription().getContact().getOrganisation());
    }

    @Test
    public void syncMetadata_givenNewTimeseries_shouldTransferSeasonalAdjustmentAndCDID() throws IOException, ZebedeeException, URISyntaxException {
        // Given
        // A currently unpublished timeseries
        DataPublicationDetails details = inReview.getDetails(publishedReader, collectionReader.getReviewed());
        TimeSeries timeSeries = inReview.timeSeriesList.get(0);

        DataProcessor processor = new DataProcessor();
        TimeSeries initial = processor.initialTimeseries(timeSeries, publishedReader, details);

        // When
        // we sync details
        TimeSeries synced = processor.syncLandingPageMetadata(initial, details);
        synced = processor.syncTimeSeriesMetadata(synced, timeSeries);

        // Then
        // we expect the newPage to have copied details from the
        assertEquals(timeSeries.getCdid(), synced.getCdid());
        assertEquals(timeSeries.getDescription().getSeasonalAdjustment(), synced.getDescription().getSeasonalAdjustment());
    }

    @Test
    public void syncMetadata_overExistingTimeSeries_shouldTransferNameFromTimeSeriesIfNameBlank() throws IOException, ZebedeeException, ParseException, URISyntaxException {
        // Given
        // The initial timeseries generated by a publish over existing data
        DataPagesSet republish = generator.generateDataPagesSet("dataprocessor", "published", 2016, 2, "");
        dataBuilder.addReviewedDataPagesSet(republish, collection, collectionWriter);

        DataPublicationDetails details = republish.getDetails(publishedReader, collectionReader.getReviewed());
        TimeSeries timeSeries = republish.timeSeriesList.get(0);

        DataProcessor processor = new DataProcessor();
        TimeSeries initial = processor.initialTimeseries(timeSeries, publishedReader, details);
        initial.getDescription().setTitle("");

        // When
        // we sync details
        TimeSeries synced = processor.syncLandingPageMetadata(initial, details);
        synced = processor.syncTimeSeriesMetadata(synced, timeSeries);

        // Then
        // we expect the name to come from the new timeseries
        assertEquals(timeSeries.getDescription().getTitle(), synced.getDescription().getTitle());
    }

    @Test
    public void syncMetadata_overExistingTimeSeries_shouldNotTransferNameIfNameExists() throws IOException, ParseException, URISyntaxException, ZebedeeException {
        // Given
        // We create a publish over an existing dataset
        DataPagesSet republish = generator.generateDataPagesSet("dataprocessor", "published", 2016, 2, "");
        dataBuilder.addReviewedDataPagesSet(republish, collection, collectionWriter);

        DataPublicationDetails details = republish.getDetails(publishedReader, collectionReader.getReviewed());
        TimeSeries timeSeries = republish.timeSeriesList.get(0);
        TimeSeries publishedTimeseries = published.timeSeriesList.get(0);

        DataProcessor processor = new DataProcessor();
        TimeSeries initial = processor.initialTimeseries(timeSeries, publishedReader, details);

        // When
        // we sync details
        TimeSeries synced = processor.syncLandingPageMetadata(initial, details);
        synced = processor.syncTimeSeriesMetadata(synced, timeSeries);

        // Then
        // we expect the name to come from the old timeseries
        assertEquals(publishedTimeseries.getDescription().getTitle(), synced.getDescription().getTitle());
    }

    @Test
    public void processTimeseries_overExistingTimeSeries_persistsManualSetFields() throws ParseException, URISyntaxException, ZebedeeException, IOException {
        // Given
        // We create a published dataset with distinct manual metadata
        DataPagesSet manual = generator.generateDataPagesSet("dataprocessor", "manual", 2015, 2, "");
        TimeSeries manualSeries = manual.timeSeriesList.get(0);
        manualSeries.getDescription().setUnit(Random.id());
        manualSeries.getDescription().setPreUnit(Random.id());
        manualSeries.getDescription().setNationalStatistic(true);
        dataBuilder.publishDataPagesSet(manual);

        DataPagesSet review = generator.generateDataPagesSet("dataprocessor", "manual", 2016, 2, "");
        dataBuilder.addReviewedDataPagesSet(review, collection, collectionWriter);
        TimeSeries series = review.timeSeriesList.get(0);

        // When
        // we sync details
        TimeSeries processed = new DataProcessor().processTimeseries(publishedReader, review.getDetails(publishedReader, collectionReader.getReviewed()), series);

        // Then
        // we expect the manual data to be persisted
        assertEquals(manualSeries.getDescription().getUnit(), processed.getDescription().getUnit());
        assertEquals(manualSeries.getDescription().getPreUnit(), processed.getDescription().getPreUnit());
        assertEquals(manualSeries.getDescription().isNationalStatistic(), processed.getDescription().isNationalStatistic());
    }


    /**
     * Helper method
     */
    private void setRandomContact(Page page) {
        page.getDescription().getContact().setEmail(Random.id());
        page.getDescription().getContact().setName(Random.id());
        page.getDescription().getContact().setOrganisation(Random.id());
        page.getDescription().getContact().setTelephone(Random.id());
    }

}