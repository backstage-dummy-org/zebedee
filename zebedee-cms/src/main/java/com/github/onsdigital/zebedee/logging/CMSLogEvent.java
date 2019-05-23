package com.github.onsdigital.zebedee.logging;

import com.github.onsdigital.logging.v2.DPLogger;
import com.github.onsdigital.logging.v2.event.BaseEvent;
import com.github.onsdigital.logging.v2.event.Severity;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.session.model.Session;
import org.apache.commons.lang3.StringUtils;

import static com.github.onsdigital.logging.v2.DPLogger.logConfig;

public class CMSLogEvent extends BaseEvent<CMSLogEvent> {

    public static CMSLogEvent warn() {
        return new CMSLogEvent(logConfig().getNamespace(), Severity.WARN);
    }

    public static CMSLogEvent info() {
        return new CMSLogEvent(logConfig().getNamespace(), Severity.INFO);
    }

    public static CMSLogEvent error() {
        return new CMSLogEvent(logConfig().getNamespace(), Severity.ERROR);
    }

    private CMSLogEvent(String namespace, Severity severity) {
        super(namespace, severity, DPLogger.logConfig().getLogStore());
    }

    public CMSLogEvent user(Session session) {
        if (session != null) {
            user(session.getEmail());
        }
        return this;
    }

    public CMSLogEvent sessionID(Session session) {
        if (session != null) {
            sessionID(session.getId());
        }
        return this;
    }

    public CMSLogEvent sessionID(String sessionID) {
        if (StringUtils.isNotEmpty(sessionID)) {
            data("session_id", sessionID);
        }
        return this;
    }

    public CMSLogEvent user(String email) {
        if (StringUtils.isNotEmpty(email)) {
            data("user", email);
        }
        return this;
    }

    public CMSLogEvent collectionID(CollectionDescription desc) {
        if (desc != null) {
            collectionID(desc.getId());
        }
        return this;
    }

    public CMSLogEvent collectionID(Collection collection) {
        if (collection != null && collection.getDescription() != null) {
            collectionID(collection.getDescription().getId());
        }
        return this;
    }

    public CMSLogEvent collectionID(String collectionID) {
        if (StringUtils.isNotEmpty(collectionID)) {
            data("collection_id", collectionID);
        }
        return this;
    }

    public CMSLogEvent datasetID(String datasetID) {
        if (StringUtils.isNotEmpty(datasetID)) {
            data("dataset_id", datasetID);
        }
        return this;
    }

    public CMSLogEvent serviceAccountID(String serviceAccountID) {
        if (StringUtils.isNotEmpty(serviceAccountID)) {
            data("service_account_id", serviceAccountID);
        }
        return this;
    }

    public CMSLogEvent serviceAccountToken(String serviceAccountToken) {
        if (StringUtils.isNotEmpty(serviceAccountToken)) {
            data("service_account_token", serviceAccountToken);
        }
        return this;
    }
}