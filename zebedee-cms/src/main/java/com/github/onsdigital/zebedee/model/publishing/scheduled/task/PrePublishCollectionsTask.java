package com.github.onsdigital.zebedee.model.publishing.scheduled.task;

import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.json.ApprovalStatus;
import com.github.onsdigital.zebedee.json.CollectionType;
import com.github.onsdigital.zebedee.keyring.cache.SchedulerKeyCache;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.ZebedeeCollectionReader;
import com.github.onsdigital.zebedee.model.publishing.PostPublisher;
import com.github.onsdigital.zebedee.model.publishing.Publisher;
import com.github.onsdigital.zebedee.model.publishing.scheduled.PublishScheduler;
import com.github.onsdigital.zebedee.util.SlackNotification;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.onsdigital.logging.v2.event.SimpleEvent.error;
import static com.github.onsdigital.logging.v2.event.SimpleEvent.info;

/**
 * A scheduled task to run the pre-publish process for a number of collections.
 * <p>
 * Once this task is complete it will create a schedule the actual publish task.
 */
public class PrePublishCollectionsTask extends ScheduledTask {

    private final Set<String> collectionIds; // The list of collections ID's used in the task.
    private final Zebedee zebedee;
    private final SchedulerKeyCache schedulerKeyCache;
    private final Date publishDate; // the date of the actual publish, NOT the prepublish date associated with this task.
    private PublishScheduler publishScheduler;

    /**
     * Create a new instance of the PrePublishCollectionsTask.
     *
     * @param zebedee     The instance of Zebedee this task will run under.
     * @param publishDate The date the actual publish is scheduled for.
     */
    public PrePublishCollectionsTask(Zebedee zebedee, SchedulerKeyCache schedulerKeyCache, Date publishDate,
                                     PublishScheduler publishScheduler) {
        this.schedulerKeyCache = schedulerKeyCache;
        this.publishDate = publishDate;
        this.publishScheduler = publishScheduler;
        this.collectionIds = new HashSet<>();
        this.zebedee = zebedee;
    }

    /**
     * The run method is called at the time this task has been scheduled for.
     * <p>
     * Do everything possible before the publish so its ready to go with everything it needs.
     */
    @Override
    public void run() {

        long startTime = System.currentTimeMillis();
        info().log("PRE-PUBLISH: Starting Pre-publish process.");

        // load collections into memory
        Set<Collection> collections = loadCollections(this, zebedee);

        // create a publish task for each collection ready to publish.
        List<PublishCollectionTask> collectionPublishTasks = createCollectionPublishTasks(collections);

        // create a post-publish task for each collection
        List<PostPublishCollectionTask> postPublishCollectionTasks = createCollectionPostPublishTasks(collectionPublishTasks, zebedee);

        info().data("publishDate", publishDate).log("PRE-PUBLISH: Scheduling publish task");
        publishScheduler.schedulePublish(collectionPublishTasks, postPublishCollectionTasks, publishDate);

        info().data("totalProcessTime", (System.currentTimeMillis() - startTime))
                .log("PRE-PUBLISH: Finished Pre-publish");
    }

    /**
     * Load all of the collection objects for each collection to be published in this task.
     * <p>
     * Loading them into memory in the pre-publish step takes the overhead off of the publish process
     *
     * @return
     */
    public static Set<Collection> loadCollections(PrePublishCollectionsTask task, Zebedee zebedee) {
        Set<Collection> collections = new HashSet<>();

        info().log("PRE-PUBLISH: Loading collections into memory");

        task.getCollectionIds().forEach(collectionId -> {

            info().data("collectionId", collectionId).log("PRE-PUBLISH: Loading collection job");
            try {
                Collection collection = zebedee.getCollections().getCollection(collectionId);

                if (collection.getDescription().getApprovalStatus() != ApprovalStatus.COMPLETE) {
                    info().log("Scheduled collection has not been approved - switching to manual");

                    // Switch to manual
                    collection.getDescription().setType(CollectionType.manual);
                    // TODO Alarm message
                    collection.save();

                } else {
                    collections.add(collection);
                }
            } catch (Exception e) {
                error().data("collectionId", collectionId).logException(e, "Exception publishing collection");
            }
        });
        return collections;
    }

