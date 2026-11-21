package com.hms.service.pdf;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every label printed on a prescription or case paper, in English, Marathi and Hindi, so a
 * bilingual print (EN_MR / EN_HI) is bilingual in every section — headings, table columns,
 * dose, frequency and duration — in one consistent "English / मराठी" form. English-only prints
 * are unchanged.
 */
public final class PrintLabels {

    private PrintLabels() {}

    private record L(String en, String mr, String hi) {}

    private static final Map<String, L> LABELS = Map.ofEntries(
            Map.entry("medicine", new L("Medicine", "औषध", "दवा")),
            Map.entry("dosage", new L("Dose", "मात्रा", "खुराक")),
            Map.entry("frequency", new L("Freq", "वेळा", "बार")),
            Map.entry("duration", new L("Duration", "कालावधी", "अवधि")),
            Map.entry("instruction", new L("Instruction", "सूचना", "निर्देश")),
            Map.entry("noMedicines", new L("No medications prescribed.", "औषधे लिहिलेली नाहीत.", "कोई दवा नहीं लिखी गई.")),
            Map.entry("advice", new L("DOCTOR ADVICE", "सल्ला", "सलाह")),
            Map.entry("followUp", new L("Follow Up Date", "पुढील तपासणी", "अगली जांच")),
            Map.entry("vitals", new L("VITAL SIGNS", "तपासणी नोंदी", "जांच रिकॉर्ड")),
            Map.entry("complaint", new L("CHIEF COMPLAINT / REASON FOR VISIT", "तक्रार", "शिकायत")),
            Map.entry("symptoms", new L("SYMPTOMS / CHIEF COMPLAINTS", "लक्षणे", "लक्षण")),
            Map.entry("diagnosis", new L("DIAGNOSIS / CLINICAL IMPRESSION", "निदान", "निदान")),
            Map.entry("treatment", new L("TREATMENT & CLINICAL NOTES", "उपचार", "उपचार")),
            Map.entry("evaluation", new L("CLINICAL EVALUATION", "वैद्यकीय तपासणी", "चिकित्सा जांच")),
            Map.entry("labTests", new L("LAB TESTS ADVISED", "सुचवलेल्या चाचण्या", "सुझाई गई जांचें")));

    /** "English" for EN, "English / मराठी" for EN_MR, "English / हिन्दी" for EN_HI. */
    public static String label(String key, String mode) {
        L l = LABELS.get(key);
        if (l == null) return key;
        String other = other(l, mode);
        return other == null ? l.en() : l.en() + " / " + other;
    }

    private static String other(L l, String mode) {
        if (PatientInstructionFormatter.MODE_EN_MR.equals(mode)) return l.mr();
        if (PatientInstructionFormatter.MODE_EN_HI.equals(mode)) return l.hi();
        return null;
    }

    private static final Map<String, String[]> DOSE_UNITS = Map.of(
            "tablet", new String[] {"गोळी", "गोली"},
            "capsule", new String[] {"कॅप्सूल", "कैप्सूल"},
            "ml", new String[] {"मिली", "मिली"},
            "drops", new String[] {"थेंब", "बूंद"},
            "puff", new String[] {"पफ", "पफ"},
            "sachet", new String[] {"पुडी", "पाउच"},
            "application", new String[] {"लावणे", "लगाएं"},
            "injection", new String[] {"इंजेक्शन", "इंजेक्शन"});

    private static final Pattern DOSE = Pattern.compile("(\\d+(?:[./]\\d+)?(?: \\d+/\\d+)?) ?([a-zA-Z]+)");

    /** "1/2 tablet" → "1/2 tablet / 1/2 गोळी". Anything not recognised prints as written. */
    public static String dose(String dose, String mode) {
        if (dose == null || dose.isBlank() || PatientInstructionFormatter.MODE_EN.equals(mode)) return dose;
        Matcher m = DOSE.matcher(dose.trim());
        if (!m.matches()) return dose;
        String unit = m.group(2).toLowerCase().replaceAll("s$", "");
        String[] t = DOSE_UNITS.getOrDefault(unit, DOSE_UNITS.get(unit + "s"));
        if (t == null) return dose;
        String local = t[PatientInstructionFormatter.MODE_EN_MR.equals(mode) ? 0 : 1];
        return dose.trim() + " / " + m.group(1) + " " + local;
    }

    private static final Pattern DURATION = Pattern.compile("^(\\d+)\\s*(day|days|week|weeks|month|months)$", Pattern.CASE_INSENSITIVE);

    /** "5 Days" → "5 Days / 5 दिवस". */
    public static String duration(String duration, String mode) {
        if (duration == null || duration.isBlank() || PatientInstructionFormatter.MODE_EN.equals(mode)) return duration;
        Matcher m = DURATION.matcher(duration.trim());
        if (!m.matches()) return duration;
        char u = Character.toLowerCase(m.group(2).charAt(0));
        boolean mr = PatientInstructionFormatter.MODE_EN_MR.equals(mode);
        String local = switch (u) {
            case 'w' -> mr ? "आठवडे" : "सप्ताह";
            case 'm' -> mr ? "महिने" : "महीने";
            default -> "दिवस";
        };
        return duration.trim() + " / " + m.group(1) + " " + local;
    }

    private static final Pattern MAN = Pattern.compile("^(\\d+(?:/\\d+)?)-(\\d+(?:/\\d+)?)-(\\d+(?:/\\d+)?)$");

    /** "1-0-1" → "1-0-1 / सकाळ-रात्र": the times of day, so a patient who cannot read the numbers still can. */
    public static String frequency(String frequency, String mode) {
        if (frequency == null || frequency.isBlank() || PatientInstructionFormatter.MODE_EN.equals(mode)) return frequency;
        Matcher m = MAN.matcher(frequency.trim());
        if (!m.matches()) return frequency;
        boolean mr = PatientInstructionFormatter.MODE_EN_MR.equals(mode);
        String[] times = mr ? new String[] {"सकाळ", "दुपार", "रात्र"} : new String[] {"सुबह", "दोपहर", "रात"};
        StringBuilder when = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (!"0".equals(m.group(i + 1))) {
                if (!when.isEmpty()) when.append('-');
                when.append(times[i]);
            }
        }
        return when.isEmpty() ? frequency : frequency.trim() + " / " + when;
    }
}
