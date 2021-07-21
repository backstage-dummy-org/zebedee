package com.github.onsdigital.zebedee.keyring;

import com.github.onsdigital.zebedee.api.Root;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.keyring.cache.KeyringCache;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.Collections;
import com.github.onsdigital.zebedee.permissions.service.PermissionsService;
import com.github.onsdigital.zebedee.user.model.User;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * CentralKeyringImpl adds a permissions check wrapper around a {@link KeyringCache} instance to ensure only
 * authorised users can access collection encryption keys
 */
public class CentralKeyringImpl implements Keyring {

    static final String USER_NULL_ERR = "user required but was null";
    static final String USER_EMAIL_ERR = "user email required but was null or empty";
    static final String USER_KEYRING_NULL_ERR = "user keyring required but was null";
    static final String USER_KEYRING_LOCKED_ERR = "error user keyring is locked";
    static final String NOT_INITIALISED_ERR = "CollectionKeyring accessed but not yet initialised";
    static final String KEYRING_CACHE_NULL_ERR = "keyringCache required but was null";
    static final String COLLECTION_NULL_ERR = "collection required but was null";
    static final String COLLECTION_DESCRIPTION_NULL_ERR = "collection description required but is null";
    static final String COLLECTION_ID_NULL_OR_EMPTY_ERR = "collection ID required but was null or empty";
    static final String PERMISSION_SERVICE_NULL_ERR = "permissionsService required but was null";
    static final String SECRET_KEY_NULL_ERR = "secret key required but was null";

    /**
     * Singleton instance.
     */
    private static Keyring INSTANCE = null;

    private final KeyringCache cache;
    private final PermissionsService permissionsService;

    /**
     * CollectionKeyringImpl is a singleton instance. Use {@link CentralKeyringImpl#init(KeyringCache, PermissionsService)} to
     * construct and initialise a new instance. Use {@link CentralKeyringImpl#getInstance()} to accessed the
     * singleton.
     *
     * @param cache the {@link KeyringCache} instance to use.
     * @throws KeyringException the {@link KeyringCache} was null.
     */
    private CentralKeyringImpl(KeyringCache cache, PermissionsService permissionsService) throws KeyringException {
        if (cache == null) {
            throw new KeyringException(KEYRING_CACHE_NULL_ERR);
        }

        if (permissionsService == null) {
            throw new KeyringException(PERMISSION_SERVICE_NULL_ERR);
        }

        this.permissionsService = permissionsService;
        this.cache = cache;
    }

    @Override
    public void cacheKeyring(User user) throws KeyringException {
        validateUser(user);

        if (user.keyring() == null) {
            throw new KeyringException(USER_KEYRING_NULL_ERR);
        }

        if (!user.keyring().isUnlocked()) {
            throw new KeyringException(USER_KEYRING_LOCKED_ERR);
        }

        if (!user.keyring().list().isEmpty()) {
            for (String collectionID : user.keyring().list()) {
                cache.add(collectionID, user.keyring().get(collectionID));
            }
        }
    }


    @Override
    public SecretKey get(User user, Collection collection) throws KeyringException {
        validateUser(user);
        validateCollection(collection);

        boolean hasPermission = hasEditPermissions(user);

        if (!hasPermission) {
            return null;
        }

        return cache.get(collection.getDescription().getId());
    }

    @Override
    public void remove(User user, Collection collection) throws KeyringException {
        validateUser(user);
        validateCollection(collection);

        boolean hasPermission = hasEditPermissions(user);

        if (!hasPermission) {
            return;
        }

        cache.remove(collection.getDescription().getId());
    }

    @Override
    public void add(User user, Collection collection, SecretKey key) throws KeyringException {
        validateUser(user);
        validateCollection(collection);
        validateKey(key);

        boolean hasPermission = hasEditPermissions(user);

        if (!hasPermission) {
            return;
        }

        cache.add(collection.getDescription().getId(), key);
    }


