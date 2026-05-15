// frontend/src/pages/hospital/pharmacy/MedicineMasterView.jsx

import React, { useState, useEffect, useCallback } from 'react';
import { useToast } from '../../../context/ToastContext';
import DataTable from '../../../components/DataTable';
import MedicineForm from './MedicineForm';
import medicinesApi from '../../../services/pharmacy/medicinesApi';
import categoriesApi from '../../../services/pharmacy/categoriesApi';
import manufacturersApi from '../../../services/pharmacy/manufacturersApi';
import CategoryForm from './CategoryForm';
import ManufacturerForm from './ManufacturerForm';

const MedicineMasterView = () => {
  // UI state
  const [searchTerm, setSearchTerm] = useState('');
  const [medicines, setMedicines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState('create');

  const [selectedMedicine, setSelectedMedicine] = useState(null);

  const [categoryOptions, setCategoryOptions] = useState([]);
  const [manufacturerOptions, setManufacturerOptions] = useState([]);

  // Quick-create states
  const [isManufacturerModalOpen, setIsManufacturerModalOpen] = useState(false);
  const [isManufacturerSubmitting, setIsManufacturerSubmitting] = useState(false);
  const [isCategoryModalOpen, setIsCategoryModalOpen] = useState(false);
  const [isCategorySubmitting, setIsCategorySubmitting] = useState(false);

  // Pagination
  const [pageInfo, setPageInfo] = useState({
    pageIndex: 0,
    pageSize: 10,
    totalItems: 0,
    pageCount: 0,
  });

  const toast = useToast();

  // ------------------------------------------------------------------
  // Fetch Medicines
  // ------------------------------------------------------------------
  const fetchMedicines = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const resp = await medicinesApi.getAll(
        searchTerm,
        pageInfo.pageIndex,
        pageInfo.pageSize
      );

      const {
        content = [],
        totalElements = 0,
        totalPages = 0,
      } = resp || {};

      setMedicines(content);

      setPageInfo((prev) => ({
        ...prev,
        totalItems: totalElements,
        pageCount: totalPages,
      }));
    } catch (err) {
      console.error('Failed to load medicines:', err);

      setError(
        'Unable to fetch medicines. Please try again later.'
      );
    } finally {
      setLoading(false);
    }
  }, [searchTerm, pageInfo.pageIndex, pageInfo.pageSize]);

  // ------------------------------------------------------------------
  // Fetch Dropdown Data
  // ------------------------------------------------------------------
  const fetchDropdownData = async () => {
    try {
      const [catsRaw, mansRaw] = await Promise.all([
        medicinesApi.getCategories(),
        medicinesApi.getManufacturers(),
      ]);

      // Support both plain arrays and Spring pagination wrappers
      setCategoryOptions(
        Array.isArray(catsRaw)
          ? catsRaw
          : catsRaw?.content || []
      );
      setManufacturerOptions(
        Array.isArray(mansRaw)
          ? mansRaw
          : mansRaw?.content || []
      );
    } catch (err) {
      console.error('Failed to load dropdown data:', err);

      toast.showError(
        'Failed to load categories or manufacturers.'
      );
    }
  };

  // ------------------------------------------------------------------
  // Effects
  // ------------------------------------------------------------------
  useEffect(() => {
    fetchMedicines();
  }, [fetchMedicines]);

  useEffect(() => {
    fetchDropdownData();
  }, []);

  // ------------------------------------------------------------------
  // Search
  // ------------------------------------------------------------------
  const handleSearch = () => {
    setPageInfo((prev) => ({
      ...prev,
      pageIndex: 0,
    }));

    fetchMedicines();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSearch();
    }
  };

  // ------------------------------------------------------------------
  // Modal Actions
  // ------------------------------------------------------------------
  const handleAdd = () => {
    setSelectedMedicine(null);
    setModalMode('create');
    setIsModalOpen(true);
  };

  const handleEdit = (medicine) => {
    setSelectedMedicine(medicine);
    setModalMode('edit');
    setIsModalOpen(true);
  };

  // ------------------------------------------------------------------
  // Toggle Status
  // ------------------------------------------------------------------


  // ------------------------------------------------------------------
  // Save Medicine
  // ------------------------------------------------------------------
  const handleSave = async (payload) => {
    try {
      // payload logging removed
      if (modalMode === 'create') {
        await medicinesApi.create(payload);
        toast.success('Medicine created successfully');
      } else {
        await medicinesApi.update(selectedMedicine.id, payload);
        toast.success('Medicine updated successfully');
      }
      setIsModalOpen(false);
      setSelectedMedicine(null);
      fetchMedicines();
    } catch (err) {
      console.error('Save medicine error:', err);
      console.error('Backend error response:', err?.response?.data);
      toast.error('Failed to save medicine');
    }
  };

  // ------------------------------------------------------------------
  // Quick Create Handlers
  // ------------------------------------------------------------------
  const handleQuickCategorySave = async (formData) => {
    setIsCategorySubmitting(true);
    try {
      await categoriesApi.create(formData);
      toast.success('Category created successfully');
      setIsCategoryModalOpen(false);
      await fetchDropdownData();
    } catch (err) {
      console.error('Quick category save error:', err);
      toast.error('Failed to create category');
    } finally {
      setIsCategorySubmitting(false);
    }
  };

  const handleQuickManufacturerSave = async (formData) => {
    setIsManufacturerSubmitting(true);
    try {
      await manufacturersApi.create(formData);
      toast.success('Manufacturer created successfully');
      setIsManufacturerModalOpen(false);
      await fetchDropdownData();
    } catch (err) {
      console.error('Quick manufacturer save error:', err);
      toast.error('Failed to create manufacturer');
    } finally {
      setIsManufacturerSubmitting(false);
    }
  };

  // ------------------------------------------------------------------
  // Table Columns
  // ------------------------------------------------------------------
  const columns = [
    {
      header: 'Medicine',
      accessorKey: 'medicineName',
      cell: ({ row }) => {
        const { medicineName, genericName } = row.original;
        return (
          <div className="flex flex-col leading-tight">
            <span className="truncate max-w-[220px] text-sm font-medium text-gray-900" title={medicineName}>
              {medicineName || '-'}
            </span>
            {genericName && (
              <span className="truncate max-w-[220px] text-xs text-gray-500 mt-0.5" title={genericName}>
                {genericName}
              </span>
            )}
          </div>
        );
      },
    },
    {
      header: 'Code',
      accessorKey: 'medicineCode',
      cell: ({ getValue }) => (
        <span className="text-sm text-gray-600 whitespace-nowrap">
          {getValue() || '-'}
        </span>
      ),
    },
    {
      header: 'Format',
      accessorKey: 'dosageForm',
      cell: ({ row }) => {
        const { dosageForm, strength } = row.original;
        return (
          <div className="flex flex-col leading-tight">
            <span className="truncate max-w-[120px] text-sm font-medium text-gray-900 capitalize" title={dosageForm}>
              {dosageForm || '-'}
            </span>
            {strength && (
              <span className="truncate max-w-[120px] text-xs text-gray-500 mt-0.5" title={strength}>
                {strength}
              </span>
            )}
          </div>
        );
      },
    },
    {
      header: 'Classification',
      accessorKey: 'categoryId',
      cell: ({ row }) => {
        const catName = row.original.category?.categoryName || '-';
        const type = row.original.medicineType;
        return (
          <div className="flex flex-col leading-tight">
            <span className="truncate max-w-[150px] text-sm font-medium text-gray-900" title={catName}>
              {catName}
            </span>
            {type && (
              <span className="truncate max-w-[150px] text-xs text-gray-500 capitalize mt-0.5" title={type}>
                {type}
              </span>
            )}
          </div>
        );
      },
    },
    {
      header: 'Manufacturer',
      accessorKey: 'manufacturerId',
      cell: ({ row }) => {
        const mfgName = row.original.manufacturer?.manufacturerName || '-';
        return (
          <span className="block truncate max-w-[180px] text-sm text-gray-600" title={mfgName}>
            {mfgName}
          </span>
        );
      },
    },
    {
      header: 'Status',
      accessorKey: 'isActive',
      cell: ({ row }) => {
        const active = row.original.isActive;
        const isRx = row.original.requiresPrescription;

        return (
          <div className="flex flex-col items-start gap-0.5">
            <span
              className={`px-1.5 py-px rounded-sm text-[11px] font-semibold whitespace-nowrap ${
                active ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-600'
              }`}
            >
              {active ? 'Active' : 'Inactive'}
            </span>
            <span
              className={`px-1.5 py-px rounded-sm text-[11px] font-semibold whitespace-nowrap ${
                isRx ? 'bg-red-50 text-red-700' : 'bg-blue-50 text-blue-700'
              }`}
            >
              {isRx ? 'Rx Required' : 'OTC'}
            </span>
          </div>
        );
      },
    },
    {
      header: 'Actions',
      cell: ({ row }) => {
        const med = row.original;
        return (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              handleEdit(med);
            }}
            className="px-3 py-1 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50 whitespace-nowrap"
          >
            Edit
          </button>
        );
      },
    },
  ];

  // ------------------------------------------------------------------
  // Empty State
  // ------------------------------------------------------------------
  const emptyState = (
    <div className="flex flex-col items-center justify-center py-16 text-center text-gray-500">
      <h3 className="text-lg font-semibold">
        No Medicines Found
      </h3>

      <p className="mt-1 text-sm">
        Add medicines or adjust your search.
      </p>
    </div>
  );

  // ------------------------------------------------------------------
  // Pagination Config
  // ------------------------------------------------------------------
  const pagination = {
    pageIndex: pageInfo.pageIndex,
    pageSize: pageInfo.pageSize,
    totalItems: pageInfo.totalItems,
    pageCount: pageInfo.pageCount,

    onPageChange: (newPage) => {
      setPageInfo((prev) => ({
        ...prev,
        pageIndex: newPage,
      }));
    },
  };

  // ------------------------------------------------------------------
  // Render
  // ------------------------------------------------------------------
  return (
    <div className="h-full flex flex-col gap-2 -mt-2 max-w-7xl mx-auto w-full">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 bg-white px-4 py-3 rounded-lg border border-gray-200">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold text-gray-900">
              Medicine Master
            </h1>
          </div>
          <p className="text-sm text-gray-500 mt-0.5">
            Centralized medicine, manufacturer and category registry.
          </p>
        </div>
      </div>

      {/* Toolbar */}
      <div className="bg-white border border-gray-200 rounded-lg px-4 py-2.5 flex flex-wrap items-center justify-between gap-3">
        {/* ZONE A: Search Zone */}
        <div className="flex items-center gap-1.5">
          <div className="relative w-80">
            <input
              type="text"
              placeholder="Search catalog..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyDown={handleKeyDown}
              className="pl-3 pr-3 py-1.5 border border-gray-300 rounded-md w-full text-sm outline-none focus:border-gray-900 transition-colors"
            />
          </div>
          <button
            onClick={handleSearch}
            className="px-3 py-1.5 bg-gray-900 text-white text-sm font-medium rounded-md hover:bg-gray-800 transition-colors shadow-sm"
          >
            Search
          </button>
        </div>

        {/* ZONE B: Creation Zone */}
        <div className="inline-flex items-center gap-1.5 rounded-xl border border-gray-200 bg-gray-50 px-2 py-1.5">
          <button
            onClick={handleAdd}
            className="px-3 py-1.5 bg-white border border-gray-300 text-gray-800 text-sm font-medium rounded-lg hover:bg-gray-50 transition-colors shadow-sm"
          >
            + Add Medicine
          </button>
          
          <div className="h-5 w-px bg-gray-300 mx-0.5"></div>
          
          <button
            onClick={() => setIsManufacturerModalOpen(true)}
            className="px-2.5 py-1.5 bg-white border border-gray-200 text-gray-600 text-sm font-medium rounded-lg hover:bg-gray-50 transition-colors"
          >
            + Manufacturer
          </button>
          <button
            onClick={() => setIsCategoryModalOpen(true)}
            className="px-2.5 py-1.5 bg-white border border-gray-200 text-gray-600 text-sm font-medium rounded-lg hover:bg-gray-50 transition-colors"
          >
            + Category
          </button>
        </div>
      </div>

      {/* Table */}
      <div
        className="flex flex-1 gap-4 overflow-hidden"
        style={{ height: 'calc(100vh - 260px)' }}
      >
        <div className="flex-1 bg-white border border-gray-200 rounded-lg flex flex-col overflow-x-auto">
          <DataTable
            data={medicines}
            columns={columns}
            pagination={pagination}
            loading={loading}
            emptyState={emptyState}
            idAccessor="id"
          />
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded text-sm">
          {error}
        </div>
      )}

      {/* Modal */}
      {isModalOpen && (
        <MedicineForm
          isOpen={isModalOpen}
          onClose={() => {
            setIsModalOpen(false);
            setSelectedMedicine(null);
          }}
          onSave={handleSave}
          mode={modalMode}
          initialData={selectedMedicine}
          categoryOptions={categoryOptions}
          manufacturerOptions={manufacturerOptions}
        />
      )}

      {/* Quick Create Modals */}
      <CategoryForm
        isOpen={isCategoryModalOpen}
        onClose={() => setIsCategoryModalOpen(false)}
        onSubmit={handleQuickCategorySave}
        isSubmitting={isCategorySubmitting}
        mode="create"
      />

      <ManufacturerForm
        isOpen={isManufacturerModalOpen}
        onClose={() => setIsManufacturerModalOpen(false)}
        onSubmit={handleQuickManufacturerSave}
        isSubmitting={isManufacturerSubmitting}
        mode="create"
      />
    </div>
  );
};

export default MedicineMasterView;