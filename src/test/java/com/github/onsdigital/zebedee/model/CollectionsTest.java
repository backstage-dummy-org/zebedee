package com.github.onsdigital.zebedee.model;

import com.github.onsdigital.zebedee.Builder;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CollectionsTest {

    Zebedee zebedee;
    Builder builder;

    @Before
    public void setUp() throws Exception {
        builder = new Builder(this.getClass());
        zebedee = new Zebedee(builder.zebedee);
    }

    @After
    public void tearDown() throws Exception {
        builder.delete();
    }

    @Test
    public void shouldFindCollection() throws IOException {
        Collections collections = new Collections();

        Collection firstCollection = Collection.create(new CollectionDescription("FirstCollection"), zebedee);
        Collection secondCollection = Collection.create(new CollectionDescription("SecondCollection"), zebedee);

        collections.add(firstCollection);
        collections.add(secondCollection);

        Collection firstCollectionFound = collections.getCollection(firstCollection.description.id);
        Collection secondCollectionFound = collections.getCollection(secondCollection.description.id);

        assertEquals(firstCollection.description.id, firstCollectionFound.description.id);
        assertEquals(firstCollection.description.name, firstCollectionFound.description.name);
        assertEquals(secondCollection.description.id, secondCollectionFound.description.id);
        assertEquals(secondCollection.description.name, secondCollectionFound.description.name);
    }

    @Test
    public void shouldReturnNullIfNotFound() throws IOException {

        Collections collections = new Collections();

        Collection firstCollection = Collection.create(new CollectionDescription("FirstCollection"), zebedee);

        collections.add(firstCollection);

        assertNull(collections.getCollection("SecondCollection"));
    }
}
