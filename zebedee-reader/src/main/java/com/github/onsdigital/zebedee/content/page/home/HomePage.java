package com.github.onsdigital.zebedee.content.page.home;

import com.github.onsdigital.zebedee.content.page.base.PageType;
import com.github.onsdigital.zebedee.content.page.taxonomy.base.TaxonomyNode;
import com.github.onsdigital.zebedee.content.partial.markdown.MarkdownSection;
import com.github.onsdigital.zebedee.content.util.ContentConstants;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by bren on 04/06/15.
 * <p>
 * Object mapping for homepage of the website
 * <p>
 * HomePage is considered as the root of Taxonomy. It also contains links and references to non-statistics contents (Methodology, Release, About Us pages , etc.)
 */
public class HomePage extends TaxonomyNode {

    private List<HomeContentItem> featuredContent;

    private String serviceMessage;

    public HomePage() {
        this.featuredContent = new ArrayList<HomeContentItem>();
    }

    @Override
    public PageType getType() {
        return PageType.home_page;
    }

    public List<HomeContentItem> getFeaturedContent() {
        return featuredContent;
    }

    public void etFeaturedContent(List<HomeContentItem> featuredContent) {
        this.featuredContent = featuredContent;
    }

    public String getServiceMessage() {
        return serviceMessage;
    }

    public void setServiceMessage(String serviceMessage) {
        this.serviceMessage = serviceMessage;
    }
}
