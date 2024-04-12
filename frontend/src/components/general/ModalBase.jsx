import { Modal, Fade, Box, Backdrop, IconButton } from "@mui/material";

/* Modal de confirmacion con shift */

export default function ModalBase(props) {
    const { isOpen, handleCloseModal, children } = props

    return (
        <Modal
            aria-labelledby="transition-modal-title"
            aria-describedby="transition-modal-description"
            open={isOpen}
            onClose={handleCloseModal}
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