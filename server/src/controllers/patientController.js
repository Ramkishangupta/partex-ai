import Patient from '../models/Patient.js';
import Prescription from '../models/Prescription.js';
import { generatePatientId } from '../utils/idGenerator.js';
import { getPatientSummary } from '../services/ragService.js';
import { generateReportPdfBuffer } from '../services/reportPdfService.js';

const getDoctorScope = (req) => ({ doctorId: req.doctorId || 'DOC-DEFAULT' });

// POST /api/patients - Create a new patient
export const createPatient = async (req, res, next) => {
  try {
    const scope = getDoctorScope(req);
    const { name, age, gender, phone, address, bloodGroup, allergies, medicalHistory } = req.body;

    if (!name) {
      return res.status(400).json({ error: 'Patient name is required' });
    }

    const patient = new Patient({
      doctorId: scope.doctorId,
      patientId: generatePatientId(),
      name,
      age,
      gender,
      phone,
      address,
      bloodGroup,
      allergies: allergies || [],
      medicalHistory: medicalHistory || [],
    });

    await patient.save();
    res.status(201).json({ success: true, patient });
  } catch (error) {
    next(error);
  }
};

// GET /api/patients - List all patients (with optional search)
export const getPatients = async (req, res, next) => {
  try {
    const scope = getDoctorScope(req);
    const { q, page = 1, limit = 20 } = req.query;
    const skip = (page - 1) * limit;

    let query = { ...scope };
    if (q) {
      query = {
        ...scope,
        $or: [
          { name: { $regex: q, $options: 'i' } },
          { patientId: { $regex: q, $options: 'i' } },
          { phone: { $regex: q, $options: 'i' } },
        ],
      };
    }

    const [patients, total] = await Promise.all([
      Patient.find(query).sort({ updatedAt: -1 }).skip(skip).limit(parseInt(limit)).lean(),
      Patient.countDocuments(query),
    ]);

    res.json({
      success: true,
      patients,
      pagination: { page: parseInt(page), limit: parseInt(limit), total, pages: Math.ceil(total / limit) },
    });
  } catch (error) {
    next(error);
  }
};

// GET /api/patients/:id - Get a single patient by patientId
export const getPatientById = async (req, res, next) => {
  try {
    const scope = getDoctorScope(req);
    const patient = await Patient.findOne({ patientId: req.params.id, ...scope }).lean();
    if (!patient) {
      return res.status(404).json({ error: 'Patient not found' });
    }
    res.json({ success: true, patient });
  } catch (error) {
    next(error);
  }
};

// PUT /api/patients/:id - Update patient
export const updatePatient = async (req, res, next) => {
  try {
    const scope = getDoctorScope(req);
    const updates = req.body;
    delete updates.patientId; // Don't allow ID changes
    delete updates.doctorId;

    const patient = await Patient.findOneAndUpdate(
      { patientId: req.params.id, ...scope },
      updates,
      { new: true, runValidators: true }
    );

    if (!patient) {
      return res.status(404).json({ error: 'Patient not found' });
    }

    res.json({ success: true, patient });
  } catch (error) {
    next(error);
  }
};

// GET /api/patients/:id/history - Get full visit history
export const getPatientHistory = async (req, res, next) => {
  try {
    const scope = getDoctorScope(req);
    const { default: Consultation } = await import('../models/Consultation.js');

    const patient = await Patient.findOne({ patientId: req.params.id, ...scope }).lean();
    if (!patient) {
      return res.status(404).json({ error: 'Patient not found' });
    }

    const consultations = await Consultation.find({ patientId: req.params.id, ...scope })
      .sort({ consultationDate: -1 })
      .lean();

    res.json({
      success: true,
      patient,
      totalVisits: consultations.length,
      consultations,
    });
  } catch (error) {
    next(error);
  }
};

