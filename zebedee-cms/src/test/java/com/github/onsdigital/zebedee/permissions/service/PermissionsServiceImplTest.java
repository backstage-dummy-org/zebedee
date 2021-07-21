package com.github.onsdigital.zebedee.permissions.service;

import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.permissions.model.AccessMapping;
import com.github.onsdigital.zebedee.permissions.store.PermissionsStore;
import com.github.onsdigital.zebedee.service.ServiceSupplier;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.teams.model.Team;
import com.github.onsdigital.zebedee.teams.service.TeamsService;
import com.github.onsdigital.zebedee.user.model.User;
import com.github.onsdigital.zebedee.user.model.UserList;
import com.github.onsdigital.zebedee.user.service.UsersService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Created by dave on 31/05/2017.
 */
public class PermissionsServiceImplTest {

    private static final String EMAIL = "admin@ons.gov.uk";
    private static final Integer COLLECTION_ID = 123;

    @Mock
    private PermissionsStore permissionsStore;

    @Mock
    private UsersService usersService;

    @Mock
    private TeamsService teamsService;

    @Mock
    private AccessMapping accessMapping;

    @Mock
    private User userMock;

    @Mock
    private Team teamMock;

    @Mock
    private Collection collection1, collection2;

    @Mock
    private CollectionDescription collectionDescription;

    private PermissionsService permissions;
    private ServiceSupplier<UsersService> usersServiceServiceSupplier;
    private ServiceSupplier<TeamsService> teamsServiceSupplier;
    private Session session;
    private Set<String> digitalPublishingTeam;
    private Set<String> admins;
    private List<Team> teamsList;
    private UserList userList;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        teamsList = new ArrayList<>();
        teamsList.add(teamMock);

        userList = new UserList();
        userList.add(userMock);

        usersServiceServiceSupplier = () -> usersService;
        teamsServiceSupplier = () -> teamsService;

        session = new Session();
        session.setEmail(EMAIL);

        digitalPublishingTeam = new HashSet<>();
        admins = new HashSet<>();

        when(userMock.getEmail())
                .thenReturn(EMAIL);

