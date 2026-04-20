# 🎤 Sample Doctor-Patient Consultation Script
## For Voice Recording Test

> **Instructions:** Read this aloud naturally (two people if possible — one as Doctor, one as Patient). 
> Include pauses, natural speech patterns. Record as audio file.

---

## Script (Hindi + English Code-Switching)

**Doctor:** Haan bataiye, kya problem hai aapko?

**Patient:** Doctor sahab, mujhe teen din se bahut tez bukhar aa raha hai. Raat ko 102-103 tak chala jaata hai. Aur sir mein bahut dard ho raha hai.

**Doctor:** Aur koi problem? Khansi ya sardi?

**Patient:** Haan thodi khansi bhi hai, dry khansi hai. Aur gala bhi dukh raha hai. Kabhi kabhi ulti jaisa feel hota hai lekin ulti nahi hui.

**Doctor:** Body mein dard ho raha hai? Jodon mein ya muscles mein?

**Patient:** Haan doctor, poore body mein dard hai, especially kamar aur tangon mein.

**Doctor:** Aapko pehle se koi bimari hai? BP ya sugar? Koi regular medicine lete ho?

**Patient:** Nahi doctor, koi bimari nahi hai. Lekin mujhe penicillin se allergy hai, pehle reaction hua tha.

**Doctor:** Okay noted. Chalo temperature check karte hain... 101.4 degree Fahrenheit hai. BP 130 over 85 hai. Pulse 92 beats per minute. Aur oxygen 97 percent hai.

**Patient:** Doctor ye serious toh nahi hai na? Dengue toh nahi hai?

**Doctor:** Abhi toh viral fever lag raha hai, lekin rule out karna padega. Main aapko kuch medicines deta hoon aur ek blood test bhi likhta hoon.

**Doctor:** Paracetamol 650mg, din mein teen baar, khaana khaane ke baad, 5 din ke liye. Azithromycin 500mg, ek tablet raat ko khaane ke baad, 3 din ke liye. Aur Pantoprazole 40mg, subah khali pet, 5 din.

**Patient:** Aur gale ke liye kuch?

**Doctor:** Haan, Betadine gargle karo din mein do baar, garam paani mein. Aur bahut paani piyo, aaram karo, heavy khaana mat khao. Light diet lo — khichdi, daliya sab theek hai.

**Doctor:** Blood test karwao — CBC aur Dengue NS1 antigen test. Agar 3 din mein bukhar nahi utra ya aur badh gaya, ya body pe rashes aaye, toh turant wapas aana.

**Patient:** Theek hai doctor, dhanyavaad.

---

## Expected AI Extraction Output

The AI should extract:

| Field | Expected Value |
|-------|---------------|
| **Chief Complaint** | Fever with headache for 3 days |
| **Symptoms** | Fever (3 days, severe), Headache (3 days, severe), Dry cough, Sore throat, Nausea, Body ache, Joint/muscle pain |
| **Vitals** | Temp: 101.4°F, BP: 130/85, Pulse: 92 bpm, SpO2: 97% |
| **Diagnosis** | Viral fever (suspected) |
| **Medications** | Paracetamol 650mg TID x5d, Azithromycin 500mg OD x3d, Pantoprazole 40mg OD x5d, Betadine gargle BID |
| **Allergies** | Penicillin |
| **Missing Info** | Weight not recorded |
| **Follow-up** | Return in 3 days if fever persists or worsens, or if rashes appear |
| **Languages** | Hindi, English |
