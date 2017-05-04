package com.github.onsdigital.zebedee.model.publishing.scheduled;

import com.github.onsdigital.zebedee.Builder;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.ApprovalStatus;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.CollectionType;
import com.github.onsdigital.zebedee.json.Session;
import com.github.onsdigital.zebedee.model.Collection;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Ignore("IGNORE: user keys concurrency defect")
public class PublishSchedulerTest {

    private Builder builder;
    private Zebedee zebedee;
    private Session session;
    private PublishScheduler scheduler;

    @Before
    public void setUp() throws Exception {
        builder = new Builder();
        zebedee = new Zebedee(builder.zebedee);
        session = zebedee.openSession(builder.publisher1Credentials);
        scheduler = new PublishScheduler();
    }

    @After
    public void tearDown() throws Exception {
        builder.delete();
    }

    @Test
    public void scheduledPublish() throws IOException, ZebedeeException, InterruptedException {

        // Given a scheduled collection
        CollectionDescription description = new CollectionDescription("collectionName");
        description.type = CollectionType.scheduled;
        description.approvalStatus = ApprovalStatus.COMPLETE;
        description.publishDate = DateTime.now().plusSeconds(2000).toDate();
        Collection collection = Collection.create(description, zebedee, session);
        Date startDate = description.publishDate;
        Date prePublishStartDate = new DateTime(description.publishDate).minusSeconds(1).toDate();

        // When the collection is scheduled for pre-publish
        scheduler.schedulePrePublish(collection, zebedee, prePublishStartDate, startDate);

        // Then the scheduled date should match the date it was given to be scheduled for.
        List<ScheduledPublishTaskData> prePublishTaskData = scheduler.getPrePublishTaskData(zebedee);

        Assert.assertNotNull(prePublishTaskData);
        ScheduledPublishTaskData taskData = prePublishTaskData.get(0);
        Assert.assertNotNull(taskData);
        Assert.assertTrue(taskData.collectionIds.contains(description.id));

        // check scheduled time is as expected within 50ms tolerance
        Assert.assertEquals(prePublishStartDate.getTime() / 100, taskData.scheduledPublishDate.getTime() / 100);

        // cleanup
        scheduler.cancel(collection);
    }

    @Test
    public void cancelPublishShouldRemoveCollection() throws IOException, ZebedeeException, InterruptedException {

        // Given a scheduled collection
        CollectionDescription description = new CollectionDescription("collectionName");
        description.type = CollectionType.scheduled;
        description.approvalStatus = ApprovalStatus.COMPLETE;
        description.publishDate = DateTime.now().plusSeconds(2000).toDate();
        Collection collection = Collection.create(description, zebedee, session);
        Date startDate = description.publishDate;
        Date prePublishStartDate = new DateTime(description.publishDate).minusSeconds(1).toDate();
        scheduler.schedulePrePublish(collection, zebedee, prePublishStartDate, startDate);

        // When we cancel the scheduled collection publish
        scheduler.cancel(collection);

        // Then the collection task is no longer queued.
        List<ScheduledPublishTaskData> prePublishTaskData = scheduler.getPrePublishTaskData(zebedee);
        Assert.assertNotNull(prePublishTaskData);
        ScheduledPublishTaskData taskData = prePublishTaskData.get(0);
        Assert.assertNotNull(taskData);
        Assert.assertFalse(taskData.collectionIds.contains(description.id));
    }

    @Test
    public void deletedCollectionShouldCancel() throws IOException, ZebedeeException, InterruptedException {

        // Given a scheduled collection that has been deleted.
        CollectionDescription description = new CollectionDescription("collectionName");
        description.type = CollectionType.scheduled;
        description.approvalStatus = ApprovalStatus.COMPLETE;
        description.publishDate = DateTime.now().plusSeconds(2000).toDate();
        Collection collection = Collection.create(description, zebedee, session);
        Date startDate = description.publishDate;
        Date prePublishStartDate = new DateTime(description.publishDate).minusSeconds(1).toDate();
        scheduler.schedulePrePublish(collection, zebedee, prePublishStartDate, startDate);
        zebedee.getCollections().delete(collection, session);

        // When the collection is cancelled from the schuduler.
        scheduler.cancel(collection);

        // Then the collection task is no longer queued.
        List<ScheduledPublishTaskData> prePublishTaskData = scheduler.getPrePublishTaskData(zebedee);
        Assert.assertNotNull(prePublishTaskData);
        ScheduledPublishTaskData taskData = prePublishTaskData.get(0);
        Assert.assertNotNull(taskData);
        Assert.assertFalse(taskData.collectionIds.contains(description.id));
    }

    @Test
    public void deletedCollectionShouldNotThrowExceptionOnLoadCollections() throws IOException, ZebedeeException, InterruptedException {

        // Given a scheduled collection
        CollectionDescription description = new CollectionDescription("collectionName");
        description.type = CollectionType.scheduled;
        description.approvalStatus = ApprovalStatus.COMPLETE;
        description.publishDate = DateTime.now().plusSeconds(2000).toDate();
        Collection collection = Collection.create(description, zebedee, session);

        Date startDate = description.publishDate;
        Date prePublishStartDate = new DateTime(description.publishDate).minusSeconds(1).toDate();
        scheduler.schedulePrePublish(collection, zebedee, prePublishStartDate, startDate);

        // When the collection is deleted but not removed from the scheduler.
        zebedee.getCollections().delete(collection, session);

        // Then getting collection data does not throw any exceptions.
        List<ScheduledPublishTaskData> prePublishTaskData = scheduler.getPrePublishTaskData(zebedee);
        Assert.assertNotNull(prePublishTaskData);
        ScheduledPublishTaskData taskData = prePublishTaskData.get(0);
        Assert.assertNotNull(taskData);
    }

    @Test
    public void deletedCollectionShouldRemoveCollectionIfOtherCollectionsAreScheduledFirst() throws IOException, ZebedeeException, InterruptedException {

        // Given two scheduled collections
        CollectionDescription description = new CollectionDescription("collectionName");
        description.type = CollectionType.scheduled;
        description.approvalStatus = ApprovalStatus.COMPLETE;
        description.publishDate = DateTime.now().plusSeconds(2000).toDate();
        Collection collection = Collection.create(description, zebedee, session);
        Date startDate = description.publishDate;
        Date prePublishStartDate = new DateTime(description.publishDate).minusSeconds(1).toDate();
        scheduler.schedulePrePublish(collection, zebedee, prePublishStartDate, startDate);

        CollectionDescription description2 = new CollectionDescription("collection2");
        description2.type = CollectionType.scheduled;
        description.approvalStatus = ApprovalStatus.COMPLETE;
        description2.publishDate = DateTime.now().plusSeconds(3000).toDate();
        Collection collection2 = Collection.create(description2, zebedee, session);
        Date startDate2 = description2.publishDate;
        Date prePublishStartDate2 = new DateTime(description2.publishDate).minusSeconds(1).toDate();
        scheduler.schedulePrePublish(collection2, zebedee, prePublishStartDate2, startDate2);

        // When the second collection is cancelled.
        scheduler.cancel(collection2);

        // Then it is removed from the scheduler.
        List<ScheduledPublishTaskData> prePublishTaskData = scheduler.getPrePublishTaskData(zebedee);
        Assert.assertNotNull(prePublishTaskData);
        ScheduledPublishTaskData taskData = prePublishTaskData.get(1);
        Assert.assertNotNull(taskData);
        Assert.assertFalse(taskData.collectionIds.contains(description2.id));
    }
}
