import React, { useState, useEffect, useCallback } from 'react';
import masterDataService from '../../services/masterDataService';
import { useToast } from '../../context/ToastContext';

const TABS = [
  { id: 'lab', label: 'Lab Tests' },
  { id: 'radiology', label: 'Radiology Tests' },
  { id: 'allergies', label: 'Allergies' },
  { id: 'diagnoses', label: 'Diagnoses (ICD)' },
  { id: 'procedures', label: 'Procedures' },
];

export default function MasterDataView() {
  const [activeTab, setActiveTab] = useState('lab');
  const [items, setItems] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editItem, setEditItem] = useState(null);
  const [seeded, setSeeded] = useState(false);
  const { toastSuccess, toastError } = useToast();

  const fetchItems = useCallback(async (q = '') => {
    setLoading(true);
    try {
      let data = [];
      if (activeTab === 'lab') data = await masterDataService.searchLabTests(q);
      else if (activeTab === 'radiology') data = await masterDataService.searchRadiologyTests(q);
      else if (activeTab === 'allergies') data = await masterDataService.searchAllergies(q);
      else if (activeTab === 'diagnoses') data = await masterDataService.searchDiagnoses(q);
      else if (activeTab === 'procedures') data = await masterDataService.searchProcedures(q);
      setItems(data);
    } catch { toastError('Failed to load master data'); }
    setLoading(false);
  }, [activeTab]);

  useEffect(() => { setSearch(''); fetchItems(''); }, [activeTab]);

  useEffect(() => {
    const t = setTimeout(() => fetchItems(search), 300);
    return () => clearTimeout(t);
  }, [search, fetchItems]);

  const handleSeed = async () => {
    try {
      await masterDataService.seedDefaults();
      toastSuccess('Default allergies and diagnoses seeded successfully');
      setSeeded(true);
      fetchItems('');
    } catch { toastError('Seed failed'); }
  };

  const handleDeactivate = async (id) => {
    if (!window.confirm('Deactivate this entry?')) return;
    try {
      if (activeTab === 'lab') await masterDataService.deactivateLabTest(id);
      else if (activeTab === 'radiology') await masterDataService.deactivateRadiologyTest(id);
      else if (activeTab === 'allergies') await masterDataService.deactivateAllergy(id);
      else if (activeTab === 'diagnoses') await masterDataService.deactivateDiagnosis(id);
      else if (activeTab === 'procedures') await masterDataService.deactivateProcedure(id);
      toastSuccess('Deactivated');
      fetchItems(search);
    } catch { toastError('Failed to deactivate'); }
  };

  const handleSave = async (formData) => {
    try {
      if (editItem) {
        if (activeTab === 'lab') await masterDataService.updateLabTest(editItem.id, formData);
        else if (activeTab === 'radiology') await masterDataService.updateRadiologyTest(editItem.id, formData);
        else if (activeTab === 'procedures') await masterDataService.updateProcedure(editItem.id, formData);
      } else {
        if (activeTab === 'lab') await masterDataService.createLabTest(formData);
        else if (activeTab === 'radiology') await masterDataService.createRadiologyTest(formData);
        else if (activeTab === 'allergies') await masterDataService.createAllergy(formData);
        else if (activeTab === 'diagnoses') await masterDataService.createDiagnosis(formData);
        else if (activeTab === 'procedures') await masterDataService.createProcedure(formData);
      }
      toastSuccess(editItem ? 'Updated' : 'Created');
      setShowModal(false);
      setEditItem(null);
      fetchItems(search);
    } catch { toastError('Save failed'); }
  };

  const columns = getColumns(activeTab);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex gap-2 flex-wrap">
          {TABS.map(t => (
            <button key={t.id} onClick={() => setActiveTab(t.id)}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition ${activeTab === t.id ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
              {t.label}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          {(activeTab === 'allergies' || activeTab === 'diagnoses') && !seeded && (
            <button onClick={handleSeed}
              className="px-3 py-2 text-sm rounded-lg bg-amber-100 text-amber-700 hover:bg-amber-200 font-medium">
              Seed Defaults
            </button>
          )}
          <button onClick={() => { setEditItem(null); setShowModal(true); }}
            className="px-4 py-2 text-sm rounded-lg bg-blue-600 text-white hover:bg-blue-700 font-medium">
            + Add New
          </button>
        </div>
      </div>

      <input
        type="text" value={search} onChange={e => setSearch(e.target.value)}
        placeholder={`Search ${TABS.find(t => t.id === activeTab)?.label}...`}
        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />

      {loading ? (
        <div className="text-center py-12 text-gray-400">Loading...</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                {columns.map(col => (
                  <th key={col.key} className="px-4 py-3 text-left font-medium text-gray-500">{col.label}</th>
                ))}
                <th className="px-4 py-3 text-left font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {items.length === 0 && (
                <tr><td colSpan={columns.length + 1} className="px-4 py-8 text-center text-gray-400">No entries yet. Click "Add New" or "Seed Defaults".</td></tr>
              )}
              {items.map(item => (
                <tr key={item.id} className="hover:bg-gray-50">
                  {columns.map(col => (
                    <td key={col.key} className="px-4 py-3 text-gray-700">{col.render ? col.render(item) : item[col.key] ?? '-'}</td>
                  ))}
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      {(activeTab === 'lab' || activeTab === 'radiology' || activeTab === 'procedures') && (
                        <button onClick={() => { setEditItem(item); setShowModal(true); }}
                          className="text-blue-600 hover:underline text-xs">Edit</button>
                      )}
                      <button onClick={() => handleDeactivate(item.id)}
                        className="text-red-500 hover:underline text-xs">Deactivate</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showModal && (
        <MasterDataModal
          tab={activeTab}
          editItem={editItem}
          onSave={handleSave}
          onClose={() => { setShowModal(false); setEditItem(null); }}
        />
      )}
    </div>
  );
}

function getColumns(tab) {
  if (tab === 'lab') return [
    { key: 'testCode', label: 'Code' },
    { key: 'testName', label: 'Test Name' },
    { key: 'department', label: 'Department' },
    { key: 'sampleType', label: 'Sample' },
    { key: 'normalRangeText', label: 'Normal Range' },
    { key: 'unit', label: 'Unit' },
    { key: 'turnaroundHours', label: 'TAT (hrs)' },
    { key: 'price', label: 'Price' },
  ];
  if (tab === 'radiology') return [
    { key: 'testCode', label: 'Code' },
    { key: 'testName', label: 'Test Name' },
    { key: 'modality', label: 'Modality' },
    { key: 'estimatedDurationMinutes', label: 'Duration (min)' },
    { key: 'price', label: 'Price' },
  ];
  if (tab === 'allergies') return [
    { key: 'allergyName', label: 'Allergy' },
    { key: 'category', label: 'Category' },
    { key: 'isCustom', label: 'Type', render: item => item.isCustom ? 'Custom' : 'Default' },
  ];
  if (tab === 'diagnoses') return [
    { key: 'icdCode', label: 'ICD Code' },
    { key: 'icdDescription', label: 'Description' },
    { key: 'category', label: 'Category' },
    { key: 'isCustom', label: 'Type', render: item => item.isCustom ? 'Custom' : 'Default' },
  ];
  if (tab === 'procedures') return [
    { key: 'procedureCode', label: 'Code' },
    { key: 'procedureName', label: 'Procedure' },
    { key: 'department', label: 'Department' },
    { key: 'estimatedDurationMinutes', label: 'Duration (min)' },
    { key: 'price', label: 'Price' },
  ];
  return [];
}

function MasterDataModal({ tab, editItem, onSave, onClose }) {
  const [form, setForm] = useState(editItem ? { ...editItem } : {});
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const handleSubmit = (e) => { e.preventDefault(); onSave(form); };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-gray-800">
            {editItem ? 'Edit' : 'Add'} {TABS.find(t => t.id === tab)?.label}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl leading-none">&times;</button>
        </div>
        <form onSubmit={handleSubmit} className="space-y-3">
          {tab === 'lab' && <>
            <Field label="Test Name *" value={form.testName || ''} onChange={v => set('testName', v)} required />
            <Field label="Code" value={form.testCode || ''} onChange={v => set('testCode', v)} />
            <SelectField label="Department" value={form.department || 'OTHER'} onChange={v => set('department', v)}
              options={['BIOCHEMISTRY','HEMATOLOGY','MICROBIOLOGY','SEROLOGY','PATHOLOGY','OTHER']} />
            <SelectField label="Sample Type" value={form.sampleType || 'BLOOD'} onChange={v => set('sampleType', v)}
              options={['BLOOD','URINE','STOOL','SWAB','CSF','OTHER']} />
            <Field label="Normal Range" value={form.normalRangeText || ''} onChange={v => set('normalRangeText', v)} />
            <Field label="Unit" value={form.unit || ''} onChange={v => set('unit', v)} />
            <Field label="TAT (hours)" value={form.turnaroundHours || ''} onChange={v => set('turnaroundHours', v)} type="number" />
            <Field label="Price" value={form.price || ''} onChange={v => set('price', v)} type="number" />
          </>}
          {tab === 'radiology' && <>
            <Field label="Test Name *" value={form.testName || ''} onChange={v => set('testName', v)} required />
            <Field label="Code" value={form.testCode || ''} onChange={v => set('testCode', v)} />
            <SelectField label="Modality" value={form.modality || 'OTHER'} onChange={v => set('modality', v)}
              options={['X_RAY','CT','MRI','USG','ECHO','ECG','OTHER']} />
            <Field label="Preparation Instructions" value={form.preparationInstructions || ''} onChange={v => set('preparationInstructions', v)} textarea />
            <Field label="Duration (minutes)" value={form.estimatedDurationMinutes || ''} onChange={v => set('estimatedDurationMinutes', v)} type="number" />
            <Field label="Price" value={form.price || ''} onChange={v => set('price', v)} type="number" />
          </>}
          {tab === 'allergies' && <>
            <Field label="Allergy Name *" value={form.allergyName || ''} onChange={v => set('allergyName', v)} required />
            <SelectField label="Category" value={form.category || 'OTHER'} onChange={v => set('category', v)}
              options={['DRUG','FOOD','ENVIRONMENTAL','OTHER']} />
          </>}
          {tab === 'diagnoses' && <>
            <Field label="ICD Code *" value={form.icdCode || ''} onChange={v => set('icdCode', v)} required />
            <Field label="Description *" value={form.icdDescription || ''} onChange={v => set('icdDescription', v)} required />
            <SelectField label="Category" value={form.category || 'OTHER'} onChange={v => set('category', v)}
              options={['INFECTIOUS','CARDIOVASCULAR','RESPIRATORY','ENDOCRINE','NEUROLOGICAL',
                'MUSCULOSKELETAL','GASTROINTESTINAL','GENITOURINARY','OBSTETRIC','MENTAL','INJURY','NEOPLASM','OTHER']} />
          </>}
          {tab === 'procedures' && <>
            <Field label="Procedure Name *" value={form.procedureName || ''} onChange={v => set('procedureName', v)} required />
            <Field label="Code" value={form.procedureCode || ''} onChange={v => set('procedureCode', v)} />
            <Field label="Department" value={form.department || ''} onChange={v => set('department', v)} />
            <Field label="Duration (minutes)" value={form.estimatedDurationMinutes || ''} onChange={v => set('estimatedDurationMinutes', v)} type="number" />
            <Field label="Price" value={form.price || ''} onChange={v => set('price', v)} type="number" />
          </>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50">Cancel</button>
            <button type="submit" className="px-4 py-2 text-sm rounded-lg bg-blue-600 text-white hover:bg-blue-700 font-medium">
              {editItem ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Field({ label, value, onChange, required, type = 'text', textarea }) {
  const cls = "w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500";
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
      {textarea
        ? <textarea className={cls} rows={2} value={value} onChange={e => onChange(e.target.value)} required={required} />
        : <input className={cls} type={type} value={value} onChange={e => onChange(e.target.value)} required={required} />}
    </div>
  );
}

function SelectField({ label, value, onChange, options }) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
      <select className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        value={value} onChange={e => onChange(e.target.value)}>
        {options.map(o => <option key={o} value={o}>{o.replace(/_/g, ' ')}</option>)}
      </select>
    </div>
  );
}
