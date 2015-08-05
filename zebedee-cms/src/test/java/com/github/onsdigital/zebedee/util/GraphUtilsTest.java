package com.github.onsdigital.zebedee.util;

import com.github.davidcarboni.ResourceUtils;
import com.github.onsdigital.content.link.PageReference;
import com.github.onsdigital.content.page.statistics.dataset.Dataset;
import com.github.onsdigital.content.page.statistics.document.article.Article;
import com.github.onsdigital.content.page.statistics.document.bulletin.Bulletin;
import com.github.onsdigital.content.page.taxonomy.ProductPage;
import com.github.onsdigital.content.page.taxonomy.TaxonomyLandingPage;
import com.github.onsdigital.zebedee.Builder;
import com.github.onsdigital.zebedee.Zebedee;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by thomasridd on 16/07/15.
 */
public class GraphUtilsTest {

    @Test
    public void graphUtilsShouldIdentifyRelatedLinksOnABulletin() {
        // Given
        // a bulletin with a bunch of links to various media
        Bulletin bulletin = new Bulletin();

        List<PageReference> references = new ArrayList<>();
        references.add(new PageReference(URI.create("relatedbulletin1")));
        references.add(new PageReference(URI.create("relatedbulletin2")));
        bulletin.setRelatedBulletins(references);

        references = new ArrayList<>();
        references.add(new PageReference(URI.create("relateddataset1")));
        references.add(new PageReference(URI.create("relateddataset2")));
        bulletin.setRelatedData(references);

        // When
        // we get the related list
        List<String> relatedUris = GraphUtils.relatedUris(bulletin);
        Collections.sort(relatedUris);

        // Then
        // we expect the links above
        List<String> expected = Arrays.asList("relatedbulletin1", "relatedbulletin2", "relateddataset1", "relateddataset2");
        assertArrayEquals(expected.toArray(), relatedUris.toArray());
    }

    @Test
    public void graphUtilsShouldIdentifyRelatedLinksOnADataset() {
        // Given
        // a dataset with a bunch of links to various media
        Dataset dataset = new Dataset();

        List<PageReference> references = new ArrayList<>();
        references.add(new PageReference(URI.create("relatedarticle1")));
        references.add(new PageReference(URI.create("relatedbulletin1")));
        dataset.setRelatedDocuments(references);

        references = new ArrayList<>();
        references.add(new PageReference(URI.create("relateddataset1")));
        references.add(new PageReference(URI.create("relateddataset2")));
        dataset.setRelatedDatasets(references);

        // When
        // we get the related list
        List<String> relatedUris = GraphUtils.relatedUris(dataset);
        Collections.sort(relatedUris);

        // Then
        // we expect the links above repeated
        List<String> expected = Arrays.asList("relatedarticle1", "relatedbulletin1", "relateddataset1", "relateddataset2");
        assertArrayEquals(expected.toArray(), relatedUris.toArray());
    }

    @Test
    public void graphUtilsShouldIdentifyRelatedLinksOnAnArticle() {
        // Given
        // a dataset with a bunch of links to various media
        Article article = new Article();

        List<PageReference> references = new ArrayList<>();
        references.add(new PageReference(URI.create("relatedarticle1")));
        references.add(new PageReference(URI.create("relatedarticle2")));
        article.setRelatedArticles(references);

        references = new ArrayList<>();
        references.add(new PageReference(URI.create("relateddataset1")));
        references.add(new PageReference(URI.create("relateddataset2")));
        article.setRelatedData(references);

        // When
        // we get the related list
        List<String> relatedUris = GraphUtils.relatedUris(article);
        Collections.sort(relatedUris);

        // Then
        // we expect the links above repeated
        List<String> expected = Arrays.asList("relatedarticle1", "relatedarticle2", "relateddataset1", "relateddataset2");
        assertArrayEquals(expected.toArray(), relatedUris.toArray());
    }

    @Test
    public void graphUtilsShouldIdentifyRelatedLinksOnAProductPage() {
        // Given
        // a dataset with a bunch of links to various media
        ProductPage productPage = new ProductPage();

        List<PageReference> references = new ArrayList<>();
        references.add(new PageReference(URI.create("relatedarticle1")));
        references.add(new PageReference(URI.create("relatedarticle2")));
        productPage.setRelatedArticles(references);

        references = new ArrayList<>();
        references.add(new PageReference(URI.create("relatedbulletin1")));
        references.add(new PageReference(URI.create("relatedbulletin2")));
        productPage.setStatsBulletins(references);

        references = new ArrayList<>();
        references.add(new PageReference(URI.create("relateddataset1")));
        references.add(new PageReference(URI.create("relateddataset2")));
        productPage.setDatasets(references);

        references = new ArrayList<>();
        references.add(new PageReference(URI.create("relatedtimeseries1")));
        references.add(new PageReference(URI.create("relatedtimeseries2")));
        productPage.setItems(references);

        // When
        // we get the related list
        List<String> relatedUris = GraphUtils.relatedUris(productPage);
        Collections.sort(relatedUris);

        // Then
        // we expect the links above repeated
        List<String> expected = Arrays.asList("relatedarticle1", "relatedarticle2",
                "relatedbulletin1", "relatedbulletin2",
                "relateddataset1", "relateddataset2", "relatedtimeseries1", "relatedtimeseries2");
        assertArrayEquals(expected.toArray(), relatedUris.toArray());
    }

    @Test
    public void graphUtilsShouldIdentifyRelatedLinksOnALandingPage() {
        // Given
        // a dataset with a bunch of links to various media
        TaxonomyLandingPage landingPage = new TaxonomyLandingPage();

        List<PageReference> references = new ArrayList<>();
        references.add(new PageReference(URI.create("section1")));
        references.add(new PageReference(URI.create("section2")));
        landingPage.setSections(references);

        // When
        // we get the related list
        List<String> relatedUris = GraphUtils.relatedUris(landingPage);
        Collections.sort(relatedUris);

        // Then
        // we expect the links above repeated
        List<String> expected = Arrays.asList("section1", "section2");
        assertArrayEquals(expected.toArray(), relatedUris.toArray());
    }

    @Test
    public void graphUtilsShouldIdentifyProductPage() throws IOException {
        // With
        // The Basic zebedee setup
        Builder bob = new Builder(GraphUtils.class, ResourceUtils.getPath("/bootstraps/basic"));
        Zebedee zebedee = new Zebedee(bob.zebedee);


        // When
        // We identify the product page for
        String uri = "/themea/landinga/producta/bulletins/bulletina/2015-01-01";
        ProductPage page = GraphUtils.productPageForPageWithURI(zebedee.launchpad, uri);

        // Then
        // we expect
        String pageUri = "/themea/landinga/producta";
        assertEquals(pageUri, page.getUri().toString());

    }
}