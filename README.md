# 🏥 VoiceCare — System Flow Diagrams
> Share these with your team to understand the full pipeline.

---

## 1. End-to-End MVP Flow (The Big Picture)

```mermaid
flowchart TB
    subgraph INPUT["🎤 INPUT"]
        A["Doctor speaks with patient<br/>(Hindi / Marathi / English)"]
    end

    subgraph CAPTURE["📡 CAPTURE"]
        B["Browser Mic Recording<br/>(Web Audio API)"]
        B2["Audio chunks sent<br/>via WebSocket (Socket.io)"]
    end

    subgraph ASR["🗣️ SPEECH-TO-TEXT"]
        C["Deepgram Nova-2<br/>(Real-time Streaming)"]
        C2["Auto Language Detection"]
        C3["Speaker Diarization<br/>(Doctor vs Patient)"]
    end

    subgraph LLM["🧠 AI EXTRACTION"]
        D["Gemini 2.0 Flash"]
        D2["Prompt Engineering:<br/>Hindi/Marathi → English<br/>Colloquial → Medical Terms<br/>Brand → Generic Drugs"]
        D3["Structured JSON Output"]
    end

    subgraph STORAGE["💾 DATABASE"]
        E["MongoDB"]
        E2["Patient Record<br/>(PAT-XXXXX)"]
        E3["Consultation Record<br/>(Session + Visit #)"]
    end

    subgraph OUTPUT["📊 OUTPUT"]
        F["Live Transcript Display"]
        F2["Structured Medical Data"]
        F3["Auto-Generated Prescription"]
        F4["AI Suggestions & Flags"]
    end

    A --> B --> B2
    B2 --> C
    C --> C2 & C3
    C2 & C3 --> F
    C --> D
    D --> D2 --> D3
    D3 --> E
    E --> E2 & E3
    D3 --> F2 & F3 & F4

    style INPUT fill:#1a1a2e,color:#fff,stroke:#e94560
    style CAPTURE fill:#16213e,color:#fff,stroke:#0f3460
    style ASR fill:#0f3460,color:#fff,stroke:#53a8b6
    style LLM fill:#533483,color:#fff,stroke:#e94560
    style STORAGE fill:#1a1a2e,color:#fff,stroke:#53a8b6
    style OUTPUT fill:#0f3460,color:#fff,stroke:#e94560
```

---

## 2. Real-Time Audio Pipeline (WebSocket)

```mermaid
sequenceDiagram
    participant 👨‍⚕️ as Doctor + Patient
    participant 🖥️ as Frontend
    participant 🔌 as Socket.io
    participant 🎤 as Deepgram ASR
    participant 🧠 as Gemini LLM
    participant 💾 as MongoDB

    👨‍⚕️->>🖥️: Start consultation
    🖥️->>🔌: audio:start {patientId}
    🔌->>💾: Create consultation (status: active)
    🔌->>🎤: Open live stream
    🔌-->>🖥️: audio:ready {sessionId}

    rect rgb(30, 50, 80)
        Note over 👨‍⚕️,🎤: Live Recording Loop
        👨‍⚕️->>🖥️: Speaking...
        🖥️->>🔌: audio:chunk (every 250ms)
        🔌->>🎤: Forward audio buffer
        🎤-->>🔌: Interim transcript
        🔌-->>🖥️: transcript:interim {text, lang}
        🎤-->>🔌: Final transcript segment
        🔌-->>🖥️: transcript:final {text, lang}
    end

    👨‍⚕️->>🖥️: Stop recording
    🖥️->>🔌: audio:stop
    🔌->>🎤: Close stream

    rect rgb(60, 30, 80)
        Note over 🔌,💾: AI Extraction Pipeline
        🔌-->>🖥️: extraction:progress (30%)
        🔌->>🧠: Full transcript + languages
        🧠-->>🔌: Structured JSON
        🔌-->>🖥️: extraction:progress (70%)
        🔌->>🧠: Generate doctor suggestions
        🧠-->>🔌: Diagnoses + Warnings
        🔌-->>🖥️: extraction:progress (90%)
        🔌->>💾: Save complete consultation
    end

    🔌-->>🖥️: extraction:complete {structuredData, suggestions}
    🖥️-->>👨‍⚕️: Display results
```

---

## 3. What the LLM Actually Does

