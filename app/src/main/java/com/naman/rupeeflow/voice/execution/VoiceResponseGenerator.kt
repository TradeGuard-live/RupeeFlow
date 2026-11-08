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

    fun friendConfirmationPrompt(
        friend: Friend,
        isDelete: Boolean,
        language: VoiceLanguage
    ): Pair<String, String> {
        val name = friend.name
        val phoneSuffix = if (!friend.phoneNumber.isNullOrBlank()) " (${friend.phoneNumber})" else ""
        return if (isDelete) {
            when (language) {
                VoiceLanguage.ENGLISH -> "Are you sure you want to delete friend $name?" to "Are you sure you want to delete friend $name?"
                VoiceLanguage.HINDI -> "क्या आप दोस्त $name को हटाना चाहते हैं?" to "Kya aap dost $name ko hatana chahte hain?"
                VoiceLanguage.GUJARATI -> "શું તમે મિત્ર $name ને યાદીમાંથી કાઢી નાખવા માંગો છો?" to "Shu tame mitra $name ne kaadhi naakhva maango cho?"
                VoiceLanguage.HINGLISH -> "Kya aap dost $name ko delete karna chahte hain?" to "Kya aap dost $name ko delete karna chahte hain?"
            }
        } else {
            when (language) {
                VoiceLanguage.ENGLISH -> "Add $name$phoneSuffix to Friends?" to "Add $name to Friends?"
                VoiceLanguage.HINDI -> "क्या $name$phoneSuffix को दोस्त जोड़ें?" to "Kya $name ko dost ke roop me jodein?"
                VoiceLanguage.GUJARATI -> "શું $name$phoneSuffix ને મિત્ર તરીકે ઉમેરવા છે?" to "Shu $name ne mitra tarike umerva che?"
                VoiceLanguage.HINGLISH -> "$name$phoneSuffix ko friend list me add karein?" to "$name ko friend list me add karein?"
            }
        }
    }

    fun friendSavedSuccess(friend: Friend, isEdit: Boolean, language: VoiceLanguage): Pair<String, String> {
        val name = friend.name
        return when (language) {
            VoiceLanguage.ENGLISH -> "Friend $name ${if (isEdit) "updated" else "added"} successfully." to "Friend $name ${if (isEdit) "updated" else "added"} successfully."
            VoiceLanguage.HINDI -> "दोस्त $name को ${if (isEdit) "अपडेट" else "जोड़"} दिया गया है।" to "Dost $name ko jod diya gaya hai."
            VoiceLanguage.GUJARATI -> "$name ${if (isEdit) "અપડેટ" else "મિત્ર તરીકે ઉમેરાઈ"} ગયા છે." to "$name mitra tarike umerai gaya che."
            VoiceLanguage.HINGLISH -> "Friend $name ${if (isEdit) "update" else "add"} ho gaya." to "Friend $name add ho gaya."
        }
    }

    fun friendDeletedSuccess(friendName: String, language: VoiceLanguage): Pair<String, String> {
        return when (language) {
            VoiceLanguage.ENGLISH -> "Friend $friendName removed." to "Friend $friendName removed."
            VoiceLanguage.HINDI -> "दोस्त $friendName को हटा दिया गया है।" to "Dost $friendName ko hata diya gaya hai."
            VoiceLanguage.GUJARATI -> "$friendName ને મિત્રોની યાદીમાંથી કાઢી નાખવામાં આવ્યા છે." to "$friendName ne kaadhi naakhvama aavya che."
            VoiceLanguage.HINGLISH -> "Friend $friendName delete ho gaya." to "Friend $friendName delete ho gaya."
        }
    }

    fun groupConfirmationPrompt(
        groupName: String,
        isDelete: Boolean,
        isAddMember: Boolean,
        isRemoveMember: Boolean,
        targetMembers: List<String>,
        language: VoiceLanguage
    ): Pair<String, String> {
        val membersStr = targetMembers.joinToString(", ")
        return when {
            isDelete -> when (language) {
                VoiceLanguage.ENGLISH -> "Are you sure you want to delete group \"$groupName\"?" to "Are you sure you want to delete group $groupName?"
                VoiceLanguage.HINDI -> "क्या आप \"$groupName\" ग्रुप को हटाना चाहते हैं?" to "Kya aap $groupName group ko hatana chahte hain?"
                VoiceLanguage.GUJARATI -> "શું તમે \"$groupName\" ગ્રૂપ ડિલીટ કરવા માંગો છો?" to "Shu tame $groupName grup delete karva maango cho?"
                VoiceLanguage.HINGLISH -> "Kya aap group $groupName ko delete karna chahte hain?" to "Kya aap group $groupName ko delete karna chahte hain?"
            }
            isAddMember -> when (language) {
                VoiceLanguage.ENGLISH -> "Add $membersStr to group \"$groupName\"?" to "Add $membersStr to group $groupName?"
                VoiceLanguage.HINDI -> "क्या $membersStr को \"$groupName\" ग्रुप में जोड़ें?" to "Kya $membersStr ko $groupName group me jodein?"
                VoiceLanguage.GUJARATI -> "શું $membersStr ને \"$groupName\" ગ્રૂપમાં ઉમેરવા છે?" to "Shu $membersStr ne $groupName grup ma umerva che?"
                VoiceLanguage.HINGLISH -> "Kya $membersStr ko group $groupName me add karein?" to "Kya $membersStr ko group $groupName me add karein?"
            }
            isRemoveMember -> when (language) {
                VoiceLanguage.ENGLISH -> "Remove $membersStr from group \"$groupName\"?" to "Remove $membersStr from group $groupName?"
                VoiceLanguage.HINDI -> "क्या $membersStr को \"$groupName\" ग्रुप से हटाएं?" to "Kya $membersStr ko $groupName group se hatayein?"
                VoiceLanguage.GUJARATI -> "શું $membersStr ને \"$groupName\" ગ્રૂપમાંથી કાઢવા છે?" to "Shu $membersStr ne $groupName grup mathi kaadhva che?"
                VoiceLanguage.HINGLISH -> "Kya $membersStr ko group $groupName se remove karein?" to "Kya $membersStr ko group $groupName se remove karein?"
            }
            else -> when (language) {
                VoiceLanguage.ENGLISH -> "Create group \"$groupName\"?" to "Create group $groupName?"
                VoiceLanguage.HINDI -> "क्या \"$groupName\" नाम का ग्रुप बनाएं?" to "Kya $groupName group banayein?"
                VoiceLanguage.GUJARATI -> "શું \"$groupName\" ગ્રૂપ બનાવવું છે?" to "Shu $groupName grup banavvu che?"
                VoiceLanguage.HINGLISH -> "Kya group \"$groupName\" create karein?" to "Kya group $groupName create karein?"
            }
        }
    }

    fun groupSavedSuccess(groupName: String, language: VoiceLanguage): Pair<String, String> {
        return when (language) {
            VoiceLanguage.ENGLISH -> "Group \"$groupName\" created successfully." to "Group $groupName created successfully."
            VoiceLanguage.HINDI -> "\"$groupName\" ग्रुप बन गया है।" to "$groupName group ban gaya hai."
            VoiceLanguage.GUJARATI -> "\"$groupName\" ગ્રૂપ બની ગયું છે." to "$groupName grup bani gayu che."
            VoiceLanguage.HINGLISH -> "Group \"$groupName\" create ho gaya." to "Group $groupName create ho gaya."
        }
    }

    fun groupMemberAddedSuccess(groupName: String, members: List<String>, language: VoiceLanguage): Pair<String, String> {
        val mStr = members.joinToString(", ")
        return when (language) {
            VoiceLanguage.ENGLISH -> "Added $mStr to $groupName." to "Added $mStr to $groupName."
            VoiceLanguage.HINDI -> "$mStr को $groupName में जोड़ दिया गया है।" to "$mStr ko $groupName me jod diya gaya hai."
            VoiceLanguage.GUJARATI -> "$mStr ને $groupName માં ઉમેર્યા છે." to "$mStr ne $groupName ma umerya che."
            VoiceLanguage.HINGLISH -> "$mStr ko $groupName me add kar diya." to "$mStr ko $groupName me add kar diya."
        }
    }

    fun groupMemberRemovedSuccess(groupName: String, memberName: String, language: VoiceLanguage): Pair<String, String> {
        return when (language) {
            VoiceLanguage.ENGLISH -> "Removed $memberName from $groupName." to "Removed $memberName from $groupName."
            VoiceLanguage.HINDI -> "$memberName को $groupName से हटा दिया गया है।" to "$memberName ko $groupName se hata diya gaya hai."
            VoiceLanguage.GUJARATI -> "$memberName ને $groupName માંથી કાઢી નાખવામાં આવ્યા છે." to "$memberName ne $groupName mathi kaadhi naakhya che."
            VoiceLanguage.HINGLISH -> "$memberName ko $groupName se remove kar diya." to "$memberName ko $groupName se remove kar diya."
        }
    }

    fun groupDeletedSuccess(groupName: String, language: VoiceLanguage): Pair<String, String> {
        return when (language) {
            VoiceLanguage.ENGLISH -> "Group \"$groupName\" deleted." to "Group $groupName deleted."
            VoiceLanguage.HINDI -> "ग्रुप \"$groupName\" हटा दिया गया है।" to "Group $groupName hata diya gaya hai."
            VoiceLanguage.GUJARATI -> "\"$groupName\" ગ્રૂપ ડિલીટ થઈ ગયું છે." to "$groupName grup delete thai gayu che."
            VoiceLanguage.HINGLISH -> "Group \"$groupName\" delete ho gaya." to "Group $groupName delete ho gaya."
        }
    }

    fun highestExpenseResponse(title: String, amountPaise: Long, language: VoiceLanguage): Pair<String, String> {
        val amtDisplay = formatCurrencyDisplay(amountPaise)
        val amtSpoken = formatCurrencySpoken(amountPaise, language)
        return when (language) {
            VoiceLanguage.ENGLISH -> "Your highest expense is \"$title\" of $amtDisplay." to "Your highest expense is $title of $amtSpoken."
            VoiceLanguage.HINDI -> "आपका सबसे बड़ा खर्च \"$title\" है, $amtDisplay का।" to "Aapka sabse bada kharch $title hai, $amtSpoken ka."
            VoiceLanguage.GUJARATI -> "તમારો સૌથી મોટો ખર્ચ \"$title\" છે, $amtDisplay નો." to "Tamaro sauthi moto kharch $title che, $amtSpoken no."
            VoiceLanguage.HINGLISH -> "Aapka highest expense \"$title\" hai: $amtDisplay." to "Aapka highest expense $title hai, $amtSpoken ka."
        }
    }

    fun missingFriendNamePrompt(language: VoiceLanguage): Pair<String, String> {
        return when (language) {
            VoiceLanguage.ENGLISH -> "What is the friend's name?" to "What is the friend's name?"
            VoiceLanguage.HINDI -> "दोस्त का क्या नाम है?" to "Dost ka kya naam hai?"
            VoiceLanguage.GUJARATI -> "મિત્રનું નામ શું છે?" to "Mitranu naam shu che?"
            VoiceLanguage.HINGLISH -> "Friend ka kya naam hai?" to "Friend ka kya naam hai?"
        }
    }

    fun missingGroupNamePrompt(language: VoiceLanguage): Pair<String, String> {
        return when (language) {
            VoiceLanguage.ENGLISH -> "What should we name the group?" to "What should we name the group?"
            VoiceLanguage.HINDI -> "ग्रुप का क्या नाम रखना है?" to "Group ka kya naam rakhna hai?"
            VoiceLanguage.GUJARATI -> "ગ્રૂપનું નામ શું રાખવું છે?" to "Grupnu naam shu rakhvu che?"
            VoiceLanguage.HINGLISH -> "Group ka kya naam rakhna hai?" to "Group ka kya naam rakhna hai?"
        }
    }

    fun missingExpenseTitlePrompt(language: VoiceLanguage): Pair<String, String> {
        return when (language) {
            VoiceLanguage.ENGLISH -> "What was this expense for?" to "What was this expense for?"
            VoiceLanguage.HINDI -> "यह खर्च किस लिए था?" to "Yeh kharch kis liye tha?"
            VoiceLanguage.GUJARATI -> "આ ખર્ચ શેના માટે હતો?" to "Aa kharch shena maate hato?"
            VoiceLanguage.HINGLISH -> "Yeh expense kis cheez ka tha?" to "Yeh expense kis cheez ka tha?"
        }
    }
}
