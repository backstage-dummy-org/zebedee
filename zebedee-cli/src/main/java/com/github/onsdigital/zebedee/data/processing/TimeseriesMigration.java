package com.github.onsdigital.zebedee.data.processing;

import com.github.onsdigital.zebedee.content.page.base.Page;
import com.github.onsdigital.zebedee.content.page.statistics.data.timeseries.TimeSeries;
import com.github.onsdigital.zebedee.content.page.statistics.dataset.TimeSeriesDataset;
import com.github.onsdigital.zebedee.content.page.statistics.dataset.Version;
import com.github.onsdigital.zebedee.data.processing.setup.DataIndexBuilder;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.model.ContentWriter;
import com.github.onsdigital.zebedee.model.content.item.VersionedContentItem;
import com.github.onsdigital.zebedee.reader.ContentReader;
import com.github.onsdigital.zebedee.reader.FileSystemContentReader;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TimeseriesMigration {

    public static void main(String[] args) throws ZebedeeException, IOException, InterruptedException, URISyntaxException {
        migrateTimeseries(Paths.get("/Users/carlhembrough/dev/zebedee/zebedee/masterlive"), Paths.get("/Users/carlhembrough/dev/zebedee/migration"));
    }

    public static void migrateTimeseries(String[] args) throws InterruptedException, ZebedeeException, IOException, URISyntaxException {
        // args[1] - source data directory
        // args[2] - destination directory to save the updated timeseries (can be a collection or master)

        Path source = Paths.get(args[1]);
        Path destination = Paths.get(args[2]);

        long start = System.currentTimeMillis();

        migrateTimeseries(source, destination);

        long taken = System.currentTimeMillis() - start;
        System.out.println("seconds taken: " + taken / 1000);
    }

    private static void migrateTimeseries(Path source, Path destination) throws ZebedeeException, IOException, InterruptedException, URISyntaxException {

        // - build data index from existing timeseries structure
        // - find all datasets on the site so we can re-run them to generate timeseries
        // - order by date
        // - create a collection for the whole migration
        // - run the timeseries generator for each version of the dataset into the collection
        // - replace the old timeseries files with timeseries landing pages

        // - merge the collection files into master on publishing when deployment happens

        // - take one web server at a time, copy from publishing to the web server + deploy latest code

        ContentReader publishedContentReader = new FileSystemContentReader(source); // read dataset / timeseries content from master

        ContentReader destinationContentReader = new FileSystemContentReader(destination);
        ContentWriter destinationContentWriter = new ContentWriter(destination); // write output content to collection

        System.out.println("Building data index...");
        DataIndex dataIndex = DataIndexBuilder.buildDataIndex(publishedContentReader); // build the dataindex of existing timeseries to determine location of output

        System.out.println("Building migration index...");
        // data index used specifically for the migration to carry over certain field values from timeseries.
        Map<String, TimeseriesMigrationData> migrationIndex = new HashMap<>();
        for (String url : dataIndex.index.values()) {

            //System.out.println("data index url = " + url);

            // read values from
            TimeSeries content = (TimeSeries) publishedContentReader.getContent(url);

            TimeseriesMigrationData data = new TimeseriesMigrationData();
            data.monthLabelStyle = content.getDescription().getMonthLabelStyle();
            data.relatedData = content.getRelatedData();

            data.title = content.getDescription().getTitle();
            data.unit = content.getDescription().getUnit();
            data.preunit = content.getDescription().getPreUnit();
            data.keynote = content.getDescription().getKeyNote();

            migrationIndex.put(url, data);
        }


        // find all dataset files on the site including versions.
        List<TimeseriesDatasetFiles> datasetDownloads = getTimeseriesDatasets(source);

        // load the pages
        List<TimeseriesDatasetContainer> datasetContainers = loadPageObjectsForDatasets(publishedContentReader, datasetDownloads);

        // sort by release date.
        sortDatasetsByReleaseDate(datasetContainers);

        for (TimeseriesDatasetContainer container : datasetContainers) {
            System.out.println("------------------------loadPageObjectsForDatasets------------------------------");
            System.out.println("CsdbPath() = " + container.timeseriesDatasetFiles.getCsdbPath());
            System.out.println("DatasetPath() = " + container.timeseriesDatasetFiles.getDatasetPath());
            System.out.println("Uri = " + container.timeSeriesDataset.getUri());
            System.out.println("release date = " + container.timeSeriesDataset.getDescription().getReleaseDate());
        }

        System.out.println("Number of datasets: " + datasetContainers.size());

        int count = 1;

        // iterate each dataset found
        for (TimeseriesDatasetContainer datasetContainer : datasetContainers) {

            TimeSeriesDataset dataset = datasetContainer.timeSeriesDataset;
            TimeseriesDatasetFiles datasetFiles = datasetContainer.timeseriesDatasetFiles;

            System.out.println("------------------------------------------------------");
            System.out.println("Processing dataset : " + count + " of " + datasetContainers.size());
            System.out.println("dataset.getUri() = " + dataset.getUri());
            System.out.println("dataset.getDescription().getReleaseDate() = " + dataset.getDescription().getReleaseDate());
            count++;

            String datasetUri = getDatasetUri(dataset);
            String landingPageUri = Paths.get(datasetUri).getParent().toString();

            prepareDatasetContentInDestination(publishedContentReader, destinationContentReader, destinationContentWriter, dataset, datasetFiles, datasetUri, landingPageUri);

            // create the data publication object to pass into the data processor.
            DataPublication dataPublication = new DataPublication(publishedContentReader, destinationContentReader, datasetUri);

            // process the dataset as it would normally be.
            if (dataPublication.hasUpload()) {
                boolean saveTimeSeries = true;
                System.out.println("--- Processing dataset... ");
                dataPublication.process(destinationContentReader, destinationContentReader, destinationContentWriter, saveTimeSeries, dataIndex, new ArrayList<>());
            }


            // inject timeseries vales into the newly generated timeseries.
            TimeseriesFinder finder = new TimeseriesFinder();
            for (Path path : finder.findTimeseries(destination)) {

                System.out.println("migrating values from old timeseries to new timeseries:");

                String datauri = "/" + destination.relativize(path).toString();
                String uri = datauri.substring(0, datauri.length() - "/data.json".length());
                String oldUri = Paths.get(uri).getParent().toString();

                System.out.println("oldUri = " + oldUri);
                System.out.println("uri = " + uri);

                TimeseriesMigrationData timeseriesMigrationData = migrationIndex.get(oldUri);
                TimeSeries newTimeseries = (TimeSeries) destinationContentReader.getContent(uri);

                System.out.println("timeseriesMigrationData.monthLabelStyle = " + timeseriesMigrationData.monthLabelStyle);
                newTimeseries.getDescription().setMonthLabelStyle(timeseriesMigrationData.monthLabelStyle);
                newTimeseries.setRelatedData(timeseriesMigrationData.relatedData);

                destinationContentWriter.writeObject(newTimeseries, datauri);
            }

        }

        // inject timeseries vales into the newly generated timeseries.
        TimeseriesFinder finder = new TimeseriesFinder();
        for (Path path : finder.findTimeseries(destination)) {

            System.out.println("migrating FINAL values from old timeseries to new timeseries:");

            String datauri = "/" + destination.relativize(path).toString();
            String uri = datauri.substring(0, datauri.length() - "/data.json".length());
            String oldUri = Paths.get(uri).getParent().toString();

            TimeseriesMigrationData timeseriesMigrationData = migrationIndex.get(oldUri);
            TimeSeries newTimeseries = (TimeSeries) destinationContentReader.getContent(uri);

            System.out.println("timeseriesMigrationData.title = " + timeseriesMigrationData.title);
            newTimeseries.getDescription().setTitle(timeseriesMigrationData.title);
            newTimeseries.getDescription().setUnit(timeseriesMigrationData.unit);
            newTimeseries.getDescription().setPreUnit(timeseriesMigrationData.preunit);
            newTimeseries.getDescription().setKeyNote(timeseriesMigrationData.keynote);

            destinationContentWriter.writeObject(newTimeseries, datauri);
        }
    }

    private static String getDatasetUri(TimeSeriesDataset dataset) {
        String datasetUri = dataset.getUri().toString();
        System.out.println("datasetUri = " + datasetUri);

        // if processing a versioned dataset, determine the root URL of the dataset.
        if (VersionedContentItem.isVersionedUri(datasetUri)) {
            datasetUri = VersionedContentItem.resolveBaseUri(datasetUri);
            System.out.println("VERSIONED datasetUri = " + datasetUri);
        }
        return datasetUri;
    }

    private static void prepareDatasetContentInDestination(ContentReader publishedContentReader, ContentReader destinationContentReader, ContentWriter destinationContentWriter, TimeSeriesDataset dataset, TimeseriesDatasetFiles datasetFiles, String datasetUri, String landingPageUri) throws ZebedeeException, IOException {
        try {
            // attempt to read the existing dataset if it exists
            Page content = destinationContentReader.getContent(datasetUri);
            //content.getDescription().setNextRelease(null);
            //destinationContentWriter.writeObject(content, datasetUri + "/.json");

            System.out.println("--- dataset already exists - creating version");

            // create a version of the existing dataset.
            VersionedContentItem versionedContentItem = new VersionedContentItem(datasetUri);
            versionedContentItem.createVersion(destinationContentReader, destinationContentWriter);

            updateLandingPageReleaseDate(destinationContentReader, destinationContentWriter, dataset, landingPageUri);

            // copy the dataset and CSDB from the version directory to the destination.
            copyDatasetJsonToDestination(destinationContentWriter, dataset, datasetUri);
            copyCsdbFileToLocation(publishedContentReader, destinationContentWriter, datasetFiles, datasetUri);

        } catch (NotFoundException e) {

            // if the dataset does not alreadt exist in the destination, just move it from source into place.
            System.out.println("--- dataset does not exist - creating copy...");

            copyLandingPageToDestination(publishedContentReader, destinationContentWriter, dataset, landingPageUri);
            copyDatasetJsonToDestination(destinationContentWriter, dataset, datasetUri);
            copyCsdbFileToLocation(publishedContentReader, destinationContentWriter, datasetFiles, datasetUri);
        }
    }

    private static void updateLandingPageReleaseDate(ContentReader destinationContentReader, ContentWriter destinationContentWriter, TimeSeriesDataset dataset, String landingPageUri) throws ZebedeeException, IOException {
        // if the dataset has a version, we want to use the date it was updated as the release date
        if (dataset.getVersions() != null && dataset.getVersions().size() > 0) {

            dataset.getVersions()
                    .stream()
                    .sorted((o1, o2) -> o1.getUpdateDate().compareTo(o2.getUpdateDate()))
                    .forEach(version -> System.out.println("VersionList: " + version.getLabel() + " " + version.getUpdateDate() + " " + version.getCorrectionNotice()));

            Version lastVersion = dataset.getVersions()
                    .stream()
                    .sorted((o1, o2) -> o1.getUpdateDate().compareTo(o2.getUpdateDate()))
                    .reduce((v1, v2) -> v2)
                    .orElse(null);

            System.out.println("SelectedVersion : " + lastVersion.getLabel() + " " + lastVersion.getUpdateDate() + " " + lastVersion.getCorrectionNotice());

            if (StringUtils.isBlank(lastVersion.getCorrectionNotice())) {
                System.out.println("There is no correction notice here, using the last version date");

                // set the landing page release date to the most recent version date in the dataset.
                updateLandingPageReleaseDateWithVersionDate(destinationContentReader, destinationContentWriter, landingPageUri, lastVersion);
            } else {
                System.out.println("There is a correction notice here. Do not change the release date");
            }
        } else {
            System.out.println("No versions found here. Do not change the release date.");
        }
    }

    private static void updateLandingPageReleaseDateWithVersionDate(ContentReader destinationContentReader, ContentWriter destinationContentWriter, String landingPageUri, Version lastVersion) throws ZebedeeException, IOException {
        System.out.println("Updating existing landing page release date..." + landingPageUri);
        Page landingPage = destinationContentReader.getContent(landingPageUri);
        landingPage.getDescription().setReleaseDate(lastVersion.getUpdateDate());
        destinationContentWriter.writeObject(landingPage, landingPageUri + "/data.json");
    }

    private static void copyLandingPageToDestination(ContentReader publishedContentReader, ContentWriter destinationContentWriter, TimeSeriesDataset dataset, String landingPageUri) throws ZebedeeException, IOException {
        System.out.println("Copy landing page from published..." + landingPageUri);
        Page landingPage = publishedContentReader.getContent(landingPageUri);
        landingPage.getDescription().setReleaseDate(dataset.getDescription().getReleaseDate());
        destinationContentWriter.writeObject(landingPage, landingPageUri + "/data.json");
    }

    private static void copyCsdbFileToLocation(ContentReader publishedContentReader, ContentWriter destinationContentWriter, TimeseriesDatasetFiles datasetFiles, String datasetUri) throws IOException, ZebedeeException {
        // copy csdb file to new location
        String csdbSource = datasetFiles.getCsdbPath().toString();
        String csdbDestination = datasetUri + "/" + datasetFiles.getCsdbPath().getFileName().toString();

        System.out.println("Copying CSDB file from " + csdbSource + " to " + csdbDestination);

        try (InputStream inputStream = publishedContentReader.getResource(csdbSource).getData()) {
            destinationContentWriter.write(inputStream, csdbDestination);
        }
    }

    private static void copyDatasetJsonToDestination(ContentWriter destinationContentWriter, TimeSeriesDataset dataset, String datasetUri) throws IOException, BadRequestException {
        // clear downloads
        //dataset.setDownloads(new ArrayList<>());
        // clear versions
        //dataset.setVersions(new ArrayList<>());

        dataset.getDescription().setReleaseDate(null);
        dataset.getDescription().setUnit(null);
        dataset.getDescription().setPreUnit(null);
        dataset.getDescription().setVersionLabel(null);

        // write json to destination
        String datasetPath = datasetUri + "/data.json";
        System.out.println("Writing dataset file to " + datasetPath);
        destinationContentWriter.writeObject(dataset, datasetPath);
    }

    private static List<TimeseriesDatasetContainer> loadPageObjectsForDatasets(ContentReader publishedContentReader, List<TimeseriesDatasetFiles> datasetDownloads) throws ZebedeeException, IOException {
        List<TimeseriesDatasetContainer> datasetContainers = new ArrayList<>();

        for (TimeseriesDatasetFiles TimeseriesDatasetFile : datasetDownloads) {


            Page page = publishedContentReader.getContent(TimeseriesDatasetFile.getRootPath().toString());

            if (page instanceof TimeSeriesDataset == false) {
                System.out.println("This is not a timeseries dataset.");
                continue;
            }

            TimeSeriesDataset timeseriesDatasetPage = (TimeSeriesDataset) page;

            if (timeseriesDatasetPage.getVersions() != null) {
                Version lastVersion = timeseriesDatasetPage.getVersions()
                        .stream()
                        .sorted((o1, o2) -> o1.getUpdateDate().compareTo(o2.getUpdateDate()))
                        .reduce((v1, v2) -> v2)
                        .orElse(null);

                if (lastVersion != null) {
                    timeseriesDatasetPage.getDescription().setReleaseDate(lastVersion.getUpdateDate());
                }
            }

            TimeseriesDatasetContainer timeseriesDatasetContainer = new TimeseriesDatasetContainer();
            timeseriesDatasetContainer.timeSeriesDataset = timeseriesDatasetPage;
            timeseriesDatasetContainer.timeseriesDatasetFiles = TimeseriesDatasetFile;
            datasetContainers.add(timeseriesDatasetContainer);
        }
        return datasetContainers;
    }

    private static void sortDatasetsByReleaseDate(List<TimeseriesDatasetContainer> datasets) {
        Comparator<TimeseriesDatasetContainer> byReleaseDate = (ds1, ds2) -> ds1.timeSeriesDataset.getDescription().getReleaseDate()
                .compareTo(ds2.timeSeriesDataset.getDescription().getReleaseDate());
        datasets.sort(byReleaseDate);
    }

    /**
     * find all CSDB files to determine the timeseries datasets.
     *
     * @param source
     * @return
     */
    private static List<TimeseriesDatasetFiles> getTimeseriesDatasets(Path source) {
        CsdbFinder csdbFinder = new CsdbFinder();
        boolean includeVersions = true;
        csdbFinder.find(source, includeVersions);
        List<TimeseriesDatasetFiles> datasetDownloads = new ArrayList<>();
        for (String uri : csdbFinder.uris) {
            datasetDownloads.add(new TimeseriesDatasetFiles(Paths.get(uri)));
        }
        return datasetDownloads;
    }
}
