import React, { useState, useEffect } from 'react';
import { Combobox } from '@headlessui/react';
import { CheckIcon, ChevronUpDownIcon } from '@heroicons/react/20/solid';
import hospitalService from '../services/hospitalService';

export default function MedicineAutocomplete({ value, onChange, onSelect }) {
    const [query, setQuery] = useState('');
    const [selectedMedicine, setSelectedMedicine] = useState(null);
    const [medicines, setMedicines] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        const timeoutId = setTimeout(async () => {
            if (query.length >= 2) {
                setLoading(true);
                try {
                    const results = await hospitalService.searchMedicines(query);
                    setMedicines(results);
                } catch (error) {
                    console.error("Failed to search medicines", error);
                    setMedicines([]);
                } finally {
                    setLoading(false);
                }
            } else {
                setMedicines([]);
            }
        }, 300);

        return () => clearTimeout(timeoutId);
    }, [query]);

    // Reset when parent clears value
    useEffect(() => {
        if (!value) {
            setSelectedMedicine(null);
            setQuery('');
        }
    }, [value]);

    return (
        <Combobox
            value={selectedMedicine}
            onChange={(medicine) => {
                setSelectedMedicine(medicine);
                if (medicine) {
                    onChange(medicine.name);
                    if (onSelect) onSelect(medicine);
                } else {
                    onChange('');
                }
            }}
            nullable
        >
            <div className="relative mt-1">
                <div className="relative w-full cursor-default overflow-hidden rounded-lg bg-white text-left shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-white/75 focus-visible:ring-offset-2 focus-visible:ring-offset-teal-300 sm:text-sm border border-gray-300">
                    <Combobox.Input
                        className="w-full border-none py-2 pl-3 pr-10 text-sm leading-5 text-gray-900 focus:ring-0"
                        displayValue={(medicine) => medicine?.name || ''}
                        onChange={(event) => {
                            setQuery(event.target.value);
                            if (!event.target.value) {
                                setSelectedMedicine(null);
                                onChange('');
                            } else if (selectedMedicine) {
                                // User is retyping after a selection — clear the previous pick
                                setSelectedMedicine(null);
                                onChange('');
                            }
                        }}
                        placeholder="Search medicine from catalog..."
                    />
                    <Combobox.Button className="absolute inset-y-0 right-0 flex items-center pr-2">
                        <ChevronUpDownIcon className="h-5 w-5 text-gray-400" aria-hidden="true" />
                    </Combobox.Button>
                </div>
                <Combobox.Options className="absolute mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black/5 focus:outline-none sm:text-sm z-50">
                    {loading ? (
                        <div className="relative cursor-default select-none px-4 py-2 text-gray-500 text-sm">
                            Searching...
                        </div>
                    ) : medicines.length === 0 && query.length >= 2 ? (
                        <div className="relative cursor-default select-none px-4 py-2 text-gray-700 text-sm">
                            No medicines found in catalog. Add to catalog first.
                        </div>
                    ) : (
                        medicines.map((medicine) => (
                            <Combobox.Option
                                key={medicine.id}
                                className={({ active }) =>
                                    `relative cursor-default select-none py-2 pl-10 pr-4 ${active ? 'bg-primary-600 text-white' : 'text-gray-900'}`
                                }
                                value={medicine}
                            >
                                {({ selected, active }) => (
                                    <>
                                        <div className="flex justify-between">
                                            <span className={`block truncate ${selected ? 'font-medium' : 'font-normal'}`}>
                                                {medicine.name}
                                            </span>
                                            <span className={`text-xs ${active ? 'text-blue-200' : 'text-gray-500'}`}>
                                                {medicine.type}{medicine.defaultDosage ? ` • ${medicine.defaultDosage}` : ''}
                                            </span>
                                        </div>
                                        {selected && (
                                            <span className={`absolute inset-y-0 left-0 flex items-center pl-3 ${active ? 'text-white' : 'text-primary-600'}`}>
                                                <CheckIcon className="h-5 w-5" aria-hidden="true" />
                                            </span>
                                        )}
                                    </>
                                )}
                            </Combobox.Option>
                        ))
                    )}
                </Combobox.Options>
            </div>
        </Combobox>
    );
}
