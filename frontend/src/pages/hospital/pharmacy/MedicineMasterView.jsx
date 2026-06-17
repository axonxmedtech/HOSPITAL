import React, { useState, useEffect, useCallback } from 'react';
import { useViewManager } from '../../../hooks/pharmacy/useViewManager';
import { ViewLayout, ViewToolbar, SearchInput } from '../../../components/pharmacy/shared/ViewComponents';
import { useToast } from '../../../context/ToastContext';
import DataTable from '../../../components/DataTable';
import MedicineForm from './MedicineForm';
import CategoryForm from './CategoryForm';
import ManufacturerForm from './ManufacturerForm';
import medicinesApi from '../../../services/pharmacy/medicinesApi';
import categoriesApi from '../../../services/pharmacy/categoriesApi';
import manufacturersApi from '../../../services/pharmacy/manufacturersApi';

const MedicineMasterView = ({ refreshKey = 0 }) => {
    const toast = useToast();
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [modalMode, setModalMode] = useState('create');
    const [selectedMedicine, setSelectedMedicine] = useState(null);

    const [categoryOptions, setCategoryOptions] = useState([]);
    const [manufacturerOptions, setManufacturerOptions] = useState([]);

    const [isManufacturerModalOpen, setIsManufacturerModalOpen] = useState(false);
    const [isCategoryModalOpen, setIsCategoryModalOpen] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const {
        data: medicines,
        loading,
        error,
        search,
        page,
        pageSize,
        totalPages,
        totalElements,
        handleSearch,
        handlePageChange,
        refresh
    } = useViewManager(medicinesApi.getAll, { dependencies: [refreshKey] });

    const fetchDropdownData = useCallback(async () => {
        try {
            const [cats, mans] = await Promise.all([
                medicinesApi.getCategories(),
                medicinesApi.getManufacturers(),
            ]);
            setCategoryOptions(Array.isArray(cats) ? cats : cats?.content || []);
            setManufacturerOptions(Array.isArray(mans) ? mans : mans?.content || []);
        } catch (err) {
            toast.error('Failed to load categories or manufacturers.');
        }
    }, [toast]);

    useEffect(() => {
        fetchDropdownData();
    }, [fetchDropdownData]);

    const handleSaveMedicine = async (payload) => {
        try {
            if (modalMode === 'create') {
                await medicinesApi.create(payload);
                toast.success('Medicine created successfully');
            } else {
                await medicinesApi.update(selectedMedicine.id, payload);
                toast.success('Medicine updated successfully');
            }
            setIsModalOpen(false);
            refresh();
        } catch (err) {
            toast.error('Failed to save medicine');
        }
    };

    const handleQuickSave = async (api, formData, type) => {
        setIsSubmitting(true);
        try {
            await api.create(formData);
            toast.success(`${type} created successfully`);
            type === 'Category' ? setIsCategoryModalOpen(false) : setIsManufacturerModalOpen(false);
            fetchDropdownData();
        } catch (err) {
            toast.error(`Failed to create ${type}`);
        } finally {
            setIsSubmitting(false);
        }
    };

    const columns = [
        {
            header: 'Medicine',
            cell: ({ row }) => (
                <div className="flex flex-col leading-tight">
                    <span className="font-bold text-gray-900 text-sm">{row.original.medicineName}</span>
                    <span className="text-[10px] text-gray-500 font-medium">{row.original.genericName || '-'}</span>
                </div>
            )
        },
        { header: 'Code', accessorKey: 'medicineCode' },
        { 
            header: 'Format', 
            cell: ({ row }) => (
                <div className="flex flex-col leading-tight">
                    <span className="text-sm font-medium text-gray-700 capitalize">{row.original.dosageForm}</span>
                    <span className="text-[10px] text-gray-400 font-bold">{row.original.strength}</span>
                </div>
            )
        },
        { 
            header: 'Classification', 
            cell: ({ row }) => (
                <div className="flex flex-col leading-tight">
                    <span className="text-sm font-medium text-gray-700">{row.original.category?.categoryName || '-'}</span>
                    <span className="text-[10px] text-gray-400 capitalize">{row.original.medicineType}</span>
                </div>
            )
        },
        { header: 'Manufacturer', accessorKey: 'manufacturer.manufacturerName' },
        {
            header: 'Min Stock',
            cell: ({ row }) => (
                <span className={`px-2 py-0.5 rounded text-[10px] font-black border ${
                    row.original.minStockLevel > 0
                        ? 'bg-amber-50 text-amber-700 border-amber-100'
                        : 'bg-red-50 text-red-400 border-red-100'
                }`}>
                    {row.original.minStockLevel > 0 ? `${row.original.minStockLevel} units` : 'Not set'}
                </span>
            )
        },
        {
            header: 'Status',
            cell: ({ row }) => (
                <div className="flex flex-col gap-1">
                    <span className={`px-1.5 py-0.5 rounded text-[10px] font-black uppercase border ${row.original.isActive ? 'bg-green-50 text-green-700 border-green-100' : 'bg-gray-100 text-gray-600 border-gray-200'}`}>
                        {row.original.isActive ? 'Active' : 'Inactive'}
                    </span>
                    <span className={`px-1.5 py-0.5 rounded text-[10px] font-black uppercase border ${row.original.requiresPrescription ? 'bg-red-50 text-red-700 border-red-100' : 'bg-blue-50 text-blue-700 border-blue-100'}`}>
                        {row.original.requiresPrescription ? 'Rx Required' : 'OTC'}
                    </span>
                </div>
            )
        },
        {
            header: 'Actions',
            cell: ({ row }) => (
                <button
                    onClick={() => {
                        setSelectedMedicine(row.original);
                        setModalMode('edit');
                        setIsModalOpen(true);
                    }}
                    className="px-3 py-1 text-xs font-bold text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50 transition-colors"
                >
                    Edit
                </button>
            )
        }
    ];

    return (
        <ViewLayout
            header={
                <div className="flex justify-between items-center">
                    <div>
                        <h1 className="text-xl font-black text-gray-900 tracking-tight">Medicine Master</h1>
                        <p className="text-xs text-gray-500 font-medium mt-1">Central registry for drugs, manufacturers, and clinical categories.</p>
                    </div>
                </div>
            }
            error={error}
            toolbar={
                <ViewToolbar 
                    left={
                        <SearchInput 
                            placeholder="Search catalog by name or code..."
                            value={search}
                            onChange={(e) => handleSearch(e.target.value)}
                        />
                    }
                    right={
                        <div className="flex gap-2">
                            <button 
                                onClick={() => { setSelectedMedicine(null); setModalMode('create'); setIsModalOpen(true); }}
                                className="px-4 py-2 bg-gray-900 text-white text-sm font-bold rounded-lg hover:bg-gray-800 transition-all shadow-md"
                            >
                                + Add Medicine
                            </button>
                            <div className="w-px h-8 bg-gray-100 mx-1"></div>
                            <button onClick={() => setIsManufacturerModalOpen(true)} className="px-3 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded-lg hover:bg-gray-50 transition-colors">
                                + Mfg
                            </button>
                            <button onClick={() => setIsCategoryModalOpen(true)} className="px-3 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded-lg hover:bg-gray-50 transition-colors">
                                + Category
                            </button>
                        </div>
                    }
                />
            }
        >
            <DataTable
                data={medicines}
                columns={columns}
                loading={loading}
                idAccessor="id"
                pagination={{
                    pageIndex: page,
                    pageSize: pageSize,
                    totalItems: totalElements,
                    pageCount: totalPages,
                    onPageChange: handlePageChange,
                }}
            />

            {isModalOpen && (
                <MedicineForm
                    isOpen={isModalOpen}
                    onClose={() => { setIsModalOpen(false); setSelectedMedicine(null); }}
                    onSave={handleSaveMedicine}
                    mode={modalMode}
                    initialData={selectedMedicine}
                    categoryOptions={categoryOptions}
                    manufacturerOptions={manufacturerOptions}
                />
            )}

            <CategoryForm
                isOpen={isCategoryModalOpen}
                onClose={() => setIsCategoryModalOpen(false)}
                onSubmit={(data) => handleQuickSave(categoriesApi, data, 'Category')}
                isSubmitting={isSubmitting}
                mode="create"
            />

            <ManufacturerForm
                isOpen={isManufacturerModalOpen}
                onClose={() => setIsManufacturerModalOpen(false)}
                onSubmit={(data) => handleQuickSave(manufacturersApi, data, 'Manufacturer')}
                isSubmitting={isSubmitting}
                mode="create"
            />
        </ViewLayout>
    );
};

export default MedicineMasterView;