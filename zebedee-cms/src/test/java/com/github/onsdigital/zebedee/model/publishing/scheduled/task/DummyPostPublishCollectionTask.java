package com.github.onsdigital.zebedee.model.publishing.scheduled.task;

import com.github.davidcarboni.cryptolite.Random;
import com.github.onsdigital.zebedee.util.Log;

import java.util.Date;

public class DummyPostPublishCollectionTask extends PostPublishCollectionTask {

    final String id = Random.id();
    private final int duration;
    private Date start;
    private Date end;


    public DummyPostPublishCollectionTask(int durationMillis) {
        super(null, null);
        this.duration = durationMillis;
    }

    /**
     * Dummy publish task which does nothing other than set that the publish is complete.
     */
    public DummyPostPublishCollectionTask() {
        super(null, null);
        this.duration = 0;
    }

    @Override
    public Boolean call() throws Exception {
        this.start = new Date();
        Log.print("Running dummy post-publish task with ID %s", id);

        Thread.sleep(duration);
        this.done = true;

        Log.print("Finished dummy post-publish task with ID %s", id);
        this.end = new Date();
        return true;
    }

    public String getId() {
        return id;
    }

    public Date getStart() {
        return start;
    }

    public Date getEnd() {
        return end;
    }
}