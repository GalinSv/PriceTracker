import type { ReactNode } from "react";

import {
    Box,
    Button,
    Card,
    Checkbox,
    FormControl,
    InputAdornment,
    InputLabel,
    ListItemText,
    MenuItem,
    Select,
    TextField,
    Typography,
} from "@mui/material";

import type { CatalogFacets, FacetValue, StoreCatalog } from "../../types/api";
import { NO_FILTERS } from "../../utils/catalogGroups";
import type { CatalogFilters, SortOrder } from "../../utils/catalogGroups";

type Props = {
    filters: CatalogFilters;
    onChange: (filters: CatalogFilters) => void;
    stores: StoreCatalog[];
    // Brands, series, memory and colors of all results, with counts; null while unknown
    facets: CatalogFacets | null;
    // Shown at the right end of the bar, e.g. "Load all results"
    action?: ReactNode;
};

const SORT_LABELS: Record<SortOrder, string> = {
    relevance: "Най-подходящи",
    priceAsc: "Цена: от ниска към висока",
    priceDesc: "Цена: от висока към ниска",
    stores: "Брой магазини",
};

function toNumber(text: string) {
    const value = Number(text);
    return text.trim() === "" || Number.isNaN(value) ? null : value;
}

// Multi-select values come as an array, or as a string after autofill
function toList(value: string | string[]) {
    return typeof value === "string" ? value.split(",") : value;
}

// "256 GB" -> 256, "1 TB" -> 1024
function gigabytes(value: string) {
    const [number, unit] = value.split(" ");
    return Number(number) * (unit === "TB" ? 1024 : 1);
}

type FacetSelectProps = {
    id: string;
    label: string;
    values: FacetValue[];
    selected: string[];
    onChange: (selected: string[]) => void;
    width: number;
};

function FacetSelect({ id, label, values, selected, onChange, width }: FacetSelectProps) {
    // A chosen value stays in the list even when this search has none of it
    const missing = selected.filter((value) => !values.some((option) => option.value === value));
    const options = [...values, ...missing.map((value) => ({ value, count: 0 }))];

    return (
        <FormControl size="small" sx={{ width }} disabled={options.length === 0}>
            <InputLabel id={id} shrink>
                {label}
            </InputLabel>
            <Select
                labelId={id}
                label={label}
                notched
                multiple
                displayEmpty
                value={selected}
                onChange={(event) => onChange(toList(event.target.value))}
                renderValue={(chosen) => (chosen.length === 0 ? "Всички" : chosen.join(", "))}
            >
                {options.map((option) => (
                    <MenuItem key={option.value} value={option.value}>
                        <Checkbox size="small" checked={selected.includes(option.value)} />
                        <ListItemText primary={option.value} />
                        <Typography variant="body2" color="text.secondary" sx={{ ml: 2 }}>
                            {option.count}
                        </Typography>
                    </MenuItem>
                ))}
            </Select>
        </FormControl>
    );
}

function CatalogFilterBar({ filters, onChange, stores, facets, action }: Props) {
    const storeNames = new Map(stores.map((store) => [store.storeCode, store.storeName]));
    const memory = [...(facets?.memory ?? [])].sort((a, b) => gigabytes(a.value) - gigabytes(b.value));
    const hasFilters =
        filters.stores.length > 0 ||
        filters.brands.length > 0 ||
        filters.series.length > 0 ||
        filters.memory.length > 0 ||
        filters.colors.length > 0 ||
        filters.minPrice != null ||
        filters.maxPrice != null ||
        filters.sort !== NO_FILTERS.sort;

    return (
        <Card sx={{ p: 2, mb: 2, display: "flex", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
            <FormControl size="small" sx={{ width: 160 }}>
                <InputLabel id="filter-stores" shrink>
                    Магазини
                </InputLabel>
                <Select
                    labelId="filter-stores"
                    label="Магазини"
                    notched
                    multiple
                    displayEmpty
                    value={filters.stores}
                    onChange={(event) => onChange({ ...filters, stores: toList(event.target.value) })}
                    renderValue={(selected) =>
                        selected.length === 0
                            ? "Всички"
                            : selected.length === 1
                              ? storeNames.get(selected[0])
                              : `${selected.length} магазина`
                    }
                >
                    {stores.map((store) => (
                        <MenuItem key={store.storeCode} value={store.storeCode}>
                            <Checkbox size="small" checked={filters.stores.includes(store.storeCode)} />
                            <ListItemText primary={store.storeName} />
                        </MenuItem>
                    ))}
                </Select>
            </FormControl>

            <FacetSelect
                id="filter-brands"
                label="Марка"
                values={facets?.brands ?? []}
                selected={filters.brands}
                onChange={(brands) => onChange({ ...filters, brands })}
                width={160}
            />
            <FacetSelect
                id="filter-series"
                label="Серия"
                values={facets?.series ?? []}
                selected={filters.series}
                onChange={(series) => onChange({ ...filters, series })}
                width={220}
            />
            <FacetSelect
                id="filter-memory"
                label="Памет"
                values={memory}
                selected={filters.memory}
                onChange={(selected) => onChange({ ...filters, memory: selected })}
                width={130}
            />
            <FacetSelect
                id="filter-colors"
                label="Цвят"
                values={facets?.colors ?? []}
                selected={filters.colors}
                onChange={(colors) => onChange({ ...filters, colors })}
                width={130}
            />

            <TextField
                size="small"
                type="number"
                label="Цена от"
                value={filters.minPrice ?? ""}
                onChange={(event) => onChange({ ...filters, minPrice: toNumber(event.target.value) })}
                sx={{ width: 115 }}
                slotProps={{
                    input: { endAdornment: <InputAdornment position="end">€</InputAdornment> },
                    htmlInput: { min: 0, step: "any" },
                }}
            />
            <TextField
                size="small"
                type="number"
                label="Цена до"
                value={filters.maxPrice ?? ""}
                onChange={(event) => onChange({ ...filters, maxPrice: toNumber(event.target.value) })}
                sx={{ width: 115 }}
                slotProps={{
                    input: { endAdornment: <InputAdornment position="end">€</InputAdornment> },
                    htmlInput: { min: 0, step: "any" },
                }}
            />

            <FormControl size="small" sx={{ width: 210 }}>
                <InputLabel id="filter-sort">Подреди по</InputLabel>
                <Select
                    labelId="filter-sort"
                    label="Подреди по"
                    value={filters.sort}
                    onChange={(event) => onChange({ ...filters, sort: event.target.value as SortOrder })}
                >
                    {Object.entries(SORT_LABELS).map(([value, label]) => (
                        <MenuItem key={value} value={value}>
                            {label}
                        </MenuItem>
                    ))}
                </Select>
            </FormControl>

            {hasFilters && <Button onClick={() => onChange(NO_FILTERS)}>Изчисти</Button>}
            {action && <Box sx={{ ml: "auto" }}>{action}</Box>}
        </Card>
    );
}

export default CatalogFilterBar;
