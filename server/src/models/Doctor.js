import mongoose from 'mongoose';

const doctorSchema = new mongoose.Schema({
  doctorId: {
    type: String,
    unique: true,
    required: true,
  },
  name: {
    type: String,
    required: true,
    trim: true,
  },
  email: {
    type: String,
    unique: true,
    required: true,
    trim: true,
    lowercase: true,
  },
  password: {
    type: String,
    required: true,
  },
  specialization: {
    type: String,
    trim: true,
  },
  licenseNumber: {
    type: String,
    trim: true,
  },
}, {
  timestamps: true,
});

const Doctor = mongoose.model('Doctor', doctorSchema);
export default Doctor;
