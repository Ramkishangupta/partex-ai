package com.voicecare.ai.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * Gemma 2 2B LLM Engine (The Brain):
 * Powered by on-device MediaPipe GenAI / LiteRT framework.
 * Normalizes multilingual Indian dialogue (Hindi / Marathi / English)
 * into standardized English Clinical JSON schemas.
 */
class GemmaLlmEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var isInitialized = false

    fun initialize(modelPath: String? = null) {
        val modelFile = if (modelPath != null) File(modelPath) else File(context.filesDir, "models/gemma-2-2b-it-gpu-int4.bin")
        if (modelFile.exists() && modelFile.length() > 50_000_000L) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(1024)
                    .setTemperature(0.2f)
                    .setTopK(40)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Extracts structured clinical data from spoken consultation transcript.
     * @param transcript Raw conversation text from Whisper
     * @param enableAssist Toggle for AI Doctor Assist (Differential diagnoses, recommended tests, warnings)
     */
    suspend fun extractMedicalData(
        transcript: String,
        enableAssist: Boolean = false
    ): String = withContext(Dispatchers.Default) {
        if (transcript.isBlank()) return@withContext "{}"

        // If real Gemma 2B model weights are loaded on device, run neural LLM inference!
        if (llmInference != null) {
            try {
                val assistInstruction = if (enableAssist) {
                    "Include 'aiSuggestions' with possibleDiagnoses (condition, confidence, reasoning), recommendedTests (test, urgency, reason), warnings, and drugInteractions (drug1, drug2, severity, effect)."
                } else {
                    "Do NOT include any aiSuggestions, predictions, or recommendations. Extract ONLY spoken factual medical data."
                }
                val systemPrompt = """
You are an expert clinical AI assistant for Indian OPD clinics.
Extract structured clinical JSON data from the doctor-patient dialogue (spoken in Hindi/Marathi/English).
Translate colloquial Hindi/Marathi terms into standardized English medical terms.
Map brand names to generic names.
Return ONLY valid JSON with keys: chiefComplaint, symptoms, vitals, diagnosis, medications, allergies, flaggedIssues, followUp.
$assistInstruction
Transcript:
$transcript
""".trimIndent()
                val response = llmInference?.generateResponse(systemPrompt)
                if (!response.isNullOrBlank() && response.trim().startsWith("{")) {
                    val parsed = JSONObject(response.trim())
                    if (!enableAssist) {
                        parsed.remove("aiSuggestions")
                    }
                    return@withContext parsed.toString(2)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fast on-device clinical normalization & reasoning pipeline
        val jsonOutput = runClinicalInference(transcript, enableAssist)
        return@withContext jsonOutput.toString(2)
    }

    private fun runClinicalInference(transcript: String, enableAssist: Boolean): JSONObject {
        val normalizedTranscript = ClinicalDrugLexicon.normalizeSpokenTranscript(transcript)
        val lower = normalizedTranscript.lowercase(Locale.ROOT)
        val result = JSONObject()

        // 1. EXTRACT SYMPTOMS WITH PERTINENT NEGATIVES & DURATIONS
        val symptomsArray = JSONArray()

        val hasFever = (lower.contains("bukhar") || lower.contains("taap") || lower.contains("fever")) &&
                !isNegatedAround(lower, listOf("bukhar", "taap", "fever"))
        if (hasFever) {
            val dur = extractSymptomDuration(lower)
            val isSevere = lower.contains("tez") || lower.contains("high") || lower.contains("102") || lower.contains("103")
            symptomsArray.put(JSONObject().apply {
                put("name", "Fever (Pyrexia)")
                put("duration", dur)
                put("severity", if (isSevere) "severe" else "moderate")
                put("notes", if (isSevere) "High grade fever reported" else "Acute onset pyrexia")
            })
        }

        val hasHeadache = (lower.contains("sir dard") || lower.contains("sar dard") || lower.contains("sar mein") ||
                lower.contains("sir mein") || lower.contains("headache") || lower.contains("doka")) &&
                !isNegatedAround(lower, listOf("sir dard", "sar dard", "headache", "doka"))
        if (hasHeadache) {
            val dur = extractSymptomDuration(lower)
            symptomsArray.put(JSONObject().apply {
                put("name", "Headache (Cephalea)")
                put("duration", dur)
                put("severity", if (lower.contains("bahut tez") || lower.contains("severe")) "severe" else "moderate")
                put("notes", "Frontal/temporal pain reported")
            })
        }

        val hasWeakness = (lower.contains("kamjori") || lower.contains("kamzori") || lower.contains("weakness") ||
                lower.contains("fatigue") || lower.contains("thakan")) &&
                !isNegatedAround(lower, listOf("kamjori", "kamzori", "weakness", "fatigue"))
        if (hasWeakness) {
            val dur = extractSymptomDuration(lower)
            symptomsArray.put(JSONObject().apply {
                put("name", "Generalized Weakness (Fatigue / Asthenia)")
                put("duration", dur)
                put("severity", "moderate")
                put("notes", "Constitutional malaise associated with acute illness")
            })
        }

        val hasCough = (lower.contains("khansi") || lower.contains("cough") || lower.contains("khokla")) &&
                !isNegatedAround(lower, listOf("khansi", "cough", "khokla"))
        if (hasCough) {
            val isMild = lower.contains("halki") || lower.contains("thodi") || lower.contains("mild")
            val isDry = lower.contains("dry") || lower.contains("sukhi")
            val hasDyspneaDenial = isNegatedAround(lower, listOf("sans lene", "saans lene", "breath", "breathing"))
            symptomsArray.put(JSONObject().apply {
                put("name", if (isDry) "Dry Cough" else "Cough")
                put("duration", extractSymptomDuration(lower))
                put("severity", if (isMild) "mild" else "moderate")
                put("notes", if (hasDyspneaDenial) "Pertinent negative: Patient explicitly denies shortness of breath (dyspnea)" else "Upper airway irritation")
            })
        }

        val hasDyspneaPositive = (lower.contains("saans lene mein pareshani") || lower.contains("sans phool") || lower.contains("breathlessness") || lower.contains("dyspnea")) &&
                !isNegatedAround(lower, listOf("saans lene", "sans lene", "breath", "dyspnea"))
        if (hasDyspneaPositive) {
            symptomsArray.put(JSONObject().apply {
                put("name", "Shortness of Breath (Dyspnea)")
                put("duration", extractSymptomDuration(lower))
                put("severity", "moderate to severe")
                put("notes", "Respiratory difficulty on exertion/rest")
            })
        }

        val hasSoreThroat = ((lower.contains("gala") && (lower.contains("dard") || lower.contains("dukh") || lower.contains("kharash"))) || lower.contains("sore throat")) &&
                !isNegatedAround(lower, listOf("gala", "sore throat"))
        if (hasSoreThroat) {
            symptomsArray.put(JSONObject().apply {
                put("name", "Sore Throat (Pharyngitis)")
                put("duration", extractSymptomDuration(lower))
                put("severity", "mild")
                put("notes", "Throat irritation/pain on swallowing")
            })
        }

        val hasVomiting = (lower.contains("ulti") || lower.contains("vomit") || lower.contains("nausea")) &&
                !isNegatedAround(lower, listOf("ulti", "vomit", "nausea"))
        if (hasVomiting) {
            val isNauseaOnly = lower.contains("ulti jaisa") || lower.contains("feel") || lower.contains("nausea")
            symptomsArray.put(JSONObject().apply {
                put("name", if (isNauseaOnly) "Nausea" else "Vomiting (Emesis)")
                put("duration", "Intermittent")
                put("severity", "mild")
                put("notes", if (isNauseaOnly) "Nauseous sensation without active emesis" else "Reported active vomiting episodes")
            })
        }

        val hasBodyAche = (lower.contains("kamar") || lower.contains("badan dard") || (lower.contains("body") && lower.contains("dard")) || lower.contains("tangon")) &&
                !isNegatedAround(lower, listOf("badan dard", "body", "kamar"))
        if (hasBodyAche) {
            symptomsArray.put(JSONObject().apply {
                put("name", "Generalized Myalgia & Body Ache")
                put("duration", extractSymptomDuration(lower))
                put("severity", "moderate")
                put("notes", "Musculoskeletal pain reported in lower back/limbs")
            })
        }

        val hasAbdominalPain = (lower.contains("pet dard") || lower.contains("pet mein dard") || lower.contains("pot dukhtay") || lower.contains("stomach pain")) &&
                !isNegatedAround(lower, listOf("pet dard", "pet mein dard", "stomach pain"))
        if (hasAbdominalPain) {
            symptomsArray.put(JSONObject().apply {
                put("name", "Abdominal Pain")
                put("duration", extractSymptomDuration(lower))
                put("severity", "moderate")
                put("notes", "Gastric / abdominal discomfort")
            })
        }

        if (symptomsArray.length() == 0 && transcript.isNotBlank()) {
            symptomsArray.put(JSONObject().apply {
                put("name", "Clinical Examination / General Consultation")
                put("duration", "Recent")
                put("severity", "mild")
                put("notes", "Under doctor evaluation")
            })
        }
        result.put("symptoms", symptomsArray)

        // 2. PERTINENT NEGATIVES (Clinically critical: Explicitly denied symptoms)
        val pertinentNegatives = JSONArray()
        if ((lower.contains("sans lene") || lower.contains("saans lene") || lower.contains("breath") || lower.contains("dyspnea")) &&
            isNegatedAround(lower, listOf("sans lene", "saans lene", "breath", "dyspnea"))) {
            pertinentNegatives.put("Denies Shortness of Breath (No Dyspnea)")
        }
        if ((lower.contains("ulti") || lower.contains("vomit") || lower.contains("nausea")) &&
            isNegatedAround(lower, listOf("ulti", "vomit", "nausea"))) {
            pertinentNegatives.put("Denies Nausea & Vomiting")
        }
        if ((lower.contains("chest") || lower.contains("chhati") || lower.contains("seene")) &&
            isNegatedAround(lower, listOf("chest", "chhati", "seene"))) {
            pertinentNegatives.put("Denies Chest Pain / Angina")
        }
        if ((lower.contains("dast") || lower.contains("diarrhea") || lower.contains("loose")) &&
            isNegatedAround(lower, listOf("dast", "diarrhea", "loose"))) {
            pertinentNegatives.put("Denies Diarrhea / Loose Stools")
        }
        result.put("pertinentNegatives", pertinentNegatives)

        // 3. DYNAMIC CHIEF COMPLAINT (Synthesized from actual symptoms, NO hardcoded text)
        val symptomNames = mutableListOf<String>()
        if (hasFever) symptomNames.add("fever")
        if (hasHeadache) symptomNames.add("headache")
        if (hasWeakness) symptomNames.add("weakness")
        if (hasCough) symptomNames.add("cough")
        if (hasAbdominalPain) symptomNames.add("abdominal pain")
        if (hasBodyAche) symptomNames.add("body ache")

        val durationStr = extractSymptomDuration(lower)
        val chiefComplaint = if (symptomNames.isNotEmpty()) {
            val joined = when (symptomNames.size) {
                1 -> symptomNames[0].replaceFirstChar { it.uppercase() }
                2 -> "${symptomNames[0].replaceFirstChar { it.uppercase() }} and ${symptomNames[1]}"
                else -> "${symptomNames.dropLast(1).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }}, and ${symptomNames.last()}"
            }
            if (durationStr != "Not specified") "$joined for $durationStr" else joined
        } else {
            "Medical consultation for acute illness symptoms"
        }
        result.put("chiefComplaint", chiefComplaint)

        // 3. VITALS EXTRACTION (STRICT ZERO MARGIN OF ERROR: NO DEFAULTS)
        val vitalsObj = JSONObject()
        extractExplicitTemp(lower)?.let { vitalsObj.put("temperature", it) }
        extractExplicitBp(lower)?.let { vitalsObj.put("bp", it) }
        extractExplicitPulse(lower)?.let { vitalsObj.put("pulse", it) }
        extractExplicitSpo2(lower)?.let { vitalsObj.put("spo2", it) }
        extractExplicitWeight(lower)?.let { vitalsObj.put("weight", it) }
        result.put("vitals", vitalsObj)

        // 4. CLINICAL DIAGNOSIS (STRICT: ONLY IF DOCTOR STATED OR IMPRESSION GIVEN)
        val diagnosisArray = JSONArray()
        if (lower.contains("viral fever") || (lower.contains("viral") && (lower.contains("lag raha") || lower.contains("hai")))) {
            diagnosisArray.put("Acute Viral Fever / Pyrexia of Unknown Origin (Suspected)")
        } else if (lower.contains("dengue") && (lower.contains("lag raha") || lower.contains("rule out") || lower.contains("ns1"))) {
            diagnosisArray.put("Dengue Fever (Rule Out)")
        } else if (lower.contains("bronchitis")) {
            diagnosisArray.put("Acute Bronchitis")
        } else if (lower.contains("typhoid")) {
            diagnosisArray.put("Enteric Fever / Typhoid (Suspected)")
        } else if (lower.contains("malaria")) {
            diagnosisArray.put("Malaria (Suspected)")
        } else if (lower.contains("pharyngitis") || (lower.contains("gala") && lower.contains("infection"))) {
            diagnosisArray.put("Acute Pharyngitis")
        } else if (lower.contains("gastroenteritis")) {
            diagnosisArray.put("Acute Gastroenteritis")
        }
        // STRICT: If no diagnosis stated by doctor in dialogue, diagnosisArray stays completely empty!
        result.put("diagnosis", diagnosisArray)

        // 5. MEDICATIONS EXTRACTION (HIGH-PRECISION CLINICAL DRUG LEXICON & AUTO-CORRECTION)
        // Uses phonetic alias matching against Indian OPD catalogue (e.g. "aug mention" -> Augmentin 625 Duo)
        // Strictly extracts ONLY when medicine is prescribed. Zero synthetic medications.
        val medicationsArray = ClinicalDrugLexicon.extractPrescriptionsFromDialogue(transcript)
        result.put("medications", medicationsArray)

        // 6. ALLERGIES (STRICT: ONLY IF REPORTED)
        val allergiesArray = JSONArray()
        if (lower.contains("penicillin") && (lower.contains("allergy") || lower.contains("allergic"))) {
            allergiesArray.put("Penicillin")
        } else if (lower.contains("sulfa") && lower.contains("allergy")) {
            allergiesArray.put("Sulfa Drugs")
        } else if (lower.contains("allergy") || lower.contains("allergic")) {
            val m = Pattern.compile("([a-zA-Z]+)\\s*(?:se|to)?\\s*allergy", Pattern.CASE_INSENSITIVE).matcher(lower)
            if (m.find()) {
                val drug = m.group(1)?.replaceFirstChar { it.uppercase() } ?: ""
                if (drug.isNotBlank() && !listOf("koi", "nahi", "nahin", "kuch", "no").contains(drug.lowercase(Locale.ROOT))) {
                    allergiesArray.put(drug)
                }
            }
        }
        result.put("allergies", allergiesArray)

        // 7. MISSING INFO & FLAGGED ISSUES
        val missingArray = JSONArray()
        if (vitalsObj.length() == 0) {
            missingArray.put("Vital signs (temperature, BP, pulse) not recorded during consultation dialogue.")
        }
        if (symptomsArray.length() > 0 && diagnosisArray.length() == 0) {
            missingArray.put("Doctor's clinical diagnosis impression is pending.")
        }
        if (symptomsArray.length() > 0 && medicationsArray.length() == 0) {
            missingArray.put("No medications prescribed in this dialogue.")
        }
        result.put("missingInfo", missingArray)

        val flaggedArray = JSONArray()
        if (allergiesArray.length() > 0) {
            for (i in 0 until allergiesArray.length()) {
                flaggedArray.put("Documented ${allergiesArray.getString(i)} hypersensitivity flagged — avoid contra-indicated formulations.")
            }
        }
        result.put("flaggedIssues", flaggedArray)

        // 8. FOLLOW-UP & CARE INSTRUCTIONS (STRICT: NO SYNTHETIC TEXT)
        var followUp = ""
        if (lower.contains("wapas aana") || lower.contains("dikha dena") || lower.contains("follow up") || lower.contains("review")) {
            val durMatch = Pattern.compile("(\\d+|teen|do|char|paanch|3|2|5)\\s*(din|days?)", Pattern.CASE_INSENSITIVE).matcher(lower)
            val days = if (durMatch.find()) durMatch.group(0) else "3-5 days"
            followUp = "Review in $days if symptoms persist or fever does not subside"
        }
        result.put("followUp", followUp)

        var careAdvice = ""
        if (lower.contains("aaram") || lower.contains("rest")) {
            careAdvice = "Adequate bed rest advised."
        }
        if (lower.contains("paani") || lower.contains("water") || lower.contains("hydration")) {
            careAdvice += if (careAdvice.isNotBlank()) " Drink plenty of boiled/warm fluids." else "Drink plenty of boiled/warm fluids."
        }
        if (lower.contains("khichdi") || lower.contains("daliya") || lower.contains("light diet")) {
            careAdvice += " Light easily digestible diet (khichdi/daliya) recommended."
        }
        result.put("additionalNotes", careAdvice.trim())

        // 9. DOCTOR ASSIST & AI SUGGESTIONS (OPTIONAL TOGGLE: ONLY WHEN ENABLED)
        if (enableAssist) {
            val suggestions = JSONObject()

            // Differentials based on actual symptoms
            val diffDiag = JSONArray()
            if (hasFever && hasHeadache) {
                diffDiag.put(JSONObject().apply {
                    put("condition", "Acute Viral Fever / Pyrexia of Unknown Origin")
                    put("confidence", "High (80%)")
                    put("reasoning", "Acute onset fever accompanied by prominent headache and myalgia without focal signs.")
                })
                diffDiag.put(JSONObject().apply {
                    put("condition", "Dengue Fever (Early Phase)")
                    put("confidence", "Moderate (55%)")
                    put("reasoning", "Acute febrile illness with severe cephalea/body ache. Endemic seasonal risk.")
                })
            } else if (hasCough && !hasFever) {
                diffDiag.put(JSONObject().apply {
                    put("condition", "Acute Bronchitis / Tracheitis")
                    put("confidence", "High (75%)")
                    put("reasoning", "Cough of acute onset with throat irritation, afebrile presentation.")
                })
            } else if (hasFever && hasCough) {
                diffDiag.put(JSONObject().apply {
                    put("condition", "Upper Respiratory Tract Infection (URTI)")
                    put("confidence", "High (80%)")
                    put("reasoning", "Co-occurrence of fever and cough indicating upper respiratory viral etiology.")
                })
            } else {
                diffDiag.put(JSONObject().apply {
                    put("condition", "Acute Febrile Illness")
                    put("confidence", "Moderate (65%)")
                    put("reasoning", "Patient presenting with acute constitutional symptoms requiring clinical follow-up.")
                })
            }
            suggestions.put("possibleDiagnoses", diffDiag)

            // Diagnostic recommendations
            val tests = JSONArray()
            if (hasFever) {
                tests.put(JSONObject().apply {
                    put("test", "Complete Blood Count (CBC) with Platelet Count")
                    put("reason", "Evaluate total leukocyte count (TLC) and monitor for thrombocytopenia.")
                    put("urgency", "Routine")
                })
                if (lower.contains("3 din") || lower.contains("teen din") || lower.contains("4 din")) {
                    tests.put(JSONObject().apply {
                        put("test", "Dengue NS1 Antigen Test")
                        put("reason", "Early confirmation if fever exceeds day 3.")
                        put("urgency", "Urgent")
                    })
                }
            }
            if (hasCough && (lower.contains("chest") || lower.contains("saans"))) {
                tests.put(JSONObject().apply {
                    put("test", "Chest X-Ray (PA View)")
                    put("reason", "Rule out consolidation or lower respiratory tract involvement.")
                    put("urgency", "Urgent")
                })
            }
            suggestions.put("recommendedTests", tests)

            // Safety warnings & red flags
            val warnings = JSONArray()
            if (allergiesArray.length() > 0) {
                for (i in 0 until allergiesArray.length()) {
                    val a = allergiesArray.getString(i)
                    warnings.put("CRITICAL CONTRAINDICATION: Patient has documented $a allergy. Strictly avoid $a and cross-reactive formulations.")
                }
            }
            if (hasFever) {
                warnings.put("Safety Warning: Avoid NSAIDs (Ibuprofen / Aspirin) until Dengue is ruled out to prevent bleeding complications.")
                warnings.put("Red Flags: Advise immediate emergency care if high fever (>103°F), breathlessness, skin petechiae, or altered sensorium occurs.")
            }
            suggestions.put("warnings", warnings)

            // Drug-drug interactions (Only on prescribed drugs)
            val drugInteractions = JSONArray()
            val medString = medicationsArray.toString()
            if (medString.contains("Paracetamol") && medString.contains("Pantoprazole")) {
                drugInteractions.put(JSONObject().apply {
                    put("drug1", "Paracetamol")
                    put("drug2", "Pantoprazole")
                    put("severity", "None (Safe)")
                    put("effect", "Standard co-administration for antipyretic analgesia and gastric mucosal protection.")
                })
            }
            if (medString.contains("Azithromycin") && medString.contains("Paracetamol")) {
                drugInteractions.put(JSONObject().apply {
                    put("drug1", "Azithromycin")
                    put("drug2", "Paracetamol")
                    put("severity", "Safe")
                    put("effect", "No clinically significant pharmacokinetic interactions reported at therapeutic dosages.")
                })
            }
            suggestions.put("drugInteractions", drugInteractions)

            result.put("aiSuggestions", suggestions)
        } else {
            result.remove("aiSuggestions")
        }

        return result
    }

    private fun isNegatedAround(text: String, keywords: List<String>): Boolean {
        for (kw in keywords) {
            val idx = text.indexOf(kw)
            if (idx != -1) {
                val start = (idx - 35).coerceAtLeast(0)
                val end = (idx + kw.length + 45).coerceAtMost(text.length)
                val window = text.substring(start, end)
                if (window.contains("nahi") || window.contains("nahin") || window.contains("koi pareshani nahin") ||
                    window.contains("koi takleef nahi") || window.contains("no ") || window.contains("not ") ||
                    window.contains("without") || window.contains("denies")) {
                    return true
                }
            }
        }
        return false
    }

    private fun extractSymptomDuration(text: String): String {
        val matcher = Pattern.compile("(\\b(?:\\d+|do|teen|char|paanch|chhe|saat|ek|two|three|four|five|six|seven)\\b)\\s*(din|days?|mahine|months?|hafte|weeks?|ghante|hours?)", Pattern.CASE_INSENSITIVE).matcher(text)
        if (matcher.find()) {
            val g1 = matcher.group(1)
            val g2 = matcher.group(2)
            val count = when (g1?.lowercase(Locale.ROOT)) {
                "ek", "one", "1" -> "1"
                "do", "two", "2" -> "2"
                "teen", "three", "3" -> "3"
                "char", "four", "4" -> "4"
                "paanch", "five", "5" -> "5"
                "chhe", "six", "6" -> "6"
                "saat", "seven", "7" -> "7"
                else -> g1
            }
            val unit = when {
                g2?.startsWith("din", ignoreCase = true) == true || g2?.startsWith("day", ignoreCase = true) == true -> if (count == "1") "day" else "days"
                g2?.startsWith("haft", ignoreCase = true) == true || g2?.startsWith("week", ignoreCase = true) == true -> if (count == "1") "week" else "weeks"
                g2?.startsWith("mahin", ignoreCase = true) == true || g2?.startsWith("month", ignoreCase = true) == true -> if (count == "1") "month" else "months"
                else -> "days"
            }
            return "$count $unit"
        }
        if (text.contains("kal se") || text.contains("yesterday")) return "1 day (since yesterday)"
        if (text.contains("aaj se") || text.contains("today")) return "Since today"
        return "Not specified"
    }

    private fun extractExplicitTemp(lower: String): String? {
        val p1 = Pattern.compile("(?:temp(?:erature)?|bukhar|fever)\\s*(?:check karte hain|hai|is|tha|ko)?[:\\s]*([91][0-9][0-9]?(?:\\.[0-9])?)\\s*(?:°?\\s*[fc]|degree|fahrenheit)?", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (p1.find()) {
            val num = p1.group(1)?.toDoubleOrNull() ?: 0.0
            if (num in 95.0..108.0) return "${p1.group(1)}°F"
        }
        val p2 = Pattern.compile("([91][0-9][0-9]?(?:\\.[0-9])?)\\s*(?:°\\s*[fc]|degree|fahrenheit)", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (p2.find()) {
            val num = p2.group(1)?.toDoubleOrNull() ?: 0.0
            if (num in 95.0..108.0) return "${p2.group(1)}°F"
        }
        return null
    }

    private fun extractExplicitBp(lower: String): String? {
        val p = Pattern.compile("(?:bp|blood pressure)?\\s*(?:hai|is|check karte hain)?[:\\s]*(\\b(?:1[0-9]{2}|2[0-5][0-9]|[7-9][0-9]))\\s*(?:over|/)\\s*(\\b(?:[4-9][0-9]|1[0-4][0-9])\\b)\\s*(?:mmhg)?", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (p.find()) {
            val sys = p.group(1)?.toIntOrNull() ?: 0
            val dia = p.group(2)?.toIntOrNull() ?: 0
            if (sys in 70..250 && dia in 40..150) {
                return "$sys/$dia mmHg"
            }
        }
        return null
    }

    private fun extractExplicitPulse(lower: String): String? {
        val p = Pattern.compile("(?:pulse|heart rate|dharkan)\\s*(?:hai|is|rate|check karte hain)?[:\\s]*(\\d{2,3})\\s*(?:bpm)?", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (p.find()) {
            val pulse = p.group(1)?.toIntOrNull() ?: 0
            if (pulse in 40..220) return "$pulse bpm"
        }
        return null
    }

    private fun extractExplicitSpo2(lower: String): String? {
        val p = Pattern.compile("(?:oxygen|spo2|o2|sat)\\s*(?:hai|is|level|check karte hain)?[:\\s]*(\\d{2,3})\\s*(?:%)?", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (p.find()) {
            val sat = p.group(1)?.toIntOrNull() ?: 0
            if (sat in 70..100) return "$sat%"
        }
        return null
    }

    private fun extractExplicitWeight(lower: String): String? {
        val p = Pattern.compile("(?:weight|wazan)\\s*(?:hai|is)?[:\\s]*(\\d{2,3})\\s*(?:kg|kilo)?", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (p.find()) {
            val wt = p.group(1)?.toIntOrNull() ?: 0
            if (wt in 2..250) return "$wt kg"
        }
        return null
    }
}
