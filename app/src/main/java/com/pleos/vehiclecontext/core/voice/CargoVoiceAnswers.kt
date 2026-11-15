package com.pleos.vehiclecontext.core.voice

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

private val nativeUnits = listOf(
    listOf("한", "하나"), listOf("두", "둘"), listOf("세", "셋"), listOf("네", "넷"),
    listOf("다섯"), listOf("여섯"), listOf("일곱"), listOf("여덟"), listOf("아홉")
)
private val nativeTens = listOf("열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔")
private const val sinoDigits = "일이삼사오육칠팔구"
private val sinoNumber = Regex("([$sinoDigits]?백)?([$sinoDigits]?십)?([$sinoDigits])?")
private val politeEnding = Regex("(입니다|이에요|이예요|예요|에요|이요|요)$")
private val loadedEnding = Regex("(을|를)?\\s*(실었어|실었어요|실었습니다|넣었어|넣었어요|넣었습니다)$")
private val usageEnding = Regex("(에|으로|로)?\\s*(쓰려고|사용하려고)\\s*(가져왔어요|실었어요|넣었어요)$")
private val unloadingEnding = Regex("\\s*(에서|에)\\s*(내릴\\s*(거예요|거에요|거야|겁니다)|내릴게요|내려요)$")
private val quantityPrefix = Regex("^(수량은|수량이|수량|개수는|갯수는|개수|갯수|총|모두|합해서|합계는)+")
private val quantityUnit = Regex("(개|게|박스|상자|봉지|세트|병|통|팩|장|켤레|봉|포대|묶음)$")
private val exactCounterHomophones = mapOf("세계" to 3, "내게" to 4, "두계" to 2, "한계" to 1)
private val modelNumericToken = Regex("^\\[([0-9]{1,3})\\]")
private val quantitySentenceEnding = Regex("(을|를|가|이)?(실었어|실었어요|실었습니다|넣었어|넣었어요|넣었습니다|있어|있어요|있습니다)$")

fun spokenCargoName(text: String): String =
    text.trim().trimEnd('.', '?', '!', '。').replace(loadedEnding, "").replace(politeEnding, "").trim()

fun spokenPurpose(text: String): String =
    text.trim().trimEnd('.', '?', '!', '。').replace(usageEnding, "").replace(politeEnding, "").trim()

fun spokenQuantity(text: String): Int? {
    val answer = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .trim().trimEnd('.', '?', '!', '。').filterNot(Char::isWhitespace)
        .replace(quantityPrefix, "").replace(quantitySentenceEnding, "").replace(politeEnding, "")
        .replace(modelNumericToken, "$1")
    exactCounterHomophones[answer]?.let { return it }
    val number = answer.replace(quantityUnit, "")
    number.toIntOrNull()?.let { return it.takeIf { value -> value in 1..999 } }
    if (number.isEmpty()) return null
    if (number == "스무") return 20
    val tens = nativeTens.indexOfFirst { number.startsWith(it) }
    val remainder = if (tens >= 0) number.removePrefix(nativeTens[tens]) else number
    val units = nativeUnits.indexOfFirst { remainder in it }
    if (units >= 0 || (tens >= 0 && remainder.isEmpty())) return (tens + 1) * 10 + (units + 1)
    val groups = sinoNumber.matchEntire(number)?.groupValues ?: return null
    val hundreds = if (groups[1].isEmpty()) 0 else sinoMultiplier(groups[1]) * 100
    val decades = if (groups[2].isEmpty()) 0 else sinoMultiplier(groups[2]) * 10
    val singles = groups[3].firstOrNull()?.let { sinoDigits.indexOf(it) + 1 } ?: 0
    return (hundreds + decades + singles).takeIf { it in 1..999 }
}

/**
 * 자연어 한 문장 발화 속에서 수량 표현(예: "와인 두 병", "케이크 2개")이 있으면 추출합니다.
 */
fun extractQuantityFromUtterance(text: String): Int? {
    val digitMatch = Regex("([0-9]{1,3})\\s*(개|게|박스|상자|봉지|세트|병|통|팩|장|켤레|봉|포대|묶음)").find(text)
    digitMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..999 }?.let { return it }

    val koreanMatch = Regex("(한|하나|두|둘|세|셋|네|넷|다섯|여섯|일곱|여덟|아홉|열)\\s*(개|게|박스|상자|봉지|세트|병|통|팩|장|켤레|봉|포대|묶음)").find(text)
    if (koreanMatch != null) {
        return spokenQuantity(koreanMatch.value)
    }
    return null
}

