# CarbonTrack
CarbonTrack - Environmental Impact Tracking and Sustainability Analytics Platform

## Password recovery

Password recovery uses `POST /api/auth/password-reset/request` and
`POST /api/auth/password-reset/confirm`. Request responses are generic whether
or not the supplied email belongs to an account. Reset tokens are
cryptographically random, single-use, expire after 30 minutes by default, and
are stored only as SHA-256 hashes. Redis limits reset requests to three per
email address and ten per client IP in a 15-minute window.

### Local development

With PostgreSQL and Redis running, explicitly activate the `dev` profile and
start the backend. When SMTP is not configured, the backend prints the reset
URL to its console only in this profile.

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
Set-Location C:\Projects\CarbonTrack\backend
.\mvnw.cmd spring-boot:run
```

The `dev` profile is not enabled by default. When launching from IntelliJ,
set the run configuration's active profile to `dev` (or add
`SPRING_PROFILES_ACTIVE=dev` to that configuration's environment variables)
and restart the backend. The checked-in process on port 8080 was started
without an explicit profile argument, which is why its console notifier was
inactive.

In another terminal, start the frontend:

```powershell
cd frontend
npm run dev
```

Then test the complete flow:

1. Open CarbonTrack and select **Sign in**, then **Forgot password?**.
2. Enter the registered email address and submit the recovery request.
3. Copy the `Development password reset link` printed in the backend console
   and open it in the browser. The URL token is captured and removed from the
   address bar.
4. Enter and confirm a password with at least eight characters, an uppercase
   letter, a lowercase letter, a number, and a symbol.
5. After the success confirmation, return to sign in and authenticate with the
   new password.
6. Submit the same reset link again to verify it cannot be reused.

### SMTP and production configuration

Set these environment variables when sending reset links by email:

| Variable | Purpose |
| --- | --- |
| `SMTP_HOST` | SMTP server hostname |
| `SMTP_PORT` | SMTP server port (defaults to `587`) |
| `SMTP_USERNAME` | SMTP account username |
| `SMTP_PASSWORD` | SMTP account password or app password |
| `SMTP_FROM` | Verified sender email address |
| `CARBONTRACK_FRONTEND_URL` | Frontend origin used in recovery links (defaults to `http://localhost:5173`) |
| `PASSWORD_RESET_TOKEN_TTL` | Token lifetime as a Spring duration (defaults to `PT30M`) |

In the `dev` profile, a console reset link is a development fallback, not an
email delivery attempt. To send email locally, configure all five SMTP
variables above and restart the backend. If the backend logs an SMTP
authentication failure, verify the account name and password with the mail
provider; providers with multi-factor authentication commonly require an
application-specific password. Do not put credentials in source files or
include them in diagnostic logs.

Activate the `prod` Spring profile in production. Startup fails unless all
required SMTP settings are present. Reset links and tokens are never logged in
production; the console fallback is available only when the explicit `dev`
profile is active and SMTP is absent. Password-reset requests require the
existing PostgreSQL and Redis services.

Request a reset:

```http
POST /api/auth/password-reset/request
Content-Type: application/json

{"email":"person@example.com"}
```

Confirm a reset:

```http
POST /api/auth/password-reset/confirm
Content-Type: application/json

{"token":"<token-from-email>","newPassword":"A new secure password1!"}
```

Both endpoints are public and intentionally omit bearer authentication. Reset
requests do not alter login, registration, JWT, or existing user data.
For account safety, registration, login, password changes, and password resets
reject a password that is the same as the account username (case-insensitive).
