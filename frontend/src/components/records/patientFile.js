/** Where a patient's file lives. Every list links a patient's name here. */
export const patientFilePath = (publicId) => `/patients/${encodeURIComponent(publicId)}/file`;
