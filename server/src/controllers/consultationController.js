import Patient from '../models/Patient.js';
import Prescription from '../models/Prescription.js';
import Consultation from '../models/Consultation.js';
import { generateSessionId } from '../utils/idGenerator.js';
import { extractMedicalData, generateDoctorAssist } from '../services/llmService.js';
import { transcribeAudioFile } from '../services/asrService.js';
import { getPatientSummary } from '../services/ragService.js';
import { generateReportPdfBuffer } from '../services/reportPdfService.js';

const getDoctorId = (req) => req.doctorId || 'DOC-DEFAULT';

// POST /api/consultations - Start a new consultation (or process uploaded audio)
export const createConsultation = async (req, res, next) => {
  try {
    const { patientId, transcript } = req.body;
    const doctorId = getDoctorId(req);

    if (!patientId) {
      return res.status(400).json({ error: 'Patient ID is required' });
    }

    const sessionId = generateSessionId();
    const visitCount = await Consultation.countDocuments({ patientId, doctorId });

    // Case 1: Audio file uploaded — full pipeline
    if (req.file) {
      // Step 1: Transcribe
      const asrResult = await transcribeAudioFile(req.file.buffer, req.file.mimetype);
      const cleanedTranscript = (asrResult.transcript || '').trim();

      if (!cleanedTranscript) {
        return res.status(422).json({
          error: 'No speech detected in audio. Please re-record and speak clearly.',
          code: 'EMPTY_TRANSCRIPT',
        });
      }

      // Step 2: Extract medical data
      const structuredData = await extractMedicalData(
        cleanedTranscript,
        [asrResult.detectedLanguage]
      );

      // Step 3: Generate doctor assist
      let aiSuggestions = {};
      try {
        aiSuggestions = await generateDoctorAssist(structuredData);
      } catch {}

      // Step 4: Save
      const consultation = new Consultation({
        patientId,
        doctorId,
        sessionId,
        visitNumber: visitCount + 1,
        status: 'completed',
        rawTranscript: cleanedTranscript,
        detectedLanguages: [asrResult.detectedLanguage],
        transcriptSegments: asrResult.segments || [],
        structuredData,
        aiSuggestions,
      });

      await consultation.save();

      return res.status(201).json({
        success: true,
        consultation: {
          sessionId,
          patientId,
          visitNumber: visitCount + 1,
          transcript: cleanedTranscript,
          structuredData,
          aiSuggestions,
          languages: [asrResult.detectedLanguage],
        },
      });
    }

    // Case 2: Raw transcript provided — extraction only
    if (transcript) {
      const cleanedTranscript = String(transcript).trim();
      if (!cleanedTranscript) {
        return res.status(422).json({
          error: 'Transcript is empty. Please provide valid speech text.',
          code: 'EMPTY_TRANSCRIPT',
        });
      }

      const structuredData = await extractMedicalData(cleanedTranscript);

      // Doctor assist is optional (saves 1 API call) — enable with ?assist=true
      let aiSuggestions = {};
      if (req.query.assist === 'true') {
        try {
          aiSuggestions = await generateDoctorAssist(structuredData);
        } catch {}
      }

      const consultation = new Consultation({
        patientId,
        doctorId,
        sessionId,
        visitNumber: visitCount + 1,
        status: 'completed',
        rawTranscript: cleanedTranscript,
        structuredData,
        aiSuggestions,
      });
      await consultation.save();

      return res.status(201).json({
        success: true,
        consultation: {
          sessionId,
          patientId,
          visitNumber: visitCount + 1,
          transcript: cleanedTranscript,
          structuredData,
          aiSuggestions,
        },
      });
    }

    // Case 3: Just creating a session (for live recording via WebSocket)
    const consultation = new Consultation({
      patientId,
      doctorId,
      sessionId,
      visitNumber: visitCount + 1,
      status: 'active',
    });
    await consultation.save();

    res.status(201).json({
      success: true,
      consultation: {
        sessionId,
        patientId,
        visitNumber: visitCount + 1,
        status: 'active',
      },
    });
  } catch (error) {
    next(error);
  }
};

