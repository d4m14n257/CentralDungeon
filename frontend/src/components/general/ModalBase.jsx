import { useCallback, useContext } from "react";
import { Confirm } from "@/contexts/ConfirmContext";
import { Modal, Fade, Box, Backdrop } from "@mui/material";

export default function ModalBase(props) {
    const { isOpen, handleCloseModal, closeConfirm = false, children } = props
    const { confirm, setMessage } = useContext(Confirm)

    const handleCloseConfirmModal = useCallback(async () => {
        try {
            setMessage('¿Estas seguro que desae cerrar el modal sin finalizar?')

            await confirm()
                .catch(() => {throw {canceled: true}});

            handleCloseModal();
        }
        catch (err){
            return;
        }
    }, [])
    
    return (
        <Modal
            aria-labelledby="transition-modal-title"
            aria-describedby="transition-modal-description"
            open={isOpen}
            onClose={closeConfirm ? handleCloseConfirmModal : handleCloseModal}
            closeAfterTransition
            slots={{ backdrop: Backdrop }}
            slotProps={{
                backdrop: {
                    timeout: 500,
                },
            }}
            sx={{
                overflowY: "scroll",
            }}
        >
            <Fade in={isOpen}>
                <Box
                    sx={{
                        position: 'absolute',
                        display: "flex",
                        justifyContent: "center",
                        alignItems: "center",
                        minWidth: 250,
                        maxWidth: 850,
                        top: '50%',
                        left: '50%',
                        transform: 'translate(-50%, max(-50vh, -50%))',
                        bgcolor: 'background.paper',
                        boxShadow: "2em",
                        padding: "2em",
                        borderRadius: "1em",
                    }}
                >
                    <Box>
                        {children}
                    </Box>
                </Box>
            </Fade>
        </Modal>
    );
}