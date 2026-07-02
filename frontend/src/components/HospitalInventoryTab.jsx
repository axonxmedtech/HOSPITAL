import React, { useState, useEffect } from 'react';
import hospitalService from '../services/hospitalService';
import { useToast } from '../context/ToastContext';
import ConfirmationModal from './ConfirmationModal';

const HospitalInventoryTab = () => {
    const [subTab, setSubTab] = useState('inventory'); // 'inventory', 'purchase', 'catalog', 'indents', 'requisitions', 'vendors', 'invoices'
    
    // Data states
    const [inventoryList, setInventoryList] = useState([]);
    const [purchaseList, setPurchaseList] = useState([]);
    const [catalogList, setCatalogList] = useState([]);
    const [indentsList, setIndentsList] = useState([]);
    const [requisitionsList, setRequisitionsList] = useState([]);
    const [vendorsList, setVendorsList] = useState([]);
    const [poList, setPoList] = useState([]);
    const [invoicesList, setInvoicesList] = useState([]);
    const [fefoBatches, setFefoBatches] = useState([]);
    const [loading, setLoading] = useState(false);
    
    // Form and active selection states
    const [activeIndent, setActiveIndent] = useState(null);
    const [activeRequisition, setActiveRequisition] = useState(null);
    const [activePo, setActivePo] = useState(null);
    const [activeInvoice, setActiveInvoice] = useState(null);
    
    // Fee options for catalog linking (fetched from admin fees)
    const [availableFees, setAvailableFees] = useState([]);
    
    // Modal states
    const [stockModal, setStockModal] = useState({ isOpen: false, isEdit: false, data: null });
    const [catalogModal, setCatalogModal] = useState({ isOpen: false, isEdit: false, data: null });

    const { success, error: toastError } = useToast();

    const [confirmState, setConfirmState] = useState({ open: false, title: '', message: '', onConfirm: null });

    const [stockItemQuery, setStockItemQuery] = useState('');
    const [showStockSuggestions, setShowStockSuggestions] = useState(false);
    const [stockFormState, setStockFormState] = useState({
        type: 'Consumable',
        manufacturer: '',
        minStockLevel: '10'
    });

    // Relative items states for catalog item
    const [selectedRelativeItems, setSelectedRelativeItems] = useState([]);
    const [relativeItemSearch, setRelativeItemSearch] = useState('');
    const [showRelativeSuggestions, setShowRelativeSuggestions] = useState(false);

    useEffect(() => {
        if (stockModal.isOpen) {
            setStockItemQuery(stockModal.data?.name || '');
            setStockFormState({
                type: stockModal.data?.type || 'Consumable',
                manufacturer: stockModal.data?.manufacturer || '',
                minStockLevel: stockModal.data?.minStockLevel?.toString() || '10'
            });
        } else {
            setStockItemQuery('');
        }
        setShowStockSuggestions(false);
    }, [stockModal.isOpen, stockModal.data]);

    useEffect(() => {
        if (catalogModal.isOpen) {
            if (catalogModal.isEdit && catalogModal.data) {
                try {
                    const ids = JSON.parse(catalogModal.data.relativeItemIds || '[]');
                    const matched = catalogList.filter(x => ids.includes(x.id)).map(x => ({ id: x.id, name: x.name }));
                    setSelectedRelativeItems(matched);
                } catch (e) {
                    setSelectedRelativeItems([]);
                }
            } else {
                setSelectedRelativeItems([]);
            }
            setRelativeItemSearch('');
            setShowRelativeSuggestions(false);
        }
    }, [catalogModal.isOpen, catalogModal.isEdit, catalogModal.data, catalogList]);

    // Fetch catalog list
    const fetchCatalog = async () => {
        try {
            const res = await hospitalService.getHospitalInventoryCatalog();
            setCatalogList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    // Fetch available fees for linking (from admin Fees tab)
    // Only custom fees are shown here — standard fees (consultation/casepaper) apply automatically
    const fetchFees = async () => {
        try {
            const customFees = await hospitalService.getCustomFees();
            // Use raw numeric ID from HospitalFee — stored directly as linkedFeeId (Long) in DB
            const custom = (customFees || []).map(f => ({
                id: f.id,          // numeric Long ID
                name: f.name,
                displayName: `${f.name} (₹${f.defaultAmount})`,
                amount: f.defaultAmount
            }));
            setAvailableFees(custom);
        } catch (err) {
            console.error('Failed to load fees', err);
        }
    };

    // Fetch active stock inventory
    const fetchInventory = async () => {
        try {
            const res = await hospitalService.getHospitalInventory();
            setInventoryList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    // Fetch purchases
    const fetchPurchases = async () => {
        try {
            const res = await hospitalService.getHospitalInventoryPurchases();
            setPurchaseList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    const fetchIndents = async () => {
        try {
            const res = await hospitalService.getStoreIndents();
            setIndentsList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    const fetchRequisitions = async () => {
        try {
            const [prRes, poRes] = await Promise.all([
                hospitalService.getPurchaseRequisitions(),
                hospitalService.getPurchaseOrders()
            ]);
            setRequisitionsList(prRes || []);
            setPoList(poRes || []);
        } catch (err) {
            console.error(err);
        }
    };

    const fetchVendors = async () => {
        try {
            const res = await hospitalService.getVendors();
            setVendorsList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    const fetchInvoices = async () => {
        try {
            const res = await hospitalService.getVendorInvoices();
            setInvoicesList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    const loadData = async () => {
        setLoading(true);
        try {
            if (subTab === 'inventory') {
                await fetchInventory();
                await fetchCatalog(); // Load catalog to populate options
            } else if (subTab === 'purchase') {
                await fetchPurchases();
                await fetchCatalog(); // For autocomplete in add stock
            } else if (subTab === 'catalog') {
                await Promise.all([fetchCatalog(), fetchFees()]);
            } else if (subTab === 'indents') {
                await Promise.all([fetchIndents(), fetchCatalog(), fetchInventory()]);
            } else if (subTab === 'requisitions') {
                await Promise.all([fetchRequisitions(), fetchCatalog(), fetchVendors()]);
            } else if (subTab === 'vendors') {
                await fetchVendors();
            } else if (subTab === 'invoices') {
                await Promise.all([fetchInvoices(), fetchVendors(), fetchRequisitions()]);
            }
        } catch (err) {
            toastError('Failed to load hospital inventory data.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, [subTab]);

    // Handle Stock Intake / Purchase Save
    const handleStockSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const itemName = form.itemName.value.trim();
        const type = form.type.value;
        const stockQuantity = parseInt(form.stockQuantity.value);
        const unitPrice = parseFloat(form.unitPrice.value);
        const minStockLevel = parseInt(form.minStockLevel.value);
        const expiryDate = form.expiryDate.value;
        const manufacturer = form.manufacturer.value.trim();

        if (!itemName) return;

        const payload = {
            name: itemName,
            type,
            quantity: stockQuantity, // mapped to quantity in purchase schema
            unitPrice,
            minStockLevel,
            expiryDate: expiryDate ? expiryDate : null,
            manufacturer: manufacturer ? manufacturer : null
        };

        try {
            setLoading(true);
            await hospitalService.addHospitalInventoryPurchase(payload);
            success('Purchase recorded and stock inventory updated.');
            setStockModal({ isOpen: false, isEdit: false, data: null });
            loadData();
        } catch (err) {
            toastError(err.response?.data || 'Failed to record purchase.');
        } finally {
            setLoading(false);
        }
    };

    // Handle Catalog Save
    const handleCatalogSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const name = form.name.value.trim();
        const type = form.type.value;
        const manufacturer = form.manufacturer.value.trim();
        const linkedFeeId = form.linkedFeeId?.value || null;

        if (!name) return;

        const payload = {
            name,
            type,
            manufacturer: manufacturer ? manufacturer : null,
            // Parse as number (custom fee ID is a Long in DB); null if empty/invalid
            linkedFeeId: linkedFeeId && !isNaN(linkedFeeId) ? Number(linkedFeeId) : null,
            relativeItemIds: JSON.stringify(selectedRelativeItems.map(x => x.id))
        };

        try {
            setLoading(true);
            if (catalogModal.isEdit) {
                await hospitalService.updateHospitalInventoryCatalog(catalogModal.data.id, payload);
                success('Catalog record updated successfully.');
            } else {
                await hospitalService.addHospitalInventoryCatalog(payload);
                success('Item registered in catalog.');
            }
            setCatalogModal({ isOpen: false, isEdit: false, data: null });
            loadData();
        } catch (err) {
            toastError(err.response?.data || 'Failed to save catalog record.');
        } finally {
            setLoading(false);
        }
    };

    const handleDeactivateStock = (id) => {
        setConfirmState({
            open: true,
            title: 'Remove Stock Item',
            message: 'Are you sure you want to remove this item from active stock inventory?',
            onConfirm: async () => {
                await hospitalService.deleteHospitalInventory(id);
                success('Item removed from inventory.');
                loadData();
            }
        });
    };

    const handleDeactivateCatalog = (id) => {
        setConfirmState({
            open: true,
            title: 'Deactivate Catalog Item',
            message: 'Are you sure you want to deactivate this item in the catalog directory?',
            onConfirm: async () => {
                await hospitalService.deleteHospitalInventoryCatalog(id);
                success('Item deactivated in catalog.');
                loadData();
            }
        });
    };

    // Phase E handlers
    const handleIndentSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const fromDepartment = form.fromDepartment.value;
        const itemId = parseInt(form.inventoryItemId.value);
        const quantity = parseFloat(form.requestedQty.value);

        try {
            await hospitalService.raiseStoreIndent({ fromDepartment, inventoryItemId: itemId, requestedQty: quantity });
            success('Store indent raised successfully.');
            form.reset();
            fetchIndents();
        } catch (err) {
            toastError(err.message || 'Failed to raise store indent.');
        }
    };

    const handleApproveIndent = async (id, signature) => {
        try {
            await hospitalService.approveStoreIndent(id, { approvedBySig: signature });
            success('Indent approved successfully.');
            fetchIndents();
        } catch (err) {
            toastError(err.message || 'Failed to approve indent.');
        }
    };

    const handleIssueStockSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const indentId = parseInt(form.indentId.value);
        const batchId = parseInt(form.batchId.value);
        const quantity = parseFloat(form.issuedQty.value);

        try {
            await hospitalService.issueStoreStock({ indentId, batchId, issuedQty: quantity });
            success('Stock issued successfully.');
            setActiveIndent(null);
            fetchIndents();
            fetchInventory();
        } catch (err) {
            toastError(err.message || 'Failed to issue stock.');
        }
    };

    const handlePrSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const department = form.department.value;
        const priority = form.priority.value;
        const requiredDate = form.requiredDate.value;
        const itemId = parseInt(form.inventoryItemId.value);
        const quantity = parseFloat(form.requestedQty.value);
        const itemsJson = JSON.stringify([{ itemId, quantity }]);

        try {
            await hospitalService.createPurchaseRequisition({ department, requiredDate, priority, itemsJson });
            success('Purchase requisition created.');
            form.reset();
            fetchRequisitions();
        } catch (err) {
            toastError(err.message || 'Failed to create requisition.');
        }
    };

    const handleApprovePr = async (id) => {
        try {
            await hospitalService.approvePurchaseRequisition(id);
            success('Requisition approved.');
            fetchRequisitions();
        } catch (err) {
            toastError(err.message || 'Failed to approve requisition.');
        }
    };

    const handlePoSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const vendorId = parseInt(form.vendorId.value);
        const expectedDelivery = form.expectedDelivery.value;
        const itemId = parseInt(form.inventoryItemId.value);
        const quantity = parseFloat(form.requestedQty.value);
        const rate = parseFloat(form.rate.value);
        const itemsJson = JSON.stringify([{ itemId, quantity, rate }]);

        try {
            await hospitalService.createPurchaseOrder({ vendorId, expectedDelivery, itemsJson });
            success('Purchase Order drafted.');
            setActiveRequisition(null);
            fetchRequisitions();
        } catch (err) {
            toastError(err.message || 'Failed to create PO.');
        }
    };

    const handleApprovePo = async (id, signature) => {
        try {
            await hospitalService.approvePurchaseOrder(id, { approvedBySig: signature });
            success('PO approved and sent to supplier.');
            fetchRequisitions();
        } catch (err) {
            toastError(err.message || 'Failed to approve PO.');
        }
    };

    const handleGrnSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const poId = parseInt(form.poId.value);
        const itemName = form.itemName.value;
        const qty = parseInt(form.qty.value);
        const unitPrice = parseFloat(form.unitPrice.value);
        const expiryDate = form.expiryDate.value || null;

        const items = [{ name: itemName, stockQuantity: qty, unitPrice, expiryDate }];

        try {
            await hospitalService.confirmGrn(poId, items);
            success('Goods receipt note confirmed. Inventory updated.');
            setActivePo(null);
            fetchRequisitions();
            fetchInventory();
        } catch (err) {
            toastError(err.message || 'Failed to confirm GRN.');
        }
    };

    const handleVendorSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const vendorName = form.vendorName.value.trim();
        const vendorCode = form.vendorCode.value.trim();
        const gstNumber = form.gstNumber.value.trim();
        const licenseNumber = form.licenseNumber.value.trim() || '';
        const rating = parseFloat(form.rating.value) || 5.0;

        try {
            await hospitalService.createVendor({ vendorName, vendorCode, gstNumber, licenseNumber, rating, status: 'ACTIVE' });
            success('Vendor registered successfully.');
            form.reset();
            fetchVendors();
        } catch (err) {
            toastError(err.message || 'Failed to register vendor.');
        }
    };

    const handleInvoiceSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const vendorId = parseInt(form.vendorId.value);
        const matchedPoId = parseInt(form.matchedPoId.value);
        const invoiceNumber = form.invoiceNumber.value.trim();
        const amount = parseFloat(form.amount.value);

        try {
            await hospitalService.verifyVendorInvoice({ vendorId, matchedPoId, invoiceNumber, amount, matchedGrnId: 1 });
            success('Invoice verified via 3-way matching!');
            form.reset();
            fetchInvoices();
        } catch (err) {
            toastError(err.message || 'Invoice match failed.');
        }
    };

    const handlePaymentSubmit = async (invoiceId) => {
        try {
            await hospitalService.processVendorInvoicePayment({ invoiceId, paymentMode: 'Bank Transfer', amount: 0 });
            success('Payment registered.');
            fetchInvoices();
        } catch (err) {
            toastError(err.message || 'Failed to process payment.');
        }
    };

    return (
        <div className="p-6 bg-white rounded-2xl border border-gray-200/80 shadow-sm space-y-6">
            
            {/* Header and Toggle Controls */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-gray-100 pb-4">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Hospital Equipment & Consumable Inventory</h2>
                    <p className="text-sm text-gray-500">Manage catalog lookup items and active physical stock levels for non-medicine equipment (e.g. saline, syringes, gloves).</p>
                </div>
                
                {/* Segmented Top-Tab Toggle */}
                <div className="flex bg-gray-100 p-1 rounded-xl w-full sm:w-auto overflow-x-auto whitespace-nowrap scrollbar-none">
                    <button
                        onClick={() => setSubTab('inventory')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'inventory' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Active Stock
                    </button>
                    <button
                        onClick={() => setSubTab('purchase')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'purchase' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Purchase History
                    </button>
                    <button
                        onClick={() => setSubTab('catalog')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'catalog' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Catalog Lookup
                    </button>
                    <button
                        onClick={() => setSubTab('indents')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'indents' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Store Indents
                    </button>
                    <button
                        onClick={() => setSubTab('requisitions')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'requisitions' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Requisitions & POs
                    </button>
                    <button
                        onClick={() => setSubTab('vendors')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'vendors' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Vendors
                    </button>
                    <button
                        onClick={() => setSubTab('invoices')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'invoices' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Invoices (3-Way Match)
                    </button>
                </div>
            </div>

            {/* Quick Actions Panel */}
            <div className="flex justify-between items-center bg-teal-50/40 p-4 rounded-xl border border-teal-100/60">
                <div className="text-sm text-teal-800 font-medium">
                    {subTab === 'inventory' && `Displaying ${inventoryList.filter(x => x.isActive !== false).length} active stock items in-clinic`}
                    {subTab === 'purchase' && `Displaying ${purchaseList.length} purchase ledger entries`}
                    {subTab === 'catalog' && `Displaying ${catalogList.filter(x => x.isActive !== false).length} catalog lookup dictionary names`}
                    {subTab === 'indents' && `Displaying ${indentsList.length} store indents`}
                    {subTab === 'requisitions' && `Displaying ${requisitionsList.length} requisitions & ${poList.length} POs`}
                    {subTab === 'vendors' && `Displaying ${vendorsList.length} suppliers`}
                    {subTab === 'invoices' && `Displaying ${invoicesList.length} verified vendor bills`}
                </div>
                {subTab === 'purchase' && (
                    <button
                        onClick={() => setStockModal({ isOpen: true, isEdit: false, data: null })}
                        className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition font-semibold text-sm shadow-md shadow-teal-600/10 active:scale-95"
                    >
                        + Add Stock (Purchase Intake)
                    </button>
                )}
                {subTab === 'catalog' && (
                    <button
                        onClick={() => setCatalogModal({ isOpen: true, isEdit: false, data: null })}
                        className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition font-semibold text-sm shadow-md shadow-teal-600/10 active:scale-95"
                    >
                        + Add Catalog Item
                    </button>
                )}
            </div>

            {/* Main Tables */}
            {loading && inventoryList.length === 0 && purchaseList.length === 0 && catalogList.length === 0 ? (
                <div className="space-y-3">
                    <div className="h-10 bg-slate-100 rounded-lg animate-pulse" />
                    <div className="h-10 bg-slate-100 rounded-lg animate-pulse" />
                    <div className="h-10 bg-slate-100 rounded-lg animate-pulse" />
                </div>
            ) : subTab === 'inventory' ? (
                /* INVENTORY TAB LIST */
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm text-left">
                        <thead>
                            <tr className="border-b border-gray-200 text-gray-500 font-medium">
                                <th className="pb-3 text-left">Item Name</th>
                                <th className="pb-3 text-center">Type</th>
                                <th className="pb-3 text-center">Quantity</th>
                                <th className="pb-3 text-right">Unit Cost</th>
                                <th className="pb-3 text-center">Expiry Date</th>
                                <th className="pb-3 text-center">Stock Level</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {inventoryList.filter(x => x.isActive !== false).map((item) => {
                                const isLow = item.stockQuantity <= item.minStockLevel;
                                return (
                                    <tr key={item.id} className="hover:bg-slate-50/50 transition">
                                        <td className="py-3 font-semibold text-gray-800">{item.name}</td>
                                        <td className="py-3 text-center text-gray-600">
                                            <span className="px-2 py-0.5 text-xs bg-slate-100 rounded-full font-medium">{item.type || 'Consumable'}</span>
                                        </td>
                                        <td className="py-3 text-center font-bold text-gray-900">{item.stockQuantity}</td>
                                        <td className="py-3 text-right text-gray-900 font-medium">₹{item.unitPrice?.toFixed(2)}</td>
                                        <td className="py-3 text-center text-gray-500">{item.expiryDate ? new Date(item.expiryDate).toLocaleDateString() : '-'}</td>
                                        <td className="py-3 text-center">
                                            {isLow ? (
                                                <span className="px-2 py-1 text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200 rounded-full">
                                                    Low (Min: {item.minStockLevel})
                                                </span>
                                            ) : (
                                                <span className="px-2 py-1 text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full">
                                                    Good (Min: {item.minStockLevel})
                                                </span>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                            {inventoryList.filter(x => x.isActive !== false).length === 0 && (
                                <tr>
                                    <td colSpan={6} className="py-8 text-center text-gray-400">
                                        No stock items in inventory. Record purchases in the "Purchase History" tab to add stock.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            ) : subTab === 'purchase' ? (
                /* PURCHASE HISTORY TAB LIST */
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm text-left">
                        <thead>
                            <tr className="border-b border-gray-200 text-gray-500 font-medium">
                                <th className="pb-3 text-left">Item Name</th>
                                <th className="pb-3 text-center">Type</th>
                                <th className="pb-3 text-center">Quantity Purchased</th>
                                <th className="pb-3 text-right">Unit Price</th>
                                <th className="pb-3 text-right">Total Cost</th>
                                <th className="pb-3 text-center">Expiry Date</th>
                                <th className="pb-3 text-left">Manufacturer</th>
                                <th className="pb-3 text-center">Purchase Date</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {purchaseList.map((item) => {
                                const totalCost = item.quantity * item.unitPrice;
                                return (
                                    <tr key={item.id} className="hover:bg-slate-50/50 transition">
                                        <td className="py-3 font-semibold text-gray-800">{item.name}</td>
                                        <td className="py-3 text-center text-gray-600">
                                            <span className="px-2 py-0.5 text-xs bg-slate-100 rounded-full font-medium">{item.type || 'Consumable'}</span>
                                        </td>
                                        <td className="py-3 text-center font-bold text-gray-900">{item.quantity}</td>
                                        <td className="py-3 text-right text-gray-900 font-medium">₹{item.unitPrice?.toFixed(2)}</td>
                                        <td className="py-3 text-right text-teal-700 font-semibold">₹{totalCost.toFixed(2)}</td>
                                        <td className="py-3 text-center text-gray-500">{item.expiryDate ? new Date(item.expiryDate).toLocaleDateString() : '-'}</td>
                                        <td className="py-3 text-left text-gray-500">{item.manufacturer || '-'}</td>
                                        <td className="py-3 text-center text-gray-500">
                                            {item.purchaseDate ? new Date(item.purchaseDate).toLocaleString() : '-'}
                                        </td>
                                    </tr>
                                );
                            })}
                            {purchaseList.length === 0 && (
                                <tr>
                                    <td colSpan={8} className="py-8 text-center text-gray-400">
                                        No purchase history found. Click "+ Add Stock (Purchase Intake)" to record a purchase.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            ) : (
                /* CATALOG LIST */
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm text-left">
                        <thead>
                            <tr className="border-b border-gray-200 text-gray-500 font-medium">
                                <th className="pb-3 text-left">Item Name</th>
                                <th className="pb-3 text-center">Type</th>
                                <th className="pb-3 text-left">Manufacturer</th>
                                <th className="pb-3 text-left">Linked Charge</th>
                                <th className="pb-3 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {catalogList.filter(x => x.isActive !== false).map((item) => (
                                <tr key={item.id} className="hover:bg-slate-50/50 transition">
                                    <td className="py-3 font-semibold text-gray-800">{item.name}</td>
                                    <td className="py-3 text-center">
                                        <span className="px-2 py-0.5 text-xs bg-slate-100 rounded-full font-medium">{item.type}</span>
                                    </td>
                                    <td className="py-3 text-left text-gray-500">{item.manufacturer || '-'}</td>
                                    <td className="py-3 text-left">
                                        {item.linkedFeeId ? (
                                            <span className="px-2 py-0.5 text-xs bg-teal-50 text-teal-700 border border-teal-100 rounded-full font-medium">
                                                {availableFees.find(f => String(f.id) === String(item.linkedFeeId))?.name || `Fee ID: ${item.linkedFeeId}`}
                                            </span>
                                        ) : (
                                            <span className="text-xs text-gray-400">No charge linked</span>
                                        )}
                                    </td>
                                    <td className="py-3 text-right space-x-2">
                                        <button
                                            onClick={() => setCatalogModal({ isOpen: true, isEdit: true, data: item })}
                                            className="text-teal-600 hover:text-teal-800 font-semibold"
                                        >
                                            Edit
                                        </button>
                                        <button
                                            onClick={() => handleDeactivateCatalog(item.id)}
                                            className="text-red-500 hover:text-red-700 font-semibold"
                                        >
                                            Deactivate
                                        </button>
                                    </td>
                                </tr>
                            ))}
                            {catalogList.filter(x => x.isActive !== false).length === 0 && (
                                <tr>
                                    <td colSpan={5} className="py-8 text-center text-gray-400">
                                        No catalog items registered.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            )}

            {subTab === 'indents' && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Raise Indent Form */}
                    <div className="bg-slate-50/50 p-6 rounded-xl border border-gray-200">
                        <h3 className="text-sm font-bold text-gray-800 uppercase tracking-wider mb-4">Raise Store Indent</h3>
                        <form onSubmit={handleIndentSubmit} className="space-y-4">
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">From Department</label>
                                <input
                                    name="fromDepartment"
                                    required
                                    placeholder="e.g. ICU, OT, Ward A"
                                    className="w-full border border-gray-200 rounded-lg p-2.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-teal-500"
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Select Catalog Item</label>
                                <select
                                    name="inventoryItemId"
                                    required
                                    className="w-full border border-gray-200 rounded-lg p-2.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-teal-500"
                                >
                                    <option value="">-- Choose Item --</option>
                                    {catalogList.filter(x => x.isActive !== false).map(item => (
                                        <option key={item.id} value={item.id}>{item.name} ({item.type})</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Requested Quantity</label>
                                <input
                                    name="requestedQty"
                                    type="number"
                                    step="0.01"
                                    required
                                    min="0.01"
                                    placeholder="0.00"
                                    className="w-full border border-gray-200 rounded-lg p-2.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-teal-500"
                                />
                            </div>
                            <button
                                type="submit"
                                className="w-full py-2.5 bg-teal-600 hover:bg-teal-700 text-white rounded-lg transition font-semibold text-sm shadow-md"
                            >
                                Raise Indent Request
                            </button>
                        </form>
                    </div>

                    {/* Indent List Table */}
                    <div className="lg:col-span-2 space-y-4">
                        <div className="overflow-x-auto bg-white rounded-xl border border-gray-200">
                            <table className="min-w-full text-sm text-left">
                                <thead>
                                    <tr className="border-b border-gray-200 text-gray-500 font-medium bg-slate-50/50">
                                        <th className="p-3 text-left">Dept</th>
                                        <th className="p-3 text-left">Requested Item</th>
                                        <th className="p-3 text-center">Qty</th>
                                        <th className="p-3 text-center">Status</th>
                                        <th className="p-3 text-right">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {indentsList.map(indent => {
                                        const catItem = catalogList.find(x => x.id === indent.inventoryItemId);
                                        return (
                                            <tr key={indent.id} className="hover:bg-slate-50/50 transition">
                                                <td className="p-3 font-semibold text-gray-800">{indent.fromDepartment}</td>
                                                <td className="p-3 text-gray-700">{catItem?.name || `Item ID: ${indent.inventoryItemId}`}</td>
                                                <td className="p-3 text-center font-bold text-gray-900">{indent.requestedQty}</td>
                                                <td className="p-3 text-center">
                                                    <span className={`px-2 py-0.5 text-[10px] font-bold rounded-full ${
                                                        indent.status === 'FILLED' ? 'bg-emerald-100 text-emerald-800' :
                                                        indent.status === 'APPROVED' ? 'bg-blue-100 text-blue-800' :
                                                        'bg-amber-100 text-amber-800'
                                                    }`}>
                                                        {indent.status}
                                                    </span>
                                                </td>
                                                <td className="p-3 text-right space-y-1 sm:space-y-0 sm:space-x-2">
                                                    {indent.status === 'PENDING' && (
                                                        <button
                                                            onClick={() => {
                                                                const sig = prompt("Enter Supervisor Approval Signature:");
                                                                if (sig) handleApproveIndent(indent.id, sig);
                                                            }}
                                                            className="px-2 py-1 bg-teal-50 text-teal-700 border border-teal-200 rounded text-xs font-semibold hover:bg-teal-100"
                                                        >
                                                            Approve
                                                        </button>
                                                    )}
                                                    {indent.status === 'APPROVED' && (
                                                        <button
                                                            onClick={() => setActiveIndent(indent)}
                                                            className="px-2 py-1 bg-blue-50 text-blue-700 border border-blue-200 rounded text-xs font-semibold hover:bg-blue-100"
                                                        >
                                                            Issue Stock
                                                        </button>
                                                    )}
                                                    {indent.status === 'FILLED' && (
                                                        <span className="text-xs text-gray-400 font-medium">Completed</span>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                    {indentsList.length === 0 && (
                                        <tr>
                                            <td colSpan={5} className="py-8 text-center text-gray-400">
                                                No store indents currently active.
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>

                        {/* Expandable Issue Stock Modal Context */}
                        {activeIndent && (
                            <div className="bg-blue-50/40 p-6 rounded-xl border border-blue-200">
                                <h4 className="text-sm font-bold text-blue-900 mb-2">Issue Consumables against Indent #{activeIndent.id}</h4>
                                <p className="text-xs text-blue-700 mb-4">Issuing supplies to department: <strong>{activeIndent.fromDepartment}</strong></p>
                                <form onSubmit={handleIssueStockSubmit} className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <input type="hidden" name="indentId" value={activeIndent.id} />
                                    <div>
                                        <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Select Sterile Batch</label>
                                        <select
                                            name="batchId"
                                            required
                                            className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                        >
                                            <option value="">-- Choose Stock Batch --</option>
                                            {inventoryList
                                                .filter(x => catalogList.find(c => c.id === activeIndent.inventoryItemId)?.name === x.name)
                                                .map(batch => (
                                                    <option key={batch.id} value={batch.id}>
                                                        Qty: {batch.stockQuantity} | Exp: {batch.expiryDate || 'N/A'} (Batch ID: {batch.id})
                                                    </option>
                                                ))
                                            }
                                        </select>
                                    </div>
                                    <div>
                                        <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Issued Qty</label>
                                        <input
                                            name="issuedQty"
                                            type="number"
                                            step="0.01"
                                            required
                                            defaultValue={activeIndent.requestedQty}
                                            className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                        />
                                    </div>
                                    <div className="flex items-end space-x-2">
                                        <button
                                            type="submit"
                                            className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition font-semibold text-xs shadow"
                                        >
                                            Confirm Issue
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => setActiveIndent(null)}
                                            className="px-3 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 text-xs font-semibold"
                                        >
                                            Cancel
                                        </button>
                                    </div>
                                </form>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {subTab === 'requisitions' && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Raise Requisition Form */}
                    <div className="bg-slate-50/50 p-6 rounded-xl border border-gray-200 space-y-6">
                        <div>
                            <h3 className="text-sm font-bold text-gray-800 uppercase tracking-wider mb-4">Raise Purchase Requisition</h3>
                            <form onSubmit={handlePrSubmit} className="space-y-4">
                                <div>
                                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Target Department</label>
                                    <input
                                        name="department"
                                        required
                                        placeholder="e.g. Pharmacy, Lab"
                                        className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white focus:ring-2 focus:ring-teal-500 outline-none"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Select Item</label>
                                    <select
                                        name="inventoryItemId"
                                        required
                                        className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white focus:ring-2 focus:ring-teal-500 outline-none"
                                    >
                                        <option value="">-- Choose Item --</option>
                                        {catalogList.map(item => (
                                            <option key={item.id} value={item.id}>{item.name}</option>
                                        ))}
                                    </select>
                                </div>
                                <div>
                                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Quantity</label>
                                    <input
                                        name="requestedQty"
                                        type="number"
                                        required
                                        min="1"
                                        placeholder="Quantity"
                                        className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white focus:ring-2 focus:ring-teal-500 outline-none"
                                    />
                                </div>
                                <div className="grid grid-cols-2 gap-2">
                                    <div>
                                        <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Priority</label>
                                        <select name="priority" className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white focus:ring-2 focus:ring-teal-500 outline-none">
                                            <option value="ROUTINE">ROUTINE</option>
                                            <option value="URGENT">URGENT</option>
                                            <option value="EMERGENCY">EMERGENCY</option>
                                        </select>
                                    </div>
                                    <div>
                                        <label className="block text-xs font-semibold text-gray-500 uppercase mb-1">Required Date</label>
                                        <input type="date" name="requiredDate" required className="w-full border border-gray-200 rounded-lg p-1.5 text-sm bg-white focus:ring-2 focus:ring-teal-500 outline-none" />
                                    </div>
                                </div>
                                <button
                                    type="submit"
                                    className="w-full py-2 bg-teal-600 text-white rounded-lg font-semibold text-sm hover:bg-teal-700 shadow"
                                >
                                    Submit PR
                                </button>
                            </form>
                        </div>
                    </div>

                    {/* PR & PO Registry */}
                    <div className="lg:col-span-2 space-y-6">
                        {/* Requisitions List */}
                        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
                            <div className="p-4 border-b border-gray-100 bg-slate-50/50">
                                <h4 className="text-sm font-bold text-gray-800">Active Requisitions</h4>
                            </div>
                            <table className="min-w-full text-sm text-left">
                                <thead>
                                    <tr className="border-b border-gray-100 text-gray-500 font-medium bg-slate-50/30">
                                        <th className="p-3">PR ID</th>
                                        <th className="p-3">Department</th>
                                        <th className="p-3">Priority</th>
                                        <th className="p-3">Status</th>
                                        <th className="p-3 text-right">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {requisitionsList.map(pr => (
                                        <tr key={pr.id} className="hover:bg-slate-50/30">
                                            <td className="p-3 font-semibold text-gray-800">{pr.publicId}</td>
                                            <td className="p-3 text-gray-600">{pr.department}</td>
                                            <td className="p-3">
                                                <span className={`px-2 py-0.5 text-[9px] font-bold rounded ${
                                                    pr.priority === 'EMERGENCY' ? 'bg-red-100 text-red-800' :
                                                    pr.priority === 'URGENT' ? 'bg-orange-100 text-orange-800' :
                                                    'bg-slate-100 text-slate-700'
                                                }`}>{pr.priority}</span>
                                            </td>
                                            <td className="p-3">
                                                <span className="text-xs font-semibold text-gray-800">{pr.status}</span>
                                            </td>
                                            <td className="p-3 text-right space-x-2">
                                                {pr.status === 'PENDING_APPROVAL' && (
                                                    <button
                                                        onClick={() => handleApprovePr(pr.id)}
                                                        className="px-2 py-1 bg-teal-50 text-teal-700 border border-teal-100 rounded text-xs font-semibold hover:bg-teal-100"
                                                    >
                                                        Approve
                                                    </button>
                                                )}
                                                {pr.status === 'APPROVED' && (
                                                    <button
                                                        onClick={() => {
                                                            try {
                                                                const items = JSON.parse(pr.itemsJson || '[]');
                                                                setActiveRequisition({ prId: pr.id, itemId: items[0]?.itemId, quantity: items[0]?.quantity });
                                                            } catch (e) {
                                                                toastError("PR contains invalid items schema.");
                                                            }
                                                        }}
                                                        className="px-2 py-1 bg-blue-50 text-blue-700 border border-blue-100 rounded text-xs font-semibold hover:bg-blue-100"
                                                    >
                                                        Convert to PO
                                                    </button>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        {/* PO Builder (When Requisition Approved) */}
                        {activeRequisition && (
                            <div className="bg-teal-50/50 p-6 rounded-xl border border-teal-200">
                                <h4 className="text-sm font-bold text-teal-900 mb-4">Draft Purchase Order</h4>
                                <form onSubmit={handlePoSubmit} className="grid grid-cols-1 md:grid-cols-4 gap-4">
                                    <input type="hidden" name="inventoryItemId" value={activeRequisition.itemId} />
                                    <input type="hidden" name="requestedQty" value={activeRequisition.quantity} />
                                    <div>
                                        <label className="block text-xs font-semibold text-gray-500 mb-1">Vendor</label>
                                        <select name="vendorId" required className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white outline-none">
                                            <option value="">-- Choose Vendor --</option>
                                            {vendorsList.map(v => (
                                                <option key={v.id} value={v.id}>{v.vendorName}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div>
                                        <label className="block text-xs font-semibold text-gray-500 mb-1">Expected Delivery</label>
                                        <input type="date" name="expectedDelivery" required className="w-full border border-gray-200 rounded-lg p-1.5 text-sm bg-white outline-none" />
                                    </div>
                                    <div>
                                        <label className="block text-xs font-semibold text-gray-500 mb-1">Negotitated Rate (₹)</label>
                                        <input type="number" step="0.01" name="rate" placeholder="0.00" required className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white outline-none" />
                                    </div>
                                    <div className="flex items-end space-x-2">
                                        <button type="submit" className="flex-1 py-2.5 bg-teal-600 hover:bg-teal-700 text-white rounded-lg font-semibold text-xs shadow">Draft PO</button>
                                        <button type="button" onClick={() => setActiveRequisition(null)} className="px-3 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 text-xs font-semibold">Close</button>
                                    </div>
                                </form>
                            </div>
                        )}

                        {/* Purchase Orders List */}
                        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
                            <div className="p-4 border-b border-gray-100 bg-slate-50/50">
                                <h4 className="text-sm font-bold text-gray-800">Purchase Orders Issued</h4>
                            </div>
                            <table className="min-w-full text-sm text-left">
                                <thead>
                                    <tr className="border-b border-gray-100 text-gray-500 font-medium bg-slate-50/30">
                                        <th className="p-3">PO Number</th>
                                        <th className="p-3">Vendor</th>
                                        <th className="p-3">Order Date</th>
                                        <th className="p-3">Status</th>
                                        <th className="p-3 text-right">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {poList.map(po => {
                                        const vend = vendorsList.find(x => x.id === po.vendorId);
                                        return (
                                            <tr key={po.id} className="hover:bg-slate-50/30">
                                                <td className="p-3 font-semibold text-gray-800">{po.poNumber}</td>
                                                <td className="p-3 text-gray-600">{vend?.vendorName || `Vendor ID: ${po.vendorId}`}</td>
                                                <td className="p-3 text-gray-500">{new Date(po.orderDate).toLocaleDateString()}</td>
                                                <td className="p-3">
                                                    <span className={`px-2 py-0.5 text-[9px] font-bold rounded ${
                                                        po.status === 'RECEIVED' ? 'bg-emerald-100 text-emerald-800' :
                                                        po.status === 'SENT' ? 'bg-blue-100 text-blue-800' :
                                                        'bg-gray-100 text-gray-700'
                                                    }`}>{po.status}</span>
                                                </td>
                                                <td className="p-3 text-right space-x-2">
                                                    {po.status === 'DRAFT' && (
                                                        <button
                                                            onClick={() => {
                                                                const sig = prompt("Enter Manager Approval Signature:");
                                                                if (sig) handleApprovePo(po.id, sig);
                                                            }}
                                                            className="px-2 py-1 bg-teal-50 text-teal-700 border border-teal-100 rounded text-xs font-semibold hover:bg-teal-100"
                                                        >
                                                            Approve PO
                                                        </button>
                                                    )}
                                                    {po.status === 'SENT' && (
                                                        <button
                                                            onClick={() => {
                                                                try {
                                                                    const items = JSON.parse(po.itemsJson || '[]');
                                                                    const catItem = catalogList.find(x => x.id === items[0]?.itemId);
                                                                    setActivePo({ poId: po.id, itemName: catItem?.name || '', qty: items[0]?.quantity || 0, price: items[0]?.rate || 0, type: catItem?.type || 'Consumable' });
                                                                } catch (e) {
                                                                    toastError("PO contains invalid items layout.");
                                                                }
                                                            }}
                                                            className="px-2 py-1 bg-blue-50 text-blue-700 border border-blue-100 rounded text-xs font-semibold hover:bg-blue-100"
                                                        >
                                                            Confirm GRN
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>

                        {/* GRN Intake Panel */}
                        {activePo && (
                            <div className="bg-emerald-50/50 p-6 rounded-xl border border-emerald-200">
                                <h4 className="text-sm font-bold text-emerald-950 mb-4">Confirm Goods Receipt Note (GRN)</h4>
                                <form onSubmit={handleGrnSubmit} className="space-y-4">
                                    <input type="hidden" name="poId" value={activePo.poId} />
                                    <input type="hidden" name="itemName" value={activePo.itemName} />
                                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                                        <div>
                                            <label className="block text-xs font-semibold text-gray-500 mb-1">Item Name</label>
                                            <input disabled value={activePo.itemName} className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-gray-100 text-gray-700" />
                                        </div>
                                        <div>
                                            <label className="block text-xs font-semibold text-gray-500 mb-1">Qty Received</label>
                                            <input type="number" name="qty" required defaultValue={activePo.qty} className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white outline-none" />
                                        </div>
                                        <div>
                                            <label className="block text-xs font-semibold text-gray-500 mb-1">Unit Cost (₹)</label>
                                            <input type="number" step="0.01" name="unitPrice" required defaultValue={activePo.price} className="w-full border border-gray-200 rounded-lg p-2 text-sm bg-white outline-none" />
                                        </div>
                                        <div>
                                            <label className="block text-xs font-semibold text-gray-500 mb-1">Batch Expiry</label>
                                            <input type="date" name="expiryDate" className="w-full border border-gray-200 rounded-lg p-1.5 text-sm bg-white outline-none" />
                                        </div>
                                    </div>
                                    <div className="flex justify-end space-x-2">
                                        <button type="submit" className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg font-semibold text-xs shadow">Record Goods Intake</button>
                                        <button type="button" onClick={() => setActivePo(null)} className="px-3 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 text-xs font-semibold">Cancel</button>
                                    </div>
                                </form>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {subTab === 'vendors' && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Register Vendor */}
                    <div className="bg-slate-50/50 p-6 rounded-xl border border-gray-200">
                        <h3 className="text-sm font-bold text-gray-800 uppercase tracking-wider mb-4">Register Supplier</h3>
                        <form onSubmit={handleVendorSubmit} className="space-y-4">
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">Vendor Name</label>
                                <input name="vendorName" required placeholder="Supplier Enterprise" className="w-full border border-gray-200 p-2.5 text-sm bg-white rounded-lg outline-none focus:ring-2 focus:ring-teal-500" />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">Vendor Code</label>
                                <input name="vendorCode" required placeholder="VEND-01" className="w-full border border-gray-200 p-2.5 text-sm bg-white rounded-lg outline-none focus:ring-2 focus:ring-teal-500" />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">GSTIN</label>
                                <input name="gstNumber" required placeholder="GST Registration #" className="w-full border border-gray-200 p-2.5 text-sm bg-white rounded-lg outline-none focus:ring-2 focus:ring-teal-500" />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">Drug License # (If applicable)</label>
                                <input name="licenseNumber" placeholder="License Code" className="w-full border border-gray-200 p-2.5 text-sm bg-white rounded-lg outline-none focus:ring-2 focus:ring-teal-500" />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">Supplier Rating (1.0 - 5.0)</label>
                                <input name="rating" type="number" step="0.1" max="5" min="1" placeholder="5.0" defaultValue="5.0" className="w-full border border-gray-200 p-2.5 text-sm bg-white rounded-lg outline-none focus:ring-2 focus:ring-teal-500" />
                            </div>
                            <button type="submit" className="w-full py-2.5 bg-teal-600 hover:bg-teal-700 text-white rounded-lg font-semibold text-sm shadow">
                                Register Supplier
                            </button>
                        </form>
                    </div>

                    {/* Vendors Registry */}
                    <div className="lg:col-span-2 overflow-x-auto bg-white rounded-xl border border-gray-200">
                        <table className="min-w-full text-sm text-left">
                            <thead>
                                <tr className="border-b border-gray-200 text-gray-500 font-medium bg-slate-50/50">
                                    <th className="p-3">Supplier Name</th>
                                    <th className="p-3">Code</th>
                                    <th className="p-3">GSTIN</th>
                                    <th className="p-3">Drug License</th>
                                    <th className="p-3 text-center">Score</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {vendorsList.map(v => (
                                    <tr key={v.id} className="hover:bg-slate-50/30">
                                        <td className="p-3 font-semibold text-gray-800">{v.vendorName}</td>
                                        <td className="p-3 text-gray-600">{v.vendorCode}</td>
                                        <td className="p-3 text-gray-500">{v.gstNumber}</td>
                                        <td className="p-3 text-gray-500">{v.licenseNumber || 'None'}</td>
                                        <td className="p-3 text-center">
                                            <span className="px-2 py-0.5 bg-amber-50 text-amber-700 border border-amber-200 rounded font-bold text-xs">
                                                ★ {v.rating || '5.00'}
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {subTab === 'invoices' && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Log Supplier Invoice Form */}
                    <div className="bg-slate-50/50 p-6 rounded-xl border border-gray-200">
                        <h3 className="text-sm font-bold text-gray-800 uppercase tracking-wider mb-4">Run 3-Way Match Verification</h3>
                        <form onSubmit={handleInvoiceSubmit} className="space-y-4">
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">Select Supplier</label>
                                <select name="vendorId" required className="w-full border border-gray-200 rounded-lg p-2.5 text-sm bg-white outline-none">
                                    <option value="">-- Choose Vendor --</option>
                                    {vendorsList.map(v => (
                                        <option key={v.id} value={v.id}>{v.vendorName}</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">Select Matched Purchase Order</label>
                                <select name="matchedPoId" required className="w-full border border-gray-200 rounded-lg p-2.5 text-sm bg-white outline-none">
                                    <option value="">-- Choose PO --</option>
                                    {poList.filter(po => po.status === 'RECEIVED').map(po => (
                                        <option key={po.id} value={po.id}>{po.poNumber} (Status: RECEIVED)</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">Vendor Invoice #</label>
                                <input name="invoiceNumber" required placeholder="e.g. INV-1002" className="w-full border border-gray-200 p-2.5 text-sm bg-white rounded-lg outline-none focus:ring-2 focus:ring-teal-500" />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 mb-1">Billed Net Amount (₹)</label>
                                <input name="amount" type="number" step="0.01" placeholder="0.00" required className="w-full border border-gray-200 p-2.5 text-sm bg-white rounded-lg outline-none focus:ring-2 focus:ring-teal-500" />
                            </div>
                            <button type="submit" className="w-full py-2.5 bg-teal-600 hover:bg-teal-700 text-white rounded-lg font-semibold text-sm shadow">
                                Match & Log Invoice
                            </button>
                        </form>
                    </div>

                    {/* Invoice Registry */}
                    <div className="lg:col-span-2 overflow-x-auto bg-white rounded-xl border border-gray-200">
                        <table className="min-w-full text-sm text-left">
                            <thead>
                                <tr className="border-b border-gray-200 text-gray-500 font-medium bg-slate-50/50">
                                    <th className="p-3">Invoice Number</th>
                                    <th className="p-3">Supplier</th>
                                    <th className="p-3 text-right">Amount</th>
                                    <th className="p-3 text-center">Status</th>
                                    <th className="p-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {invoicesList.map(invoice => {
                                    const vend = vendorsList.find(x => x.id === invoice.vendorId);
                                    return (
                                        <tr key={invoice.id} className="hover:bg-slate-50/30">
                                            <td className="p-3 font-semibold text-gray-800">{invoice.invoiceNumber}</td>
                                            <td className="p-3 text-gray-600">{vend?.vendorName || `Vendor ID: ${invoice.vendorId}`}</td>
                                            <td className="p-3 text-right text-gray-900 font-bold">₹{invoice.amount?.toFixed(2)}</td>
                                            <td className="p-3 text-center">
                                                <span className={`px-2 py-0.5 text-[9px] font-bold rounded-full ${
                                                    invoice.status === 'PAID' ? 'bg-emerald-100 text-emerald-800' :
                                                    'bg-blue-100 text-blue-800'
                                                }`}>{invoice.status}</span>
                                            </td>
                                            <td className="p-3 text-right">
                                                {invoice.status === 'VERIFIED' && (
                                                    <button
                                                        onClick={() => handlePaymentSubmit(invoice.id)}
                                                        className="px-2 py-1 bg-emerald-50 text-emerald-700 border border-emerald-100 rounded text-xs font-semibold hover:bg-emerald-100"
                                                    >
                                                        Release Payment
                                                    </button>
                                                )}
                                                {invoice.status === 'PAID' && (
                                                    <span className="text-xs text-gray-400 font-medium">Cleared</span>
                                                )}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* MODAL 1: ADD/EDIT ACTIVE INVENTORY STOCK */}
            {stockModal.isOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg overflow-hidden animate-fade-in-up" onClick={e => e.stopPropagation()}>
                        <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-slate-50">
                            <h3 className="text-lg font-bold text-gray-800">{stockModal.isEdit ? 'Edit Stock Details' : 'Add Stock Intake'}</h3>
                            <button onClick={() => setStockModal({ isOpen: false, isEdit: false, data: null })} className="text-gray-400 hover:text-gray-600">
                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        
                        <form onSubmit={handleStockSubmit} className="p-6 space-y-4">
                            {/* Item Name Autocomplete Input */}
                            <div className="relative">
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Item Name *</label>
                                <input
                                    type="text"
                                    name="itemName"
                                    placeholder="Type general item name (e.g. Gloves, Syringe)..."
                                    required
                                    disabled={stockModal.isEdit}
                                    value={stockItemQuery}
                                    onChange={(e) => {
                                        const name = e.target.value;
                                        setStockItemQuery(name);
                                        setShowStockSuggestions(true);
                                        
                                        const isKnown = catalogList.some(x => x.name.toLowerCase() === name.trim().toLowerCase());
                                        const hint = document.getElementById('catalog-hint');
                                        if (hint) {
                                            if (name.trim().length >= 3 && !isKnown) {
                                                hint.innerText = "💡 This item will be registered automatically in the catalog dictionary.";
                                                hint.classList.remove('hidden');
                                            } else {
                                                hint.classList.add('hidden');
                                            }
                                        }
                                    }}
                                    onFocus={() => setShowStockSuggestions(true)}
                                    onBlur={() => {
                                        setTimeout(() => setShowStockSuggestions(false), 200);
                                    }}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none disabled:bg-gray-100 text-gray-800"
                                />
                                {showStockSuggestions && stockItemQuery.trim().length >= 3 && (
                                    <div className="absolute left-0 right-0 mt-1 max-h-40 overflow-y-auto bg-white rounded-lg border border-gray-200 shadow-lg z-50 divide-y divide-gray-100">
                                        {catalogList
                                            .filter(x => x.isActive !== false && x.name.toLowerCase().includes(stockItemQuery.toLowerCase().trim()))
                                            .map(c => (
                                                <button
                                                    key={c.id}
                                                    type="button"
                                                    onMouseDown={() => {
                                                        setStockItemQuery(c.name);
                                                        setShowStockSuggestions(false);
                                                        setStockFormState({
                                                            type: c.type || 'Consumable',
                                                            manufacturer: c.manufacturer || '',
                                                            minStockLevel: '10'
                                                        });
                                                        const hint = document.getElementById('catalog-hint');
                                                        if (hint) hint.classList.add('hidden');
                                                    }}
                                                    className="w-full text-left px-4 py-2 hover:bg-slate-50 text-sm font-medium text-gray-800"
                                                >
                                                    {c.name} <span className="text-xs text-gray-400 font-normal">({c.type})</span>
                                                </button>
                                            ))}
                                        {catalogList.filter(x => x.isActive !== false && x.name.toLowerCase().includes(stockItemQuery.toLowerCase().trim())).length === 0 && (
                                            <div className="p-2.5 text-center text-xs text-gray-400">No matching catalog item.</div>
                                        )}
                                    </div>
                                )}
                                <p id="catalog-hint" className="text-xs text-amber-600 font-medium mt-1 hidden"></p>
                            </div>

                            {/* Type Select */}
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Type *</label>
                                <select
                                    name="type"
                                    required
                                    value={stockFormState.type}
                                    onChange={(e) => setStockFormState(prev => ({ ...prev, type: e.target.value }))}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none bg-white text-gray-800"
                                >
                                    <option value="Consumable">Consumable (Gloves, Swabs)</option>
                                    <option value="Surgical">Surgical Instruments (Syringes, Needles)</option>
                                    <option value="Fluid">Saline/Fluid</option>
                                    <option value="Equipment">Diagnostic Equipment</option>
                                    <option value="Other">Other</option>
                                </select>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Quantity *</label>
                                    <input
                                        type="number"
                                        name="stockQuantity"
                                        min="1"
                                        required
                                        placeholder="0"
                                        defaultValue={stockModal.data?.stockQuantity !== undefined ? stockModal.data.stockQuantity : ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Unit Price (₹) *</label>
                                    <input
                                        type="number"
                                        name="unitPrice"
                                        step="0.01"
                                        min="0"
                                        required
                                        placeholder="0.00"
                                        defaultValue={stockModal.data?.unitPrice !== undefined ? stockModal.data.unitPrice : ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Min Stock Warning Level *</label>
                                    <input
                                        type="number"
                                        name="minStockLevel"
                                        min="0"
                                        required
                                        placeholder="10"
                                        value={stockFormState.minStockLevel}
                                        onChange={(e) => setStockFormState(prev => ({ ...prev, minStockLevel: e.target.value }))}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Expiry Date</label>
                                    <input
                                        type="date"
                                        name="expiryDate"
                                        defaultValue={stockModal.data?.expiryDate || ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Manufacturer</label>
                                <input
                                    type="text"
                                    name="manufacturer"
                                    placeholder="e.g. Generic Co."
                                    value={stockFormState.manufacturer}
                                    onChange={(e) => setStockFormState(prev => ({ ...prev, manufacturer: e.target.value }))}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                />
                            </div>

                            <div className="pt-4 flex gap-3">
                                <button
                                    type="button"
                                    onClick={() => setStockModal({ isOpen: false, isEdit: false, data: null })}
                                    className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg font-semibold hover:bg-slate-50 transition"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="flex-1 px-4 py-2.5 bg-teal-600 text-white rounded-lg font-semibold hover:bg-teal-700 transition shadow-md shadow-teal-600/10"
                                >
                                    {stockModal.isEdit ? 'Save Changes' : 'Restock / Intake'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            <ConfirmationModal
                isOpen={confirmState.open}
                title={confirmState.title}
                message={confirmState.message}
                onConfirm={confirmState.onConfirm}
                onCancel={() => setConfirmState({ open: false })}
            />

            {/* MODAL 2: ADD/EDIT CATALOG DICTIONARY ITEM */}
            {catalogModal.isOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg overflow-hidden animate-fade-in-up" onClick={e => e.stopPropagation()}>
                        <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-slate-50">
                            <h3 className="text-lg font-bold text-gray-800">{catalogModal.isEdit ? 'Edit Catalog Specifications' : 'Register Catalog Item'}</h3>
                            <button onClick={() => setCatalogModal({ isOpen: false, isEdit: false, data: null })} className="text-gray-400 hover:text-gray-600">
                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        
                        <form onSubmit={handleCatalogSubmit} className="p-6 space-y-4">
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Item Name *</label>
                                <input
                                    type="text"
                                    name="name"
                                    required
                                    placeholder="e.g. Syringe 5ml"
                                    defaultValue={catalogModal.data?.name || ''}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Type *</label>
                                <select
                                    name="type"
                                    required
                                    defaultValue={catalogModal.data?.type || 'Consumable'}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none bg-white text-gray-800"
                                >
                                    <option value="Consumable">Consumable (Gloves, Swabs)</option>
                                    <option value="Surgical">Surgical Instruments (Syringes, Needles)</option>
                                    <option value="Fluid">Saline/Fluid</option>
                                    <option value="Equipment">Diagnostic Equipment</option>
                                    <option value="Other">Other</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Manufacturer</label>
                                <input
                                    type="text"
                                    name="manufacturer"
                                    placeholder="e.g. Generic Co."
                                    defaultValue={catalogModal.data?.manufacturer || ''}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Linked Charge / Fee</label>
                                <select
                                    name="linkedFeeId"
                                    defaultValue={catalogModal.data?.linkedFeeId || ''}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none bg-white text-gray-800"
                                >
                                     <option value="">-- No charge linked --</option>
                                     {availableFees.map(fee => (
                                         <option key={fee.id} value={fee.id}>{fee.displayName || fee.name}</option>
                                     ))}
                                 </select>
                                 <p className="text-xs text-gray-400 mt-1">Link a custom fee from the Fees tab. When this item is used in a consultation/IPD, the linked fee will be auto-applied to the bill.</p>
                            </div>

                            {/* Relative Items search and select */}
                            <div className="relative">
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Relative Items (Dependencies)</label>
                                <input
                                    type="text"
                                    placeholder="Search child/relative items (needle, tube)..."
                                    value={relativeItemSearch}
                                    onChange={(e) => {
                                        setRelativeItemSearch(e.target.value);
                                        setShowRelativeSuggestions(true);
                                    }}
                                    onFocus={() => setShowRelativeSuggestions(true)}
                                    onBlur={() => setTimeout(() => setShowRelativeSuggestions(false), 200)}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none text-gray-800"
                                />

                                {showRelativeSuggestions && relativeItemSearch.trim().length >= 1 && (
                                    <div className="absolute left-0 right-0 mt-1 max-h-40 overflow-y-auto bg-white rounded-lg border border-gray-200 shadow-lg z-50 divide-y divide-gray-100">
                                        {catalogList
                                            .filter(x => x.isActive !== false 
                                                && x.name.toLowerCase().includes(relativeItemSearch.toLowerCase().trim())
                                                // Prevent self-reference
                                                && x.id !== catalogModal.data?.id
                                                // Prevent duplicate selection
                                                && !selectedRelativeItems.some(item => item.id === x.id)
                                            )
                                            .map(c => (
                                                <button
                                                    key={c.id}
                                                    type="button"
                                                    onMouseDown={() => {
                                                        setSelectedRelativeItems(prev => [...prev, { id: c.id, name: c.name }]);
                                                        setRelativeItemSearch('');
                                                        setShowRelativeSuggestions(false);
                                                    }}
                                                    className="w-full text-left px-4 py-2 hover:bg-slate-50 text-sm font-medium text-gray-800"
                                                >
                                                    {c.name} <span className="text-xs text-gray-400 font-normal">({c.type})</span>
                                                </button>
                                            ))}
                                        {catalogList.filter(x => x.isActive !== false 
                                            && x.name.toLowerCase().includes(relativeItemSearch.toLowerCase().trim())
                                            && x.id !== catalogModal.data?.id
                                            && !selectedRelativeItems.some(item => item.id === x.id)
                                        ).length === 0 && (
                                            <div className="p-2.5 text-center text-xs text-gray-400">No matching catalog items.</div>
                                        )}
                                    </div>
                                )}

                                {/* Selected Items Tags */}
                                {selectedRelativeItems.length > 0 && (
                                    <div className="flex flex-wrap gap-2 mt-2">
                                        {selectedRelativeItems.map(item => (
                                            <span 
                                                key={item.id} 
                                                className="inline-flex items-center gap-1 px-3 py-1 bg-teal-50 text-teal-700 border border-teal-200 rounded-full text-xs font-semibold"
                                            >
                                                {item.name}
                                                <button
                                                    type="button"
                                                    onClick={() => setSelectedRelativeItems(prev => prev.filter(x => x.id !== item.id))}
                                                    className="hover:text-teal-900 focus:outline-none text-teal-500 font-bold"
                                                >
                                                    &times;
                                                </button>
                                            </span>
                                        ))}
                                    </div>
                                )}
                                <p className="text-xs text-gray-400 mt-1">These relative items will be automatically degraded from active stock when this catalog item is administered to a patient.</p>
                            </div>

                            <div className="pt-4 flex gap-3">
                                <button
                                    type="button"
                                    onClick={() => setCatalogModal({ isOpen: false, isEdit: false, data: null })}
                                    className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg font-semibold hover:bg-slate-50 transition"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="flex-1 px-4 py-2.5 bg-teal-600 text-white rounded-lg font-semibold hover:bg-teal-700 transition shadow-md shadow-teal-600/10"
                                >
                                    {catalogModal.isEdit ? 'Save Changes' : 'Register Item'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default HospitalInventoryTab;
