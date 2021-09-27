package com.github.onsdigital.zebedee.keyring.migration;

import com.github.onsdigital.slack.messages.PostMessage;
import com.github.onsdigital.slack.messages.PostMessageAttachment;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.Event;
import com.github.onsdigital.zebedee.json.EventType;
import com.github.onsdigital.zebedee.keyring.CollectionKeyCache;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.Collections;
import com.github.onsdigital.zebedee.permissions.service.PermissionsService;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.util.slack.Notifier;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.onsdigital.slack.messages.Colour.DANGER;
import static com.github.onsdigital.zebedee.configuration.Configuration.getDefaultSlackAlarmChannel;
import static com.github.onsdigital.zebedee.configuration.Configuration.getSlackUsername;
import static com.github.onsdigital.zebedee.logging.CMSLogEvent.error;
import static com.github.onsdigital.zebedee.logging.CMSLogEvent.warn;

/**
 * Run a health check that verifies an Admin user's collection keyring contains all collection keys.
 * If any collection keys are missing a Slack notification is sent with details of the affected collections. This is
 * a temp class to help identify issues with the new keyring migration work. It will be deleted once the migration is
 * fully completed.
 */
public class KeyringHealthCheckerImpl implements KeyringHealthChecker {

    static final String MESSAGE = "Keyring health check failure: Identified 1 or more collection keys missing from " +
            "an Admin collection keyring.\n\n" +
            ":warning: It is *strongly* recommended this is investigated to rule out any issues with the" +
            " new keyring migration functionality. :warning:\n";

    private PermissionsService permissionsService;
    private Collections collections;
    private CollectionKeyCache keyCache;
    private Notifier slackNotifier;

    /**
     * Construct a new KeyringHealthCheckerImpl instance.
     *
     * @param permissionsService the {@link PermissionsService} to check the user permissions.
     * @param collections        the {@link Collections} service to list the collections.
     * @param keyCache           {@link CollectionKeyCache} to get the user keys from.
     * @param notifier           a {@link Notifier} to send notification in the event of a invalid keyring.
     */
    public KeyringHealthCheckerImpl(PermissionsService permissionsService, Collections collections,
                                    CollectionKeyCache keyCache, Notifier notifier) {
        this.permissionsService = permissionsService;
        this.collections = collections;
        this.keyCache = keyCache;
        this.slackNotifier = notifier;
    }

    @Override
    public void check(Session session) {
        try {
            runHealthCheck(session);
        } catch (Exception ex) {
            // Any errors should be handled quietly so catch and swallow the error and log it out.
            warn().exception(ex).user(session).log("unexpected error running user keyring healthcheck");
        }
    }

    private void runHealthCheck(Session session) {
        if (!isValidSession(session)) {
            return;
        }

        if (!hasAdminPermissions(session)) {
            return;
        }

        Collections.CollectionList list = listAllCollections(session);
        if (list == null || list.isEmpty()) {
            return;
        }

        List<Collection> absent = getCollectionsAbsentInSchedulerKeyCache(list);
        if (absent == null || absent.isEmpty()) {
            return;
        }

        List<PostMessageAttachment> attachments = createMsgAttachments(absent, session);
        PostMessage msg = createSlackMessage(attachments);

        sendSlackAlert(msg);
    }

    private boolean isValidSession(Session session) {
        if (session == null) {
            warn().log("unable to health check user keyring as the provided session was null");
            return false;
        }

        if (StringUtils.isEmpty(session.getEmail())) {
            warn().log("unable to health check user keyring as the provided session was null");
            return false;
        }

        return true;
    }

    private boolean hasAdminPermissions(Session session) {
        boolean isAdmin = false;
        try {
            isAdmin = permissionsService.isAdministrator(session);
        } catch (Exception ex) {
            warn().exception(ex)
                    .user(session)
                    .log("unexpected error while checking user permissions, aborting keyring health check");
        }
        return isAdmin;
    }

    private Collections.CollectionList listAllCollections(Session session) {
        Collections.CollectionList list = null;
        try {
            list = collections.list();
        } catch (IOException ex) {
            warn().exception(ex)
                    .user(session)
                    .log("unable to health check user keyring unexpected error listing collections");
        }
        return list;
    }

    private List<Collection> getCollectionsAbsentInSchedulerKeyCache(Collections.CollectionList list) {
        List<Collection> absent = new ArrayList<>();

        final Set<String> scheduledKeys = getSchedulerKeys();
        if (scheduledKeys == null) {
            return absent;
        }

        absent = list.stream()
                .filter(c -> !scheduledKeys.contains(c.getId()))
                .collect(Collectors.toList());

        return absent;
    }

    private Set<String> getSchedulerKeys() {
        Set<String> scheduledKeys = new HashSet<>();
        try {
            scheduledKeys = keyCache.list();
        } catch (Exception ex) {
            warn().exception(ex)
                    .log("unable to health check user keyring unexpected error listing scheduler key cache");
        }

        return scheduledKeys;
    }

    private List<PostMessageAttachment> createMsgAttachments(List<Collection> missing, Session session) {
        List<PostMessageAttachment> attachments = new ArrayList<>();

        for (Collection c : missing) {
            attachments.add(createMsgAttatchment(c, session));
        }

        return attachments;
    }

    private PostMessageAttachment createMsgAttatchment(Collection c, Session session) {
        if (c == null) {
            throw new IllegalArgumentException("collection expected but was null");
        }

        if (c.getDescription() == null) {
            throw new IllegalArgumentException("collection.description expected but was null");
        }

        CollectionDescription desc = c.getDescription();

        PostMessageAttachment attachment = new PostMessageAttachment("Collection ID", c.getId(), DANGER)
                .addField("Collection Name",desc.getName(), false)
                .addField("Missing from", session.getEmail(), true);

        if (desc.getType() != null) {
            attachment.addField("Publish Type", desc.getType().name(), true);
        }

        Optional<Event> createdEvent = getCollectionCreatedEvent(desc);

        if (createdEvent.isPresent() && createdEvent.get().getDate() != null) {
            attachment.addField("Creation Date", createdEvent.get().getDate().toString(), true);
        }

        if (createdEvent.isPresent() && StringUtils.isNoneEmpty(createdEvent.get().getEmail())) {
            attachment.addField("Created By", createdEvent.get().getEmail(), true);
        }

        return attachment;
    }

    private Optional<Event> getCollectionCreatedEvent(CollectionDescription desc) {
        if (desc.getEvents() == null) {
            return Optional.empty();
        }

        return desc.getEvents()
                .stream()
                .filter(e -> EventType.CREATED.equals(e.type))
                .findFirst();
    }


    private PostMessage createSlackMessage(List<PostMessageAttachment> attachments) {
        PostMessage msg = new PostMessage(getSlackUsername(), getDefaultSlackAlarmChannel(), ":flo:", MESSAGE);

        msg.getAttachments().addAll(attachments);
        return msg;
    }

    private void sendSlackAlert(PostMessage msg) {
        try {
            slackNotifier.sendSlackMessage(msg);
        } catch (Exception ex) {
            warn().exception(ex)
                    .log("unexpected error sending keying health check slack alert message");
        }
    }

}
