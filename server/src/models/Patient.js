import mongoose from 'mongoose';

const patientSchema = new mongoose.Schema({
  patientId: {
    type: String,
    unique: true,
    required: true,
    index: true,
  },
  name: {
    type: String,
    required: true,
    trim: true,
  },
  age: {
    type: Number,
    min: 0,
    max: 150,
  },
  gender: {
    type: String,
    enum: ['male', 'female', 'other'],
  },
  phone: {
    type: String,
    trim: true,
  },
  address: {
    type: String,
    trim: true,
  },
  bloodGroup: {
    type: String,
    enum: ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-', ''],
    default: '',
  },
  allergies: [{
    type: String,
    trim: true,
  }],
  medicalHistory: [{
    type: String,
    trim: true,
  }],
}, {
  timestamps: true,
});

// Text index for search
patientSchema.index({ name: 'text', phone: 'text', patientId: 'text' });

const Patient = mongoose.model('Patient', patientSchema);
export default Patient;