```mermaid
flowchart LR
    subgraph IN["Raw Transcript"]
        T1["'Doctor: Kya problem hai?<br/>Patient: Mujhe 3 din se<br/>bukhar hai aur sir mein<br/>bahut dard ho raha hai.<br/>Doctor: Temperature check<br/>karo... 101 hai. Crocin<br/>500mg do din mein<br/>teen baar, 5 din ke liye'"]
    end

    subgraph PROCESS["Gemini 2.0 Flash"]
        P1["🔤 Language Detection<br/>Hindi detected"]
        P2["🏥 Medical Term Mapping<br/>bukhar → Fever<br/>sir dard → Headache"]
        P3["💊 Drug Normalization<br/>Crocin → Paracetamol"]
        P4["⚠️ Missing Info Check"]
    end

    subgraph OUT["Structured JSON"]
        O1["✅ Chief Complaint:<br/>'Fever with headache'"]
        O2["✅ Symptoms:<br/>- Fever (3 days, moderate)<br/>- Headache (3 days, severe)"]
        O3["✅ Vitals:<br/>Temp: 101°F"]
        O4["✅ Medications:<br/>Paracetamol 500mg<br/>3x daily, 5 days"]
        O5["⚠️ Missing:<br/>- Allergy status<br/>- BP not recorded"]
    end

    IN --> PROCESS --> OUT

    style IN fill:#1a1a2e,color:#fff
    style PROCESS fill:#533483,color:#fff
    style OUT fill:#0f3460,color:#fff
```

---

## 4. Tech Stack Map

```mermaid
flowchart TB
    subgraph FRONTEND["🖥️ Frontend — Vite + React"]
        FE1["AudioRecorder<br/>(MediaRecorder API)"]
        FE2["LiveTranscript"]
        FE3["StructuredDataView"]
        FE4["RAG Chatbot"]
        FE5["Dashboard"]
    end

    subgraph BACKEND["⚙️ Backend — Node.js + Express"]
        BE1["REST API<br/>/api/patients<br/>/api/consultations<br/>/api/chat<br/>/api/prescriptions"]
        BE2["Socket.io Server<br/>(Real-time audio)"]
        BE3["Audio Pipeline<br/>Orchestrator"]
    end

    subgraph AI["🧠 AI Services"]
        AI1["Deepgram Nova-2<br/>Speech-to-Text<br/>(Streaming)"]
        AI2["Google Gemini 2.0 Flash<br/>Medical Data Extraction<br/>(JSON Mode)"]
        AI3["RAG Pipeline<br/>MongoDB Retrieval +<br/>Gemini Generation"]
    end

    subgraph DB["💾 Database"]
        DB1[("MongoDB<br/>Patients<br/>Consultations<br/>Prescriptions<br/>Doctors")]
    end

    FE1 -->|WebSocket| BE2
    FE2 & FE3 & FE5 -->|REST| BE1
    FE4 -->|REST| BE1
    BE2 --> BE3
    BE3 --> AI1
    BE3 --> AI2
    BE1 --> AI3
    AI1 -->|Transcript| BE3
    AI2 -->|Structured JSON| BE3
    AI3 -->|Answer| BE1
    BE1 & BE3 --> DB1
    AI3 --> DB1

    style FRONTEND fill:#16213e,color:#fff,stroke:#53a8b6
    style BACKEND fill:#1a1a2e,color:#fff,stroke:#e94560
    style AI fill:#533483,color:#fff,stroke:#e94560
    style DB fill:#0f3460,color:#fff,stroke:#53a8b6
```

---

## 5. Database Relationships

```mermaid
erDiagram
    DOCTOR ||--o{ CONSULTATION : "conducts"
    PATIENT ||--o{ CONSULTATION : "has many visits"
    CONSULTATION ||--|| STRUCTURED_DATA : "extracted by AI"
    CONSULTATION ||--o| PRESCRIPTION : "auto-generates"

    PATIENT {
        string patientId "PAT-123456"
        string name
        int age
        string gender
        string phone
    }

    CONSULTATION {
        string sessionId "SES-XYZ123"
        int visitNumber "1, 2, 3..."
        string rawTranscript
        string status "active → completed"
        date consultationDate
    }

    STRUCTURED_DATA {
        string chiefComplaint
        array symptoms "name + duration + severity"
        array diagnosis
        array medications "name + dosage + freq"
        object vitals "BP, temp, pulse"
        array flaggedIssues "⚠️ ambiguous items"
        array missingInfo "❌ critical gaps"
    }

    PRESCRIPTION {
        array medications
        string instructions
        boolean isAIGenerated "true"
    }
```

