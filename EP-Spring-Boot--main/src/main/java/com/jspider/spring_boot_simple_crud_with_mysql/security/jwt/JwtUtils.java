package com.jspider.spring_boot_simple_crud_with_mysql.security.jwt;

import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.jspider.spring_boot_simple_crud_with_mysql.security.services.UserDetailsImpl;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.annotation.PostConstruct;

/**
 * Spring-managed component that encapsulates every interaction with the JJWT
 * library. {@code JwtUtils} is the single, authoritative origin of JSON Web
 * Tokens (JWT) within the application: it issues signed access tokens after a
 * successful login, parses incoming bearer tokens to extract the authenticated
 * principal's username, and validates the signature, format, and expiration of
 * any candidate token presented on a protected request.
 *
 * <h2>Role within the security architecture</h2>
 *
 * <p>Per the Agent Action Plan (AAP &sect;0.5.1.4), this class is the
 * foundational utility of the JWT subsystem. Three production collaborators
 * depend on it directly:
 * <ul>
 *   <li>{@code AuthService.authenticate(LoginRequest)} &mdash; calls
 *       {@link #generateJwtToken(Authentication)} after the
 *       {@code AuthenticationManager} has verified the user's credentials, and
 *       returns the resulting JWT to the API client inside a
 *       {@code JwtResponse}.</li>
 *   <li>{@code AuthTokenFilter.doFilterInternal} &mdash; calls
 *       {@link #validateJwtToken(String)} to gate every authenticated request
 *       on a successful signature/expiration check, then calls
 *       {@link #getUserNameFromJwtToken(String)} to recover the username for
 *       a {@code UserDetailsService} lookup that builds the
 *       {@code SecurityContextHolder} principal.</li>
 *   <li>{@code JwtUtilsTest} &mdash; exercises all three public methods to
 *       verify generation, validation, expiration handling, and
 *       signature-mismatch rejection.</li>
 * </ul>
 *
 * <h2>Cryptographic primitives</h2>
 *
 * <p>Tokens are signed with HMAC-SHA-256 (HS256) using a symmetric secret
 * loaded from the {@code jwt.secret} property. The property holds a
 * Base64-encoded byte string that <strong>must</strong> decode to at least 32
 * bytes (256 bits) per RFC 7518 &sect;3.2; shorter values cause
 * {@link Keys#hmacShaKeyFor(byte[])} to throw
 * {@link WeakKeyException} during the {@link PostConstruct @PostConstruct}
 * bean-initialization phase, providing the AAP &sect;0.7.1.4 startup
 * fail-fast guarantee.
 *
 * <p>The algorithm is <strong>explicitly</strong> pinned to
 * {@link Jwts.SIG#HS256} on every {@link #generateJwtToken(Authentication)
 * sign} call. Pinning is essential because JJWT 0.13's single-argument
 * {@code .signWith(SecretKey)} variant auto-derives the strongest secure
 * HMAC algorithm from the key's byte length &mdash; a 64-byte secret would
 * silently upgrade signing to HS512, a 48-byte secret to HS384, and only a
 * 32-to-47-byte secret would yield HS256. AAP &sect;0.1.1 mandates HS256
 * for this application, so the two-argument
 * {@code .signWith(SecretKey, MacAlgorithm)} variant is used to guarantee
 * the {@code "alg":"HS256"} JWT header regardless of how long the operator's
 * configured secret happens to be (any length &ge; 32 bytes is accepted, but
 * only the first 32 bytes contribute meaningful entropy to HS256).
 *
 * <p>HS256 was chosen over an asymmetric algorithm (RS256, ES256) because the
 * application is a single-process monolith: the same JVM signs and verifies
 * its own tokens, eliminating the public-key distribution problem that
 * motivates asymmetric signing in microservices. HS256 is faster to compute
 * and equally secure for this deployment topology.
 *
 * <h2>Token claim set</h2>
 *
 * <p>The minted JWT carries only the three RFC 7519 registered claims that
 * are strictly necessary for stateless authentication:
 * <ul>
 *   <li>{@code sub} (subject) &mdash; the authenticated user's unique
 *       login name, sourced from
 *       {@link UserDetailsImpl#getUsername()}.</li>
 *   <li>{@code iat} (issued-at) &mdash; the timestamp at which the token
 *       was minted, sourced from {@code new Date()}.</li>
 *   <li>{@code exp} (expiration) &mdash; the timestamp after which the
 *       token must be rejected, sourced from
 *       {@code new Date(System.currentTimeMillis() + jwtExpirationMs)}.</li>
 * </ul>
 *
 * <p>Per AAP &sect;0.7.1.4, deliberately absent from the claim set are
 * {@code email}, {@code roles}, and any other custom claims. Two
 * considerations drive this choice:
 * <ol>
 *   <li><strong>PII minimization.</strong> A JWT may end up in HTTP access
 *       logs, browser history, or URL referrers; embedding only the
 *       opaque username minimizes information leakage if the token is
 *       inadvertently exposed.</li>
 *   <li><strong>Authority freshness.</strong> Roles are loaded by
 *       {@code UserDetailsServiceImpl.loadUserByUsername} on every
 *       protected request via the username carried in {@code sub}.
 *       Embedding roles in the token would freeze them at issue time,
 *       so a role demotion (e.g., revoking ADMIN from an account) would
 *       not take effect until the existing token expired. Database-backed
 *       authority resolution is one extra indexed-SELECT per request,
 *       which is acceptable given the table's unique index on
 *       {@code username}.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 *
 * <p>This component is registered as a Spring singleton via {@link Component}
 * and consequently shared across all request-handling threads. Its safety
 * under concurrent invocation rests on three invariants:
 * <ul>
 *   <li>The two configuration fields ({@link #jwtSecret} and
 *       {@link #jwtExpirationMs}) are populated once by Spring's
 *       property-source resolution at startup and are never reassigned.</li>
 *   <li>The cached {@link #signingKey} field is assigned exactly once by
 *       {@link #init()} during {@link PostConstruct @PostConstruct}, before
 *       any request thread can reach {@link #key()}. Spring's container
 *       guarantees the bean is fully initialized before being made available
 *       to consumers, providing the safe-publication semantics required for
 *       lock-free reads from {@code signingKey} on every subsequent
 *       request thread.</li>
 *   <li>The JJWT {@code JwtBuilder} and {@code JwtParser} types returned by
 *       {@link Jwts#builder()} and {@link Jwts#parser()} are themselves
 *       per-call objects; no shared parser is reused across threads.</li>
 * </ul>
 *
 * <h2>Exception strategy</h2>
 *
 * <p>{@link #generateJwtToken(Authentication)} and
 * {@link #getUserNameFromJwtToken(String)} propagate every failure to the
 * caller; these methods are invoked only on tokens that have already been
 * successfully validated, so exception flow is rare. By contrast,
 * {@link #validateJwtToken(String)} catches the five JJWT exception
 * categories enumerated in AAP &sect;0.5.1.4, logs each at ERROR level for
 * server-side diagnosis, and returns {@code false} so the surrounding filter
 * chain can decline the request without short-circuiting the public
 * {@code /api/auth/**} routes.
 *
 * @see UserDetailsImpl
 * @see Jwts
 * @see Keys
 */
