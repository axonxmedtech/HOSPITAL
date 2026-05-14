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
      header: 'Medicine Name',
      accessorKey: 'medicineName',
    },

    {
      header: 'Generic Name',
      accessorKey: 'genericName',
      cell: ({ getValue }) => getValue() || '-',
    },

    {
      header: 'Category',
      accessorKey: 'categoryId',
      cell: ({ row }) => {
        return row.original.category?.categoryName || '-';
      },
    },

    {
      header: 'Manufacturer',
      accessorKey: 'manufacturerId',
      cell: ({ row }) => {
        return row.original.manufacturer?.manufacturerName || '-';
      },
    },

    {
      header: 'Dosage Form',
      accessorKey: 'dosageForm',
      cell: ({ getValue }) => getValue() || '-',
    },



    {
      header: 'Status',
      accessorKey: 'isActive',
      cell: ({ getValue }) => {
        const active = getValue();

        return (
          <span
            className={`px-2 py-1 rounded text-xs font-bold ${active
                ? 'bg-green-50 text-green-700 border border-green-100'
                : 'bg-amber-50 text-amber-700 border border-amber-100'
              }`}
          >
            {active ? 'Active' : 'Inactive'}
          </span>
        );
      },
    },

    {
      header: 'Actions',
      cell: ({ row }) => {
        const med = row.original;

        return (
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                handleEdit(med);
              }}
              className="px-3 py-1 text-sm text-white bg-gray-900 rounded hover:bg-gray-800"
            >
              Edit
            </button>


          </div>
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
    <div className="h-full flex flex-col gap-4 -mt-2">
      {/* Toolbar */}
      <div className="bg-white border border-gray-200 rounded-lg p-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap gap-3 items-center">
          {/* Search */}
          <div className="relative w-72">
            <input
              type="text"
              placeholder="Search medicine..."
              value={searchTerm}
              onChange={(e) =>
                setSearchTerm(e.target.value)
              }
              onKeyDown={handleKeyDown}
              className="pl-4 pr-4 py-2 border border-gray-300 rounded w-full text-sm outline-none focus:border-gray-900"
            />
          </div>

          {/* Search Button */}
          <button
            onClick={handleSearch}
            className="px-4 py-2 bg-gray-900 text-white text-sm font-bold rounded hover:bg-gray-800"
          >
            Search
          </button>

          {/* Add Button */}
          <button
            onClick={handleAdd}
            className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded bg-white hover:bg-gray-50"
          >
            + Add Medicine
          </button>

          <button
            onClick={() => setIsManufacturerModalOpen(true)}
            className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded bg-white hover:bg-gray-50"
          >
            + Add Manufacturer
          </button>

          <button
            onClick={() => setIsCategoryModalOpen(true)}
            className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded bg-white hover:bg-gray-50"
          >
            + Add Category
          </button>
        </div>
      </div>

      {/* Table */}
      <div
        className="flex flex-1 gap-4 overflow-hidden"
        style={{ height: 'calc(100vh - 260px)' }}
      >
        <div className="flex-1 bg-white border border-gray-200 rounded-lg flex flex-col overflow-hidden">
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