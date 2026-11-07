package com.naman.rupeeflow.voice.execution

import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendBalanceStatus
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.voice.model.VoiceLanguage
import kotlin.math.abs

/**
 * Localized response generator for Voice Assistant across 4 languages:
 * English (en-IN), Hindi (hi-IN), Gujarati (gu-IN), and Hinglish.
 */
object VoiceResponseGenerator {

    fun formatCurrencyDisplay(paise: Long): String {
        return CurrencyUtils.formatPaiseToRupees(paise)
    }

    fun formatCurrencySpoken(paise: Long, language: VoiceLanguage): String {
        val rupees = paise / 100
        val remainingPaise = abs(paise % 100)
        val numStr = if (remainingPaise == 0L) {
            rupees.toString()
        } else {
            "$rupees.$remainingPaise"
        }

        return when (language) {
            VoiceLanguage.ENGLISH -> "$numStr rupees"
            VoiceLanguage.HINDI -> "$numStr रुपये"
            VoiceLanguage.GUJARATI -> "$numStr રૂપિયા"
            VoiceLanguage.HINGLISH -> "$numStr rupaye"
        }
    }

    fun transactionConfirmationPrompt(
        transaction: Transaction,
        language: VoiceLanguage
    ): Pair<String, String> {
        val amountDisplay = formatCurrencyDisplay(transaction.amountPaise)
        val amountSpoken = formatCurrencySpoken(transaction.amountPaise, language)
        val title = transaction.title

        val (text, spoken) = when (language) {
            VoiceLanguage.ENGLISH -> {
                "Save transaction of $amountDisplay for \"$title\"?" to
                        "Should I save transaction of $amountSpoken for $title?"
            }
            VoiceLanguage.HINDI -> {
                "क्या \"$title\" के लिए $amountDisplay का लेन-देन सेव करें?" to
                        "Kya $title ke liye $amountSpoken ka transaction save karein?"
            }
            VoiceLanguage.GUJARATI -> {
                "શું \"$title\" માટે $amountDisplay નો વ્યવહાર સેવ કરવો છે?" to
                        "Shu $title mate $amountSpoken no transaction save karvo che?"
            }
            VoiceLanguage.HINGLISH -> {
                "\"$title\" ke liye $amountDisplay ka transaction save karein?" to
                        "$title ke liye $amountSpoken ka transaction save karein?"
            }
        }
        return text to spoken
    }

    fun settlementConfirmationPrompt(
        settlement: Settlement,
        friendName: String,
        language: VoiceLanguage
    ): Pair<String, String> {
        val amountDisplay = formatCurrencyDisplay(settlement.amountPaise)
        val amountSpoken = formatCurrencySpoken(settlement.amountPaise, language)
        val isReceived = settlement.receiverId == null // Friend paid me

        val (text, spoken) = when (language) {
            VoiceLanguage.ENGLISH -> {
                if (isReceived) {
                    "Record $amountDisplay received from $friendName?" to
                            "Record $amountSpoken received from $friendName?"
                } else {
                    "Record $amountDisplay paid to $friendName?" to
                            "Record $amountSpoken paid to $friendName?"
                }
            }
            VoiceLanguage.HINDI -> {
                if (isReceived) {
                    "$friendName से $amountDisplay प्राप्त हुए रिकॉर्ड करें?" to
                            "$friendName se $amountSpoken praapt hue record karein?"
                } else {
                    "$friendName को $amountDisplay दिए रिकॉर्ड करें?" to
                            "$friendName ko $amountSpoken diye record karein?"
                }
            }
            VoiceLanguage.GUJARATI -> {
                if (isReceived) {
                    "$friendName પાસેથી $amountDisplay મળ્યા નોંધવું છે?" to
                            "$friendName paasethi $amountSpoken malya nondhvu che?"
                } else {
                    "$friendName ને $amountDisplay ચૂકવ્યા નોંધવું છે?" to
                            "$friendName ne $amountSpoken chukavya nondhvu che?"
                }
            }
            VoiceLanguage.HINGLISH -> {
                if (isReceived) {
                    "$friendName se $amountDisplay receive hua record karein?" to
                            "$friendName se $amountSpoken receive hua record karein?"
                } else {
                    "$friendName ko $amountDisplay pay kiya record karein?" to
                            "$friendName ko $amountSpoken pay kiya record karein?"
                }
            }
        }
        return text to spoken
    }

    fun deleteTransactionPrompt(
        transaction: Transaction,
        language: VoiceLanguage
    ): Pair<String, String> {
        val amountDisplay = formatCurrencyDisplay(transaction.amountPaise)
        val amountSpoken = formatCurrencySpoken(transaction.amountPaise, language)
        val title = transaction.title

        return when (language) {
            VoiceLanguage.ENGLISH -> {
                "Delete transaction \"$title\" of $amountDisplay?" to
                        "Are you sure you want to delete $title of $amountSpoken?"
            }
            VoiceLanguage.HINDI -> {
                "क्या $amountDisplay का लेन-देन \"$title\" डिलीट करें?" to
                        "Kya $title ka $amountSpoken transaction delete karein?"
            }
            VoiceLanguage.GUJARATI -> {
                "શું \"$title\" નો $amountDisplay નો વ્યવહાર ડિલીટ કરવો છે?" to
                        "Shu $title no $amountSpoken no transaction delete karvo che?"
            }
            VoiceLanguage.HINGLISH -> {
                "$amountDisplay ka transaction \"$title\" delete karein?" to
                        "$title ka $amountSpoken transaction delete karein?"
            }
        }
    }

