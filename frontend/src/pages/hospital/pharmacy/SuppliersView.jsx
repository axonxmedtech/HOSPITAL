import React, { useState, useEffect } from 'react';
import { useToast } from '../../../context/ToastContext';
import DataTable from '../../../components/DataTable';
import SupplierForm from '../../../components/SupplierForm';
import suppliersApi from '../../../services/pharmacy/suppliersApi';

const SuppliersView = () => {
  // UI state
  const [search, setSearch] = useState('');
  const [suppliers, setSuppliers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [expandedRowIds, setExpandedRowIds] = useState([]);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  // New states for edit functionality
  const [selectedSupplier, setSelectedSupplier] = useState(null);
  const [modalMode, setModalMode] = useState('create');

  // Pagination state
  const [pageInfo, setPageInfo] = useState({
    pageIndex: 0,
    pageSize: 10,
    totalItems: 0,
    pageCount: 0,
  });

  // Table columns (including an expander column)
  const columns = [
    {
      header: '',
      id: 'expander',
      size: 30,
      cell: ({ row }) => {
        const id = row.original.id;
        const isOpen = expandedRowIds.includes(id);
        const toggle = (e) => {
          e.stopPropagation();
          setExpandedRowIds((prev) =>
            isOpen ? prev.filter((i) => i !== id) : [...prev, id]
          );
        };
        return (
          <button
            onClick={toggle}
            className="text-sm text-gray-500 hover:text-gray-800"
            aria-label={isOpen ? 'Collapse' : 'Expand'}
          >
            {isOpen ? '▾' : '▸'}
          </button>
        );
      },
    },
    { header: 'Supplier', accessorKey: 'supplierName' },
    { header: 'Contact Person', accessorKey: 'contactPerson' },
    { header: 'Phone', accessorKey: 'phone' },
    { header: 'Email', accessorKey: 'email' },
    { header: 'GST No.', accessorKey: 'gstNumber' },
    { header: 'Drug License', accessorKey: 'drugLicenseNumber' },
    { header: 'Credit Days', accessorKey: 'creditDays' },
    {
      header: 'Status',
      accessorKey: 'isActive',
      cell: ({ getValue }) => {
        const active = getValue();
        const bg = active ? 'bg-green-50' : 'bg-amber-50';
        const txt = active ? 'text-green-700' : 'text-amber-700';
        const border = active ? 'border-green-100' : 'border-amber-100';
        return (
          <span
            className={`px-2 py-0.5 border rounded text-xs font-bold ${bg} ${txt} ${border}`}
          >
            {active ? 'Active' : 'Inactive'}
          </span>
        );
      },
    },
    // Actions column with Edit button
    {
      header: 'Actions',
      cell: ({ row }) => {
        const supplier = row.original;
        const handleEdit = (e) => {
          e.stopPropagation();
          setSelectedSupplier(supplier);
          setModalMode('edit');
          setIsAddModalOpen(true);
        };
        return (
          <button
            type="button"
            onClick={handleEdit}
            className="px-2 py-1 text-sm text-white bg-primary-600 rounded hover:bg-primary-700"
          >
            Edit
          </button>
        );
      },
    },
  ];

  // Data fetch using suppliersApi.getAll(search, page, size)
  const fetchSuppliers = async () => {
    try {
      setLoading(true);
      setError(null);
      const resp = await suppliersApi.getAll(
        search,
        pageInfo.pageIndex,
        pageInfo.pageSize
      );
      const { content = [], totalElements = 0, totalPages = 0 } = resp || {};
      setSuppliers(content);
      setPageInfo((prev) => ({
        ...prev,
        totalItems: totalElements,
        pageCount: totalPages,
      }));
    } catch (err) {
      console.error('Failed to load suppliers:', err);
      setError('Unable to fetch suppliers. Please try again later.');
    } finally {
      setLoading(false);
    }
  };

  // Effect: fetch when pageIndex or pageSize changes
  useEffect(() => {
    fetchSuppliers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageInfo.pageIndex, pageInfo.pageSize]);

  // Search button handler – resets to first page then fetches
  const handleSearch = () => {
    setPageInfo((prev) => ({ ...prev, pageIndex: 0 }));
    fetchSuppliers();
  };

  // Expanded row renderer – show address only (available in supplier entity)
  const renderExpandedRow = (row) => {
    const { address } = row.original;
    return (
      <div className="text-sm text-gray-700 p-2">
        <p>
          <strong>Address:</strong> {address || '—'}
        </p>
      </div>
    );
  };

  // Empty state UI
  const emptyState = (
    <div className="flex flex-col items-center justify-center py-16 text-center text-gray-500">
      <svg
        className="w-16 h-16 mb-4 text-gray-300"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M9 14l2-2 4 4M7 10h10M5 6h14a2 2 0 012 2v12a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2z"
        />
      </svg>
      <h3 className="text-lg font-medium">No Suppliers Found</h3>
      <p className="mt-1">Adjust your search criteria or add a new supplier.</p>
    </div>
  );

  // Pagination prop object for DataTable
  const pagination = {
    pageIndex: pageInfo.pageIndex,
    pageSize: pageInfo.pageSize,
    totalItems: pageInfo.totalItems,
    pageCount: pageInfo.pageCount,
    onPageChange: (newPage) =>
      setPageInfo((prev) => ({ ...prev, pageIndex: newPage })),
  };

  return (
    <div className="h-full flex flex-col gap-4 -mt-2">
      {/* Toolbar */}
      <div className="bg-white border border-gray-200 rounded-lg p-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap gap-3 items-center">
          {/* Search input */}
          <div className="relative w-72">
            <input
              type="text"
              placeholder="Search supplier / contact..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-10 pr-4 py-2 border border-gray-300 rounded w-full text-sm outline-none focus:border-gray-900"
            />
            <svg
              className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 transform -translate-y-1/2"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              />
            </svg>
          </div>
          {/* Search button */}
          <button
            onClick={handleSearch}
            className="px-4 py-2 bg-gray-900 text-white text-sm font-bold rounded hover:bg-gray-800 transition-colors"
          >
            Search
          </button>
          {/* Add Supplier */}
          <button
            onClick={() => {
              setSelectedSupplier(null);
              setModalMode('create');
              setIsAddModalOpen(true);
            }}
            className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded bg-white hover:bg-gray-50"
          >
            + Add Supplier
          </button>
        </div>
      </div>

      {/* Main table area */}
      <div
        className="flex flex-1 gap-4 overflow-hidden"
        style={{ height: 'calc(100vh - 260px)' }}
      >
        <div className="flex-1 bg-white border border-gray-200 rounded-lg flex flex-col overflow-hidden transition-all duration-300">
          <DataTable
            data={suppliers}
            columns={columns}
            pagination={pagination}
            loading={loading}
            emptyState={emptyState}
            expandedRowIds={expandedRowIds}
            renderExpandedRow={renderExpandedRow}
            idAccessor="id"
          />
        </div>
      </div>

      {/* Error banner */}
      {error && (
        <div className="mt-2 bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded text-sm">
          {error}
        </div>
      )}
      {/* Add Supplier Modal */}
      {isAddModalOpen && (
        <SupplierForm
          isOpen={isAddModalOpen}
          onClose={() => {
            setIsAddModalOpen(false);
            setSelectedSupplier(null);
          }}
          onSuccess={fetchSuppliers}
          mode={modalMode}
          supplier={selectedSupplier}
        />
      )}
    </div>
  );
};

export default SuppliersView;