/** Tokens verified against the bundled Korean Vosk graph. Unknown words keep a rejection path. */
val koreanQuantityGrammar: String by lazy {
    val phrases = linkedSetOf("<UNK>", "한개", "두개", "세개", "네개")
    fun addNumber(words: String) {
        phrases.add(words)
        for (unit in listOf("개", "게", "개요", "병", "묶음")) phrases.add("$words $unit")
    }
    nativeUnits.flatten().forEach(::addNumber)
    addNumber("스무")
    for (tens in nativeTens) {
        addNumber(tens)
        for (unit in nativeUnits.flatten()) addNumber("$tens $unit")
    }
    for (count in 1..999) {
        val words = buildList {
            for ((divisor, unit) in listOf(100 to "백", 10 to "십")) {
                val digit = count / divisor % 10
                if (digit > 1) add(sinoDigits[digit - 1].toString())
                if (digit > 0) add(unit)
            }
            if (count % 10 > 0) add(sinoDigits[count % 10 - 1].toString())
        }.joinToString(" ")
        phrases.add(words)
        phrases.add("$words 개")
    }
    JSONArray(phrases.toList()).toString()
}

fun confirmedQuantityResult(json: JSONObject, unrestricted: String): String {
    val text = json.optString("text").trim()
    val words = json.optJSONArray("result") ?: return ""
    val count = spokenQuantity(text) ?: return ""
    // The Korean general decoder sometimes writes standalone “셋” as its near-homophone “새”.
    val secondCount = spokenQuantity(unrestricted) ?: 3.takeIf { unrestricted.trim() == "새" }
    if (words.length() == 0 || count != secondCount) return ""
    return text.takeIf {
        (0 until words.length()).all { i ->
            val word = words.getJSONObject(i)
            word.optDouble("conf", 0.0) >= 0.35 && word.optString("word") != "<UNK>"
        }
    }.orEmpty()
}

private fun sinoMultiplier(group: String): Int = if (group.length == 1) 1 else sinoDigits.indexOf(group[0]) + 1

fun spokenPlace(text: String, navigationDestination: String): String {
    val answer = text.trim().trimEnd('.', '?', '!', '。')
    val place = answer.replace(unloadingEnding, "").replace(politeEnding, "").trim()
    val reference = place.filterNot(Char::isWhitespace).removePrefix("지금").removePrefix("현재")
    val useCurrent = reference in setOf("내비목적지", "네비목적지", "목적지")
    return if (useCurrent || samePlaceName(place, navigationDestination)) navigationDestination else place
}

fun samePlaceName(first: String?, second: String?): Boolean =
    !first.isNullOrBlank() && !second.isNullOrBlank() &&
        first.trim().replace(Regex("\\s+"), "").lowercase() ==
        second.trim().replace(Regex("\\s+"), "").lowercase()

/**
 * 1차 통합 음성 입력("항목 + 개수 + 목적지") 파싱 결과
 */
data class ParsedCargoVoiceDraft(
    val itemName: String? = null,
    val quantity: Int? = null,
    val isBundle: Boolean = false,
    val targetDestination: String? = null,
    val purpose: String? = null
)

