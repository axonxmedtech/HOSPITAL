import React, { useState } from 'react';
import {
    useReactTable,
    getCoreRowModel,
    flexRender,
} from '@tanstack/react-table';
import classNames from 'classnames';

const DataTable = ({ data, columns, pagination, loading, emptyState }) => {
    const table = useReactTable({
        data,
        columns,
        getCoreRowModel: getCoreRowModel(),
        manualPagination: true, // We handle pagination via props (server-side)
    });

    if (loading) {
        return (
            <div className="flex justify-center items-center h-64">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-500"></div>
            </div>
        );
    }

    if (!data.length && emptyState) {
        return emptyState;
    }

    return (
        <div className="overflow-x-auto">
            <div className="inline-block min-w-full align-middle">
                <div className="overflow-hidden border border-neutral-200 rounded-xl shadow-soft">
                    <table className="min-w-full divide-y divide-neutral-200">
                        <thead className="bg-neutral-50">
                            {table.getHeaderGroups().map(headerGroup => (
                                <tr key={headerGroup.id}>
                                    {headerGroup.headers.map(header => (
                                        <th
                                            key={header.id}
                                            scope="col"
                                            className="px-6 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wider"
                                        >
                                            {header.isPlaceholder
                                                ? null
                                                : flexRender(
                                                    header.column.columnDef.header,
                                                    header.getContext()
                                                )}
                                        </th>
                                    ))}
                                </tr>
                            ))}
                        </thead>
                        <tbody className="bg-white divide-y divide-neutral-100">
                            {table.getRowModel().rows.map(row => (
                                <tr key={row.id} className="hover:bg-neutral-50 transition-colors duration-200">
                                    {row.getVisibleCells().map(cell => (
                                        <td
                                            key={cell.id}
                                            className="px-6 py-4 whitespace-nowrap text-sm text-slate-700"
                                        >
                                            {flexRender(
                                                cell.column.columnDef.cell,
                                                cell.getContext()
                                            )}
                                        </td>
                                    ))}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                {/* Pagination Controls */}
                {pagination && (
                    <div className="bg-white px-4 py-3 flex items-center justify-between border-t border-neutral-200 sm:px-6 rounded-b-xl">
                        <div className="flex-1 flex justify-between sm:hidden">
                            <button
                                onClick={() => pagination.onPageChange(pagination.pageIndex - 1)}
                                disabled={pagination.pageIndex === 0}
                                className="relative inline-flex items-center px-4 py-2 border border-neutral-300 text-sm font-medium rounded-xl text-slate-700 bg-white hover:bg-neutral-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                            >
                                Previous
                            </button>
                            <button
                                onClick={() => pagination.onPageChange(pagination.pageIndex + 1)}
                                disabled={pagination.pageIndex >= pagination.pageCount - 1}
                                className="ml-3 relative inline-flex items-center px-4 py-2 border border-neutral-300 text-sm font-medium rounded-xl text-slate-700 bg-white hover:bg-neutral-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                            >
                                Next
                            </button>
                        </div>
                        <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
                            <div>
                                <p className="text-sm text-slate-700">
                                    Showing <span className="font-medium">{pagination.pageIndex * pagination.pageSize + 1}</span> to <span className="font-medium">{Math.min((pagination.pageIndex + 1) * pagination.pageSize, pagination.totalItems)}</span> of <span className="font-medium">{pagination.totalItems}</span> results
                                </p>
                            </div>
                            <div>
                                <nav className="relative z-0 inline-flex rounded-xl shadow-soft -space-x-px" aria-label="Pagination">
                                    <button
                                        onClick={() => pagination.onPageChange(pagination.pageIndex - 1)}
                                        disabled={pagination.pageIndex === 0}
                                        className="relative inline-flex items-center px-2 py-2 rounded-l-xl border border-neutral-300 bg-white text-sm font-medium text-slate-500 hover:bg-neutral-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                                    >
                                        <span className="sr-only">Previous</span>
                                        <svg className="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                            <path fillRule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clipRule="evenodd" />
                                        </svg>
                                    </button>
                                    {[...Array(pagination.pageCount)].map((_, i) => (
                                        // Logic to show limited page numbers can be added here. For now simplicity.
                                        // If we have many pages, we might need a smarter pagination component.
                                        <button
                                            key={i}
                                            onClick={() => pagination.onPageChange(i)}
                                            className={`relative inline-flex items-center px-4 py-2 border text-sm font-medium transition-colors ${pagination.pageIndex === i
                                                    ? 'z-10 bg-primary-50 border-primary-500 text-primary-600'
                                                    : 'bg-white border-neutral-300 text-slate-500 hover:bg-neutral-50'
                                                }`}
                                        >
                                            {i + 1}
                                        </button>
                                    ))}
                                    <button
                                        onClick={() => pagination.onPageChange(pagination.pageIndex + 1)}
                                        disabled={pagination.pageIndex >= pagination.pageCount - 1}
                                        className="relative inline-flex items-center px-2 py-2 rounded-r-xl border border-neutral-300 bg-white text-sm font-medium text-slate-500 hover:bg-neutral-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                                    >
                                        <span className="sr-only">Next</span>
                                        <svg className="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                            <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
                                        </svg>
                                    </button>
                                </nav>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default DataTable;