@Component
public class JwtUtils {

    /**
     * SLF4J logger used to emit ERROR-level diagnostic messages from the five
     * exception branches of {@link #validateJwtToken(String)}. Declared as
     * {@code private static final} so it is shared by every instance (the
     * class is a Spring singleton, so there is only one) and is initialized
     * exactly once at class loading time. The logger category is the fully
     * qualified class name of {@code JwtUtils}, allowing operators to tune
     * its threshold independently in {@code logback-spring.xml} or via the
     * {@code logging.level} family of Spring properties.
     */
    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    /**
     * Base64-encoded HMAC-SHA-256 secret loaded from the {@code jwt.secret}
     * property at application startup via Spring's
     * {@code PropertySourcesPlaceholderConfigurer}.
     *
     * <p>The Base64 envelope keeps the secret representable as a plain ASCII
     * string in {@code application.properties}, environment variables (e.g.,
     * {@code JWT_SECRET}), and external secret stores. The
     * {@link Decoders#BASE64} decoder inside {@link #key()} reverses the
     * envelope to recover the raw byte array consumed by
     * {@link Keys#hmacShaKeyFor(byte[])}.
     *
     * <p>The decoded byte array <strong>must</strong> be at least 32 bytes
     * (256 bits) long to satisfy RFC 7518 &sect;3.2's minimum-key-size
     * requirement for HS256. {@code Keys.hmacShaKeyFor} enforces this
     * invariant by throwing {@code WeakKeyException} on shorter inputs.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Token lifetime in milliseconds, loaded from the {@code jwt.expiration}
     * property at application startup. The default in
     * {@code application.properties} is {@code 86400000} (24 hours).
     *
     * <p>The {@code int} type is sufficient for any practical access-token
     * lifetime: the maximum positive {@code int} value of approximately
     * {@code 2.1 * 10^9} milliseconds equates to roughly 24.8 days. Tokens
     * live for hours or, at most, days; using {@code long} would needlessly
     * widen the type without enabling a useful range.
     */
    @Value("${jwt.expiration}")
    private int jwtExpirationMs;

