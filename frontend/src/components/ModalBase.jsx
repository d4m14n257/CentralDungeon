import { Modal, Fade, Box, Backdrop, IconButton } from "@mui/material";

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
                        top: '50%',
                        left: '50%',
                        transform: 'translate(-50%, -50%)',
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        width: "100%",
                        flexGrow: 1,
                    }}
                >
                    <Box
                      sx={{
                        minWidth: 250,
                        maxWidth: 850,
                        position: "relative",
                        bgcolor: 'background.paper',
                        boxShadow: "2em",
                        padding: "2em",
                        borderRadius: "1em",
                      }}
                    >
                        {children}
                    </Box>
                </Box>
            </Fade>
        </Modal>
    );
}