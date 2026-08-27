# Security Architecture & Policies

## 1. Architectural Overview
This application utilizes a **Dynamic Permission Set Authorization Engine**, inspired by enterprise ERP systems like IFS Cloud. It abandons static, hardcoded role annotations in favor of a 100% database-driven security model.

Access control is evaluated dynamically at runtime, allowing administrators to map users to permission sets, and permission sets to specific API projections (endpoints) without requiring code deployments.

---

## 2. Authentication (AuthN)
The application uses a stateless, **Dual-Token Pattern (JWT + Opaque Token)** to ensure high security without compromising mobile or web user experience.

*   **Passwords:** Hashed using **BCrypt** before database persistence. Raw passwords are never stored or logged.
*   **Access Tokens:** Short-lived JSON Web Tokens (JWT) valid for **15 minutes**. Signed via HMAC-SHA256. They are strictly stateless and validated in memory.
*   **Refresh Tokens:** Long-lived opaque strings (UUIDs) valid for **60 days**.
    *   **Storage:** Securely stored in **Redis** (`refresh_tokens:{username}`).
    *   **Rotation:** Every time a client refreshes their access token, the old refresh token is revoked and a new one is issued (Token Rotation).
    *   **Revocation:** Logging out instantly deletes the refresh token from Redis, severing the client's ability to renew sessions.

---

## 3. Authorization (AuthZ)
Authorization is handled by a custom Spring Security 6 `AuthorizationManager` that intercepts all HTTP requests.

### API Auto-Discovery
On application startup, the `EndpointDiscoveryListener` scans the Spring Context for all `@RestController` mappings. It extracts the HTTP Method and URL Path (e.g., `GET:/api/users/{id}`) and synchronizes them into the PostgreSQL `projections` table.

### Dynamic Permission Evaluation
1.  A request enters the `SecurityFilterChain`.
2.  The `JwtAuthenticationFilter` extracts the user's identity from the JWT.
3.  The `DynamicPermissionSetManager` intercepts the request.
4.  It queries the assigned `PermissionSets` for that specific user and HTTP method.
5.  It uses Spring's `PathPatternParser` to match the incoming URI against the allowed patterns.

### Redis Caching Layer (Performance)
To prevent the authorization engine from bottlenecking the PostgreSQL database with complex `JOIN` queries on every API call, permission evaluations are cached in **Redis**.
*   **Key:** `userPermissions::{username}_{httpMethod}`
*   **TTL:** 60 Minutes.
*   **Eviction:** Upon user logout or admin modification, the Redis cache is aggressively evicted to ensure immediate security state consistency.

---

## 4. Threat Mitigation Strategies
| Threat | Mitigation |
| :--- | :--- |
| **Stolen Access Token** | Limited blast radius due to 15-minute expiration time. |
| **Stolen Refresh Token** | Token Rotation ensures a stolen token can only be used once before the server detects a replay anomaly and revokes the session. |
| **Database Overload (DoS)** | All permission checks are served from Redis memory (microseconds) after the first cache miss. |
| **Unauthorized API Access** | APIs are locked down by default. Endpoints must be explicitly granted via a Permission Set. |

## 5. Frontend Security & Integration Guide

The frontend (Next.js Web / React Native / Flutter) must adhere to strict token management and state reconciliation rules to maintain the integrity of the backend security model.

### 5.1 Token Storage Strategy
**Never store JWTs or Refresh Tokens in `localStorage` or `sessionStorage`.** These storage mechanisms are synchronous and fully accessible to malicious JavaScript (XSS attacks).

#### For Web (Next.js / React)
*   **Refresh Token:** Should be stored in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie. This prevents JavaScript from reading it while ensuring the browser sends it automatically to the `/refresh` endpoint.
*   **Access Token:** Should be stored strictly in **Application Memory** (e.g., a React Context or a Redux store variable). If the user refreshes the page, the in-memory token is lost, and the app silently calls the `/refresh` endpoint on load to get a new one.

#### For Mobile (iOS / Android)
*   **Refresh Token:** Must be stored in the device's secure enclave using libraries like:
    *   *React Native:* `react-native-keychain`
    *   *Flutter:* `flutter_secure_storage`
*   **Access Token:** Stored in Application Memory.

### 5.2 The "Silent Refresh" Interceptor
To ensure users are not logged out every 15 minutes when the Access Token expires, the frontend HTTP client (e.g., Axios or Dio) must implement an interceptor:

1.  **Intercept 401s:** Catch any `401 Unauthorized` responses from the backend.
2.  **Pause Traffic:** Pause all outgoing API requests (queue them).
3.  **Refresh:** Call `/api/auth/refresh` using the securely stored Refresh Token.
4.  **Update & Retry:** If successful, update the in-memory Access Token, attach the new token to the paused requests, and retry them.
5.  **Hard Logout:** If the `/refresh` call *also* returns a `401` (meaning the refresh token expired or was revoked by the server), wipe the frontend state and redirect the user to the login screen.

### 5.3 UI State vs. Backend Truth
The frontend should conditionally render UI elements (like hiding the "Delete User" button) based on the user's known permissions to provide good UX.

**However, Frontend UI hiding is NOT security.**
A malicious user can easily alter compiled JavaScript to un-hide a button or trigger a network request manually. The Spring Boot backend `DynamicPermissionSetManager` remains the absolute source of truth and will reject unauthorized requests regardless of what the frontend UI allows.

### 5.4 XSS & CSRF Protections
*   **XSS:** Rely on Next.js/React's default DOM escaping. Strictly avoid `dangerouslySetInnerHTML` unless rendering heavily sanitized HTML (via libraries like DOMPurify).
*   **CSRF:** Because the primary API relies on `Authorization: Bearer <token>` headers rather than session cookies, the API is inherently resistant to Cross-Site Request Forgery (CSRF).