private val combinedQuantityRegex = Regex(
    "(?:([0-9]{1,3})|(열\\s*(?:한|두|세|네|다섯|여섯|일곱|여덟|아홉)|스무|스물|한|하나|두|둘|세|셋|네|넷|다섯|여섯|일곱|여덟|아홉|열))\\s*(개|게|박스|상자|봉지|세트|병|통|팩|장|켤레|봉|포대|묶음)(?=$|[\\s,]|을|를|이|가|은|는|만|에|에서)"
)
private val permanentKeepPhraseRegex = Regex(
    "(?:차에\\s*)?(상시\\s*보관|계속\\s*보관|차에\\s*계속\\s*(?:둘|보관할)(?:\\s*(?:거야|거예요|거에요|게요))?)"
)
private val navDestRefRegex = Regex(
    "(?:현재\\s*|지금\\s*)?(?:내비|네비)?\\s*목적지(?:에서|에|으로|로)?(?:\\s*(?:내릴(?:\\s*(?:거야|거예요|거에요|겁니다))?|내릴게요|내려요|갈|가져갈|하차할)[^,]*)?"
)
private val unloadingClauseRegex = Regex(
    "(.*?)(?:에서|에)\\s*(?:내릴(?:\\s*(?:거야|거예요|거에요|겁니다))?|내릴게요|내려요|꺼낼(?:\\s*(?:거야|거예요|거에요))?|하차(?:할[^,]*)?)$"
)
private val directionClauseRegex = Regex(
    "(.*?)(?:으로|로|까지)(?:\\s*(?:가져갈(?:\\s*(?:거야|거예요|게요))?|가져가요|갈(?:\\s*(?:거야|거예요))?|보낼(?:\\s*(?:거야|거예요))?|실었(?:어|어요|습니다)|넣었(?:어|어요|습니다)|내릴[^,]*))?(?=$|,)"
)
private val originFromRegex = Regex(
    "(?:^|[\\s,]+)[가-힣A-Za-z0-9]+에서(?=$|[\\s,])"
)
private val purposePhraseRegex = Regex(
    "(?:^|[\\s,])([가-힣A-Za-z0-9 ]{1,12}?)(?:에|으로|로)?\\s*(?:쓰려고|사용하려고)(?:\\s*(?:가져왔어요|실었어요|넣었어요))?"
)
private val purposeTagRegex = Regex(
    "(?:^|[\\s,])((?:생일\\s*파티|파티|선물|캠핑|레저|회의|행사|집들이|피크닉|출장|여행)용)(?=$|[\\s,])"
)
private val twoWordPlaceRegex = Regex(
    "(?:^|[\\s,])((?:우리|부모님|친정|시|처가|강남|서초|판교|성수|분당|수원|인천|김포|양평|제주|용산|여의도|송파|잠실|서울|부산|[가-힣]{1,5}동)\\s+(?:집|자택|댁|본가|회사|오피스|사무실|연구소|캠핑장|공항|터미널|아파트|빌라|역|센터)|[가-힣]{2,6}\\s+(?:캠핑장|오피스|사무실|연구소|아파트|빌라|백화점|공항|터미널|병원|학교|센터))$"
)
private val trailingBareDestRegex = Regex(
    "(?:^|[\\s,])((?:우리|부모님|친정|시|처가|강남|서초|판교|성수|분당|수원|인천|김포|양평|제주|용산|여의도|송파|잠실|서울|부산|[가-힣]{1,5}동)\\s+(?:집|자택|댁|본가|회사|오피스|사무실|연구소|캠핑장|공항|터미널|아파트|빌라|역|센터)|[가-힣]{2,6}\\s*(?:캠핑장|오피스|사무실|연구소|아파트|빌라|공항|터미널|병원|학교|센터)|우리집|집|자택|본가|회사|오피스|사무실|캠핑장)$"
)
private val trailingItemCleanupRegex = Regex(
    "(?:을|를|이|가|은|는)?\\s*(?:실었어|실었어요|실었습니다|넣었어|넣었어요|넣었습니다|담았어|담았어요|담았습니다|가져갈\\s*(?:거야|거예요)|가져왔어요|가져가요|챙겼어|챙겼어요|내릴\\s*(?:거야|거예요)|보관해줘|보관할게|보관할게요|있어요|있어)$"
)
private val hangingParticlesRegex = Regex("(^[\\s,은는이가을를]+|[\\s,은는이가을를]+$)")

/**
 * 한 문장 발화("생수 2묶음 우리 집에서 내릴 거야" 등)에서 항목·개수·목적지·용도를 동시 추출합니다.
 * 누락되거나 이해하지 못한 슬롯은 null로 남겨, 후속 재질문 대상만 정확히 추려낼 수 있도록 합니다.
 */