    /**
     * Prepare a task object for each collection to be published.
     * <p>
     * Doing this in the pre-publish step trims time off of the publish process and ensures everything that is
     * required is ready in memory as soon as the publish starts.
     *
     * @param collections
     * @return
     */
    private List<PublishCollectionTask> createCollectionPublishTasks(Set<Collection> collections) {
        List<PublishCollectionTask> collectionPublishTasks = new ArrayList<>(collections.size());
        List<Future<Boolean>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(collections.size()); // thread per collection

        // create a publish task for each collection that will publish the content to the website.
        // creating the individual collection publish tasks here to do all the work ahead of the actual publish.
        try {
            for (Collection collection : collections) {
                futures.add(pool.submit(() -> {
                    try {

                        info().data("collectionId", collection.getDescription().getId())
                                .log("PRE-PUBLISH: creating collection publish task");

                        // FIXME using PostPublisher.getPublishedCollection feels a bit hacky
                        SlackNotification.publishNotification(PostPublisher.getPublishedCollection(collection), SlackNotification.CollectionStage.PRE_PUBLISH, SlackNotification.StageStatus.STARTED);

                        // begin the publish ahead of time. This creates the transaction on the train.
                        Map<String, String> hostToTransactionIdMap = Publisher.createPublishingTransactions(collection);

                        // send versioned files manifest ahead of time. allowing files to be copied from the website into the transaction.
                        Publisher.sendManifest(collection);

                        SecretKey key = schedulerKeyCache.get(collection.getDescription().getId());
                        ZebedeeCollectionReader collectionReader = new ZebedeeCollectionReader(collection, key);
                        PublishCollectionTask publishCollectionTask = new PublishCollectionTask(collection, collectionReader, hostToTransactionIdMap);

                        info().data("collectionId", collection.getDescription().getId())
                                .log("PRE-PUBLISH: Adding publish task");
                        collectionPublishTasks.add(publishCollectionTask);

                        // FIXME using PostPublisher.getPublishedCollection feels a bit hacky
                        SlackNotification.publishNotification(PostPublisher.getPublishedCollection(collection), SlackNotification.CollectionStage.PRE_PUBLISH, SlackNotification.StageStatus.COMPLETED);

                        return true;
                    } catch (BadRequestException | IOException | UnauthorizedException | NotFoundException e) {
                        // FIXME using PostPublisher.getPublishedCollection feels a bit hacky
                        // TODO pass through the error?
                        SlackNotification.publishNotification(PostPublisher.getPublishedCollection(collection), SlackNotification.CollectionStage.PRE_PUBLISH, SlackNotification.StageStatus.FAILED);

                        error().logException(e, "PRE-PUBLISH: error when creating collection publish task");
                        return false;
                    }
                }));

            }
        } finally {
            if (pool != null) pool.shutdown();
        }

        for (Future<Boolean> future : futures) {
            try {
                future.get().booleanValue();
            } catch (InterruptedException | ExecutionException e) {
                error().logException(e, "PRE-PUBLISH: unexpected submission error. An attempeted pre-publish has returned no result.");
            }
        }

        return collectionPublishTasks;
    }

    /**
     * Prepare a post publish task for each collection ahead of the publish.
     *
     * @param collectionPublishTasks
     * @param zebedee
     * @return
     */
    private List<PostPublishCollectionTask> createCollectionPostPublishTasks(List<PublishCollectionTask> collectionPublishTasks, Zebedee zebedee) {
        List<PostPublishCollectionTask> postPublishCollectionTasks = new ArrayList<>(collectionPublishTasks.size());

        collectionPublishTasks.forEach(publishTask -> {
            info().data("collectionId", publishTask.getCollection().getDescription().getId())
                    .log("PRE-PUBLISH: creating collection post-publish task");
            PostPublishCollectionTask postPublishCollectionTask = new PostPublishCollectionTask(zebedee, publishTask);
            postPublishCollectionTasks.add(postPublishCollectionTask);
        });

        return postPublishCollectionTasks;
    }

    /**
     * Add a collection to this task.
     *
     * @param collection
     */
    public void addCollection(Collection collection) {
        collectionIds.add(collection.getDescription().getId());
    }

    /**
     * Remove a collection from this task.
     *
     * @param collection
     */
    public void removeCollection(Collection collection) {
        collectionIds.remove(collection.getDescription().getId());
    }

    /**
     * Get a list of current collection ids that cannot be changed
     *
     * @return
     */
    public Set<String> getCollectionIds() {
        return Collections.unmodifiableSet(collectionIds);
    }

    public Date getPublishDate() {
        return publishDate;
    }
}