// GET /api/consultations/:sessionId - Get a consultation
export const getConsultationById = async (req, res, next) => {
  try {
    const doctorId = getDoctorId(req);
    const consultation = await Consultation.findOne({ sessionId: req.params.sessionId, doctorId }).lean();
    if (!consultation) {
      return res.status(404).json({ error: 'Consultation not found' });
    }
    res.json({ success: true, consultation });
  } catch (error) {
    next(error);
  }
};

// GET /api/consultations/:sessionId/report - Get a readable report for a specific encounter
export const getConsultationReport = async (req, res, next) => {
  try {
    const doctorId = getDoctorId(req);
    const consultation = await Consultation.findOne({ sessionId: req.params.sessionId, doctorId }).lean();
    if (!consultation) {
      return res.status(404).json({ error: 'Consultation not found' });
    }

    const [patient, prescription, summary] = await Promise.all([
      Patient.findOne({ patientId: consultation.patientId, doctorId }).lean(),
      Prescription.findOne({ consultationId: consultation.sessionId, doctorId }).lean(),
      getPatientSummary(consultation.patientId),
    ]);

    const report = {
      generatedAt: new Date().toISOString(),
      patient,
      summary,
      consultation: {
        sessionId: consultation.sessionId,
        visitNumber: consultation.visitNumber,
        date: consultation.consultationDate,
        doctorId: consultation.doctorId,
        status: consultation.status,
        chiefComplaint: consultation.structuredData?.chiefComplaint || '',
        symptoms: consultation.structuredData?.symptoms || [],
        diagnosis: consultation.structuredData?.diagnosis || [],
        medications: consultation.structuredData?.medications || [],
        vitals: consultation.structuredData?.vitals || {},
        flaggedIssues: consultation.structuredData?.flaggedIssues || [],
        missingInfo: consultation.structuredData?.missingInfo || [],
        followUp: consultation.structuredData?.followUp || '',
        additionalNotes: consultation.structuredData?.additionalNotes || '',
        transcript: consultation.rawTranscript || '',
        aiSuggestions: consultation.aiSuggestions || {},
      },
      prescription: prescription
        ? {
            id: prescription._id,
            medications: prescription.medications || [],
            diagnosis: prescription.diagnosis || [],
            generalInstructions: prescription.generalInstructions || '',
            followUpDate: prescription.followUpDate || null,
            validUntil: prescription.validUntil || null,
          }
        : null,
    };

    const reportText = buildConsultationReportText(report);

    if (req.query.format === 'json') {
      return res.json({
        success: true,
        report: {
          ...report,
          text: reportText,
        },
      });
    }

    const pdfBuffer = await generateReportPdfBuffer({
      title: 'VoiceCare Encounter Report',
      subtitle: `${patient?.name || 'Unknown Patient'} (${consultation.sessionId})`,
      content: reportText,
    });

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${consultation.sessionId}-encounter-report.pdf"`);
    return res.send(pdfBuffer);
  } catch (error) {
    next(error);
  }
};

// GET /api/consultations - List recent consultations
export const getConsultations = async (req, res, next) => {
  try {
    const doctorId = getDoctorId(req);
    const { patientId, status, page = 1, limit = 20 } = req.query;
    const skip = (page - 1) * limit;

    const query = { doctorId };
    if (patientId) query.patientId = patientId;
    if (status) query.status = status;

    const [consultations, total] = await Promise.all([
      Consultation.find(query)
        .sort({ consultationDate: -1 })
        .skip(skip)
        .limit(parseInt(limit))
        .lean(),
      Consultation.countDocuments(query),
    ]);

    res.json({
      success: true,
      consultations,
      pagination: { page: parseInt(page), limit: parseInt(limit), total },
    });
  } catch (error) {
    next(error);
  }
};