    fun reverseSettlementPrompt(
        settlement: Settlement,
        friendName: String,
        language: VoiceLanguage
    ): Pair<String, String> {
        val amountDisplay = formatCurrencyDisplay(settlement.amountPaise)
        val amountSpoken = formatCurrencySpoken(settlement.amountPaise, language)

        return when (language) {
            VoiceLanguage.ENGLISH -> {
                "Reverse settlement of $amountDisplay with $friendName?" to
                        "Do you want to reverse the settlement of $amountSpoken with $friendName?"
            }
            VoiceLanguage.HINDI -> {
                "क्या $friendName के साथ $amountDisplay का सेटलमेंट रिवर्स करें?" to
                        "Kya $friendName ke sath $amountSpoken ka settlement reverse karein?"
            }
            VoiceLanguage.GUJARATI -> {
                "શું $friendName સાથેનો $amountDisplay નો સેટલમેન્ટ રિવર્સ કરવો છે?" to
                        "Shu $friendName satheno $amountSpoken no settlement reverse karvo che?"
            }
            VoiceLanguage.HINGLISH -> {
                "$friendName ke sath $amountDisplay ka settlement reverse karein?" to
                        "$friendName ke sath $amountSpoken ka settlement reverse karein?"
            }
        }
    }

    fun transactionSavedSuccess(
        transaction: Transaction,
        language: VoiceLanguage
    ): Pair<String, String> {
        val amountDisplay = formatCurrencyDisplay(transaction.amountPaise)
        val amountSpoken = formatCurrencySpoken(transaction.amountPaise, language)
        val title = transaction.title

        return when (language) {
            VoiceLanguage.ENGLISH -> {
                "Saved $amountDisplay for \"$title\"." to "Saved $amountSpoken for $title."
            }
            VoiceLanguage.HINDI -> {
                "\"$title\" के लिए $amountDisplay सेव कर लिया गया है।" to "$title ke liye $amountSpoken save kar liya gaya hai."
            }
            VoiceLanguage.GUJARATI -> {
                "\"$title\" માટે $amountDisplay સેવ થઈ ગયું છે." to "$title mate $amountSpoken save thai gayu che."
            }
            VoiceLanguage.HINGLISH -> {
                "\"$title\" ke liye $amountDisplay save ho gaya." to "$title ke liye $amountSpoken save ho gaya."
            }
        }
    }

    fun settlementSavedSuccess(
        settlement: Settlement,
        friendName: String,
        language: VoiceLanguage
    ): Pair<String, String> {
        val amountDisplay = formatCurrencyDisplay(settlement.amountPaise)
        val amountSpoken = formatCurrencySpoken(settlement.amountPaise, language)

        return when (language) {
            VoiceLanguage.ENGLISH -> {
                "Settlement of $amountDisplay with $friendName recorded." to
                        "Settlement of $amountSpoken with $friendName recorded."
            }
            VoiceLanguage.HINDI -> {
                "$friendName के साथ $amountDisplay का सेटलमेंट दर्ज हो गया है।" to
                        "$friendName ke sath $amountSpoken ka settlement darj ho gaya hai."
            }
            VoiceLanguage.GUJARATI -> {
                "$friendName સાથે $amountDisplay નો સેટલમેન્ટ નોંધાઈ ગયો છે." to
                        "$friendName sathe $amountSpoken no settlement nondhai gayo che."
            }
            VoiceLanguage.HINGLISH -> {
                "$friendName ke sath $amountDisplay ka settlement record ho gaya." to
                        "$friendName ke sath $amountSpoken ka settlement record ho gaya."
            }
        }
    }

    fun transactionDeletedSuccess(
        title: String,
        language: VoiceLanguage
    ): Pair<String, String> {
        return when (language) {
            VoiceLanguage.ENGLISH -> "Transaction \"$title\" deleted." to "Transaction $title deleted."
            VoiceLanguage.HINDI -> "लेन-देन \"$title\" डिलीट कर दिया गया है।" to "Transaction $title delete kar diya gaya hai."
            VoiceLanguage.GUJARATI -> "વ્યવહાર \"$title\" ડિલીટ કરી દેવામાં આવ્યો છે." to "Transaction $title delete kari devama aavyo che."
            VoiceLanguage.HINGLISH -> "Transaction \"$title\" delete kar diya gaya." to "Transaction $title delete ho gaya."
        }
    }

