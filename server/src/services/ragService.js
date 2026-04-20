import Consultation from '../models/Consultation.js';
import { buildRAGPrompt } from '../prompts/chatbot.js';
import { generateRAGResponse } from './llmService.js';

/**
 * RAG Service - Retrieval Augmented Generation for patient history queries
 * 
 * Strategy: Since we're in a hackathon with MongoDB already set up,
 * we use MongoDB text search + recency ranking as our retrieval layer
 * instead of a separate vector DB. This keeps the stack simpler while
 * still delivering the RAG functionality.
 * 
 * For production, swap this retrieval layer with ChromaDB/Pinecone vectors.
 */

/**
 * Answer a doctor's natural language query about a patient's history
 * @param {string} patientId - The patient to query about
 * @param {string} query - Natural language question from the doctor
 * @returns {object} - { answer, sources }
 */
export const queryPatientHistory = async (patientId, query) => {
  try {
    // Step 1: Retrieve relevant consultations for this patient
    const consultations = await Consultation.find({
      patientId,
      status: 'completed',
    })
      .sort({ consultationDate: -1 })
      .limit(10)
      .lean();

    if (consultations.length === 0) {
      return {
        answer: `No consultation records found for patient ${patientId}.`,
        sources: [],
      };
    }

    // Step 2: Prepare context documents
    const contextDocs = consultations.map((c) => ({
      sessionId: c.sessionId,
      visitNumber: c.visitNumber,
      date: c.consultationDate?.toISOString?.() || 'Unknown',
      data: {
        chiefComplaint: c.structuredData?.chiefComplaint,
        symptoms: c.structuredData?.symptoms,
        diagnosis: c.structuredData?.diagnosis,
        medications: c.structuredData?.medications,
        vitals: c.structuredData?.vitals,
        allergies: c.structuredData?.allergies,
        followUp: c.structuredData?.followUp,
      },
    }));

    // Step 3: Build RAG prompt and generate response
    const ragPrompt = buildRAGPrompt(query, contextDocs);
    const answer = await generateRAGResponse(ragPrompt);

    // Step 4: Return answer with source references
    return {
      answer,
      sources: contextDocs.map((d) => ({
        sessionId: d.sessionId,
        visitNumber: d.visitNumber,
        date: d.date,
      })),
    };
  } catch (error) {
    console.error('❌ RAG query error:', error.message);
    throw error;
  }
};

/**
 * Get a summary of all visits for a patient (for quick context)
 */
export const getPatientSummary = async (patientId) => {
  const consultations = await Consultation.find({
    patientId,
    status: 'completed',
  })
    .sort({ consultationDate: -1 })
    .lean();

  if (consultations.length === 0) {
    return { totalVisits: 0, summary: 'No consultation history.' };
  }

  const allDiagnoses = [...new Set(consultations.flatMap((c) => c.structuredData?.diagnosis || []))];
  const allMedications = [...new Set(consultations.flatMap((c) =>
    (c.structuredData?.medications || []).map((m) => m.name)
  ))];
  const allAllergies = [...new Set(consultations.flatMap((c) => c.structuredData?.allergies || []))];

  return {
    totalVisits: consultations.length,
    lastVisit: consultations[0].consultationDate,
    diagnoses: allDiagnoses,
    medicationsHistory: allMedications,
    knownAllergies: allAllergies,
    recentComplaint: consultations[0].structuredData?.chiefComplaint || 'Unknown',
  };
};
