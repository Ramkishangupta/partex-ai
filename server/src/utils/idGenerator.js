import { v4 as uuidv4 } from 'uuid';

// Generate a human-readable unique ID with prefix
export const generatePatientId = () => {
  const num = Math.floor(100000 + Math.random() * 900000);
  return `PAT-${num}`;
};

export const generateSessionId = () => {
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).substring(2, 6);
  return `SES-${timestamp}-${random}`.toUpperCase();
};

export const generateDoctorId = () => {
  const num = Math.floor(10000 + Math.random() * 90000);
  return `DOC-${num}`;
};

export const generatePrescriptionId = () => {
  const date = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const random = Math.random().toString(36).substring(2, 6).toUpperCase();
  return `RX-${date}-${random}`;
};

export const generateUUID = () => uuidv4();
