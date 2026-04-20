import mongoose from 'mongoose';

// Sub-schema for structured medical data extracted by LLM
const symptomDetailSchema = new mongoose.Schema({
  name: String,
  duration: String,
  severity: { type: String, enum: ['mild', 'moderate', 'severe', 'unknown'], default: 'unknown' },
  notes: String,
}, { _id: false });

const medicationSchema = new mongoose.Schema({
  name: String,
  dosage: String,
  frequency: String,
  duration: String,
  route: { type: String, enum: ['oral', 'iv', 'topical', 'inhaled', 'injection', 'other'], default: 'oral' },
  notes: String,
}, { _id: false });

const vitalsSchema = new mongoose.Schema({
  bp: String,
  temperature: String,
  pulse: String,
  spo2: String,
  weight: String,
  respiratoryRate: String,
}, { _id: false });

const structuredDataSchema = new mongoose.Schema({
  chiefComplaint: String,
  symptoms: [symptomDetailSchema],
  diagnosis: [String],
  medications: [medicationSchema],
  vitals: vitalsSchema,
  allergies: [String],
  flaggedIssues: [String],        // Ambiguous or concerning items
  missingInfo: [String],           // Critical info not mentioned
  followUp: String,
  additionalNotes: String,
}, { _id: false });

// Main consultation schema
const consultationSchema = new mongoose.Schema({
  patientId: {
    type: String,
    required: true,
    index: true,
  },
  doctorId: {
    type: String,
    default: 'DOC-DEFAULT',
  },
  sessionId: {
    type: String,
    unique: true,
    required: true,
  },
  visitNumber: {
    type: Number,
    default: 1,
  },
  status: {
    type: String,
    enum: ['active', 'processing', 'completed', 'failed'],
    default: 'active',
  },
  // Raw transcript from ASR
  rawTranscript: {
    type: String,
    default: '',
  },
  // Detected languages during conversation
  detectedLanguages: [{
    type: String,
  }],
  // Speaker-diarized transcript segments
  transcriptSegments: [{
    speaker: String,      // "speaker_0", "speaker_1"
    text: String,
    language: String,
    startTime: Number,
    endTime: Number,
    confidence: Number,
  }],
  // LLM-extracted structured data
  structuredData: structuredDataSchema,
  // AI-generated suggestions (Doctor Assist)
  aiSuggestions: {
    possibleDiagnoses: [{
      condition: String,
      confidence: String,
      reasoning: String
    }],
    recommendedTests: [{
      test: String,
      reason: String,
      urgency: String
    }],
    warnings: [String],
    drugInteractions: [{
      drug1: String,
      drug2: String,
      severity: String,
      effect: String
    }],
  },
  // Audio metadata
  audioMetadata: {
    durationSeconds: Number,
    format: String,
    sampleRate: Number,
  },
  consultationDate: {
    type: Date,
    default: Date.now,
  },
}, {
  timestamps: true,
});

consultationSchema.index({ patientId: 1, consultationDate: -1 });

const Consultation = mongoose.model('Consultation', consultationSchema);
export default Consultation;
