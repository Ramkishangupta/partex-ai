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
| `POST` | `/api/patients` | Register new patient |
| `GET` | `/api/patients?q=search` | Search patients |
| `GET` | `/api/patients/:id/history` | Full visit history |
| `POST` | `/api/consultations` | Process audio/transcript → structured data |
| `GET` | `/api/consultations/:sessionId` | Get consultation result |
| `POST` | `/api/chat` | Ask AI about patient history (RAG) |
| `POST` | `/api/prescriptions` | Auto-generate prescription |
| `WS` | `audio:start → audio:chunk → audio:stop` | Live recording pipeline |
