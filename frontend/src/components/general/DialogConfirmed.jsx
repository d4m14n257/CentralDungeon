import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from "@mui/material";

export default function DialogConfirmed (props) {
    const { open, onClose, onCancel, onConfirm, message} = props;

    return (
        <Dialog
            open={open}
            onClose={onClose}
        >
            <DialogTitle>{message}</DialogTitle>
            <DialogContent>
                <DialogContentText>
                    Una vez realizado, este cambio no podra ser revertido de ninguna forma.
                </DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button size="small" variant='outlined' onClick={onConfirm}>Aceptar</Button>
                <Button color="error" size="small" variant='outlined' onClick={onCancel}>Cancelar</Button>
            </DialogActions>
        </Dialog>
    );
}