function buildConsultationReportText(report) {
  const consultation = report.consultation;
  const patient = report.patient || {};
  const lines = [];

  lines.push('VoiceCare Encounter Report');
  lines.push(`Generated at: ${new Date(report.generatedAt).toLocaleString()}`);
  lines.push(`Patient: ${patient.name || 'N/A'} (${patient.patientId || consultation.patientId || 'N/A'})`);
  lines.push(`Age/Gender: ${patient.age || 'N/A'} / ${patient.gender || 'N/A'}`);
  lines.push(`Doctor: ${consultation.doctorId || 'N/A'}`);
  lines.push(`Encounter: #${consultation.visitNumber || 'N/A'}`);
  lines.push(`Date: ${consultation.date ? new Date(consultation.date).toLocaleString() : 'N/A'}`);
  lines.push(`Session: ${consultation.sessionId}`);
  lines.push(`Status: ${consultation.status || 'completed'}`);
  lines.push('');

  lines.push('SUMMARY');
  lines.push(`Recent complaint: ${report.summary?.recentComplaint || consultation.chiefComplaint || 'N/A'}`);
  lines.push(`Diagnoses: ${(consultation.diagnosis || []).join(', ') || 'N/A'}`);
  lines.push(`Medications: ${(consultation.medications || []).map((med) => med.name).join(', ') || 'N/A'}`);
  lines.push(`Follow-up: ${consultation.followUp || 'N/A'}`);
  lines.push(`Flags: ${(consultation.flaggedIssues || []).join(', ') || 'N/A'}`);
  lines.push('');

  if (consultation.symptoms && consultation.symptoms.length > 0) {
    lines.push('SYMPTOMS');
    consultation.symptoms.forEach((symptom) => {
      lines.push(`- ${symptom.name || 'Unknown'}${symptom.severity ? ` (${symptom.severity})` : ''}${symptom.duration ? ` - ${symptom.duration}` : ''}`);
    });
    lines.push('');
  }

  if (consultation.vitals && Object.keys(consultation.vitals).length > 0) {
    lines.push('VITALS');
    Object.entries(consultation.vitals).forEach(([key, value]) => {
      if (value) lines.push(`${key}: ${value}`);
    });
    lines.push('');
  }

  lines.push('CLINICAL NOTES');
  lines.push(consultation.additionalNotes || 'N/A');
  lines.push('');
  lines.push('TRANSCRIPT');
  lines.push(consultation.transcript || 'Not available');
  lines.push('');

  if (report.prescription) {
    lines.push('PRESCRIPTION');
    lines.push(`General instructions: ${report.prescription.generalInstructions || 'N/A'}`);
    lines.push(`Follow-up date: ${report.prescription.followUpDate ? new Date(report.prescription.followUpDate).toLocaleDateString() : 'N/A'}`);
    lines.push(`Valid until: ${report.prescription.validUntil ? new Date(report.prescription.validUntil).toLocaleDateString() : 'N/A'}`);
    lines.push('Medications:');
    (report.prescription.medications || []).forEach((medication) => {
      lines.push(`- ${medication.name || 'Unknown'} | ${medication.dosage || 'N/A'} | ${medication.frequency || 'N/A'} | ${medication.duration || 'N/A'}`);
    });
  }

  return lines.join('\n');
}

// PATCH /api/consultations/:sessionId - Update consultation (e.g., doctor edits)
export const updateConsultation = async (req, res, next) => {
  try {
    const doctorId = getDoctorId(req);
    const updates = req.body;
    delete updates.sessionId;
    delete updates.patientId;
    delete updates.doctorId;

    const consultation = await Consultation.findOneAndUpdate(
      { sessionId: req.params.sessionId, doctorId },
      { $set: updates },
      { new: true }
    );

    if (!consultation) {
      return res.status(404).json({ error: 'Consultation not found' });
    }

    res.json({ success: true, consultation });
  } catch (error) {
    next(error);
  }
};