    /**
     * The HMAC {@link SecretKey} used by JJWT to sign newly minted tokens
     * and to verify the signature on incoming tokens. The field is assigned
     * exactly once by {@link #init()} during the
     * {@link PostConstruct @PostConstruct} bean-initialization phase &mdash;
     * after Spring has injected {@link #jwtSecret} but before any request
     * thread can call into {@link #generateJwtToken(Authentication)},
     * {@link #validateJwtToken(String)}, or
     * {@link #getUserNameFromJwtToken(String)}.
     *
     * <p>Caching the derived key (rather than re-deriving it on every call)
     * has two benefits:
     * <ol>
     *   <li><strong>Fail-fast at startup.</strong> If
     *       {@link #jwtSecret} is missing, malformed, or too short to satisfy
     *       RFC 7518 &sect;3.2, the failure surfaces at bean creation time
     *       and Spring aborts the application context with a
     *       {@code BeanCreationException} carrying the underlying
     *       {@link WeakKeyException} or
     *       {@link IllegalArgumentException}. Operators discover the
     *       misconfiguration during deployment rather than at the first
     *       login attempt &mdash; this is the AAP &sect;0.7.1.4
     *       fail-fast guarantee.</li>
     *   <li><strong>Hot-path efficiency.</strong> Token operations no longer
     *       perform a Base64 decode plus key wrapping on every request;
     *       only the HMAC computation itself remains.</li>
     * </ol>
     *
     * <p>The field is not {@code volatile} because Spring's container
     * guarantees that bean initialization (including {@code @PostConstruct}
     * methods) happens-before the bean is published to consumers, which is
     * sufficient for safe publication of the immutable
     * {@code SecretKeySpec}-style key reference produced by
     * {@link Keys#hmacShaKeyFor(byte[])}.
     */
    private SecretKey signingKey;

    /**
     * Eagerly derives the HMAC {@link SecretKey} from {@link #jwtSecret} at
     * bean-initialization time and caches it in {@link #signingKey}.
     *
     * <p>{@link Keys#hmacShaKeyFor(byte[])} performs strict length
     * validation: if the decoded byte array is shorter than 32 bytes
     * (256 bits) it throws {@link WeakKeyException}. By invoking
     * {@code Keys.hmacShaKeyFor} from a {@link PostConstruct @PostConstruct}
     * method &mdash; rather than lazily on the first token operation &mdash;
     * the validation runs during Spring's bean-initialization phase, before
     * the embedded Tomcat connector starts accepting traffic. A weak or
     * malformed secret therefore aborts application startup with a
     * {@code BeanCreationException}, satisfying the AAP &sect;0.7.1.4
     * fail-fast contract that QA Issue #2 reported as deferred.
     *
     * <p>The method is package-private (default visibility) so that
     * {@code @PostConstruct} can invoke it via reflection and so that
     * adjacent test code in the same package can re-invoke it after
     * reflectively mutating {@link #jwtSecret} or
     * {@link #jwtExpirationMs}; it is intentionally NOT public to prevent
     * accidental re-initialization from foreign packages.
     *
     * @throws WeakKeyException         if {@code jwtSecret} decodes to fewer
     *                                  than 32 bytes (256 bits)
     * @throws IllegalArgumentException if {@code jwtSecret} is {@code null},
     *                                  empty, or not valid Base64
     */
    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /**
     * Returns the cached HMAC {@link SecretKey} for signing and verifying
     * JWTs.
     *
     * <p>Under normal operation the key is populated by {@link #init()}
     * during {@link PostConstruct @PostConstruct}, so this method is a
     * trivial field read on the request hot path. To keep stand-alone unit
     * tests &mdash; which instantiate {@code JwtUtils} via {@code new
     * JwtUtils()} and reflectively assign {@link #jwtSecret} without going
     * through Spring's bean lifecycle &mdash; correct, the method falls
     * through to a one-time lazy derivation when {@link #signingKey} is
     * still {@code null}. This dual-mode design preserves the AAP
     * &sect;0.7.1.4 fail-fast guarantee for production while keeping the
     * existing test fixtures functional.
     *
     * <p>The method is {@code private} because the {@link SecretKey} is an
     * implementation detail; no caller outside {@code JwtUtils} should be
     * able to retrieve, reuse, or stash the raw key material.
     *
     * @return the cached {@link SecretKey} suitable for HS256 signing and
     *         verification
     * @throws WeakKeyException if {@code jwtSecret} decodes to fewer than
     *         32 bytes (256 bits) AND the field has not yet been
     *         initialized by {@link #init()}
     */
    private SecretKey key() {
        SecretKey local = this.signingKey;
        if (local == null) {
            local = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
            this.signingKey = local;
        }
        return local;
    }

