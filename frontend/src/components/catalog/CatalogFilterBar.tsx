import {
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
} from "@mui/material";

import type { StoreCatalog } from "../../types/api";
import { NO_FILTERS } from "../../utils/catalogGroups";
import type { CatalogFilters, SortOrder } from "../../utils/catalogGroups";

type Props = {
    filters: CatalogFilters;
    onChange: (filters: CatalogFilters) => void;
    stores: StoreCatalog[];
    // [key, name, count], see brandOptions()
    brands: [string, string, number][];
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

function CatalogFilterBar({ filters, onChange, stores, brands }: Props) {
    const brandNames = new Map(brands.map(([key, name]) => [key, name]));
    const storeNames = new Map(stores.map((store) => [store.storeCode, store.storeName]));
    const hasFilters =
        filters.stores.length > 0 ||
        filters.minPrice != null ||
        filters.maxPrice != null ||
        filters.brands.length > 0 ||
        filters.sort !== NO_FILTERS.sort;

    return (
        <Card sx={{ p: 2, mb: 2, display: "flex", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
            <FormControl size="small" sx={{ width: 190 }}>
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

            <TextField
                size="small"
                type="number"
                label="Цена от"
                value={filters.minPrice ?? ""}
                onChange={(event) => onChange({ ...filters, minPrice: toNumber(event.target.value) })}
                sx={{ width: 130 }}
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
                sx={{ width: 130 }}
                slotProps={{
                    input: { endAdornment: <InputAdornment position="end">€</InputAdornment> },
                    htmlInput: { min: 0, step: "any" },
                }}
            />

            <FormControl size="small" sx={{ width: 190 }} disabled={brands.length === 0}>
                <InputLabel id="filter-brands" shrink>
                    Марка
                </InputLabel>
                <Select
                    labelId="filter-brands"
                    label="Марка"
                    notched
                    multiple
                    displayEmpty
                    value={filters.brands}
                    onChange={(event) => onChange({ ...filters, brands: toList(event.target.value) })}
                    renderValue={(selected) =>
                        selected.length === 0
                            ? "Всички"
                            : selected.map((key) => brandNames.get(key) ?? key).join(", ")
                    }
                >
                    {brands.map(([key, name, count]) => (
                        <MenuItem key={key} value={key}>
                            <Checkbox size="small" checked={filters.brands.includes(key)} />
                            <ListItemText primary={name} secondary={count} />
                        </MenuItem>
                    ))}
                </Select>
            </FormControl>

            <FormControl size="small" sx={{ width: 240 }}>
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
        </Card>
    );
}

export default CatalogFilterBar;