---

## 6. Team Task Division

```mermaid
flowchart LR
    subgraph T1["👤 Person 1: Frontend"]
        A1["Audio Recorder Component"]
        A2["Live Transcript UI"]
        A3["Dashboard + Patient List"]
        A4["Structured Data Cards"]
    end

    subgraph T2["👤 Person 2: Backend + AI"]
        B1["Express Server + Socket.io"]
        B2["Deepgram ASR Integration"]
        B3["Gemini Prompt Engineering"]
        B4["MongoDB Models + Routes"]
    end

    subgraph T3["👤 Person 3: Features + Polish"]
        C1["RAG Chatbot"]
        C2["Doctor Assist Panel"]
        C3["Prescription Generation"]
        C4["UI Polish + Animations"]
    end

    T1 <-->|Socket.io + REST| T2
    T2 <-->|RAG + Assist APIs| T3
    T1 <-->|Chat + Assist UI| T3

    style T1 fill:#16213e,color:#fff,stroke:#53a8b6
    style T2 fill:#533483,color:#fff,stroke:#e94560
    style T3 fill:#0f3460,color:#fff,stroke:#e94560
```

---

## Quick Reference: API Endpoints

| Method | Endpoint | What it does |
|--------|----------|-------------|
| `POST` | `/api/patients` | Register new patient (shared across all doctors) |
| `GET` | `/api/patients?q=search` | Search patients across entire clinic/system |
| `GET` | `/api/patients/:id/history` | Full visit history across all attending doctors |
| `POST` | `/api/consultations` | Process audio/transcript → structured data |
| `GET` | `/api/consultations/:sessionId` | Get consultation result (any doctor) |
| `POST` | `/api/chat` | Ask AI about patient history (RAG) |
| `POST` | `/api/prescriptions` | Auto-generate prescription |
| `POST` | `/api/sync` | **2-Way Offline Sync**: Push changes + resolve conflicts + pull cloud updates |
| `POST` | `/api/sync/push` | Push offline records to cloud DB |
| `GET` | `/api/sync/pull?since=...` | Pull recent changes from other doctors |
| `WS` | `audio:start → audio:chunk → audio:stop` | Live recording pipeline |

---

## 7. 📱 Offline-First Rural Architecture (100% On-Device AI)
> Built specifically for villages, PHCs, and low-connectivity regions. **Zero internet required during consultation.**

```mermaid
flowchart TB
    subgraph DEVICE["📱 ANDROID DEVICE (Zero Internet / Offline)"]
        direction TB
        
        subgraph UI_LAYER["🎨 Consistent UI (Capacitor + React Web App)"]
            UI1["Unified Web/Mobile UI<br/>(TailwindCSS + Lucide Icons)"]
            UI2["Audio Recorder View<br/>(Visual Waveforms + Live Feedback)"]
            UI3["Doctor Assist Toggle<br/>🔘 AI Recommendations: [ON / OFF]"]
            UI4["Offline Status Badge<br/>📶 'Offline Mode - 3 Consultations Queued'"]
        end

        subgraph LOCAL_AI["🤖 Local On-Device AI Engine"]
            STT["👂 ASR: Whisper Small / Base (~150MB)<br/>• Powered by Sherpa-ONNX / Whisper.cpp<br/>• Runs on mobile CPU/DSP<br/>• Real-time Hindi / Marathi / English"]
            LLM["🧠 Brain: Google Gemma 2 2B-IT (~1.4GB)<br/>• Powered by MediaPipe GenAI / LiteRT (GPU/NPU)<br/>• Prompt: Structured Clinical Extraction<br/>• Optional: Medical advice & differential flags"]
        end

        subgraph LOCAL_STORAGE["💾 Local Offline Database"]
            LDB[("Local SQLite / IndexedDB<br/>• Schema identical to MongoDB<br/>• Version tracking per record<br/>• Sync Queue: pending | synced | conflict")]
        end

        UI2 -->|Live Mic Audio| STT
        STT -->|Raw Transcript| UI1
        STT -->|Raw Transcript| LLM
        UI3 -.->|"Prompt Config: Assist ON/OFF"| LLM
        LLM -->|Structured Medical JSON| UI1
        LLM -->|Prescription & Notes| LDB
        UI1 -->|Patient & Consult Updates| LDB
    end

    subgraph CLOUD["☁️ CENTRAL CLOUD / CLINIC HUB (When Net Connects)"]
        direction TB
        SYNC_API["🔄 /api/sync Endpoint<br/>(Conflict Resolution & Version Merge)"]
        CENTRAL_DB[("🌐 MongoDB Cloud<br/>Universal Patient Database<br/>Accessible by ANY Doctor")]
        SYNC_API <--> CENTRAL_DB
    end

    LDB <===>|Auto-Sync via Wi-Fi/4G/Hotspot| SYNC_API

    style DEVICE fill:#0f172a,color:#fff,stroke:#38bdf8,stroke-width:2px
    style UI_LAYER fill:#1e293b,color:#fff,stroke:#38bdf8
    style LOCAL_AI fill:#1e1b4b,color:#fff,stroke:#a855f7
    style LOCAL_STORAGE fill:#14532d,color:#fff,stroke:#22c55e
    style CLOUD fill:#1e293b,color:#fff,stroke:#f59e0b,stroke-width:2px
```

