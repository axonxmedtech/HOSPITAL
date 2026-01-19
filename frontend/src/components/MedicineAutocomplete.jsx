import React, { useState, useEffect } from 'react';
import { Combobox } from '@headlessui/react';
import { CheckIcon, ChevronUpDownIcon } from '@heroicons/react/20/solid';
import hospitalService from '../services/hospitalService';

export default function MedicineAutocomplete({ value, onChange, onSelect }) {
    const [query, setQuery] = useState('');
    const [selectedMedicine, setSelectedMedicine] = useState(null);
    const [medicines, setMedicines] = useState([]);
    const [loading, setLoading] = useState(false);

    // Debounce search
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
        }, 300); // 300ms debounce

        return () => clearTimeout(timeoutId);
    }, [query]);

    // Handle external value changes (e.g., reset)
    useEffect(() => {
        if (!value) {
            setSelectedMedicine(null);
            setQuery('');
        } else {
            // If value is set externally but query is empty, allow it to just be the text
            // Don't overwrite query if user is typing
            if (value !== query && !query) {
                // setQuery(value); // Optional: Pre-fill query if editing? 
            }
        }
    }, [value]);

    return (
        <Combobox
            value={selectedMedicine}
            onChange={(medicine) => {
                setSelectedMedicine(medicine);
                // Handle clear (null)
                if (medicine) {
                    onChange(medicine.name);
                    if (onSelect) onSelect(medicine);
                } else {
                    onChange(''); // Clear the text value
                }
            }}
            nullable // Allow clearing
        >
            <div className="relative mt-1">
                <div className="relative w-full cursor-default overflow-hidden rounded-lg bg-white text-left shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-white/75 focus-visible:ring-offset-2 focus-visible:ring-offset-teal-300 sm:text-sm border border-gray-300">
                    <Combobox.Input
                        className="w-full border-none py-2 pl-3 pr-10 text-sm leading-5 text-gray-900 focus:ring-0"
                        displayValue={(medicine) => (typeof medicine === 'string' ? medicine : medicine?.name || value || '')}
                        onChange={(event) => {
                            setQuery(event.target.value);
                            onChange(event.target.value); // Allow free text input too
                        }}
                        placeholder="Search medicine..."
                    />
                    <Combobox.Button className="absolute inset-y-0 right-0 flex items-center pr-2">
                        <ChevronUpDownIcon
                            className="h-5 w-5 text-gray-400"
                            aria-hidden="true"
                        />
                    </Combobox.Button>
                </div>
                <Combobox.Options className="absolute mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black/5 focus:outline-none sm:text-sm z-50">
                    {medicines.length === 0 && query !== '' && !loading ? (
                        <div className="relative cursor-default select-none px-4 py-2 text-gray-700">
                            Nothing found.
                        </div>
                    ) : (
                        medicines.map((medicine) => (
                            <Combobox.Option
                                key={medicine.id}
                                className={({ active }) =>
                                    `relative cursor-default select-none py-2 pl-10 pr-4 ${active ? 'bg-primary-600 text-white' : 'text-gray-900'
                                    }`
                                }
                                value={medicine}
                            >
                                {({ selected, active }) => (
                                    <>
                                        <div className="flex justify-between">
                                            <span
                                                className={`block truncate ${selected ? 'font-medium' : 'font-normal'
                                                    }`}
                                            >
                                                {medicine.name}
                                            </span>
                                            <span className={`text-xs ${active ? 'text-blue-200' : 'text-gray-500'}`}>
                                                {medicine.type} • {medicine.defaultDosage}
                                            </span>
                                        </div>
                                        {selected ? (
                                            <span
                                                className={`absolute inset-y-0 left-0 flex items-center pl-3 ${active ? 'text-white' : 'text-primary-600'
                                                    }`}
                                            >
                                                <CheckIcon className="h-5 w-5" aria-hidden="true" />
                                            </span>
                                        ) : null}
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
