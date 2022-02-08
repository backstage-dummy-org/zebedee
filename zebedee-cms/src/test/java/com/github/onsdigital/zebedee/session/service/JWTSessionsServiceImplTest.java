package com.github.onsdigital.zebedee.session.service;

import com.github.onsdigital.JWTHandlerImpl;
import com.github.onsdigital.impl.UserDataPayload;
import com.github.onsdigital.interfaces.JWTHandler;
import com.github.onsdigital.zebedee.junit4.rules.RunInThread;
import com.github.onsdigital.zebedee.junit4.rules.RunInThreadRule;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.teams.model.Team;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class JWTSessionsServiceImplTest {

    private static final String REQUIRED_CLAIM_PAYLOAD_ERROR = "Required JWT payload claim not found [username or cognito:groups].";
    private static final String ACCESS_TOKEN_INTEGRITY_ERROR = "Verification of JWT token integrity failed.";
    private static final String RSA_PUBLIC_KEY_INALID_ERROR  = "Public Key cannot be empty or null.";

    private static final String SIGNED_TOKEN         = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjUxR3BvdHBTVGxtK3FjNXhOWUhzSko2S2tlT2JSZjlYSDQxYkhIS0JJOE09In0.eyJzdWIiOiJhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJkZXZpY2Vfa2V5IjoiYWFhYWFhYWEtYmJiYi1jY2NjLWRkZGQtZWVlZWVlZWVlZWVlIiwiY29nbml0bzpncm91cHMiOlsiYWRtaW4iLCJwdWJsaXNoaW5nIiwiZGF0YSIsInRlc3QiXSwidG9rZW5fdXNlIjoiYWNjZXNzIiwic2NvcGUiOiJhd3MuY29nbml0by5zaWduaW4udXNlci5hZG1pbiIsImF1dGhfdGltZSI6MTU2MjE5MDUyNCwiaXNzIjoiaHR0cHM6Ly9jb2duaXRvLWlkcC51cy13ZXN0LTIuYW1hem9uYXdzLmNvbS91cy13ZXN0LTJfZXhhbXBsZSIsImV4cCI6OTk5OTk5OTk5OSwiaWF0IjoxNTYyMTkwNTI0LCJqdGkiOiJhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJjbGllbnRfaWQiOiI1N2NiaXNoazRqMjRwYWJjMTIzNDU2Nzg5MCIsInVzZXJuYW1lIjoiamFuZWRvZUBleGFtcGxlLmNvbSJ9.fC3P6jnpnhmOxdlw0u4nOhehz7dCXsqX7RvqI1gEC4wrJoE6rlKH1mo7lR16K-EXWdXRoeN0_z0PZQzo__xOprAsY2XSNOexOcIo3hoydx6CkGWGmNNsLp35iGY3DgW6SLpQsdGF8HicJ9D9KCTPXKAGmOrkX3t92WSCLiQXXuER9gndzC6oLMU0akvKDstoTfwLWeSsogOQBn7_lUqGaHC8T06ZR37n_eOgUGSXwSFuYbg1zcY2xK3tMh4Wo8TOrADOrfLg660scpXuu-oDf0PNdgpXGU318IK1R0A2LiqqJWIV1sDE88uuPcX9-xgKc0eUn6qABXM9qhEyr6MS6g";
    private static final String UNSIGNED_TOKEN       = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjUxR3BvdHBTVGxtK3FjNXhOWUhzSko2S2tlT2JSZjlYSDQxYkhIS0JJOE09In0.eyJzdWIiOiJhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJkZXZpY2Vfa2V5IjoiYWFhYWFhYWEtYmJiYi1jY2NjLWRkZGQtZWVlZWVlZWVlZWVlIiwiY29nbml0bzpncm91cHMiOlsiYWRtaW4iLCJwdWJsaXNoaW5nIiwiZGF0YSIsInRlc3QiXSwidG9rZW5fdXNlIjoiYWNjZXNzIiwic2NvcGUiOiJhd3MuY29nbml0by5zaWduaW4udXNlci5hZG1pbiIsImF1dGhfdGltZSI6MTU2MjE5MDUyNCwiaXNzIjoiaHR0cHM6Ly9jb2duaXRvLWlkcC51cy13ZXN0LTIuYW1hem9uYXdzLmNvbS91cy13ZXN0LTJfZXhhbXBsZSIsImV4cCI6OTk5OTk5OTk5OSwiaWF0IjoxNTYyMTkwNTI0LCJqdGkiOiJhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJjbGllbnRfaWQiOiI1N2NiaXNoazRqMjRwYWJjMTIzNDU2Nzg5MCIsInVzZXJuYW1lIjoiamFuZWRvZUBleGFtcGxlLmNvbSJ9";
    private final static String TOKEN_NO_USER        = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjUxR3BvdHBTVGxtK3FjNXhOWUhzSko2S2tlT2JSZjlYSDQxYkhIS0JJOE09In0.eyJzdWIiOiJhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJkZXZpY2Vfa2V5IjoiYWFhYWFhYWEtYmJiYi1jY2NjLWRkZGQtZWVlZWVlZWVlZWVlIiwiY29nbml0bzpncm91cHMiOlsiYWRtaW4iLCJwdWJsaXNoaW5nIiwiZGF0YSIsInRlc3QiXSwidG9rZW5fdXNlIjoiYWNjZXNzIiwic2NvcGUiOiJhd3MuY29nbml0by5zaWduaW4udXNlci5hZG1pbiIsImF1dGhfdGltZSI6MTU2MjE5MDUyNCwiaXNzIjoiaHR0cHM6Ly9jb2duaXRvLWlkcC51cy13ZXN0LTIuYW1hem9uYXdzLmNvbS91cy13ZXN0LTJfZXhhbXBsZSIsImV4cCI6OTk5OTk5OTk5OSwiaWF0IjoxNTYyMTkwNTI0LCJqdGkiOiJhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJjbGllbnRfaWQiOiI1N2NiaXNoazRqMjRwYWJjMTIzNDU2Nzg5MCJ9.Vy6CJLdgDsCpoExm79aZh-2ugrO5u8M4M2g6s65-4RcocXxN5FZaQFvibwdh9h4bbz_qXqxJloBgZq3PmrIZrCIllmHhIbRmc3IISPG5_fdVspcjwVLUWLw-dWbdqaMo2uP6JIFmUx6DenO8ZB5I-82woyqhRxqfiCKG5q-ZEos4PzYO8bWcxYSOtC-j9p9bHJHxCUjwNvNHwSPUKrLacoo7e0dmpQI90PqK1KZqp52iieKdrHRYgHrmcTmiXY2mV2Ul8RodDl04jWvUwd52Qn4nIo-qUxROfnf5jbY1-rNotK-B3n5MSFA0YHcuiGN-bt8dUCyLLKkYjqBRpalzlg";
    private final static String TOKEN_EXPIRED_TIME   = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjUxR3BvdHBTVGxtK3FjNXhOWUhzSko2S2tlT2JSZjlYSDQxYkhIS0JJOE09In0.eyJzdWIiOiJhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJkZXZpY2Vfa2V5IjoiYWFhYWFhYWEtYmJiYi1jY2NjLWRkZGQtZWVlZWVlZWVlZWVlIiwiY29nbml0bzpncm91cHMiOlsiYWRtaW4iLCJwdWJsaXNoaW5nIiwiZGF0YSIsInRlc3QiXSwidG9rZW5fdXNlIjoiYWNjZXNzIiwic2NvcGUiOiJhd3MuY29nbml0by5zaWduaW4udXNlci5hZG1pbiIsImF1dGhfdGltZSI6MTU2MjE5MDUyNCwiaXNzIjoiaHR0cHM6Ly9jb2duaXRvLWlkcC51cy13ZXN0LTIuYW1hem9uYXdzLmNvbS91cy13ZXN0LTJfZXhhbXBsZSIsImV4cCI6MTYyNTEzNzUzLCJpYXQiOjE1NjIxOTA1MjQsImp0aSI6ImFhYWFhYWFhLWJiYmItY2NjYy1kZGRkLWVlZWVlZWVlZWVlZSIsImNsaWVudF9pZCI6IjU3Y2Jpc2hrNGoyNHBhYmMxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJqYW5lZG9lQGV4YW1wbGUuY29tIn0.S-s8RJNTY2czRGpKLGmMb7fPgdmB086diMtc7M7eXPmjj4DCFkH0Pn9quqy3VEzPUp0NpKWmlVZlaZf0dyAhld7wIUYD8csMD7pMOE9zJMBw3elc9TZJnV06nA63-Htv_ykNvp-nuU1GzewIh_ujIV0RyPRbcxnxF8p2_kWuTnqvaZ6kt1M-XNuHt3lVDj9yAJFeApeZEdrB2-ma3sAsupHuvMQ2JFPvTKz0jWp_7oKi-O21M66TmBiNzpcZJFc7_S9oFHHuy0lW6C_kEI8yQMUPEewVhXwE6doJPHQj-v6j6xc0ieOyDwXpyRUmItapZyDTVF0hkawtw4h5vmvNNw";
    private final static String INVALID_SIGNED_TOKEN = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjUxR3BvdHBTVGxtK3FjNXhOWUhzSko2S2tlT2JSZjlYSDQxYkhIS0JJOE09In0.eyJzdWIiOiJhYWFhYWFhLS1iYmJiLWNjY2MtZGPvv71kLWVlZWVlZWVlZWVlZSIsImPzv712aWNlX2tleSI6ImFhYWFhYWFhLWJiYmItY2NjYy1kZGRkLWVlZWVlZWVlZWVlZSIsImNvZ25pdG86Z3JvdXBzIjpbImFkbWluIiwicHVibGlzaGluZyIsImRhdGEiLCJ0ZXN0Il0sInRva2VuX3VzZSI6ImFjY2VzcyIsInNjb3BlIjoiYXdzLmNvZ25pdG8uc2lnbmluLnVzZXIuYWRtaW4iLCJhdXRoX3RpbWUiOjE1NjIxOTA1MjQsImlzcyI6Imh0dHBzOi8vY29nbml0by1pZHAudXMtd2VzdC0yLmFtYXpvbmF3cy5jb20vdXMtd2VzdC0yX2V4YW1wbGUiLCJleHAiOjk5OTk5OTk5OTksImlhdCI6MTU2MjE5MDUyNCwianRpIjoiYWFhYWFhYWEtYmJiYi1jY2NjLWRkZGQtZWVlZWVlZWVlZWVlIiwiY2xpZW50X2lkIjoiNTdjYmlzaGs0ajI0cGFiYzEyMzQ1Njc4OTAiLCJ1c2VybmFtZSI6ImphbmVkb2VAZXhhbXBsZS5jb20ifQ.C5gKr5nVH1-ytJ9bAf996c5d7n1NLRsBi0wLYMoODJr36Kq-0fcQRC-l_2HAjnNmmj-FL8w8MJaIFkwt5rC4X5safj_LAELfJXgSukoDXhbeEZyyr5bG9gBkt92c7SKdyfHo19qNxZWt1b3xBAinrzBc9gv2khpII-Qc75s4FBOPiXIf1020gOirCW8Hzmj6Hpa2pCJHO4nuVqtJea-L8YVRw6Gc4d-DJQcDGH1tR8l1ynSI1e_8v8e0nNWkUxsBPAAUfb1jg61-YDCXylQhsEkLUfqSYNseRUtQMw2j4VEzHwu6f_P_g67-587Zp-FiATy_S2cwZdsIl_Ga9f0xrA";
    private final static String HEADER_KID_NOT_FOUND = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjUxR3BvdHBTVGxtK3FjNXhOWUhzSko2S2tlT2JSZjlYSDQxYkhIS0JJWk09In0.eyJzdWIiOiJhYWFhYWFhLS1iYmJiLWNjY2MtZGPvv71kLWVlZWVlZWVlZWVlZSIsImPvv712aWNlX2tleSI6ImFhYWFhYWFhLWJiYmItY2NjYy1kZGRkLWVlZWVlZWVlZWVlZSIsImNvZ25pdG86Z3JvdXBzIjpbImFkbWluIiwicHVibGlzaGluZyIsImRhdGEiLCJ0ZXN0Il0sInRva2VuX3VzZSI6ImFjY2VzcyIsInNjb3BlIjoiYXdzLmNvZ25pdG8uc2lnbmluLnVzZXIuYWRtaW4iLCJhdXRoX3RpbWUiOjE1NjIxOTA1MjQsImlzcyI6Imh0dHBzOi8vY29nbml0by1pZHAudXMtd2VzdC0yLmFtYXpvbmF3cy5jb20vdXMtd2VzdC0yX2V4YW1wbGUiLCJleHAiOjk5OTk5OTk5OTksImlhdCI6MTU2MjE5MDUyNCwianRpIjoiYWFhYWFhYWEtYmJiYi1jY2NjLWRkZGQtZWVlZWVlZWVlZWVlIiwiY2xpZW50X2lkIjoiNTdjYmlzaGs0ajI0cGFiYzEyMzQ1Njc4OTAiLCJ1c2VybmFtZSI6ImphbmVkb2VAZXhhbXBsZS5jb20ifQ.anpazybyKL3RHUGqDFlSJKKj3OUVyoixfOsThR3yL_AuwVFyO7pXrur_nFOxmQvd4jGYCKZrx3DJa3GHNorbg2HghdV_THAIxzyDPq7mTB9yJTNHp8ewiWFltXtWRwaRBwnE23vbU7JNiAb2-SkD6uaemA_ZSO9RM3j4VNjeNmNXvXh2qO_yij_Z1clIDFu5gZr_XH7d5Vq5ie62BbxlHGP4Kv_aC6kZDOfca3tK02XBNaF_AtJAQa-Z9yUpmMWqbVwZEwjUwrZ2QxPx1-Yylj8G3tJTuFqur174RQaKY7Kd1ooP9ZvXXrtdhyKdeBMZguJg34rIEefc_CUV_1zXpw";

    private static final String RSA_KEY_ID_1      = "51GpotpSTlm+qc5xNYHsJJ6KkeObRf9XH41bHHKBI8M=";
    private static final String RSA_SIGNING_KEY_1 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnzyis1ZjfNB0bBgKFMSv"
    +"vkTtwlvBsaJq7S5wA+kzeVOVpVWwkWdVha4s38XM/pa/yr47av7+z3VTmvDRyAHc"
    +"aT92whREFpLv9cj5lTeJSibyr/Mrm/YtjCZVWgaOYIhwrXwKLqPr/11inWsAkfIy"
    +"tvHWTxZYEcXLgAXFuUuaS3uF9gEiNQwzGTU1v0FqkqTBr4B8nW3HCN47XUu0t8Y0"
    +"e+lf4s4OxQawWD79J9/5d3Ry0vbV3Am1FtGJiJvOwRsIfVChDpYStTcHTCMqtvWb"
    +"V6L11BWkpzGXSW4Hv43qa+GSYOD2QU68Mb59oSk2OB+BtOLpJofmbGEGgvmwyCI9"
    +"MwIDAQAB";

    private static final String RSA_KEY_ID_2      = "572VpotpSTlm+qc5xNYHsJJ6KkeObRf9XH41bbsYnb7Gf=";
    private static final String RSA_SIGNING_KEY_2 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnzyis1ZjfNB0bBgKFMSv"
    +"vkTtwlvBsaJq7S5wA+kzeVOVpVWwkWdVha4s38XM/pa/yr47av7+z3VTmvDRyAHc"
    +"aT92whREFpLv9cj5lTeJSibyr/Mrm/YtjCZVWgaOYIhwrXwKLqPr/11inWsAkfIy"
    +"tvHWTxZYEcXLgAXFuUuaS3uF9gEiNQwzGTU1v0FqkqTBr4B8nW3HCN47XUu0t8Y0"
    +"e+lf4s4OxQawWD79J9/5d3Ry0vbV3Am1FtGJiJvOwRsIfVChDpYStTcHTCMqtvWb"
    +"V6L11BWkpzGXSW4Hv43qa+GSYOD2QU68Mb59oSk2OB+BtOLpJofmbGEGgvmwyCI9"
    +"MwIDAQAB";

    private final static String EMAIL = "janedoe@example.com";

    private JWTSessionsServiceImpl jwtSessionsServiceImpl;

    private Map<String, String> rsaKeyMap = new HashMap<String, String>();

    private JWTHandler jwtHandler = new JWTHandlerImpl();

    private List<Team> teams;
    private String[] teamIDs;
    private Team team1;
    private Team team2;

    @Mock
    private UserDataPayload userDataPayload;

    private static ThreadLocal<UserDataPayload> store = new ThreadLocal<>();

    @Rule
    public RunInThreadRule runInThread = new RunInThreadRule();

    @BeforeClass
    public static void staticSetUp() {
        JWTSessionsServiceImpl.setStore(store);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        rsaKeyMap.put(RSA_KEY_ID_1, RSA_SIGNING_KEY_1);
        rsaKeyMap.put(RSA_KEY_ID_2, RSA_SIGNING_KEY_2);

        this.teams = new ArrayList<>();

        this.team1 = new Team();
        this.team1.setId(10);
        this.teams.add(team1);

        this.team2 = new Team();
        this.team2.setId(20);
        this.team2.addMember(EMAIL);
        this.teams.add(team2);

        this.teamIDs = teams.stream().map(t -> Integer.toString(t.getId())).toArray(String[]::new);

        this.userDataPayload = new UserDataPayload(EMAIL, teamIDs);

        this.jwtSessionsServiceImpl = new JWTSessionsServiceImpl(jwtHandler, rsaKeyMap);
    }

    @Test
    @RunInThread
    public void decodeVerifyAndStoreAccessTokenData() throws Exception {
        this.jwtSessionsServiceImpl.set(SIGNED_TOKEN);

        UserDataPayload actual = this.store.get();

        assertThat(actual, is(notNullValue()));
        assertThat(actual.getEmail(), is(String.format("\"%s\"", EMAIL)));

        Arrays.sort(actual.getGroups());
        assertThat(actual.getGroups()[0], is("admin"));
        assertThat(actual.getGroups()[1], is("data"));
        assertThat(actual.getGroups()[2], is("publishing"));
        assertThat(actual.getGroups()[3], is("test"));
    }   

    @Test
    @RunInThread
    public void decodeVerifyAndStoreAccessTokenDataThrowsTokenExcpetion() throws Exception {
        Exception exception = assertThrows(SessionsException.class, () -> this.jwtSessionsServiceImpl.set(""));
        assertThat(exception.getMessage(), is(this.jwtSessionsServiceImpl.ACCESS_TOKEN_REQUIRED_ERROR));
        assertThat(this.store.get(), is(nullValue()));
    }

    @Test
    @RunInThread
    public void decodingAccessTokenWithNoUsernameInClaimsThrowsDecodeExcpetion() throws Exception {
        Exception exception = assertThrows(SessionsException.class, () -> this.jwtSessionsServiceImpl.set(TOKEN_NO_USER));
        assertThat(exception.getMessage(), is(REQUIRED_CLAIM_PAYLOAD_ERROR));
        assertThat(this.store.get(), is(nullValue()));
    }

    @Test
    @RunInThread
    public void decodedTokenIsExiredThrowsSessionsTokenExpiredException() throws Exception {
        Exception exception = assertThrows(SessionsException.class, () -> this.jwtSessionsServiceImpl.set(TOKEN_EXPIRED_TIME));
        assertThat(exception.getMessage(), is(this.jwtSessionsServiceImpl.ACCESS_TOKEN_EXPIRED_ERROR));
        assertThat(this.store.get(), is(nullValue()));
    }

    @Test
    @RunInThread
    public void decodedTokenIsInvalidThrowsSessionVerificationException() throws Exception {
        Exception exception = assertThrows(SessionsException.class, () -> this.jwtSessionsServiceImpl.set(INVALID_SIGNED_TOKEN));
        assertThat(exception.getMessage(), is(ACCESS_TOKEN_INTEGRITY_ERROR));
        assertThat(this.store.get(), is(nullValue()));
    }

    @Test
    @RunInThread
    public void decodedTokenHeaderContainsKIDNotFoundInMapThrowsSessionsDecodeException() throws Exception {
        Exception exception = assertThrows(SessionsException.class, () -> this.jwtSessionsServiceImpl.set(HEADER_KID_NOT_FOUND));
        assertThat(exception.getMessage(), is(RSA_PUBLIC_KEY_INALID_ERROR));
        assertThat(this.store.get(), is(nullValue()));
    }

    @Test
    @RunInThread
    public void invalidlyFormattedAccessTokenPassedtoSetterThrowsSessionsDecodeException() throws Exception {
        Exception exception = assertThrows(SessionsException.class, () -> this.jwtSessionsServiceImpl.set(UNSIGNED_TOKEN));
        assertThat(exception.getMessage(), is(this.jwtSessionsServiceImpl.TOKEN_NOT_VALID_ERROR));
        assertThat(this.store.get(), is(nullValue()));
    }

    @Test
    @RunInThread
    public void set_ShouldClearThread_WhenSetAgain() throws Exception {
        store.set(userDataPayload);

        assertThrows(SessionsException.class, () -> jwtSessionsServiceImpl.set(""));
        assertNull(store.get());
    }

    @Test
    @RunInThread
    public void getByRequest_sessionDoesNotExist_shouldReturnNull() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);

        Session actual = jwtSessionsServiceImpl.get(request);

        assertThat(actual, is(nullValue()));
    }

    @Test
    @RunInThread
    public void getByRequest_success_shouldReturnSession() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);

        this.store.set(userDataPayload);

        Session actual = jwtSessionsServiceImpl.get(request);

        assertThat(actual, is(notNullValue()));
        assertThat(actual.getEmail(), equalTo(EMAIL));
        assertThat(actual.getGroups(), equalTo(teamIDs));
    }

    @Test
    @RunInThread
    public void getByIDSessionDoesNotExist_shouldReturnNull() throws Exception {
        assertThat(jwtSessionsServiceImpl.get("ID"), is(nullValue()));
    }

    @Test
    @RunInThread
    public void getByID_success_shouldReturnSession() throws Exception {
        this.store.set(userDataPayload);

        Session actual = jwtSessionsServiceImpl.get("666");

        assertThat(actual, is(notNullValue()));
        assertThat(actual.getEmail(), equalTo(EMAIL));
        assertThat(actual.getGroups(), equalTo(teamIDs));
    }

    @Test
    @RunInThread
    public void resetThread_ShouldClearSessions() throws Exception {
        store.set(userDataPayload);

        jwtSessionsServiceImpl.resetThread();

        assertNull(store.get());
    }
}