// GET /api/patients/:id/report - Get a doctor-friendly report summary for a patient
export const getPatientReport = async (req, res, next) => {
  try {
    const scope = getDoctorScope(req);
    const { default: Consultation } = await import('../models/Consultation.js');

    const patient = await Patient.findOne({ patientId: req.params.id, ...scope }).lean();
    if (!patient) {
      return res.status(404).json({ error: 'Patient not found' });
    }

    const [consultations, prescriptions, summary] = await Promise.all([
      Consultation.find({ patientId: req.params.id, ...scope })
        .sort({ consultationDate: -1 })
        .lean(),
      Prescription.find({ patientId: req.params.id, ...scope })
        .sort({ createdAt: -1 })
        .lean(),
      getPatientSummary(req.params.id),
    ]);

    const report = {
      generatedAt: new Date().toISOString(),
      patient,
      summary,
      totalConsultations: consultations.length,
      totalPrescriptions: prescriptions.length,
      consultations: consultations.map((consultation) => ({
        sessionId: consultation.sessionId,
        visitNumber: consultation.visitNumber,
        date: consultation.consultationDate,
        doctorId: consultation.doctorId,
        chiefComplaint: consultation.structuredData?.chiefComplaint || '',
        diagnosis: consultation.structuredData?.diagnosis || [],
        medications: consultation.structuredData?.medications || [],
        vitals: consultation.structuredData?.vitals || {},
        followUp: consultation.structuredData?.followUp || '',
        flaggedIssues: consultation.structuredData?.flaggedIssues || [],
        transcript: consultation.rawTranscript || '',
      })),
      prescriptions: prescriptions.map((prescription) => ({
        id: prescription._id,
        consultationId: prescription.consultationId,
        createdAt: prescription.createdAt,
        medications: prescription.medications || [],
        diagnosis: prescription.diagnosis || [],
        generalInstructions: prescription.generalInstructions || '',
        followUpDate: prescription.followUpDate || null,
        validUntil: prescription.validUntil || null,
      })),
    };

    const reportText = buildPatientReportText(report);

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
      title: 'VoiceCare Patient Report',
      subtitle: `${report.patient.name} (${report.patient.patientId})`,
      content: reportText,
    });

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${req.params.id}-report.pdf"`);
    return res.send(pdfBuffer);
  } catch (error) {
    next(error);
  }
};

function buildPatientReportText(report) {
  const lines = [];

  lines.push('VoiceCare Patient Report');
  lines.push(`Generated at: ${new Date(report.generatedAt).toLocaleString()}`);
  lines.push(`Patient: ${report.patient.name} (${report.patient.patientId})`);
  lines.push(`Age/Gender: ${report.patient.age || 'N/A'} / ${report.patient.gender || 'N/A'}`);
  lines.push(`Blood group: ${report.patient.bloodGroup || 'N/A'}`);
  lines.push(`Phone: ${report.patient.phone || 'N/A'}`);
  lines.push(`Total consultations: ${report.totalConsultations}`);
  lines.push(`Total prescriptions: ${report.totalPrescriptions}`);
  lines.push('');
  lines.push('SUMMARY');
  lines.push(`Recent complaint: ${report.summary?.recentComplaint || 'N/A'}`);
  lines.push(`Diagnoses: ${(report.summary?.diagnoses || []).join(', ') || 'N/A'}`);
  lines.push(`Medication history: ${(report.summary?.medicationsHistory || []).join(', ') || 'N/A'}`);
  lines.push(`Known allergies: ${(report.summary?.knownAllergies || []).join(', ') || 'N/A'}`);
  lines.push('');

  if (report.consultations.length === 0) {
    lines.push('No consultation records available.');
  } else {
    lines.push('CONSULTATIONS');
    report.consultations.forEach((consultation) => {
      lines.push(`Encounter #${consultation.visitNumber} | ${consultation.date ? new Date(consultation.date).toLocaleString() : 'Unknown date'}`);
      lines.push(`Session: ${consultation.sessionId}`);
      lines.push(`Chief complaint: ${consultation.chiefComplaint || 'N/A'}`);
      lines.push(`Diagnosis: ${(consultation.diagnosis || []).join(', ') || 'N/A'}`);
      lines.push(`Medications: ${(consultation.medications || []).map((med) => med.name).join(', ') || 'N/A'}`);
      lines.push(`Follow-up: ${consultation.followUp || 'N/A'}`);
      lines.push(`Flags: ${(consultation.flaggedIssues || []).join(', ') || 'N/A'}`);
      lines.push('');
    });
  }

  if (report.prescriptions.length > 0) {
    lines.push('PRESCRIPTIONS');
    report.prescriptions.forEach((prescription, index) => {
      lines.push(`Prescription #${index + 1} | Consultation: ${prescription.consultationId}`);
      lines.push(`Medications: ${(prescription.medications || []).map((med) => med.name).join(', ') || 'N/A'}`);
      lines.push(`Instructions: ${prescription.generalInstructions || 'N/A'}`);
      lines.push(`Follow-up date: ${prescription.followUpDate ? new Date(prescription.followUpDate).toLocaleDateString() : 'N/A'}`);
      lines.push('');
    });
  }

  return lines.join('\n');
}