    @Override
    public Set<String> list(User user) throws KeyringException {
        validateUser(user);

        if (hasEditPermissions(user)) {
            return cache.list();
        }

        Collections.CollectionList collectionList = null;
        try {
           collectionList = Root.zebedee.getCollections().list();
        } catch (Exception ex) {
            throw new KeyringException(ex);
        }

        Set<String> results = new HashSet<>();

        for (Collection collection : collectionList) {
            try {
                List<User> users = permissionsService.getCollectionAccessMapping(collection);
                Optional<User> result = users
                        .stream()
                        .filter(u -> u.getEmail().equals(user.getEmail()))
                        .findFirst();

                if (result.isPresent()) {
                    results.add(collection.getId());
                }
            } catch (Exception ex) {
                throw new KeyringException(ex);
            }
        }

        return results;
    }

    /**
     * <b>Do nothing.</b>
     * <p>This method is defined in order to maintain backwards compatability. This functionality is not
     * This method will be removed once we have fully migrated to the central keyring implementation.
     *
     * @param user     the user the keyring belongs to.
     * @param password the user's password.
     * @throws KeyringException thrown if there is a problem unlocking the keyring.
     */
    @Override
    public void unlock(User user, String password) throws KeyringException {
        // This method is defined in order to maintain backwards compatability. This functionality is not required in
        // the new central keyring design so when called we do nothing.
    }

    @Override
    public void assignTo(User src, User target, List<CollectionDescription> assignments) throws KeyringException {
        //Do nothing - required to maintain backwards compatability.
    }

    @Override
    public void assignTo(User src, User target, CollectionDescription... assignments) throws KeyringException {
        //Do nothing - required to maintain backwards compatability.
    }

    @Override
    public void revokeFrom(User target, List<CollectionDescription> removals) throws KeyringException {
        //Do nothing - required to maintain backwards compatability.
    }

    @Override
    public void revokeFrom(User target, CollectionDescription... removals) throws KeyringException {
        //Do nothing - required to maintain backwards compatability.
    }

    private void validateUser(User user) throws KeyringException {
        if (user == null) {
            throw new KeyringException(USER_NULL_ERR);
        }

        if (StringUtils.isEmpty(user.getEmail())) {
            throw new KeyringException(USER_EMAIL_ERR);
        }
    }

    private void validateKey(SecretKey key) throws KeyringException {
        if (key == null) {
            throw new KeyringException(SECRET_KEY_NULL_ERR);
        }
    }


    private void validateCollection(Collection collection) throws KeyringException {
        if (collection == null) {
            throw new KeyringException(COLLECTION_NULL_ERR);
        }

        if (collection.getDescription() == null) {
            throw new KeyringException(COLLECTION_DESCRIPTION_NULL_ERR);
        }

        if (StringUtils.isEmpty(collection.getDescription().getId())) {
            throw new KeyringException(COLLECTION_ID_NULL_OR_EMPTY_ERR);
        }
    }

    private boolean hasEditPermissions(User user) throws KeyringException {
        try {
            return permissionsService.isAdministrator(user.getEmail()) || permissionsService.canEdit(user.getEmail());
        } catch (IOException ex) {
            throw new KeyringException(ex);
        }
    }

    /**
     * Initailise the CollectionKeyring.
     *
     * @param keyringCache the {@link KeyringCache} instance to use.
     * @throws KeyringException failed to initialise instance.
     */
    public static void init(KeyringCache keyringCache, PermissionsService permissionsService) throws KeyringException {
        if (INSTANCE == null) {
            synchronized (CentralKeyringImpl.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CentralKeyringImpl(keyringCache, permissionsService);
                }
            }
        }
    }

    /**
     * @return a singleton instance of the CollectionKeyring
     * @throws KeyringException CollectionKeyring has not been initalised before being accessed.
     */
    public static Keyring getInstance() throws KeyringException {
        if (INSTANCE == null) {
            throw new KeyringException(NOT_INITIALISED_ERR);
        }
        return INSTANCE;
    }
}
