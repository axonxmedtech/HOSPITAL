import React, { useState, useEffect, Fragment } from 'react';
import { Tab } from '@headlessui/react';
import { useToast } from '../../context/ToastContext';
import { useNavigate } from 'react-router-dom';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import authService from '../../services/authService';
import PageHeader from '../../components/PageHeader';
import DataTable from '../../components/DataTable';
import hospitalService from '../../services/hospitalService';
import { format } from 'date-fns';
import {
    createColumnHelper,
    getCoreRowModel,
    useReactTable,
    getPaginationRowModel,
    getSortedRowModel,
    getFilteredRowModel,
} from '@tanstack/react-table';

// Helper for class names
function classNames(...classes) {
    return classes.filter(Boolean).join(' ');
}

const PharmacyDashboard = () => {
    const [user] = useState(authService.getCurrentUser());
    const navigate = useNavigate();
    const { success, error: toastError } = useToast();
    const [loading, setLoading] = useState(true);
    const [pendingPrescriptions, setPendingPrescriptions] = useState([]);
    const [inventory, setInventory] = useState([]);
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

    // Dispense Modal State
    const [dispenseModal, setDispenseModal] = useState({
        isOpen: false,
        prescription: null,
        stockAvailable: 0
    });

    const handleLogout = () => {
        authService.logout();
        navigate('/login');
    };

    const sidebarTabs = [
        { id: 'pharmacy', label: 'Pharmacy', icon: null }
    ];

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const [pendingRes, inventoryRes] = await Promise.all([
                hospitalService.getPendingPrescriptions(),
                hospitalService.getInventory()
            ]);
            setPendingPrescriptions(pendingRes);
            setInventory(inventoryRes);
        } catch (error) {
            console.error("Error loading pharmacy data", error);
            toastError("Failed to load dashboard data");
        } finally {
            setLoading(false);
        }
    };

    const handleOpenDispense = (prescription) => {
        // Find stock for this medicine
        // Note: Ideally backend should link ID, but we search by name for V1
        const invItem = inventory.find(i => i.name.toLowerCase() === prescription.medicineName.toLowerCase());
        const stock = invItem ? invItem.stockQuantity : 0;

        setDispenseModal({
            isOpen: true,
            prescription,
            stockAvailable: stock,
            invItem: invItem // Store full item for validation
        });
    };

    const handleConfirmDispense = async () => {
        if (!dispenseModal.prescription) return;

        try {
            await hospitalService.dispenseMedicine(dispenseModal.prescription.id);
            success("Medicine dispensed successfully");
            setDispenseModal({ isOpen: false, prescription: null, stockAvailable: 0 });
            loadData(); // Refresh list
        } catch (error) {
            console.error("Dispense failed", error);
            toastError("Failed to dispense medicine");
        }
    };

    // --- Tab Components ---

    const PendingTab = () => {
        if (loading) return <div>Loading prescriptions...</div>;

        if (pendingPrescriptions.length === 0) {
            return (
                <div className="text-center py-10 bg-white rounded-lg border border-gray-200">
                    <p className="text-gray-600 text-lg">No pending prescriptions to dispense.</p>
                </div>
            );
        }

        return (
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                        <tr>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">Date</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">Patient</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">Medicine</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">Details</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">Doctor</th>
                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-600 uppercase tracking-wider">Action</th>
                        </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                        {pendingPrescriptions.map((p) => (
                            <tr key={p.id}>
                                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                    {p.createdAt ? format(new Date(p.createdAt), 'dd MMM HH:mm') : '-'}
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                                    {p.patientName || 'Unknown'}
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 font-semibold">
                                    {p.medicineName}
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                                    {p.dosage} | {p.frequency} | {p.duration}
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                                    {p.doctorName || '-'}
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                                    <button
                                        onClick={() => handleOpenDispense(p)}
                                        className="bg-gray-900 text-white px-3 py-1 rounded hover:bg-gray-800 transition"
                                    >
                                        Dispense
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        );
    };

    const InventoryTab = () => {
        // Create column helper
        const columnHelper = createColumnHelper();

        const columns = [
            columnHelper.accessor('name', {
                header: 'Medicine Name',
                cell: info => <span className="font-medium text-gray-900">{info.getValue()}</span>,
            }),
            columnHelper.accessor('type', {
                header: 'Type',
            }),
            columnHelper.accessor('stockQuantity', {
                header: 'Stock',
                cell: info => {
                    const val = info.getValue();
                    const min = info.row.original.minStockLevel || 10;
                    const isLow = val <= min;
                    return (
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${isLow ? 'bg-gray-200 text-gray-800' : 'bg-gray-100 text-gray-700'}`}>
                            {val} {isLow && 'Low'}
                        </span>
                    )
                }
            }),
            columnHelper.accessor('unitPrice', {
                header: 'Price (₹)',
                cell: info => info.getValue()?.toFixed(2)
            }),
            columnHelper.accessor('expiryDate', {
                header: 'Expiry',
                cell: info => info.getValue() || '-'
            }),
            columnHelper.display({
                id: 'actions',
                header: 'Actions',
                cell: info => (
                    <button className="text-gray-600 hover:text-gray-900" onClick={() => toastError("Edit Stock Coming Soon")}>
                        Edit
                    </button>
                ),
            }),
        ];

        return (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
                {loading ? <div>Loading Inventory...</div> :
                    <DataTable
                        data={inventory}
                        columns={columns}
                        searchPlaceholder="Search medicines..."
                    />
                }
            </div>
        );
    };

    return (
        <div className="flex h-screen bg-white">
            <Sidebar
                title="HMS Portal"
                tabs={sidebarTabs}
                activeTab="pharmacy"
                onTabChange={() => { }}
                footerTitle="Hospital"
                footerData={user?.hospitalName}
                variant="plain"
                isCollapsed={sidebarCollapsed}
            />
            <div className="flex-1 flex flex-col overflow-hidden">
                <Navbar
                    title={`Pharmacy Dashboard - ${user?.name}`}
                    user={user}
                    onLogout={handleLogout}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                />
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-white p-6">
                    <div className="space-y-6">
                        <PageHeader
                            title="Pharmacy & Inventory"
                            subtitle="Dispense medicines and manage stock"
                            actions={
                                <button className="bg-gray-900 text-white px-4 py-2 rounded-lg hover:bg-gray-800" onClick={loadData}>
                                    Refresh Data
                                </button>
                            }
                        />

                        <Tab.Group>
                            <Tab.List className="flex space-x-1 rounded-lg bg-gray-100 p-1 max-w-md">
                                <Tab
                                    className={({ selected }) =>
                                        classNames(
                                            'w-full rounded-lg py-2.5 text-sm font-medium leading-5',
                                            'focus:outline-none focus:ring-2 focus:ring-gray-500',
                                            selected
                                                ? 'bg-white text-gray-900 shadow'
                                                : 'text-gray-600 hover:bg-white hover:text-gray-900'
                                        )
                                    }
                                >
                                    Pending Prescriptions
                                </Tab>
                                <Tab
                                    className={({ selected }) =>
                                        classNames(
                                            'w-full rounded-lg py-2.5 text-sm font-medium leading-5',
                                            'focus:outline-none focus:ring-2 focus:ring-gray-500',
                                            selected
                                                ? 'bg-white text-gray-900 shadow'
                                                : 'text-gray-600 hover:bg-white hover:text-gray-900'
                                        )
                                    }
                                >
                                    Inventory Management
                                </Tab>
                            </Tab.List>
                            <Tab.Panels className="mt-2">
                                <Tab.Panel className={classNames('rounded-lg bg-white p-3', 'focus:outline-none focus:ring-2 focus:ring-gray-500')}>
                                    <PendingTab />
                                </Tab.Panel>
                                <Tab.Panel className={classNames('rounded-lg bg-white p-3', 'focus:outline-none focus:ring-2 focus:ring-gray-500')}>
                                    <InventoryTab />
                                </Tab.Panel>
                            </Tab.Panels>
                        </Tab.Group>

                        {/* Dispense Modal */}
                        {dispenseModal.isOpen && (
                            <div className="fixed inset-0 z-50 overflow-y-auto">
                                <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
                                    <div className="fixed inset-0 transition-opacity" aria-hidden="true">
                                        <div className="absolute inset-0 bg-gray-500 opacity-75" onClick={() => setDispenseModal({ ...dispenseModal, isOpen: false })}></div>
                                    </div>
                                    <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>
                                    <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full">
                                        <div className="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
                                            <div className="sm:flex sm:items-start">
                                                <div className="mx-auto flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-gray-100 sm:mx-0 sm:h-10 sm:w-10">
                                                </div>
                                                <div className="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left w-full">
                                                    <h3 className="text-lg leading-6 font-medium text-gray-900">
                                                        Dispense Medicine
                                                    </h3>
                                                    <div className="mt-4 space-y-3">
                                                        <div className="bg-gray-50 p-3 rounded text-sm">
                                                            <p className="text-gray-500">Patient</p>
                                                            <p className="font-semibold text-gray-900">{dispenseModal.prescription?.patientName}</p>
                                                        </div>
                                                        <div className="bg-gray-50 p-3 rounded border border-gray-200">
                                                            <p className="text-sm text-gray-600 font-medium">Prescribed Item</p>
                                                            <p className="text-lg font-bold text-gray-900">{dispenseModal.prescription?.medicineName}</p>
                                                            <p className="text-sm text-gray-600">
                                                                {dispenseModal.prescription?.dosage} • {dispenseModal.prescription?.duration}
                                                            </p>
                                                        </div>

                                                        <div className="grid grid-cols-2 gap-4">
                                                            <div className="border p-2 rounded">
                                                                <span className="block text-xs text-gray-500">Required Qty</span>
                                                                {/* V1: Hardcoded 1 unit deduction or estimated */}
                                                                <span className="text-xl font-bold">1 Unit</span>
                                                            </div>
                                                            <div className={`border p-2 rounded ${dispenseModal.stockAvailable < 1 ? 'bg-gray-100 border-gray-300' : 'bg-gray-50 border-gray-200'}`}>
                                                                <span className="block text-xs text-gray-600">Available Stock</span>
                                                                <span className={`text-xl font-bold ${dispenseModal.stockAvailable < 1 ? 'text-gray-700' : 'text-gray-900'}`}>
                                                                    {dispenseModal.stockAvailable}
                                                                </span>
                                                            </div>
                                                        </div>

                                                        {dispenseModal.stockAvailable < 1 && (
                                                            <div className="text-gray-700 text-sm font-medium bg-gray-100 p-2 rounded">
                                                                Insufficient Stock. Cannot dispense.
                                                            </div>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="bg-gray-50 px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse">
                                            <button
                                                type="button"
                                                disabled={dispenseModal.stockAvailable < 1}
                                                onClick={handleConfirmDispense}
                                                className={`w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 text-base font-medium text-white sm:ml-3 sm:w-auto sm:text-sm 
                                            ${dispenseModal.stockAvailable < 1 ? 'bg-gray-400 cursor-not-allowed' : 'bg-gray-900 hover:bg-gray-800'}`}
                                            >
                                                Confirm Dispense
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => setDispenseModal({ ...dispenseModal, isOpen: false })}
                                                className="mt-3 w-full inline-flex justify-center rounded-md border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 sm:mt-0 sm:ml-3 sm:w-auto sm:text-sm"
                                            >
                                                Cancel
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </main>
            </div>
        </div>
    );
};
export default PharmacyDashboard;
