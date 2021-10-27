package com.github.onsdigital.zebedee.reader.api.bean;

import com.github.onsdigital.zebedee.search.indexing.Document;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Object representing a response for the PublishedIndex endpoint
 */
public class PublishedIndexResponse {

    private int count;
    private List<Item> items;
    private int limit;
    private int offset;
    @SerializedName("total_count")
    private int totalCount;

    /**
     * Constructor to create an index response from a list of documents and details of the paging request
     *
     * @param docs       List of Documents to return in response
     * @param totalCount Total count of all available documents (docs returned + docs not returned)
     * @param offset     Index of first document returned
     * @param limit      Number of documents requested per page
     */
    public PublishedIndexResponse(List<Document> docs, int totalCount, int offset, int limit) {
        items = docs.stream().map(d -> new Item(d.getUri())).collect(Collectors.toList());
        count = docs.size();
        this.totalCount = totalCount;
        this.offset = offset;
        this.limit = limit;
    }

    /**
     * Sub-object representing an individual document returned by the PublishedIndex
     */
    public class Item {
        private String uri;

        public Item(String uri) {
            this.uri = uri;
        }
    }
}