fun parseCombinedCargoUtterance(
    utterance: String,
    navigationDestination: String
): ParsedCargoVoiceDraft {
    var working = utterance.trim().trimEnd('.', '?', '!', '。')
    if (working.isBlank()) return ParsedCargoVoiceDraft()

    // 단독 수량 발화만 들어온 경우 우선 판별
    spokenQuantity(working)?.let { onlyQty ->
        val isBundleOnly = working.contains("묶음")
        return ParsedCargoVoiceDraft(
            itemName = null,
            quantity = onlyQty,
            isBundle = isBundleOnly,
            targetDestination = null
        )
    }

    // 1. 수량 + 단위 추출
    var quantity: Int? = null
    var isBundle = false
    combinedQuantityRegex.find(working)?.let { match ->
        val parsed = spokenQuantity(match.value)
        if (parsed != null) {
            quantity = parsed
            isBundle = match.groupValues.getOrNull(3) == "묶음"
            working = working.removeRange(match.range)
                .replace(Regex("\\s*(은|는|이|가|을|를|만)(?=$|[\\s,])"), " ")
        }
    }

    // 2. 목적지 추출
    var targetDestination: String? = null
    val permMatch = permanentKeepPhraseRegex.find(working)
    if (permMatch != null) {
        targetDestination = "상시 보관"
        working = working.removeRange(permMatch.range)
    }

    if (targetDestination == null) {
        navDestRefRegex.find(working)?.let { match ->
            if (navigationDestination.isNotBlank()) {
                targetDestination = navigationDestination
                working = working.removeRange(match.range)
            }
        }
    }

    if (targetDestination == null) {
        val clauseMatch = unloadingClauseRegex.find(working.trim()) ?: directionClauseRegex.find(working.trim())
        if (clauseMatch != null) {
            val prefix = clauseMatch.groupValues[1].trimEnd(',', ' ')
            val extractedPlace = extractTrailingPlaceCandidate(prefix, navigationDestination)
            if (!extractedPlace.isNullOrBlank()) {
                targetDestination = spokenPlace(extractedPlace, navigationDestination).ifBlank { extractedPlace }
                val prefixBeforePlace = prefix.removeSuffix(extractedPlace).trimEnd(',', ' ')
                working = prefixBeforePlace.replace(originFromRegex, " ")
            }
        }
    }

    if (targetDestination == null) {
        val trimmed = working.trim()
        if (navigationDestination.isNotBlank() && trimmed.endsWith(navigationDestination)) {
            targetDestination = navigationDestination
            working = trimmed.removeSuffix(navigationDestination)
        } else {
            trailingBareDestRegex.find(trimmed)?.let { match ->
                val place = match.groupValues[1].trim()
                if (place.isNotBlank()) {
                    targetDestination = spokenPlace(place, navigationDestination).ifBlank { place }
                    working = trimmed.removeRange(match.range)
                }
            }
        }
    }

    // 목적지 추출 과정에서 남은 출발지("~에서") 표현 정리
    if (targetDestination != null) {
        working = working.replace(originFromRegex, " ")
    }

    // 3. 선택적 용도 표현 추출
    var purpose: String? = null
    purposePhraseRegex.find(working)?.let { match ->
        val cleaned = match.groupValues[1].trim()
        if (cleaned.isNotBlank()) {
            purpose = cleaned
            working = working.removeRange(match.range)
        }
    } ?: purposeTagRegex.find(working)?.let { match ->
        val cleaned = match.groupValues[1].removeSuffix("용").trim()
        if (cleaned.isNotBlank()) {
            purpose = cleaned
            working = working.removeRange(match.range)
        }
    }

    // 4. 남은 텍스트를 품목명으로 정리
    val cleanedItem = spokenCargoName(
        working
            .replace(Regex("[,]+"), " ")
            .replace(trailingItemCleanupRegex, "")
            .replace(Regex("\\s+"), " ")
            .replace(hangingParticlesRegex, "")
            .trim()
    ).replace(hangingParticlesRegex, "").trim()

    val finalItem = cleanedItem.takeIf { it.isNotBlank() }
    return ParsedCargoVoiceDraft(
        itemName = finalItem,
        quantity = quantity,
        isBundle = isBundle,
        targetDestination = targetDestination?.takeIf { it.isNotBlank() },
        purpose = purpose?.takeIf { it.isNotBlank() }
    )
}

private fun extractTrailingPlaceCandidate(prefix: String, navigationDestination: String): String? {
    val trimmed = prefix.trim()
    if (trimmed.isBlank()) return null

    if (navigationDestination.isNotBlank() && trimmed.endsWith(navigationDestination)) {
        return navigationDestination
    }

    val lastCommaIdx = trimmed.lastIndexOf(',')
    if (lastCommaIdx >= 0 && lastCommaIdx < trimmed.lastIndex) {
        val afterComma = trimmed.substring(lastCommaIdx + 1).trim()
        val afterFrom = afterComma.substringAfter("에서 ", afterComma).trim()
        if (afterFrom.isNotBlank() && afterFrom.split(Regex("\\s+")).size <= 3) {
            return afterFrom
        }
    }

    twoWordPlaceRegex.find(trimmed)?.let { return it.groupValues[1].trim() }

    val afterFrom = trimmed.substringAfterLast("에서 ").trim()
    if (afterFrom != trimmed && afterFrom.isNotBlank() && afterFrom.split(Regex("\\s+")).size <= 2) {
        return afterFrom
    }

    return trimmed.split(Regex("\\s+")).lastOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * 1차 통합 음성 입력 후 앱이 아직 파악하지 못한 슬롯만 후속 질문 목록으로 추립니다.
 */
fun determineMissingVoiceQuestions(
    draft: ParsedCargoVoiceDraft,
    isAutoBundleItem: Boolean
): List<CargoVoiceQuestion> = buildList {
    if (draft.itemName.isNullOrBlank()) add(CargoVoiceQuestion.NAME)
    if (draft.quantity == null && !isAutoBundleItem) add(CargoVoiceQuestion.QUANTITY)
    if (draft.targetDestination.isNullOrBlank()) add(CargoVoiceQuestion.TARGET)
}
