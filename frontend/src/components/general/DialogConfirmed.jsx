import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from "@mui/material";

export default function DialogConfirmed (props) {
    const { open, onClose, onCancel, onConfirm, message} = props;

    return (
        <Dialog
            open={open}
            onClose={onClose}
        >
            <DialogTitle>{message}</DialogTitle>
            <DialogActions>
                <Button size="small" variant='outlined' onClick={onConfirm}>Aceptar</Button>
                <Button color="error" size="small" variant='outlined' onClick={onCancel}>Cancelar</Button>
            </DialogActions>
        </Dialog>
    );
}