package com.github.onsdigital.zebedee.data;

import com.github.onsdigital.zebedee.data.processing.DataIndex;
import com.github.onsdigital.zebedee.data.processing.DataPublication;
import com.github.onsdigital.zebedee.data.processing.DataPublicationFinder;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.ContentWriter;
import com.github.onsdigital.zebedee.reader.ContentReader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class DataPublisherReloaded {
    public boolean doNotCompress = false;

    public DataPublisherReloaded(boolean doNotCompress) {
        this.doNotCompress = doNotCompress;
    }

    public DataPublisherReloaded() {
        this(false);
    }

    /**
     * Run the preprocess routine that processes csdb uploads with the option of skipping timeseries filesaves
     *
     * @param publishedContentReader  reader for the master content
     * @param reviewedContentReader   reader for this publications collection content
     * @param collectionContentWriter reader for this publications collection content
     * @param collection              the collection being processed
     * @param saveTimeSeries          the option to skip saving the individual timeseries
     * @throws IOException
     * @throws ZebedeeException
     * @throws URISyntaxException
     */
    public List<String> preprocessCollection(ContentReader publishedContentReader, ContentReader reviewedContentReader, ContentWriter collectionContentWriter, Collection collection, boolean saveTimeSeries, DataIndex dataIndex) throws IOException, ZebedeeException, URISyntaxException {

        // Find all files that need data preprocessing
        List<DataPublication> dataPublications = new DataPublicationFinder().findPublications(publishedContentReader, reviewedContentReader, collection);

        // For each file in this collection
        for (DataPublication dataPublication : dataPublications) {
            // If a file upload exists
            if (dataPublication.hasUpload())
                dataPublication.process(publishedContentReader, reviewedContentReader, collectionContentWriter, saveTimeSeries, dataIndex);
        }

        // Get the list of uris in reviewed
        List<String> uris = reviewedContentReader.listUris();
        return uris;
    }
}
