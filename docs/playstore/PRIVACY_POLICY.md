# Privacy Policy for RupeeFlow

**Last Updated:** November 2026  
**Effective Date:** November 2026  
**Contact:** privacy@rupeeflow.app

RupeeFlow ("we", "our", or "the application") is committed to protecting user privacy. This Privacy Policy outlines how your information is handled when using RupeeFlow.

---

### 1. Zero Cloud Transmission & Offline Operation

RupeeFlow is a **local-first, 100% offline personal finance application**.
- We do not operate remote servers, user databases, or cloud accounts.
- Your financial transactions, friends, groups, settlements, and preferences are stored exclusively on your device within an isolated SQLite database.
- We do not transmit, sell, rent, or share your data with any third party.

---

### 2. Permissions & Data Access

RupeeFlow requests only permissions strictly necessary for its features, with full user consent:

#### A. SMS Detection (`android.permission.RECEIVE_SMS`) — Optional
- **Purpose:** Automatically detect incoming transaction alerts from financial institutions to save manual entry time.
- **Scope:** Processes only incoming SMS messages. RupeeFlow never reads your stored inbox (`READ_SMS` is not requested).
- **Processing:** Raw SMS content is parsed locally on your device via regular expressions. Sensitive tokens (OTPs, personal notes) are sanitized.
- **Opt-in:** Disabled by default. Enabled only after user review of the Prominent Disclosure dialog in Settings.

#### B. Notification Listener Service — Optional
- **Purpose:** Detect UPI and banking notifications from payment applications.
- **Processing:** Processed purely on-device to extract transaction amounts and merchants.

#### C. Biometric Authentication (`android.permission.USE_BIOMETRIC`) — Optional
- **Purpose:** Protect access to the application using Android's BiometricPrompt API.
- **Processing:** Handled entirely by the Android OS hardware security module. Biometric data is never accessible to the app.

---

### 3. Encrypted Backups

RupeeFlow allows users to export and import backups:
- Encrypted backups are protected using **PBKDF2 with HMAC-SHA256** (100,000 iterations) and **AES-256-GCM** authenticated encryption.
- Backup files are saved only to locations explicitly selected by the user via the Android Storage Access Framework (SAF).

---

### 4. Children's Privacy

RupeeFlow does not collect personal data from any user, including children under the age of 13.

---

### 5. Changes to This Privacy Policy

If any modifications are made to this policy, the updated policy will be posted with a revised effective date.

---

### 6. Contact Information

For any questions regarding this Privacy Policy, please contact:
Email: `privacy@rupeeflow.app`
Repository: `https://github.com/TradeGuard-live/RupeeFlow`