    /**
     * Generates a signed JSON Web Token for the authenticated user.
     *
     * <p>The {@code Authentication} argument is expected to have been
     * produced by {@code AuthenticationManager.authenticate(...)} during the
     * login flow. Its {@code getPrincipal()} value is downcast to
     * {@link UserDetailsImpl} &mdash; the application's only
     * {@code UserDetails} implementation, returned by
     * {@code UserDetailsServiceImpl.loadUserByUsername} &mdash; so the cast is
     * safe by construction.
     *
     * <p>The token is built using the JJWT 0.12+ fluent API:
     * <ul>
     *   <li>{@code .subject(String)} sets the {@code sub} registered claim
     *       to the user's username.</li>
     *   <li>{@code .issuedAt(Date)} sets the {@code iat} registered claim
     *       to the current wall-clock time.</li>
     *   <li>{@code .expiration(Date)} sets the {@code exp} registered claim
     *       to the current time plus {@link #jwtExpirationMs}.</li>
     *   <li>{@code .signWith(SecretKey, Jwts.SIG.HS256)} signs the header
     *       and payload with the cached symmetric key returned by
     *       {@link #key()} and <strong>explicitly</strong> pins the JWS
     *       algorithm header to {@code HS256} per AAP &sect;0.1.1.
     *       Without this explicit pinning, JJWT 0.13's algorithm
     *       auto-detection would pick the strongest secure HMAC variant
     *       supported by the key's byte length (HS384 for &ge; 48 bytes,
     *       HS512 for &ge; 64 bytes), causing the token's
     *       {@code "alg"} header to drift away from the documented HS256
     *       contract whenever an operator configures a longer secret &mdash;
     *       the exact defect QA Issue #3 reported.</li>
     *   <li>{@code .compact()} serializes the resulting JWS to its
     *       three-segment Base64URL string form
     *       ({@code header.payload.signature}).</li>
     * </ul>
     *
     * <p>No custom claims are added: per AAP &sect;0.7.1.4, the token carries
     * only the three registered claims listed above. Any failure inside the
     * builder propagates to the caller (login flow), which translates it
     * through {@code GlobalExceptionHandler} into a 500 error.
     *
     * @param authentication a fully authenticated principal whose
     *                       {@code getPrincipal()} returns a
     *                       {@link UserDetailsImpl}; must be non-{@code null}
     * @return the compact JWS-encoded JWT, ready to be returned to the API
     *         client inside a {@code JwtResponse} or asserted against in
     *         tests
     * @throws ClassCastException if the authentication's principal is not a
     *         {@link UserDetailsImpl} (a programming error indicating a
     *         misconfigured {@code UserDetailsService})
     */
    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extracts the {@code sub} (subject) claim &mdash; i.e., the authenticated
     * user's username &mdash; from a previously validated JWT.
     *
     * <p>The method uses the JJWT 0.12+ parser API:
     * <ul>
     *   <li>{@link Jwts#parser()} returns a {@code JwtParserBuilder}.</li>
     *   <li>{@code .verifyWith(SecretKey)} configures the parser to verify
     *       the HS256 signature using the same key that signed the token.</li>
     *   <li>{@code .build()} freezes the configuration into an immutable
     *       {@code JwtParser}.</li>
     *   <li>{@code .parseSignedClaims(String)} parses the compact JWS,
     *       verifies the signature, and returns a {@code Jws<Claims>}.</li>
     *   <li>{@code .getPayload()} unwraps the {@code Claims} body.</li>
     *   <li>{@code .getSubject()} returns the {@code sub} claim &mdash; the
     *       username.</li>
     * </ul>
     *
     * <p>This method does <strong>not</strong> catch JJWT exceptions; it
     * propagates {@link MalformedJwtException},
     * {@link ExpiredJwtException}, {@link UnsupportedJwtException},
     * {@code io.jsonwebtoken.security.SignatureException}, and
     * {@link IllegalArgumentException} to the caller. In production usage,
     * {@code AuthTokenFilter} always invokes
     * {@link #validateJwtToken(String)} <em>first</em> to gate the request,
     * so by the time this method runs the token has already been proven
     * well-formed, signature-correct, and unexpired. Letting the exceptions
     * propagate from this method preserves their fidelity for any future
     * caller that wants distinct error handling.
     *
     * @param token the compact JWS-encoded JWT, exactly as it appeared in the
     *              {@code Authorization: Bearer <token>} header (after the
     *              prefix has been stripped)
     * @return the username carried in the token's {@code sub} claim
     * @throws MalformedJwtException if the token is not a valid JWS
     * @throws ExpiredJwtException if the token's {@code exp} claim is in the
     *         past
     * @throws UnsupportedJwtException if the token uses a JWT format that
     *         this parser does not support
     * @throws IllegalArgumentException if {@code token} is {@code null},
     *         empty, or whitespace
     * @throws io.jsonwebtoken.security.SignatureException if the HMAC
     *         signature does not verify against the configured key
     */
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Validates the syntactic structure, cryptographic signature, and
     * expiration of a candidate JWT.
     *
     * <p>The method attempts a full parse-and-verify pass via
     * {@code Jwts.parser().verifyWith(key()).build().parseSignedClaims(...)}.
     * If the parse succeeds, the token is accepted and the method returns
     * {@code true}. If the parse fails, the method routes the exception to
     * one of five distinct {@code catch} blocks &mdash; one per exception
     * category enumerated in AAP &sect;0.5.1.4 &mdash; logs a category-specific
     * ERROR-level message via SLF4J, and falls through to the final
     * {@code return false}. The five categories are:
     * <ul>
     *   <li>{@link MalformedJwtException} &mdash; the token is not a
     *       well-formed JWS (e.g., missing dot separators, non-Base64URL
     *       segments, invalid header).</li>
     *   <li>{@link ExpiredJwtException} &mdash; the token's {@code exp}
     *       claim has already elapsed.</li>
     *   <li>{@link UnsupportedJwtException} &mdash; the token uses a
     *       structure or algorithm not supported by this parser
     *       configuration (e.g., unsigned tokens when a key is required).</li>
     *   <li>{@link IllegalArgumentException} &mdash; the input string is
     *       {@code null}, empty, or whitespace-only (typically the result of
     *       an upstream defect that submitted an empty Authorization header
     *       value).</li>
     *   <li>{@code io.jsonwebtoken.security.SignatureException} &mdash; the
     *       HMAC signature does not verify against the configured key,
     *       indicating either tampering or a key mismatch between the
     *       issuing and validating environments.</li>
     * </ul>
     *
     * <p>The {@code io.jsonwebtoken.security.SignatureException} catch clause
     * uses the fully qualified type name on purpose: importing it would
     * shadow {@code java.security.SignatureException}, which is the standard
     * JDK type and could conflict with unrelated code. Spelling out the
     * full type name catches only the JJWT-specific exception without
     * polluting the import section.
     *
     * <p>The method <strong>never</strong> throws: it transforms every
     * possible JJWT failure into a {@code false} return so callers (notably
     * {@code AuthTokenFilter}) can implement the canonical Spring Security
     * idiom of "if the token does not validate, simply do not populate the
     * {@code SecurityContextHolder} and let the downstream filter decide
     * the response." The ERROR-level logs preserve the diagnostic detail
     * server-side without leaking it to the client.
     *
     * @param authToken the candidate JWT to validate; may be {@code null},
     *                  empty, malformed, expired, signed with the wrong key,
     *                  or otherwise invalid
     * @return {@code true} iff the token parses cleanly with a verifying
     *         signature and a future {@code exp} claim; {@code false}
     *         otherwise
     */
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        }
        return false;
    }
}
