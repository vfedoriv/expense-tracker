# WireMock OAuth2 Testing Reference

## Dependency

```xml
<dependency>
    <groupId>org.wiremock.integrations</groupId>
    <artifactId>wiremock-spring-boot</artifactId>
    <version>4.2.1</version>
    <scope>test</scope>
</dependency>
```

This single dependency pulls in wiremock-jetty12 3.13.x transitively. Compatible with Spring Boot 4.0.4+.

If 4.2.1 has issues, fallback to 4.1.0 or 4.0.8. Versions below 4.0.7 have Jetty 12.0.x vs 12.1.x conflicts.

## Usage Pattern

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock({
    @ConfigureWireMock(name = "oauth-provider", baseUrlProperties = "wiremock.oauth.url")
})
@ActiveProfiles("oauth-test")
class OAuth2WireMockFlowTest {

    @InjectWireMock("oauth-provider")
    WireMockServer wireMock;

    @Value("${wiremock.oauth.url}")
    String wireMockUrl;
}
```

## Key Architecture Note

The project's `OAuth2ClientConfig.java` builds ClientRegistration objects **programmatically** with hardcoded URLs (google=https://accounts.google.com/..., github=https://github.com/...). The test must override the `clientRegistrationRepository` bean via a `@TestConfiguration` to point all URLs at WireMock.

## Google OIDC Stubs Needed

1. `GET /.well-known/openid-configuration` -> OIDC discovery JSON
2. `POST /oauth2/v4/token` -> access_token + id_token (valid JWT)
3. `GET /oauth2/v3/userinfo` -> user claims JSON
4. `GET /oauth2/v3/certs` -> JWKS with RSA public key

The id_token must be a valid signed JWT. Generate RSA 2048 key pair in test, sign JWT with private key, serve public key in JWKS via certs stub.

## GitHub OAuth2 Stubs Needed

1. `POST /login/oauth/access_token` -> JSON with access_token
2. `GET /api/user` -> user JSON with {id, login, name, email, avatar_url}

## State Parameter Handling

OAuth2 uses state parameter for CSRF. Test must:
1. GET /oauth2/authorization/github (or /google) -> follows redirect -> capture state from Location header
2. Use captured state in callback: GET /login/oauth2/code/github?code=mock-code&state=<captured>

Use java.net.http.HttpClient with HttpClient.Redirect.NEVER to capture redirects manually.
