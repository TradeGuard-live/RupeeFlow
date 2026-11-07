# Google Play SMS and Call Log Permission Declaration

**App Name:** RupeeFlow  
**Package Name:** `com.naman.rupeeflow`  
**Requested Sensitive Permission:** `android.permission.RECEIVE_SMS`  
**Declaration Category:** Financial Management / Expense Tracking

---

## 1. Core Feature Justification

RupeeFlow provides automated transaction detection from bank SMS notifications. When users make payments via UPI, debit cards, or net banking, Indian financial institutions dispatch SMS notifications containing transaction details (amount, bank, merchant, reference number).

`android.permission.RECEIVE_SMS` allows RupeeFlow to intercept incoming financial notifications in real time, extract transaction parameters through local regex patterns, and populate pending transactions for user review without requiring repetitive manual entry.

## 2. In-App User Journey & Prominent Disclosure

1. **No App-Launch Prompts:** RupeeFlow does NOT request SMS permissions upon initial launch or setup.
2. **Settings Opt-In:** SMS detection is completely optional and disabled by default.
3. **Prominent Disclosure Dialog:** When the user taps "Enable SMS Auto-Detection" in Settings, an explicit modal disclosure dialog appears before requesting the runtime permission:
   - Discloses exactly what is accessed (only incoming financial SMS alerts).
   - Clarifies that processing is 100% on-device.
   - States that personal messages, OTPs, and private text are never recorded, transmitted, or shared.
4. **Independent Fallback:** If permission is denied or revoked, all core features (manual expense recording, bill splitting, friend groups, ledger calculations, encrypted backup/restore) remain fully functional.

## 3. Data Privacy & Minimization

- **No READ_SMS:** RupeeFlow does not request `android.permission.READ_SMS`. It never scans user inbox history.
- **Hardware Feature Compatibility:** Declared `<uses-feature android:name="android.hardware.telephony" android:required="false" />` to support non-telephony devices.
- **Zero Remote Transmission:** RupeeFlow contains no analytics SDKs, no ad SDKs, and no network transmission code. All parsed transactions remain within the application's encrypted local SQLite database.

