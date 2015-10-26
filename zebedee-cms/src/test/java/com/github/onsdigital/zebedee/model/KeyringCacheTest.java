package com.github.onsdigital.zebedee.model;

import com.github.davidcarboni.cryptolite.Random;
import com.github.onsdigital.zebedee.Builder;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.exceptions.CollectionNotFoundException;
import com.github.onsdigital.zebedee.json.Keyring;
import com.github.onsdigital.zebedee.json.Session;
import com.github.onsdigital.zebedee.json.User;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Test for {@link KeyringCache}.
 */
public class KeyringCacheTest {

    static Builder builder;
    private Zebedee zebedee;
    private KeyringCache keyringCache;

    @BeforeClass
    public static void beforeClass() throws IOException, CollectionNotFoundException {
        builder = new Builder(KeyringCacheTest.class);
    }

    @Before
    public  void before() throws IOException, CollectionNotFoundException {
        zebedee = new Zebedee(builder.zebedee);
         keyringCache = zebedee.keyringCache;
    }

    @Test
    public void shouldPutAndGetKeyring() throws Exception {

        // Given
        // A user with a session
        User user = user();
        builder.createSession(user);

        // When
        // We put the user's keyring
        keyringCache.put(user);
        Keyring keyring = zebedee.keyringCache.get(user);

        // Then
        // We sholud be able to get the user's keyring
        assertNotNull(keyring);
        assertTrue(keyring.isUnlocked());
    }

    @Test
    public void shouldNotPutNulls() throws Exception {

        // Given
        // A null user and missing session
        User nullUser = null;
        User noSessionUser = user();

        // When
        // We put the user's keyring
        keyringCache.put(nullUser);
        keyringCache.put(noSessionUser);

        // Then
        // We should get no error and nothing should be present in the cache
        assertNull(keyringCache.get(nullUser));
        assertNull(keyringCache.get(noSessionUser));
    }

    @Test
    public void shouldGetNullsWithoutError() throws Exception {

        // Given
        // A null user and missing session
        User nullUser = null;
        User noSessionUser = user();

        // When
        // We put the user's keyring
        Keyring nullUserKeyring = keyringCache.get(nullUser);
        Keyring nullSessioKeyring = keyringCache.get(noSessionUser);

        // Then
        // We should get no error and nothing should be returned from the cache
        assertNull(nullUserKeyring);
        assertNull(nullSessioKeyring);
    }

    @Test
    public void shouldRemoveKeyring() throws Exception {

        // Given
        // A keyring in the cache
        User user = user();
        Session session = builder.createSession(user);
        keyringCache.put(user);

        // When
        // We remove the user's keyring
        zebedee.keyringCache.remove(session);

        // Then
        // The user's keyring should not be present in the cache
        assertNull(zebedee.keyringCache.get(user));
    }

    @Test
    public void shouldRemoveNullWithoutError() throws Exception {

        // Given
        // A null session and user not in the cache
        Session nullSession = null;
        User user = user();
        Session notInCacheSession = builder.createSession(user);

        // When
        // We put the user's keyring
        keyringCache.remove(nullSession);
        keyringCache.remove(notInCacheSession);

        // Then
        // We should get no error and nothing should be present in the cache
        assertNull(keyringCache.get(user));
    }

    private User user() {
        User result = new User();
        result.email = Random.id()+"@example.com";
        result.resetPassword(Random.password(8));
        return result;
    }
}