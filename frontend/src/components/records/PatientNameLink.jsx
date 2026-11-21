import { Link } from 'react-router-dom';
import { patientFilePath } from './patientFile';

/** A patient's name that opens their Patient File. Plain text when there is no id to link. */
export default function PatientNameLink({ publicId, name, className = '' }) {
  if (!publicId)
    return <span className={`font-medium text-gray-900 ${className}`}>{name || '—'}</span>;
  return (
    <Link
      to={patientFilePath(publicId)}
      title="Open patient file"
      className={`font-medium text-sky-700 hover:underline ${className}`}
      onClick={(e) => e.stopPropagation()}
    >
      {name || '—'}
    </Link>
  );
}
