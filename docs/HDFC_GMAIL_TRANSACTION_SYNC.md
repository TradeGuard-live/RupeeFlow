# HDFC Gmail transaction sync

## Implementation
- Explicit Gmail read-only OAuth consent flow from Settings.
- Unique WorkManager polling job (15-minute minimum interval) for recent HDFC emails.
- Sender allowlist and reuse of the existing HDFC parser.
- Durable Room idempotency keys shared by SMS, banking notifications and Gmail.
- Gmail message IDs are separately claimed to make repeated polling idempotent.

## Google Cloud configuration required
1. Enable Gmail API in a Google Cloud project.
2. Configure OAuth consent screen and application identity/privacy policy.
3. Add the Gmail `gmail.readonly` scope.
4. Configure Android OAuth client for package `com.naman.rupeeflow` and debug/release signing SHA-1 fingerprints.
5. Complete Google's verification requirements before public distribution; Gmail read-only is a restricted scope.
6. Build and open Settings → SMS & Notification Detection → Connect HDFC Transaction Emails; complete account selection and consent.

The selected Gmail address is stored locally. RupeeFlow does not store the Gmail password or raw refresh token; Google account credential services manage authorization.

## Deduplication
A normalized reference/UTR is claimed with a unique Room primary key in the same database transaction as the pending-row insert. Only one of SMS, app notification or email can win for the same reference, even when events race. Email message IDs are separately claimed. Existing contextual duplicate checks remain as fallback for older records and messages without references.

## Operational limitations
WorkManager periodic jobs have a minimum 15-minute interval and Android may defer execution for battery/network policy. This is background polling, not instant push. The initial implementation scans the last seven days (up to 100 messages each run). Gmail API quotas and sender-format variations apply.