---

## 8. 🔄 Offline-to-Cloud Sync & Conflict Resolution Pipeline
> Resolves conflicts when multiple doctors edit patients or consultations simultaneously while offline.

```mermaid
sequenceDiagram
    autonumber
    participant 📱 as Doctor A (Android Offline)
    participant 💻 as Doctor B (Web / Clinic)
    participant ⚙️ as Cloud Server (/api/sync)
    participant 🗄️ as MongoDB Cloud

    Note over 📱: Doctor A records in village without internet.<br/>Saved locally (version: 1, syncStatus: pending)
    
    Note over 💻: Doctor B updates patient address on Web.<br/>Saved to cloud (version: 2, updatedAt: 10:15 AM)
    
    📱->>📱: Internet connection restored (4G / Wi-Fi detected)
    
    rect rgb(30, 45, 75)
        Note over 📱,⚙️: Bidirectional Sync Phase (POST /api/sync)
        📱->>⚙️: Push Offline Changes { patients: [...], consultations: [...] }<br/>with lastSyncTimestamp = 10:00 AM
        
        Note over ⚙️: Conflict Resolution Engine runs:<br/>1. Unique sessionId prevents consultation overwrite<br/>2. Allergies & Medical History: Non-destructive Set Union<br/>3. Demographics: Last-Write-Wins by timestamp<br/>4. Version incremented: version = 3
        
        ⚙️->>🗄️: Commit merged Patient & Consultation
        🗄️-->>⚙️: Merged state saved
        
        ⚙️->>🗄️: Fetch updates from other doctors since 10:00 AM
        🗄️-->>⚙️: Return Doctor B's updates
        
        ⚙️-->>📱: { success: true, pushed: [...], conflicts: [], serverUpdates: [...] }
        📱->>📱: Update local SQLite/IndexedDB to version 3 (syncStatus: synced)
    end
    
    Note over 📱,💻: Both devices are now 100% in sync with exact same medical history!
```

---

## 9. 🎨 Cross-Platform UI Consistency Strategy (Web & Native Kotlin)

To ensure **zero learning curve** for doctors switching between the Web dashboard and the Android mobile app:

1. **Shared Design Tokens & Color Palette (Jetpack Compose Material3):**
   - The Android app is written in **100% Native Kotlin using Jetpack Compose**.
   - Design tokens map directly from the web app: Outfit typography, Slate dark surfaces (`#0F172A`, `#1E293B`), Sky blue accent (`#0EA5E9`), Emerald status indicators (`#10B981`), and Indigo AI accents (`#6366F1`).
   - Transcription chat bubbles, medical summary cards, and prescription layouts match the web app pixel-for-pixel.

2. **Adaptive Responsive Layout:**
   - **Desktop Web:** Split-screen layout (Audio Recorder on Left, Live Structured Cards on Right).
   - **Android Native App:** Bottom Navigation + Tabbed screens (`[Dashboard]`, `[Consultation]`, `[History]`) optimized for single-handed doctor usage on smartphones and clinical tablets.

3. **Doctor Assist Toggle (Performance & Autonomy):**
   - A dedicated toggle on both Web and Mobile:
     - **OFF (Default):** Extracts only spoken facts, medications, vitals, and symptoms. Saves mobile battery and CPU execution time by 50%.
     - **ON:** Activates Gemma 2B's reasoning to suggest differential diagnoses, warning flags, and test recommendations.