    fun settlementReversedSuccess(
        friendName: String,
        amountPaise: Long,
        language: VoiceLanguage
    ): Pair<String, String> {
        val amountDisplay = formatCurrencyDisplay(amountPaise)
        val amountSpoken = formatCurrencySpoken(amountPaise, language)

        return when (language) {
            VoiceLanguage.ENGLISH -> {
                "Settlement of $amountDisplay with $friendName reversed." to
                        "Settlement of $amountSpoken with $friendName reversed."
            }
            VoiceLanguage.HINDI -> {
                "$friendName के साथ $amountDisplay का सेटलमेंट रिवर्स कर दिया गया है।" to
                        "$friendName ke sath $amountSpoken ka settlement reverse kar diya gaya hai."
            }
            VoiceLanguage.GUJARATI -> {
                "$friendName સાથે $amountDisplay નો સેટલમેન્ટ રિવર્સ થઈ ગયો છે." to
                        "$friendName sathe $amountSpoken no settlement reverse thai gayo che."
            }
            VoiceLanguage.HINGLISH -> {
                "$friendName ke sath $amountDisplay ka settlement reverse ho gaya." to
                        "$friendName ke sath $amountSpoken ka settlement reverse ho gaya."
            }
        }
    }

    fun friendBalanceResponse(
        friend: Friend,
        language: VoiceLanguage
    ): Pair<String, String> {
        val friendName = friend.name
        val net = friend.netBalancePaise
        val absNet = abs(net)
        val amountDisplay = formatCurrencyDisplay(absNet)
        val amountSpoken = formatCurrencySpoken(absNet, language)

        return when (friend.status) {
            FriendBalanceStatus.THEY_OWE_ME -> {
                when (language) {
                    VoiceLanguage.ENGLISH -> {
                        "$friendName owes you $amountDisplay." to "$friendName owes you $amountSpoken."
                    }
                    VoiceLanguage.HINDI -> {
                        "$friendName से आपको $amountDisplay लेने हैं।" to "$friendName se aapko $amountSpoken lene hain."
                    }
                    VoiceLanguage.GUJARATI -> {
                        "$friendName પાસેથી તમારે $amountDisplay લેવાના છે." to "$friendName paasethi tamare $amountSpoken levana che."
                    }
                    VoiceLanguage.HINGLISH -> {
                        "$friendName owes you $amountDisplay." to "$friendName se aapko $amountSpoken lene hain."
                    }
                }
            }
            FriendBalanceStatus.I_OWE_THEM -> {
                when (language) {
                    VoiceLanguage.ENGLISH -> {
                        "You owe $friendName $amountDisplay." to "You owe $friendName $amountSpoken."
                    }
                    VoiceLanguage.HINDI -> {
                        "आपको $friendName को $amountDisplay देने हैं।" to "Aapko $friendName ko $amountSpoken dene hain."
                    }
                    VoiceLanguage.GUJARATI -> {
                        "તમારે $friendName ને $amountDisplay ચૂકવવાના છે." to "Tamare $friendName ne $amountSpoken chukavvana che."
                    }
                    VoiceLanguage.HINGLISH -> {
                        "You owe $friendName $amountDisplay." to "Aapko $friendName ko $amountSpoken dene hain."
                    }
                }
            }
            FriendBalanceStatus.SETTLED -> {
                when (language) {
                    VoiceLanguage.ENGLISH -> {
                        "You and $friendName are all settled up (₹0 balance)." to "You and $friendName are all settled up."
                    }
                    VoiceLanguage.HINDI -> {
                        "$friendName के साथ हिसाब चुकता है (₹0 बैलेंस)।" to "$friendName ke sath hisaab chukta hai."
                    }
                    VoiceLanguage.GUJARATI -> {
                        "$friendName સાથે હિસાબ ચૂકતે છે (₹0 બેલેન્સ)." to "$friendName sathe hisaab chukte che."
                    }
                    VoiceLanguage.HINGLISH -> {
                        "$friendName ke sath hisaab barabar hai (₹0 balance)." to "$friendName ke sath hisaab barabar hai."
                    }
                }
            }
        }
    }

    fun spendingSummaryResponse(
        scopeTitle: String,
        totalPaise: Long,
        categoryBreakdown: Map<String, Long> = emptyMap(),
        language: VoiceLanguage
    ): Pair<String, String> {
        val amountDisplay = formatCurrencyDisplay(totalPaise)
        val amountSpoken = formatCurrencySpoken(totalPaise, language)

        val (mainText, mainSpoken) = when (language) {
            VoiceLanguage.ENGLISH -> {
                "Total spending for $scopeTitle: $amountDisplay." to "You spent $amountSpoken for $scopeTitle."
            }
            VoiceLanguage.HINDI -> {
                "$scopeTitle का कुल खर्च: $amountDisplay।" to "$scopeTitle me kul kharch $amountSpoken hua."
            }
            VoiceLanguage.GUJARATI -> {
                "$scopeTitle નો કુલ ખર્ચ: $amountDisplay." to "$scopeTitle ma kul kharch $amountSpoken thayo."
            }
            VoiceLanguage.HINGLISH -> {
                "$scopeTitle me total spend: $amountDisplay." to "$scopeTitle me total kharch $amountSpoken hua."
            }
        }

        return mainText to mainSpoken
    }
}