        permissions = new PermissionsServiceImpl(permissionsStore, usersServiceServiceSupplier, teamsServiceSupplier);
    }

    @Test
    public void isPublisherBySession_ShouldReturnFalseIfSessionNull() throws Exception {
        session = null;

        assertThat(permissions.isPublisher(session), is(false));
        verifyZeroInteractions(permissionsStore, usersService, teamsService);
    }

    @Test
    public void isPublisherBySession_ShouldReturnFalseIfSessionEmailNull() throws Exception {
        session.setEmail(null);

        assertThat(permissions.isPublisher(session), is(false));
        verifyZeroInteractions(permissionsStore, usersService, teamsService);
    }

    @Test
    public void isPublisherBySession_ShouldReturnFalseIfPSTIsNull() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(null);

        assertThat(permissions.isPublisher(session), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(1)).getDigitalPublishingTeam();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isPublisherBySession_ShouldReturnFalseIfPSTDoesNotContainSessionEmail() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(digitalPublishingTeam);

        assertThat(permissions.isPublisher(session), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getDigitalPublishingTeam();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isPublisherBySession_ShouldReturnTrueIfPSTContainsSessionEmail() throws Exception {
        digitalPublishingTeam.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(digitalPublishingTeam);

        assertThat(permissions.isPublisher(session), is(true));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getDigitalPublishingTeam();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isPublisherByEmail_ShouldReturnFalseIfEmailIsNull() throws Exception {
        String email = null;
        assertThat(permissions.isPublisher(email), is(false));
        verifyZeroInteractions(permissionsStore, usersService, accessMapping, teamsService);
    }

    @Test
    public void isPublisherByEmail_ShouldReturnFalseIfEmailIsEmpty() throws Exception {
        assertThat(permissions.isPublisher(""), is(false));
        verifyZeroInteractions(permissionsStore, usersService, accessMapping, teamsService);
    }

    @Test
    public void isPublisherByEmail_ShouldReturnFalseIfPSTIsNull() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(null);

        assertThat(permissions.isPublisher(EMAIL), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(1)).getDigitalPublishingTeam();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isPublisherByEmail_ShouldReturnFalseIfPSTDoesNotContainSessionEmail() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(digitalPublishingTeam);

        assertThat(permissions.isPublisher(EMAIL), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getDigitalPublishingTeam();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isPublisherByEmail_ShouldReturnTrueIfPSTContainsSessionEmail() throws Exception {
        digitalPublishingTeam.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(digitalPublishingTeam);

        assertThat(permissions.isPublisher(EMAIL), is(true));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getDigitalPublishingTeam();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isAdministratorBySession_ShouldReturnFalseIsSessionNull() throws Exception {
        session = null;
        assertThat(permissions.isAdministrator(session), is(false));
        verifyZeroInteractions(permissionsStore, usersService, accessMapping, teamsService);
    }

    @Test
    public void isAdministratorBySession_ShouldReturnFalseIsSessionEmailNull() throws Exception {
        session.setEmail(null);
        assertThat(permissions.isAdministrator(session), is(false));
        verifyZeroInteractions(permissionsStore, usersService, accessMapping, teamsService);
    }

    @Test
    public void isAdministratorBySession_ShouldReturnFalseIfAdminsIsNull() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(null);

        assertThat(permissions.isAdministrator(session), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(1)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isAdministratorBySession_ShouldReturnFalseIfAdminsDoesNotContainEmail() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        assertThat(permissions.isAdministrator(session), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isAdministratorBySession_ShouldReturnTrueIfAdminsContainsEmail() throws Exception {
        admins.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        assertThat(permissions.isAdministrator(session), is(true));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isAdministratorByEmail_ShouldReturnFalseIsEmailNull() throws Exception {
        String email = null;
        assertThat(permissions.isAdministrator(email), is(false));
        verifyZeroInteractions(permissionsStore, usersService, accessMapping, teamsService);
    }

    @Test
    public void isAdministratorByEmail_ShouldReturnFalseIfAdminsIsNull() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(null);

        assertThat(permissions.isAdministrator(EMAIL), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(1)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isAdministratorByEmail_ShouldReturnFalseIfAdminsDoesNotContainEmail() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        assertThat(permissions.isAdministrator(EMAIL), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void isAdministratorByEmail_ShouldReturnTrueIfAdminsContainsEmail() throws Exception {
        admins.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        assertThat(permissions.isAdministrator(EMAIL), is(true));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void hasAdministrator_ShouldReturnFalseIfAdminsIsNull() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(null);

        assertThat(permissions.hasAdministrator(), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(1)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void hasAdministrator_ShouldReturnFalseIfAdminsIsEmpty() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        assertThat(permissions.hasAdministrator(), is(false));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void hasAdministrator_ShouldReturnTrueIfAdminsIsNotEmpty() throws Exception {
        admins.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        assertThat(permissions.hasAdministrator(), is(true));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyZeroInteractions(usersService, teamsService);
    }


    @Test(expected = UnauthorizedException.class)
    public void removeAdministrator_ShouldThrowExceptionIfSessionNull() throws Exception {
        try {
            permissions.removeAdministrator(EMAIL, null);
        } catch (UnauthorizedException e) {
            verifyZeroInteractions(permissionsStore, accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void removeAdministrator_ShouldThrowExceptionIfEmailNull() throws Exception {
        try {
            permissions.removeAdministrator(null, session);
        } catch (UnauthorizedException e) {
            verifyZeroInteractions(permissionsStore, accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void removeAdministrator_ShouldThrowExceptionIfUserIsNotAnAdmin() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        try {
            permissions.removeAdministrator(EMAIL, session);
        } catch (UnauthorizedException e) {
            verify(permissionsStore, times(1)).getAccessMapping();
            verify(accessMapping, times(2)).getAdministrators();
            verifyZeroInteractions(accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test
    public void removeAdministrator_Success() throws Exception {
        admins.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        permissions.removeAdministrator(EMAIL, session);
        verify(permissionsStore, times(2)).getAccessMapping();
        verify(accessMapping, times(4)).getAdministrators();
        verify(permissionsStore, times(1)).saveAccessMapping(accessMapping);
        verifyZeroInteractions(usersService, teamsService);
    }


    @Test(expected = UnauthorizedException.class)
    public void removeEditor_ShouldThrowExceptionIfSessionNull() throws Exception {
        try {
            permissions.removeEditor(EMAIL, null);
        } catch (UnauthorizedException e) {
            verifyZeroInteractions(permissionsStore, accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void removeEditor_ShouldThrowExceptionIfEmailNull() throws Exception {
        try {
            permissions.removeEditor(null, session);
        } catch (UnauthorizedException e) {
            verifyZeroInteractions(permissionsStore, accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void removeEditor_ShouldThrowExceptionIfUserIsNotAnAdmin() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(digitalPublishingTeam);

        try {
            permissions.removeEditor(EMAIL, session);
        } catch (UnauthorizedException e) {
            verify(permissionsStore, times(1)).getAccessMapping();
            verify(accessMapping, times(2)).getAdministrators();
            verifyZeroInteractions(accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test
    public void removeEditor_Success() throws Exception {
        digitalPublishingTeam.add(EMAIL);
        admins.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(digitalPublishingTeam);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        permissions.removeEditor(EMAIL, session);
        verify(permissionsStore, times(2)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verify(permissionsStore, times(1)).saveAccessMapping(accessMapping);
        verifyZeroInteractions(usersService, teamsService);
    }

    @Test
    public void getCollectionAccessMapping_ForAdminUserSuccess() throws Exception {
        admins.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(teamsService.listTeams())
                .thenReturn(teamsList);
        when(usersService.list())
                .thenReturn(userList);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        List<User> result = permissions.getCollectionAccessMapping(collection1);

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(teamsService, times(1)).listTeams();
        verify(usersService, times(1)).list();
        verify(userMock, times(1)).getEmail();
        verify(accessMapping, times(2)).getAdministrators();
    }

    @Test
    public void getCollectionAccessMapping_ForPublisherUserSuccess() throws Exception {
        digitalPublishingTeam.add(EMAIL);

        List<User> expected = new ArrayList<>();
        expected.add(userMock);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(teamsService.listTeams())
                .thenReturn(teamsList);
        when(usersService.list())
                .thenReturn(userList);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(digitalPublishingTeam);
        when(collection1.getDescription())
                .thenReturn(collectionDescription);

        List<User> result = permissions.getCollectionAccessMapping(collection1);

        assertThat(result, equalTo(expected));
        verify(permissionsStore, times(2)).getAccessMapping();
        verify(teamsService, times(1)).listTeams();
        verify(usersService, times(1)).list();
        verify(userMock, times(2)).getEmail();
        verify(accessMapping, times(2)).getAdministrators();
    }

    @Test(expected = UnauthorizedException.class)
    public void addAdministrator_ShouldThrowErrorSessionNull() throws Exception {
        admins.add(EMAIL);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        try {
            permissions.addAdministrator(EMAIL, null);
        } catch (UnauthorizedException e) {
            verify(permissionsStore, times(1)).getAccessMapping();
            verify(accessMapping, times(2)).getAdministrators();
            verifyNoMoreInteractions(permissionsStore, accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void addAdministrator_ShouldThrowErrorSessionEmailNull() throws Exception {
        admins.add(EMAIL);
        session.setEmail(null);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        try {
            permissions.addAdministrator(EMAIL, session);
        } catch (UnauthorizedException e) {
            verify(permissionsStore, times(1)).getAccessMapping();
            verify(accessMapping, times(2)).getAdministrators();
            verifyNoMoreInteractions(permissionsStore, accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void addAdministrator_ShouldThrowErrorIfUserNotAdmin() throws Exception {
        admins.add(EMAIL);
        session.setEmail("test2@ons.gov.uk");

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        try {
            permissions.addAdministrator(EMAIL, session);
        } catch (UnauthorizedException e) {
            verify(permissionsStore, times(2)).getAccessMapping();
            verify(accessMapping, times(4)).getAdministrators();
            verifyNoMoreInteractions(permissionsStore, accessMapping, usersService, teamsService);
            throw e;
        }
    }

    @Test
    public void addAdministrator_Success() throws Exception {
        admins.add(EMAIL);
        String email2 = "test2@ons.gov.uk";

        Set<String> adminsMock = mock(Set.class);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);
        when(accessMapping.getAdministrators())
                .thenReturn(admins)
                .thenReturn(admins)
                .thenReturn(admins)
                .thenReturn(admins)
                .thenReturn(admins)
                .thenReturn(adminsMock);

        permissions.addAdministrator(email2, session);

        verify(permissionsStore, times(3)).getAccessMapping();
        verify(accessMapping, times(6)).getAdministrators();
        verify(permissionsStore, times(1)).saveAccessMapping(accessMapping);
        verify(adminsMock, times(1)).add(email2);
    }

    @Test
    public void canView_ShouldReturnFalseIfCollectionTeamsNull() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(new AccessMapping());
        when(teamsService.listTeams())
                .thenReturn(teamsList);

        assertThat(permissions.canView(userMock, collectionDescription), is(false));

        verify(permissionsStore, times(1)).getAccessMapping();
        verifyZeroInteractions(teamsService);
    }

    @Test
    public void listCollectionsAccessibleByTeam_getAccessMappingError_shouldThrowEx() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenThrow(IOException.class);

        Team t = mock(Team.class);
        assertThrows(IOException.class, () -> permissions.listCollectionsAccessibleByTeam(t));
    }

    @Test
    public void listCollectionsAccessibleByTeam_accessMappingNull_shouldThrowEx() throws Exception {
        when(permissionsStore.getAccessMapping())
                .thenReturn(null);

        Team t = mock(Team.class);
        IOException ex = assertThrows(IOException.class, () -> permissions.listCollectionsAccessibleByTeam(t));

        assertThat(ex.getMessage(), equalTo("error reading accessMapping expected value but was null"));
    }

    @Test
    public void listCollectionsAccessibleByTeam_collectionsNull_shouldReturnEmptySet() throws Exception {
        when(accessMapping.getCollections())
                .thenReturn(null);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);

        Team t = mock(Team.class);
        Set<String> actual = permissions.listCollectionsAccessibleByTeam(t);

        assertThat(actual, is(notNullValue()));
        assertTrue(actual.isEmpty());
    }

    @Test
    public void listCollectionsAccessibleByTeam_collectionsEmpty_shouldReturnEmptySet() throws Exception {
        when(accessMapping.getCollections())
                .thenReturn(new HashMap<>());

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);

        Team t = mock(Team.class);
        Set<String> actual = permissions.listCollectionsAccessibleByTeam(t);

        assertThat(actual, is(notNullValue()));
        assertTrue(actual.isEmpty());
    }

    @Test
    public void listCollectionsAccessibleByTeam_teamNotAssignedToAnyCollection_shouldReturnEmptySet() throws Exception {
        Map<String, Set<Integer>> collectionMapping = new HashMap<>();
        collectionMapping.put("1234", new HashSet<Integer>() {{
            add(1);
            add(2);
        }});
        collectionMapping.put("5678", new HashSet<Integer>() {{
            add(2);
            add(3);
        }});

        when(accessMapping.getCollections())
                .thenReturn(collectionMapping);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);

        Team t = new Team();
        t.setId(6);

        Set<String> actual = permissions.listCollectionsAccessibleByTeam(t);

        assertThat(actual, is(notNullValue()));
        assertTrue(actual.isEmpty());
    }

    @Test
    public void listCollectionsAccessibleByTeam_teamAssignedToCollections_shouldReturnCollectionIDs() throws Exception {
        Map<String, Set<Integer>> collectionMapping = new HashMap<>();
        collectionMapping.put("aaaa", new HashSet<Integer>() {{
            add(1);
            add(2);
        }});
        collectionMapping.put("bbbb", new HashSet<>());
        collectionMapping.put("cccc", new HashSet<Integer>() {{
            add(2);
            add(3);
        }});

        when(accessMapping.getCollections())
                .thenReturn(collectionMapping);

        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);

        Team t = new Team();
        t.setId(2);

        Set<String> actual = permissions.listCollectionsAccessibleByTeam(t);

        assertThat(actual, is(notNullValue()));
        assertThat(actual.size(), equalTo(2));
        assertTrue(actual.contains("aaaa"));
        assertTrue(actual.contains("cccc"));

    }

    @Test
    public void getCollectionsAccessibleByUser_userNull_shouldThrowEx() throws Exception {
        IOException actual = assertThrows(IOException.class,
                () -> permissions.getCollectionsAccessibleByUser(null, null));

        assertThat(actual.getMessage(), equalTo("user required but was null"));
    }

    @Test
    public void getCollectionsAccessibleByUser_userEmailNull_shouldThrowEx() throws Exception {
        when(userMock.getEmail())
                .thenReturn(null);

        IOException actual = assertThrows(IOException.class,
                () -> permissions.getCollectionsAccessibleByUser(userMock, null));

        assertThat(actual.getMessage(), equalTo("user.email required but was null/empty"));
    }

    @Test
    public void getCollectionsAccessibleByUser_userEmailEmpty_shouldThrowEx() throws Exception {
        when(userMock.getEmail())
                .thenReturn("");

        IOException actual = assertThrows(IOException.class,
                () -> permissions.getCollectionsAccessibleByUser(userMock, null));

        assertThat(actual.getMessage(), equalTo("user.email required but was null/empty"));
    }

    @Test
    public void getCollectionsAccessibleByUser_adminUser_shouldReturnAllCollections() throws Exception {
        IOException actual = assertThrows(IOException.class,
                () -> permissions.getCollectionsAccessibleByUser(userMock, null));

        assertThat(actual.getMessage(), equalTo("user.email required but was null/empty"));
    }


}