---

## 10. 📱 Android App Internal Subsystems Diagram (Native Kotlin)

```mermaid
flowchart TB
    subgraph APP["📱 ANDROID NATIVE KOTLIN APPLICATION"]
        direction TB

        subgraph LAYER1["1. UI & Presentation (Jetpack Compose + Material3)"]
            UI_REC["🎙️ Audio Recorder & Visualizer"]
            UI_TRANS["💬 Live Transcript (Hindi/Marathi/English)"]
            UI_NOTES["📋 Structured Medical Cards"]
            UI_RX["💊 Prescription & PDF Generator"]
            UI_SEARCH["👥 Patient Lookup (Universal All-Doctor Access)"]
            UI_TOGGLE["🔘 Doctor Assist: [ON / OFF]"]
        end

        subgraph LAYER2["2. Native Audio Subsystem (Android NDK / AudioRecord)"]
            MIC_IN["🎤 Phone Mic (16kHz, 16-bit Mono PCM)"]
            CIRC_BUFFER["🔄 10s Circular Audio Ring Buffer"]
            VAD_FILTER["🔇 Voice Activity Detection (Silero VAD)"]
            MIC_IN --> CIRC_BUFFER --> VAD_FILTER
        end

        subgraph LAYER3["3. Local AI Engine (On-Device Inference)"]
            STT_ENGINE["👂 Whisper Small ASR (~150MB, Sherpa-ONNX)<br/>• Real-time Speech-to-Text<br/>• Multi-threaded ARM NEON SIMD<br/>• Low RAM (~180MB)"]
            LLM_ENGINE["🧠 Google Gemma 2 2B-IT (~1.4GB, MediaPipe)<br/>• Medical JSON Structuring<br/>• GPU/NPU Accelerated (Vulkan/OpenCL)<br/>• Fast Inference (~15 tok/sec)"]
            VAD_FILTER -->|Clean Voice Audio| STT_ENGINE
            STT_ENGINE -->|Text Transcript| LLM_ENGINE
        end

        subgraph LAYER4["4. Local Persistence (SQLite / Room Database)"]
            DB_PATIENTS[("Table: patients<br/>(patientId, name, allergies, version)")]
            DB_CONSULT[("Table: consultations<br/>(sessionId, transcript, structuredData)")]
            DB_RX[("Table: prescriptions<br/>(medications, instructions)")]
            DB_QUEUE[("Table: sync_queue<br/>(pending offline operations)")]
        end

        subgraph LAYER5["5. Background Sync Orchestrator"]
            NET_WATCH["📶 ConnectivityManager (Detects 4G/Wi-Fi)"]
            WORK_MGR["⏱️ Android WorkManager (Background Sync)"]
            CONF_RES["⚖️ Conflict Resolver (OCC Versioning + Array Union)"]
            NET_WATCH --> WORK_MGR --> CONF_RES
        end

        LAYER1 <--> LAYER2
        STT_ENGINE -->|Live Interim Text| LAYER1
        LLM_ENGINE -->|Structured JSON| LAYER1
        LAYER1 <--> LAYER4
        LAYER4 <--> CONF_RES
    end

    subgraph BACKEND_CLOUD["☁️ VOICECARE CLOUD SERVER"]
        API_SYNC["POST /api/sync<br/>(Bidirectional Sync + Conflict Resolution)"]
        MONGO_DB[("MongoDB Cloud Cluster<br/>(Universal All-Doctor Patient Database)")]
        API_SYNC <--> MONGO_DB
    end

    CONF_RES <===>|Auto-Sync on Internet Connection| API_SYNC

    style APP fill:#0f172a,color:#fff,stroke:#38bdf8,stroke-width:2px
    style LAYER1 fill:#1e293b,color:#fff,stroke:#38bdf8
    style LAYER2 fill:#16213e,color:#fff,stroke:#0284c7
    style LAYER3 fill:#1e1b4b,color:#fff,stroke:#a855f7,stroke-width:2px
    style LAYER4 fill:#064e3b,color:#fff,stroke:#10b981
    style LAYER5 fill:#7c2d12,color:#fff,stroke:#f97316
    style BACKEND_CLOUD fill:#1e293b,color:#fff,stroke:#fbbf24,stroke-width:2px
```


