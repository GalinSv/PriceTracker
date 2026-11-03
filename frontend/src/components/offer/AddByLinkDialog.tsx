import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";

import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    TextField,
} from "@mui/material";

import { importOffer } from "../../services/offerService";
import { notify } from "../../utils/notify";

type Props = {
    open: boolean;
    onClose: () => void;
};

function AddByLinkDialog({ open, onClose }: Props) {
    const navigate = useNavigate();
    const [url, setUrl] = useState("");
    const [saving, setSaving] = useState(false);

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        if (!url.trim()) return;

        setSaving(true);
        try {
            const product = await importOffer(url.trim());
            notify.success("Продуктът е добавен");
            setUrl("");
            onClose();
            navigate(`/products/${product.id}`);
        } catch (error) {
            notify.error(error, "Продуктът не можа да бъде добавен");
        } finally {
            setSaving(false);
        }
    }

    return (
        <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
            <form onSubmit={handleSubmit}>
                <DialogTitle>Добави продукт по линк</DialogTitle>
                <DialogContent>
                    <DialogContentText sx={{ mb: 2 }}>
                        Постави линк към продукт от поддържан магазин (засега PIC и Ozone).
                    </DialogContentText>
                    <TextField
                        autoFocus
                        fullWidth
                        label="Линк към продукта"
                        placeholder="https://www.pic.bg/..."
                        value={url}
                        onChange={(e) => setUrl(e.target.value)}
                        disabled={saving}
                    />
                </DialogContent>
                <DialogActions sx={{ px: 3, pb: 2 }}>
                    <Button onClick={onClose} disabled={saving}>
                        Отказ
                    </Button>
                    <Button type="submit" variant="contained" loading={saving} disabled={!url.trim()}>
                        Добави
                    </Button>
                </DialogActions>
            </form>
        </Dialog>
    );
}

export default AddByLinkDialog;
