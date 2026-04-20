import mongoose from 'mongoose';

const prescriptionSchema = new mongoose.Schema({
  consultationId: {
    type: String,
    required: true,
    index: true,
  },
  patientId: {
    type: String,
    required: true,
    index: true,
  },
  doctorId: {
    type: String,
    required: true,
  },
  medications: [{
    name: String,
    dosage: String,
    frequency: String,
    duration: String,
    route: { type: String, default: 'oral' },
    instructions: String,
  }],
  generalInstructions: String,
  diagnosis: [String],
  followUpDate: Date,
  isAIGenerated: {
    type: Boolean,
    default: true,
  },
  validUntil: Date,
}, {
  timestamps: true,
});

const Prescription = mongoose.model('Prescription', prescriptionSchema);
export default Prescription;
